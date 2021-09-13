package com.gorisse.thomas.sceneform.arcore

import com.google.ar.core.Anchor

fun Anchor.destroy() {
    detach()
}