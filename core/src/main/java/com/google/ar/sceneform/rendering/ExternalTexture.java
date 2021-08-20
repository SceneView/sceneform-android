package com.google.ar.sceneform.rendering;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.google.android.filament.Stream;
import com.google.android.filament.Texture;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import com.google.ar.sceneform.utilities.Preconditions;

/**
 * Creates an Android {@link SurfaceTexture} and {@link Surface} that can be displayed by Sceneform.
 * Useful for displaying video, or anything else that can be drawn to a {@link SurfaceTexture}.
 *
 * <p>The getFilamentEngine OpenGL ES texture is automatically created by Sceneform. Also, {@link
 * SurfaceTexture#updateTexImage()} is automatically called and should not be called manually.
 *
 * <p>Call {@link Material#setExternalTexture(String, ExternalTexture)} to use an ExternalTexture.
 * The material parameter MUST be of type 'samplerExternal'.
 */
public class ExternalTexture {
    private static final String TAG = ExternalTexture.class.getSimpleName();

    @Nullable
    private final SurfaceTexture surfaceTexture;
    @Nullable
    private final Surface surface;

    @Nullable
    private com.google.android.filament.Texture filamentTexture;
    @Nullable
    private Stream filamentStream;

    /**
     * Creates an ExternalTexture with a new Android {@link SurfaceTexture} and {@link Surface}.
     */
    @SuppressWarnings("initialization")
    public ExternalTexture() {
        // Create the Android surface texture.
        SurfaceTexture surfaceTexture = new SurfaceTexture(0);
        surfaceTexture.detachFromGLContext();
        this.surfaceTexture = surfaceTexture;

        // Create the Android surface.
        this.surface = new Surface(surfaceTexture);

        // Create the filament stream.
        this.filamentStream = new Stream.Builder()
                .stream(surfaceTexture)
                .build(EngineInstance.getEngine().getFilamentEngine());
        // Create the filament texture.
        this.filamentTexture = new Texture.Builder()
                .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                .format(Texture.InternalFormat.RGB8)
                .build(EngineInstance.getEngine().getFilamentEngine());
        this.filamentTexture.setExternalStream(EngineInstance.getEngine().getFilamentEngine()
                , this.filamentStream);

        ResourceManager.getInstance()
                .getExternalTextureCleanupRegistry()
                .register(this, new CleanupCallback(this.filamentTexture, this.filamentStream));
    }

    /**
     * Creates an ExternalTexture from an OpenGL ES textureId without a SurfaceTexture. For internal
     * use only.
     */
    public ExternalTexture(int textureId, int width, int height) {
        // Explicitly set the surface, surfaceTexture and stream to null, since they are unused in this case.
        this.surfaceTexture = null;
        this.surface = null;
        this.filamentStream = null;

        this.filamentTexture = new Texture
                .Builder()
                .importTexture(textureId)
                .width(width)
                .height(height)
                .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                .format(Texture.InternalFormat.RGB8)
                .build(EngineInstance.getEngine().getFilamentEngine());

        ResourceManager.getInstance()
                .getExternalTextureCleanupRegistry()
                .register(this, new CleanupCallback(this.filamentTexture, null));
    }

    /**
     * Gets the surface texture created for this ExternalTexture.
     */
    public SurfaceTexture getSurfaceTexture() {
        return Preconditions.checkNotNull(surfaceTexture);
    }

    /**
     * Gets the surface created for this ExternalTexture that draws to {@link #getSurfaceTexture()}
     */
    public Surface getSurface() {
        return Preconditions.checkNotNull(surface);
    }

    public Texture getFilamentTexture() {
        return Preconditions.checkNotNull(filamentTexture);
    }

    public Stream getFilamentStream() {
        return Preconditions.checkNotNull(filamentStream);
    }

    /**
     * Cleanup filament objects after garbage collection
     */
    private static final class CleanupCallback implements Runnable {
        @Nullable
        private final Texture filamentTexture;
        @Nullable
        private final Stream filamentStream;

        CleanupCallback(com.google.android.filament.Texture filamentTexture, Stream filamentStream) {
            this.filamentTexture = filamentTexture;
            this.filamentStream = filamentStream;
        }

        @Override
        public void run() {
            AndroidPreconditions.checkUiThread();

            IEngine engine = EngineInstance.getEngine();
            if (engine == null || !engine.isValid()) {
                return;
            }
            if (filamentTexture != null) {
                engine.destroyTexture(filamentTexture);
            }

            if (filamentStream != null) {
                engine.destroyStream(filamentStream);
            }
        }
    }
}
