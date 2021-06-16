package com.google.ar.sceneform.ux;

import android.content.Context;
import android.media.MediaPlayer;

import androidx.annotation.Nullable;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.PlaneFactory;
import com.google.ar.sceneform.rendering.Renderable;

/**
 * Node that can show a video by passing a {@link MediaPlayer} instance. Note that
 * VideoNode does not manage video playback by itself (e.g. starting the video).
 * <p>
 * Filtering out a specific color in the video is also supported by
 * defining a chroma key color.
 * </p>
 * <p>
 * Optionally an {@link ExternalTexture} can be passed if multiple VideoNode instances
 * need to render the exact same instance of a video. This will also improve performance
 * dramatically instead of rendering each instance separately.
 * </p>
 */
public class VideoNode extends Node {
    private static final String MATERIAL_PARAMETER_VIDEO_TEXTURE = "videoTexture";
    private static final String MATERIAL_PARAMETER_CHROMA_KEY_COLOR = "keyColor";

    private final MediaPlayer player;
    private final ExternalTexture texture;
    private final Color chromaKeyColor;
    private final ErrorListener errorListener;

    /**
     * Create a new VideoNode for showing a video from a MediaPlayer instance inside a node on an
     * adjusted plane renderable
     *
     * @param context       Resources context
     * @param player        The video media player to render on the plane node
     * @param errorListener Loading error
     */
    public VideoNode(Context context, MediaPlayer player, @Nullable ErrorListener errorListener) {
        this(context, player, null, null, errorListener);
    }

    /**
     * Create a new VideoNode for showing a video from a MediaPlayer instance inside a node on an
     * adjusted plane renderable with video transparency color set to chromaKeyColor.
     *
     * @param context        Resources context
     * @param player         The video media player to render on the plane node
     * @param chromaKeyColor Chroma Key color to made the video transparent from
     * @param errorListener  Loading error
     */
    public VideoNode(Context context, MediaPlayer player, @Nullable Color chromaKeyColor,
                     @Nullable ErrorListener errorListener) {
        this(context, player, chromaKeyColor, null, errorListener);
    }

    /**
     * Create a new VideoNode for showing a video from a MediaPlayer instance inside a node on your
     * own material renderable and material with video transparency color set to chromaKeyColor
     *
     * @param context        Resources context
     * @param player         The video media player to render on the plane node
     * @param chromaKeyColor Chroma Key color to made the video transparent from
     * @param texture        Custom ExternalTexture for using your own renderable and material.
     *                       Null for default Plane shape renderable.
     * @param errorListener  Loading error
     */
    public VideoNode(Context context, MediaPlayer player, @Nullable Color chromaKeyColor,
                     @Nullable ExternalTexture texture, @Nullable ErrorListener errorListener) {
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
                    material.setExternalTexture(MATERIAL_PARAMETER_VIDEO_TEXTURE, texture);
                    if (chromaKeyColor != null) {
                        material.setFloat4(MATERIAL_PARAMETER_CHROMA_KEY_COLOR, chromaKeyColor);
                    }
                    final Renderable renderable = createModel(player, material);
                    setRenderable(renderable);
                })
                .exceptionally(throwable -> {
                    onError(throwable);
                    return null;
                });
    }

    /**
     * Create the renderable on which the video will be displayed.
     * Override this function for using a custom Model.
     * Default return is a centered plane
     *
     * @param player   the media player
     * @param material the material to apply to your custom model
     * @return the the renderable with the applied material.
     */
    public Renderable createModel(MediaPlayer player, Material material) {
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

    /**
     * Listener for VideoNode loading
     */
    public interface ErrorListener {
        /**
         * Something wrong happened during the VideoNode instantiation
         *
         * @param throwable corresponding error
         */
        void onError(Throwable throwable);
    }
}
