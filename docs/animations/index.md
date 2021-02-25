Animations
==========

Until now, only `RenderableInstance` are animtable.  
Below `model` corresponds to a `RenderablaInstance` returned from  
a `node.getRenderableInstance()`

### Simple usage

On a very basic 3D model like a single infinite rotating sphere, you should not have to
use ModelAnimator but probably instead just call:

```java
model.animate(repeat).start();
```


### Single Model with Single Animation

If you want to animate a single model to a specific timeline position, use:
```java
ModelAnimator.ofAnimationFrame(model, "action", 100).start();
```
```java
ModelAnimator.ofAnimationFraction(model, "action", 0.2f, 0.8f, 1f).start();
```
```java
ModelAnimator.ofAnimationTime(model, "action", 10.0f)}.start();
```

#### Values
* A single time, frame, fraction value will go from the actual position to the desired value
* Two values means form value1 to value2
* More than two values means form value1 to value2 then to value3


### Single Model with Multiple Animations

If the model is a character, for example, there may be one ModelAnimation for a walkcycle, a
second for a jump, a third for sidestepping and so on:

#### Play Sequentially
```java
AnimatorSet animatorSet = new AnimatorSet();
animatorSet.playSequentially(ModelAnimator.ofMultipleAnimations(model, "walk", "run"));
animatorSet.start();
```

#### Auto Cancel
Here you can see that no call to `animator.cancel()` is required because the
`animator.setAutoCancel(boolean)` is set to true by default
```java
ObjectAnimator walkAnimator = ModelAnimator.ofAnimation(model, "walk");
walkButton.setOnClickListener(v -> walkAnimator.start());

ObjectAnimator runAnimator = ModelAnimator.ofAnimation(model, "run");
runButton.setOnClickListener(v -> runAnimator.start());
```


### Multiple Models with Multiple Animations

For a synchronised animation set like animating a complete scene with multiple models
time or sequentially, please consider using an `AnimatorSet` with one
`ModelAnimator` parametrized per step
```java
AnimatorSet completeFly = new AnimatorSet();

ObjectAnimator liftOff = ModelAnimator.ofAnimationFraction(airPlaneModel, "FlyAltitude",0, 40);
liftOff.setInterpolator(new AccelerateInterpolator());

AnimatorSet flying = new AnimatorSet();
ObjectAnimator flyAround = ModelAnimator.ofAnimation(airPlaneModel, "FlyAround");
flyAround.setRepeatCount(ValueAnimator.INFINITE);
flyAround.setDuration(10000);
ObjectAnimator airportBusHome = ModelAnimator.ofAnimationFraction(busModel, "Move", 0);
flying.playTogether(flyAround, airportBusHome);

ObjectAnimator land = ModelAnimator.ofAnimationFraction(airPlaneModel, "FlyAltitude", 0);
land.setInterpolator(new DecelerateInterpolator());

completeFly.playSequentially(liftOff, flying, land);
```


### Morphing animation

Assuming a character object has a skeleton, one keyframe track could store the data for the
position changes of the lower arm bone over time, a different track the data for the rotation
changes of the same bone, a third the track position, rotation or scaling of another bone, and so
on. It should be clear, that an ModelAnimation can act on lots of such tracks.

Assuming the model has morph targets (for example one morph target showing a friendly face
and another showing an angry face), each track holds the information as to how the influence of a
certain morph target changes during the performance of the clip.

In a glTF context, this {@link android.animation.Animator} updates matrices according to glTF
animation and skin definitions.


#### ModelAnimator can be used for two things
* Updating matrices in `TransformManager` components according to the model animation definitions.
* Updating bone matrices in `RenderableManager` com ## Animations

Every PropertyValuesHolder that applies a modification on the time position of the animation
must use the `ModelAnimation.TIME_POSITION` instead of its own Property in order to possibly cancel
any ObjectAnimator operating time modifications on the same ModelAnimation.

More information about Animator:
<https://developer.android.com/guide/topics/graphics/prop-animation>