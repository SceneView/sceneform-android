Sceneform SDK for Android - Maintained
======================================
Copyright (c) 2018 Google Inc.  All rights reserved.

[ ![Core - jCenter](https://img.shields.io/badge/Core%20--%20jCenter-1.18.0-blue) ](https://bintray.com/thomasgorisse/maven/com.gorisse.thomas.sceneform:core/1.18.0/link)
[ ![Ux - jCenter](https://img.shields.io/badge/Ux%20--%20jCenter-1.18.0-blue) ](https://bintray.com/thomasgorisse/maven/com.gorisse.thomas.sceneform:ux/1.18.0/link)

Sceneform is a 3D framework with a physically based renderer that's optimized
for mobile devices and that makes it easy for you to build augmented reality
apps without requiring OpenGL or Unity.

This repository is the continuation of the [Archived version](https://github.com/google-ar/sceneform-android-sdk)

## Usage benefits
* Continuous compatibility with the latests versions of [ARCore SDK](https://github.com/google-ar/arcore-android-sdk) and [Filament](https://github.com/google/filament) 
* Based on AndroidX
* Avaiable has jCenter dependencies 
* Supports <a href="https://www.khronos.org/gltf/">glTF</a> instead of olds <code>SFA</code> and <code>SFB</code> formats 
* Open source 


## Dependencies

Sceneform is available on `jCenter()`

```gradle
//Scenform Core
implementation("com.gorisse.thomas.sceneform:core:1.18.0")
//Scenform Fragment
implementation("com.gorisse.thomas.sceneform:ux:1.18.0")
```

## Usage

Check out the [Full source code samples](https://github.com/ThomasGorisse/sceneform-android-sdk/tree/master/samples).

### Update your AndroidManifest.xml

Modify your ```AndroidManifest.xml``` to indicate that your app uses (AR Optional) or requires (AR Required) ARCore and CAMERA access:
```xml
<!-- Both "AR Optional" and "AR Required" apps require CAMERA permission. -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- Sceneform requires OpenGL ES 3.0 or later. -->
<uses-feature android:glEsVersion="0x00030000" android:required="true" />

<!-- Indicates that app requires ARCore ("AR Required"). Ensures the app is
     visible only in the Google Play Store on devices that support ARCore.
     For "AR Optional" apps remove this line. -->
<uses-feature android:name="android.hardware.camera.ar" />

<application>
    …
    <!-- Indicates that app requires ARCore ("AR Required"). Causes the Google
         Play Store to download and install Google Play Services for AR along
         with the app. For an "AR Optional" app, specify "optional" instead of
         "required".
    -->
    <meta-data android:name="com.google.ar.core" android:value="required" />
</application>
```

### Add the fragment to your layout
```xml
<fragment android:name="com.google.ar.sceneform.ux.ArFragment"
      android:id="@+id/ux_fragment"
      android:layout_width="match_parent"
      android:layout_height="match_parent" />
```

### Add your renderable
```kotlin
 ModelRenderable.builder()
    .setSource(
        this,
        // Http source
        Uri.parse("https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb")
        // Or raw resource : model.glb
        // R.raw.model
    )
    .setIsFilamentGltf(true)
    .build()
    .thenAccept { modelRenderable: ModelRenderable? ->
        activity.renderable = modelRenderable
    }
    .exceptionally { throwable: Throwable? ->
        Toast.makeText(this, "Unable to load renderable", Toast.LENGTH_LONG)
        null
    }
```

## Release notes

The SDK release notes are available on the
[releases](https://github.com/ThomasGorisse/sceneform-android-sdk/releases) page.


## License

Please see the
[LICENSE](https://github.com/ThomasGorisse/sceneform-android-sdk/blob/master/LICENSE)
file.


## OLD - Choosing the right Sceneform SDK version for your project

As of ARCore release 1.16.0, Google open-sourced the implementation of Sceneform
allowing to extend Sceneform's features and capabilities. As part of the
1.16.0 release, support for `SFA` and `SFB` assets was removed in favor of
adding `glTF` support

You can continue to use Sceneform 1.15.0 (or earlier). There is no requirement
that you migrate to Sceneform 1.16.0.

Do not use Sceneform 1.17.0 as that release will not work correctly. (Sceneform
1.17.1 can be used, but is otherwise identical to Sceneform 1.15.0.)


<table>
  <tr>
    <th>Sceneform SDK</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Sceneform SDK<br>versions <b>1.0.0 - 1.15.0</b></td>
    <td>
      <ul>
        <li>Closed source</li>
        <li>Included in your project as an external Gradle dependency</li>
        <li>
          <code>FBX</code> and <code>OBJ</code> files can be converted to
          Sceneform's <code>SFA</code> and <code>SFB</code> Sceneform
          formats
        </li>
      </ul>
    </td>
  </tr>
  <tr>
    <td>Sceneform SDK<br>version <b>1.16.0</b></td>
    <td>
      <ul>
        <li>Open source</li>
        <li>Built alongside an application as a Gradle module</li>
        <li>
          Supports <a href="https://www.khronos.org/gltf/">glTF</a> instead of
          <code>SFA</code> and <code>SFB</code> Sceneform formats
        </li>
      </ul>
    </td>
  </tr>
  <tr>
    <td>Sceneform SDK<br>version <b>1.17.0</b></td>
    <td>Do not use</td>
  </tr>
  <tr>
    <td>Sceneform SDK<br>version <b>1.17.1</b></td>
    <td>Identical to version 1.15.0</td>
  </tr>
   <tr>
    <td>Sceneform SDK<br>version <b>1.18.0</b></td>
    <td>
      <ul>
        <li>Open source</li>
        <li>Avaiable on jCenter dependencies</li>
        <li>Based on AndroidX</li>
        <li>
          Supports <a href="https://www.khronos.org/gltf/">glTF</a> instead of
          olds <code>SFA</code> and <code>SFB</code> formats.
        </li>
        <li>
          Compatible with the latests versions of <a href="https://github.com/google-ar/arcore-android-sdk">ARCore SDK</a> and <a href="https://github.com/google/filament">Filament</a>
        </li>
      </ul>
    </td>
  </tr>
</table>

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
