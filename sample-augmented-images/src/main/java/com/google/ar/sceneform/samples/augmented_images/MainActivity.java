package com.google.ar.sceneform.samples.augmented_images;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.filament.Engine;
import com.google.android.filament.filamat.MaterialBuilder;
import com.google.android.filament.filamat.MaterialPackage;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.EngineInstance;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity implements
        BaseArFragment.OnSessionInitializationListener {
    private ArFragment arFragment;

    private Scene scene;
    private boolean matrixDetected = false;
    private boolean rabbitDetected = false;
    private boolean isDetected = false;

    private boolean configureSession = false;
    private Session session;

    private AugmentedImageDatabase database;

    private Renderable plainVideoModel;
    private Material plainVideoMaterial;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            ((ViewGroup.MarginLayoutParams) toolbar.getLayoutParams()).topMargin = insets.getSystemWindowInsetTop();
            return insets.consumeSystemWindowInsets();
        });

        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (fragment.getId() == R.id.arFragment) {
                this.arFragment = (ArFragment) fragment;
                this.arFragment.setOnSessionInitializationListener(MainActivity.this);
            }
        });

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                    .add(R.id.arFragment, ArFragment.class, null)
                    .commit();
            }
        }

        //.glb models can be loaded at runtime when needed or when app starts
        //this method loads ModelRenderable when app starts
        loadMatrixModel();
        loadMatrixMaterial();
    }

    private void loadMatrixModel() {

        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
            .setSource(this, Uri.parse("Video.glb"))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept(model -> {
                MainActivity activity = weakActivity.get();
                if (activity != null) {
                    //removing shadows for this Renderable
                    model.setShadowCaster(false);
                    model.setShadowReceiver(false);

                    activity.plainVideoModel = model;
                }
            })
            .exceptionally(
                throwable -> {
                    Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    private void loadMatrixMaterial() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        Engine filamentEngine = EngineInstance.getEngine().getFilamentEngine();

        MaterialBuilder.init();
        MaterialBuilder materialBuilder = new MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("Plain Video Material")
            .require(MaterialBuilder.VertexAttribute.UV0)
            .shading(MaterialBuilder.Shading.UNLIT)
            .doubleSided(true)
            .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
            .optimization(MaterialBuilder.Optimization.NONE);

        MaterialPackage plainVideoMaterialPackage = materialBuilder
            .blending(MaterialBuilder.BlendingMode.OPAQUE)
            .material("void material(inout MaterialInputs material) {\n" + "    prepareMaterial(material);\n" +
                    "    material.baseColor = texture(materialParams_videoTexture, getUV0()).rgba;\n" + "}\n")
            .build(filamentEngine);
        if (plainVideoMaterialPackage.isValid()) {
            ByteBuffer buffer = plainVideoMaterialPackage.getBuffer();
            Material.builder()
                .setSource(buffer)
                .build()
                .thenAccept(material -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.plainVideoMaterial = material;
                    }
                })
                .exceptionally(
                    throwable -> {
                        Toast.makeText(this, "Unable to load material", Toast.LENGTH_LONG).show();
                        return null;
                    });
        }
        MaterialBuilder.shutdown();
    }

    @Override
    public void onSessionInitialization(Session session) {
        this.scene = this.arFragment.getArSceneView().getScene();
        this.scene.addOnUpdateListener(this::onUpdate);

        setupSession();
    }

    private void setupSession() {
        if (session == null) {
            try {
                session = new Session(this);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            configureSession = true;
        }

        if (configureSession) {

            Config config = new Config(session);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);

            //Images to be detected by our AR need to be added in AugmentedImageDatabase
            //This is how database is created at runtime
            //You can also prebuild database in you computer and load it directly (see: https://developers.google.com/ar/develop/java/augmented-images/guide#database)

            database = new AugmentedImageDatabase(session);

            Bitmap matrixImage = BitmapFactory.decodeResource(getResources(), R.drawable.matrix);
            Bitmap rabbitImage = BitmapFactory.decodeResource(getResources(), R.drawable.rabbit);
            //Every image has to have its own unique String identifier
            database.addImage("matrix", matrixImage);
            database.addImage("rabbit", rabbitImage);

            config.setAugmentedImageDatabase(database);

            session.configure(config);

            configureSession = false;
            arFragment.getArSceneView().setupSession(session);
            //hiding plane indicating dots
            arFragment.getArSceneView().getPlaneRenderer().setVisible(false);
        }

        try {
            session.resume();
            arFragment.getArSceneView().resume();
        }
        catch (CameraNotAvailableException e) {
            e.printStackTrace();
            session = null;
        }
    }

    //every time new image is processed by ARCore and ready, this method is called
    public void onUpdate(FrameTime frameTime) {
        //if there are both images already detected, for better CPU usage we do not need scan for them
        if (matrixDetected && rabbitDetected)
            return;

        Frame frame = this.arFragment.getArSceneView().getArFrame();
        try {
            //this is collection of all images from our AugmentedImageDatabase that are currently detected by ARCore in this session
            Collection<AugmentedImage> augmentedImageCollection = frame.getUpdatedTrackables(AugmentedImage.class);

            for (AugmentedImage image : augmentedImageCollection) {
                if (image.getTrackingState() == TrackingState.TRACKING) {
                    //if matrix video haven't been placed yet and detected image has String identifier of "matrix"
                    if (!matrixDetected && image.getName().equals("matrix")) {
                        matrixDetected = true;
                        Toast.makeText(this, "Matrix tag detected", Toast.LENGTH_LONG).show();

                        //new AnchorNode placed to the detected tag and set it to the real size of the tag
                        //this will cause deformation if your AR tag has different aspect ratio than your video
                        AnchorNode anchorNode = new AnchorNode(image.createAnchor(image.getCenterPose()));
                        anchorNode.setWorldScale(new Vector3(image.getExtentX(), 1f, image.getExtentZ()));

                        arFragment.getArSceneView().getScene().addChild(anchorNode);

                        TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
                        transNode.setParent(anchorNode);
                        //for some reason it is shown upside down so this will rotate it correctly
                        transNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 180f));

                        //setting texture
                        ExternalTexture externalTexture = new ExternalTexture();
                        RenderableInstance renderableInstance;
                        transNode.setRenderable(plainVideoModel);
                        renderableInstance = transNode.getRenderableInstance();
                        renderableInstance.setMaterial(plainVideoMaterial);

                        //setting MediaPLayer
                        renderableInstance.getMaterial().setExternalTexture("videoTexture", externalTexture);
                        mediaPlayer = MediaPlayer.create(this, R.raw.matrix);
                        mediaPlayer.setLooping(true);
                        mediaPlayer.setSurface(externalTexture.getSurface());
                        mediaPlayer.start();
                    }
                    //if rabbit model haven't been placed yet and detected image has String identifier of "rabbit"
                    //this is also example of model loading and placing at runtime
                    if (!rabbitDetected && image.getName().equals("rabbit")) {
                        rabbitDetected = true;
                        Toast.makeText(this, "Rabbit tag detected", Toast.LENGTH_LONG).show();

                        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
                        ModelRenderable.builder()
                            .setSource(this, Uri.parse("Rabbit.glb"))
                            .setIsFilamentGltf(true)
                            .build()
                            .thenAccept(model -> {
                                MainActivity activity = weakActivity.get();
                                if (activity != null) {

                                    //setting anchor to the center of AR tag
                                    AnchorNode anchorNode = new AnchorNode(image.createAnchor(image.getCenterPose()));

                                    arFragment.getArSceneView().getScene().addChild(anchorNode);

                                    TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
                                    transNode.setParent(anchorNode);
                                    transNode.setRenderable(model);

                                    //removing shadows
                                    model.setShadowCaster(false);
                                    model.setShadowReceiver(false);
                                }
                            })
                            .exceptionally(
                                throwable -> {
                                    Toast.makeText(this, "Unable to load rabbit model", Toast.LENGTH_LONG).show();
                                    return null;
                                });
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}