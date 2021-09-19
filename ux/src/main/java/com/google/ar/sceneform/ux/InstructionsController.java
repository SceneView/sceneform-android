package com.google.ar.sceneform.ux;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

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

    /**
     * The augmented image fit to scan detection instruction view
     */
    public static final int TYPE_AUGMENTED_IMAGE_SCAN = 1;

    protected LayoutInflater inflater;
    protected FrameLayout container;

    private final Map<Integer, View> views = new HashMap<>();
    private boolean isVisible = true;
    private final Map<Integer, Boolean> typesVisibilities = new HashMap<>();
    private boolean isEnabled = true;
    private final Map<Integer, Boolean> typesEnabled = new HashMap<>();

    public InstructionsController(LayoutInflater inflater, FrameLayout container) {
        this.inflater = inflater;
        this.container = container;
    }

    protected View onCreateView(int type) {
        View view = null;
        switch (type) {
            case TYPE_PLANE_DISCOVERY:
                view = inflater.inflate(R.layout.sceneform_instructions_plane_discovery, container, false);
                break;
            case TYPE_AUGMENTED_IMAGE_SCAN:
                view = inflater.inflate(R.layout.sceneform_instructions_augmented_image, container, false);
                break;
        }
        if (view != null) {
            container.addView(view);
        }
        return view;
    }

    /**
     * Set the instructions view to present over the Sceneform view.
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @param view the associated type with the view
     */
    public void setView(int type, View view) {
        this.views.put(type, view);
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
     * Retrieve if the view type (finding a plane or other overrides instructions) is enable.
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @return true is the view type is visible
     */
    public boolean isEnabled(int type) {
        return typesEnabled.getOrDefault(type, true);
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
     * Enable/disable the instruction view for for the view type (finding a plane or other override
     * instructions)
     *
     * @param type   the view type (ex: TYPE_PLANE_DISCOVERY)
     * @param enable true is the view type is visible
     */
    public void setEnabled(int type, boolean enable) {
        if (isEnabled(type) != enable) {
            typesEnabled.put(type, enable);
            updateVisibility();
        }
    }

    /**
     * Get the instructions view visibility for all types.
     * You should not use this function for global visibility purposes since it's called internally
     * but call {@link #isEnabled()} instead.
     *
     * @return the visibility
     */
    public boolean isVisible() {
        return this.isVisible;
    }

    /**
     * Retrieve the visibility for the view type (finding a plane or other overrides instructions)
     * You should not use this function for global visibility purposes since it's called internally
     * but call {@link #isEnabled(int)} instead.
     *
     * @param type the view type (ex: TYPE_PLANE_DISCOVERY)
     * @return true is the view type is visible
     */
    public boolean isVisible(int type) {
        return typesVisibilities.getOrDefault(type, false);
    }

    /**
     * Set the instructions view visibility for all types.
     * You should not use this function for global visibility purposes since it's called internally
     * but call {@link #setEnabled(boolean)} instead
     *
     * @param visible the visibility
     */
    public void setVisible(boolean visible) {
        if (this.isVisible != visible) {
            this.isVisible = visible;
            updateVisibility();
        }
    }

    /**
     * Set the instructions visibility for the view type (finding a plane or other override
     * instructions)
     * You should not use this function for global visibility purposes since it's called internally
     * but call {@link #setEnabled(int, boolean)} instead
     *
     * @param type    the view type (ex: TYPE_PLANE_DISCOVERY)
     * @param visible the visibility
     */
    public void setVisible(int type, boolean visible) {
        if (isVisible(type) != visible) {
            typesVisibilities.put(type, visible);
            updateVisibility();
        }
    }

    private void updateVisibility() {
        for (int type : this.typesVisibilities.keySet()) {
            boolean isVisible = isEnabled()
                    && isVisible()
                    && isEnabled(type)
                    && isVisible(type);
            View view = views.get(type);
            if (isVisible && view == null) {
                view = onCreateView(type);
                views.put(type, view);
            }
            if (view != null) {
                view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            }
        }
    }
}
