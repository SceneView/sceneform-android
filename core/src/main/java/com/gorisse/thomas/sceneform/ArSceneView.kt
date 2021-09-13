package com.gorisse.thomas.sceneform

import com.google.ar.sceneform.ArSceneView
import com.gorisse.thomas.sceneform.environment.Environment
import com.gorisse.thomas.sceneform.filament.Light
import com.gorisse.thomas.sceneform.filament.clone
import com.gorisse.thomas.sceneform.filament.destroy

/**
 * ### The environment that is estimated by AR Core to render the scene.
 *
 * Environment handles a reflections, indirect lighting and skybox
 */
var ArSceneView.estimatedEnvironment: Environment?
    get() = _estimatedEnvironment
    internal set(value) {
        renderer?.setEnvironment(value ?: environment)
        _estimatedEnvironment?.destroy()
        _estimatedEnvironment = value
    }

/**
 * ### The main light that is estimated by AR Core to render the scene.
 *
 * Ar Core will estimate the direction, the intensity and the color of the light
 */
var ArSceneView.estimatedMainLight: Light?
    get() = _estimatedMainLight
    internal set(value) {
        renderer?.setMainDirectionalLight(value ?: mainDirectionalLight)
        _estimatedMainLight?.destroy()
        _estimatedMainLight = value
    }

// TODO : Move to internal when ArSceneView is fully Kotlined
var ArSceneView.estimatedMainLightInfluence: (Light.() -> Unit)?
    get() = _estimatedMainLightInfluence
    internal set(value) {
        _estimatedMainLightInfluence = value
        if (value != null) {
            val estimatedMainLight = estimatedMainLight
                ?: mainDirectionalLight?.clone()?.also {
                    estimatedMainLight = it
                }
            estimatedMainLight?.apply(value)
        } else {
            estimatedMainLight = null
        }
    }