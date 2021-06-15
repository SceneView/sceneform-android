package com.google.ar.sceneform.ux;

import android.content.Context;
import android.media.MediaPlayer;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.PlaneFactory;

import androidx.annotation.Nullable;

/**
 * Node that can show a video.
 */
public class VideoNode extends Node {
    private static final String KEY_VIDEO_TEXTURE = "videoTexture";
    private static final String KEY_CHROMA_KEY_COLOR = "keyColor";

    private final MediaPlayer player;
    private final ExternalTexture texture;
    private final Color chromaKeyColor;
    private final ErrorListener errorListener;

    public VideoNode(Context context, MediaPlayer player, @Nullable ErrorListener errorListener) {
        this(context, player, null, null, errorListener);
    }

    public VideoNode(Context context, MediaPlayer player, @Nullable Color chromaKeyColor,
                     @Nullable ErrorListener errorListener) {
        this(context, player, null, chromaKeyColor, errorListener);
    }

    public VideoNode(Context context, MediaPlayer player, @Nullable ExternalTexture texture,
                     @Nullable Color chromaKeyColor, @Nullable ErrorListener errorListener) {
        this.player = player;
        this.texture = texture != null ? texture : new ExternalTexture();
        this.chromaKeyColor = chromaKeyColor;
        this.errorListener = errorListener;
        init(context);
    }

    private void init(Context context) {
        player.setSurface(texture.getSurface());
        final int rawResId;
        if (chromaKeyColor != null) {
            rawResId = R.raw.external_chroma_key_video_material;
        } else {
            rawResId = R.raw.external_plain_video_material;
        }
        Material.builder()
                .setSource(context, rawResId)
                .build()
                .thenAccept(material -> {
                    material.setExternalTexture(KEY_VIDEO_TEXTURE, texture);
                    if (chromaKeyColor != null) {
                        material.setFloat4(KEY_CHROMA_KEY_COLOR, chromaKeyColor);
                    }
                    final ModelRenderable renderable = makeModelRenderable(player, material);
                    renderable.setShadowCaster(chromaKeyColor == null);
                    setRenderable(renderable);
                })
                .exceptionally(throwable -> {
                    onError(throwable);
                    return null;
                });
    }

    private ModelRenderable makeModelRenderable(MediaPlayer player, Material material) {
        final int width = player.getVideoWidth();
        final int height = player.getVideoHeight();
        final float x;
        final float y;
        if (width >= height) {
            x = 1.0f;
            y = (float) height / (float) width;
        } else {
            x = (float) width / (float) height;
            y = 1.0f;
        }
        return PlaneFactory
                .makePlane(
                        new Vector3(x, y, 0.0f),
                        new Vector3(0.0f, y / 2.0f, 0.0f),
                        material
                );
    }

    private void onError(Throwable throwable) {
        if (errorListener != null) {
            errorListener.onError(throwable);
        }
    }

    public interface ErrorListener {
        void onError(Throwable throwable);
    }
}
