package com.google.ar.sceneform.extensions

import com.google.android.filament.EntityManager
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderLoader
import com.google.android.filament.utils.Float3
import com.google.ar.sceneform.rendering.EngineInstance

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

    val iblPrefilter by lazy { IBLPrefilter(engine) }
}

fun FloatArray.toFloat3() = this.let { (x, y, z) -> Float3(x, y, z) }
fun FloatArray.toColor() = this.let { (r, g, b) -> Color(r, g, b) }
fun FloatArray.toDirection() = this.let { (x, y, z) -> Direction(x, y, z) }

