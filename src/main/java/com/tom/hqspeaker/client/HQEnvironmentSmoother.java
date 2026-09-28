package com.tom.hqspeaker.client;

import java.util.Arrays;

/**
 * Pure acoustic smoothing state using the last runtime-approved compat coefficients.
 *
 * <p>Direct muffling reacts faster. Direct clearing is logarithmic so wall-to-open transitions
 * do not snap perceptually. Native SPR room-send targets are retained and only smoothed.</p>
 */
final class HQEnvironmentSmoother {
    static final float MUFFLE_ALPHA = 0.30f;
    static final float CLEAR_CUTOFF_ALPHA = 0.18f;
    static final float CLEAR_GAIN_ALPHA = 0.16f;
    static final float REVERB_ALPHA = 0.22f;

    private static final float LOG_EPSILON = 1.0e-5f;

    record Environment(
        float[] sendGains,
        float[] sendCutoffs,
        float directCutoff,
        float directGain
    ) {
        Environment {
            if (sendGains == null || sendGains.length != 4) {
                throw new IllegalArgumentException("sendGains must contain four values");
            }
            if (sendCutoffs == null || sendCutoffs.length != 4) {
                throw new IllegalArgumentException("sendCutoffs must contain four values");
            }
            sendGains = sendGains.clone();
            sendCutoffs = sendCutoffs.clone();
        }

        @Override public float[] sendGains() { return sendGains.clone(); }
        @Override public float[] sendCutoffs() { return sendCutoffs.clone(); }
    }

    private final float[] sendGains = new float[4];
    private final float[] sendCutoffs = new float[4];
    private float directCutoff;
    private float directGain;
    private boolean initialized;

    Environment update(
        float[] targetSendGains,
        float[] targetSendCutoffs,
        float targetDirectCutoff,
        float targetDirectGain
    ) {
        validate(targetSendGains, "targetSendGains");
        validate(targetSendCutoffs, "targetSendCutoffs");
        requireFinite(targetDirectCutoff, "targetDirectCutoff");
        requireFinite(targetDirectGain, "targetDirectGain");

        if (!initialized) {
            System.arraycopy(targetSendGains, 0, sendGains, 0, 4);
            System.arraycopy(targetSendCutoffs, 0, sendCutoffs, 0, 4);
            directCutoff = targetDirectCutoff;
            directGain = targetDirectGain;
            initialized = true;
        } else {
            for (int i = 0; i < 4; i++) {
                sendGains[i] = approachLinear(sendGains[i], targetSendGains[i], REVERB_ALPHA);
                sendCutoffs[i] = approachLinear(sendCutoffs[i], targetSendCutoffs[i], REVERB_ALPHA);
            }

            directCutoff = targetDirectCutoff < directCutoff
                ? approachLinear(directCutoff, targetDirectCutoff, MUFFLE_ALPHA)
                : approachLog(directCutoff, targetDirectCutoff, CLEAR_CUTOFF_ALPHA);
            directGain = targetDirectGain < directGain
                ? approachLinear(directGain, targetDirectGain, MUFFLE_ALPHA)
                : approachLog(directGain, targetDirectGain, CLEAR_GAIN_ALPHA);
        }

        return snapshot();
    }

    Environment snapshot() {
        if (!initialized) {
            return new Environment(new float[4], new float[]{ 1f, 1f, 1f, 1f }, 1f, 1f);
        }
        return new Environment(sendGains, sendCutoffs, directCutoff, directGain);
    }

    void reset() {
        Arrays.fill(sendGains, 0f);
        Arrays.fill(sendCutoffs, 0f);
        directCutoff = 0f;
        directGain = 0f;
        initialized = false;
    }

    private static float approachLinear(float current, float target, float alpha) {
        return current + (target - current) * alpha;
    }

    private static float approachLog(float current, float target, float alpha) {
        if (target <= 0f) return approachLinear(current, target, alpha);
        double from = Math.log(Math.max(LOG_EPSILON, current));
        double to = Math.log(Math.max(LOG_EPSILON, target));
        return (float) Math.exp(from + (to - from) * alpha);
    }

    private static void validate(float[] values, String name) {
        if (values == null || values.length != 4) {
            throw new IllegalArgumentException(name + " must contain four values");
        }
        for (float value : values) requireFinite(value, name);
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
