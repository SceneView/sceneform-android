package com.google.ar.sceneform.samples.purereflection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private val arFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
    }

    private val arScene by lazy {
        arFragment.arSceneView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setListeners()
    }

    private fun setListeners() {
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)

            MaterialFactory.makeTransparentWithColor(this, TRANSPARENT)
                .thenAccept { material ->
                    material.setFloat(
                        MaterialFactory.MATERIAL_ROUGHNESS,
                        DEFAULT_MATERIAL_ROUGHNESS
                    )
                    val model = createPlane(material = material)
                    placeParallelObject(model, anchorNode)
                    arScene.planeRenderer.disable()
                }

            anchorNode.setParent(arFragment.arSceneView.scene)
        }
    }

    private fun placeParallelObject(
        modelRenderable: ModelRenderable,
        parentNode: Node
    ) {
        val lookingUpNode = Node().apply {
            setParent(parentNode)
            setLookDirection(Vector3.up(), parentNode.up)
        }

        Node().apply {
            this.renderable = modelRenderable
            localRotation = Quaternion.axisAngle(Vector3(-1f, 0f, 0f), 90f)
            setParent(lookingUpNode)
        }
    }

    private fun createPlane(
        size: Vector3 = DEFAULT_MODEL_SIZE,
        material: Material,
    ): ModelRenderable {
        val extents = size.scaled(0.5f)

        val p0 = Vector3.add(Vector3.zero(), Vector3(-extents.x, extents.y, extents.z))
        val p1 = Vector3.add(Vector3.zero(), Vector3(extents.x, extents.y, extents.z))
        val p2 = Vector3.add(Vector3.zero(), Vector3(extents.x, -extents.y, extents.z))
        val p3 = Vector3.add(Vector3.zero(), Vector3(-extents.x, -extents.y, extents.z))

        val vertices = ArrayList(
            listOf(
                Vertex.builder().setPosition(p0).build(),
                Vertex.builder().setPosition(p1).build(),
                Vertex.builder().setPosition(p2).build(),
                Vertex.builder().setPosition(p3).build()
            )
        )

        val trianglesPerSide = 2
        val triangleIndices = ArrayList<Int>(trianglesPerSide * COORDS_PER_TRIANGLE)

        // First triangle for this side
        triangleIndices.add(3)
        triangleIndices.add(1)
        triangleIndices.add(0)

        // Second triangle for this side
        triangleIndices.add(3)
        triangleIndices.add(2)
        triangleIndices.add(1)

        val submesh = RenderableDefinition.Submesh.builder()
            .setMaterial(material)
            .setTriangleIndices(triangleIndices)
            .build()

        val renderableDefinition = RenderableDefinition.builder()
            .setVertices(vertices)
            .setSubmeshes(listOf(submesh))
            .build()

        return ModelRenderable.builder()
            .setSource(renderableDefinition)
            .build()
            .join()
            .apply {
                isShadowCaster = false
                isShadowReceiver = false
            }
    }

    private fun PlaneRenderer.disable() {
        isVisible = false
        isEnabled = false
    }


    companion object {

        private const val COORDS_PER_TRIANGLE = 3

        private const val DEFAULT_MATERIAL_ROUGHNESS = 0f

        private val DEFAULT_MODEL_SIZE = Vector3(1f, 1f, 0f)

        private val TRANSPARENT = Color(0f, 0f, 0f, 0f)
    }
}