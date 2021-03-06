import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import GenericWebClient

/**
 * @author Senthil
 *
 * Fragment to show Augmented reality view
 */
class YouTubeARFragment : Fragment() {

    private lateinit var binding: FragmentYoutubeArBinding
    private lateinit var arFragment: ArFragment
    private val genericWebClient = GenericWebClient()

    companion object {
        private const val TAG = "YouTubeARFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentYoutubeArBinding.inflate(inflater)

        /*binding.closeIcon.setOnClickListener {
            this.findNavController().popBackStack()
        }*/

        // since arFragment is not "View", it cannot be accessed like traditional view using id.
        // so added the tag for the fragment in layout and accessing it using that tag
        arFragment = childFragmentManager.findFragmentByTag("fragmentArSceneForm") as ArFragment
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                return@setOnTapArPlaneListener
            }
            val anchor = hitResult.createAnchor()
            placeObject(anchor)
        }

        return binding.root
    }

    /***
     * Handle the renderable object and place object in scene
     */
    private fun placeObject(anchor: Anchor) {
        val viewRenderable = ViewRenderable.builder()
            .setView(context, R.layout.ar_webview)
            .build()
        //when the model render is build add node to scene
        viewRenderable.thenAccept { renderableObject ->
            addNodeToScene(arFragment, anchor, renderableObject)
        }
        //handle error
        viewRenderable.exceptionally {
            val toast = Toast.makeText(context, "Error", Toast.LENGTH_SHORT)
            toast.show()
            null
        }
    }

    /***
     * Add a child anchor to a new scene
     */
    private fun addNodeToScene(
        fragment: ArFragment, anchor: Anchor, renderableObject: ViewRenderable
    ) {
        addContentToRenderable(renderableObject)
        val anchorNode = AnchorNode(anchor)
        val transformableNode = TransformableNode(fragment.transformationSystem)
        transformableNode.renderable = renderableObject
        transformableNode.setParent(anchorNode)
        fragment.arSceneView.scene.addChild(anchorNode)
        transformableNode.select()
    }

    /**
     * Add anything specific on the renderable view
     */
    private fun addContentToRenderable(viewRenderable: ViewRenderable) {
        viewRenderable.isShadowCaster = false
        viewRenderable.isShadowReceiver = false
        val webView = viewRenderable.view.findViewById<WebView>(R.id.webView)
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
            }
            webViewClient = genericWebClient
            loadUrl("https://www.youtube.com")
        }
    }

}