package com.google.ar.sceneform.samples.augmentedimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.android.filament.ColorGrading;
import com.google.android.filament.Engine;
import com.google.android.filament.filamat.MaterialBuilder;
import com.google.android.filament.filamat.MaterialPackage;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.EngineInstance;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;

    private boolean matrixDetected = false;
    private boolean rabbitDetected = false;

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
        getSupportFragmentManager().addFragmentOnAttachListener(this::onAttachFragment);

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }

        // .glb models can be loaded at runtime when needed or when app starts
        // This method loads ModelRenderable when app starts
        loadModels();
        loadMaterials();
    }

    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnSessionConfigurationListener(this::onSessionConfiguration);
            arFragment.setOnViewCreatedListener(this::onViewCreated);
        }
    }

    public void onSessionConfiguration(Session session, Config config) {
        // Disable plane detection
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);

        if(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
            config.setDepthMode(Config.DepthMode.AUTOMATIC);

        // Images to be detected by our AR need to be added in AugmentedImageDatabase
        // This is how database is created at runtime
        // You can also prebuild database in you computer and load it directly (see: https://developers.google.com/ar/develop/java/augmented-images/guide#database)

        database = new AugmentedImageDatabase(session);

        Bitmap matrixImage = BitmapFactory.decodeResource(getResources(), R.drawable.matrix);
        Bitmap rabbitImage = BitmapFactory.decodeResource(getResources(), R.drawable.rabbit);
        // Every image has to have its own unique String identifier
        database.addImage("matrix", matrixImage);
        database.addImage("rabbit", rabbitImage);

        config.setAugmentedImageDatabase(database);
    }

    public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
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

        // Hide plane indicating dots
        arSceneView.getPlaneRenderer().setVisible(false);
        // Disable the rendering of detected planes.
        arSceneView.getPlaneRenderer().setEnabled(false);

        // Check for image detection
        arSceneView.getScene().addOnUpdateListener(this::onUpdate);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

    private void loadModels() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/Video.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        //removing shadows for this Renderable
                        model.setShadowCaster(false);
                        model.setShadowReceiver(true);
                        activity.plainVideoModel = model;
                    }
                })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                            return null;
                        });
    }

    private void loadMaterials() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        Engine filamentEngine = EngineInstance.getEngine().getFilamentEngine();

        MaterialBuilder.init();
        MaterialBuilder materialBuilder = new MaterialBuilder()
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("External Video Material")
                .require(MaterialBuilder.VertexAttribute.UV0)
                .shading(MaterialBuilder.Shading.UNLIT)
                .doubleSided(true)
                .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT, MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
                .optimization(MaterialBuilder.Optimization.NONE);

        MaterialPackage plainVideoMaterialPackage = materialBuilder
                .blending(MaterialBuilder.BlendingMode.OPAQUE)
                .material("void material(inout MaterialInputs material) {\n" +
                        "    prepareMaterial(material);\n" +
                        "    material.baseColor = texture(materialParams_videoTexture, getUV0()).rgba;\n" +
                        "}\n")
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

    // Every time new image is processed by ARCore and ready, this method is called
    public void onUpdate(FrameTime frameTime) {
        // If there are both images already detected, for better CPU usage we do not need scan for them
        if (matrixDetected && rabbitDetected)
            return;

        Frame frame = arFragment.getArSceneView().getArFrame();
        try {
            // This is collection of all images from our AugmentedImageDatabase that are currently detected by ARCore in this session
            Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

            for (AugmentedImage image : augmentedImages) {
                if (image.getTrackingState() == TrackingState.TRACKING) {
                    arFragment.getPlaneDiscoveryController().hide();

                    // If matrix video haven't been placed yet and detected image has String identifier of "matrix"
                    if (!matrixDetected && image.getName().equals("matrix")) {
                        matrixDetected = true;
                        Toast.makeText(this, "Matrix tag detected", Toast.LENGTH_LONG).show();

                        // New AnchorNode placed to the detected tag and set it to the real size of the tag
                        // This will cause deformation if your AR tag has different aspect ratio than your video
                        AnchorNode anchorNode = new AnchorNode(image.createAnchor(image.getCenterPose()));
                        anchorNode.setWorldScale(new Vector3(image.getExtentX(), 1f, image.getExtentZ()));
                        arFragment.getArSceneView().getScene().addChild(anchorNode);

                        TransformableNode videoNode = new TransformableNode(arFragment.getTransformationSystem());
                        videoNode.setParent(anchorNode);
                        // For some reason it is shown upside down so this will rotate it correctly
                        videoNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 180f));

                        // Setting texture
                        ExternalTexture externalTexture = new ExternalTexture();
                        RenderableInstance renderableInstance = videoNode.setRenderable(plainVideoModel);
                        renderableInstance.setMaterial(plainVideoMaterial);

                        // Setting MediaPLayer
                        renderableInstance.getMaterial().setExternalTexture("videoTexture", externalTexture);
                        mediaPlayer = MediaPlayer.create(this, R.raw.matrix);
                        mediaPlayer.setLooping(true);
                        mediaPlayer.setSurface(externalTexture.getSurface());
                        mediaPlayer.start();
                    }
                    // If rabbit model haven't been placed yet and detected image has String identifier of "rabbit"
                    // This is also example of model loading and placing at runtime
                    if (!rabbitDetected && image.getName().equals("rabbit")) {
                        rabbitDetected = true;
                        Toast.makeText(this, "Rabbit tag detected", Toast.LENGTH_LONG).show();

                        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
                        ModelRenderable.builder()
                                .setSource(this, Uri.parse("models/Rabbit.glb"))
                                .setIsFilamentGltf(true)
                                .build()
                                .thenAccept(rabbitModel -> {
                                    MainActivity activity = weakActivity.get();
                                    if (activity != null) {

                                        // Setting anchor to the center of AR tag
                                        AnchorNode anchorNode = new AnchorNode(image.createAnchor(image.getCenterPose()));

                                        arFragment.getArSceneView().getScene().addChild(anchorNode);

                                        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
                                        modelNode.setParent(anchorNode);
                                        RenderableInstance renderableInstance = modelNode.setRenderable(rabbitModel);

                                        // Removing shadows
                                        renderableInstance.setShadowCaster(true);
                                        renderableInstance.setShadowReceiver(true);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}