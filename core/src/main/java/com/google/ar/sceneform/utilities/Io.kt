package com.google.ar.sceneform.utilities

import java.io.InputStream
import java.nio.ByteBuffer

fun <R> InputStream.useBuffer(block: (ByteBuffer) -> R): R = use { inputStream ->
    val bytes = ByteArray(inputStream.available())
    inputStream.read(bytes)
    block(ByteBuffer.wrap(bytes))
}

fun <R> InputStream.useText(block: (String) -> R): R = use { inputStream ->
    inputStream.bufferedReader().use {
        block(it.readText())
    }
}

inline fun <T : AutoCloseable?, R> Array<T>.use(block: (Array<T>) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        forEach {
            try {
                it?.close()
            } catch (e: Exception) {
                throw exception ?: e
            }
        }
    }
}