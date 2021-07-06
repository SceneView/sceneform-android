# FPS-Bound

### Upper-Bound

The Update-Rate of the rendering is limited through the used camera config of ARCore. For most Smartphones it is 30 fps and for the Pixel Smartphones it is 60 fps. The user can manually change this value *(you should know what you are doing)*.

```java
@Override
public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
    arSceneView.setMaxFramesPerSeconds([int])
}
```

> The default value is **60**.



### Further adjustments

The Upper-Bound can be further adjusted if needed. To do so a Frame-Rate-Factor can be set. 

```java
@Override
public void onViewCreated(ArFragment arFragment, ArSceneView arSceneView) {
    arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
}
```

Three different factor values are available:

- FULL (1)
- HALF (2)
- THIRD (3)

The Frame-Rate-Factor divides the Upper-Bound to further reduce the maximum allowed Frame-Rate.

```
60/FULL = 60
60/HALF = 30
60/THIRD = 20
```

> The default value for the Frame-Rate-Factor is **FULL**
