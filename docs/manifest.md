
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
**[more...](https://developers.google.com/ar/develop/java/enable-arcore)**