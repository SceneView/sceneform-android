package com.google.ar.sceneform.rendering;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.math.Vector3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Control rendering of ARCore planes.
 *
 * <p>Used to visualize detected planes and to control whether Renderables cast shadows on them.
 */
@SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"}) // CompletableFuture
public class PlaneRenderer {
    /**
     * Material parameter that controls what texture is being used when rendering the planes.
     */
    public static final String MATERIAL_TEXTURE = "texture";
    /**
     * Float2 material parameter to control the X/Y scaling of the texture's UV coordinates. Can be
     * used to adjust for the texture's aspect ratio and control the frequency of tiling.
     */
    public static final String MATERIAL_UV_SCALE = "uvScale";
    /**
     * Float3 material parameter to control the RGB tint of the plane.
     */
    public static final String MATERIAL_COLOR = "color";
    /**
     * Float material parameter to control the radius of the spotlight.
     */
    public static final String MATERIAL_SPOTLIGHT_RADIUS = "radius";
    private static final String TAG = PlaneRenderer.class.getSimpleName();
    /**
     * Float3 material parameter to control the grid visualization point.
     */
    private static final String MATERIAL_SPOTLIGHT_FOCUS_POINT = "focusPoint";

    /**
     * Used to control the UV Scale for the default texture.
     */
    private static final float BASE_UV_SCALE = 8.0f;

    private static final float DEFAULT_TEXTURE_WIDTH = 293;
    private static final float DEFAULT_TEXTURE_HEIGHT = 513;

    private static final float SPOTLIGHT_RADIUS = .5f;

    private final Renderer renderer;

    private final Map<Plane, PlaneVisualizer> visualizerMap = new HashMap<>();
    // Per-plane overrides
    private final Map<Plane, Material> materialOverrides = new HashMap<>();
    private CompletableFuture<Material> planeMaterialFuture;
    private Material shadowMaterial;
    private boolean isEnabled = true;
    private boolean isVisible = true;
    private boolean isShadowReceiver = true;
    private PlaneRendererMode planeRendererMode = PlaneRendererMode.RENDER_ALL;
    // Distance from the camera to last plane hit, default value is 4 meters (standing height).
    private float lastPlaneHitDistance = 4.0f;

    /**
     * @hide PlaneRenderer is constructed in a different package, but not part of external API.
     */
    @SuppressWarnings("initialization")
    public PlaneRenderer(Renderer renderer) {
        this.renderer = renderer;

        loadPlaneMaterial();
        loadShadowMaterial();
    }

    /**
     * Check if the plane renderer is enabled.
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Enable/disable the plane renderer.
     */
    public void setEnabled(boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;

            for (PlaneVisualizer visualizer : visualizerMap.values()) {
                visualizer.setEnabled(isEnabled);
            }
        }
    }

    /**
     * Return true if Renderables in the scene cast shadows onto the planes.
     */
    public boolean isShadowReceiver() {
        return isShadowReceiver;
    }

    /**
     * Control whether Renderables in the scene should cast shadows onto the planes.
     *
     * <p>If false - no planes receive shadows, regardless of the per-plane setting.
     */
    public void setShadowReceiver(boolean shadowReceiver) {
        if (isShadowReceiver != shadowReceiver) {
            isShadowReceiver = shadowReceiver;

            for (PlaneVisualizer visualizer : visualizerMap.values()) {
                visualizer.setShadowReceiver(isShadowReceiver);
            }
        }
    }

    /**
     * Return true if plane visualization is visible.
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * Control visibility of plane visualization.
     *
     * <p>If false - no planes are drawn. Note that shadow visibility is independent of plane
     * visibility.
     */
    public void setVisible(boolean visible) {
        if (isVisible != visible) {
            isVisible = visible;

            for (PlaneVisualizer visualizer : visualizerMap.values()) {
                visualizer.setVisible(isVisible);
            }
        }
    }

    /**
     * Returns default material instance used to render the planes.
     */
    public CompletableFuture<Material> getMaterial() {
        return planeMaterialFuture;
    }

    /**
     * <pre>
     *     Return the used {@link PlaneRendererMode}. Two options are available,
     *     <code>RENDER_ALL</code> and <code>RENDER_TOP_MOST</code>. See
     *     {@link PlaneRendererMode} and
     *     {@link #setPlaneRendererMode(PlaneRendererMode)} for more information.
     * </pre>
     *
     * @return {@link PlaneRendererMode}
     */
    public PlaneRendererMode getPlaneRendererMode() {
        return planeRendererMode;
    }

    /**
     * <pre>
     *     Set here how tracked planes should be visualized on the screen. Two options are available,
     *     <code>RENDER_ALL</code> and <code>RENDER_TOP_MOST</code>.
     *     To see all tracked planes which are visible to the camera set the PlaneRendererMode to
     *     <code>RENDER_ALL</code>. This mode eats up quite a few resources and should only be set
     *     with care. To optimize the rendering set the mode to <code>RENDER_TOP_MOST</code>.
     *     In that case only the top most plane visible to a camera is rendered on the screen.
     *     Especially on weaker smartphone models this improves the overall performance.
     *
     *     The default mode is <code>RENDER_TOP_MOST</code>
     * </pre>
     *
     * @param planeRendererMode {@link PlaneRendererMode}
     */
    public void setPlaneRendererMode(PlaneRendererMode planeRendererMode) {
        this.planeRendererMode = planeRendererMode;
    }

    /**
     * @hide PlaneRenderer is updated in a different package, but not part of external API.
     */
    public void update(Frame frame, Collection<Plane> updatedPlanes, int viewWidth, int viewHeight) {
        // Do a hittest on the current frame. The result is used to calculate
        // a focusPoint and to render the top most plane Trackable if
        // planeRendererMode is set to RENDER_TOP_MOST.
        HitResult hitResult = getHitResult(frame, viewWidth, viewHeight);
        // Calculate the focusPoint. It is used to determine the position of
        // the visualized grid.
        Vector3 focusPoint = getFocusPoint(frame, hitResult);

        @SuppressWarnings("nullness")
        @Nullable
        Material planeMaterial = planeMaterialFuture.getNow(null);
        if (planeMaterial != null) {
            planeMaterial.setFloat3(MATERIAL_SPOTLIGHT_FOCUS_POINT, focusPoint);
            planeMaterial.setFloat(MATERIAL_SPOTLIGHT_RADIUS, SPOTLIGHT_RADIUS);
        }

        if (planeRendererMode == PlaneRendererMode.RENDER_ALL && hitResult != null) {
            renderAll(updatedPlanes, planeMaterial);
        } else if (planeRendererMode == PlaneRendererMode.RENDER_TOP_MOST && hitResult != null) {
            Plane topMostPlane = (Plane) hitResult.getTrackable();
            Optional.ofNullable(topMostPlane)
                    .ifPresent(plane -> renderPlane(plane, planeMaterial));
        }

        // Check for not tracking Plane-Trackables and remove them.
        cleanupOldPlaneVisualizer();
    }

    /**
     * <pre>
     *     Render all tracked Planes
     * </pre>
     *
     * @param updatedPlanes {@link Collection}<{@link Plane}>
     * @param planeMaterial {@link Material}
     */
    private void renderAll(
            Collection<Plane> updatedPlanes,
            Material planeMaterial
    ) {
        for (Plane plane : updatedPlanes) {
            renderPlane(plane, planeMaterial);
        }
    }

    /**
     * <pre>
     *     This function is responsible to update the rendering
     *     of a {@link PlaneVisualizer}. If for the given {@link Plane}
     *     no {@link PlaneVisualizer} exists, create a new one and add
     *     it to the <code>visualizerMap</code>.
     * </pre>
     *
     * @param plane         {@link Plane}
     * @param planeMaterial {@link Material}
     */
    private void renderPlane(Plane plane, Material planeMaterial) {
        PlaneVisualizer planeVisualizer;

        // Find the plane visualizer if it already exists.
        // If not, create a new plane visualizer for this plane.
        if (visualizerMap.containsKey(plane)) {
            planeVisualizer = visualizerMap.get(plane);
        } else {
            planeVisualizer = new PlaneVisualizer(plane, renderer);
            Material overrideMaterial = materialOverrides.get(plane);
            if (overrideMaterial != null) {
                planeVisualizer.setPlaneMaterial(overrideMaterial);
            } else if (planeMaterial != null) {
                planeVisualizer.setPlaneMaterial(planeMaterial);
            }
            if (shadowMaterial != null) {
                planeVisualizer.setShadowMaterial(shadowMaterial);
            }
            planeVisualizer.setShadowReceiver(isShadowReceiver);
            planeVisualizer.setVisible(isVisible);
            planeVisualizer.setEnabled(isEnabled);
            visualizerMap.put(plane, planeVisualizer);
        }

        // Update the plane visualizer.
        Optional.ofNullable(planeVisualizer)
                .ifPresent(PlaneVisualizer::updatePlane);
    }

    /**
     * <pre>
     *     Remove plane visualizers for old planes that are no longer tracking.
     *     Update the material parameters for all remaining planes.
     * </pre>
     */
    private void cleanupOldPlaneVisualizer() {
        Iterator<Map.Entry<Plane, PlaneVisualizer>> iter = visualizerMap.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<Plane, PlaneVisualizer> entry = iter.next();
            Plane plane = entry.getKey();
            PlaneVisualizer planeVisualizer = entry.getValue();

            // If this plane was subsumed by another plane or it has permanently stopped tracking,
            // remove it.
            if (plane.getSubsumedBy() != null || plane.getTrackingState() == TrackingState.STOPPED) {
                planeVisualizer.release();
                iter.remove();
                continue;
            }
        }
    }


    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    private void loadShadowMaterial() {
        Material.builder()
                .setSource(
                        renderer.getContext(),
                        RenderingResources.GetSceneformResource(
                                renderer.getContext(), RenderingResources.Resource.PLANE_SHADOW_MATERIAL))
                .build()
                .thenAccept(
                        material -> {
                            shadowMaterial = material;
                            for (PlaneVisualizer visualizer : visualizerMap.values()) {
                                visualizer.setShadowMaterial(shadowMaterial);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load plane shadow material.", throwable);
                            return null;
                        });
    }

    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    private void loadPlaneMaterial() {
        Texture.Sampler sampler =
                Texture.Sampler.builder()
                        .setMinMagFilter(Texture.Sampler.MagFilter.LINEAR)
                        .setWrapMode(Texture.Sampler.WrapMode.REPEAT)
                        .build();

        CompletableFuture<Texture> textureFuture =
                Texture.builder()
                        .setSource(
                                renderer.getContext(),
                                RenderingResources.GetSceneformResource(
                                        renderer.getContext(), RenderingResources.Resource.PLANE))
                        .setSampler(sampler)
                        .build();

        planeMaterialFuture =
                Material.builder()
                        .setSource(
                                renderer.getContext(),
                                RenderingResources.GetSceneformResource(
                                        renderer.getContext(), RenderingResources.Resource.PLANE_MATERIAL))
                        .build()
                        .thenCombine(
                                textureFuture,
                                (material, texture) -> {
                                    material.setTexture(MATERIAL_TEXTURE, texture);
                                    material.setFloat3(MATERIAL_COLOR, 1.0f, 1.0f, 1.0f);

                                    // TODO: Don't use hardcoded width and height... Need api for getting
                                    // width and
                                    // height from the Texture class.
                                    float widthToHeightRatio = DEFAULT_TEXTURE_WIDTH / DEFAULT_TEXTURE_HEIGHT;
                                    float scaleX = BASE_UV_SCALE;
                                    float scaleY = scaleX * widthToHeightRatio;
                                    material.setFloat2(MATERIAL_UV_SCALE, scaleX, scaleY);

                                    for (Map.Entry<Plane, PlaneVisualizer> entry : visualizerMap.entrySet()) {
                                        if (!materialOverrides.containsKey(entry.getKey())) {
                                            entry.getValue().setPlaneMaterial(material);
                                        }
                                    }
                                    return material;
                                });
    }

    /**
     * <pre>
     *    Cast a ray from the centre of the screen onto the scene and check
     *    if any plane is hit. The result is a {@link HitResult} with information
     *    about the hit position as a {@link Pose} and the trackable which got hit.
     * </pre>
     *
     * @param frame  {@link Frame}
     * @param width  int
     * @param height int
     * @return {@link HitResult}
     */
    @Nullable
    private HitResult getHitResult(Frame frame, int width, int height) {
        // If we hit a plane, return the hit point.
        List<HitResult> hits = frame.hitTest(width / 2f, height / 2f);
        if (hits != null && !hits.isEmpty()) {
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                Pose hitPose = hit.getHitPose();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitPose)) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * <pre>
     *     Calculate the FocusPoint based on a {@link HitResult} on the current {@link Frame}.
     *     The FocusPoint is used to determine the position of the visualized plane.
     *     If the {@link HitResult} is null, we use the last known distance of the camera to
     *     the last hit plane.
     * </pre>
     *
     * @param frame {@link Frame}
     * @param hit   {@link HitResult}
     * @return {@link Vector3}
     */
    private Vector3 getFocusPoint(Frame frame, HitResult hit) {
        if (hit != null) {
            Pose hitPose = hit.getHitPose();
            lastPlaneHitDistance = hit.getDistance();
            return new Vector3(hitPose.tx(), hitPose.ty(), hitPose.tz());
        }

        // If we didn't hit anything, project a point in front of the camera so that the spotlight
        // rolls off the edge smoothly.
        Pose cameraPose = frame.getCamera().getPose();
        Vector3 cameraPosition = new Vector3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz());
        float[] zAxis = cameraPose.getZAxis();
        Vector3 backwards = new Vector3(zAxis[0], zAxis[1], zAxis[2]);

        return Vector3.add(cameraPosition, backwards.scaled(-lastPlaneHitDistance));
    }


    /**
     * <pre>
     *     Use this enum to configure the Plane Rendering.
     *
     *     For performance reasons use <code>RENDER_TOP_MOST</code>.
     * </pre>
     */
    public enum PlaneRendererMode {
        /**
         * Render all possible {@link Plane}s which are visible to the camera.
         */
        RENDER_ALL,
        /**
         * Render only the top most {@link Plane} which is visible to the camera.
         */
        RENDER_TOP_MOST
    }
}
