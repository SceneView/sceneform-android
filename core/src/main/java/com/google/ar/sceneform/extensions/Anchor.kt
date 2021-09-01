package com.google.ar.sceneform.extensions

import com.google.ar.core.Anchor

fun Anchor.destroy() {
    detach()
}