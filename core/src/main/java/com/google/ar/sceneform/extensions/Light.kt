package com.google.ar.sceneform.extensions

import com.google.android.filament.EntityInstance
import com.google.android.filament.LightManager

typealias Light = Int
typealias LightInstance = Int

val Light.instance @EntityInstance get() : LightInstance = Filament.lightManager.getInstance(this)

fun LightManager.Builder.build(): Light =
    Filament.entityManager.create().apply {
        build(Filament.engine, this)
    }

var Light.intensity: Float
    get() = Filament.lightManager.getIntensity(instance)
    set(value) = Filament.lightManager.setIntensity(instance, value)

var Light.color: Color
    get() = FloatArray(3).apply {
        Filament.lightManager.getColor(instance, this)
    }.toColor()
    set(value) = Filament.lightManager.setColor(instance, value.r, value.g, value.b)

var Light.direction: Direction
    get() = FloatArray(3).apply {
        Filament.lightManager.getDirection(instance, this)
    }.toDirection()
    set(value) = Filament.lightManager.setDirection(instance, value.x, value.y, value.z)

/**
 * Destroys a Light and frees all its associated resources.
 */
fun Light.destroy() {
    Filament.lightManager.destroy(this)
}