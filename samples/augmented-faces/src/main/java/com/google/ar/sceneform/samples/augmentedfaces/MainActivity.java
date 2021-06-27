package com.google.ar.sceneform.samples.augmentedfaces;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.filament.ColorGrading;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Frame;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.rendering.EngineInstance;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.AugmentedFaceNode;
import com.google.ar.sceneform.ux.FaceArFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    private Set<CompletableFuture> loaders = new HashSet<>();

    private FaceArFragment arFragment;
    private ArSceneView arSceneView;

    private Texture faceTexture;
    private ModelRenderable faceModel;

    private final HashMap<AugmentedFace, AugmentedFaceNode> facesNodes = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getSupportFragmentManager().addFragmentOnAttachListener(this::onAttachFragment);

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, FaceArFragment.class, null)
                        .commit();
            }
        }

        loadModels();
        loadTextures();
    }

    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (FaceArFragment) fragment;
            arFragment.setOnViewCreatedListener(this::onViewCreated);
        }
    }

    public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
        this.arSceneView = arSceneView;

        // Currently, the tone-mapping should be changed to FILMIC
        // because with other tone-mapping operators except LINEAR
        // the inverseTonemapSRGB function in the materials can produce incorrect results.
        // The LINEAR tone-mapping cannot be used together with the inverseTonemapSRGB function.
        Renderer renderer = arSceneView.getRenderer();
        if (renderer != null) {
            renderer.getFilamentView().setColorGrading(
                    new ColorGrading.Builder()
                            .toneMapping(ColorGrading.ToneMapping.FILMIC)
                            .build(EngineInstance.getEngine().getFilamentEngine())
            );
        }

        //TODO : Check that
        // This is important to make sure that the camera stream renders first so that
        // the face mesh occlusion works correctly.
//        arSceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);

        // Hide the moving hand tutorial
        arFragment.getPlaneDiscoveryController().hide();

        // Hide plane indicating dots
        arSceneView.getPlaneRenderer().setVisible(false);
        // Disable the rendering of detected planes.
        arSceneView.getPlaneRenderer().setEnabled(false);

        // Check for face detections
        arSceneView.getScene().addOnUpdateListener(this::onUpdate);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (CompletableFuture<?> loader : loaders) {
            if (!loader.isDone()) {
                loader.cancel(true);
            }
        }
    }

    private void loadModels() {
        loaders.add(ModelRenderable.builder()
//                .setSource(this, Uri.parse("models/canonical_face_transparent.glb"))
                .setSource(this, Uri.parse("models/fox_face.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> faceModel = model)
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                            return null;
                        }));
    }

    private void loadTextures() {
        loaders.add(Texture.builder()
                .setSource(this, Uri.parse("textures/fox_face.png"))
                .setUsage(Texture.Usage.COLOR_MAP)
                .build()
                .thenAccept(texture -> faceTexture = texture)
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load texture", Toast.LENGTH_LONG).show();
                            return null;
                        }));
    }

    private void onUpdate(FrameTime frameTime) {
        if (faceModel == null || faceTexture == null) {
            return;
        }

        Frame frame = arFragment.getArSceneView().getArFrame();

        // Get a list of AugmentedFace which are updated on this frame.
        Collection<AugmentedFace> augmentedFaces = frame.getUpdatedTrackables(AugmentedFace.class);

        //TODO: Check the difference with getAllTrackables.
        // See: https://stackoverflow.com/questions/49241526/what-is-the-difference-between-session-getalltrackables-and-frame-getupdatedtrac
//          Collection<AugmentedFace> faceList = arSceneView.getSession().getAllTrackables(AugmentedFace.class);

        // Make new AugmentedFaceNodes for any new faces.
        for (AugmentedFace augmentedFace : new ArrayList<>(augmentedFaces)) {
            AugmentedFaceNode existingFaceNode = facesNodes.get(augmentedFace);
            switch (augmentedFace.getTrackingState()) {
                case TRACKING:
                    if (existingFaceNode == null) {
                        AugmentedFaceNode faceNode = new AugmentedFaceNode(augmentedFace);
                        RenderableInstance modelInstance = faceNode.setFaceRegionsRenderable(faceModel);
                        modelInstance.setShadowCaster(false);
                        modelInstance.setShadowReceiver(true);
//                        modelInstance.getMaterial().setBaseColorTexture(faceTexture);
                        arSceneView.getScene().addChild(faceNode);

                        facesNodes.put(augmentedFace, faceNode);
                    }
                    break;
                case STOPPED:
                    arSceneView.getScene().removeChild(existingFaceNode);
                    facesNodes.remove(augmentedFace);
                    break;
            }
        }
    }
}