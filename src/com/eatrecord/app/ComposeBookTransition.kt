package com.eatrecord.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Camera
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.max

/** Compose owns the presented book. The Activity supplies textures and owns diary/navigation state. */
open class ComposeBookTransition(context: Context) : FrameLayout(context) {
    interface Listener {
        fun onFrame(progress: Float, left: Float, top: Float, right: Float, bottom: Float)
        fun onFinished()
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        companion object { fun from(rect: RectF) = Bounds(rect.left, rect.top, rect.right, rect.bottom) }
    }

    private data class Request(val id: Long, val from: Float, val to: Float, val duration: Int, val listener: Listener)
    private data class Scene(
        val page: Bitmap? = null, val cover: Bitmap? = null, val backdrop: Bitmap? = null,
        val source: Bounds = Bounds(0f, 0f, 0f, 0f), val destination: Bounds = Bounds(0f, 0f, 0f, 0f),
        val closeFrom: Bounds = Bounds(0f, 0f, 0f, 0f), val radiusX: Float = 0f, val radiusY: Float = 0f,
        val pageRadius: Float = 0f, val paperColor: Int = 0xfff9f3e4.toInt(),
        val closeRadiusX: Float = 0f, val closeRadiusY: Float = 0f,
        val closing: Boolean = false, val startProgress: Float = 1f, val startOpening: Float = 1f,
        val revision: Int = 0, val stationaryProgress: Float = 0f, val request: Request? = null
    )

    private class PresentedFrame {
        val bounds = RectF()
        var opening = 0f
        var radiusX = 0f
        var radiusY = 0f
        var angle = 0f
    }
    private var scene by mutableStateOf(Scene())
    private val compose = ComposeView(context)
    private val owner = SurfaceOwner()
    private var recomposer: Recomposer? = null
    private var requestId = 0L
    private var drawProgress by mutableFloatStateOf(Float.NaN)
    private var presentationOpening = Float.NaN
    private var presentationRadiusX = Float.NaN
    private var presentationRadiusY = Float.NaN
    private val presentationRect = RectF()
    private val frame = PresentedFrame()
    private val renderer = BookRenderer(resources.displayMetrics.density)
    private var presentationDisposed = false
    private val travel = CubicBezierEasing(.32f, .72f, 0f, 1f)
    private val returning = CubicBezierEasing(.25f, .1f, .25f, 1f)

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        id = View.generateViewId()
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        if (Build.VERSION.SDK_INT >= 29) {
            isForceDarkAllowed = false
            compose.isForceDarkAllowed = false
        }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        compose.setContent { Presentation() }
    }

    fun setPresentationTextures(page: Bitmap?, cover: Bitmap?, backdrop: Bitmap?, paperColor: Int) {
        if (presentationDisposed) return
        renderer.prepare(page, cover)
        scene = scene.copy(page = page, cover = cover, backdrop = backdrop, paperColor = paperColor,
            revision = scene.revision + 1)
    }

    fun setPresentationGeometry(source: RectF, destination: RectF, radiusX: Float, radiusY: Float, pageRadius: Float) {
        if (presentationDisposed) return
        scene = scene.copy(source = Bounds.from(source), destination = Bounds.from(destination),
            radiusX = radiusX, radiusY = radiusY, pageRadius = pageRadius)
        renderer.prepare(scene.page, scene.cover)
    }

    fun animatePresentation(from: Float, to: Float, durationMillis: Long, closing: Boolean,
                            closeFrom: RectF, startOpening: Float, listener: Listener) {
        if (presentationDisposed) return
        val duration = durationMillis.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val request = Request(++requestId, from, to, duration, listener)
        scene = scene.copy(closing = closing, closeFrom = Bounds.from(closeFrom),
            startProgress = max(.0001f, from), startOpening = startOpening.coerceIn(0f, 1f),
            closeRadiusX = if (presentationRadiusX.isFinite()) presentationRadiusX else scene.pageRadius,
            closeRadiusY = if (presentationRadiusY.isFinite()) presentationRadiusY else scene.pageRadius, request = request)
        publishFrame(scene, from, listener)
    }

    fun cancelPresentationMotion() {
        requestId++
        scene = scene.copy(request = null,
            stationaryProgress = if (drawProgress.isFinite()) drawProgress else scene.stationaryProgress)
    }

    fun showPresentationAt(progress: Float) {
        scene = scene.copy(stationaryProgress = progress.coerceIn(0f, 1f))
        updatePresentedFrame(scene, scene.stationaryProgress)
    }

    fun getPresentationProgress(): Float = drawProgress
    fun getPresentationOpening(): Float = presentationOpening
    fun getPresentationRect(): RectF = RectF(presentationRect)

    /** Call before the Activity recycles any texture Bitmap. */
    fun disposePresentation() {
        if (presentationDisposed) return
        presentationDisposed = true
        requestId++
        scene = Scene()
        compose.disposeComposition()
        recomposer?.cancel()
        recomposer = null
        renderer.release()
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        owner.active(true)
        val context = AndroidUiDispatcher.CurrentThread
        val clock = PresentedClock(requireNotNull(context[MonotonicFrameClock]))
        recomposer = createLifecycleAwareWindowRecomposer(coroutineContext = context + clock, lifecycle = owner.lifecycle)
        compose.setParentCompositionContext(recomposer)
        disableForceDark(compose)
    }

    override fun onDetachedFromWindow() {
        disposePresentation()
        owner.destroy()
        super.onDetachedFromWindow()
    }

    private fun disableForceDark(view: View) {
        if (Build.VERSION.SDK_INT >= 29) view.isForceDarkAllowed = false
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) disableForceDark(view.getChildAt(index))
    }

    private class PresentedClock(private val delegate: MonotonicFrameClock) : MonotonicFrameClock {
        private val intervals = AnimationFrameClock(AnimationFrameClock.MAX_PRESENTED_SECONDS)
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = delegate.withFrameNanos { actual ->
            time += (intervals.advance(actual) * 1_000_000_000).toLong()
            onFrame(time)
        }
    }

    private class SurfaceOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
        fun active(value: Boolean) { registry.currentState = if (value) Lifecycle.State.RESUMED else Lifecycle.State.CREATED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }

    private fun updatePresentedFrame(state: Scene, value: Float) {
        val p = value.coerceIn(0f, 1f)
        val fraction = if (state.closing) returning.transform((1 - p / state.startProgress).coerceIn(0f, 1f))
            else travel.transform(p)
        val from = if (state.closing) state.closeFrom else state.source
        val to = if (state.closing) state.source else state.destination
        frame.bounds.set(mix(from.left, to.left, fraction), mix(from.top, to.top, fraction),
            mix(from.right, to.right, fraction), mix(from.bottom, to.bottom, fraction))
        frame.opening = if (state.closing) state.startOpening * (p / state.startProgress).coerceIn(0f, 1f)
            else openingForProgress(p)
        frame.radiusX = if (state.closing) mix(state.closeRadiusX, state.radiusX, fraction) else mix(state.radiusX, state.pageRadius, fraction)
        frame.radiusY = if (state.closing) mix(state.closeRadiusY, state.radiusY, fraction) else mix(state.radiusY, state.pageRadius, fraction)
        frame.angle = smooth(frame.opening) * 168f
        presentationOpening = frame.opening
        presentationRadiusX = frame.radiusX
        presentationRadiusY = frame.radiusY
        presentationRect.set(frame.bounds)
        // This draw-observed primitive invalidates only Canvas, without composing/layouting the scene each frame.
        drawProgress = value
    }

    private fun publishFrame(state: Scene, value: Float, listener: Listener) {
        updatePresentedFrame(state, value)
        listener.onFrame(value, frame.bounds.left, frame.bounds.top, frame.bounds.right, frame.bounds.bottom)
    }

    @Composable private fun Presentation() {
        val state = scene
        val progress = remember { Animatable(state.request?.from ?: state.stationaryProgress) }
        val request = state.request
        LaunchedEffect(request?.id) {
            if (request == null) return@LaunchedEffect
            progress.snapTo(request.from)
            if (!ValueAnimator.areAnimatorsEnabled() || request.duration == 0) {
                progress.snapTo(request.to)
                if (!presentationDisposed && requestId == request.id) {
                    publishFrame(scene, request.to, request.listener)
                    request.listener.onFinished()
                }
            } else {
                progress.animateTo(request.to, tween(request.duration, easing = LinearEasing)) {
                    if (!presentationDisposed && requestId == request.id) publishFrame(scene, value, request.listener)
                }
                if (!presentationDisposed && requestId == request.id) request.listener.onFinished()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            renderer.draw(drawContext.canvas.nativeCanvas, state, frame, drawProgress, size.width, size.height)
        }
    }

    /** One hardware Canvas records bitmap shaders and transforms, avoiding per-layer alpha buffers and changing GPU shadows. */
    private class BookRenderer(private val density: Float) {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val camera = Camera()
        private val hingeMatrix = Matrix()
        private val shaderMatrix = Matrix()
        private val panel = RectF()
        private val screen = RectF()
        private var pageBitmap: Bitmap? = null
        private var coverBitmap: Bitmap? = null
        private val edgeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        fun prepare(page: Bitmap?, cover: Bitmap?) {
            val pageChanged = page !== pageBitmap
            val coverChanged = cover !== coverBitmap
            if (pageChanged) {
                pageBitmap = page
                pagePaint.shader = page?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            }
            if (coverChanged) {
                coverBitmap = cover
                coverPaint.shader = cover?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            }
            if (page == null) return
            if (pageChanged || coverChanged) {
                shaderMatrix.setScale(page.width / (cover?.width ?: page.width).toFloat(), page.height / (cover?.height ?: page.height).toFloat())
                coverPaint.shader?.setLocalMatrix(shaderMatrix)
            }
            if (pageChanged) camera.setLocation(0f, 0f, -page.width * 4f / 72f)
        }

        fun draw(canvas: AndroidCanvas, state: Scene, frame: PresentedFrame, progress: Float, width: Float, height: Float) {
            val page = state.page
            val backdropAlpha = if (page == null) 1f else if (state.closing) 0f else (1 - progress / .24f).coerceIn(0f, 1f)
            if (state.backdrop != null && backdropAlpha > 0f) {
                screen.set(0f, 0f, width, height)
                bitmapPaint.alpha = (255 * backdropAlpha).toInt()
                canvas.drawBitmap(state.backdrop, null, screen, bitmapPaint)
            }
            if (page == null || frame.bounds.isEmpty) return
            val saved = canvas.save()
            canvas.translate(frame.bounds.left, frame.bounds.top)
            canvas.scale(frame.bounds.width() / page.width, frame.bounds.height() / page.height)
            panel.set(0f, 0f, page.width.toFloat(), page.height.toFloat())
            bitmapPaint.alpha = 255
            // The edge follows the current cover radius. A filled page-radius shadow
            // could remain visible in the larger source-cover corners.
            edgeShadow.strokeWidth = 2 * density
            edgeShadow.color = 0xff000000.toInt()
            edgeShadow.alpha = (12 * frame.opening).toInt()
            if (edgeShadow.alpha > 0) canvas.drawRoundRect(panel, frame.radiusX, frame.radiusY, edgeShadow)
            pagePaint.alpha = (255 * (frame.opening * 12f).coerceIn(0f, 1f)).toInt()
            if (pagePaint.alpha > 0) canvas.drawRoundRect(panel, frame.radiusX, frame.radiusY, pagePaint)
            val coverAlpha = (255 * ((1 - frame.opening) / .12f).coerceIn(0f, 1f)).toInt()
            if (coverAlpha > 0) {
                camera.save()
                camera.rotateY(-frame.angle)
                camera.getMatrix(hingeMatrix)
                camera.restore()
                hingeMatrix.preTranslate(0f, -page.height * .5f)
                hingeMatrix.postTranslate(0f, page.height * .5f)
                canvas.concat(hingeMatrix)
                if (frame.angle <= 90f && state.cover != null) {
                    coverPaint.alpha = coverAlpha
                    canvas.drawRoundRect(panel, frame.radiusX, frame.radiusY, coverPaint)
                } else {
                    backPaint.color = state.paperColor
                    backPaint.alpha = coverAlpha
                    canvas.drawRoundRect(panel, frame.radiusX, frame.radiusY, backPaint)
                }
            }
            canvas.restoreToCount(saved)
        }

        fun release() {
            pagePaint.shader = null
            coverPaint.shader = null
            pageBitmap = null
            coverBitmap = null
        }
    }

    companion object {
        @JvmStatic fun openingForProgress(progress: Float): Float = ((progress - .02f) / .70f).coerceIn(0f, 1f)
        private fun smooth(value: Float): Float = value * value * (3 - 2 * value)
        private fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount
    }
}
