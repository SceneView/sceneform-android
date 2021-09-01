package com.google.ar.sceneform.extensions

import com.google.android.filament.Texture

/**
 * Destroys a Texture and frees all its associated resources.
 */
fun Texture.destroy() {
    Filament.engine.destroyTexture(this)
}