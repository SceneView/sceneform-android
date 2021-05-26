/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.ux;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.RenderableInstance;

import java.util.Collections;
import java.util.Set;

/**
 * Implements AR Required ArFragment. Does not require additional permissions and uses the default
 * configuration for ARCore.
 */
public class ArFragment extends BaseArFragment {
    private static final String TAG = "StandardArFragment";

    private OnViewCreatedListener onViewCreatedListener;
    private Renderable onTapRenderable;

    /**
     * Creates a new fragment instance with the supplied arguments
     *
     * @param fullscreen whether the fragment enables the fullscreen mode
     * @return the new fragment instance
     */
    public static ArFragment newInstance(boolean fullscreen) {
        ArFragment fragment = new ArFragment();

        Bundle bundle = new Bundle();
        bundle.putBoolean(ARGUMENT_FULLSCREEN, fullscreen);

        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (onViewCreatedListener != null) {
            onViewCreatedListener.onViewCreated(this, getArSceneView());
        }
    }

    @Override
    public boolean isArRequired() {
        return true;
    }

    @Override
    public String[] getAdditionalPermissions() {
        return new String[0];
    }

    @Override
    protected void handleSessionException(UnavailableException sessionException) {

        String message;
        if (sessionException instanceof UnavailableArcoreNotInstalledException) {
            message = "Please install ARCore";
        } else if (sessionException instanceof UnavailableApkTooOldException) {
            message = "Please update ARCore";
        } else if (sessionException instanceof UnavailableSdkTooOldException) {
            message = "Please update this app";
        } else if (sessionException instanceof UnavailableDeviceNotCompatibleException) {
            message = "This device does not support AR";
        } else {
            message = "Failed to create AR session";
        }
        Log.e(TAG, "Error: " + message, sessionException);
        Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected Config getSessionConfiguration(Session session) {
        return new Config(session);
    }

    @Override
    protected Set<Session.Feature> getSessionFeatures() {
        return Collections.emptySet();
    }

    /**
     * Invoked when the ARSceneView is created and added to the fragment.
     * You can use it to configure the ARSceneView and other ArFragment parameters.
     */
    public void setOnViewCreatedListener(OnViewCreatedListener onViewCreatedListener) {
        this.onViewCreatedListener = onViewCreatedListener;
    }

    /**
     * Loads a monolithic binary glTF and add it to the fragment when the user tap on a detected
     * plane surface.
     * <p>
     * Plays the animations automatically if the model has one.
     * </p>
     *
     * @param glbSource Glb file source location can be come from the asset folder ("model.glb")
     *                  or an http source ("http://domain.com/model.glb")
     */
    public void setOnTapPlaneGlbModel(String glbSource, OnTapModelListener listener) {
        ModelRenderable.builder()
                .setSource(
                        getContext(),
                        Uri.parse(glbSource))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(
                        modelRenderable -> {
                            onTapRenderable = modelRenderable;
                        })
                .exceptionally(
                        throwable -> {
                            if (listener != null) {
                                listener.onModelError(throwable);
                            }
                            return null;
                        });

        setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (onTapRenderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(getArSceneView().getScene());

                    // Create the transformable model and add it to the anchor.
                    TransformableNode model = new TransformableNode(getTransformationSystem());
                    model.setParent(anchorNode);
                    model.setRenderable(onTapRenderable);
                    model.select();

                    // Animate if has animation
                    RenderableInstance renderableInstance = model.getRenderableInstance();
                    if (renderableInstance != null && renderableInstance.hasAnimations()) {
                        renderableInstance.animate(true);
                    }

                    if (listener != null) {
                        listener.onModelAdded(renderableInstance);
                    }
                });
    }

    /**
     * Invoked when an ARCore plane is tapped and model is added or an error occurred during the
     * model loading.
     */
    public interface OnTapModelListener {
        /**
         * Called when an ARCore plane is tapped and the model is added to the scene.
         * The callback will only be invoked if no {@link com.google.ar.sceneform.Node} was tapped.
         *
         * @param renderableInstance the added instance of the model.
         * @see #setOnTapArPlaneListener(BaseArFragment.OnTapArPlaneListener)
         */
        void onModelAdded(RenderableInstance renderableInstance);

        /**
         * An error occurred while loading the ModelRenderable from the source.
         *
         * @param exception
         */
        void onModelError(Throwable exception);
    }

    /**
     * Invoked when the ARSceneView is created and added to the fragment.
     * You can use it to configure the ARSceneView.
     */
    public interface OnViewCreatedListener {
        /**
         * Called at the end of the fragment onCreateView() call.
         *
         * @param arFragment  the onViewCreated fragment.
         * @param arSceneView the created ARSceneView ready to be configured.
         */
        void onViewCreated(ArFragment arFragment, ArSceneView arSceneView);
    }
}
