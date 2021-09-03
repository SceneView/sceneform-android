package com.google.ar.sceneform.extensions

import android.content.res.AssetManager
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderLoader
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.ar.sceneform.rendering.EngineInstance
import com.google.ar.sceneform.utilities.useBuffer

typealias Color = Float3
typealias Direction = Float3

fun colorOf(r: Float = 0.0f, g: Float = 0.0f, b: Float = 0.0f) = Color(r, g, b)

object Filament {

    @JvmStatic
    val engine = EngineInstance.getEngine().filamentEngine

    @JvmStatic
    val entityManager
        get() = EntityManager.get()

    val uberShaderLoader by lazy { UbershaderLoader(engine) }

    @JvmStatic
    val assetLoader by lazy {
        AssetLoader(engine, uberShaderLoader, entityManager)
    }

    val transformManager get() = engine.transformManager

    val resourceLoader by lazy { ResourceLoader(engine, true, false) }

    val lightManager get() = engine.lightManager

    fun loadHdr(
        assets: AssetManager,
        hdrFileName: String,
        reflections: ((Texture) -> Unit)? = null,
        ibl: ((IndirectLight) -> Unit)? = null,
        skybox: ((Skybox) -> Unit)? = null,
        error: (Exception) -> Unit = {}
    ) {
        val engine = EngineInstance.getEngine().filamentEngine
        assets.open(hdrFileName).useBuffer { buffer ->
            HDRLoader.createTexture(engine, buffer)
        }?.let { hdrTexture ->
            val context = IBLPrefilterContext(engine)
            IBLPrefilterContext.EquirectangularToCubemap(context)
                .run(hdrTexture)?.let { skyboxTexture ->
                    if (reflections != null || ibl != null) {
                        IBLPrefilterContext.SpecularFilter(context)
                            .run(skyboxTexture)?.let { reflectionsTexture ->
                                reflections?.invoke(reflectionsTexture)
                                ibl?.invoke(
                                    IndirectLight.Builder()
                                        .reflections(reflectionsTexture)
                                        .intensity(30000.0f)
                                        .build(engine)
                                )
                            } ?: error(Exception("Could not decode reflections texture"))
                    }
                    skybox?.invoke(Skybox.Builder().environment(skyboxTexture).build(engine))
                } ?: error(java.lang.Exception("Could not load hdr texture"))
        }
    }
}

fun FloatArray.toFloat3() = this.let { (x, y, z) -> Float3(x, y, z) }
fun FloatArray.toColor() = this.let { (r, g, b) -> Color(r, g, b) }
fun FloatArray.toDirection() = this.let { (x, y, z) -> Direction(x, y, z) }

