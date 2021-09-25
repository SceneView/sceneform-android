package com.gorisse.thomas.sceneform.environment

import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.KTXLoader
import com.gorisse.thomas.sceneform.Filament
import com.gorisse.thomas.sceneform.light.destroy
import java.io.Closeable

/**
 * Default Filament indirect light intensity for default camera exposure
 */
const val defaultIndirectLightIntensity = 30_000.0f

/**
 *
 * ### Indirect light and skybox environment for a scene
 *
 * Environments are usually captured as high-resolution HDR equirectangular images and processed by
 * the cmgen tool to generate the data needed by IndirectLight.
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
 * Defines the lighting environment and the skybox for the scene
 *
 *
 * @property indirectLight ### IndirectLight is used to simulate environment lighting.
 * Environment lighting has a two components:
 * - irradiance
 * - reflections (specular component)
 *
 * @property sphericalHarmonics ### Array of 9 * 3 floats
 *
 * @property skybox ### The Skybox is drawn last and covers all pixels not touched by geometry.
 * The Skybox to use to fill untouched pixels, or null to unset the Skybox.
 *
 * @see [IndirectLight]
 * @see [KTXLoader.loadEnvironment]
 * @see [HDRLoader.loadEnvironment]
 */
open class Environment(
    sphericalHarmonics: FloatArray? = null,
    indirectLight: IndirectLight? = null,
    skybox: Skybox? = null
) : Closeable {

    var indirectLight: IndirectLight? = indirectLight
        private set
    var sphericalHarmonics: FloatArray? = sphericalHarmonics
        private set
    var skybox: Skybox? = skybox
        private set

    /**
     * ### Destroys the EnvironmentLights and frees all its associated resources.
     */
    open fun destroy() {
        indirectLight?.destroy()
        indirectLight = null
        sphericalHarmonics = null
        skybox?.destroy()
        skybox = null
    }

    override fun close() = destroy()

    companion object {

        /**
         * ### Convert Environmental HDR's spherical harmonics to Filament spherical harmonics.
         *
         * This conversion is calculated to include the following:
         *     - pre-scaling by SH basis normalization factor [shader optimization]
         *     - sqrt(2) factor coming from keeping only the real part of the basis
         *     [shader optimization]
         *     - 1/pi factor for the diffuse lambert BRDF [shader optimization]
         *     - |dot(n,l)| spherical harmonics [irradiance]
         *     - scaling for convolution of SH function by radially symmetrical SH function
         *     [irradiance]
         */
        internal val SPHERICAL_HARMONICS_IRRADIANCE_FACTORS =
            //  SH coefficients at indices 6 and 7 are swapped between the two implementations.
            floatArrayOf(
                0.282095f, -0.325735f, 0.325735f,
                -0.325735f, 0.273137f, -0.273137f,
                0.078848f, -0.273137f, 0.136569f
            ).let {
                it.mapIndexed { index, value ->
                    // TODO : Check if we still got to swap those indexes
                    // SH coefficients are not in the same order in Filament and Environmental HDR.
                    // SH coefficients at indices 6 and 7 are swapped between the two
                    // implementations.
                    when (index) {
                        6 -> it[7]
                        7 -> it[6]
                        else -> value
                    }
                }
            }
    }
}

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