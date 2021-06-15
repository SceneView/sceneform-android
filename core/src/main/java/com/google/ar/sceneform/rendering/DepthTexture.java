package com.google.ar.sceneform.rendering;

import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.filament.Texture;
import com.google.ar.sceneform.utilities.AndroidPreconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

        /*Log.d("DepthTexture", "Format: " + depthImage.getFormat());
        Log.d("DepthTexture", "Planes: " + depthImage.getPlanes().length);
        Log.d("DepthTexture", "Width: " + depthImage.getWidth());
        Log.d("DepthTexture", "Height: " + depthImage.getHeight());*/

        IEngine engine = EngineInstance.getEngine();

        Image.Plane plane = depthImage.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();

        filamentTexture.setImage(
                engine.getFilamentEngine(),
                0,
                new Texture.PixelBufferDescriptor(
                        buffer,
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
