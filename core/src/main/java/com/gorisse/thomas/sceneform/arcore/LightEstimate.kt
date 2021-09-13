package com.gorisse.thomas.sceneform.arcore

import com.google.android.filament.IndirectLight
import com.google.android.filament.Texture
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.max
import com.google.android.filament.utils.pow
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import com.gorisse.thomas.sceneform.environment.Environment
import com.gorisse.thomas.sceneform.environment.HDREnvironment
import com.gorisse.thomas.sceneform.filament.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ARCore will estimate lighting to provide directional light, ambient spherical harmonics,
 * and reflection cubemap estimation
 */
data class LightEstimationConfig @JvmOverloads constructor(
    var mode: Config.LightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR,
    /**
     * true if the AR Core reflection cubemap should be used.
     * false for using the default/static/fake environment reflections
     */
    var environmentalHdrReflections: Boolean = true,
    var environmentalHdrIrradiance: Boolean = true,
    var environmentalHdrSpecularFilter: Boolean = true,
    var environmentalHdrMainLightDirection: Boolean = true,
    var environmentalHdrMainLightIntensity: Boolean = true
)

fun Frame.environmentEstimate(
    config: LightEstimationConfig,
    baseEnvironment: Environment?,
    previousEstimate: Environment?,
    // ARCore's light estimation uses unit-less (relative) values while Filament uses a physically
    // based camera model with lux or lumen values.
    // In order to keep the "standard" Filament behavior we scale AR Core values.
    cameraExposureFactor: Float
) = lightEstimate?.takeIf { it.state == LightEstimate.State.VALID }?.let { lightEstimate ->
    when (config.mode) {
        Config.LightEstimationMode.AMBIENT_INTENSITY -> lightEstimate.ambientIntensityEnvironment(
            baseEnvironment,
            cameraExposureFactor
        )
        Config.LightEstimationMode.ENVIRONMENTAL_HDR -> lightEstimate.environmentalHdrEnvironment(
            baseEnvironment,
            previousEstimate as? HDREnvironment,
            cameraExposureFactor,
            config.environmentalHdrReflections,
            config.environmentalHdrIrradiance,
            config.environmentalHdrSpecularFilter
        )
        else -> null
    }
}

fun LightEstimate.ambientIntensityEnvironment(
    baseEnvironment: Environment?,
    // ARCore's light estimation uses unit-less (relative) values while Filament uses a physically
    // based camera model with lux or lumen values.
    // In order to keep the "standard" Filament behavior we scale AR Core values.
    cameraExposureFactor: Float
) = Environment(
    indirectLight = IndirectLight.Builder().apply {
        baseEnvironment?.indirectLight?.reflectionsTexture?.let {
            reflections(it)
        }

        // Sets light estimate to modulate the scene lighting and intensity. The rendered lights
        // will use a combination of these values and the color and intensity of the lights.
        // A value of a white colorCorrection and pixelIntensity of 1 mean that no changes are
        // made to the light settings.
        val colorCorrection = FloatArray(4).apply {
            getColorCorrection(this, 0)
        }.map {
            // Rendering in linear space so first convert this value to linear space by rising
            // to the power 2.2. Normalize the result by dividing it by 0.18, which is middle
            // gray in linear space.
            pow(it, 2.2f) / 0.18f
        }
        colorCorrection.let { (r, g, b, pixelIntensity) ->
            // Scale and bias the estimate to avoid over darkening. Modulates ambient color with
            // modulation factor.
            // irradianceData must have at least one vector of three floats.
            baseEnvironment?.sphericalHarmonics?.let { sphericalHarmonics ->
                val colorCorrections = Color(r, g, b)
                irradiance(
                    3,
                    FloatArray(sphericalHarmonics.size) { index ->
                        when (index) {
                            in 0..2 -> {
                                // Use the RGB scale factors (components 0-2) to match the color
                                // of the light in the scene
                                // TODO: Should we apply the cameraExposureFactor?
                                sphericalHarmonics[index] * colorCorrections[index]
                            }
                            else -> sphericalHarmonics[index]
                        }
                    })
            }
            // TODO: Should we also apply the intensity to the directional light?
            baseEnvironment?.indirectLight?.intensity?.let { baseIntensity ->
                intensity(baseIntensity * pixelIntensity * cameraExposureFactor)
            }
        }
    }.build(),
    sphericalHarmonics = baseEnvironment?.sphericalHarmonics
)

fun LightEstimate.environmentalHdrEnvironment(
    baseEnvironment: Environment?,
    previousEstimate: HDREnvironment?,
    // ARCore's light estimation uses unit-less (relative) values while Filament uses a physically
    // based camera model with lux or lumen values.
    // In order to keep the "standard" Filament behavior we scale AR Core values.
    cameraExposureFactor: Float,
    withReflections: Boolean,
    withIrradiance: Boolean,
    withSpecularFilter: Boolean
) = HDREnvironment(
    reflections = if (withReflections) {
        acquireEnvironmentalHdrCubeMap()?.let { arImages ->
            val width = arImages[0].width
            val height = arImages[0].height
            val faceOffsets = IntArray(arImages.size)
            val buffer = Texture.PixelBufferDescriptor(
                ByteBuffer.allocateDirect(
                    width * height *
                            arImages.size *
                            // RGB Bytes per pixel
                            6 * 2
                ).apply {
                    // Use the device hardware's native byte order
                    order(ByteOrder.nativeOrder())

                    val rgbaBytes = ByteArray(8) // ARGB Bytes per pixel
                    arImages.forEachIndexed { index, image ->
                        faceOffsets[index] = position()
                        image.planes[0].buffer.let { imageBuffer ->
                            while (imageBuffer.hasRemaining()) {
                                // Only take the RGB channels
                                put(rgbaBytes.apply {
                                    imageBuffer.get(this)
                                } // Skip the Alpha channel
                                    .sliceArray(0..5))
                            }
                        }
                        image.close()
                    }
                    flip()
                },
                Texture.Format.RGB,
                Texture.Type.HALF
            )

            // Reuse the previous texture instead of creating a new one for performance and memory
            val texture = previousEstimate?.reflections?.takeIf {
                it.getWidth(0) == width && it.getHeight(0) == height
            } ?: Texture.Builder()
                .width(width)
                .height(height)
                .levels(0xff)
                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                .format(Texture.InternalFormat.R11F_G11F_B10F)
                .build(Filament.engine)

            texture.apply {
                if (withSpecularFilter) {
                    generatePrefilterMipmap(Filament.engine,
                        buffer,
                        faceOffsets,
                        Texture.PrefilterOptions().apply {
                            mirror = false
                        })
                } else {
                    setImage(Filament.engine, 0, buffer, faceOffsets)
                }
            }
        }
    } else {
        baseEnvironment?.indirectLight?.reflectionsTexture
    },
    irradiance = if (withIrradiance) {
        environmentalHdrAmbientSphericalHarmonics?.let { sphericalHarmonics ->
            sphericalHarmonics.mapIndexed { index, sphericalHarmonic ->
                sphericalHarmonic *
                        // Convert Environmental HDR's spherical harmonics to Filament
                        // irradiance spherical harmonics.
                        // TODO: Should we apply the cameraExposureFactor?
                        Environment.SPHERICAL_HARMONICS_IRRADIANCE_FACTORS[index / 3]
            }.toFloatArray()
        }
    } else {
        baseEnvironment?.sphericalHarmonics
    },
    intensity = baseEnvironment?.indirectLight?.intensity)

fun Frame.mainLightInfluenceEstimate(
    baseLight: Light,
    config: LightEstimationConfig,
    // ARCore's light estimation uses unit-less (relative) values while Filament uses a physically
    // based camera model with lux or lumen values.
    // In order to keep the "standard" Filament behavior we scale AR Core values.
    cameraExposureFactor: Float
) = lightEstimate?.takeIf { it.state == LightEstimate.State.VALID }?.let { lightEstimate ->
    when (config.mode) {
        Config.LightEstimationMode.ENVIRONMENTAL_HDR ->
            lightEstimate.environmentalHdrMainLightInfluence(
                baseLight,
                cameraExposureFactor,
                config.environmentalHdrMainLightDirection,
                config.environmentalHdrMainLightIntensity
            )
        else -> null
    }
}

fun LightEstimate.environmentalHdrMainLightInfluence(
    baseLight: Light,
    // ARCore's light estimation uses unit-less (relative) values while Filament uses a physically
    // based camera model with lux or lumen values.
    // In order to keep the "standard" Filament behavior we scale AR Core values.
    cameraExposureFactor: Float,
    withDirection: Boolean = true,
    withIntensity: Boolean = true
): (Light.() -> Unit) = {
    if (withDirection) {
        environmentalHdrMainLightDirection.let { (x, y, z) ->
            // TODO (or not):
            //  If light is detected as shining up from below, we flip the Y
            //  component so that we always end up with a shadow on the ground to
            //  fulfill UX requirements.
            direction = Direction(-x, -y, -z)
        }
    }
    if (withIntensity) {
        // Returns the intensity of the main directional light based on the inferred
        // Environmental HDR Lighting Estimation. All return values are larger or equal to zero.
        // The color correction method uses the green channel as reference baseline and scales the
        // red and blue channels accordingly. In this way the overall intensity will not be
        // significantly changed
        environmentalHdrMainLightIntensity.let { (redIntensity, greenIntensity, blueIntensity) ->
            // TODO : Do we have to apply the gray scale and/or convert to linear?
            val colorIntensities = Float3(redIntensity, greenIntensity, blueIntensity) *
                    cameraExposureFactor

            max(colorIntensities).takeIf { it > 0 }?.let { maxIntensity ->
                // Scale max r or b or g value and fit in range [0, 1)
                // Note that if we were not using the HDR cubemap from ARCore for specular
                // lighting, we would be adding a specular contribution from the main light
                // here.
                val colorIntensitiesFactors = (colorIntensities / maxIntensity)
                color = baseLight.color * colorIntensitiesFactors
                // TODO : Should we apply the intensity in addition to the color correction?
//                intensity = baseLight.intensity *
//                        colorIntensitiesFactors.toFloatArray().average().toFloat()
            }
        }
    }
}