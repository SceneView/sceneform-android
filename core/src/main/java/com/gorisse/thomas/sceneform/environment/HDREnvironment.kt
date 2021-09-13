package com.gorisse.thomas.sceneform.environment

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.gorisse.thomas.sceneform.filament.Filament
import com.gorisse.thomas.sceneform.filament.build
import com.gorisse.thomas.sceneform.filament.use
import com.gorisse.thomas.sceneform.util.fileBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.Buffer

class HDREnvironment(
    val reflections: Texture? = null,
    val irradiance: FloatArray? = null,
    val intensity: Float? = null,
    val environment: Texture? = null
) : Environment(
    indirectLight = IndirectLight.Builder().apply {
        reflections?.let {
            reflections(it)
        }
        irradiance?.let {
            irradiance(3, it)
        }
        intensity?.let {
            intensity(it)
        }
    }.build(),
    sphericalHarmonics = irradiance,
    skybox = environment?.let {
        Skybox.Builder().apply {
            environment(it)
        }.build()
    }
) {

    companion object {
        @JvmStatic
        val defaultIblFilter = { cubemap: Texture ->
            Filament.iblPrefilter.specularFilter(cubemap)
        }
    }
}

/**
 * ### Utility for decoding and producing environment resources from an HDR file
 *
 * [Documentation][HDRLoader.createEnvironment]
 *
 * @param hdrFileLocation the hdr file location
 * [Documentation][com.google.ar.sceneform.util.ResourceLoader.fileBuffer]
 * @param iblFilter [Documentation][HDRLoader.createEnvironment]
 *
 * @return [Documentation][HDRLoader.createEnvironment]
 */
@JvmOverloads
suspend fun HDRLoader.loadEnvironment(
    context: Context,
    hdrFileLocation: String,
    iblFilter: ((Texture) -> Texture)? = HDREnvironment.defaultIblFilter
): HDREnvironment? {
    var environment: Environment? = null
    return try {
        context.fileBuffer(hdrFileLocation)?.let { buffer ->
            withContext(Dispatchers.Main) {
                createEnvironment(buffer, iblFilter)?.also { environment = it }
            }
        }
    } finally {
        // TODO: See why the finally is called before the onDestroy()
//        environment?.destroy()
    }
}

/**
 * ### Utility for decoding and producing environment resources from an HDR file
 *
 * For Java compatibility usage.
 *
 * Kotlin developers should use [HDRLoader.loadEnvironment]
 *
 * [Documentation][HDRLoader.loadEnvironment]
 *
 */
fun HDRLoader.loadEnvironmentAsync(
    context: Context,
    hdrFileLocation: String,
    iblFilter: ((Texture) -> Texture)? = HDREnvironment.defaultIblFilter,
    coroutineScope: LifecycleCoroutineScope,
    result: (HDREnvironment?) -> Unit
) = coroutineScope.launchWhenCreated {
    result(
        HDRLoader.loadEnvironment(
            context,
            hdrFileLocation,
            iblFilter
        )
    )
}

/**
 * ### Utility for decoding and producing environment resources from an HDR file
 *
 * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
 *
 * @param hdrBuffer The content of the HDR File
 * @param iblFilter A filter to apply to the resulting indirect light reflexions texture.
 * Default generates a specular prefiltered cubemap reflection texture.
 *
 * @return the generated environment indirect light and skybox from the hdr
 *
 * @see HDRLoader.createTexture
 */
@JvmOverloads
fun HDRLoader.createEnvironment(
    hdrBuffer: Buffer,
    iblFilter: ((Texture) -> Texture)? = HDREnvironment.defaultIblFilter
) = createTexture(Filament.engine, hdrBuffer)?.use { hdrTexture ->
    Filament.iblPrefilter.equirectangularToCubemap(hdrTexture)
}?.let { cubemap ->
    val reflections = iblFilter?.invoke(cubemap)
    HDREnvironment(reflections = reflections, environment = cubemap)
}

/**
 * ## IBLPrefilter creates and initializes GPU state common to all environment map filters.
 * Typically, only one instance per filament Engine of this object needs to exist.
 *
 * @see [IBLPrefilterContext]
 */
class IBLPrefilter(engine: Engine) {

    /**
     * ### Created IBLPrefilterContext, keeping it around if several cubemap will be processed.
     */
    val context by lazy { IBLPrefilterContext(engine) }

    /**
     * EquirectangularToCubemap is use to convert an equirectangluar image to a cubemap.
     *
     * Creates a EquirectangularToCubemap processor.
     */
    private val equirectangularToCubemap by lazy {
        IBLPrefilterContext.EquirectangularToCubemap(
            context
        )
    }

    /**
     * ### Converts an equirectangular image to a cubemap.
     *
     * @param equirect Texture to convert to a cubemap.
     * - Can't be null.
     * - Must be a 2d texture
     * - Must have equirectangular geometry, that is width == 2*height.
     * - Must be allocated with all mip levels.
     * - Must be SAMPLEABLE
     *
     * @return the cubemap texture
     *
     * @see [EquirectangularToCubemap]
     */
    fun equirectangularToCubemap(equirect: Texture) = equirectangularToCubemap.run(equirect)

    /**
     * Created specular (reflections) filter. This operation generates the kernel, so it's
     * important to keep it around if it will be reused for several cubemaps.
     * An instance of SpecularFilter is needed per filter configuration. A filter configuration
     * contains the filter's kernel and sample count.
     */
    private val specularFilter by lazy { IBLPrefilterContext.SpecularFilter(context) }

    /**
     * ### Generates a prefiltered cubemap.
     *
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     *
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     *
     * @param skybox Environment cubemap.
     * This cubemap is SAMPLED and have all its levels allocated.
     *
     * @return the reflections texture
     */
    fun specularFilter(skybox: Texture) = specularFilter.run(skybox)
}