# Remove or Hide a node

## Remove an AnchorNode from the Scene
```kotlin
anchorNode.anchor = null
```
**Does this:**
- Disable the AnchorNode repositioning every Anchor position update
- Remove itself and all it child Nodes from the Scene = setParent(null)
- Disable/Stop rendering itself and all it child Nodes = setEnable(false)

## Remove a Model Node, VideoNode, AugmentedFaceNode,... from the Scene
```kotlin
node.parent = null
```
**OR** (do the same thing)
```kotlin
parentNode.removeChild(node)
```
**Both does this:**
- Remove himself and all it child Nodes from the Scene
- Disable/Stop rendering himself and all it child Nodes = setEnable(false)

## Show/Hide a Node = Don't render it
```kotlin
node.enabled= false
```
**Does this:**
- Disable/Stop rendering himself and all it child Nodes = setEnable(false)

## Destroy a Model Node = free renderable resources
```kotlin
node.renderableInstance.destroy()
```