package com.google.ar.sceneform.samples.defaultmaterialtest;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity implements
        BaseArFragment.OnTapArPlaneListener,
        ArFragment.OnViewCreatedListener,
        FragmentOnAttachListener {

    private ArFragment arFragment;
    private Renderable model;
    private CompletableFuture<Material> material;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().addFragmentOnAttachListener(this);

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }
    }

    private void prepareMaterial() {
        material = MaterialFactory
                .makeOpaqueWithColor(this, new Color(getColor(R.color.red_500)))
                .thenApply(material -> {
                    material.setFloat(MaterialFactory.MATERIAL_METALLIC, 0.0f);
                    material.setFloat(MaterialFactory.MATERIAL_REFLECTANCE, 0.4f);
                    material.setFloat(MaterialFactory.MATERIAL_ROUGHNESS, 0.5f);

                    return material;
                });
    }

    private void prepareCube() {
        material.thenAccept(material1 -> {
            model = ShapeFactory.makeCube(
                    new Vector3(0.1f, 0.1f, 0.1f).scaled(1f),
                    new Vector3(0.0f, 0.0f, 0.0f).scaled(1f),
                    material1);
        });
    }

    @Override
    public void onTapPlane(
            HitResult hitResult,
            Plane plane,
            MotionEvent motionEvent
    ) {
        // Create the Anchor.
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
        modelNode.setParent(anchorNode);
        modelNode.setRenderable(this.model);
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnViewCreatedListener(this);

            getSupportFragmentManager().removeFragmentOnAttachListener(this);
        }
    }

    @Override
    public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
        prepareMaterial();
        prepareCube();
        arFragment.setOnTapArPlaneListener(MainActivity.this);
    }
}