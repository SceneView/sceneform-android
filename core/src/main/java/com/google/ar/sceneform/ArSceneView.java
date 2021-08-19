package com.google.ar.sceneform;

import android.content.Context;
import android.media.Image;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig.FacingDirection;
import com.google.ar.core.Config;
import com.google.ar.core.Config.LightEstimationMode;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.DeadlineExceededException;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.CameraStream;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.EnvironmentalHdrLightEstimate;
import com.google.ar.sceneform.rendering.GLHelper;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.rendering.ThreadPools;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A SurfaceView that integrates with ARCore and renders a scene.
 */
@SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
public class ArSceneView extends SceneView {
    private static final String TAG = ArSceneView.class.getSimpleName();
    private static final String REPORTED_ENGINE_TYPE = "Sceneform";
    private static final float DEFAULT_PIXEL_INTENSITY = 1.0f;
    private static final Color DEFAULT_COLOR_CORRECTION = new Color(1, 1, 1);

    /**
     * When the camera has moved this distance, we create a new anchor to which we attach the Hdr
     * Lighting scene.
     */
    private static final float RECREATE_LIGHTING_ANCHOR_DISTANCE = 0.5f;
    private final Color lastValidColorCorrection = new Color(DEFAULT_COLOR_CORRECTION);
    private final float[] colorCorrectionPixelIntensity = new float[4];
    // pauseResumeTask is modified on the main thread only.  It may be completed on background
    // threads however.
    private final SequentialTask pauseResumeTask = new SequentialTask();
    private int cameraTextureId;
    @Nullable
    private Session session;
    @Nullable
    private Config sessionConfig;
    private AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    @Nullable
    private Frame currentFrame;
    private Long currentFrameTimestamp = 0L;
    private Collection<Trackable> allTrackables = new ArrayList<>();
    private Collection<Trackable> updatedTrackables = new ArrayList<>();
    private Display display;
    private CameraStream cameraStream;
    private PlaneRenderer planeRenderer;
    private boolean lightEstimationEnabled = true;
    private boolean isLightDirectionUpdateEnabled = true;
    @Nullable
    private Consumer<EnvironmentalHdrLightEstimate> onNextHdrLightingEstimate = null;
    private float lastValidPixelIntensity = DEFAULT_PIXEL_INTENSITY;
    @Nullable
    private Anchor lastValidEnvironmentalHdrAnchor;
    @Nullable
    private float[] lastValidEnvironmentalHdrAmbientSphericalHarmonics;
    @Nullable
    private float[] lastValidEnvironmentalHdrMainLightDirection;
    @Nullable
    private float[] lastValidEnvironmentalHdrMainLightIntensity;
    @Nullable
    private OnSessionConfigChangeListener onSessionConfigChangeListener;

    /**
     * Constructs a ArSceneView object and binds it to an Android Context.
     *
     * <p>In order to have rendering work correctly, {@link #setSession(Session)} must be called.
     *
     * @param context the Android Context to use
     * @see #ArSceneView(Context, AttributeSet)
     */
    public ArSceneView(Context context) {
        super(context);
    }

    /**
     * Constructs a ArSceneView object and binds it to an Android Context.
     *
     * <p>In order to have rendering work correctly, {@link #setSession(Session)} must be called.
     *
     * @param context the Android Context to use
     * @param attrs   the Android AttributeSet to associate with
     * @see #setSession(Session)
     */
    public ArSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void initialize() {
        // SceneView will initialize the scene, renderer, and camera.
        super.initialize();

        Renderer renderer = Preconditions.checkNotNull(getRenderer());
        renderer.enablePerformanceMode();
        initializeAr();
    }


    /**
     * Returns the ARCore Session used by this view.
     */
    @Nullable
    public Session getSession() {
        return session;
    }

    /**
     * Define the session used by this Fragment and so the ArSceneView.
     *
     * @param session the new session
     */

    /**
     * Setup the view with an AR Session. This method must be called once to supply the ARCore
     * session. The session is needed for any rendering to occur.
     *
     * Before calling this function, make sure to call the {@link Session#configure(Config)}
     *
     * If you only want to change the Session Config please call
     * {@link #setSessionConfig(Config, boolean)} and check that all your Session Config parameters
     * are taken in account by ARCore at runtime.
     * If it's not the case, you will have to create a new session and call this function.
     *
     * The session is expected to be configured with the update mode of LATEST_CAMERA_IMAGE.
     * Without this configuration, the updating of the ARCore session could block the UI Thread
     * causing poor UI experience.
     *
     * @param session the ARCore session to use for this view
     * @see #ArSceneView(Context, AttributeSet)
     */
    public void setSession(Session session) {
        // Enforce api level 24
        AndroidPreconditions.checkMinAndroidApiLevel();

        if(this.session != null) {
            destroySession();
        }
        this.session = session;

        Renderer renderer = Preconditions.checkNotNull(getRenderer());
        int width = renderer.getDesiredWidth();
        int height = renderer.getDesiredHeight();
        if (width != 0 && height != 0) {
            session.setDisplayGeometry(display.getRotation(), width, height);
        }

        // Feature config, therefore facing direction, can only be configured once per session.
        if (session.getCameraConfig().getFacingDirection() == FacingDirection.FRONT) {
            renderer.setFrontFaceWindingInverted(true);
        }

        // Session needs access to a texture id for updating the camera stream.
        // Filament and the Main thread each have their own gl context that share resources for this.
        session.setCameraTextureName(cameraTextureId);

        // Set max frames per seconds here.
        int fpsBound = session.getCameraConfig().getFpsRange().getUpper();
        setMaxFramesPerSeconds(fpsBound);

        setSessionConfig(session.getConfig(), false);
    }

    /**
     * Define the session config used by this View.
     * <p>
     * Please check that all your Session Config parameters are taken in account by ARCore at
     * runtime.
     * If it's not the case, you will have to create a new session and call
     * {@link #setSession(Session)}.
     *
     * @param config           the new config to apply
     * @param configureSession false if you already called the {@link Session#configure(Config)}
     */
    public void setSessionConfig(Config config, boolean configureSession) {
        this.sessionConfig = config;
        if (getSession() != null) {
            if (configureSession) {
                getSession().configure(config);
            }

            // Set the correct Texture configuration on the camera stream
            cameraStream.checkIfDepthIsEnabled(session, config);
        }
        if (getPlaneRenderer() != null) {
            // Disable the rendering of detected planes if no PlaneFindingMode
            getPlaneRenderer().setEnabled(config.getPlaneFindingMode() != Config.PlaneFindingMode.DISABLED);
        }

        if (onSessionConfigChangeListener != null) {
            onSessionConfigChangeListener.onSessionConfigChange(config);
        }
    }

    /**
     * Resumes the rendering thread and ARCore session.
     *
     * <p>This must be called from onResume().
     *
     * @return true if the ARSession has been initialized.
     * false if there where a CameraNotAvailableException
     */
    @Override
    public void resume() throws Exception {
        resumeSession();
        super.resume();
    }

    /**
     * Resumes the session without starting the scene.
     */
    protected void resumeSession() throws CameraNotAvailableException {
        if (this.session != null) {
            this.session.resume();
        }
    }

    /**
     * Non blocking call to resume the rendering thread and ARCore session in the background
     *
     * <p>This must be called from onResume().
     *
     * <p>If called while another pause or resume is in progress, the resume will be enqueued and
     * happen after the current operation completes.
     *
     * @return A CompletableFuture completed on the main thread once the resume has completed. The
     * future will be completed exceptionally if the resume can not be done.
     */
    public CompletableFuture<Void> resumeAsync(Executor executor) {
        final WeakReference<ArSceneView> currentSceneView = new WeakReference<>(this);
        pauseResumeTask.appendRunnable(
                () -> {
                    ArSceneView arSceneView = currentSceneView.get();
                    if (arSceneView == null) {
                        return;
                    }
                    try {
                        arSceneView.resumeSession();
                    } catch (CameraNotAvailableException e) {
                        throw new RuntimeException(e);
                    }
                },
                executor);

        return pauseResumeTask.appendRunnable(
                () -> {
                    ArSceneView arSceneView = currentSceneView.get();
                    if (arSceneView == null) {
                        return;
                    }
                    try {
                        arSceneView.resumeScene();
                    } catch (IllegalStateException e) {
                        throw new RuntimeException(e);
                    }
                },
                ThreadPools.getMainExecutor());
    }

    /**
     * Pauses the rendering thread and ARCore session.
     *
     * <p>This must be called from onPause().
     */
    @Override
    public void pause() {
        super.pause();
        pauseSession();
    }

    /**
     * Pause the session without touching the scene
     */
    protected void pauseSession() {
        if (session != null) {
            session.pause();
        }
    }

    /**
     * Non blocking call to pause the rendering thread and ARCore session.
     *
     * <p>This should be called from onPause().
     *
     * <p>If pauseAsync is called while another pause or resume is in progress, the pause will be
     * enqueued and happen after the current operation completes.
     *
     * @return A {@link CompletableFuture} completed on the main thread on the pause has completed.
     * The future Will will be completed exceptionally if the resume can not be done.
     */
    public CompletableFuture<Void> pauseAsync(Executor executor) {
        final WeakReference<ArSceneView> currentSceneView = new WeakReference<>(this);
        pauseResumeTask.appendRunnable(
                () -> {
                    ArSceneView arSceneView = currentSceneView.get();
                    if (arSceneView == null) {
                        return;
                    }
                    arSceneView.pauseScene();
                },
                ThreadPools.getMainExecutor());

        return pauseResumeTask
                .appendRunnable(
                        () -> {
                            ArSceneView arSceneView = currentSceneView.get();
                            if (arSceneView == null) {
                                return;
                            }
                            arSceneView.pauseSession();
                        },
                        executor)
                .thenAcceptAsync(
                        // Ensure the final completed future is on the main thread.
                        notUsed -> {
                        },
                        ThreadPools.getMainExecutor());
    }

    /**
     * Required to exit Sceneform.
     *
     * <p>Typically called from onDestroy().
     */
    @Override
    public void destroy() {
        super.destroy();
        destroySession();
    }

    /**
     * Destroy the session without touching the scene
     */
    public void destroySession() {
        if (session != null) {
            session.pause();
            session.close();
        }
        session = null;
    }

    /**
     * @hide
     */
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (session != null) {
            int width = right - left;
            int height = bottom - top;
            session.setDisplayGeometry(display.getRotation(), width, height);
        }
    }

    /**
     * @return returns true if light estimation is enabled.
     */
    public boolean isLightEstimationEnabled() {
        return lightEstimationEnabled;
    }

    /**
     * Enable Light Estimation based on the camera feed. The color and intensity of the sun's indirect
     * light will be modulated by values provided by ARCore's light estimation. Lit objects in the
     * scene will be affected.
     *
     * @param enable set to true to enable Light Estimation or false to use the default estimate,
     *               which is a pixel intensity of 1.0 and color correction value of white (1.0, 1.0, 1.0).
     */
    public void setLightEstimationEnabled(boolean enable) {
        lightEstimationEnabled = enable;
        if (!lightEstimationEnabled) {
            // Update the light probe with the current best light estimate.
            getScene().setLightEstimate(DEFAULT_COLOR_CORRECTION, DEFAULT_PIXEL_INTENSITY);
            lastValidPixelIntensity = DEFAULT_PIXEL_INTENSITY;
            lastValidColorCorrection.set(DEFAULT_COLOR_CORRECTION);
        }
    }

    /**
     * Returns the most recent ARCore Frame if it is available. The frame is updated at the beginning
     * of each drawing frame. Callers of this method should not retain a reference to the return
     * value, since it will be invalid to use the ARCore frame starting with the next frame.
     */
    @Nullable
    @UiThread
    public Frame getArFrame() {
        return currentFrame;
    }

    /**
     * Returns the CameraStream, used to control if the occlusion should be enabled or disabled.
     */
    public CameraStream getCameraStream() {
        return cameraStream;
    }

    /**
     * Returns PlaneRenderer, used to control plane visualization.
     */
    public PlaneRenderer getPlaneRenderer() {
        return planeRenderer;
    }

    /**
     * Before the render call occurs, update the ARCore session to grab the latest frame and update
     * listeners.
     *
     * @return true if the session updated successfully and a new frame was obtained. Update the scene
     * before rendering.
     * @hide
     */
    @SuppressWarnings("AndroidApiChecker")
    @Override
    protected boolean onBeginFrame(long frameTimeNanos) {
        if (isProcessingFrame.get())
            return false;

        isProcessingFrame.set(true);

        // No session, no drawing.
        if (session == null || !pauseResumeTask.isDone()) {
            isProcessingFrame.set(false);
            return false;
        }

        // Before doing anything update the Frame from ARCore.
        boolean arFrameUpdated = true;
        try {
            Frame frame = session.update();
            // No frame, no drawing.
            if (frame == null) {
                isProcessingFrame.set(false);
                return false;
            }

            if (currentFrameTimestamp == frame.getTimestamp()) {
                arFrameUpdated = false;
            }

            currentFrame = frame;
            currentFrameTimestamp = frame.getTimestamp();
        } catch (CameraNotAvailableException | DeadlineExceededException | FatalException e) {
            Log.w(TAG, "Exception updating ARCore session", e);
            isProcessingFrame.set(false);
            return false;
        }

        // No camera, no drawing.
        Camera currentArCamera = currentFrame.getCamera();
        if (currentArCamera == null) {
            getScene().setUseHdrLightEstimate(false);
            isProcessingFrame.set(false);
            return false;
        }

        // Setup Camera Stream if needed.
        if (!cameraStream.isTextureInitialized()) {
            cameraStream.initializeTexture(currentFrame);
        }

        // Recalculate camera Uvs if necessary.
        if (currentFrame.hasDisplayGeometryChanged()) {
            cameraStream.recalculateCameraUvs(currentFrame);
        }

        // If ARCore session has changed, update listeners.
        if (arFrameUpdated) {
            // Update Trackables
            allTrackables = session.getAllTrackables(Trackable.class);
            if (currentFrame != null) {
                updatedTrackables = currentFrame.getUpdatedTrackables(Trackable.class);
            }

            // At the start of the frame, update the tracked pose of the camera
            // to use in any calculations during the frame.
            getScene().getCamera().updateTrackedPose(currentArCamera);

            if (cameraStream.getDepthOcclusionMode() == CameraStream.DepthOcclusionMode.DEPTH_OCCLUSION_ENABLED) {
                if (cameraStream.getDepthMode() == CameraStream.DepthMode.DEPTH) {
                    try (Image depthImage = currentFrame.acquireDepthImage()) {
                        cameraStream.recalculateOcclusion(depthImage);
                    } catch (NotYetAvailableException | DeadlineExceededException ignored) {
                    }
                } else if (cameraStream.getDepthMode() == CameraStream.DepthMode.RAW_DEPTH) {
                    try (Image depthImage = currentFrame.acquireRawDepthImage()) {
                        cameraStream.recalculateOcclusion(depthImage);
                    } catch (NotYetAvailableException | DeadlineExceededException ignored) {
                    }
                }
            }

            // Update the light estimate.
            updateLightEstimate(currentFrame);
            try {
                // Update the plane renderer.
                if (planeRenderer.isEnabled()) {
                    planeRenderer.update(currentFrame, getUpdatedPlanes(),
                            getWidth(), getHeight());
                }
            } catch (DeadlineExceededException ignored) {
            }
        }

        isProcessingFrame.set(false);
        return arFrameUpdated;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        super.doFrame(frameTimeNanos);
    }

    /**
     * Get the AR light estimate from the frame and then update the scene.
     */
    private void updateLightEstimate(Frame frame) {
        // Just return if Light Estimation is disabled.
        if (!lightEstimationEnabled || getSession() == null) {
            return;
        }

        // Update the Light Probe with the new light estimate.
        LightEstimate estimate = frame.getLightEstimate();

        if (isEnvironmentalHdrLightingAvailable()) {
            if (frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                updateHdrLightEstimate(
                        estimate, Preconditions.checkNotNull(getSession()), frame.getCamera());
            }
        } else {
            updateNormalLightEstimate(estimate);
        }
    }

    /**
     * Checks whether the sunlight is being updated every frame based on the Environmental HDR
     * lighting estimate.
     *
     * @return true if the sunlight direction is updated every frame, false otherwise.
     */

    public boolean isLightDirectionUpdateEnabled() {
        return isLightDirectionUpdateEnabled;
    }

    /**
     * Sets whether the sunlight direction generated from Environmental HDR lighting should be updated
     * every frame. If false the light direction will be updated a single time and then no longer
     * change.
     *
     * <p>This may be used to turn off shadow direction updates when they are distracting or unwanted.
     *
     * <p>The default state is true, with sunlight direction updated every frame.
     */

    public void setLightDirectionUpdateEnabled(boolean isLightDirectionUpdateEnabled) {
        this.isLightDirectionUpdateEnabled = isLightDirectionUpdateEnabled;
    }

    /**
     * Returns true if the ARCore camera is configured with
     * Config.LightEstimationMode.ENVIRONMENTAL_HDR. When Environmental HDR lighting mode is enabled,
     * the resulting light estimates will be applied to the Sceneform Scene.
     *
     * @return true if HDR lighting is enabled in Sceneform because ARCore HDR lighting estimation is
     * enabled.
     */

    public boolean isEnvironmentalHdrLightingAvailable() {
        if (sessionConfig == null) {
            return false;
        }
        return (sessionConfig.getLightEstimationMode() == LightEstimationMode.ENVIRONMENTAL_HDR);
    }

    /**
     * Causes a serialized version of the next captured light estimate to be saved to disk.
     *
     * @hide
     */

    public void captureLightingValues(
            Consumer<EnvironmentalHdrLightEstimate> onNextHdrLightingEstimate) {
        this.onNextHdrLightingEstimate = onNextHdrLightingEstimate;
    }

    void updateHdrLightEstimate(
            LightEstimate estimate, Session session, com.google.ar.core.Camera camera) {
        if (estimate.getState() != LightEstimate.State.VALID) {
            return;
        }
        getScene().setUseHdrLightEstimate(true);

        // Updating the direction shouldn't be skipped if it hasn't ever been acquired yet.
        if (isLightDirectionUpdateEnabled || lastValidEnvironmentalHdrMainLightDirection == null) {
            boolean needsNewAnchor = false;

            // If the current anchor for the hdr light direction is not tracking, or we have moved too far
            // then we need a new anchor on which to base our light direction.
            if (lastValidEnvironmentalHdrAnchor == null
                    || lastValidEnvironmentalHdrAnchor.getTrackingState() != TrackingState.TRACKING) {
                needsNewAnchor = true;
            } else {
                Pose cameraPose = camera.getPose();
                Vector3 cameraPosition = new Vector3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz());
                Pose anchorPose = Preconditions.checkNotNull(lastValidEnvironmentalHdrAnchor).getPose();
                Vector3 anchorPosition = new Vector3(anchorPose.tx(), anchorPose.ty(), anchorPose.tz());
                needsNewAnchor =
                        Vector3.subtract(cameraPosition, anchorPosition).length()
                                > RECREATE_LIGHTING_ANCHOR_DISTANCE;
            }

            // If we need a new anchor we destroy the current anchor and try to create a new one. If the
            // ARCore session is tracking this will succeed, and if not we will stop updating the
            // deeplight estimate until we begin tracking again.
            if (needsNewAnchor) {
                if (lastValidEnvironmentalHdrAnchor != null) {
                    lastValidEnvironmentalHdrAnchor.detach();
                    lastValidEnvironmentalHdrAnchor = null;
                }
                lastValidEnvironmentalHdrMainLightDirection = null;
                if (camera.getTrackingState() == TrackingState.TRACKING) {
                    try {
                        lastValidEnvironmentalHdrAnchor = session.createAnchor(camera.getPose());
                    } catch (FatalException e) {
                        // Hopefully this exception is not truly fatal.
                        Log.e(TAG, "Error trying to create environmental hdr anchor", e);
                    }
                }
            }

            // If we have a valid anchor, we update the anchor-relative local direction based on the
            // current light estimate.
            if (lastValidEnvironmentalHdrAnchor != null) {
                float[] mainLightDirection = estimate.getEnvironmentalHdrMainLightDirection();
                if (mainLightDirection != null) {
                    Pose anchorPose = Preconditions.checkNotNull(lastValidEnvironmentalHdrAnchor).getPose();
                    lastValidEnvironmentalHdrMainLightDirection =
                            anchorPose.inverse().rotateVector(mainLightDirection);
                }
            }
        }

        float[] sphericalHarmonics = estimate.getEnvironmentalHdrAmbientSphericalHarmonics();
        if (sphericalHarmonics != null) {
            lastValidEnvironmentalHdrAmbientSphericalHarmonics = sphericalHarmonics;
        }

        float[] mainLightIntensity = estimate.getEnvironmentalHdrMainLightIntensity();
        if (mainLightIntensity != null) {
            lastValidEnvironmentalHdrMainLightIntensity = mainLightIntensity;
        }

        if (lastValidEnvironmentalHdrAnchor == null
                || lastValidEnvironmentalHdrMainLightIntensity == null
                || lastValidEnvironmentalHdrAmbientSphericalHarmonics == null
                || lastValidEnvironmentalHdrMainLightDirection == null) {
            return;
        }

        float mainLightIntensityScalar =
                Math.max(
                        1.0f,
                        Math.max(
                                Math.max(
                                        lastValidEnvironmentalHdrMainLightIntensity[0],
                                        lastValidEnvironmentalHdrMainLightIntensity[1]),
                                lastValidEnvironmentalHdrMainLightIntensity[2]));

        final Color mainLightColor =
                new Color(
                        lastValidEnvironmentalHdrMainLightIntensity[0] / mainLightIntensityScalar,
                        lastValidEnvironmentalHdrMainLightIntensity[1] / mainLightIntensityScalar,
                        lastValidEnvironmentalHdrMainLightIntensity[2] / mainLightIntensityScalar);

        Image[] cubeMap = estimate.acquireEnvironmentalHdrCubeMap();

        // We calculate the world-space direction relative to the current position of the tracked
        // anchor.
        Pose anchorPose = Preconditions.checkNotNull(lastValidEnvironmentalHdrAnchor).getPose();
        float[] currentLightDirection =
                anchorPose.rotateVector(
                        Preconditions.checkNotNull(lastValidEnvironmentalHdrMainLightDirection));

        if (onNextHdrLightingEstimate != null) {
            EnvironmentalHdrLightEstimate lightEstimate =
                    new EnvironmentalHdrLightEstimate(
                            lastValidEnvironmentalHdrAmbientSphericalHarmonics,
                            currentLightDirection,
                            mainLightColor,
                            mainLightIntensityScalar,
                            cubeMap);
            onNextHdrLightingEstimate.accept(lightEstimate);
            onNextHdrLightingEstimate = null;
        }

        getScene().setEnvironmentalHdrLightEstimate(
                lastValidEnvironmentalHdrAmbientSphericalHarmonics,
                currentLightDirection,
                mainLightColor,
                mainLightIntensityScalar,
                cubeMap);
        for (Image cubeMapImage : cubeMap) {
            cubeMapImage.close();
        }
    }

    private void updateNormalLightEstimate(LightEstimate estimate) {
        getScene().setUseHdrLightEstimate(false);
        // Verify that the estimate is valid
        float pixelIntensity = lastValidPixelIntensity;
        // Only update the estimate if it is valid.
        if (estimate.getState() == LightEstimate.State.VALID) {
            estimate.getColorCorrection(colorCorrectionPixelIntensity, 0);
            pixelIntensity = Math.max(colorCorrectionPixelIntensity[3], 0.0f);
            lastValidColorCorrection.set(
                    colorCorrectionPixelIntensity[0],
                    colorCorrectionPixelIntensity[1],
                    colorCorrectionPixelIntensity[2]);
        }
        // Update the light probe with the current best light estimate.
        getScene().setLightEstimate(lastValidColorCorrection, pixelIntensity);
        // Update the last valid estimate.
        lastValidPixelIntensity = pixelIntensity;
    }

    private void initializeAr() {
        display = getContext().getSystemService(WindowManager.class).getDefaultDisplay();

        Renderer renderer = Preconditions.checkNotNull(getRenderer());

        // Initialize Plane Renderer
        planeRenderer = new PlaneRenderer(renderer);

        // Initialize Camera Stream
        cameraTextureId = GLHelper.createCameraTexture();
        cameraStream = new CameraStream(cameraTextureId, renderer);
    }

    //
    // TODO : When Kotlining it, move all those trackables parts to Trackables.kt as an ArSceneView extension.
    //

    /**
     * Retrieve if the view is currently tracking a plane.
     *
     * @return true if the current frame is tracking at least one plane.
     */
    public boolean isTrackingPlane() {
        return getUpdatedPlanes(TrackingState.TRACKING).size() > 0;
    }

    /**
     * Retrieve if the view has already tracked a plane.
     *
     * @return true if the current frame has tracked at least one plane.
     */
    public boolean hasTrackedPlane() {
        return getAllPlanes(TrackingState.TRACKING, TrackingState.PAUSED).size() > 0;
    }

    /**
     * Retrieve the view session tracked planes.
     */
    public Collection<Plane> getAllPlanes() {
        return getAllPlanes((TrackingState[]) null);
    }

    /**
     * Retrieve the view session tracked planes with the specified tracking states.
     *
     * @param trackingStates the trackable tracking states or null for no states filter
     */
    public Collection<Plane> getAllPlanes(@Nullable TrackingState... trackingStates) {
        return Trackables.getPlanes(allTrackables, trackingStates);
    }

    /**
     * Retrieve the view last frame tracked planes.
     */
    public Collection<Plane> getUpdatedPlanes() {
        return getUpdatedPlanes((TrackingState[]) null);
    }

    /**
     * Retrieve the view last frame tracked planes with the specified tracking states.
     *
     * @param trackingStates the trackable tracking states or null for no states filter
     */
    public Collection<Plane> getUpdatedPlanes(@Nullable TrackingState... trackingStates) {
        return Trackables.getPlanes(updatedTrackables, trackingStates);
    }

    /**
     * Retrieve if the view is currently tracking an Augmented Image.
     *
     * @return true if the current frame is fully tracking at least one Augmented.
     */
    public boolean isTrackingAugmentedImage() {
        return getUpdatedAugmentedImages(TrackingState.TRACKING, AugmentedImage.TrackingMethod.FULL_TRACKING).size() > 0;
    }

    /**
     * Retrieve if the view has already tracked a Augmented Image.
     *
     * @return true if the current frame has tracked at least one Augmented Image.
     */
    public boolean hasTrackedAugmentedImage() {
        return getAllAugmentedImages(TrackingState.TRACKING, AugmentedImage.TrackingMethod.FULL_TRACKING).size() > 0
                && getAllAugmentedImages(TrackingState.PAUSED, AugmentedImage.TrackingMethod.FULL_TRACKING).size() > 0;
    }

    /**
     * Retrieve the view session tracked Augmented Images.
     */
    public Collection<AugmentedImage> getAllAugmentedImages() {
        return getAllAugmentedImages(null, null);
    }

    /**
     * Retrieve the view session tracked Augmented Images with the specified tracking state and method.
     *
     * @param trackingState  the trackable tracking state or null for no states filter
     * @param trackingMethod the trackable tracking method or null for no tracking method filter
     */
    public Collection<AugmentedImage> getAllAugmentedImages(@Nullable TrackingState trackingState
            , @Nullable AugmentedImage.TrackingMethod trackingMethod) {
        return Trackables.getAugmentedImages(allTrackables, trackingState, trackingMethod);
    }

    /**
     * Retrieve the view last frame tracked Augmented Images.
     */
    public Collection<AugmentedImage> getUpdatedAugmentedImages() {
        return getUpdatedAugmentedImages(null, null);
    }

    /**
     * Retrieve the view last frame tracked Augmented Images with the specified tracking state and method.
     *
     * @param trackingState  the trackable tracking state or null for no states filter
     * @param trackingMethod the trackable tracking method or null for no tracking method filter
     */
    public Collection<AugmentedImage> getUpdatedAugmentedImages(@Nullable TrackingState trackingState
            , @Nullable AugmentedImage.TrackingMethod trackingMethod) {
        return Trackables.getAugmentedImages(updatedTrackables, trackingState, trackingMethod);
    }

    /**
     * Registers a callback to be invoked when the ARCore Session is to configured. The callback will
     * only be invoked once after the Session default config has been applied and before it is
     * configured on the Session.
     *
     * @param listener the {@link OnSessionConfigChangeListener} to attach.
     */
    public void setOnSessionConfigChangeListener(@Nullable OnSessionConfigChangeListener listener) {
        this.onSessionConfigChangeListener = listener;
    }

    /**
     * Called when the ARCore Session configuration has changed.
     */
    public interface OnSessionConfigChangeListener {
        /**
         * The callback will only be invoked every time a new session or session config is defined.
         *
         * @param config The ARCore Session Config.
         * @see #setOnSessionConfigChangeListener(OnSessionConfigChangeListener)
         */
        void onSessionConfigChange(Config config);
    }
}
