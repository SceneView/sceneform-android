package com.google.ar.sceneform;

import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.google.ar.sceneform.collision.Collider;
import com.google.ar.sceneform.collision.CollisionSystem;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.rendering.Renderer;
import com.google.ar.sceneform.utilities.Preconditions;

import java.util.ArrayList;

/**
 * The Sceneform Scene maintains the scene graph, a hierarchical organization of a scene's content.
 * A scene can have zero or more child nodes and each node can have zero or more child nodes.
 *
 * <p>The Scene also provides hit testing, a way to detect which node is touched by a MotionEvent or
 * Ray.
 */
public class Scene extends NodeParent {
    /**
     * Interface definition for a callback to be invoked when a touch event is dispatched to a scene.
     * The callback will be invoked after the touch event is dispatched to the nodes in the scene if
     * no node consumed the event.
     */
    public interface OnTouchListener {
        /**
         * Called when a touch event is dispatched to a scene. The callback will be invoked after the
         * touch event is dispatched to the nodes in the scene if no node consumed the event. This is
         * called even if the touch is not over a node, in which case {@link HitTestResult#getNode()}
         * will be null.
         *
         * @param hitTestResult represents the node that was touched
         * @param motionEvent   the motion event
         * @return true if the listener has consumed the event
         * @see Scene#setOnTouchListener(OnTouchListener)
         */
        boolean onSceneTouch(HitTestResult hitTestResult, MotionEvent motionEvent);
    }

    /**
     * Interface definition for a callback to be invoked when a touch event is dispatched to a scene.
     * The callback will be invoked before the {@link OnTouchListener} is invoked. This is invoked
     * even if the gesture was consumed, making it possible to observe all motion events dispatched to
     * the scene.
     */
    public interface OnPeekTouchListener {
        /**
         * Called when a touch event is dispatched to a scene. The callback will be invoked before the
         * {@link OnTouchListener} is invoked. This is invoked even if the gesture was consumed, making
         * it possible to observe all motion events dispatched to the scene. This is called even if the
         * touch is not over a node, in which case {@link HitTestResult#getNode()} will be null.
         *
         * @param hitTestResult represents the node that was touched
         * @param motionEvent   the motion event
         * @see Scene#setOnTouchListener(OnTouchListener)
         */
        void onPeekTouch(HitTestResult hitTestResult, MotionEvent motionEvent);
    }

    /**
     * Interface definition for a callback to be invoked once per frame immediately before the scene
     * is updated.
     */
    public interface OnUpdateListener {
        /**
         * Called once per frame right before the Scene is updated.
         *
         * @param frameTime provides time information for the current frame
         */
        void onUpdate(FrameTime frameTime);
    }

    private static final String TAG = Scene.class.getSimpleName();

    @Nullable
    private final SceneView view;
    private Camera camera;

    // Systems.
    final CollisionSystem collisionSystem = new CollisionSystem();
    private final TouchEventSystem touchEventSystem = new TouchEventSystem();

    private final ArrayList<OnUpdateListener> onUpdateListeners = new ArrayList<>();

    /**
     * Create a scene with the given context.
     */
    @SuppressWarnings("initialization") // Suppress @UnderInitialization warning.
    public Scene(SceneView view) {
        Preconditions.checkNotNull(view, "Parameter \"view\" was null.");
        this.view = view;
        camera = new Camera(this);
    }

    /**
     * Returns the SceneView used to create the scene.
     */
    public SceneView getView() {
        // the view field cannot be marked for the purposes of unit testing.
        // Add this check for static analysis go/nullness.
        if (view == null) {
            throw new IllegalStateException("Scene's view must not be null.");
        }

        return view;
    }

    /**
     * Get the camera that is used to render the scene. The camera is a type of node.
     *
     * @return the camera used to render the scene
     */
    public Camera getCamera() {
        return camera;
    }

    public void destroy() {
        //TODO : Destroy the camera
//        camera.destroy();
        camera = null;
    }

    /**
     * Register a callback to be invoked when the scene is touched. The callback will be invoked after
     * the touch event is dispatched to the nodes in the scene if no node consumed the event. This is
     * called even if the touch is not over a node, in which case {@link HitTestResult#getNode()} will
     * be null.
     *
     * @param onTouchListener the touch listener to attach
     */
    public void setOnTouchListener(@Nullable OnTouchListener onTouchListener) {
        touchEventSystem.setOnTouchListener(onTouchListener);
    }

    /**
     * Adds a listener that will be called before the {@link Scene.OnTouchListener} is invoked. This
     * is invoked even if the gesture was consumed, making it possible to observe all motion events
     * dispatched to the scene. This is called even if the touch is not over a node, in which case
     * {@link HitTestResult#getNode()} will be null. The listeners will be called in the order in
     * which they were added.
     *
     * @param onPeekTouchListener the peek touch listener to add
     */
    public void addOnPeekTouchListener(OnPeekTouchListener onPeekTouchListener) {
        touchEventSystem.addOnPeekTouchListener(onPeekTouchListener);
    }

    /**
     * Removes a listener that will be called before the {@link Scene.OnTouchListener} is invoked.
     * This is invoked even if the gesture was consumed, making it possible to observe all motion
     * events dispatched to the scene. This is called even if the touch is not over a node, in which
     * case {@link HitTestResult#getNode()} will be null.
     *
     * @param onPeekTouchListener the peek touch listener to remove
     */
    public void removeOnPeekTouchListener(OnPeekTouchListener onPeekTouchListener) {
        touchEventSystem.removeOnPeekTouchListener(onPeekTouchListener);
    }

    /**
     * Adds a listener that will be called once per frame immediately before the Scene is updated. The
     * listeners will be called in the order in which they were added.
     *
     * @param onUpdateListener the update listener to add
     */
    public void addOnUpdateListener(OnUpdateListener onUpdateListener) {
        Preconditions.checkNotNull(onUpdateListener, "Parameter 'onUpdateListener' was null.");
        if (!onUpdateListeners.contains(onUpdateListener)) {
            onUpdateListeners.add(onUpdateListener);
        }
    }

    /**
     * Removes a listener that will be called once per frame immediately before the Scene is updated.
     *
     * @param onUpdateListener the update listener to remove
     */
    public void removeOnUpdateListener(OnUpdateListener onUpdateListener) {
        Preconditions.checkNotNull(onUpdateListener, "Parameter 'onUpdateListener' was null.");
        onUpdateListeners.remove(onUpdateListener);
    }

    @Override
    public void onAddChild(Node child) {
        super.onAddChild(child);
        child.setSceneRecursively(this);
    }

    @Override
    public void onRemoveChild(Node child) {
        super.onRemoveChild(child);
        child.setSceneRecursively(null);
    }

    /**
     * Tests to see if a motion event is touching any nodes within the scene, based on a ray hit test
     * whose origin is the screen position of the motion event, and outputs a HitTestResult containing
     * the node closest to the screen.
     *
     * @param motionEvent         the motion event to use for the test
     * @param onlySelectableNodes Filter the HitTestResult on only selectable nodes
     * @return the result includes the first node that was hit by the motion event (may be null), and
     * information about where the motion event hit the node in world-space
     */
    public HitTestResult hitTest(MotionEvent motionEvent, boolean onlySelectableNodes) {
        Preconditions.checkNotNull(motionEvent, "Parameter \"motionEvent\" was null.");

        if (camera == null) {
            return new HitTestResult();
        }

        Ray ray = camera.motionEventToRay(motionEvent);
        return hitTest(ray, onlySelectableNodes);
    }

    /**
     * Tests to see if a ray is hitting any nodes within the scene and outputs a HitTestResult
     * containing the node closest to the ray origin that intersects with the ray.
     *
     * @param ray                 the ray to use for the test
     * @param onlySelectableNodes Filter the HitTestResult on only selectable nodes
     * @return the result includes the first node that was hit by the ray (may be null), and
     * information about where the ray hit the node in world-space
     * @see Camera#screenPointToRay(float, float)
     */
    public HitTestResult hitTest(Ray ray, boolean onlySelectableNodes) {
        Preconditions.checkNotNull(ray, "Parameter \"ray\" was null.");

        HitTestResult result = new HitTestResult();
        Collider collider = collisionSystem.raycast(ray, result, onlySelectableNodes);
        if (collider != null) {
            result.setNode((Node) collider.getTransformProvider());
        }

        return result;
    }

    /**
     * Tests to see if a motion event is touching any nodes within the scene and returns a list of
     * HitTestResults containing all of the nodes that were hit, sorted by distance.
     *
     * @param motionEvent The motion event to use for the test.
     * @return Populated with a HitTestResult for each node that was hit sorted by distance. Empty if
     * no nodes were hit.
     */
    public ArrayList<HitTestResult> hitTestAll(MotionEvent motionEvent) {
        Preconditions.checkNotNull(motionEvent, "Parameter \"motionEvent\" was null.");

        if (camera == null) {
            return new ArrayList<>();
        }
        Ray ray = camera.motionEventToRay(motionEvent);
        return hitTestAll(ray);
    }

    /**
     * Tests to see if a ray is hitting any nodes within the scene and returns a list of
     * HitTestResults containing all of the nodes that were hit, sorted by distance.
     *
     * @param ray The ray to use for the test.
     * @return Populated with a HitTestResult for each node that was hit sorted by distance. Empty if
     * no nodes were hit.
     * @see Camera#screenPointToRay(float, float)
     */
    public ArrayList<HitTestResult> hitTestAll(Ray ray) {
        Preconditions.checkNotNull(ray, "Parameter \"ray\" was null.");

        ArrayList<HitTestResult> results = new ArrayList<>();

        collisionSystem.raycastAll(
                ray,
                results,
                (result, collider) -> result.setNode((Node) collider.getTransformProvider()),
                () -> new HitTestResult());

        return results;
    }

    /**
     * Tests to see if the given node's collision shape overlaps the collision shape of any other
     * nodes in the scene using {@link Node#getCollisionShape()}. The node used for testing does not
     * need to be active.
     *
     * @param node The node to use for the test.
     * @return A node that is overlapping the test node. If no node is overlapping the test node, then
     * this is null. If multiple nodes are overlapping the test node, then this could be any of
     * them.
     * @see #overlapTestAll(Node)
     */
    @Nullable
    public Node overlapTest(Node node) {
        Preconditions.checkNotNull(node, "Parameter \"node\" was null.");

        Collider collider = node.getCollider();
        if (collider == null) {
            return null;
        }

        Collider intersectedCollider = collisionSystem.intersects(collider);
        if (intersectedCollider == null) {
            return null;
        }

        return (Node) intersectedCollider.getTransformProvider();
    }

    /**
     * Tests to see if a node is overlapping any other nodes within the scene using {@link
     * Node#getCollisionShape()}. The node used for testing does not need to be active.
     *
     * @param node The node to use for the test.
     * @return A list of all nodes that are overlapping the test node. If no node is overlapping the
     * test node, then the list is empty.
     * @see #overlapTest(Node)
     */
    public ArrayList<Node> overlapTestAll(Node node) {
        Preconditions.checkNotNull(node, "Parameter \"node\" was null.");

        ArrayList<Node> results = new ArrayList<>();

        Collider collider = node.getCollider();
        if (collider == null) {
            return results;
        }

        collisionSystem.intersectsAll(
                collider,
                (Collider intersectedCollider) ->
                        results.add((Node) intersectedCollider.getTransformProvider()));

        return results;
    }

    /**
     * Returns the renderer used for this scene, or null if the renderer is not setup.
     */
    @Nullable
    public Renderer getRenderer() {
        return view != null ? view.getRenderer() : null;
    }

    void onTouchEvent(MotionEvent motionEvent) {
        Preconditions.checkNotNull(motionEvent, "Parameter \"motionEvent\" was null.");

        HitTestResult hitTestResult = hitTest(motionEvent, true);
        touchEventSystem.onTouchEvent(hitTestResult, motionEvent);
    }

    void dispatchUpdate(FrameTime frameTime) {
        for (OnUpdateListener onUpdateListener : onUpdateListeners) {
            onUpdateListener.onUpdate(frameTime);
        }

        callOnHierarchy(node -> node.dispatchUpdate(frameTime));
    }
}
