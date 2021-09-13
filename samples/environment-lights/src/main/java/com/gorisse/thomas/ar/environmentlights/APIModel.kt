package com.gorisse.thomas.ar.environmentlights

import android.content.Context
import android.net.Uri
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable

data class APIModel(val name: String, val url: String, val scale: Float = 1.0f) {

    var renderable: Renderable? = null

    fun load(context: Context, result: (Renderable) -> Unit) {
        if (renderable == null) {
            ModelRenderable.builder()
                .setSource(context, Uri.parse(url.absoluteUrl))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept {
                    renderable = it
                    result(it)
                }
        } else {
            result(renderable!!)
        }
    }

    override fun toString(): String {
        return name
    }

    private val String.absoluteUrl
        get() = takeIf { it.isAbsolute } ?: "$serverUrl/$modelsPath/$this"
}