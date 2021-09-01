package com.google.ar.sceneform.extensions

import com.google.android.filament.IndirectLight

/**
 * Destroys an IndirectLight and frees all its associated resources.
 */
fun IndirectLight.destroy() {
    Filament.engine.destroyIndirectLight(this)
}