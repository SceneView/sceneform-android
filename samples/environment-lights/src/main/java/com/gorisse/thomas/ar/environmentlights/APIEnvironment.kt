package com.gorisse.thomas.ar.environmentlights

import android.content.Context
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.KTXLoader
import com.gorisse.thomas.sceneform.environment.Environment
import com.gorisse.thomas.sceneform.environment.loadEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class APIEnvironment(
    val name: String,
    val ktxIblUrl: String? = null,
    val ktxSkyboxUrl: String? = null,
    val hdrUrl: String? = null
) {
    private var environment: Environment? = null

    suspend fun loadEnvironment(context: Context) = environment
        ?: withContext(Dispatchers.IO) {
            when {
                ktxIblUrl != null -> KTXLoader.loadEnvironment(
                    context,
                    ktxIblUrl.absoluteUrl,
                    ktxSkyboxUrl?.absoluteUrl
                )
                hdrUrl != null -> HDRLoader.loadEnvironment(context, hdrUrl.absoluteUrl)
                else -> null
            }?.also { environment = it }
        }

    override fun toString(): String {
        return name
    }

    private val String.absoluteUrl
        get() = takeIf { it.isAbsolute } ?: "$serverUrl/$environmentsPath/$this"
}