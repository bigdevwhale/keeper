package app.keeper.domain

import app.keeper.data.Breath

/**
 * The lamp keeps time for the breath. On the in-breath the beam swings round toward you and flares;
 * on a hold it stays on you; on the out-breath it sweeps away across the water and round behind the
 * tower, ready to come back.
 */
object Breathing {
    enum class Phase { IN, HOLD, OUT }

    class Moment(val phase: Phase, val progress: Float, val round: Int, val turn: Float)

    fun at(b: Breath, ms: Long): Moment {
        val cycle = b.cycleMs
        val round = (ms / cycle).toInt()
        val c = ms % cycle
        val inMs = b.inS * 1000L
        val holdMs = b.holdS * 1000L
        val outMs = b.outS * 1000L
        return when {
            c < inMs -> (c.toFloat() / inMs).let { Moment(Phase.IN, it, round, 0.25f * it) }
            c < inMs + holdMs -> Moment(Phase.HOLD, (c - inMs).toFloat() / holdMs, round, 0.25f)
            else -> ((c - inMs - holdMs).toFloat() / outMs).let { Moment(Phase.OUT, it, round, 0.25f + 0.75f * it) }
        }
    }

    /** Enough rounds for about a minute under the beam, and never fewer than four. */
    fun rounds(b: Breath): Int = maxOf(4, ((60_000 + b.cycleMs - 1) / b.cycleMs).toInt())
}
