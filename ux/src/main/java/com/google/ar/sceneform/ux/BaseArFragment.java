package com.google.ar.sceneform.ux;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnWindowFocusChangeListener;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.gorisse.thomas.sceneform.ArSceneViewKt;
import com.gorisse.thomas.sceneform.light.LightEstimationConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The AR fragment brings in the required view layout and controllers for common AR features.
 */
public abstract class BaseArFragment extends Fragment
        implements Scene.OnPeekTouchListener, Scene.OnUpdateListener {
    /**
     * The key for the fullscreen argument
     */
    public static final String ARGUMENT_FULLSCREEN = "fullscreen";
    private static final String TAG = BaseArFragment.class.getSimpleName();
    private boolean installRequested;
    private boolean sessionInitializationFailed = false;
    private ArSceneView arSceneView;
    private InstructionsController instructionsController;
    private TransformationSystem transformationSystem;
    private GestureDetector gestureDetector;
    private FrameLayout frameLayout;
    private boolean isStarted;
    private boolean canRequestDangerousPermissions = true;
    private boolean fullscreen = true;
    @SuppressWarnings({"initialization"})
    private final OnWindowFocusChangeListener onFocusListener =
            (hasFocus -> onWindowFocusChanged(hasFocus));
    private boolean isAugmentedImageDatabaseEnabled = true;
    @Nullable
    private OnSessionConfigurationListener onSessionConfigurationListener;
    @Nullable
    private OnTapArPlaneListener onTapArPlaneListener;
    @Nullable
    private OnAugmentedImageUpdateListener onAugmentedImageUpdateListener;
    @Nullable
    private OnAugmentedFaceUpdateListener onAugmentedFaceUpdateListener;

    /**
     * Gets the ArSceneView for this fragment.
     */
    public ArSceneView getArSceneView() {
        return arSceneView;
    }

    /**
     * Gets the instructions view controller.
     * Default: plane discovery which displays instructions for how to scan
     *
     * @return the actual instructions controller
     */
    public InstructionsController getInstructionsController() {
        return instructionsController;
    }

    /**
     * Set the instructions view controller.
     *
     * @param instructionsController the custom instructions for the view.
     */
    public void setInstructionsController(InstructionsController instructionsController) {
        this.instructionsController = instructionsController;
    }

    /**
     * Gets the transformation system, which is used by {@link TransformableNode} for detecting
     * gestures and coordinating which node is selected.
     */
    public TransformationSystem getTransformationSystem() {
        return transformationSystem;
    }

    /**
     * Registers a callback to be invoked when the ARCore Session is to configured. The callback will
     * only be invoked once after the Session default config has been applied and before it is
     * configured on the Session.
     *
     * @param onSessionConfigurationListener the {@link OnSessionConfigurationListener} to attach.
     */
    public void setOnSessionConfigurationListener(
            @Nullable OnSessionConfigurationListener onSessionConfigurationListener) {
        this.onSessionConfigurationListener = onSessionConfigurationListener;
    }

    /**
     * Registers a callback to be invoked when an ARCore Plane is tapped. The callback will only be
     * invoked if no {@link com.google.ar.sceneform.Node} was tapped.
     *
     * @param onTapArPlaneListener the {@link OnTapArPlaneListener} to attach
     */
    public void setOnTapArPlaneListener(@Nullable OnTapArPlaneListener onTapArPlaneListener) {
        this.onTapArPlaneListener = onTapArPlaneListener;
    }

    /**
     * Called when an ARCore AugmentedImage TrackingState/TrackingMethod is updated.
     * Registers a callback to be invoked when an ARCore AugmentedImage TrackingState/TrackingMethod
     * is updated.
     * The callback will be invoked on each AugmentedImage update.
     *
     * @param listener the {@link OnAugmentedImageUpdateListener} to attach
     * @see AugmentedImage#getTrackingState()
     * @see AugmentedImage#getTrackingMethod()
     */
    public void setOnAugmentedImageUpdateListener(@Nullable OnAugmentedImageUpdateListener listener) {
        this.onAugmentedImageUpdateListener = listener;
    }

    /**
     * Called when an ARCore AugmentedFace TrackingState is updated.
     * Registers a callback to be invoked when an ARCore AugmentedFace TrackingState is updated.
     * The callback will be invoked on each AugmentedFace update.
     *
     * @param listener the {@link OnAugmentedFaceUpdateListener} to attach
     * @see AugmentedFace#getTrackingState()
     */
    public void setOnAugmentedFaceUpdateListener(@Nullable OnAugmentedFaceUpdateListener listener) {
        this.onAugmentedFaceUpdateListener = listener;
    }

    @Override
    @SuppressWarnings({"initialization"})
    // Suppress @UnderInitialization warning.
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        frameLayout = (FrameLayout) inflater.inflate(R.layout.sceneform_ux_fragment_layout
                , container, false);
        arSceneView = frameLayout.findViewById(R.id.sceneform_ar_scene_view);
        arSceneView.setOnSessionConfigChangeListener(this::onSessionConfigChanged);

        // Setup the instructions view.
        instructionsController = new InstructionsController(inflater, frameLayout);
        instructionsController.setEnabled(InstructionsController.TYPE_PLANE_DISCOVERY, true);

        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            // Enforce API level 24
            return frameLayout;
        }

        transformationSystem = makeTransformationSystem();

        gestureDetector =
                new GestureDetector(
                        getContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });

        arSceneView.getScene().addOnPeekTouchListener(this);
        arSceneView.getScene().addOnUpdateListener(this);

        if (isArRequired()) {
            // Request permissions
            requestDangerousPermissions();
        }

        // Make the app immersive and don't turn off the display.
        arSceneView.getViewTreeObserver().addOnWindowFocusChangeListener(onFocusListener);
        return frameLayout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();

        if (arguments != null) {
            fullscreen = arguments.getBoolean(ARGUMENT_FULLSCREEN, true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        arSceneView.getViewTreeObserver().removeOnWindowFocusChangeListener(onFocusListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isArRequired() && arSceneView.getSession() == null) {
            initializeSession();
        }
        resume();
    }

    public void resume() {
        if (isStarted) {
            return;
        }

        if (getActivity() != null) {
            isStarted = true;
            try {
                arSceneView.resume();
            } catch (Exception ex) {
                sessionInitializationFailed = true;
            }
            if (!sessionInitializationFailed) {
                if (getInstructionsController() != null) {
                    getInstructionsController().setVisible(true);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        pause();
    }

    public void pause() {
        if (!isStarted) {
            return;
        }

        isStarted = false;
        if (getInstructionsController() != null) {
            getInstructionsController().setVisible(false);
        }
        arSceneView.pause();
    }

    @Override
    public void onDestroy() {
        destroy();
        super.onDestroy();
    }

    /**
     * Required to exit Sceneform.
     *
     * <p>Typically called from onDestroy().
     */
    public void destroy() {
        pause();
        arSceneView.destroy();
    }

    /**
     * Destroy the session without touching the scene
     */
    public void destroySession() {
        arSceneView.destroySession();
    }

    /**
     * Returns true if this application is AR Required, false if AR Optional. This is called when
     * initializing the application and the session.
     */
    public abstract boolean isArRequired();

    /**
     * Returns an array of dangerous permissions that are required by the app in addition to
     * Manifest.permission.CAMERA, which is needed by ARCore. If no additional permissions are needed,
     * an empty array should be returned.
     */
    public String[] getAdditionalPermissions() {
        return new String[0];
    }

    /**
     * Starts the process of requesting dangerous permissions. This combines the CAMERA permission
     * required of ARCore and any permissions returned from getAdditionalPermissions(). There is no
     * specific processing on the result of the request, subclasses can override
     * onRequestPermissionsResult() if additional processing is needed.
     *
     * <p>{@link #setCanRequestDangerousPermissions(Boolean)} can stop this function from doing
     * anything.
     */
    protected void requestDangerousPermissions() {
        if (!canRequestDangerousPermissions) {
            // If this is in progress, don't do it again.
            return;
        }
        canRequestDangerousPermissions = false;

        List<String> permissions = new ArrayList<String>();
        String[] additionalPermissions = getAdditionalPermissions();
        int permissionLength = additionalPermissions != null ? additionalPermissions.length : 0;
        for (int i = 0; i < permissionLength; ++i) {
            if (ActivityCompat.checkSelfPermission(requireActivity(), additionalPermissions[i])
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(additionalPermissions[i]);
            }
        }

        //TODO : Use ARCore CameraPermissionHelper.requestCameraPermission(this); instead

        // Always check for camera permission
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }

        if (!permissions.isEmpty()) {
            ActivityResultLauncher<String[]> requestMultiplePermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                results.forEach((key, value) -> {
                    if (key.equals(Manifest.permission.CAMERA)) {
                        if (!value) {
                            AlertDialog.Builder builder;
                            builder =
                                    new AlertDialog.Builder(requireActivity(), android.R.style.Theme_Material_Dialog_Alert);

                            builder
                                    .setTitle(R.string.sceneform_camera_permission_required)
                                    .setMessage(R.string.sceneform_add_camera_permission_via_settings)
                                    .setPositiveButton(
                                            android.R.string.ok,
                                            (dialog, which) -> {
                                                // If Ok was hit, bring up the Settings app.
                                                Intent intent = new Intent();
                                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                                intent.setData(Uri.fromParts("package", requireActivity().getPackageName(), null));
                                                requireActivity().startActivity(intent);
                                                // When the user closes the Settings app, allow the app to resume.
                                                // Allow the app to ask for permissions again now.
                                                setCanRequestDangerousPermissions(true);
                                            })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setOnDismissListener(
                                            arg0 -> {
                                                // canRequestDangerousPermissions will be true if "OK" was selected from the dialog,
                                                // false otherwise.  If "OK" was selected do nothing on dismiss, the app will
                                                // continue and may ask for permission again if needed.
                                                // If anything else happened, finish the activity when this dialog is
                                                // dismissed.
                                                if (!getCanRequestDangerousPermissions()) {
                                                    requireActivity().finish();
                                                }
                                            })
                                    .show();
                        }
                    } else {
                        // If any other user defined permission is not
                        // granted, finish the Activity.
                        if (!value)
                            requireActivity().finish();
                    }
                });
            });

            // Request the permissions
            requestMultiplePermissions.launch(permissions.toArray(new String[0]));
        }
    }


    /**
     * If true, {@link #requestDangerousPermissions()} returns without doing anything, if false
     * permissions will be requested
     */
    protected Boolean getCanRequestDangerousPermissions() {
        return canRequestDangerousPermissions;
    }

    /**
     * If true, {@link #requestDangerousPermissions()} returns without doing anything, if false
     * permissions will be requested
     */
    protected void setCanRequestDangerousPermissions(Boolean canRequestDangerousPermissions) {
        this.canRequestDangerousPermissions = canRequestDangerousPermissions;
    }

    protected final boolean requestInstall() throws UnavailableException {
        switch (ArCoreApk.getInstance().requestInstall(requireActivity(), !installRequested)) {
            case INSTALL_REQUESTED:
                installRequested = true;
                return true;
            case INSTALLED:
                break;
        }
        return false;
    }

    /**
     * Initializes the ARCore session. The CAMERA permission is checked before checking the
     * installation state of ARCore. Once the permissions and installation are OK, the method
     * #getSessionConfiguration(Session session) is called to get the session configuration to use.
     * Sceneform requires that the ARCore session be updated using LATEST_CAMERA_IMAGE to avoid
     * blocking while drawing. This mode is set on the configuration object returned from the
     * subclass.
     */
    protected final void initializeSession() {

        // Only try once
        if (sessionInitializationFailed) {
            return;
        }
        // if we have the camera permission, create the session
        if (ContextCompat.checkSelfPermission(requireActivity(), "android.permission.CAMERA")
                == PackageManager.PERMISSION_GRANTED) {

            UnavailableException sessionException = null;
            try {
                if (requestInstall()) {
                    return;
                }

                Session session = onCreateSession();
                Config config = onCreateSessionConfig(session);
                if (this.onSessionConfigurationListener != null) {
                    this.onSessionConfigurationListener.onSessionConfiguration(session, config);
                }
                if (session.getCameraConfig().getFacingDirection() == CameraConfig.FacingDirection.FRONT
                        && config.getLightEstimationMode() == Config.LightEstimationMode.ENVIRONMENTAL_HDR) {
                    config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                }
                session.configure(config);
                setSession(session);
                return;
            } catch (UnavailableException e) {
                sessionException = e;
            } catch (Exception e) {
                sessionException = new UnavailableException();
                sessionException.initCause(e);
            }
            sessionInitializationFailed = true;
            onArUnavailableException(sessionException);

        } else {
            requestDangerousPermissions();
        }
    }

    /**
     * - Creates the ARCore Session
     * - Specifies additional features for creating an ARCore {@link com.google.ar.core.Session}.
     * See {@link com.google.ar.core.Session.Feature}.
     * <p>
     * Don't configure the session here.
     * The {@link Session#configure(Config)} is made after this call.
     * You must override the {@link #onCreateSessionConfig(Session)} to apply configuration.
     */
    private Session onCreateSession() throws UnavailableSdkTooOldException, UnavailableDeviceNotCompatibleException,
            UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        return new Session(requireActivity());
    }

    /**
     * Creates the session config applied to the specified session.
     */
    protected Config onCreateSessionConfig(Session session) {
        Config config = new Config(session);
        LightEstimationConfig lightEstimationConfig = getArSceneView() != null ?
                ArSceneViewKt.getLightEstimationConfig(getArSceneView()) : null;
        if (lightEstimationConfig != null) {
            config.setLightEstimationMode(lightEstimationConfig.getMode());
        }
        config.setDepthMode(Config.DepthMode.DISABLED);
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        config.setFocusMode(Config.FocusMode.AUTO);
        // Force the non-blocking mode for the session.
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        return config;
    }

    /**
     * Define the session used by this Fragment and so the ArSceneView.
     * <p>
     * Before calling this function, make sure to call the {@link Session#configure(Config)}
     * <p>
     * If you only want to change the Session Config please call
     * {@link #setSessionConfig(Config, boolean)} and check that all your Session Config parameters
     * are taken in account by ARCore at runtime.
     * If it's not the case, you will have to create a new session and call this function.
     *
     * @param session the new session
     */
    public void setSession(Session session) {
        getArSceneView().setSession(session);
    }

    /**
     * Define the session config used by this Fragment and so the ArSceneView.
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
        getArSceneView().setSessionConfig(config, configureSession);
    }

    /**
     * Occurs when a session configuration has changed.
     */
    protected void onSessionConfigChanged(Config config) {
        InstructionsController instructionsController = getInstructionsController();
        if (instructionsController != null) {
            // Planes
            instructionsController.setEnabled(InstructionsController.TYPE_PLANE_DISCOVERY
                    , config.getPlaneFindingMode() != Config.PlaneFindingMode.DISABLED);

            // AugmentedImages
            AugmentedImageDatabase augmentedImageDatabase = config.getAugmentedImageDatabase();
            instructionsController.setEnabled(InstructionsController.TYPE_AUGMENTED_IMAGE_SCAN
                    , augmentedImageDatabase != null && augmentedImageDatabase.getNumImages() > 0);
        }
    }

    /**
     * Creates the transformation system used by this fragment. Can be overridden to create a custom
     * transformation system.
     */
    protected TransformationSystem makeTransformationSystem() {
        FootprintSelectionVisualizer selectionVisualizer = new FootprintSelectionVisualizer();

        TransformationSystem transformationSystem =
                new TransformationSystem(getResources().getDisplayMetrics(), selectionVisualizer);

        setupSelectionRenderable(selectionVisualizer);

        return transformationSystem;
    }

    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void setupSelectionRenderable(FootprintSelectionVisualizer selectionVisualizer) {
        ModelRenderable.builder()
                .setSource(getActivity(), R.raw.sceneform_footprint)
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(
                        renderable -> {
                            // If the selection visualizer already has a footprint renderable, then it was set to
                            // something custom. Don't override the custom visual.
                            if (selectionVisualizer.getFootprintRenderable() == null) {
                                selectionVisualizer.setFootprintRenderable(renderable);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(
                                            getContext(), "Unable to load footprint renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }

    protected abstract void onArUnavailableException(UnavailableException sessionException);

    protected void onWindowFocusChanged(boolean hasFocus) {
        FragmentActivity activity = getActivity();
        if (hasFocus && activity != null) {
            if (fullscreen) {
                // This flag should be set before using the WindowInsetsController
                // otherwise showing the transparent bars by swipe doesn't work
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = activity.getWindow().getInsetsController();

                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                } else {
                    // Standard Android full-screen functionality.
                    activity
                            .getWindow()
                            .getDecorView()
                            .setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
        }
    }

    @Override
    public void onPeekTouch(HitTestResult hitTestResult, MotionEvent motionEvent) {
        transformationSystem.onTouch(hitTestResult, motionEvent);

        if (hitTestResult.getNode() == null) {
            gestureDetector.onTouchEvent(motionEvent);
        }
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        if (getArSceneView() == null || getArSceneView().getSession() == null || getArSceneView().getArFrame() == null)
            return;

        if (getInstructionsController() != null) {
            // Instructions for the Plane finding mode.
            boolean showPlaneInstructions = !getArSceneView().hasTrackedPlane();
            if (getInstructionsController().isVisible(InstructionsController.TYPE_PLANE_DISCOVERY) != showPlaneInstructions) {
                getInstructionsController().setVisible(InstructionsController.TYPE_PLANE_DISCOVERY, showPlaneInstructions);
            }

            // Instructions for the Augmented Image finding mode.
            boolean showAugmentedImageInstructions = !getArSceneView().isTrackingAugmentedImage();
            if (getInstructionsController().isVisible(InstructionsController.TYPE_AUGMENTED_IMAGE_SCAN) != showAugmentedImageInstructions) {
                getInstructionsController().setVisible(InstructionsController.TYPE_AUGMENTED_IMAGE_SCAN, showAugmentedImageInstructions);
            }
        }

        if(onAugmentedImageUpdateListener != null) {
            for (AugmentedImage augmentedImage : getArSceneView().getUpdatedAugmentedImages()) {
                onAugmentedImageUpdateListener.onAugmentedImageTrackingUpdate(augmentedImage);
            }
        }

        if(onAugmentedFaceUpdateListener != null) {
            for (AugmentedFace augmentedFace : getArSceneView().getUpdatedAugmentedFaces()) {
                onAugmentedFaceUpdateListener.onAugmentedFaceTrackingUpdate(augmentedFace);
            }
        }
    }

    private void onSingleTap(MotionEvent motionEvent) {
        Frame frame = arSceneView.getArFrame();

        transformationSystem.selectNode(null);

        // Local variable for nullness static-analysis.
        OnTapArPlaneListener onTapArPlaneListener = this.onTapArPlaneListener;

        if (frame != null && onTapArPlaneListener != null) {
            if (motionEvent != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(motionEvent)) {
                    Trackable trackable = hit.getTrackable();
                    if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                        Plane plane = (Plane) trackable;
                        onTapArPlaneListener.onTapPlane(hit, plane, motionEvent);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Invoked when the ARCore Session is to be configured.
     */
    public interface OnSessionConfigurationListener {
        /**
         * The callback will only be invoked once after a Session is initialized and before it is
         * resumed for the first time.
         *
         * @param session The ARCore Session.
         * @param config  The ARCore Session Config.
         * @see #setOnSessionConfigurationListener(OnSessionConfigurationListener)
         */
        void onSessionConfiguration(Session session, Config config);
    }

    /**
     * Invoked when an ARCore plane is tapped.
     */
    public interface OnTapArPlaneListener {
        /**
         * Called when an ARCore plane is tapped. The callback will only be invoked if no {@link
         * com.google.ar.sceneform.Node} was tapped.
         *
         * @param hitResult   The ARCore hit result that occurred when tapping the plane
         * @param plane       The ARCore Plane that was tapped
         * @param motionEvent the motion event that triggered the tap
         * @see #setOnTapArPlaneListener(OnTapArPlaneListener)
         */
        void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent);
    }

    /**
     * Invoked when an ARCore AugmentedImage state updates.
     */
    public interface OnAugmentedImageUpdateListener {
        /**
         * Called when an ARCore AugmentedImage TrackingState/TrackingMethod is updated.
         * The callback will be invoked on each AugmentedImage update.
         *
         * @param augmentedImage The ARCore AugmentedImage.
         * @see #setOnAugmentedImageUpdateListener(OnAugmentedImageUpdateListener)
         * @see AugmentedImage#getTrackingState()
         * @see AugmentedImage#getTrackingMethod()
         */
        void onAugmentedImageTrackingUpdate(AugmentedImage augmentedImage);
    }


    /**
     * Invoked when an ARCore AugmentedFace state updates.
     */
    public interface OnAugmentedFaceUpdateListener {
        /**
         * Called when an ARCore AugmentedFace TrackingState is updated.
         * The callback will be invoked on each AugmentedFace update.
         *
         * @param augmentedFace The ARCore AugmentedFace.
         * @see #setOnAugmentedFaceUpdateListener(OnAugmentedFaceUpdateListener)
         * @see AugmentedFace#getTrackingState()
         */
        void onAugmentedFaceTrackingUpdate(AugmentedFace augmentedFace);
    }
}