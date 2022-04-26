package com.google.ar.sceneform.samples.environmentlights

import android.net.Uri
import android.os.*
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.utils.HDRLoader
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.samples.environmentlights.databinding.ActivityMainBinding
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.gorisse.thomas.sceneform.*
import com.gorisse.thomas.sceneform.environment.loadEnvironment
import com.gorisse.thomas.sceneform.light.LightEstimationConfig
import kotlinx.coroutines.*
import java.util.*

const val serverUrl = "https://sceneview.github.io/assets"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var arFragment: ArFragment
    private val sceneView: ArSceneView get() = arFragment.arSceneView
    private val scene: Scene get() = sceneView.scene

    var model: Renderable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
            .apply {
                lifecycleOwner = this@MainActivity
                activity = this@MainActivity
            }
        setSupportActionBar(binding.toolbar)

        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            (fragment as? ArFragment)?.let { onArFragmentAttached(it) }
        }
        supportFragmentManager.commit {
            add(R.id.arFragment, ArFragment::class.java, Bundle().apply {
                putBoolean(ArFragment.ARGUMENT_FULLSCREEN, false)
            })
        }

        lifecycleScope.launchWhenResumed {
            sceneView.environment = HDRLoader.loadEnvironment(
                this@MainActivity,
                "$serverUrl/environments/evening_meadow_2k.hdr"
            )
            ModelRenderable.builder()
                .setSource(
                    this@MainActivity,
                    Uri.parse("$serverUrl/models/ClearCoat.glb")
                )
                .setIsFilamentGltf(true)
                .build()
                .thenAccept {
                    model = it
                }
        }
    }

    fun onArFragmentAttached(fragment: ArFragment) {
        arFragment = fragment
        arFragment.setOnTapArPlaneListener(::onTapPlane)
    }

    fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (model == null) {
            toast("Loading...")
            return
        }

        val anchorNode = AnchorNode(hitResult.createAnchor()).apply {
            localScale = Vector3(0.05f, 0.05f, 0.05f)
        }
        TransformableNode(arFragment.transformationSystem).apply {
            setParent(anchorNode)
            setRenderable(model)
                .animate(true).start()
        }
        scene.addChild(anchorNode)
    }

    fun onEstimationModeChanged(button: CompoundButton?, checked: Boolean) {
        if (checked) {
            button?.id?.let { estimationMode ->
                sceneView.lightEstimationConfig = LightEstimationConfig(
                    mode = when (estimationMode) {
                        R.id.environmentalHdrMode,
                        R.id.environmentalHdrNoReflections,
                        R.id.environmentalHdrNoSpecularFilter -> {
                            Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        }
                        R.id.ambientIntensityMode -> Config.LightEstimationMode.AMBIENT_INTENSITY
                        else -> Config.LightEstimationMode.DISABLED
                    },
                    environmentalHdrReflections = estimationMode != R.id.environmentalHdrNoReflections,
                    environmentalHdrSpecularFilter = estimationMode != R.id.environmentalHdrNoSpecularFilter
                )
            }
        }
    }
}
