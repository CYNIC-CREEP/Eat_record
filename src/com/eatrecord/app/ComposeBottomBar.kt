package com.eatrecord.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A local Compose surface. Activity remains the owner of all navigation and meal operations. */
class ComposeBottomBar(context: Context, private val listener: Listener) : FrameLayout(context) {
    interface Listener {
        fun onTab(tab: Int)
        fun onCapture()
        fun onWrite()
    }

    private data class Appearance(
        val tab: Int = 0,
        val today: Boolean = false,
        val accent: Int = 0xffe8b537.toInt(),
        val surface: Int = 0xfffffdf7.toInt(),
        val ink: Int = 0xff573f2f.toInt(),
        val secondary: Int = 0xff85705b.toInt(),
        val night: Boolean = false,
        val active: Boolean = true,
        val menu: Boolean = false,
        val pulse: Int = 0,
        val mainPulse: Int = 0,
        val pressedTab: Int? = null,
        val mainPressed: Boolean = false,
        val cancelledPulse: Int = -1,
        val cancelledMainPulse: Int = -1,
        val drivenPosition: Float? = null,
        val drivenVelocity: Float = 0f
    )

    private var appearance by mutableStateOf(Appearance())
    private var compose = ComposeView(context)
    private var owner: SurfaceOwner? = null
    private var localRecomposer: Recomposer? = null
    private var localFrameClock: PresentedFrameClock? = null
    private var pagePreparing = false
    private var drawnRenderer: WaterdropRenderer? = null
    private var drawnAppearance: Appearance? = null
    private var drawnPosition = 0f
    private var drawnVelocity = 0f
    private var drawnSwell = 0f
    private var drawnMainSwell = 0f
    private var drawnPresence = 0f
    private var drawnMenu = 0f
    private val hitBar = RectF()
    private val hitDrop = RectF()
    private val hitMain = RectF()
    private val hitCapture = RectF()
    private val hitWrite = RectF()
    private var acceptedTouch = false
    private var touchCancelled = false
    private var touchFeedbackTarget: Int? = null
    private val gestureBarBounds = RectF()
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var navDragging = false
    private var navDragPosition = 0f
    private var touchPointer = -1
    private var touchVelocity: VelocityTracker? = null
    private var mainActionVisible = false
    private var submenuActionsVisible = false

    init {
        id = View.generateViewId()
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= 29) {
            isForceDarkAllowed = false
            compose.isForceDarkAllowed = false
        }
        installOwner()
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        compose.setContent { BottomSurface() }
    }

    fun update(tab: Int, todayAvailable: Boolean, accent: Int, paper: Int, ink: Int, secondary: Int, night: Boolean) {
        val safeTab = if (tab == 0 || tab == 1) tab else 3
        val changed = safeTab != appearance.tab
        val materialChanged = night != appearance.night || paper != appearance.surface || accent != appearance.accent
        if (changed || appearance.mainPressed && !todayAvailable) cancelCapturedTouch()
        appearance = appearance.copy(
            tab = safeTab, today = todayAvailable, accent = accent, surface = paper,
            ink = ink, secondary = secondary, night = night,
            menu = appearance.menu && todayAvailable,
            drivenPosition = if (changed) null else appearance.drivenPosition,
            pulse = appearance.pulse + if (changed) 1 else 0
        )
        if (materialChanged) disableNativeForceDark(compose)
    }

    /** Root drag is direct manipulation; clicks use the interruptible spring below. */
    fun followRootPosition(position: Float) {
        if (!position.isFinite()) return
        val bounded = position.coerceIn(0f, 2f)
        appearance = appearance.copy(drivenPosition = bounded, menu = false)
    }

    fun beginPagePreparation() {
        pagePreparing = true
        localFrameClock?.setPreparing(true)
    }
    fun endPagePreparation() {
        pagePreparing = false
        localFrameClock?.setPreparing(false)
    }
    fun closeMenu() { if (appearance.menu) appearance = appearance.copy(menu = false) }
    fun isMenuExpanded(): Boolean = appearance.today && appearance.menu && appearance.active
    fun consumeBack(): Boolean {
        if (!isMenuExpanded()) return false
        closeMenu()
        return true
    }
    fun endRootGesture(tab: Int, velocityPagesPerSecond: Float) {
        if (appearance.drivenPosition == null) return
        appearance = appearance.copy(tab = if (tab == 0 || tab == 1) tab else 3,
            drivenPosition = null, menu = false,
            drivenVelocity = if (velocityPagesPerSecond.isFinite()) velocityPagesPerSecond else 0f)
    }

    fun setHostActive(active: Boolean) {
        if (!active) cancelCapturedTouch()
        appearance = appearance.copy(active = active, menu = appearance.menu && active)
        localFrameClock?.reset()
        owner?.setActive(active && isAttachedToWindow)
    }

    /** Draw the last presented dock into a theme snapshot without software-drawing ComposeView. */
    fun drawCurrentFrame(canvas: AndroidCanvas) {
        val ready = drawnRenderer != null && drawnAppearance != null
        val renderer = drawnRenderer ?: WaterdropRenderer(resources.displayMetrics.density)
        val colors = drawnAppearance ?: appearance
        renderer.draw(canvas, Geometry(width.toFloat(), height.toFloat(), renderer.density,
            if (ready) drawnPresence else if (colors.today && colors.active) 1f else 0f,
            if (ready) drawnMenu else if (colors.menu) 1f else 0f), colors,
            if (ready) drawnPosition else tabIndex(colors.tab), if (ready) drawnVelocity else 0f,
            if (ready) drawnSwell else 0f, if (ready) drawnMainSwell else 0f)
    }

    private fun disableNativeForceDark(view: View) {
        if (Build.VERSION.SDK_INT >= 29) view.isForceDarkAllowed = false
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) disableNativeForceDark(view.getChildAt(index))
        }
    }

    /** A theme commit owns a fresh native drawing layer as well as fresh material colors. */
    fun refreshThemeSurface() {
        if (!isAttachedToWindow) return
        val surfaceOwner = owner ?: return
        val previous = compose
        cancelCapturedTouch()
        acceptedTouch = false
        releaseComposition()
        compose = ComposeView(context)
        if (Build.VERSION.SDK_INT >= 29) compose.isForceDarkAllowed = false
        compose.setViewTreeLifecycleOwner(surfaceOwner)
        compose.setViewTreeSavedStateRegistryOwner(surfaceOwner)
        compose.setViewTreeViewModelStoreOwner(surfaceOwner)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        bindRecomposer()
        compose.setContent { BottomSurface() }
        compose.createComposition()
        removeView(previous)
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        disableNativeForceDark(compose)
        requestLayout()
        invalidate()
    }

    private fun releaseComposition() {
        compose.disposeComposition()
        localRecomposer?.cancel()
        localRecomposer = null
        localFrameClock = null
        drawnRenderer = null
        drawnAppearance = null
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun bindRecomposer() {
        val lifecycle = owner!!.lifecycle
        val uiContext = AndroidUiDispatcher.CurrentThread
        val clock = PresentedFrameClock(requireNotNull(uiContext[MonotonicFrameClock]))
        clock.setPreparing(pagePreparing)
        localFrameClock = clock
        localRecomposer = createLifecycleAwareWindowRecomposer(coroutineContext = uiContext + clock, lifecycle = lifecycle)
        compose.setParentCompositionContext(localRecomposer)
    }

    fun containsInteractiveRawPoint(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return containsInteractivePoint(rawX - location[0], rawY - location[1])
    }

    /** Stable content boundary; the transparent menu host and transient swell stay outside layout. */
    fun contentTopOnScreen(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val geometry = Geometry(width.toFloat(), height.toFloat(), resources.displayMetrics.density,
            if (appearance.today) 1f else 0f, 0f)
        return location[1] + geometry.bar.top.roundToInt()
    }

    fun containsInteractivePoint(x: Float, y: Float): Boolean {
        if (!appearance.active || visibility != VISIBLE) return false
        if (hitBar.contains(x, y) || hitDrop.contains(x, y)) return true
        if (!appearance.today) return false
        if (mainActionVisible && insideMain(x, y)) return true
        return appearance.menu && submenuActionsVisible &&
                (insideCircle(hitCapture, x, y) || insideCircle(hitWrite, x, y))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            clearTouchFeedback(true)
            recycleTouchVelocity()
            touchCancelled = false
            acceptedTouch = containsInteractivePoint(event.x, event.y)
            if (!acceptedTouch) return false
            beginTouchFeedback(event)
        }
        if (!acceptedTouch) return false
        if (touchCancelled) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                acceptedTouch = false
                touchCancelled = false
                recycleTouchVelocity()
            }
            return true
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN ||
                touchPointer >= 0 && event.findPointerIndex(touchPointer) < 0) {
            cancelCapturedTouch(event)
            return true
        }
        val target = touchFeedbackTarget
        // Submenu buttons retain Foundation's normal touch and keyboard handling.
        if (target == null) {
            val handled = super.dispatchTouchEvent(event)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                acceptedTouch = false
                recycleTouchVelocity()
            }
            return handled
        }
        touchVelocity?.addMovement(event)
        if (action == MotionEvent.ACTION_MOVE) {
            if (target == MAIN_FEEDBACK) {
                if (!insideMain(event.x, event.y)) cancelCapturedTouch(event)
            } else updateTabDrag(event)
        } else if (action == MotionEvent.ACTION_UP) {
            if (target == MAIN_FEEDBACK) {
                if (!insideMain(event.x, event.y)) cancelCapturedTouch(event)
            } else updateTabDrag(event)
            if (touchCancelled) {
                acceptedTouch = false
                recycleTouchVelocity()
                return true
            }
            val selected = if (navDragging) rootTab(navDragPosition.roundToInt().coerceIn(0, 2)) else target
            val velocity = dragVelocity()
            clearTouchFeedback(false)
            appearance = appearance.copy(drivenVelocity = velocity)
            acceptedTouch = false
            recycleTouchVelocity()
            if (target == MAIN_FEEDBACK) activateMain(false) else activateTab(selected, false)
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelCapturedTouch(event)
            acceptedTouch = false
            recycleTouchVelocity()
        }
        return true
    }

    private fun beginTouchFeedback(event: MotionEvent) {
        val x = event.x
        val y = event.y
        if (appearance.menu && submenuActionsVisible &&
                (insideCircle(hitWrite, x, y) || insideCircle(hitCapture, x, y))) return
        touchPointer = event.getPointerId(0)
        touchDownX = x
        touchDownY = y
        lastTouchX = x
        navDragging = false
        gestureBarBounds.set(hitBar)
        val d = resources.displayMetrics.density
        gestureBarBounds.inset(9*d, 12*d)
        touchVelocity = VelocityTracker.obtain().also { it.addMovement(event) }
        if (appearance.today && mainActionVisible && insideMain(x, y)) {
            touchFeedbackTarget = MAIN_FEEDBACK
            appearance = appearance.copy(mainPressed = true, mainPulse = appearance.mainPulse + 1)
        } else {
            val index = ((x - gestureBarBounds.left) / (gestureBarBounds.width() / 3)).toInt().coerceIn(0, 2)
            val tab = rootTab(index)
            touchFeedbackTarget = tab
            appearance = appearance.copy(pressedTab = tab, menu = false, pulse = appearance.pulse + 1)
        }
    }

    private fun updateTabDrag(event: MotionEvent) {
        val d = resources.displayMetrics.density
        val dx = event.x - touchDownX
        val dy = event.y - touchDownY
        val slop = max(8*d, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
        if (event.y < gestureBarBounds.top - 24*d || event.y > gestureBarBounds.bottom + 24*d ||
                !navDragging && abs(dy) > slop && abs(dy) > abs(dx) * 1.3f) {
            cancelCapturedTouch(event)
            return
        }
        if (!navDragging && abs(dx) > slop && abs(dx) > abs(dy)) {
            navDragging = true
            navDragPosition = drawnPosition.coerceIn(0f, 2f)
        }
        if (navDragging) {
            val slot = max(1f, gestureBarBounds.width() / 3)
            navDragPosition = (navDragPosition + (event.x - lastTouchX) / slot).coerceIn(0f, 2f)
            appearance = appearance.copy(drivenPosition = navDragPosition, drivenVelocity = dragVelocity(),
                pressedTab = rootTab(navDragPosition.roundToInt().coerceIn(0, 2)))
        }
        lastTouchX = event.x
    }

    private fun dragVelocity(): Float {
        if (!navDragging || touchPointer < 0) return 0f
        val tracker = touchVelocity ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return (tracker.getXVelocity(touchPointer) / max(1f, gestureBarBounds.width() / 3)).coerceIn(-12f, 12f)
    }

    private fun insideMain(x: Float, y: Float): Boolean {
        val radius = hitMain.width() * .5f * (1 + .24f * drawnMainSwell.coerceIn(0f, 1f))
        val dx = x - hitMain.centerX()
        val dy = y - hitMain.centerY()
        return radius > 0 && dx*dx + dy*dy <= radius*radius
    }

    private fun clearTouchFeedback(cancelled: Boolean) {
        val cancellingTab = cancelled && (appearance.pressedTab != null ||
            touchFeedbackTarget != null && touchFeedbackTarget != MAIN_FEEDBACK)
        val cancellingMain = cancelled && (appearance.mainPressed || touchFeedbackTarget == MAIN_FEEDBACK)
        appearance = appearance.copy(pressedTab = null, mainPressed = false,
            drivenPosition = if (navDragging) null else appearance.drivenPosition,
            cancelledPulse = if (cancellingTab) appearance.pulse else appearance.cancelledPulse,
            cancelledMainPulse = if (cancellingMain) appearance.mainPulse else appearance.cancelledMainPulse)
        navDragging = false
        touchFeedbackTarget = null
    }

    private fun cancelCapturedTouch(event: MotionEvent? = null) {
        if (acceptedTouch && !touchCancelled) {
            if (touchFeedbackTarget == null) {
                val cancel = if (event == null) MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, 0f, 0f, 0) else MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
            }
            touchCancelled = true
        }
        clearTouchFeedback(true)
        recycleTouchVelocity()
    }

    private fun recycleTouchVelocity() {
        touchVelocity?.recycle()
        touchVelocity = null
        touchPointer = -1
    }

    private fun activateTab(tab: Int, feedback: Boolean) {
        if (!appearance.active) return
        appearance = appearance.copy(tab = tab, menu = false, drivenPosition = null,
            pulse = appearance.pulse + if (feedback) 1 else 0)
        listener.onTab(tab)
    }

    private fun activateMain(feedback: Boolean) {
        if (!appearance.today || !appearance.active || !mainActionVisible) return
        appearance = appearance.copy(menu = !appearance.menu,
            mainPulse = appearance.mainPulse + if (feedback) 1 else 0)
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onAttachedToWindow() {
        if (owner == null) {
            installOwner()
            compose.setContent { BottomSurface() }
        }
        compose.setViewTreeLifecycleOwner(owner)
        compose.setViewTreeSavedStateRegistryOwner(owner)
        compose.setViewTreeViewModelStoreOwner(owner)
        super.onAttachedToWindow()
        owner?.setActive(appearance.active)
        bindRecomposer()
        disableNativeForceDark(compose)
    }

    override fun onDetachedFromWindow() {
        cancelCapturedTouch()
        acceptedTouch = false
        appearance = appearance.copy(menu = false, drivenPosition = null)
        releaseComposition()
        owner?.destroy()
        owner = null
        super.onDetachedFromWindow()
    }

    private class PresentedFrameClock(private val delegate: MonotonicFrameClock) : MonotonicFrameClock {
        private val intervals = AnimationFrameClock(AnimationFrameClock.MAX_PRESENTED_SECONDS)
        private var presentedNanos = 0L
        private var preparing = false

        fun setPreparing(value: Boolean) {
            preparing = value
            intervals.reset()
        }

        fun reset() { intervals.reset() }

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = delegate.withFrameNanos { actualNanos ->
            if (!preparing) presentedNanos += (intervals.advance(actualNanos) * 1_000_000_000).toLong()
            onFrame(presentedNanos)
        }
    }

    private fun installOwner() {
        val next = SurfaceOwner()
        owner = next
        setViewTreeLifecycleOwner(next)
        setViewTreeViewModelStoreOwner(next)
        setViewTreeSavedStateRegistryOwner(next)
    }

    private class SurfaceOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun setActive(active: Boolean) {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
            }
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private data class PositionRequest(
        val target: Float, val driven: Float?, val velocity: Float, val enabled: Boolean
    )

    private data class FeedbackRequest(
        val held: Boolean, val pulse: Int, val cancelled: Boolean, val enabled: Boolean, val perceptible: Boolean
    )

    /** Short taps turn at a visible peak instead of waiting for the spring's invisible settling tail. */
    private suspend fun animateFeedback(value: Animatable<Float, AnimationVector1D>, main: Boolean) = withContext(
        // Keep visible fall frames after a stalled frame, independently of page-preparation pauses.
        PresentedFrameClock(requireNotNull(AndroidUiDispatcher.CurrentThread[MonotonicFrameClock]))
    ) {
        var previousPulse = if (main) appearance.mainPulse else appearance.pulse
        var feedbackJob: Job? = null
        var rising = false
        snapshotFlow {
            val current = appearance
            val pulse = if (main) current.mainPulse else current.pulse
            val held = if (main) current.mainPressed && current.today else current.pressedTab != null
            val cancelled = if (main) current.cancelledMainPulse == pulse else current.cancelledPulse == pulse
            FeedbackRequest(held, pulse, cancelled, current.active && ValueAnimator.areAnimatorsEnabled(),
                value.value >= .86f)
        }.collect { request ->
            val freshPress = request.pulse != previousPulse && !request.cancelled
            previousPulse = request.pulse
            val target = when {
                !request.enabled -> 0f
                freshPress -> 1f
                rising && (request.cancelled || !request.held && request.perceptible) -> 0f
                else -> return@collect
            }
            val velocity = value.velocity
            feedbackJob?.cancel()
            rising = target == 1f
            val stiffness = if (rising) 420f else 246.74011f
            feedbackJob = launch {
                if (!request.enabled) value.snapTo(0f)
                else value.animateTo(target, spring(dampingRatio = 1f, stiffness = stiffness), initialVelocity = velocity)
            }
        }
    }

    private data class SurfaceState(
        val tab: Int, val today: Boolean, val active: Boolean, val menu: Boolean,
        val accent: Int, val surface: Int, val night: Boolean
    )

    @Composable
    private fun BottomSurface() {
        // Pointer positions and press progress only invalidate drawing, not this semantic/layout tree.
        val state by remember { derivedStateOf(structuralEqualityPolicy()) {
            val current = appearance
            SurfaceState(current.tab, current.today, current.active, current.menu,
                current.accent, current.surface, current.night)
        } }
        val animations = state.active && ValueAnimator.areAnimatorsEnabled()
        val position = remember { Animatable(tabIndex(state.tab)) }
        val swell = remember { Animatable(0f) }
        val mainSwell = remember { Animatable(0f) }
        val presence = animateFloatAsState(
            if (state.today && state.active) 1f else 0f,
            if (animations) spring(dampingRatio = .9f, stiffness = 360f) else snap(), label = "today action space"
        )
        val menu = animateFloatAsState(
            if (state.menu && state.today && state.active) 1f else 0f,
            if (animations) spring(dampingRatio = .86f, stiffness = 420f) else snap(), label = "meal action menu"
        )
        val mainAvailable by remember { derivedStateOf(structuralEqualityPolicy()) { presence.value > .2f } }
        val submenuAvailable by remember { derivedStateOf(structuralEqualityPolicy()) { menu.value > .82f && presence.value > .85f } }
        val renderer = remember(state.night, state.surface, state.accent) {
            WaterdropRenderer(resources.displayMetrics.density)
        }
        val drawingGeometry = remember(renderer) { Geometry(0f, 0f, renderer.density, 0f, 0f) }
        val targetGeometry = remember(renderer) { Geometry(0f, 0f, renderer.density, 0f, 0f) }

        // Each new spring captures velocity before cancelling its predecessor through Animatable.
        LaunchedEffect(position) {
            var driven = false
            snapshotFlow {
                val current = appearance
                PositionRequest(tabIndex(current.pressedTab ?: current.tab), current.drivenPosition, current.drivenVelocity,
                    current.active && ValueAnimator.areAnimatorsEnabled())
            }.collect { request ->
                val velocity = when {
                    driven -> request.velocity
                    else -> position.velocity
                }
                launch {
                    if (!request.enabled || request.driven != null) {
                        position.snapTo(request.driven ?: request.target)
                    } else {
                        position.animateTo(request.target,
                            spring(dampingRatio = .85f, stiffness = 246.74011f), initialVelocity = velocity)
                    }
                }
                driven = request.driven != null
            }
        }
        LaunchedEffect(swell) { animateFeedback(swell, false) }
        LaunchedEffect(mainSwell) { animateFeedback(mainSwell, true) }

        Layout(content = {
            Canvas(Modifier.fillMaxSize()) {
                val geometry = drawingGeometry
                geometry.update(size.width, size.height, presence.value, menu.value)
                drawnRenderer = renderer
                drawnAppearance = appearance
                drawnPosition = position.value
                drawnVelocity = position.velocity
                drawnSwell = swell.value
                drawnMainSwell = mainSwell.value
                drawnPresence = presence.value
                drawnMenu = menu.value
                renderer.draw(drawContext.canvas.nativeCanvas, geometry, appearance,
                    position.value, position.velocity, swell.value, mainSwell.value)
                updateHitBounds(geometry, position.value, position.velocity, swell.value)
            }
            for ((tab, label) in listOf(0 to "今日", 1 to "足迹", 3 to "我")) {
                HitTarget(label, state.active, tab == state.tab, "", Role.Tab) { activateTab(tab, true) }
            }
            HitTarget("添加餐食", state.today && state.active && mainAvailable, false,
                if (state.menu) "已展开" else "已收起", Role.Button) { activateMain(true) }
            HitTarget("拍照或相册添加食物", state.menu && state.today && state.active && submenuAvailable, false, "", Role.Button) {
                if (appearance.today && appearance.menu && appearance.active && submenuActionsVisible) {
                    closeMenu()
                    listener.onCapture()
                }
            }
            HitTarget("手写记录餐食", state.menu && state.today && state.active && submenuAvailable, false, "", Role.Button) {
                if (appearance.today && appearance.menu && appearance.active && submenuActionsVisible) {
                    closeMenu()
                    listener.onWrite()
                }
            }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val geometry = targetGeometry
            geometry.update(width.toFloat(), height.toFloat(), presence.value, 0f)
            val rectangles = geometry.hitTargets()
            val background = measurables[0].measure(Constraints.fixed(width, height))
            val targets = measurables.drop(1).mapIndexed { index, measurable ->
                val rect = rectangles[index]
                measurable.measure(Constraints.fixed(max(1, rect.width().roundToInt()), max(1, rect.height().roundToInt())))
            }
            layout(width, height) {
                // Menu motion changes positions only; its fixed-size targets need no remeasurement.
                geometry.update(width.toFloat(), height.toFloat(), presence.value, menu.value)
                geometry.hitTargets()
                background.place(0, 0)
                targets.forEachIndexed { index, target ->
                    val rect = rectangles[index]
                    target.place(rect.left.roundToInt(), rect.top.roundToInt())
                }
            }
        }
    }

    @Composable
    private fun HitTarget(label: String, enabled: Boolean, isSelected: Boolean, status: String,
                          role: Role, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val modifier = if (enabled) {
            Modifier.fillMaxSize().semantics {
                contentDescription = label
                if (role == Role.Tab) selected = isSelected
                if (status.isNotEmpty()) stateDescription = status
            }.clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick)
        } else Modifier.fillMaxSize()
        Box(modifier)
    }

    private fun updateHitBounds(g: Geometry, position: Float, velocity: Float, swell: Float) {
        hitBar.set(g.bar)
        hitBar.inset(-g.d * 9, -g.d * 12)
        hitDrop.set(g.drop(position, velocity, swell))
        hitDrop.inset(-g.d * 8, -g.d * 6)
        hitMain.set(g.main)
        hitCapture.set(g.capture)
        hitWrite.set(g.write)
        mainActionVisible = g.presence > .2f
        submenuActionsVisible = g.menu > .82f && g.presence > .85f
    }

    private class Geometry(width: Float, height: Float, val d: Float, visible: Float, expanded: Float) {
        var presence = 0f
            private set
        var menu = 0f
            private set
        var centerY = 0f
            private set
        var slot = 0f
            private set
        val bar = RectF()
        val main = RectF()
        val capture = RectF()
        val write = RectF()
        private val dropBounds = RectF()
        private val targets = listOf(RectF(), RectF(), RectF(), main, capture, write)

        init { update(width, height, visible, expanded) }

        fun update(width: Float, height: Float, visible: Float, expanded: Float) {
            presence = visible.coerceIn(0f, 1f)
            menu = expanded.coerceIn(0f, 1f)
            val available = min(width - 28*d, 352*d).coerceAtLeast(160*d)
            val barWidth = min(available, 278*d) * (1-presence) + min(available-66*d, 244*d) * presence
            val groupWidth = barWidth + 66*d*presence
            val left = (width-groupWidth)/2
            centerY = height-44*d
            bar.set(left, centerY-32*d, left+barWidth, centerY+32*d)
            slot = barWidth/3
            setCircle(main, left+barWidth+38*d, centerY, 28*d)
            setCircle(capture, main.centerX(), centerY-66*d*menu, 24*d)
            setCircle(write, main.centerX(), centerY-128*d*menu, 24*d)
        }

        fun hitTargets(): List<RectF> {
            targets[0].set(bar.left-24*d, bar.top-24*d, bar.left+slot, bar.bottom+24*d)
            targets[1].set(bar.left+slot, bar.top-24*d, bar.left+slot*2, bar.bottom+24*d)
            targets[2].set(bar.left+slot*2, bar.top-24*d, bar.right+24*d, bar.bottom+24*d)
            return targets
        }

        fun drop(position: Float, velocity: Float, pulse: Float): RectF {
            val pressure = pulse.coerceIn(0f, 1f)
            val stretch = (abs(velocity)/12).coerceIn(0f, 1f)
            val halfWidth = slot*.44f+d*(11*pressure+10*stretch)
            val halfHeight = d*(29+12*pressure-2*stretch)
            val center = bar.left+slot*(.5f+position.coerceIn(-.08f, 2.08f))
            dropBounds.set(center-halfWidth, centerY-halfHeight, center+halfWidth, centerY+halfHeight)
            return dropBounds
        }

        private fun setCircle(target: RectF, x: Float, y: Float, radius: Float) {
            target.set(x-radius, y-radius, x+radius, y+radius)
        }
    }

    private class WaterdropRenderer(val density: Float) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val penPath = createPenPath()
        private val compactPenPath = Path().apply {
            moveTo(-6*density, 6*density)
            lineTo(4.7f*density, -9.1f*density)
            lineTo(8.7f*density, -6.1f*density)
            lineTo(-2*density, 9*density)
            lineTo(-7*density, 11*density)
            close()
        }
        private val sparklePath = Path().apply {
            moveTo(0f, -1f)
            quadTo(.18f, -.18f, 1f, 0f)
            quadTo(.18f, .18f, 0f, 1f)
            quadTo(-.18f, .18f, -1f, 0f)
            quadTo(-.18f, -.18f, 0f, -1f)
            close()
        }
        private val tabPaths = Array(3) { createTabPath(it) }
        private val matrix = Matrix()
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadowBounds = RectF()
        private var barShadow: Bitmap? = null
        private var dropShadow: Bitmap? = null
        private var actionShadow: Bitmap? = null
        private var palette: Appearance? = null
        private var barGradient: Shader? = null
        private var dropGradient: Shader? = null
        private var actionGradient: Shader? = null

        fun draw(canvas: AndroidCanvas, g: Geometry, colors: Appearance, position: Float, velocity: Float,
                 pulse: Float, mainPulse: Float) {
            updatePalette(colors)
            val pressure = pulse.coerceIn(0f, 1f)
            val saved = canvas.save()
            canvas.scale(1 + .035f*pressure, 1 + .065f*pressure, g.bar.centerX(), g.bar.centerY())
            path.reset(); path.addRoundRect(g.bar, 34*density, 34*density, Path.Direction.CW)
            shadow.color = alpha(AndroidColor.BLACK, if (colors.night) 74 else 28)
            drawShadow(canvas, barShadow, g.bar)
            gradient(barGradient, g.bar); paint.shader = barGradient; paint.style = Paint.Style.FILL; paint.alpha = 255
            canvas.drawPath(path, paint); paint.shader = null
            paint.color = alpha(colors.ink, if (colors.night) 28 else 24); paint.style = Paint.Style.STROKE; paint.strokeWidth = density*.7f
            canvas.drawPath(path, paint)
            val drop = g.drop(position, velocity, pulse)
            waterdrop(drop, velocity)
            shadow.color = alpha(colors.accent, if (colors.night) 38 else 32)
            drawShadow(canvas, dropShadow, drop)
            gradient(dropGradient, drop); paint.shader = dropGradient; paint.style = Paint.Style.FILL; paint.alpha = 255
            canvas.drawPath(path, paint); paint.shader = null
            paint.color = alpha(AndroidColor.WHITE, if (colors.night) 25 else 112); paint.style = Paint.Style.STROKE
            paint.strokeWidth = density*.8f; canvas.drawPath(path, paint)
            paint.color = alpha(AndroidColor.WHITE, if (colors.night) 31 else 127); paint.strokeWidth = density*1.1f
            canvas.drawArc(drop.left+drop.width()*.14f,drop.top+3*density,drop.right-drop.width()*.13f,drop.bottom-9*density,
                205f,92f,false,paint)
            for (index in 0..2) {
                val emphasis = (1-abs(position-index)).coerceIn(0f,1f)
                val color = blend(colors.secondary, colors.ink, emphasis)
                val x = g.bar.left + g.slot*(index+.5f)
                val feedbackEmphasis = if (colors.pressedTab == null) emphasis else
                    if (tabIndex(colors.pressedTab).roundToInt() == index) 1f else 0f
                val scale = 1 + .045f*pressure*feedbackEmphasis
                val icon = canvas.save(); canvas.scale(scale,scale,x,g.centerY)
                glyph(canvas,index,x,g.centerY,color)
                canvas.restoreToCount(icon)
            }
            canvas.restoreToCount(saved)
            if (g.presence > .002f) {
                val menuAlpha = (g.menu*g.presence).coerceIn(0f,1f)
                if (menuAlpha > .002f) {
                    action(canvas,g.capture,menuAlpha,.78f+.22f*g.menu,colors,1)
                    action(canvas,g.write,menuAlpha,.78f+.22f*g.menu,colors,2)
                }
                val mainScale = (.84f + .16f*g.presence) * (1 + .24f*mainPulse.coerceIn(0f, 1f))
                action(canvas,g.main,g.presence,mainScale,colors,0)
            }
        }

        private fun updatePalette(colors: Appearance) {
            val old=palette
            if(old!=null&&old.accent==colors.accent&&old.surface==colors.surface&&old.night==colors.night)return
            palette=colors
            val white=AndroidColor.WHITE
            barGradient=LinearGradient(0f,0f,0f,1f,intArrayOf(blend(colors.surface,white,if(colors.night).025f else .20f),colors.surface),null,Shader.TileMode.CLAMP)
            dropGradient=LinearGradient(0f,0f,1f,1f,intArrayOf(
                alpha(blend(colors.surface,colors.accent,if(colors.night).40f else .29f),225),
                alpha(blend(colors.surface,colors.accent,if(colors.night).25f else .17f),184),
                alpha(blend(colors.surface,colors.accent,if(colors.night).33f else .23f),207)),floatArrayOf(0f,.56f,1f),Shader.TileMode.CLAMP)
            actionGradient=LinearGradient(0f,0f,1f,1f,intArrayOf(
                blend(colors.surface,colors.accent,if(colors.night).58f else .78f),
                blend(colors.surface,colors.accent,if(colors.night).34f else .56f)),null,Shader.TileMode.CLAMP)
            barShadow = createShadow(278f, 64f, 32f, alpha(AndroidColor.BLACK, if(colors.night)74 else 28))
            dropShadow = createShadow(94f, 58f, 29f, alpha(colors.accent, if(colors.night)38 else 32))
            actionShadow = createShadow(56f, 56f, 28f, alpha(AndroidColor.BLACK, if(colors.night)72 else 35))
        }

        /** Shadow softness is sampled once in dp space, then scaled with the animated material. */
        private fun createShadow(width: Float, height: Float, radius: Float, color: Int): Bitmap? {
            var image: Bitmap? = null
            return try {
                image = Bitmap.createBitmap((width+24).roundToInt(), (height+24).roundToInt(), Bitmap.Config.ARGB_8888)
                val brush = Paint(Paint.ANTI_ALIAS_FLAG)
                brush.color = color
                brush.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                AndroidCanvas(image).drawRoundRect(12f, 12f, width+12, height+12, radius, radius, brush)
                image
            } catch (unavailable: OutOfMemoryError) {
                image?.recycle()
                null
            }
        }

        private fun drawShadow(canvas: AndroidCanvas, image: Bitmap?, bounds: RectF) {
            if(image==null){canvas.drawPath(path, shadow);return}
            shadowBounds.set(bounds)
            shadowBounds.inset(-12*density, -12*density)
            canvas.drawBitmap(image, null, shadowBounds, shadowImagePaint)
        }

        private fun gradient(shader: Shader?, rect: RectF) {
            matrix.reset(); matrix.setScale(rect.width(),rect.height()); matrix.postTranslate(rect.left,rect.top)
            shader?.setLocalMatrix(matrix)
        }

        private fun waterdrop(rect: RectF, velocity: Float) {
            val x=rect.left;val y=rect.top;val w=rect.width();val h=rect.height()
            val lean=(velocity/15).coerceIn(-.12f,.12f)
            path.reset();path.moveTo(x+w*.48f,y)
            path.cubicTo(x+w*(.82f+lean),y,x+w,y+h*.19f,x+w,y+h*.49f)
            path.cubicTo(x+w,y+h*.83f,x+w*.77f,y+h,x+w*.47f,y+h)
            path.cubicTo(x+w*.14f,y+h,x,y+h*.79f,x,y+h*.46f)
            path.cubicTo(x,y+h*.16f,x+w*.17f,y,x+w*.48f,y);path.close()
        }

        private fun action(canvas: AndroidCanvas, rect: RectF, opacity: Float, scale: Float, colors: Appearance, kind: Int) {
            val layerPadding = 12*density*max(1f,scale) + rect.width()*.5f*max(0f,scale-1)
            val layer=if(opacity>=.999f) canvas.save() else canvas.saveLayerAlpha(
                rect.left-layerPadding,rect.top-layerPadding,rect.right+layerPadding,rect.bottom+layerPadding,(255*opacity).roundToInt())
            canvas.scale(scale,scale,rect.centerX(),rect.centerY())
            path.reset();path.addOval(rect,Path.Direction.CW)
            shadow.color=alpha(AndroidColor.BLACK,if(colors.night)72 else 35);drawShadow(canvas,actionShadow,rect)
            paint.style=Paint.Style.FILL
            if(kind==0){gradient(actionGradient,rect);paint.shader=actionGradient;paint.alpha=255}else paint.color=colors.surface
            canvas.drawPath(path,paint);paint.shader=null
            paint.style=Paint.Style.STROKE;paint.strokeWidth=density*.8f;paint.color=alpha(colors.ink,if(colors.night)35 else 25);canvas.drawPath(path,paint)
            val ink=if(kind==0)colors.ink else blend(colors.ink,colors.accent,.28f)
            val icon=canvas.save();canvas.translate(rect.centerX(),rect.centerY())
            paint.strokeWidth=2*density;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.color=ink
            when(kind){
                0 -> addMealGlyph(canvas)
                1 -> sparkle(canvas,0f,0f,11*density)
                else -> penGlyph(canvas)
            }
            canvas.restoreToCount(icon);canvas.restoreToCount(layer)
        }

        private fun addMealGlyph(canvas: AndroidCanvas) {
            val saved = canvas.save()
            canvas.translate(-7*density, -1*density)
            paint.style = Paint.Style.STROKE
            canvas.drawPath(compactPenPath, paint)
            canvas.restoreToCount(saved)
            sparkle(canvas, 9*density, 0f, 6.5f*density)
        }

        private fun sparkle(canvas: AndroidCanvas,x: Float,y: Float,r: Float) {
            val saved = canvas.save()
            val stroke = paint.strokeWidth
            canvas.translate(x, y)
            canvas.scale(r, r)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke / r
            canvas.drawPath(sparklePath, paint)
            paint.strokeWidth = stroke
            canvas.restoreToCount(saved)
        }

        private fun penGlyph(canvas: AndroidCanvas) {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(penPath, paint)
        }

        private fun createPenPath(): Path = Path().apply {
            val d = density
            moveTo(-9*d, 5*d)
            lineTo(4*d, -8*d)
            quadTo(6*d, -10*d, 8*d, -8*d)
            lineTo(10*d, -6*d)
            quadTo(11*d, -4*d, 9*d, -2*d)
            lineTo(-4*d, 11*d)
            lineTo(-11*d, 12*d)
            close()
            moveTo(2*d, -6*d)
            lineTo(7*d, -1*d)
            moveTo(-10*d, 15*d)
            lineTo(10*d, 15*d)
        }

        private fun createTabPath(index: Int): Path = Path().apply {
            val d = density
            when (index) {
                0 -> {
                    moveTo(-9*d, 0f)
                    cubicTo(-8*d, 12*d, 8*d, 12*d, 9*d, 0f)
                    moveTo(-11*d, 0f)
                    lineTo(11*d, 0f)
                    moveTo(-4*d, 12*d)
                    lineTo(4*d, 12*d)
                    moveTo(-4*d, -10*d)
                    lineTo(-4*d, -5*d)
                    moveTo(3*d, -12*d)
                    lineTo(3*d, -5*d)
                }
                1 -> {
                    addOval(-9*d, -9*d, -1*d, 4*d, Path.Direction.CW)
                    addCircle(-5*d, -13*d, 2*d, Path.Direction.CW)
                    addOval(3*d, -1*d, 10*d, 11*d, Path.Direction.CW)
                    addCircle(6*d, -5*d, 1.8f*d, Path.Direction.CW)
                    transform(Matrix().apply { setRotate(-19f) })
                }
                else -> {
                    addCircle(0f, -6*d, 4.2f*d, Path.Direction.CW)
                    moveTo(-10*d, 12*d)
                    cubicTo(-10*d, 1*d, 10*d, 1*d, 10*d, 12*d)
                }
            }
        }

        private fun glyph(canvas: AndroidCanvas,index: Int,x: Float,y: Float,color: Int) {
            val d=density
            val centerCorrection=when(index){1->2*d;2->-.9f*d;else->0f}
            val saved=canvas.save();canvas.translate(x,y+centerCorrection)
            paint.color=color;paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.85f*d
            paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            canvas.drawPath(tabPaths[index], paint)
            canvas.restoreToCount(saved)
        }
    }

    companion object {
        private const val MAIN_FEEDBACK = -1
        private fun rootTab(index: Int): Int = if (index == 2) 3 else index
        private fun tabIndex(tab: Int): Float = if(tab==0)0f else if(tab==1)1f else 2f
        private fun insideCircle(rect: RectF,x: Float,y: Float): Boolean {
            val radius=rect.width()/2
            val dx=x-rect.centerX();val dy=y-rect.centerY()
            return radius>0&&dx*dx+dy*dy<=radius*radius
        }
        private fun alpha(color: Int,value: Int): Int = (color and 0x00ffffff) or (value.coerceIn(0,255) shl 24)
        private fun blend(first: Int,second: Int,fraction: Float): Int {
            val f=fraction.coerceIn(0f,1f)
            return AndroidColor.rgb((AndroidColor.red(first)*(1-f)+AndroidColor.red(second)*f).roundToInt(),
                (AndroidColor.green(first)*(1-f)+AndroidColor.green(second)*f).roundToInt(),
                (AndroidColor.blue(first)*(1-f)+AndroidColor.blue(second)*f).roundToInt())
        }
    }
}
