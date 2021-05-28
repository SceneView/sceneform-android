package com.google.ar.sceneform.samples.videotexture;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.google.android.filament.ColorGrading;
import com.google.android.filament.Engine;
import com.google.android.filament.filamat.MaterialBuilder;
import com.google.android.filament.filamat.MaterialPackage;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.rendering.Color;
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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        BaseArFragment.OnTapArPlaneListener,
        ArFragment.OnViewCreatedListener {

    private ArFragment arFragment;
    private Renderable plainVideoModel;
    private Material plainVideoMaterial;
    private Renderable chromaKeyVideoModel;
    private Material chromaKeyVideoMaterial;
    private List<MediaPlayer> mediaPlayers = new ArrayList<>();

    private int mode = R.id.menuPlainVideo;

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
                arFragment = (ArFragment) fragment;
                arFragment.setOnTapArPlaneListener(MainActivity.this);
                arFragment.setOnViewCreatedListener(MainActivity.this);
            }
        });

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }

        loadModel();
        loadMaterials();
    }

    @Override
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.isChecked()) {
            item.setChecked(false);
        } else {
            item.setChecked(true);
        }
        this.mode = item.getItemId();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (MediaPlayer mediaPlayer : this.mediaPlayers) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

    public void loadModel() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/vertical_plane_1920x1080.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.plainVideoModel = model;
                    }
                })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                            return null;
                        });
        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/vertical_plane_1096x1656.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    MainActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.chromaKeyVideoModel = model;
                    }
                })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                            return null;
                        });
    }

    public void loadMaterials() {
        WeakReference<MainActivity> weakActivity = new WeakReference<>(this);
        Engine filamentEngine = EngineInstance.getEngine().getFilamentEngine();

        MaterialBuilder.init();
        MaterialBuilder materialBuilder = new MaterialBuilder()
                // By default, materials are generated only for DESKTOP. Since we're an Android
                // app, we set the platform to MOBILE.
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("Plain Video Material")
                .require(MaterialBuilder.VertexAttribute.UV0)
                // Defaults to UNLIT because it's the only emissive one
                .shading(MaterialBuilder.Shading.UNLIT)
                .doubleSided(true)
                .samplerParameter(MaterialBuilder.SamplerType.SAMPLER_EXTERNAL, MaterialBuilder.SamplerFormat.FLOAT
                        , MaterialBuilder.SamplerPrecision.DEFAULT, "videoTexture")
                .optimization(MaterialBuilder.Optimization.NONE);

        // When compiling more than one material variant, it is more efficient to pass an Engine
        // instance to reuse the Engine's job system
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

        MaterialPackage chromaKeyVideoMaterialPackage = materialBuilder
                .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "chromaKeyColor")
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                .material(
                        "vec3 desaturate(vec3 color, float amount) {\n" +
                                "    // Convert color to grayscale using Luma formula:\n" +
                                "    // https://en.wikipedia.org/wiki/Luma_%28video%29\n" +
                                "    vec3 gray = vec3(dot(vec3(0.2126, 0.7152, 0.0722), color));\n" +
                                "    return vec3(mix(color, gray, amount));\n" +
                                "}\n" +
                                "void material(inout MaterialInputs material) {\n" +
                                "    prepareMaterial(material);\n" +
                                "    vec4 color = texture(materialParams_videoTexture, getUV0()).rgba;\n" +
                                "    vec3 keyColor = materialParams.chromaKeyColor.rgb;\n" +
                                "    float threshold = 0.675;\n" +
                                "    float slope = 0.2;\n" +
                                "    float distance = abs(length(abs(keyColor - color.rgb)));\n" +
                                "    float edge0 = threshold * (1.0 - slope);\n" +
                                "    float alpha = smoothstep(edge0, threshold, distance);\n" +
                                "    color.rgb = desaturate(color.rgb, 1.0 - (alpha * alpha * alpha));\n" +
                                "\n" +
                                "    material.baseColor.a = alpha;\n" +
                                "    material.baseColor.rgb = inverseTonemapSRGB(color.rgb);\n" +
                                "    material.baseColor.rgb *= material.baseColor.a;\n" +
                                "}\n")
                .build();
        if (chromaKeyVideoMaterialPackage.isValid()) {
            ByteBuffer buffer = chromaKeyVideoMaterialPackage.getBuffer();
            Material.builder()
                    .setSource(buffer)
                    .build()
                    .thenAccept(material -> {
                        MainActivity activity = weakActivity.get();
                        if (activity != null) {
                            activity.chromaKeyVideoMaterial = material;
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
    public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if ((mode == R.id.menuPlainVideo && (plainVideoModel == null || plainVideoMaterial == null)) ||
                (mode == R.id.menuChromaKeyVideo && (chromaKeyVideoModel == null || chromaKeyVideoMaterial == null))) {
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

        ExternalTexture externalTexture = new ExternalTexture();
        MediaPlayer mediaPlayer;
        RenderableInstance renderableInstance;
        if (mode == R.id.menuPlainVideo) {
            modelNode.setRenderable(plainVideoModel);
            renderableInstance = modelNode.getRenderableInstance();
            renderableInstance.setMaterial(plainVideoMaterial);
            mediaPlayer = MediaPlayer.create(this, R.raw.sintel);
        } else {
            modelNode.setRenderable(chromaKeyVideoModel);
            renderableInstance = modelNode.getRenderableInstance();
            renderableInstance.setMaterial(chromaKeyVideoMaterial);
            renderableInstance.getMaterial().setFloat4("chromaKeyColor", new Color(0.1843f, 1.0f, 0.098f));
            mediaPlayer = MediaPlayer.create(this, R.raw.lion);
        }
        modelNode.select();

        renderableInstance.getMaterial().setExternalTexture("videoTexture", externalTexture);
        mediaPlayer.setLooping(true);
        mediaPlayer.setSurface(externalTexture.getSurface());
        mediaPlayer.start();
        mediaPlayers.add(mediaPlayer);
    }
}
