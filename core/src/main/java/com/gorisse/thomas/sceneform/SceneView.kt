package com.gorisse.thomas.sceneform

import com.google.android.filament.utils.*
import com.google.ar.sceneform.SceneView
import com.gorisse.thomas.sceneform.environment.Environment
import com.gorisse.thomas.sceneform.light.Light

/**
 * ### Defines the lighting environment and the skybox of the scene
 *
 * Environments are usually captured as high-resolution HDR equirectangular images and processed
 * by the cmgen tool to generate the data needed by IndirectLight.
 *
 * You can also process an hdr at runtime but this is more consuming.
 *
 * - Currently IndirectLight is intended to be used for "distant probes", that is, to represent
 * global illumination from a distant (i.e. at infinity) environment, such as the sky or distant
 * mountains.
 * Only a single IndirectLight can be used in a Scene. This limitation will be lifted in the future.
 *
 * - When added to a Scene, the Skybox fills all untouched pixels.
 *
 * @see [KTXLoader.loadEnvironment]
 * @see [HDRLoader.loadEnvironment]
 */
var SceneView.environment: Environment?
    get() = _environment
    set(value) {
        scene.renderer?.setEnvironment(value)
//    _environment?.destroy()
        _environment = value
    }

/**
 * ### The main directional light of the scene
 *
 * Usually the Sun.
 */
var SceneView.mainLight: Light?
    get() = _mainLight
    set(value) {
        _mainLight?.let { scene.renderer?.removeLight(it) }
        value?.let { scene.renderer?.addLight(value) }
        _mainLight = value
    }