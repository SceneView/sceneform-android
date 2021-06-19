package com.google.ar.sceneform.ux;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.ar.core.Config;
import com.google.ar.core.Session;

import java.util.EnumSet;
import java.util.Set;

/** Implements ArFragment and configures the session for using the augmented faces feature. */
public class FaceArFragment extends ArFragment {

    @Override
    protected Config getSessionConfiguration(Session session) {
        Config config = new Config(session);
        config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D);
        return config;
    }

    @Override
    protected Set<Session.Feature> getSessionFeatures() {
        return EnumSet.of(Session.Feature.FRONT_CAMERA);
    }

    /**
     * Override to turn off planeDiscoveryController. Plane trackables are not supported with the
     * front camera.
     */
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout =
                (FrameLayout) super.onCreateView(inflater, container, savedInstanceState);

        getPlaneDiscoveryController().hide();
        getPlaneDiscoveryController().setInstructionView(null);

        // Hide plane indicating dots
        getArSceneView().getPlaneRenderer().setVisible(false);
        // Disable the rendering of detected planes.
        getArSceneView().getPlaneRenderer().setEnabled(false);

        return frameLayout;
    }
}
