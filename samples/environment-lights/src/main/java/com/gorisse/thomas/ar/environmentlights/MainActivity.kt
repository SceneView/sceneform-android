package com.gorisse.thomas.ar.environmentlights

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.*
import android.os.Environment.DIRECTORY_PICTURES
import android.util.TypedValue
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.gson.gsonDeserializer
import com.google.android.filament.Skybox
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.gson.GsonBuilder
import com.gorisse.thomas.ar.environmentlights.databinding.ActivityMainBinding
import com.gorisse.thomas.sceneform.environment
import com.gorisse.thomas.sceneform.environment.defaultIndirectLightIntensity
import com.gorisse.thomas.sceneform.estimatedEnvironment
import com.gorisse.thomas.sceneform.estimatedMainLight
import com.gorisse.thomas.sceneform.filament.*
import com.gorisse.thomas.sceneform.mainDirectionalLight
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

// TODO: Move to github.io
const val serverUrl = "https://thomas.gorisse.com/sceneform/assets"
const val modelsPath = "models"
const val environmentsPath = "environments"

class MainActivity : AppCompatActivity() {

    private var environments = arrayOf<APIEnvironment>()
        set(value) {
            field = value
            binding.environments.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                value
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            }
        }

    private var models = arrayOf<APIModel>()
        set(value) {
            field = value
            binding.models.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                value
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            }
        }

    private lateinit var binding: ActivityMainBinding

    private lateinit var arFragment: ArFragment
    private val sceneView: ArSceneView get() = arFragment.arSceneView
    private val scene: Scene get() = sceneView.scene

    var environment: APIEnvironment? = null
        set(value) {
            field = value
            if (value != null) {
                isLoadingEnvironment = true
                lifecycleScope.launchWhenResumed {
                    sceneView.environment = value.loadEnvironment(this@MainActivity)
                    isLoadingEnvironment = false
                }
            }
        }
    var isLoadingEnvironment
        get() = binding.environmentLoading.isVisible
        set(value) {
            binding.environmentLoading.isVisible = value
        }

    var model: APIModel? = null
        set(value) {
            field = value
            if (value != null) {
                modelNode?.setModel(value)
            }
        }

    private var modelNode: Node? = null

    var isLoadingModel
        get() = binding.modelLoading.isVisible
        set(value) {
            binding.modelLoading.isVisible = value
        }

    val indirectLightIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultIndirectLightIntensity)
    val estimatedIndirectLightIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultIndirectLightIntensity)
    val mainLightIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultDirectionalLightIntensity)
    val estimatedMainLightIntensity: MutableStateFlow<Float> =
        MutableStateFlow(defaultDirectionalLightIntensity)

    var reflectionSkybox = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    sceneView.renderer?.removeEntity(sceneView.cameraStream.cameraStreamRenderable)
                } else {
                    sceneView.renderer?.setSkybox(null)
                    sceneView.renderer?.addEntity(sceneView.cameraStream.cameraStreamRenderable)
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
                    arSceneView.environment?.indirectLight?.intensity?.let {
                        binding.indirectLightIntensity.max = it.toInt() * 2
                        indirectLightIntensity.value = it
                    }
                    arSceneView.mainDirectionalLight?.intensity?.let {
                        binding.mainLightIntensity.max = it.toInt() * 2
                        mainLightIntensity.value = it
                    }
                    arSceneView.scene.addOnUpdateListener {
                        if (reflectionSkybox) {
                            val environment = arSceneView?.estimatedEnvironment
                                ?: arSceneView?.environment
                            arSceneView.renderer?.setSkybox(
                                environment?.indirectLight?.reflectionsTexture?.let { reflections ->
                                    Skybox.Builder()
                                        .environment(reflections)
                                        .build()
                                })
                        }
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

        lifecycleScope.launchWhenResumed {
            isLoadingEnvironment = true
            isLoadingModel = true
            withContext(Dispatchers.IO) {
                val serverEnvironments: Array<APIEnvironment> =
                    Fuel.get("$serverUrl/$environmentsPath/index.json")
                        .awaitObject(gsonDeserializer(GsonBuilder().create()))
                withContext(Dispatchers.Main) {
                    isLoadingEnvironment = false
                    environments = serverEnvironments
                }
            }
            withContext(Dispatchers.IO) {
                Fuel.download("$serverUrl/$modelsPath/index.json").body.toStream()
                val serverModels: Array<APIModel> = Fuel.get("$serverUrl/$modelsPath/index.json")
                    .awaitObject(gsonDeserializer(GsonBuilder().create()))
                withContext(Dispatchers.Main) {
                    isLoadingModel = false
                    models = serverModels
                }
            }
            while (isActive) {
                estimatedIndirectLightIntensity.value =
                    sceneView.estimatedEnvironment?.indirectLight?.intensity ?: 0.0f
                estimatedMainLightIntensity.value =
                    when (sceneView.sessionConfig.lightEstimationMode) {
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR -> {
                            (sceneView.mainDirectionalLight?.intensity
                                ?: defaultDirectionalLightIntensity) *
                                    (sceneView.estimatedMainLight?.color?.toFloatArray()?.average()
                                        ?.toFloat() ?: 1.0f)
                        }
                        else -> sceneView.mainDirectionalLight?.intensity ?: 0.0f
                    }
                withContext(Dispatchers.IO) {
                    delay(500)
                }
            }
        }
    }

    fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (model?.renderable == null) {
            toast("Loading...")
        }

        val anchorNode = AnchorNode(hitResult.createAnchor())
        modelNode = TransformableNode(arFragment.transformationSystem).apply {
            setParent(anchorNode)
            setModel(model!!)
        }
        sceneView.scene.addChild(anchorNode)
    }

    fun Node.setModel(model: APIModel) {
        isLoadingModel = true
        model.load(this@MainActivity) {
            parent?.localScale = Vector3(model.scale, model.scale, model.scale)
            setRenderable(model.renderable)
                .animate(true).start()
            isLoadingModel = false
        }
    }

    fun toggleParametersVisibility(view: View) {
        binding.parametersLayout.isVisible = !binding.parametersLayout.isVisible
        binding.toolbar.isVisible = binding.parametersLayout.isVisible
        (view as ImageButton).setImageResource(
            if (binding.parametersLayout.isVisible) {
                R.drawable.arrow_down
            } else {
                R.drawable.arrow_up
            }
        )
    }

    fun onModelSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        model = models[pos]
    }

    fun onEnvironmentSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        environment = environments[pos]
    }

    fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            when (seekBar) {
                binding.indirectLightIntensity -> {
                    indirectLightIntensity.value = progress.toFloat()
                    sceneView.environment?.indirectLight?.intensity = progress.toFloat()
                }
                binding.mainLightIntensity -> {
                    mainLightIntensity.value = progress.toFloat()
                    sceneView.mainDirectionalLight?.intensity = progress.toFloat()
                }
            }
        }
    }

    fun onEstimationModeChanged(button: CompoundButton?, checked: Boolean) {
        if (checked) {
            sceneView.setSessionConfig(sceneView.sessionConfig?.apply {
                lightEstimationMode = when (button?.id) {
                    R.id.environmentalHdrMode,
                    R.id.environmentalHdrWithoutReflections,
                    R.id.environmentalHdrWithoutSpecular -> {
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }
                    R.id.ambientIntensityMode -> Config.LightEstimationMode.AMBIENT_INTENSITY
                    else -> Config.LightEstimationMode.DISABLED
                }
            }, true)
            sceneView.lightEstimationConfig.environmentalHdrReflections =
                (button?.id != R.id.environmentalHdrWithoutReflections)
            sceneView.lightEstimationConfig.environmentalHdrSpecularFilter =
                (button?.id != R.id.environmentalHdrWithoutSpecular)
        }
    }

    fun onReflectionSkyboxChanged(button: CompoundButton?, checked: Boolean) {
        reflectionSkybox = checked
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
                            environment = DIRECTORY_PICTURES, extension = ".png"
                        ).let { file ->
                            TextView(this).apply {
                                layout(0, 0, 1000, 500)
                                setTextSize(TypedValue.COMPLEX_UNIT_PX, 30.0f)
                                setTextColor(Color.WHITE)
                                setShadowLayer(5.0f, 2.0f, 2.0f, Color.CYAN)
                                text = "Sceneform Maintained v${Sceneform.versionName()}\n" +
                                        "Environment: ${environment?.name}\n" +
                                        "Model: ${model?.name}\n" +
                                        "${binding.indirectLightIntensityText.text}\n" +
                                        "${binding.mainLightIntensityText.text}\n" +
                                        "${binding.estimationModeText.text}: ${
                                            listOf(
                                                binding.environmentalHdrMode,
                                                binding.environmentalHdrWithoutReflections,
                                                binding.environmentalHdrWithoutSpecular,
                                                binding.ambientIntensityMode,
                                                binding.disabledMode
                                            ).firstOrNull { it.isChecked }?.text
                                        }"
                                val canvas = Canvas(bitmap)
                                canvas.drawBitmap(drawToBitmap(), 100.0f, 100.0f, null)
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