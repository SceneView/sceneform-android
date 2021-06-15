package com.google.ar.sceneform.rendering;

import android.media.Image;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.IndexBuffer.Builder.IndexType;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Scene;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.VertexBuffer.Builder;
import com.google.android.filament.VertexBuffer.VertexAttribute;
import com.google.android.filament.utils.Mat4;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Displays the Camera stream using Filament.
 *
 * @hide Note: The class is hidden because it should only be used by the Filament Renderer and does
 * not expose a user facing API.
 */
@SuppressWarnings("AndroidApiChecker") // CompletableFuture
public class CameraStream {
    public static final String MATERIAL_CAMERA_TEXTURE = "cameraTexture";
    public static final String DEPTH_TEXTURE = "depthTexture";

    private static final String TAG = CameraStream.class.getSimpleName();

    private static final int VERTEX_COUNT = 3;
    private static final int POSITION_BUFFER_INDEX = 0;
    private static final float[] CAMERA_VERTICES =
            new float[]{
                    -1.0f, 1.0f,
                    1.0f, -1.0f,
                    -3.0f, 1.0f,
                    3.0f, 1.0f,
                    1.0f};
    private static final int UV_BUFFER_INDEX = 1;
    private static final float[] CAMERA_UVS = new float[]{
            0.0f, 0.0f,
            0.0f, 2.0f,
            2.0f, 0.0f};
    private static final short[] INDICES = new short[]{0, 1, 2};


    private static final int FLOAT_SIZE_IN_BYTES = Float.SIZE / 8;
    private static final int UNINITIALIZED_FILAMENT_RENDERABLE = -1;

    private final Scene scene;
    private final int cameraTextureId;
    private final IndexBuffer cameraIndexBuffer;
    private final VertexBuffer cameraVertexBuffer;
    private final FloatBuffer cameraUvCoords;
    private final FloatBuffer transformedCameraUvCoords;
    private final IEngine engine;
    private int cameraStreamRenderable = UNINITIALIZED_FILAMENT_RENDERABLE;
    private DepthMode depthMode = DepthMode.NO_DEPTH;
    private DepthModeUsage depthModeUsage = DepthModeUsage.DEPTH_MODE_DISABLED;

    @Nullable private ExternalTexture cameraTexture;
    @Nullable private DepthTexture depthTexture;

    @Nullable private Material defaultCameraMaterial = null;
    @Nullable private Material cameraMaterial = null;
    @Nullable private Material occlusionCameraMaterial = null;

    private int renderablePriority = Renderable.RENDER_PRIORITY_LAST;

    private boolean isTextureInitialized = false;

    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored", "initialization"})
    public CameraStream(int cameraTextureId, Renderer renderer) {
        scene = renderer.getFilamentScene();
        this.cameraTextureId = cameraTextureId;

        engine = EngineInstance.getEngine();

        // INDEXBUFFER
        // create screen quad geometry to camera stream to
        ShortBuffer indexBufferData = ShortBuffer.allocate(INDICES.length);
        indexBufferData.put(INDICES);

        final int indexCount = indexBufferData.capacity();
        cameraIndexBuffer = createIndexBuffer(indexCount);

        indexBufferData.rewind();
        Preconditions.checkNotNull(cameraIndexBuffer)
                .setBuffer(engine.getFilamentEngine(), indexBufferData);


        // Note: ARCore expects the UV buffers to be direct or will assert in transformDisplayUvCoords.
        cameraUvCoords = createCameraUVBuffer();
        transformedCameraUvCoords = createCameraUVBuffer();


        // VERTEXTBUFFER
        FloatBuffer vertexBufferData = FloatBuffer.allocate(CAMERA_VERTICES.length);
        vertexBufferData.put(CAMERA_VERTICES);

        cameraVertexBuffer = createVertexBuffer();

        vertexBufferData.rewind();
        Preconditions.checkNotNull(cameraVertexBuffer)
                .setBufferAt(engine.getFilamentEngine(), POSITION_BUFFER_INDEX, vertexBufferData);

        adjustCameraUvsForOpenGL();
        cameraVertexBuffer.setBufferAt(
                engine.getFilamentEngine(), UV_BUFFER_INDEX, transformedCameraUvCoords);

        setupStandardCameraMaterial(renderer);
        setupOcclusionCameraMaterial(renderer);
    }

    /**
     * <pre>
     *     The Session holds the information if the
     *     DepthMode is configured or not. Based on
     *     that the result different materials and textures are
     *     used for the camera.
     * </pre>
     *
     * @param session {@link Session}
     */
    public void checkIfDepthEnabled(Session session) {
        depthMode = DepthMode.NO_DEPTH;

        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
            if (session.getConfig().getDepthMode() == Config.DepthMode.AUTOMATIC) {
                depthMode = DepthMode.DEPTH;
            }

        if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY))
            if (session.getConfig().getDepthMode() == Config.DepthMode.RAW_DEPTH_ONLY) {
                depthMode = DepthMode.RAW_DEPTH;
            }
    }

    private FloatBuffer createCameraUVBuffer() {
        FloatBuffer buffer =
                ByteBuffer.allocateDirect(CAMERA_UVS.length * FLOAT_SIZE_IN_BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        buffer.put(CAMERA_UVS);
        buffer.rewind();

        return buffer;
    }

    private IndexBuffer createIndexBuffer(int indexCount) {
        return new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexType.USHORT)
                .build(engine.getFilamentEngine());
    }

    private VertexBuffer createVertexBuffer() {
        return new Builder()
                .vertexCount(VERTEX_COUNT)
                .bufferCount(2)
                .attribute(
                        VertexAttribute.POSITION,
                        POSITION_BUFFER_INDEX,
                        VertexBuffer.AttributeType.FLOAT3,
                        0,
                        (CAMERA_VERTICES.length / VERTEX_COUNT) * FLOAT_SIZE_IN_BYTES)
                .attribute(
                        VertexAttribute.UV0,
                        UV_BUFFER_INDEX,
                        VertexBuffer.AttributeType.FLOAT2,
                        0,
                        (CAMERA_UVS.length / VERTEX_COUNT) * FLOAT_SIZE_IN_BYTES)
                .build(engine.getFilamentEngine());
    }

    public boolean isTextureInitialized() {
        return isTextureInitialized;
    }

    public void initializeTexture(Frame frame) {
        if (isTextureInitialized()) {
            return;
        }

        Camera arCamera = frame.getCamera();
        CameraIntrinsics intrinsics = arCamera.getTextureIntrinsics();
        int[] dimensions = intrinsics.getImageDimensions();

        // External Camera Texture
        cameraTexture = new ExternalTexture(
                cameraTextureId,
                dimensions[0],
                dimensions[1]);

        isTextureInitialized = true;

        if (depthModeUsage == DepthModeUsage.DEPTH_MODE_ENABLED && (
                depthMode == DepthMode.DEPTH ||
                depthMode == DepthMode.RAW_DEPTH)) {
            if (occlusionCameraMaterial != null) {
                Log.d("CameraStream", "setOcclusionMaterial from initializeTexture");
                setOcclusionMaterial(occlusionCameraMaterial);
                initOrUpdateRenderableMaterial(occlusionCameraMaterial);
            }
        } else {
            if (cameraMaterial != null) {
                Log.d("CameraStream", "setCameraMaterial from initializeTexture");
                setCameraMaterial(cameraMaterial);
                initOrUpdateRenderableMaterial(cameraMaterial);
            }
        }
    }


    void setupStandardCameraMaterial(Renderer renderer) {
        CompletableFuture<Material> materialFuture =
                Material.builder()
                        .setSource(
                                renderer.getContext(),
                                RenderingResources.GetSceneformResource(
                                        renderer.getContext(),
                                        RenderingResources.Resource.CAMERA_MATERIAL))
                        .build();

        materialFuture
                .thenAccept(
                        material -> {
                            float[] uvTransform = Mat4.Companion.identity().toFloatArray();
                            material
                                    .getFilamentMaterialInstance()
                                    .setParameter(
                                            "uvTransform",
                                            MaterialInstance.FloatElement.FLOAT4,
                                            uvTransform,
                                            0,
                                            4);

                            // Only set the camera material if it hasn't already been set to a custom material.
                            /*if (cameraMaterial == null) {
                                setCameraMaterial(material);
                            }*/
                            cameraMaterial = material;
/*
                            // The ExternalTexture can't be created until we receive the first AR Core Frame so that we
                            // can access the width and height of the camera texture. Return early if the ExternalTexture
                            // hasn't been created yet so we don't start rendering until we have a valid texture. This will
                            // be called again when the ExternalTexture is created.
                            if (!isTextureInitialized()) {
                                return;
                            }

                            Log.d("CameraStream", "setupStandardCameraMaterial passed !isTextureInitialized() call");

                            cameraMaterial.setExternalTexture(
                                    MATERIAL_CAMERA_TEXTURE,
                                    Preconditions.checkNotNull(cameraTexture));

                            initOrUpdateRenderableMaterial(cameraMaterial);*/
                        })
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load camera stream materials.", throwable);
                            return null;
                        });
    }

    void setupOcclusionCameraMaterial(Renderer renderer) {
        CompletableFuture<Material> materialFuture =
                Material.builder()
                        .setSource(
                                renderer.getContext(),
                                RenderingResources.GetSceneformResource(
                                        renderer.getContext(),
                                        RenderingResources.Resource.OCCLUSION_CAMERA_MATERIAL))
                        .build();
        materialFuture
                .thenAccept(
                        material -> {
                            float[] uvTransform = Mat4.Companion.identity().toFloatArray();
                            material
                                    .getFilamentMaterialInstance()
                                    .setParameter(
                                            "uvTransform",
                                            MaterialInstance.FloatElement.FLOAT4,
                                            uvTransform,
                                            0,
                                            4);

                            // Only set the camera material if it hasn't already been set to a custom material.
                            if (occlusionCameraMaterial == null) {
                                setOcclusionMaterial(material);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load camera stream materials.", throwable);
                            return null;
                        });
    }


    /**
     * <pre>
     *      Update the DepthTexture.
     * </pre>
     *
     * @param depthImage {@link Image}
     */
    public void recalculateOcclusion(Image depthImage) {
        if (depthTexture == null) {
            // The Depth Texture to realize the occlusion of virtual objects.
            depthTexture = new DepthTexture(
                    depthImage.getWidth(),
                    depthImage.getHeight());

            occlusionCameraMaterial.setDepthTexture(
                    DEPTH_TEXTURE,
                    depthTexture);
        }

        if (!isTextureInitialized || depthImage == null)
            return;
        depthTexture.updateDepthTexture(depthImage);
    }


    public void recalculateCameraUvs(Frame frame) {
        FloatBuffer cameraUvCoords = this.cameraUvCoords;
        FloatBuffer transformedCameraUvCoords = this.transformedCameraUvCoords;
        VertexBuffer cameraVertexBuffer = this.cameraVertexBuffer;
        frame.transformDisplayUvCoords(cameraUvCoords, transformedCameraUvCoords);
        adjustCameraUvsForOpenGL();
        cameraVertexBuffer.setBufferAt(
                engine.getFilamentEngine(), UV_BUFFER_INDEX, transformedCameraUvCoords);
    }


    void setCameraMaterial(Material material) {
        Log.d("CameraStream", "setCameraMaterial");
        cameraMaterial = material;
        if (cameraMaterial == null)
            return;

        // The ExternalTexture can't be created until we receive the first AR Core Frame so that we
        // can access the width and height of the camera texture. Return early if the ExternalTexture
        // hasn't been created yet so we don't start rendering until we have a valid texture. This will
        // be called again when the ExternalTexture is created.
        if (!isTextureInitialized()) {
            return;
        }

        Log.d("CameraStream", "setCameraMaterial passed !isTextureInitialized() call");

        cameraMaterial.setExternalTexture(
                MATERIAL_CAMERA_TEXTURE,
                Preconditions.checkNotNull(cameraTexture));

        //initOrUpdateRenderableMaterial(cameraMaterial);
        /*if (cameraStreamRenderable == UNINITIALIZED_FILAMENT_RENDERABLE) {
            Log.d("CameraStream", "call to initializeFilamentRenderable");
            initializeFilamentRenderable(cameraMaterial);
        } else {
            Log.d("CameraStream", "Update with existing Material");
            RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
            int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
            renderableManager.setMaterialInstanceAt(
                    renderableInstance, 0, material.getFilamentMaterialInstance());
        }*/
    }

    void setOcclusionMaterial(Material material) {
        Log.d("CameraStream", "setOcclusionMaterial");
        occlusionCameraMaterial = material;
        if (occlusionCameraMaterial == null)
            return;

        // The ExternalTexture can't be created until we receive the first AR Core Frame so that we
        // can access the width and height of the camera texture. Return early if the ExternalTexture
        // hasn't been created yet so we don't start rendering until we have a valid texture. This will
        // be called again when the ExternalTexture is created.
        if (!isTextureInitialized()) {
            return;
        }

        Log.d("CameraStream", "setOcclusionMaterial passed !isTextureInitialized() call");

        occlusionCameraMaterial.setExternalTexture(
                MATERIAL_CAMERA_TEXTURE,
                Preconditions.checkNotNull(cameraTexture));

        /*if (cameraStreamRenderable == UNINITIALIZED_FILAMENT_RENDERABLE) {
            Log.d("CameraStream", "call to initializeFilamentRenderable");
            initializeFilamentRenderable(occlusionCameraMaterial);
        } else {
            Log.d("CameraStream", "Update with existing Material");
            RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
            int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
            renderableManager.setMaterialInstanceAt(
                    renderableInstance, 0, material.getFilamentMaterialInstance());
        }*/
    }

    private void initOrUpdateRenderableMaterial(Material material) {
        if (cameraStreamRenderable == UNINITIALIZED_FILAMENT_RENDERABLE) {
            Log.d("CameraStream", "call to initializeFilamentRenderable");
            initializeFilamentRenderable(material);
        } else {
            Log.d("CameraStream", "Update with existing Material");
            RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
            int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
            renderableManager.setMaterialInstanceAt(
                    renderableInstance, 0, material.getFilamentMaterialInstance());
        }
    }

    public int getRenderPriority() {
        return renderablePriority;
    }

    public void setRenderPriority(int priority) {
        renderablePriority = priority;
        if (cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE) {
            RenderableManager renderableManager = EngineInstance.getEngine().getRenderableManager();
            int renderableInstance = renderableManager.getInstance(cameraStreamRenderable);
            renderableManager.setPriority(renderableInstance, renderablePriority);
        }
    }

    private void adjustCameraUvsForOpenGL() {
        // Correct for vertical coordinates to match OpenGL
        for (int i = 1; i < VERTEX_COUNT * 2; i += 2) {
            transformedCameraUvCoords.put(i, 1.0f - transformedCameraUvCoords.get(i));
        }
        // Correct for horizontal coordinates to match OpenGL
        // Only needed for the RealityCore Vertices, UVs and Indices definition
        /*for (int i = 0; i < VERTEX_COUNT * 2; i += 2) {
            transformedCameraUvCoords.put(i, 1.0f - transformedCameraUvCoords.get(i));
        }*/
    }

    private void initializeFilamentRenderable(Material material) {
        // create entity id
        cameraStreamRenderable = EntityManager.get().create();

        // create the quad renderable (leave off the aabb)
        RenderableManager.Builder builder = new RenderableManager.Builder(1);
        builder
                .castShadows(false)
                .receiveShadows(false)
                .culling(false)
                // Always draw the camera feed last to avoid overdraw
                .priority(renderablePriority)
                .geometry(
                        0, RenderableManager.PrimitiveType.TRIANGLES, cameraVertexBuffer, cameraIndexBuffer)
                .material(0, Preconditions.checkNotNull(material).getFilamentMaterialInstance())
                .build(EngineInstance.getEngine().getFilamentEngine(), cameraStreamRenderable);

        // add to the scene
        scene.addEntity(cameraStreamRenderable);

        ResourceManager.getInstance()
                .getCameraStreamCleanupRegistry()
                .register(
                        this,
                        new CleanupCallback(
                                scene, cameraStreamRenderable, cameraIndexBuffer, cameraVertexBuffer));
    }

    public DepthMode getDepthMode() {
        return depthMode;
    }

    public DepthModeUsage getDepthModeUsage() {
        return depthModeUsage;
    }

    public void setDepthModeUsage(DepthModeUsage depthModeUsage) {
        Log.d("CameraStream", "setDepthModeUsage " + depthModeUsage.name());

        if (depthModeUsage == DepthModeUsage.DEPTH_MODE_DISABLED) {
            if (cameraMaterial != null) {
                Log.d("CameraStream", "Set Standard Material");
                //setCameraMaterial(cameraMaterial);
                initOrUpdateRenderableMaterial(cameraMaterial);
            }
        } else if (depthModeUsage == DepthModeUsage.DEPTH_MODE_ENABLED) {
            if (occlusionCameraMaterial != null) {
                Log.d("CameraStream", "Set Occlusion Material");
                //setOcclusionMaterial(occlusionCameraMaterial);
                initOrUpdateRenderableMaterial(occlusionCameraMaterial);
            }
        }

        this.depthModeUsage = depthModeUsage;
    }

    public enum DepthMode {
        NO_DEPTH,
        DEPTH,
        RAW_DEPTH
    }

    public enum DepthModeUsage {
        DEPTH_MODE_ENABLED,
        DEPTH_MODE_DISABLED
    }

    /**
     * Cleanup filament objects after garbage collection
     */
    private static final class CleanupCallback implements Runnable {
        private final Scene scene;
        private final int cameraStreamRenderable;
        private final IndexBuffer cameraIndexBuffer;
        private final VertexBuffer cameraVertexBuffer;

        CleanupCallback(
                Scene scene,
                int cameraStreamRenderable,
                IndexBuffer cameraIndexBuffer,
                VertexBuffer cameraVertexBuffer) {
            this.scene = scene;
            this.cameraStreamRenderable = cameraStreamRenderable;
            this.cameraIndexBuffer = cameraIndexBuffer;
            this.cameraVertexBuffer = cameraVertexBuffer;
        }

        @Override
        public void run() {
            AndroidPreconditions.checkUiThread();

            IEngine engine = EngineInstance.getEngine();
            if (engine == null && !engine.isValid()) {
                return;
            }

            if (cameraStreamRenderable != UNINITIALIZED_FILAMENT_RENDERABLE) {
                scene.remove(cameraStreamRenderable);
            }

            engine.destroyIndexBuffer(cameraIndexBuffer);
            engine.destroyVertexBuffer(cameraVertexBuffer);
        }
    }
}
