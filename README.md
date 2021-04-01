Maintained Sceneform SDK for Android
====================================

#### This repository is a fork of [Sceneform](https://github.com/google-ar/sceneform-android-sdk)
Copyright (c) 2018 Google Inc.  All rights reserved.

[ ![jCenter](https://img.shields.io/badge/jCenter-1.18.7-blue) ](https://bintray.com/thomasgorisse/maven/com.gorisse.thomas.sceneform:sceneform/1.18.7/link)

Sceneform is a 3D framework with a physically based renderer that's optimized
for mobile devices and that makes it easy for you to build Augmented Reality (AR)
apps without requiring OpenGL or Unity.

![Sample Lion Model](https://thomasgorisse.github.io/sceneform-android-sdk/images/samples/model_lion.jpg)

## Usage benefits
* Continuous compatibility with the latests versions of [ARCore SDK](https://github.com/google-ar/arcore-android-sdk) and [Filament](https://github.com/google/filament) 
* Based on AndroidX
* Available has jCenter dependency
* Supports <a href="https://www.khronos.org/gltf/">glTF</a> format
* Animations made easy
* Simple model loading for basic usage



## Dependencies

*app/build.gradle*
```gradle
dependencies {
     implementation("com.gorisse.thomas.sceneform:sceneform:1.18.7")
}
```
**[more...](https://thomasgorisse.github.io/sceneform-android-sdk/dependencies)**



## Basic Usage (Simple model viewer)


### Update your `AndroidManifest.xml`

*AndroidManifest.xml*
```xml
<uses-permission android:name="android.permission.CAMERA" />

<application>
    …
    <meta-data android:name="com.google.ar.core" android:value="optional" />
</application>
```
**[more...](https://thomasgorisse.github.io/sceneform-android-sdk/manifest)**


### Add the `View` to your `layout`
*res/layout/main_activity.xml*
```xml
<FrameLayout
    android:id="@+id/arFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```
**[sample...](https://github.com/ThomasGorisse/sceneform-android-sdk/blob/master/samples/texture/src/main/res/layout/activity_main.xml)**


### Edit your `Activity` or `Fragment`
*src/main/java/…/MainActivity.java*
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    …
    getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            // Load model.glb from assets folder or http url
            arFragment.setOnTapPlaneGlbModel("model.glb", new ArFragment.OnTapModelListener() {
                @Override
                public void onModelAdded(RenderableInstance renderableInstance) {
                }
    
                @Override
                public void onModelError(Throwable exception) {
                }
            });
        }
    });
    if (savedInstanceState == null) {
        if (Sceneform.isSupported(this)) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.arFragment, ArFragment.class, null)
                    .commit();
        }
    }
}
```

**[sample...](https://github.com/ThomasGorisse/sceneform-android-sdk/blob/master/samples/texture/src/main/java/com/google/ar/sceneform/samples/texture/MainActivity.java)**


## Go further


#### AR Required vs AR Optional

If your app requires ARCore (AR Required) and is not only (AR Optional), use this manifest to indicates that this app requires Google Play Services for AR (AR Required) and results in
the app only being visible in the Google Play Store on devices that support ARCore:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.ar" android:required="true"/>

<application>
    …
    <meta-data android:name="com.google.ar.core" android:value="required" />
</application>
```
**[more...](https://thomasgorisse.github.io/sceneform-android-sdk/manifest)**


## Nodes

To add a node or multiply nodes to the Scene when the user press on a surface, you can override the `onTapPlane` function from a `BaseArFragment.OnTapArPlaneListener`:
```java
arFragment.setOnTapArPlaneListener(MainActivity.this);
```

```java
@Override
public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
   if (renderable == null) {
       Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show();
       return;
   }

   // Create the Anchor.
   Anchor anchor = hitResult.createAnchor();
   AnchorNode anchorNode = new AnchorNode(anchor);
   anchorNode.setParent(arFragment.getArSceneView().getScene());

   // Create the transformable model and add it to the anchor.
   TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
   model.setParent(anchorNode);
   model.setRenderable(renderable);
   model.select();
}
```
**[sample...](https://github.com/ThomasGorisse/sceneform-android-sdk/blob/master/samples/texture/src/main/java/com/google/ar/sceneform/samples/texture/MainActivity.java)**


## Animations

Until now, only `RenderableInstance` are animtable. Below `model` corresponds to a `RenderablaInstance` returned from a `node.getRenderableInstance()`


### Basic usage

On a very basic 3D model like a single infinite rotating sphere, you should not have to
use ModelAnimator but probably instead just call:

```java
model.animate(repeat).start();
```


### Single Model with Single Animation

If you want to animate a single model to a specific timeline position, use:
```java
ModelAnimator.ofAnimationFrame(model, "AnimationName", 100).start();
```
```java
ModelAnimator.ofAnimationFraction(model, "AnimationName", 0.2f, 0.8f, 1f).start();
```
```java
ModelAnimator.ofAnimationTime(model, "AnimationName", 10.0f)}.start();
```

#### Where can I find the "AnimationName" ?
The animation names are definied at the 3D model level.  
You can compare it to a track playing something corresponding to a particular behavior in you model.  
\
For example, on Blender "AnimationName" can correspond to
* An action defined inside the `Non linear Animation View Port`  
![](https://thomasgorisse.github.io/sceneform-android-sdk/images/tutorials/screenshotl_blender_animation_01.jpg)
* A single object behavior in the `Timeline ViewPort`

**To know the actual animation names of a glb/gltf file, you can drag it on a glTF Viewer like [here](https://gltf-viewer.donmccurdy.com/) and find it in the animation list.**

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

**[more...](https://thomasgorisse.github.io/sceneform-android-sdk/animations)**



## License

Please see the
[LICENSE](/LICENSE)
file.


## Brand Guidelines

The Sceneform trademark is a trademark of Google, and is not subject to the
copyright or patent license grants contained in the Apache 2.0-licensed
Sceneform repositories on GitHub. Any uses of the Sceneform trademark other than
those permitted in these guidelines must be approved by Google in advance.


### Purpose of the Brand Guidelines

These guidelines exist to ensure that the Sceneform project can share its
technology under open source licenses while making sure that the "Sceneform"
brand is protected as a meaningful source identifier in a way that's consistent
with trademark law. By adhering to these guidelines, you help to promote the
freedom to use and develop high-quality Sceneform technology.


### Acceptable uses

Because we are open-sourcing the Sceneform technology, you may use the Sceneform
trademark to refer to the project without prior written permission. Examples of
these approved references include the following:

* To refer to the Sceneform project itself;
* To refer to unmodified source code or other files shared by the Sceneform
  repositories on GitHub;
* To accurately identify that your design or implementation is based on, is for
  use with, or is compatible with the Sceneform technology.

Examples:

* "[Your Product] for Sceneform."
* "[Your Product] is a fork of the Sceneform project."
* "[Your Product] is compatible with Sceneform."


### Usage guidelines

* The Sceneform name may never be used or registered in a manner that would
  cause confusion as to Google's sponsorship, affiliation, or endorsement.
* Don't use the Sceneform name, or a confusingly similar term, as part of your
  company name, product name, domain name, or social media profile.
* Other than as permitted by these guidelines, the Sceneform name should not be
  combined with other trademarks, terms, or source identifiers.
* Don't remove, distort or alter the Sceneform name. That includes modifying the
  Sceneform name, for example, through hyphenation, combination, or
  abbreviation. Do not shorten, abbreviate, or create acronyms out of the
  Sceneform name.
* Don't display the Sceneform name using any different stylization, color, or
  font from the surrounding text.
* Don't use the term Sceneform as a verb, or use it in possessive form.


## Terms & Conditions

By downloading the Sceneform SDK for Android, you agree that the
[Google APIs Terms of Service](https://developers.google.com/terms/) governs
your use thereof.


## User privacy requirements

You must disclose the use of Google Play Services for AR (ARCore) and how it
collects and processes data, prominently in your application, easily accessible
to users. You can do this by adding the following text on your main menu or
notice screen: "This application runs on
[Google Play Services for AR](https://play.google.com/store/apps/details?id=com.google.ar.core)
(ARCore), which is provided by Google LLC and governed by the
[Google Privacy Policy](https://policies.google.com/privacy)".
