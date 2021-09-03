package com.gorisse.thomas.ar.environmentlights

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.util.TypedValue
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.*
import com.google.ar.sceneform.lights.defaultDirectionalIntensity
import com.google.ar.sceneform.lights.defaultIndirectIntensity
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.gorisse.thomas.ar.environmentlights.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.util.*
import java.util.concurrent.CompletableFuture


class MainActivity : AppCompatActivity() {

    data class Model(val name: String, val url: String, val scale: Float = 1.0f) {
        var renderable: Renderable? = null

        override fun toString(): String {
            return name
        }
    }

    fun Node.setModel(model: Model) {
        this.parent?.localScale = Vector3(model.scale, model.scale, model.scale)
        this.setRenderable(model.renderable)
            .animate(true).start()
    }

    val models = listOf(
        Model("Spoons", "models/Spoons.glb", 4.0f),
        Model("MetalRoughSpheres", "models/MetalRoughSpheres.glb", 0.1f),
        Model("Hair", "models/Hair.glb"),
        Model(
            "Tiger",
            "https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb",
            0.75f
        ),
        Model("Gumball", "models/Gumball.glb", 0.5f)
    )

    private lateinit var binding: ActivityMainBinding

    private lateinit var arFragment: ArFragment
    private val sceneView: ArSceneView get() = arFragment.arSceneView
    private val scene: Scene get() = sceneView.scene

    var model: Model? = null
        set(value) {
            field = value
            if (value != null) {
                if (value.renderable != null) {
                    modelNode?.setModel(value)
                } else {
                    tasks += ModelRenderable.builder()
                        .setSource(this, Uri.parse(value.url))
                        .setIsFilamentGltf(true)
                        .build()
                        .thenAccept {
                            value.renderable = it
                            modelNode?.setModel(value)
                        }
                }
            }
        }
    private var modelNode: Node? = null

    private var tasks = mutableListOf<CompletableFuture<*>>()

    val indirectIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultIndirectIntensity).apply {
            lifecycleScope.launchWhenResumed {
                collect {
                    scene.environmentLights?.indirectIntensity = it
                }
            }
        }

    val directionalIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultDirectionalIntensity).apply {
            lifecycleScope.launchWhenResumed {
                collect {
                    scene.environmentLights?.directionalIntensity = it
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
            .apply {
                lifecycleOwner = this@MainActivity
                activity = this@MainActivity
            }
        setSupportActionBar(binding.toolbar)

        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            (fragment as? ArFragment)?.let { fragment ->
                arFragment = fragment
                arFragment.setOnViewCreatedListener { _, arSceneView ->
                    arSceneView.scene.environmentLights.indirectIntensity.let {
                        binding.indirectIntensity.max = it.toInt() * 2
                        indirectIntensity.value = it
                    }
                    arSceneView.scene.environmentLights.directionalIntensity.let {
                        binding.directionalIntensity.max = it.toInt() * 2
                        directionalIntensity.value = it
                    }
                }
                arFragment.setOnSessionConfigurationListener { _, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }
                arFragment.setOnTapArPlaneListener(::onTapPlane)
            }
        }
        supportFragmentManager.commit {
            add(R.id.arFragment, ArFragment::class.java, Bundle().apply {
                putBoolean(ArFragment.ARGUMENT_FULLSCREEN, false)
            })
        }

        binding.models.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models.toTypedArray()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tasks.filter { !it.isDone }.forEach { it.cancel(true) }
    }

    fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (model?.renderable == null) {
            toast("Loading...")
            return
        }

        val anchorNode = AnchorNode(hitResult.createAnchor())
        modelNode = TransformableNode(arFragment.transformationSystem).apply {
            setParent(anchorNode)
            setModel(model!!)
        }
        sceneView.scene.addChild(anchorNode)
    }

    fun onModelSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        model = models[pos]
    }

    fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            when (seekBar?.id) {
                R.id.indirectIntensity -> indirectIntensity.value = progress.toFloat()
                R.id.directionalIntensity -> directionalIntensity.value = progress.toFloat()
            }
        }
    }

    fun onEstimationModeChanged(button: CompoundButton?, checked: Boolean) {
        if (checked) {
            sceneView.setSessionConfig(sceneView.sessionConfig?.apply {
                lightEstimationMode = when (button?.id) {
                    R.id.environmentalHdrMode, R.id.environmentalHdrNoCubemapMode -> Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    R.id.ambientIntensityMode -> Config.LightEstimationMode.AMBIENT_INTENSITY
                    else -> Config.LightEstimationMode.DISABLED
                }
            }, true)
            scene.environmentLights.useEnvironmentalHdrCubemap =
                button?.id != R.id.environmentalHdrNoCubemapMode
        }
    }

    @SuppressLint("SetTextI18n")
    fun takeScreenshot(view: View) {
        toast("Saving screenshot...")
        // Only Sceneview
        val bitmap = Bitmap.createBitmap(sceneView.width, sceneView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            sceneView, bitmap, { result ->
                when (result) {
                    PixelCopy.SUCCESS -> {
                        createExternalFile(
                            environment = Environment.DIRECTORY_PICTURES, extension = ".png"
                        ).let { file ->
                            TextView(this).apply {
                                layout(0, 0, 1000, 500)
                                setTextSize(TypedValue.COMPLEX_UNIT_PX, 30.0f)
                                setTextColor(Color.WHITE)
                                setShadowLayer(5.0f, 2.0f, 2.0f, Color.CYAN)
                                text = "Sceneform Maintained v${Sceneform.versionName()}\n" +
                                        "${binding.indirectIntensityText.text}\n" +
                                        "${binding.directionalIntensityText.text}\n" +
                                        "${binding.estimationModeText.text}: ${
                                            listOf(
                                                binding.environmentalHdrMode,
                                                binding.environmentalHdrNoCubemapMode,
                                                binding.ambientIntensityMode,
                                                binding.disabledMode
                                            ).firstOrNull { it.isChecked }?.text
                                        }"
                                val canvas = Canvas(bitmap)
                                isDrawingCacheEnabled = true
                                canvas.drawBitmap(getDrawingCache(), 100.0f, 100.0f, null);
                            }
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
                            bitmap.recycle()
                            MediaScannerConnection.scanFile(
                                this,
                                arrayOf(file.path),
                                null,
                                null
                            )
                            toast("Screenshot saved")
                        }
                    }
                    else -> toast("Screenshot failure: $result")
                }
            }, Handler(HandlerThread("screenshot").apply { start() }.looper)
        )
    }
}