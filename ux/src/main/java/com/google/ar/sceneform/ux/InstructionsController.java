package com.google.ar.sceneform.ux;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * This view manages showing instructions view like the plane discovery.
 * You can assign into the instruction view to override the default visual,
 * or assign null to remove it.
 */
public class InstructionsController {

    /**
     * The plane discovery instruction view
     */
    public static final int TYPE_PLANE_DISCOVERY = 0;

    private final Map<Integer, View> views = new HashMap<>();
    private boolean isVisible = true;
    @NonNull
    private final Map<Integer, Boolean> visibilities = new HashMap<>();
    private boolean isEnabled = true;

    public InstructionsController() {
    }

    /**
     * Set the instructions view to present over the Sceneform view.
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @param view the associated type with the view
     */
    public void setView(int type, View view) {
        this.views.put(type, view);
        this.visibilities.put(type, view != null && view.getVisibility() == View.VISIBLE);
        updateVisibility();
    }

    /**
     * Check if the instruction view is enabled.
     *
     * @return false = never show the instructions view
     */
    public boolean isEnabled() {
        return this.isEnabled;
    }

    /**
     * Enable/disable the instruction view for all types.
     *
     * @param enabled false = never show the instructions view
     */
    public void setEnabled(boolean enabled) {
        if (this.isEnabled != enabled) {
            this.isEnabled = enabled;
            updateVisibility();
        }
    }

    /**
     * Set the instructions view visibility for all types.
     *
     * @param visible the visibility
     */
    public void setVisible(boolean visible) {
        if (this.isVisible!= visible) {
            this.isVisible = visible;
            updateVisibility();
        }
    }

    /**
     * Set the instructions visibility for the view type (finding a plane or other override
     * instructions)
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @param visible the visibility
     */
    public void setVisible(int type, boolean visible) {
        if (isVisible(type) != visible) {
            this.visibilities.put(type, visible);
            updateVisibility();
        }
    }

    /**
     * Retrieve the visibility for the view type (finding a plane or other overrides instructions)
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @return true is the view type is visible
     */
    public boolean isVisible(int type) {
        return visibilities.getOrDefault(type, false);
    }

    private void updateVisibility() {
        for (int type : this.views.keySet()) {
            View view = views.get(type);
            if (view != null) {
                view.setVisibility(isVisible && isEnabled() && visibilities.getOrDefault(type, true) ?
                        View.VISIBLE : View.GONE);
            }
        }
    }
}
