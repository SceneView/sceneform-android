package com.gorisse.thomas.sceneform.filament

import com.google.android.filament.IndirectLight

/**
 * @see IndirectLight.Builder.build
 */
fun IndirectLight.Builder.build(): IndirectLight = build(Filament.engine)

/**
 * Destroys an IndirectLight and frees all its associated resources.
 */
fun IndirectLight.destroy() {
    Filament.engine.destroyIndirectLight(this)
}