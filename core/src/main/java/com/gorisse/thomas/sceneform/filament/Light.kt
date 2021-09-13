package com.gorisse.thomas.sceneform.filament

import com.google.android.filament.EntityInstance
import com.google.android.filament.LightManager
import com.google.android.filament.utils.Float3


/**
 * Default sun directional light intensity.
 */
const val defaultDirectionalLightIntensity = 100_000.0f

typealias Light = Int
typealias LightInstance = Int

data class LightInfluence(
    var direction: Direction? = null,
    var intensityFactor: Float = 1.0f,
    var colorIntensityFactors: Float3 = Float3(1.0f)
)

/**
 * @see LightManager.getInstance
 */
val Light.instance @EntityInstance get() : LightInstance = Filament.lightManager.getInstance(this)

/**
 * @see LightManager.Builder.build
 */
fun LightManager.Builder.build(): Light =
    Filament.entityManager.create().apply {
        build(Filament.engine, this)
    }

/**
 * @see LightManager.getType
 */
val Light.type: LightManager.Type
    get() = Filament.lightManager.getType(instance)

/**
 * @see LightManager.getPosition
 * @see LightManager.setPosition
 */
var Light.position: Direction
    get() = FloatArray(3).apply {
        Filament.lightManager.getPosition(instance, this)
    }.toPosition()
    set(value) = Filament.lightManager.setPosition(instance, value.x, value.y, value.z)

/**
 * @see LightManager.getDirection
 * @see LightManager.setDirection
 */
var Light.direction: Direction
    get() = FloatArray(3).apply {
        Filament.lightManager.getDirection(instance, this)
    }.toDirection()
    set(value) = Filament.lightManager.setDirection(instance, value.x, value.y, value.z)

/**
 * @see LightManager.getIntensity
 * @see LightManager.setIntensity
 */
var Light.intensity: Float
    get() = Filament.lightManager.getIntensity(instance)
    set(value) = Filament.lightManager.setIntensity(instance, value)

/**
 * @see LightManager.getColor
 * @see LightManager.setColor
 */
var Light.color: Color
    get() = FloatArray(3).apply {
        Filament.lightManager.getColor(instance, this)
    }.toColor()
    set(value) = Filament.lightManager.setColor(instance, value.r, value.g, value.b)

/**
 * @see LightManager.isShadowCaster
 * @see LightManager.setShadowCaster
 */
var Light.isShadowCaster: Boolean
    get() = Filament.lightManager.isShadowCaster(instance)
    set(value) = Filament.lightManager.setShadowCaster(instance, value)

// TODO: We need a clone on the Filament side in order to copy all values
fun Light.clone() = LightManager.Builder(type)
    .castShadows(isShadowCaster)
    .position(position.x, position.y, position.z)
    .direction(direction.x, direction.y, direction.z)
    .intensity(intensity)
    .color(color.r, color.g, color.b)
    .build()

/**
 * Destroys a Light and frees all its associated resources.
 */
@JvmOverloads
fun Light.destroy() {
    Filament.lightManager.destroy(this)
}