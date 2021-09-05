package com.google.ar.sceneform.extensions

import android.content.res.AssetManager
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.ar.sceneform.utilities.useBuffer

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

/**
 * ### Utility for decoding an HDR file and producing Filament environment resources.
 *
 * Consumes the content of an HDR file and optionally produces your needed results
 *
 * @param hdrFileName the relative asset file location
 * @param ibl the generated indirect light from the hdr with a specular filtered reflection texture
 * applied.
 * `null` if you don't need it
 * @param iblFilter a filter to apply to the resulting indirect light reflexions texture
 * @param skybox the generated environment map.
 * `null` if you don't need it
 */
fun HDRLoader.loadHdr(
    assets: AssetManager,
    hdrFileName: String,
    ibl: ((IndirectLight?) -> Unit)? = null,
    iblFilter: ((Texture) -> Texture)? = { cubemap ->
        cubemap.use {
            Filament.iblPrefilter.specularFilter(it)
        }
    },
    skybox: ((Skybox?) -> Unit)? = null
) {
    val cubemap = assets.open(hdrFileName).useBuffer { buffer ->
        createTexture(Filament.engine, buffer)
    }?.use { hdrTexture ->
        Filament.iblPrefilter.equirectangularToCubemap(hdrTexture)
    }

    ibl?.invoke(cubemap?.let {
        iblFilter?.invoke(it)
    }?.let { reflectionsTexture ->
        IndirectLight.Builder()
            .reflections(reflectionsTexture)
            .build(Filament.engine)
    })

    skybox?.invoke(cubemap?.let { environmentCubeMap ->
        Skybox.Builder()
            .environment(environmentCubeMap)
            .build(Filament.engine)
    })
}