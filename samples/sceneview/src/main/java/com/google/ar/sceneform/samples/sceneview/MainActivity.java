package com.google.ar.sceneform.samples.sceneview;

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

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
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
                        , Uri.parse("models/model.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(renderable -> {
                    Node node1 = new Node();
                    node1.setRenderable(renderable);
                    node1.setLocalScale(new Vector3(0.3f, 0.3f, 0.3f));
                    node1.setLocalRotation(Quaternion.multiply(
                            Quaternion.axisAngle(new Vector3(1f, 0f, 0f), 45),
                            Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 75)));
                    node1.setLocalPosition(new Vector3(0f, 0f, -1.0f));
                    backgroundSceneView.getScene().addChild(node1);

                    Node node2 = new Node();
                    node2.setRenderable(renderable);
                    node2.setLocalScale(new Vector3(0.3f, 0.3f, 0.3f));
                    node2.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 135));
                    node2.setLocalPosition(new Vector3(0f, 0f, -1.0f));
                    transparentSceneView.getScene().addChild(node2);
                })
                .exceptionally(
                        throwable -> {
                            Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG).show();
                            return null;
                        });
    }
}
