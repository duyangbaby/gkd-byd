package li.gkd.app.feature.vehicle

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.delay
import li.gkd.app.app
import li.gkd.app.domain.vehicle.VehicleCommand
import li.gkd.app.service.A11yService

object VehicleWidgets {
    private const val HOST_ID = 26921
    private var listeners = 0
    val manager: AppWidgetManager by lazy { AppWidgetManager.getInstance(app) }
    val host by lazy {
        object : AppWidgetHost(app, HOST_ID) {
            override fun onCreateView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo) =
                SelectionHostView(context)
        }
    }

    fun providers() = manager.installedProviders.filter {
        it.provider.packageName in setOf("com.byd.aeri.caranywhere", "com.byd.sea")
    }

    fun version(provider: ComponentName) = PackageInfoCompat.getLongVersionCode(
        app.packageManager.getPackageInfo(provider.packageName, 0),
    )

    fun acquire() {
        if (listeners++ == 0) host.startListening()
    }

    fun release() {
        if (listeners > 0 && --listeners == 0) host.stopListening()
    }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): SelectionHostView {
        val view = host.createView(context, id, info) as SelectionHostView
        val density = context.resources.displayMetrics.density
        view.updateAppWidgetSize(null, 340, (info.minHeight / density).toInt().coerceAtLeast(160),
            340, (info.minHeight / density).toInt().coerceAtLeast(160))
        return view
    }

    suspend fun click(config: VehicleConfig, command: VehicleCommand): Boolean {
        val service = A11yService.instance ?: run {
            VehicleStore.report("GKD 无障碍服务未连接，请重新启用后确认车辆状态")
            return false
        }
        val secondary = command == VehicleCommand.CLOSE_WINDOWS
        val widgetId = if (secondary) config.secondaryWidgetId else config.widgetId
        val provider = if (secondary) config.secondaryProvider else config.provider
        val providerVersion = if (secondary) config.secondaryProviderVersion else config.providerVersion
        val info = manager.getAppWidgetInfo(widgetId)
        if (info == null || info.provider.flattenToString() != provider ||
            version(info.provider) != providerVersion
        ) {
            VehicleStore.report("小组件绑定或比亚迪版本已改变，请重新配置按钮")
            return false
        }
        val view = createView(service, widgetId, info)
        val wm = service.getSystemService(WindowManager::class.java)
        val density = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (340 * density).toInt(), info.minHeight.coerceAtLeast((160 * density).toInt()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        var attached = false
        acquire()
        try {
            wm.addView(view, params)
            attached = true
            val id = when (command) {
                VehicleCommand.UNLOCK -> config.unlockViewId
                VehicleCommand.LOCK -> config.lockViewId
                VehicleCommand.POWER_ON -> config.powerOnViewId
                VehicleCommand.POWER_OFF -> config.powerOffViewId
                VehicleCommand.CLOSE_WINDOWS -> config.windowViewId
            }
            if (id < 0) { VehicleStore.report("请先标记${command.label}按钮"); return false }
            repeat(25) {
                delay(100)
                val matches = view.clickableViews().filter { it.id == id && it.isShown && it.isEnabled }
                if (matches.size == 1 && view.width > 0) {
                    delay(config.clickDelayMs.toLong())
                    val current = view.clickableViews().filter { it.id == id && it.isShown && it.isEnabled }
                    if (current.size == 1) {
                        val clicked = current.single().performClick()
                        if (clicked) delay(1200)
                        return clicked
                    }
                }
            }
            VehicleStore.report("小组件按钮未就绪，没有发出点击")
            return false
        } finally {
            try {
                if (attached && view.isAttachedToWindow) wm.removeView(view)
            } finally {
                release()
            }
        }
    }
}

/** Preview consumes touches: selecting a button cannot send a vehicle command. */
class SelectionHostView(context: Context) : AppWidgetHostView(context) {
    var onSelect: ((Int) -> Unit)? = null

    fun clickableViews(): List<View> {
        val result = mutableListOf<View>()
        fun visit(view: View) {
            if (view.isClickable && view.id != View.NO_ID) result.add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(this)
        return result
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val rect = Rect()
            val target = clickableViews().lastOrNull {
                it.isShown && it.getGlobalVisibleRect(rect) && rect.contains(event.rawX.toInt(), event.rawY.toInt())
            }
            if (target != null && clickableViews().count { it.id == target.id } == 1) onSelect?.invoke(target.id)
        }
        return true
    }
}
