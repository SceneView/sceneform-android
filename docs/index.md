### :zap: **NEWS - 11/07/2020** :zap:
SceneformMaintained is now part of the SceneView Open Community.

Everyone interested can participate in improvements or make feature queries.

**Come talk with us on the [Discord channel](https://discord.gg/ZuakSUPM) (Please don't use it for issues, go to the GitHub Issues section instead)**

**Mail us [sceneview@gorisse.com](mailto:sceneview@gorisse.com) (Please don't use it for issues, go to the GitHub Issues section instead)**
### 
### :zap: :zap:

---

---

### :zap: **NEWS - 11/01/2020** :zap:
We are currently releasing the successor of Sceneform Maintained which is has greats improvements and latest ARCore features: DepthHit, InstantPlacement,...

More infos: [SceneView for Android](https://sceneview.github.io/sceneform-android/sceneview)

If you want to help us, see what it looks like or be an early user, you can [participate here](https://github.com/sponsors/ThomasGorisse/sponsorships?sponsor=ThomasGorisse&tier_id=107219&preview=false) for early access.
### 
### :zap: :zap:

---

[![Maven Central](https://img.shields.io/maven-central/v/com.gorisse.thomas.sceneform/sceneform.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.gorisse.thomas.sceneform%22%20AND%20a:%22sceneform%22)

* Android gradle dependency in Kotlin/Java
* No OpenGL or Unity need
* Latest versions of [ARCore SDK](https://github.com/google-ar/arcore-android-sdk) and [Filament](https://github.com/google/filament)
* Latest versions of Android dependencies (Android Build tools, AndroidX,...)
* Available on `mavenCentral()`
* Supports <a href="https://www.khronos.org/gltf/">glTF</a> format
* glTF/glb with animations support
* Augmented Images supported
* Augmented Faces supported
* Depth supported
* Simple model loading for basic usage


This repository is a fork of [Sceneform](https://github.com/google-ar/sceneform-android-sdk) Copyright (c) 2021 Google Inc.  All rights reserved.


#### Maintained and continued by [Nikita Zaytsev](https://github.com/grassydragon), [Vojta Maiwald](https://github.com/VojtaMaiwald), [Brigido Rodriguez](https://github.com/imbrig), [Fvito](https://github.com/fvito), [Marius Kajczuga](https://github.com/Venthorus), [Robert Gregat](https://github.com/RGregat) and [Thomas Gorisse](https://github.com/ThomasGorisse)


---

### :star: Star the repository to help us being known

---

## Dependencies

*app/build.gradle*
```gradle
dependencies {
     implementation("com.gorisse.thomas.sceneform:sceneform:1.21.0")
}
```
[**more...**](https://sceneview.github.io/sceneform-android/dependencies)



## Usage (Simple model viewer)


### Update your `AndroidManifest.xml`

*AndroidManifest.xml*
```xml
<uses-permission android:name="android.permission.CAMERA" />

<application>
    ...
    <meta-data android:name="com.google.ar.core" android:value="optional" />
</application>
```
[**more...**](https://sceneview.github.io/sceneform-android/manifest)


### Add the `View` to your `layout`
*res/layout/main_activity.xml*
```xml
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/arFragment"
    android:name="com.google.ar.sceneform.ux.ArFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```
[**sample...**](https://github.com/SceneView/sceneform-android/blob/master/samples/ar-model-viewer/src/main/res/layout/fragment_main.xml)


### Edit your `Activity` or `Fragment`
*src/main/java/.../MainActivity.java*
```kotlin

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Load model.glb from assets folder or http url
    (supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment)
        .setOnTapPlaneGlbModel("model.glb")
}
```

**Or**

*src/main/java/.../MainFragment.java*
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Load model.glb from assets folder or http url
    (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment)
        .setOnTapPlaneGlbModel("https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb")
}
```
[**kotlin sample...**](https://github.com/SceneView/sceneform-android/blob/master/samples/ar-model-viewer/src/main/java/com/google/ar/sceneform/samples/gltf/MainFragment.kt)

[**java sample...**](https://github.com/SceneView/sceneform-android/tree/master/samples/ar-model-viewer-java/src/main/java/com/google/ar/sceneform/samples/gltf/MainActivity.java)



## Samples


### glTF with animation

[![Full Video](https://user-images.githubusercontent.com/6597529/124511737-1f6ee300-ddd7-11eb-8f97-ff8ba45809d5.gif)  
**full video...**](https://user-images.githubusercontent.com/6597529/124378254-c4db6700-dcb0-11eb-80a2-2e7381d60906.mp4)

```kotlin
arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
    // Create the Anchor
    arFragment.arSceneView.scene.addChild(AnchorNode(hitResult.createAnchor()).apply {
        // Create the transformable model and add it to the anchor
        addChild(TransformableNode(arFragment.transformationSystem).apply {
            renderable = model
            renderableInstance.animate(true).start()
        })
    })
}
```
[**kotlin sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/ar-model-viewer)

[**java sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/ar-model-viewer-java)


### Depth Occlusion


| ![Depth Occlusion 01](https://user-images.githubusercontent.com/6597529/140717447-6f3a833c-7236-4680-a380-4ebf857edd7a.png) | ![Depth Occlusion 02](https://user-images.githubusercontent.com/6597529/140717611-e3216b28-a43a-4fd7-93fd-b39d8bd3e954.png) | ![Depth Occlusion 03](https://user-images.githubusercontent.com/6597529/140717767-d68bc373-3ddf-4234-bd2a-0c1e89c0efe2.png) |
| - | - | - |

```kotlin
arFragment.apply {
    setOnSessionConfigurationListener { session, config ->
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        }
    }
    setOnViewCreatedListener { arSceneView ->
        // Available modes: DEPTH_OCCLUSION_DISABLED, DEPTH_OCCLUSION_ENABLED
        arSceneView.cameraStream.depthOcclusionMode =
            CameraStream.DepthOcclusionMode.DEPTH_OCCLUSION_ENABLED
    }
}
```
[**documentation...**](https://sceneview.github.io/sceneform-android/depth)

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/depth)


### Augmented Images

![Augmented Images 01](https://user-images.githubusercontent.com/6597529/124591171-47ecf080-de5c-11eb-9196-8fe123e7ea58.gif)

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/augmented-images)

### Augmented Faces
| ![Augmented Faces 01](https://user-images.githubusercontent.com/6597529/135052644-79cf8964-7778-42d0-83f6-1839b9c8ab8b.gif) | ![Augmented Faces 02](https://user-images.githubusercontent.com/6597529/135059873-3342d169-5ff4-4d0d-bdec-fdac44c282ba.png) | ![Augmented Faces 03](https://user-images.githubusercontent.com/6597529/135051599-14742a53-69db-47d2-a27b-ba641a1d2609.gif) |
| - | - | - |

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/augmented-faces)


### Cloud Anchors

![image](https://user-images.githubusercontent.com/6597529/139030872-2fcaa720-42c8-4927-9308-bd7765b91771.png)

```kotlin
// Create a new anchor = the pose of which ARCore will try to resolve using the ARCore Cloud Anchor service and the provided cloudAnchorId
sceneView.session?.resolveCloudAnchor(cloudAnchorId)?.let { resolvedAnchor ->
  sceneView.scene.addChild(AnchorNode(resolvedAnchor).apply {
      addChild(VideoNode(context, MediaPlayer.create(context, R.raw.restaurant_presentation).apply {
                  this.start()
              },null)
      )
  })
}
```


### Environment Lights

| ![Environment Lights 01](https://user-images.githubusercontent.com/6597529/135054559-1c5282d6-2476-4e71-92c6-cfc234cb5f3b.gif) | ![Environment Lights 02](https://user-images.githubusercontent.com/6597529/135059208-57e101d4-6215-4941-9543-20d09397e4c3.png) | ![Environment Lights 03](https://user-images.githubusercontent.com/6597529/131824742-0207ccfb-7f24-487d-b2cf-4765753697d7.gif) |
| - | - | - |
| ![Environment Lights 04](https://user-images.githubusercontent.com/6597529/131822279-613edadf-bdaf-4ae9-9da6-d620504af5f0.jpg) | ![Environment Lights 05](https://user-images.githubusercontent.com/6597529/135060214-b9b9be36-b0d2-4929-b957-c3a04fe40d1e.png) | ![Environment Lights 06](https://user-images.githubusercontent.com/6597529/131825447-d52d8c3e-2801-4d6a-8c51-8b32231c14d6.jpg) |

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/environment-lights)


### Video texture

| ![Video texture 01](https://user-images.githubusercontent.com/6597529/124379676-b85b0c80-dcb8-11eb-8250-d7ec7a449fad.gif) | ![Video texture 02](https://user-images.githubusercontent.com/6597529/124379556-13403400-dcb8-11eb-9b56-00e36979eb0f.gif) | ![Video texture 03](https://user-images.githubusercontent.com/6597529/135055851-3d3dca81-2943-4f21-b778-5fd32fa46145.gif) |
| - | - | - |

```kotlin
arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
    // Create the Anchor
    arFragment.arSceneView.scene.addChild(AnchorNode(hitResult.createAnchor()).apply {
        addChild(VideoNode(context, MediaPlayer.create(context, R.raw.video).apply {
            start()
        }, chromaKeyColor, null))
    })
}
```

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/video-texture)


### Dynamic materials/textures

| ![Dynamic materials 01](https://miro.medium.com/max/2000/1*0XSLVleiR5ijFD1aIoCm-A.jpeg) | ![Dynamic materials 02](https://images.squarespace-cdn.com/content/v1/5bf7a0d55ffd203cac0e0920/1583270741496-7FJ9O190FD2FXI5JCWM0/texture.png?format=300w) |
| - | - |

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/image-texture)


### Non AR usage

![Non AR Usage 01](http://download.tuxfamily.org/sdtraces/BottinHTML/Bottin_D-J_files/282584ef7ae1d420897d47bd7ba4d46f.jpeg)

[**sample project...**](https://github.com/SceneView/sceneform-android/tree/master/samples/3d-model-viewer)



## Demo

[![Get it on Google Play](https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/Google_Play_Store_badge_EN.svg/320px-Google_Play_Store_badge_EN.svg.png)](https://play.google.com/store/apps/details?id=com.gorisse.thomas.ar.environmentlights)

| [![Youtube Video 01](https://yt-embed.herokuapp.com/embed?v=9QP43nOSItU)](https://www.youtube.com/watch?v=9QP43nOSItU) | [![Youtube Video 02](https://yt-embed.herokuapp.com/embed?v=jpmWjigA3Ms)](https://www.youtube.com/watch?v=jpmWjigA3Ms) |
| - | - |



## Emulator


### Known working configuration


![image](https://user-images.githubusercontent.com/6597529/117983402-3513df00-b337-11eb-841d-49548429363e.png)

[**more...**](https://sceneview.github.io/sceneform-android/emulator)



## Go further


### AR Required vs AR Optional

If your app requires ARCore (AR Required) and is not only (AR Optional), use this manifest to indicates that this app requires Google Play Services for AR (AR Required) and results in
the app only being visible in the Google Play Store on devices that support ARCore:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.ar" android:required="true"/>

<application>
    ...
    <meta-data android:name="com.google.ar.core" android:value="required" />
</application>
```
[**more...**](https://sceneview.github.io/sceneform-android/manifest)


### Nodes

To add a node or multiple nodes to the Scene when the user press on a surface, you can override the `onTapPlane` function from a `BaseArFragment.OnTapArPlaneListener`:
```kotlin
arFragment.setOnTapArPlaneListener(::onTapPlane)
```

```kotlin
arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
    // Create the Anchor
    arFragment.arSceneView.scene.addChild(AnchorNode(hitResult.createAnchor()).apply {
        // Create the transformable model and add it to the anchor.
        addChild(TransformableNode(arFragment.transformationSystem).apply {
            renderable = model
            renderableInstance.animate(true).start()
            // Add child model relative the a parent model
            addChild(Node().apply {
                // Define the relative position
                localPosition = Vector3(0.0f, 1f, 0.0f)
                // Define the relative scale
                localScale = Vector3(0.7f, 0.7f, 0.7f)
                renderable = modelView
            })
        })
    })
}
```
[**sample...**](https://github.com/SceneView/sceneform-android/tree/master/samples/ar-model-viewer)


## Remove or Hide a node

### Remove an AnchorNode from the Scene

```kotlin
anchorNode.anchor = null
```

### Remove a Model Node, VideoNode, AugmentedFaceNode,... from the Scene
```kotlin
node.parent = null
```

### Show/Hide a Node = Don't render it
```kotlin
node.enabled= false
```

[**documentation...**](https://sceneview.github.io/sceneform-android/remove_node)

## Frame Rate (FPS-Bound)

### Upper-Bound

The Update-Rate of the rendering is limited through the used camera config of ARCore. For most Smartphones it is 30 fps and for the Pixel Smartphones it is 60 fps. The user can manually change this value *(you should know what you are doing)*.

```kotlin
arFragment.setOnViewCreatedListener { arSceneView ->
    // Set a higher bound for the frame rate
    arSceneView.setMaxFramesPerSeconds(60)
}
```
> The default value is **60**.

[**documentation...**](https://sceneview.github.io/sceneform-android/fps_bound)


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
  ![](https://user-images.githubusercontent.com/6597529/140717849-ae1d99e3-74c4-4b8a-8b05-242d6ee7d865.jpg)
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

[**more...**](https://sceneview.github.io/sceneform-android/animations)



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
