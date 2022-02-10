package com.google.ar.sceneform;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import com.google.android.filament.ColorGrading;
import com.google.android.filament.Entity;
import com.google.android.filament.LightManager;
import com.google.android.filament.ToneMapper;
import com.google.android.filament.View;
import com.google.android.filament.utils.KTXLoader;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.EngineInstance;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.MovingAverageMillisecondsTracker;
import com.google.ar.sceneform.utilities.Preconditions;
import com.gorisse.thomas.sceneform.SceneViewKt;
import com.gorisse.thomas.sceneform.environment.Environment;
import com.gorisse.thomas.sceneform.environment.KTXEnvironmentKt;
import com.gorisse.thomas.sceneform.light.LightKt;
import com.gorisse.thomas.sceneform.util.ResourceLoaderKt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;

/**
 * A Sceneform SurfaceView that manages rendering and interaction with the scene.
 */
public class SceneView extends SurfaceView implements Choreographer.FrameCallback {
    private static final String TAG = SceneView.class.getSimpleName();

    public static final String DEFAULT_IBL_LOCATION = "environments/default_environment_ibl.ktx";
    public static final String DEFAULT_SKYBOX_LOCATION = "environments/default_environment_skybox.ktx";

    private static final int DEFAULT_MAX_FRAMES_PER_SECONDS = 60;
    private FrameRate frameRate = FrameRate.FULL;
    private int maxFramesPerSeconds = DEFAULT_MAX_FRAMES_PER_SECONDS;
    private Long lastTick = 0L;

    @Nullable
    private Renderer renderer = null;
    private final FrameTime frameTime = new FrameTime();

    private Scene scene;
    private volatile boolean debugEnabled = false;

    // Public until full moving to Kotlin
    public Environment _environment = null;
    @Entity
    // Public until full moving to Kotlin
    public Integer _mainLight;

    private boolean isInitialized = false;

    @Nullable
    private Color backgroundColor;

    // Used to track high-level performance metrics for Sceneform
    private final MovingAverageMillisecondsTracker frameTotalTracker =
            new MovingAverageMillisecondsTracker();
    private final MovingAverageMillisecondsTracker frameUpdateTracker =
            new MovingAverageMillisecondsTracker();
    private final MovingAverageMillisecondsTracker frameRenderTracker =
            new MovingAverageMillisecondsTracker();


    /**
     * Constructs a SceneView object and binds it to an Android Context.
     *
     * @param context the Android Context to use
     * @see #SceneView(Context, AttributeSet)
     */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public SceneView(Context context) {
        super(context);
        initialize();
    }

    /**
     * Constructs a SceneView object and binds it to an Android Context.
     *
     * @param context the Android Context to use
     * @param attrs   the Android AttributeSet to associate with
     */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public SceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        // this makes sure that the view's onTouchListener is called.
        if (!super.onTouchEvent(motionEvent)) {
            scene.onTouchEvent(motionEvent);
            // We must always return true to guarantee that this view will receive all touch events.
            // TODO: Update Scene.onTouchEvent to return if it was handled.

            return true;
        }
        return true;
    }

    /**
     * Set the background to a given {@link Drawable}, or remove the background. If the background is
     * a {@link ColorDrawable}, then the background color of the {@link Scene} is set to {@link
     * ColorDrawable#getColor()} (the alpha of the color is ignored). Otherwise, default to the
     * behavior of {@link SurfaceView#setBackground(Drawable)}.
     */
    @Override
    public void setBackground(@Nullable Drawable background) {
        if (background instanceof ColorDrawable) {
            ColorDrawable colorDrawable = (ColorDrawable) background;
            backgroundColor = new Color(colorDrawable.getColor());
            if (renderer != null) {
                renderer.setClearColor(backgroundColor);
            }
        } else {
            backgroundColor = null;
            if (renderer != null) {
                renderer.setDefaultClearColor();
            }
            super.setBackground(background);
        }
    }

    /**
     * Set the background to transparent.
     */
    public void setTransparent(boolean transparent) {
        setZOrderOnTop(transparent);
        getHolder().setFormat(transparent ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE);
        renderer.getFilamentView().setBlendMode(transparent ? View.BlendMode.TRANSLUCENT : View.BlendMode.OPAQUE);
    }

    /**
     * <pre>
     *     Set a higher bound for the frame rate. It is possible
     *     to obtain the higher bound from the {@link com.google.ar.core.Session}.
     *     <code>session.getCameraConfig().getFpsRange().getUpper();</code>.
     *
     *     The default value is 60.
     * </pre>
     *
     * @param maxFramesPerSeconds int
     */
    public void setMaxFramesPerSeconds(int maxFramesPerSeconds) {
        this.maxFramesPerSeconds = maxFramesPerSeconds;
    }

    /**
     * <pre>
     *     Set this value for a finer adjustment of the upper fps value.
     *     Three different modes are supported FULL, HALF, THIRD. As the names
     *     already indicate means FULL, use the value from maxFramesPerSeconds. HALF means
     *     to divide maxFramesPerSeconds by 2 and THIRD means to divide maxFramesPerSeconds by 3.
     *
     *     The default FrameRate is set to FULL.
     * </pre>
     *
     * @param frameRateFactor {@link FrameRate}
     */
    public void setFrameRateFactor(FrameRate frameRateFactor) {
        this.frameRate = frameRateFactor;
    }

    /**
     * @hide
     */
    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;
        Preconditions.checkNotNull(renderer).setDesiredSize(width, height);
    }

    /**
     * Resume Sceneform, which resumes the rendering thread.
     * <p>
     * Typically called from onResume().
     */
    public void resume() throws Exception {
        resumeScene();
    }

    /**
     * Resumes the scene
     */
    protected void resumeScene() throws IllegalStateException {
        if (renderer == null) {
            throw new IllegalStateException("Sceneform requires Android N or later");
        }
        renderer.onResume();
        // Start the drawing when the renderer is resumed.  Remove and re-add the callback
        // to avoid getting called twice.
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    /**
     * Pause Sceneform, which pauses the rendering thread.
     *
     * <p>Typically called from onPause().
     */
    public void pause() {
        pauseScene();
    }

    /**
     * Pause the scene without touching the session
     */
    protected void pauseScene() {
        Choreographer.getInstance().removeFrameCallback(this);
        if (renderer != null) {
            renderer.onPause();
        }
    }

    /**
     * Required to exit Sceneform.
     *
     * <p>Typically called from onDestroy().
     */
    public void destroy() {
        destroyScene();
    }

    /**
     * Destroy the scene without touching the session
     */
    protected void destroyScene() {
        Choreographer.getInstance().removeFrameCallback(this);
        if (renderer != null) {
            renderer.onPause();
        }
        if (scene != null) {
            scene.destroy();
        }
        if (_environment != null) {
            _environment.destroy();
            _environment = null;
        }
        if (_mainLight != null) {
            LightKt.destroy(_mainLight);
            _mainLight = null;
        }
    }

    /**
     * Immediately releases all rendering resources, even if in use.
     *
     * <p>Use this if nothing more will be rendered in this scene or any other, and the memory must be
     * released immediately.
     */
    public static void destroyAllResources() {
        Renderer.destroyAllResources();
    }

    /**
     * Releases rendering resources ready for garbage collection
     *
     * <p>Called every frame to collect unused resources. May be called manually to release resources
     * after rendering has stopped.
     *
     * @return Count of resources currently in use
     */
    public static long reclaimReleasedResources() {
        return Renderer.reclaimReleasedResources();
    }

    /**
     * If enabled, provides various visualizations for debugging.
     *
     * @param enable True to enable debugging visualizations, false to disable it.
     */
    public void enableDebug(boolean enable) {
        debugEnabled = enable;
    }

    /**
     * Indicates whether debugging is enabled for this view.
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Returns the renderer used for this view, or null if the renderer is not setup.
     *
     * @hide Not a public facing API for version 1.0
     */
    @Nullable
    public Renderer getRenderer() {
        return renderer;
    }

    /**
     * Returns the Sceneform Scene created by this view.
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * To capture the contents of this view, designate a {@link Surface} onto which this SceneView
     * should be mirrored. Use {@link android.media.MediaRecorder#getSurface()}, {@link
     * android.media.MediaCodec#createInputSurface()} or {@link
     * android.media.MediaCodec#createPersistentInputSurface()} to obtain the input surface for
     * recording. This will incur a rendering performance cost and should only be set when capturing
     * this view. To stop the additional rendering, call stopMirroringToSurface.
     *
     * @param surface the Surface onto which the rendered scene should be mirrored.
     * @param left    the left edge of the rectangle into which the view should be mirrored on surface.
     * @param bottom  the bottom edge of the rectangle into which the view should be mirrored on
     *                surface.
     * @param width   the width of the rectangle into which the SceneView should be mirrored on surface.
     * @param height  the height of the rectangle into which the SceneView should be mirrored on
     *                surface.
     */
    public void startMirroringToSurface(
            Surface surface, int left, int bottom, int width, int height) {
        if (renderer != null) {
            renderer.startMirroring(surface, left, bottom, width, height);
        }
    }

    /**
     * When capturing is complete, call this method to stop mirroring the SceneView to the specified
     * {@link Surface}. If this is not called, the additional performance cost will remain.
     *
     * <p>The application is responsible for calling {@link Surface#release()} on the Surface when
     * done.
     */
    public void stopMirroringToSurface(Surface surface) {
        if (renderer != null) {
            renderer.stopMirroring(surface);
        }
    }

    /**
     * Initialize the renderer. This creates the Renderer and sets the camera.
     *
     * @see #SceneView(Context, AttributeSet)
     */
    protected void initialize() {
        if (isInitialized) {
            Log.w(TAG, "SceneView already initialized.");
            return;
        }

        if (!AndroidPreconditions.isMinAndroidApiLevel()) {
            Log.e(TAG, "Sceneform requires Android N or later");
            renderer = null;
        } else {
            renderer = new Renderer(this);
            if (backgroundColor != null) {
                renderer.setClearColor(backgroundColor);
            }
            scene = new Scene(this);
            renderer.setCameraProvider(scene.getCamera());

            _mainLight = LightKt.build(
                    new LightManager.Builder(LightManager.Type.SUN)
                            .intensity(LightKt.defaultMainLightIntensity)
                            .castShadows(true));
            renderer.setMainLight(_mainLight);

            // Change the ToneMapper to FILMIC to avoid some over saturated
            // colors, for example material orange 500.
            renderer.getFilamentView().setColorGrading(
                    new ColorGrading
                            .Builder()
                            .toneMapping(ColorGrading.ToneMapping.FILMIC)
                            .build(EngineInstance.getEngine().getFilamentEngine())
            );
        }

        // TODO: We load the environment synchronously until SceneView is Kotlined
//        ViewKt.doOnAttach(this, view -> {
//            LifecycleCoroutineScope lifecycleScope = LifecycleOwnerKt.getLifecycleScope(
//                    androidx.lifecycle.ViewKt.findViewTreeLifecycleOwner(SceneView.this));
//
//            KTXEnvironmentKt.loadEnvironmentAsync(KTXLoader.INSTANCE, getContext(),
//                    DEFAULT_IBL_LOCATION, null, lifecycleScope, environment -> {
//                        SceneViewKt.setEnvironment(SceneView.this, environment);
//                        return null;
//                    });
//            return null;
//        });
        try {
            SceneViewKt.setEnvironment(SceneView.this,
                    ResourceLoaderKt.useBuffer(getContext().getAssets().open(DEFAULT_IBL_LOCATION),
                            (Function1<ByteBuffer, Environment>) buffer ->
                                    KTXEnvironmentKt.createEnvironment(KTXLoader.INSTANCE, buffer)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        isInitialized = true;
    }

    /**
     * Update view-specific logic before for each display frame.
     *
     * @return true if the scene should be updated before rendering.
     * @hide
     */
    protected boolean onBeginFrame(long frameTimeNanos) {
        return true;
    }

    /**
     * Callback that occurs for each display frame. Updates the scene and reposts itself to be called
     * by the choreographer on the next frame.
     *
     * @hide
     */
    @SuppressWarnings("AndroidApiChecker")
    @Override
    public void doFrame(long frameTimeNanos) {
        // Always post the callback for the next frame.
        Choreographer.getInstance().postFrameCallback(this);

        // limit to max fps
        long nanoTime = System.nanoTime();
        long tick = nanoTime / (TimeUnit.SECONDS.toNanos(1) / maxFramesPerSeconds);
        if (lastTick / frameRate.factor() == tick / frameRate.factor())
            return;

        lastTick = tick;

        doFrameNoRepost(frameTimeNanos);
    }

    /**
     * Callback that occurs for each display frame. Updates the scene but does not post a callback
     * request to the choreographer for the next frame. This is used for testing where on-demand
     * renders are needed.
     *
     * @hide
     */
    public void doFrameNoRepost(long frameTimeNanos) {
        // TODO: Display the tracked performance metrics in debug mode.
        if (debugEnabled) {
            frameTotalTracker.beginSample();
        }

        if (onBeginFrame(frameTimeNanos)) {
            doUpdate(frameTimeNanos);
            doRender(frameTimeNanos);
        }

        if (debugEnabled) {
            frameTotalTracker.endSample();
            if ((System.currentTimeMillis() / 1000) % 60 == 0) {
                Log.d(TAG, " PERF COUNTER: frameRender: " + frameRenderTracker.getAverage());
                Log.d(TAG, " PERF COUNTER: frameTotal: " + frameTotalTracker.getAverage());
                Log.d(TAG, " PERF COUNTER: frameUpdate: " + frameUpdateTracker.getAverage());
            }
        }
    }

    private void doUpdate(long frameTimeNanos) {
        if (debugEnabled) {
            frameUpdateTracker.beginSample();
        }

        frameTime.update(frameTimeNanos);

        scene.dispatchUpdate(frameTime);

        if (debugEnabled) {
            frameUpdateTracker.endSample();
        }
    }

    private void doRender(long frameTimeNanos) {
        Renderer renderer = this.renderer;
        if (renderer == null) {
            return;
        }

        if (debugEnabled) {
            frameRenderTracker.beginSample();
        }

        renderer.render(frameTimeNanos, debugEnabled);

        if (debugEnabled) {
            frameRenderTracker.endSample();
        }
    }

    /**
     * <pre>
     *     Further limit the maximal possible frame rate.
     *     If the max frame rate is 60fps a factor of 1 results in 60fps,
     *     a factor of 2 results in 30fps and a factor of 3 results in 20fps.
     *
     *     In overall this prevents any kind of processing with more than the
     *     calculated max frame rate.
     * </pre>
     */
    public enum FrameRate {
        /**
         * divide the maximal allowed frame rate by 1
         */
        FULL(1),
        /**
         * divide the maximal allowed frame rate by 2
         */
        HALF(2),
        /**
         * divide the maximal allowed frame rate by 3
         */
        THIRD(3);

        private final int factor;

        FrameRate(int factor) {
            this.factor = factor;
        }

        public int factor() {
            return this.factor;
        }
    }
}