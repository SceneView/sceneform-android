package com.google.ar.sceneform;

import android.content.Context;
import android.media.Image;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig.FacingDirection;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.DeadlineExceededException;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.rendering.CameraStream;
import com.google.ar.sceneform.rendering.GLHelper;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.rendering.ThreadPools;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;
import com.gorisse.thomas.sceneform.ArSceneViewKt;
import com.gorisse.thomas.sceneform.light.EnvironmentLightsEstimate;
import com.gorisse.thomas.sceneform.light.LightEstimationConfig;
import com.gorisse.thomas.sceneform.light.LightEstimationKt;
import com.gorisse.thomas.sceneform.scene.CameraKt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A SurfaceView that integrates with ARCore and renders a scene.
 */
@SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
public class ArSceneView extends SceneView {
    private static final String TAG = ArSceneView.class.getSimpleName();
    private static final String REPORTED_ENGINE_TYPE = "Sceneform";

    // pauseResumeTask is modified on the main thread only.  It may be completed on background
    // threads however.
    private final SequentialTask pauseResumeTask = new SequentialTask();
    public int cameraTextureId;
    private boolean hasSetTextureNames = false;
    @Nullable
    private Session session;

    /**
     * Don't use it. Public until full moving to Kotlin.
     *
     * @see ArSceneViewKt#setEstimatedEnvironmentLights(ArSceneView, EnvironmentLightsEstimate)
     * @see ArSceneViewKt#getEstimatedEnvironmentLights(ArSceneView)
     */
    public LightEstimationConfig _lightEstimationConfig = new LightEstimationConfig();

    private AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    @Nullable
    private Frame currentFrame;
    private Long currentFrameTimestamp = 0L;
    private Collection<Trackable> allTrackables = new ArrayList<>();
    private Collection<Trackable> updatedTrackables = new ArrayList<>();
    private Display display;
    private CameraStream cameraStream;
    private PlaneRenderer planeRenderer;
    @Nullable
    /**
     * Don't use it. Public until full moving to Kotlin.
     * @see ArSceneViewKt#setEstimatedEnvironmentLights(ArSceneView, EnvironmentLightsEstimate)
     * @see ArSceneViewKt#getEstimatedEnvironmentLights(ArSceneView, EnvironmentLightsEstimate)
     */
    public EnvironmentLightsEstimate _estimatedEnvironmentLights = null;
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
     * <p>
     * Before calling this function, make sure to call the {@link Session#configure(Config)}
     * <p>
     * If you only want to change the Session Config please call
     * {@link #setSessionConfig(Config, boolean)} and check that all your Session Config parameters
     * are taken in account by ARCore at runtime.
     * If it's not the case, you will have to create a new session and call this function.
     * <p>
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

        if (this.session != null) {
            destroySession();
        }
        this.session = session;

        Renderer renderer = Preconditions.checkNotNull(getRenderer());

        // Feature config, therefore facing direction, can only be configured once per session.
        if (session.getCameraConfig().getFacingDirection() == FacingDirection.FRONT) {
            renderer.setFrontFaceWindingInverted(true);
        }

        // Session needs access to a texture id for updating the camera stream.
        // Filament and the Main thread each have their own gl context that share resources for this.
        // Reset the hasSetTextureNames variable so that the texture name is set during the first call to onBeginFrame.
        hasSetTextureNames = false;

        // Set max frames per seconds here.
        int fpsBound = session.getCameraConfig().getFpsRange().getUpper();
        setMaxFramesPerSeconds(fpsBound);

        // Light estimation is not usable with front camera
        if (session.getCameraConfig().getFacingDirection() == FacingDirection.FRONT
                && ArSceneViewKt.getLightEstimationConfig(this).getMode()
                != Config.LightEstimationMode.DISABLED) {
            ArSceneViewKt.setLightEstimationConfig(this, LightEstimationConfig.DISABLED);
        }

        setSessionConfig(session.getConfig(), false);
    }

    /**
     * The session config used by this View.
     */
    public Config getSessionConfig() {
        return session != null ? session.getConfig() : null;
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

            Renderer renderer = Preconditions.checkNotNull(getRenderer());
            int width = renderer.getDesiredWidth();
            int height = renderer.getDesiredHeight();
            if (width != 0 && height != 0) {
                session.setDisplayGeometry(display.getRotation(), width, height);
            }
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

        if (_estimatedEnvironmentLights != null) {
            _estimatedEnvironmentLights.destroy();
            _estimatedEnvironmentLights = null;
        }

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
            // Texture names should only be set once on a GL thread unless they change.
            // This is done during onBeginFrame rather than setSession since the session is
            // not guaranteed to have been initialized during the execution of setSession.
            if (!hasSetTextureNames) {
                session.setCameraTextureName(cameraTextureId);
                hasSetTextureNames = true;
            }

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
            if (_lightEstimationConfig.getMode() != Config.LightEstimationMode.DISABLED) {
                EnvironmentLightsEstimate environmentLightsEstimate = LightEstimationKt.environmentLightsEstimate(currentFrame,
                        _lightEstimationConfig,
                        _estimatedEnvironmentLights,
                        _environment,
                        _mainLight,
                        CameraKt.getExposureFactor(getRenderer().getCamera()));
                ArSceneViewKt.setEstimatedEnvironmentLights(this, environmentLightsEstimate);
            }

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

    private void initializeAr() {
        display = getContext().getSystemService(WindowManager.class).getDefaultDisplay();

        Renderer renderer = Preconditions.checkNotNull(getRenderer());

        // Initialize Plane Renderer
        planeRenderer = new PlaneRenderer(renderer);

        // Initialize Camera Stream
        cameraTextureId = GLHelper.createCameraTexture();
        cameraStream = new CameraStream(cameraTextureId, renderer);
    }

    public void setCameraStreamRenderPriority(@IntRange(from = 0L, to = 7L) int priority) {
        this.cameraStream.setRenderPriority(priority);
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
     * Retrieve if the view is currently tracking an Augmented Face.
     *
     * @return true if the current frame is fully tracking at least one Augmented.
     */
    public boolean isTrackingAugmentedFaces() {
        return getUpdatedAugmentedFaces(TrackingState.TRACKING).size() > 0;
    }

    /**
     * Retrieve if the view has already tracked a Augmented Face.
     *
     * @return true if the current frame has tracked at least one Augmented Image.
     */
    public boolean hasTrackedAugmentedFaces() {
        return getAllAugmentedFaces(TrackingState.TRACKING).size() > 0;
    }

    /**
     * Retrieve the view session tracked Augmented Images.
     */
    public Collection<AugmentedFace> getAllAugmentedFaces() {
        return getAllAugmentedFaces(null);
    }

    /**
     * Retrieve the view session tracked Augmented Faces with the specified tracking state.
     *
     * @param trackingState  the trackable tracking state or null for no states filter
     */
    public Collection<AugmentedFace> getAllAugmentedFaces(@Nullable TrackingState trackingState) {
        return Trackables.getAugmentedFaces(allTrackables, trackingState);
    }

    /**
     * Retrieve the view last frame tracked Augmented Faces.
     */
    public Collection<AugmentedFace> getUpdatedAugmentedFaces() {
        return getUpdatedAugmentedFaces(null);
    }

    /**
     * Retrieve the view last frame tracked Augmented Faces with the specified tracking state.
     *
     * @param trackingState  the trackable tracking state or null for no states filter
     */
    public Collection<AugmentedFace> getUpdatedAugmentedFaces(@Nullable TrackingState trackingState) {
        return Trackables.getAugmentedFaces(updatedTrackables, trackingState);
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
