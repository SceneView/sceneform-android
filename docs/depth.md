# Depth


## DepthMode
> The DepthMode is automatically set based on the Session config.

- **NO_DEPTH**
The Session is not configured to use the Depth-API
This is the default value

- **DEPTH**
The Session is configured to use the DepthMode AUTOMATIC

- **RAW_DEPTH**
The Session is configured to use the DepthMode RAW_DEPTH_ONLY


## DepthOcclusionMode
> The DepthOcclusionMode can be set by the user.

- **DEPTH_OCCLUSION_DISABLED**
Use this value if the standard camera material should be applied to the CameraStream Renderable even if the Session configuration has set the DepthMode to Config.DepthMode.AUTOMATIC or Config.DepthMode.RAW_DEPTH_ONLY. This Option is useful, if you want to use the DepthImage or RawDepthImage or just the DepthPoints without the occlusion effect.
This is the default value

- **DEPTH_OCCLUSION_ENABLED**
Set the occlusion material. If the Session is not configured properly the standard camera material is used. Valid Session configuration for the DepthMode are Config.DepthMode.AUTOMATIC and Config.DepthMode.RAW_DEPTH_ONLY.


## Examples

```java
@Override
public void onSessionConfiguration(Session session, Config config) {
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
        config.setDepthMode(Config.DepthMode.AUTOMATIC);
    }
    config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
}

@Override
public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
    // By default he occlusion is disabled even if the DepthMode is set to AUTOMATIC or RAW_DEPTH_ONLY
    // Not every Depth use case requires the occlusion feature!
    arSceneView.getCameraStream()
        .setDepthOcclusionMode(CameraStream.DepthOcclusionMode
            .DEPTH_OCCLUSION_ENABLED);
}
```

![Occlusion_AUTOMATIC](https://user-images.githubusercontent.com/3974162/122067720-1a300100-cdf4-11eb-8af1-8969c0df1f3e.jpg)

```java
@Override
public void onSessionConfiguration(Session session, Config config) {
    if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY))
        config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
    config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
}

@Override
public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
    // By default he occlusion is disabled even if the DepthMode is set to AUTOMATIC or RAW_DEPTH_ONLY
    // Not every Depth use case requires the occlusion feature!
    arSceneView
        .getCameraStream()
        .setDepthOcclusionMode(CameraStream.DepthOcclusionMode
            .DEPTH_OCCLUSION_ENABLED); // Available modes: DEPTH_OCCLUSION_DISABLED, DEPTH_OCCLUSION_ENABLED
    }
```

![Occlusion_RAW_DEPTH_ONLY](https://user-images.githubusercontent.com/3974162/122067801-27e58680-cdf4-11eb-8568-92124a23d336.jpg)

```java
@Override
public void onSessionConfiguration(Session session, Config config) {
    if (session.isDepthModeSupported(Config.DepthMode.DISABLED))
        config.setDepthMode(Config.DepthMode.DISABLED);
    config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
}
```

![Occlusion_DISABLED](https://user-images.githubusercontent.com/3974162/122067844-32078500-cdf4-11eb-8ed4-c667dea6933e.jpg)

Any of the following combinations results into the disabled Occlusion use case

```java
@Override
public void onSessionConfiguration(Session session, Config config) {
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
        config.setDepthMode(Config.DepthMode.DISABLED);
   config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
}
```
```java
@Override
public void onSessionConfiguration(Session session, Config config) {
    if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY))
        config.setDepthMode(Config.DepthMode.DISABLED);
    config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
}
```

If the user wants to activate the DepthMode by setting it to AUTOMATIC or RAW_DEPTH_ONLY but is not interested about the occlusion of virtual objects behind real world objects he can set the DepthOcclusionMode value to DEPTH_OCCLUSION_DISABLED. 

```java
@Override
public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
    // Currently, the tone-mapping should be changed to FILMIC
    // because with other tone-mapping operators except LINEAR
    // the inverseTonemapSRGB function in the materials can produce incorrect results.
    // The LINEAR tone-mapping cannot be used together with the inverseTonemapSRGB function.
    Renderer renderer = arSceneView.getRenderer();

    if (renderer != null) {
        renderer.getFilamentView().setColorGrading(
            new ColorGrading.Builder()
                .toneMapping(ColorGrading.ToneMapping.FILMIC)
                .build(EngineInstance.getEngine().getFilamentEngine())
        );
    }

    arSceneView
        .getCameraStream()
        .setDepthOcclusionMode(CameraStream.DepthOcclusionMode
            .DEPTH_OCCLUSION_ENABLED); // Available modes: DEPTH_OCCLUSION_DISABLED, DEPTH_OCCLUSION_ENABLED
    }
```