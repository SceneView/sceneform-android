package com.google.ar.sceneform.ux;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.util.EnumSet;
import java.util.Set;

/** Implements ArFragment and configures the session for using the augmented faces feature. */
public class FaceArFragment extends ArFragment {


    @Override
    protected void onSessionInitialization(Session session) {
        super.onSessionInitialization(session);
        CameraConfigFilter filter = new CameraConfigFilter(session);
        filter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE));
        filter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        session.setCameraConfig(session.getSupportedCameraConfigs(filter).get(0));
    }

    @Override
    protected void onSessionConfiguration(Session session, Config config) {
        super.onSessionConfiguration(session, config);
        config.setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D);
    }

    @Override
    protected Set<Session.Feature> getSessionFeatures() {
        return EnumSet.of(Session.Feature.FRONT_CAMERA);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getPlaneDiscoveryController().hide();
        getPlaneDiscoveryController().setInstructionView(null);

        // Hide plane indicating dots
        getArSceneView().getPlaneRenderer().setVisible(false);
        // Disable the rendering of detected planes.
        getArSceneView().getPlaneRenderer().setEnabled(false);
    }
}
