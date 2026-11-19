package app.cursorlimit

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import app.cursorlimit.core.AlertSteps

/**
 * Minus on the left, plus on the right, number in a fixed slot between them.
 * The slot does not change size, so the digits stay put when the buttons show.
 * A tap steps once. Holding repeats, then jumps by 5 and by 10.
 */
class AlertStepper(
    private val minus: TextView,
    private val value: TextView,
    private val plus: TextView,
    private val step: Int,
    private val floor: () -> Int,
    private val ceiling: () -> Int,
    private val read: () -> Int,
    private val write: (Int) -> Unit,
    private val format: (Int) -> String,
    private val onOpen: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var editing = false
    private var direction = 0
    private var ticks = 0
    private var gap = 160L

    private val repeat = object : Runnable {
        override fun run() {
            ticks += 1
            // A long hold is how you cross from 1 to 60 without a keypad.
            val times = when {
                ticks >= 16 -> 10
                ticks >= 8 -> 5
                else -> 1
            }
            nudge(times)
            gap = (gap * 4 / 5).coerceAtLeast(40L)
            handler.postDelayed(this, gap)
        }
    }

    fun bind() {
        value.setOnClickListener { setEditing(!editing) }
        listen(minus, -1)
        listen(plus, 1)
        show()
    }

    fun close() {
        setEditing(false)
    }

    /** Paints the saved number. Called again when a fresh balance changes the ceiling. */
    fun show() {
        value.text = format(read())
        val shown = if (editing) View.VISIBLE else View.INVISIBLE
        minus.visibility = shown
        plus.visibility = shown
        // Invisible sides must not take the tap that opens the number.
        minus.isClickable = editing
        plus.isClickable = editing
    }

    fun stop() {
        handler.removeCallbacks(repeat)
    }

    private fun setEditing(open: Boolean) {
        if (open && !editing) onOpen()
        editing = open
        if (!open) stop()
        show()
    }

    private fun listen(view: TextView, dir: Int) {
        view.setOnTouchListener { _, event ->
            if (!editing) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    direction = dir
                    ticks = 0
                    gap = 160L
                    nudge(1)
                    // A short tap ends before this, so it stays a single step.
                    handler.postDelayed(repeat, 420L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stop()
                    true
                }
                else -> false
            }
        }
    }

    private fun nudge(times: Int) {
        val current = read()
        val lo = floor()
        val hi = ceiling().coerceAtLeast(lo)
        val next = if (direction > 0) {
            AlertSteps.up(current, step, times, hi)
        } else {
            AlertSteps.down(current, step, times, lo)
        }
        if (next != current) write(next)
        show()
    }
}
