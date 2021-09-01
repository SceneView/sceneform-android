package com.google.ar.sceneform.extensions

import com.google.android.filament.Skybox

/**
 * Destroys a Skybox and frees all its associated resources.
 */
fun Skybox.destroy() {
    Filament.engine.destroySkybox(this)
}