package com.gorisse.thomas.ar.environmentlights

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

val String.isAbsolute get() = Uri.parse(this).scheme in listOf("http", "https")
val String.isRelative get() = !isAbsolute

fun String.startingWith(other: String): String {
    return if (!startsWith(other)) {
        other + this
    } else {
        this
    }
}

fun File.create(deletePrevious: Boolean = false): File {
    if (exists() && deletePrevious) {
        delete()
    }
    if (!exists()) {
        parentFile?.mkdirs()
        createNewFile()
    }
    return this
}

internal fun newFilename(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
fun Context.createFile(
    dir: File = this.filesDir,
    filename: String = newFilename(),
    extension: String? = null
): File = File(
    dir,
    filename + (extension.takeUnless { it.isNullOrEmpty() }?.startingWith(".") ?: "")
).create(true)

fun Context.createExternalFile(
    environment: String,
    filename: String = newFilename(),
    extension: String? = null
) = createFile(Environment.getExternalStoragePublicDirectory(environment)!!, filename, extension)

class FitsSystemWindowsConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    init {
        doOnAttach {
            applyFitsSystemWindows(fitsSystemWindows)
        }
    }
}

data class Insets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

fun insetsOf(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) =
    Insets(left, top, right, bottom)

var View.paddings
    get() = insetsOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    set(value) {
        updatePadding(value.left, value.top, value.right, value.bottom)
    }
var View.margins
    get() = insetsOf(marginLeft, marginTop, marginRight, marginBottom)
    set(value) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            setMargins(value.left, value.top, value.right, value.bottom)
        }
    }

fun View.applyFitsSystemWindows(fitsSystemWindows: Boolean) {
    applySystemWindows(fitsSystemWindows, fitsSystemWindows, fitsSystemWindows, fitsSystemWindows)
    if (this is ViewGroup) {
        forEach { child ->
            if (child.fitsSystemWindows != fitsSystemWindows) {
                child.applyFitsSystemWindows(child.fitsSystemWindows)
            }
        }
    }
}

fun View.applySystemWindows(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true
) {
    doOnApplyWindowInsets { view, insets, initialPadding, initialMargins ->
        val usePadding = view is ViewGroup && !view.clipToPadding

        var (left, top, right, bottom) = if (usePadding) initialPadding else initialMargins

        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
            if (applyLeft && (layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT || (layoutParams as? ConstraintLayout.LayoutParams)?.leftToLeft == ConstraintLayout.LayoutParams.PARENT_ID)
            ) {
                left += insets.systemWindowInsetLeft
            }
            if (applyTop && (layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT ||
                        (layoutParams as? ConstraintLayout.LayoutParams)?.topToTop == ConstraintLayout.LayoutParams.PARENT_ID)
            ) {
                top += insets.systemWindowInsetTop
            }
            if (applyRight && (layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                        (layoutParams as? ConstraintLayout.LayoutParams)?.rightToRight == ConstraintLayout.LayoutParams.PARENT_ID)
            ) {
                right += insets.systemWindowInsetRight
            }
            if (applyBottom && (layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT ||
                        (layoutParams as? ConstraintLayout.LayoutParams)?.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID)
            ) {
                bottom += insets.systemWindowInsetBottom
            }
            if (usePadding) {
                paddings = insetsOf(left, top, right, bottom)
            } else {
                margins = insetsOf(left, top, right, bottom)
            }
        }
    }
}

fun View.doOnApplyWindowInsets(block: (View, WindowInsetsCompat, initialPadding: Insets, initialMargins: Insets) -> Unit) {
    // Create a snapshot of the view's padding state
    val initialPaddings = paddings
    val initialMargins = margins
    // Set an actual OnApplyWindowInsetsListener which proxies to the given
    // lambda, also passing in the original padding state
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        block(view, insets, initialPaddings, initialMargins)
        // Always return the insets, so that children can also use them
        insets
    }
    // Request some insets
    doOnAttach {
        ViewCompat.requestApplyInsets(it)
    }
}