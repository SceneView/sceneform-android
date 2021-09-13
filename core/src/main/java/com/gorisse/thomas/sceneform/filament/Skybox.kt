package com.gorisse.thomas.sceneform.filament

import com.google.android.filament.Skybox

/**
 * @see Skybox.Builder.build
 */
fun Skybox.Builder.build(): Skybox = build(Filament.engine)

/**
 * Destroys a Skybox and frees all its associated resources.
 */
fun Skybox.destroy() {
    Filament.engine.destroySkybox(this)
}