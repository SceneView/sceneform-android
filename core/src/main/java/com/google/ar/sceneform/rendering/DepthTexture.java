package com.google.ar.sceneform.rendering;

import android.media.Image;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.filament.Texture;
import com.google.ar.sceneform.utilities.AndroidPreconditions;

public class DepthTexture {
    @Nullable private com.google.android.filament.Texture filamentTexture;
    private Handler handler = new Handler(Looper.myLooper());

    public DepthTexture(int width, int height) {
        filamentTexture = new com.google.android.filament.Texture.Builder()
                .width(width)
                .height(height)
                .sampler(com.google.android.filament.Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.RG8)
                .levels(1)
                .build(EngineInstance.getEngine().getFilamentEngine());

        ResourceManager.getInstance()
                .getDepthTextureCleanupRegistry()
                .register(this, new CleanupCallback(filamentTexture));
    }

    @Nullable
    public Texture getFilamentTexture() {
        return filamentTexture;
    }

    public void updateDepthTexture(Image depthImage) {
        if (filamentTexture == null)
            return;

        IEngine engine = EngineInstance.getEngine();

        filamentTexture.setImage(
                engine.getFilamentEngine(),
                0,
                new Texture.PixelBufferDescriptor(
                        depthImage.getPlanes()[0].getBuffer(),
                        Texture.Format.RG,
                        Texture.Type.UBYTE,
                        1,
                        0,
                        0,
                        0,
                        handler,
                        null
                )
        );
        depthImage.close();
    }

    /**
     * Cleanup filament objects after garbage collection
     */
    private static final class CleanupCallback implements Runnable {
        @Nullable private final com.google.android.filament.Texture filamentTexture;

        CleanupCallback(com.google.android.filament.Texture filamentTexture) {
            this.filamentTexture = filamentTexture;
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
        }
    }
}
