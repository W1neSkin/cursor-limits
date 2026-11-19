package app.cursorlimit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.cursorlimit.core.CardText
import java.time.ZoneId

/** Draws each widget size from the last saved response. */
object WidgetRenderer {
    private data class Face(
        val provider: Class<out AppWidgetProvider>,
        val layout: Int,
        val kind: Kind,
    )

    private enum class Kind { CARD, WIDE, FLEX }

    private val faces = listOf(
        Face(LimitWidgetProvider::class.java, R.layout.widget_limit, Kind.CARD),
        Face(WideWidgetProvider::class.java, R.layout.widget_wide, Kind.WIDE),
        Face(TallWidgetProvider::class.java, R.layout.widget_tall, Kind.FLEX),
    )

    private const val DARK_TEXT = 0xFFF4F6FB.toInt()
    private const val DARK_MUTED = 0xFF9AA3B5.toInt()
    private const val DARK_GREEN = 0xFF3DDC97.toInt()
    private const val DARK_GOLD = 0xFFE8C36A.toInt()
    private const val LIGHT_TEXT = 0xFF161A22.toInt()
    private const val LIGHT_MUTED = 0xFF5C667A.toInt()
    private const val LIGHT_GREEN = 0xFF0C8F5A.toInt()
    private const val LIGHT_GOLD = 0xFF8A5A10.toInt()
    private const val DARK_TRACK = 0xFF2C3344.toInt()
    private const val LIGHT_TRACK = 0xFFD5DBE6.toInt()

    fun hasAny(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return faces.any { face ->
            manager.getAppWidgetIds(ComponentName(context, face.provider)).isNotEmpty()
        }
    }

    fun update(context: Context, loading: Boolean = false) {
        val card = Prefs.card(context)
        val text = card?.let { CardText.from(it) }
        val zone = ZoneId.systemDefault()
        val day = card?.let { CardText.resetDay(it.resetAtEpochMs, zone) } ?: "—"
        val clock = card?.let { CardText.resetClock(it.resetAtEpochMs, zone) }.orEmpty()
        val left = card?.let { CardText.percentLeft(it.usedPercent) }
        val error = Prefs.error(context)
        val light = Prefs.isLight(context)
        val manager = AppWidgetManager.getInstance(context)
        val open = openIntent(context)
        val refresh = refreshIntent(context)
        for (face in faces) {
            val ids = manager.getAppWidgetIds(ComponentName(context, face.provider))
            for (id in ids) {
                val layout = if (face.kind == Kind.FLEX) flexLayout(manager, id) else face.layout
                val views = RemoteViews(context.packageName, layout)
                views.setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    if (light) R.drawable.widget_bg_light else R.drawable.widget_bg_dark,
                )
                val scale = if (face.kind == Kind.CARD || face.kind == Kind.WIDE) {
                    spanScale(widgetWidthDp(manager, id))
                } else {
                    1f
                }
                when (face.kind) {
                    Kind.CARD -> bindCard(context, views, text, day, clock, left, error, loading, light, scale)
                    Kind.WIDE -> bindWide(context, views, text, day, left, light, scale)
                    Kind.FLEX -> bindFlex(context, views, layout, text, left, light)
                }
                views.setOnClickPendingIntent(R.id.widget_root, open)
                if (layout != R.layout.widget_tiny) {
                    // The arrow stays. A ProgressBar spins in its place while the request runs.
                    // A normal animation on the arrow does not play inside a home-screen widget.
                    views.setOnClickPendingIntent(R.id.refresh_button, refresh)
                    showRefresh(views, layout, loading, light)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    /** 250dp is four columns. A wider row grows the type so the numbers fill it. */
    private fun spanScale(widthDp: Int): Float {
        if (widthDp <= 0) return 1.28f
        return (widthDp / 250f).coerceIn(1f, 1.4f)
    }

    private fun widgetWidthDp(manager: AppWidgetManager, id: Int): Int {
        val options = manager.getAppWidgetOptions(id)
        return sizeDp(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    }

    /** Uses the widget's shape. Launchers report different widths for one cell. */
    private fun flexLayout(manager: AppWidgetManager, id: Int): Int {
        val options = manager.getAppWidgetOptions(id)
        val width = sizeDp(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val height = sizeDp(options, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width <= 0 || height <= 0) return R.layout.widget_tall
        val wider = width.toFloat() / height
        val taller = height.toFloat() / width
        return when {
            wider >= 1.45f -> R.layout.widget_compact
            taller >= 1.45f -> R.layout.widget_tall
            width >= 140 && height >= 140 -> R.layout.widget_square
            else -> R.layout.widget_tiny
        }
    }

    private fun sizeDp(options: android.os.Bundle, minKey: String, maxKey: String): Int {
        val min = options.getInt(minKey)
        return if (min > 0) min else options.getInt(maxKey)
    }

    private fun textDp(views: RemoteViews, id: Int, sp: Float) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun bindCard(
        context: Context,
        views: RemoteViews,
        text: CardText?,
        day: String,
        clock: String,
        left: Int?,
        error: String?,
        loading: Boolean,
        light: Boolean,
        scale: Float,
    ) {
        val textColor = if (light) LIGHT_TEXT else DARK_TEXT
        val muted = if (light) LIGHT_MUTED else DARK_MUTED
        val gold = if (light) LIGHT_GOLD else DARK_GOLD
        views.setImageViewBitmap(
            R.id.ring,
            ring(context, 112, left, if (left == null) "—" else "$left%", light),
        )
        views.setTextViewText(R.id.reset_day, if (text != null) day else "—")
        views.setTextViewText(R.id.reset_time, if (text != null) clock else "")
        views.setTextViewText(R.id.credits_value, text?.credits ?: "—")
        views.setTextColor(R.id.reset_caption, muted)
        views.setTextColor(R.id.reset_day, textColor)
        views.setTextColor(R.id.reset_time, muted)
        views.setTextColor(R.id.credits_caption, gold)
        views.setTextColor(R.id.credits_value, gold)
        val status = when {
            loading -> "Updating…"
            !error.isNullOrBlank() -> error
            text == null -> "Sign in from the app"
            else -> ""
        }
        if (status.isEmpty()) {
            views.setViewVisibility(R.id.status, View.GONE)
        } else {
            views.setViewVisibility(R.id.status, View.VISIBLE)
            views.setTextViewText(R.id.status, status)
            val problem = !loading && !error.isNullOrBlank()
            views.setTextColor(R.id.status, if (problem) 0xFFC53636.toInt() else muted)
        }
        val pad = (14f * scale).toInt().coerceAtLeast(14)
        views.setViewPadding(R.id.widget_root, pad, pad, pad, pad)
        textDp(views, R.id.reset_caption, 11f * scale)
        textDp(views, R.id.reset_day, 22f * scale)
        textDp(views, R.id.reset_time, 14f * scale)
        textDp(views, R.id.credits_caption, 11f * scale)
        textDp(views, R.id.credits_value, 22f * scale)
        textDp(views, R.id.status, 11f * scale)
    }

    private fun bindWide(
        context: Context,
        views: RemoteViews,
        text: CardText?,
        day: String,
        left: Int?,
        light: Boolean,
        scale: Float,
    ) {
        val muted = if (light) LIGHT_MUTED else DARK_MUTED
        views.setImageViewBitmap(R.id.ring, ring(context, 36, left, "", light))
        views.setTextViewText(R.id.remaining, if (left == null) "—" else "$left%")
        views.setTextViewText(R.id.reset_day, if (text != null) day else "—")
        views.setTextViewText(R.id.credits_value, text?.credits ?: "—")
        views.setTextColor(R.id.remaining, if (light) LIGHT_GREEN else DARK_GREEN)
        views.setTextColor(R.id.left_label, if (light) LIGHT_GREEN else DARK_GREEN)
        views.setTextColor(R.id.reset_day, muted)
        views.setTextColor(R.id.credits_value, if (light) LIGHT_GOLD else DARK_GOLD)
        val padX = (12f * scale).toInt().coerceAtLeast(12)
        val padY = (6f * scale).toInt().coerceAtLeast(6)
        views.setViewPadding(R.id.widget_root, padX, padY, padX, padY)
        textDp(views, R.id.remaining, 20f * scale)
        textDp(views, R.id.left_label, 11f * scale)
        textDp(views, R.id.reset_day, 14f * scale)
        textDp(views, R.id.credits_value, 18f * scale)
    }

    private fun bindFlex(
        context: Context,
        views: RemoteViews,
        layout: Int,
        text: CardText?,
        left: Int?,
        light: Boolean,
    ) {
        val dp = when (layout) {
            R.layout.widget_square -> 96
            R.layout.widget_tall -> 72
            R.layout.widget_compact -> 56
            else -> 64
        }
        val label = when {
            left == null -> "—"
            layout == R.layout.widget_tiny -> "$left"
            else -> "$left"
        }
        views.setImageViewBitmap(R.id.ring, ring(context, dp, left, label, light))
        if (layout == R.layout.widget_tiny) return
        views.setTextViewText(R.id.credits_value, text?.credits ?: "—")
        views.setTextColor(R.id.credits_value, if (light) LIGHT_GOLD else DARK_GOLD)
    }

    /** Draws the arc for one widget. [label] is empty on the wide row, where the number sits beside it. */
    private fun ring(context: Context, dp: Int, left: Int?, label: String, light: Boolean): android.graphics.Bitmap {
        val px = (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(32)
        val fraction = (left ?: 0) / 100f
        return RingBitmap.create(
            size = px,
            leftFraction = fraction,
            label = label,
            arcColor = if (light) LIGHT_GREEN else DARK_GREEN,
            trackColor = if (light) LIGHT_TRACK else DARK_TRACK,
            labelColor = if (light) LIGHT_TEXT else DARK_TEXT,
        )
    }

    private fun showRefresh(views: RemoteViews, layout: Int, loading: Boolean, light: Boolean) {
        val iconSp = when (layout) {
            R.layout.widget_limit -> 22f
            R.layout.widget_compact -> 18f
            else -> 20f
        }
        textDp(views, R.id.refresh_icon, iconSp)
        views.setTextColor(R.id.refresh_icon, if (light) LIGHT_TEXT else DARK_TEXT)
        views.setViewVisibility(R.id.refresh_icon, if (loading) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.refresh_spinner, if (loading && !light) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.refresh_spinner_light, if (loading && light) View.VISIBLE else View.GONE)
    }

    private fun openIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, LimitWidgetProvider::class.java).setAction(LimitWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
