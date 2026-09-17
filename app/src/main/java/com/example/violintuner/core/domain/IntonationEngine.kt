package com.example.violintuner.core.domain

/**
 * Stateful pipeline from raw [PitchFrame]s to [IntonationReading]s. Time comes only from
 * [PitchFrame.tMs], so the engine is deterministic. Not thread-safe: feed it from one coroutine.
 */
class IntonationEngine(private val config: IntonationConfig = IntonationConfig()) {
    private val gate = SignalGate(config)
    private val noteLock = NoteLock(config)
    private val hysteresis = ZoneHysteresis(config)
    private val holdTimer = HoldTimer(config)

    // Frames agreeing with the locked note feed `smoother`; frames of a not-yet-locked candidate
    // feed `pendingSmoother`, which takes over on lock so the new note starts warmed up instead
    // of gliding from the old one.
    private var smoother = PitchSmoother(config)
    private var pendingSmoother = PitchSmoother(config)

    private var mode: TargetMode? = null
    private var lastActive: IntonationReading.Active? = null
    private var lastMatchMs = 0L

    fun process(frame: PitchFrame, mode: TargetMode): IntonationReading {
        if (mode != this.mode) {
            reset()
            this.mode = mode
        }
        val fractionalMidi = frame.usableMidi()
        val targetMidi = fractionalMidi?.let { targetFor(it, frame.freqHz, mode) }
        val kind = when {
            frame.rms < config.silenceRms -> FrameKind.QUIET
            fractionalMidi == null || targetMidi == null -> FrameKind.UNCLEAR
            else -> FrameKind.PITCHED
        }
        return when (gate.update(frame.tMs, kind)) {
            SignalState.SILENCE -> {
                clearTracking()
                IntonationReading.Silence
            }
            SignalState.TOO_NOISY -> {
                clearTracking()
                IntonationReading.TooNoisy
            }
            SignalState.GAP -> bridgeGap(frame.tMs)
            // kind == PITCHED guarantees both values are non-null
            SignalState.PITCHED -> track(frame.tMs, fractionalMidi!!, targetMidi!!)
        }
    }

    fun reset() {
        gate.reset()
        clearTracking()
        mode = null
    }

    private fun track(tMs: Long, fractionalMidi: Double, targetMidi: Int): IntonationReading {
        if (targetMidi != noteLock.candidate) pendingSmoother.reset()
        val previousLocked = noteLock.locked
        val locked = noteLock.update(tMs, targetMidi)
        if (locked != previousLocked) {
            smoother = pendingSmoother.also { pendingSmoother = smoother }
            pendingSmoother.reset()
            hysteresis.reset()
            holdTimer.reset()
        }
        if (locked == null || targetMidi != locked) {
            pendingSmoother.add(fractionalMidi)
            return bridgeGap(tMs)
        }

        val cents = PitchMath.centsFromNote(smoother.add(fractionalMidi), locked)
        val zone = hysteresis.update(cents)
        val active = IntonationReading.Active(
            note = Note(locked),
            cents = cents,
            zone = zone,
            direction = if (zone == Zone.IN_TUNE) null else Direction.of(cents),
            holdProgress = holdTimer.update(tMs, inTune = zone == Zone.IN_TUNE),
        )
        lastActive = active
        lastMatchMs = tMs
        return active
    }

    /** No frame for the locked note: keep the last reading; drop the hold once the gap is long. */
    private fun bridgeGap(tMs: Long): IntonationReading {
        val last = lastActive ?: return IntonationReading.Silence
        if (tMs - lastMatchMs <= config.pitchGapToleranceMs) return last.copy(held = true)
        holdTimer.reset()
        return last.copy(holdProgress = 0.0).also { lastActive = it }.copy(held = true)
    }

    private fun clearTracking() {
        noteLock.reset()
        smoother.reset()
        pendingSmoother.reset()
        hysteresis.reset()
        holdTimer.reset()
        lastActive = null
    }

    /** Fractional MIDI if the frame has a confident pitch inside the range of interest. */
    private fun PitchFrame.usableMidi(): Double? {
        val freq = freqHz ?: return null
        if (freq <= 0.0 || clarity < config.clarityThreshold) return null
        val midi = PitchMath.frequencyToMidi(freq, config.a4Hz)
        val lowest = config.lowestMidi - config.rangeMarginBelowCents / PitchMath.CENTS_PER_SEMITONE
        val highest = config.highestMidi + config.rangeMarginAboveCents / PitchMath.CENTS_PER_SEMITONE
        return midi.takeIf { it in lowest..highest }
    }

    private fun targetFor(fractionalMidi: Double, freqHz: Double?, mode: TargetMode): Int? = when (mode) {
        TargetMode.Chromatic -> PitchMath.nearestMidi(fractionalMidi)
        is TargetMode.Strings ->
            freqHz?.let { StringSnapper.snap(it, mode.locked, config)?.midi }
    }
}
