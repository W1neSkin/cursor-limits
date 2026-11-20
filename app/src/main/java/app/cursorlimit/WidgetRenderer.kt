package app.cursorlimit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
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
    // Dark enough to sit on the card, light enough that the ring is still a ring.
    private const val DARK_TRACK = 0xFF4A5368.toInt()
    private const val LIGHT_TRACK = 0xFFC5CDD8.toInt()

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
                val width = widgetSize(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                val height = widgetSize(manager, id, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                when (face.kind) {
                    Kind.CARD -> bindCard(context, views, text, day, clock, left, error, loading, light, height)
                    Kind.WIDE -> bindWide(context, views, text, day, left, light, height)
                    Kind.FLEX -> bindFlex(context, views, layout, text, left, light, width, height)
                }
                views.setOnClickPendingIntent(R.id.widget_root, open)
                // Every size has an arrow. On the small ones it sits beside or under the ring.
                views.setOnClickPendingIntent(R.id.refresh_button, refresh)
                showRefresh(views, layout, loading, light)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun widgetSize(manager: AppWidgetManager, id: Int, minKey: String, maxKey: String): Int {
        return sizeDp(manager.getAppWidgetOptions(id), minKey, maxKey)
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
        height: Int,
    ) {
        val textColor = if (light) LIGHT_TEXT else DARK_TEXT
        val muted = if (light) LIGHT_MUTED else DARK_MUTED
        val gold = if (light) LIGHT_GOLD else DARK_GOLD
        // The ring follows the cell height, so a short row does not push the dollar off the edge.
        val ringDp = if (height > 0) (height - 24).coerceIn(72, 120) else 96
        fitRing(views, ringDp)
        views.setImageViewBitmap(
            R.id.ring,
            ring(context, ringDp, left, if (left == null) "—" else "$left%", "LEFT", light),
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
    }

    private fun bindWide(
        context: Context,
        views: RemoteViews,
        text: CardText?,
        day: String,
        left: Int?,
        light: Boolean,
        height: Int,
    ) {
        val muted = if (light) LIGHT_MUTED else DARK_MUTED
        val ringDp = if (height > 0) (height - 12).coerceIn(26, 40) else 32
        fitRing(views, ringDp)
        views.setImageViewBitmap(R.id.ring, ring(context, ringDp, left, "", "", light))
        views.setTextViewText(R.id.remaining, if (left == null) "—" else "$left%")
        views.setTextViewText(R.id.reset_day, if (text != null) day else "—")
        views.setTextViewText(R.id.credits_value, text?.credits ?: "—")
        views.setTextColor(R.id.remaining, if (light) LIGHT_GREEN else DARK_GREEN)
        views.setTextColor(R.id.left_label, if (light) LIGHT_GREEN else DARK_GREEN)
        views.setTextColor(R.id.reset_day, muted)
        views.setTextColor(R.id.credits_value, if (light) LIGHT_GOLD else DARK_GOLD)
    }

    private fun bindFlex(
        context: Context,
        views: RemoteViews,
        layout: Int,
        text: CardText?,
        left: Int?,
        light: Boolean,
        width: Int,
        height: Int,
    ) {
        val dp = flexRingDp(layout, width, height)
        val label = if (left == null) "—" else "$left"
        fitRing(views, dp)
        views.setImageViewBitmap(R.id.ring, ring(context, dp, left, label, "", light))
        if (layout == R.layout.widget_tiny) return
        views.setTextViewText(R.id.credits_value, text?.credits ?: "—")
        views.setTextColor(R.id.credits_value, if (light) LIGHT_GOLD else DARK_GOLD)
    }

    /** Leaves a line for the dollar on the tall and square sizes. */
    private fun flexRingDp(layout: Int, width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) {
            return when (layout) {
                R.layout.widget_square -> 96
                R.layout.widget_compact -> 56
                else -> 72
            }
        }
        return when (layout) {
            // Leave the dollar and the refresh arrow their own space, and stay square.
            R.layout.widget_compact -> (height - 12).coerceIn(28, 52)
            R.layout.widget_tiny -> (minOf(width, height) - 8).coerceIn(36, 120)
            else -> (minOf(width, height) - 56).coerceIn(44, 120)
        }
    }

    /** Squares the ring view to the cell, on launchers that accept a runtime size. */
    private fun fitRing(views: RemoteViews, dp: Int) {
        if (Build.VERSION.SDK_INT < 31) return
        views.setViewLayoutWidth(R.id.ring, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.ring, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }

    /** Draws the arc. [sublabel] is LEFT on the large card and empty everywhere else. */
    private fun ring(
        context: Context,
        dp: Int,
        left: Int?,
        label: String,
        sublabel: String,
        light: Boolean,
    ): android.graphics.Bitmap {
        val px = (dp * context.resources.displayMetrics.density).toInt().coerceIn(48, 360)
        val fraction = (left ?: 0) / 100f
        return RingBitmap.create(
            size = px,
            leftFraction = fraction,
            label = label,
            sublabel = sublabel,
            arcColor = if (light) LIGHT_GREEN else DARK_GREEN,
            trackColor = if (light) LIGHT_TRACK else DARK_TRACK,
            labelColor = if (light) LIGHT_TEXT else DARK_TEXT,
            typeface = interBold(context),
        )
    }

    private fun interBold(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
    }

    private fun showRefresh(views: RemoteViews, layout: Int, loading: Boolean, light: Boolean) {
        val iconSp = when (layout) {
            R.layout.widget_limit -> 16f
            R.layout.widget_tiny -> 12f
            else -> 14f
        }
        views.setTextViewTextSize(R.id.refresh_icon, TypedValue.COMPLEX_UNIT_SP, iconSp)
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
