package com.gorisse.thomas.sceneform

import com.google.ar.core.Config
import com.google.ar.sceneform.ArSceneView
import com.gorisse.thomas.sceneform.light.*


/**
 * ### ARCore light estimation configuration
 *
 * ARCore estimate lighting to provide directional light, ambient spherical harmonics,
 * and reflection cubemap estimation
 *
 * Light bounces off of surfaces differently depending on whether the surface has specular
 * (highly reflective) or diffuse (not reflective) properties.
 * For example, a metallic ball will be highly specular and reflect its environment, while
 * another ball painted a dull matte gray will be diffuse. Most real-world objects have a
 * combination of these properties — think of a scuffed-up bowling ball or a well-used credit
 * card.
 *
 * Reflective surfaces also pick up colors from the ambient environment. The coloring of an
 * object can be directly affected by the coloring of its environment. For example, a white ball
 * in a blue room will take on a bluish hue.
 *
 * The main directional light API calculates the direction and intensity of the scene's
 * main light source. This information allows virtual objects in your scene to show reasonably
 * positioned specular highlights, and to cast shadows in a direction consistent with other
 * visible real objects.
 *
 * @see LightEstimationConfig.REALISTIC
 * @see LightEstimationConfig.SPECTACULAR
 * @see LightEstimationConfig.AMBIENT_INTENSITY
 */
var ArSceneView.lightEstimationConfig: LightEstimationConfig
    get() = _lightEstimationConfig
    set(value) {
        if (_lightEstimationConfig != value) {
            if (sessionConfig != null && value.mode != sessionConfig?.lightEstimationMode) {
                setSessionConfig(sessionConfig?.apply {
                    lightEstimationMode = value.mode
                }, true)
            }
            mainLight?.intensity = when (value.mode) {
                Config.LightEstimationMode.DISABLED -> defaultMainLightIntensity
                else -> sunnyDayMainLightIntensity
            }
            estimatedEnvironmentLights = null
            _lightEstimationConfig = value
        }
    }

/**
 * ### The environment and main light that are estimated by AR Core to render the scene.
 *
 * - Environment handles a reflections, indirect lighting and skybox.
 *
 * - ARCore will estimate the direction, the intensity and the color of the light
 */
var ArSceneView.estimatedEnvironmentLights: EnvironmentLightsEstimate?
    get() = _estimatedEnvironmentLights
    internal set(value) {
        val environment = value?.environment ?: environment
        if (renderer?.getEnvironment() != environment) {
            if (_estimatedEnvironmentLights?.environment != environment) {
                _estimatedEnvironmentLights?.environment?.destroy()
            }
            renderer?.setEnvironment(environment)
        }
        val mainLight = value?.mainLight ?: mainLight
        if (renderer?.getMainLight() != mainLight) {
            if (_estimatedEnvironmentLights?.mainLight != mainLight) {
                _estimatedEnvironmentLights?.mainLight?.destroy()
            }
            renderer?.setMainLight(mainLight)
        }
        _estimatedEnvironmentLights = value
    }