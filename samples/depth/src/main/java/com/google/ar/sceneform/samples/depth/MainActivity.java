package com.google.ar.sceneform.samples.depth;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.CameraStream;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnTapArPlaneListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener {

    private ArFragment arFragment;
    private Renderable model;
    private ObjectAnimator modelAnimator;
    private MediaPlayer modelSound = new MediaPlayer();
    private ViewRenderable viewRenderable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        getSupportFragmentManager().addFragmentOnAttachListener(this);

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }

        loadModels();
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnTapArPlaneListener(this);
            arFragment.setOnViewCreatedListener(this);
            arFragment.setOnSessionConfigurationListener(this);
        }
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        arFragment.setOnViewCreatedListener(null);

        // Available modes: DEPTH_OCCLUSION_DISABLED, DEPTH_OCCLUSION_ENABLED
        arSceneView.getCameraStream()
                .setDepthOcclusionMode(CameraStream.DepthOcclusionMode.DEPTH_OCCLUSION_ENABLED);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (modelAnimator != null && modelAnimator.isPaused()) {
            modelAnimator.start();
            modelSound.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (modelAnimator != null && modelAnimator.isRunning()) {
            modelAnimator.pause();
            modelSound.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopSound();
        modelSound.release();
    }

    public void loadModels() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
                .setSource(this, Uri.parse("https://storage.googleapis.com/ar-answers-in-search-models/static/GiantPanda/model.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(model -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.model = model;
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
        ViewRenderable.builder()
                .setView(this, R.layout.view_model_title)
                .build()
                .thenAccept(viewRenderable -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.viewRenderable = viewRenderable;
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    @Override
    public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if (model == null || viewRenderable == null) {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create the Anchor.
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
        modelNode.setParent(anchorNode);
        RenderableInstance modelInstance = modelNode.setRenderable(this.model);
        modelAnimator = modelInstance.animate(true);
        modelAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                playSound();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                stopSound();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                stopSound();
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                stopSound();
                playSound();
            }
        });
        modelAnimator.start();
        modelNode.select();

        Node titleNode = new Node();
        titleNode.setParent(modelNode);
        titleNode.setEnabled(false);
        titleNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));
        titleNode.setRenderable(viewRenderable);
        titleNode.setEnabled(true);
    }

    private void playSound() {
        try {
            // Can't figure out why does the repeat function doesn't for remote url
            modelSound.setDataSource("https://storage.googleapis.com/ar-answers-in-search-models/static/GiantPanda/Bear_Panda_Giant_Unisex_Adult.ogg");
            modelSound.prepare();
            modelSound.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopSound() {
        modelSound.stop();
        modelSound.reset();
    }

    @Override
    public void onSessionConfiguration(Session session, Config config) {
        // Comment this in to feed the DepthTexture with Raw Depth Data.
        /*if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY))
            config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);*/

        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        }

        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
    }
}
