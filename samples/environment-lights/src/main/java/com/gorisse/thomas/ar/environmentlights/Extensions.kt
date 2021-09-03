package com.gorisse.thomas.ar.environmentlights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

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

    private var drawStatusBarBackground = false
    private var lastInsets: WindowInsetsCompat? = null
    private val childsMargins = mutableMapOf<View, Rect>()
    private val childsPaddings = mutableMapOf<View, Rect>()

    var statusBarBackground: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        if (ViewCompat.getFitsSystemWindows(this)) {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                (view as? FitsSystemWindowsConstraintLayout)?.apply {
                    setChildInsets(insets, insets.systemWindowInsetTop > 0)
                }
                return@setOnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
            }
            this.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            this.statusBarBackground =
                context.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimaryDark)).use {
                    it.getDrawable(0)
                }
        } else {
            this.statusBarBackground = null
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (this.drawStatusBarBackground && this.statusBarBackground != null) {
            this.lastInsets?.systemWindowInsetTop?.takeIf { it > 0 }?.let { inset ->
                statusBarBackground?.setBounds(0, 0, width, inset)
                if (canvas != null) {
                    statusBarBackground?.draw(canvas)
                }
            }
        }
    }

    private fun setChildInsets(insets: WindowInsetsCompat, draw: Boolean) {
        this.lastInsets = insets
        this.drawStatusBarBackground = draw
        setWillNotDraw(!draw && this.background == null)
        for (child in children) {
            if (child.visibility != GONE) {
                if (ViewCompat.getFitsSystemWindows(this)) {
                    if (ViewCompat.getFitsSystemWindows(child)) {
                        if (child !is ViewGroup) {
                            ViewCompat.setOnApplyWindowInsetsListener(child) { _, insets ->
                                return@setOnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
                            }
                        }
                        ViewCompat.dispatchApplyWindowInsets(child, insets)
                    } else {
                        (child.layoutParams as LayoutParams).let { layoutParams ->
                            val usePadding = child is ViewGroup && !child.clipToPadding
                            var insetMargins = Rect(
                                if (usePadding) {
                                    this.childsPaddings.getOrPut(child, {
                                        Rect(
                                            child.paddingLeft,
                                            child.paddingTop,
                                            child.paddingRight,
                                            child.paddingBottom
                                        )
                                    })
                                } else {
                                    this.childsMargins.getOrPut(child, {
                                        Rect(
                                            layoutParams.leftMargin,
                                            layoutParams.topMargin,
                                            layoutParams.rightMargin,
                                            layoutParams.bottomMargin
                                        )
                                    })
                                }
                            )
                            if (layoutParams.leftToLeft === LayoutParams.PARENT_ID || layoutParams.width == LayoutParams.MATCH_PARENT) {
                                insetMargins.left += insets.systemWindowInsetLeft
                            }
                            if (layoutParams.topToTop === LayoutParams.PARENT_ID || layoutParams.height == LayoutParams.MATCH_PARENT) {
                                insetMargins.top += insets.systemWindowInsetTop
                            }
                            if (layoutParams.rightToRight === LayoutParams.PARENT_ID || layoutParams.width == LayoutParams.MATCH_PARENT) {
                                insetMargins.right += insets.systemWindowInsetRight
                            }
                            if (layoutParams.bottomToBottom === LayoutParams.PARENT_ID || layoutParams.height == LayoutParams.MATCH_PARENT) {
                                insetMargins.bottom += insets.systemWindowInsetBottom
                            }
                            if (usePadding) {
                                child.setPadding(
                                    insetMargins.left,
                                    insetMargins.top,
                                    insetMargins.right,
                                    insetMargins.bottom
                                )
                            } else {
                                layoutParams.setMargins(
                                    insetMargins.left,
                                    insetMargins.top,
                                    insetMargins.right,
                                    insetMargins.bottom
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}