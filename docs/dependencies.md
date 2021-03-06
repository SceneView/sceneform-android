## Dependencies


### RELEASES

*app/build.gradle*
```gradle
dependencies {
     implementation("com.gorisse.thomas.sceneform:sceneform:version")
}
```


### SNAPSHOT

*/build.gradle*
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

*app/build.gradle*
```gradle
dependencies {
    implementation 'com.github.ThomasGorisse.sceneform-android-sdk:sceneform:-SNAPSHOT'
}
```