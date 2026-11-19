package app.cursorlimit.core

/**
 * Steps for the alert numbers.
 *
 * A tap moves by one step. A hold passes a larger [times] so the jump grows.
 * Dollars step by 100 cents, and the last step up can land on the exact balance
 * (1394 cents is $13.94, not $14).
 */
object AlertSteps {
    fun up(current: Int, step: Int, times: Int, ceiling: Int): Int {
        if (current >= ceiling) return ceiling
        return (current + step * times).coerceAtMost(ceiling)
    }

    /** From $13.94 the first step down lands on $13, then continues in whole dollars. */
    fun down(current: Int, step: Int, times: Int, floor: Int): Int {
        if (current <= floor) return floor
        if (step > 0 && current % step != 0) {
            val grid = current - current % step
            return (grid - step * (times - 1)).coerceAtLeast(floor)
        }
        return (current - step * times).coerceAtLeast(floor)
    }
}
