package com.google.ar.sceneform.ux;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Session;

/**
 * Implements ArFragment and configures the session for using the augmented faces feature.
 */
public class FaceArFragment extends ArFragment {

    @Override
    protected Config onCreateSessionConfig(Session session) {
        CameraConfigFilter filter = new CameraConfigFilter(session);
        filter.setFacingDirection(CameraConfig.FacingDirection.FRONT);

        session.setCameraConfig(session.getSupportedCameraConfigs(filter).get(0));

        Config config = super.onCreateSessionConfig(session);
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
        config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D);

        return config;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getInstructionsController().setEnabled(false);

        // Hide plane indicating dots
        getArSceneView().getPlaneRenderer().setVisible(false);
        // Disable the rendering of detected planes.
        getArSceneView().getPlaneRenderer().setEnabled(false);
    }
}
