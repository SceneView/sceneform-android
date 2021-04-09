package com.google.ar.sceneform.samples.sceneviewbackground;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;

public class MainActivity extends AppCompatActivity {

    private SceneView backgroundSceneView;
    private SceneView transparentSceneView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        backgroundSceneView = findViewById(R.id.backgroundSceneView);

        transparentSceneView = findViewById(R.id.transparentSceneView);
        transparentSceneView.setTransparent(true);

        loadModels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            backgroundSceneView.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        try {
            transparentSceneView.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        backgroundSceneView.pause();
        transparentSceneView.pause();
    }

    public void loadModels() {
        ModelRenderable.builder()
                .setSource(this
                        , Uri.parse("models/cube.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    Node modelNode1 = new Node();
                    modelNode1.setRenderable(model);
                    modelNode1.setLocalScale(new Vector3(0.3f, 0.3f, 0.3f));
                    modelNode1.setLocalRotation(Quaternion.multiply(
                            Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 45),
                            Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 75)));
                    modelNode1.setLocalPosition(new Vector3(0f, 0f, -1.0f));
                    backgroundSceneView.getScene().addChild(modelNode1);

                    Node modelNode2 = new Node();
                    modelNode2.setRenderable(model);
                    modelNode2.setLocalScale(new Vector3(0.3f, 0.3f, 0.3f));
                    modelNode2.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 135));
                    modelNode2.setLocalPosition(new Vector3(0f, 0f, -1.0f));
                    transparentSceneView.getScene().addChild(modelNode2);
                })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                            return null;
                        });
    }
}
