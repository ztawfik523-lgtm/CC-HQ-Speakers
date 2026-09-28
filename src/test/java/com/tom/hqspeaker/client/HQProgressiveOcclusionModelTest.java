package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HQProgressiveOcclusionModelTest {
    @Test
    void firstRefreshUsesAllSeventeenPathsThenAlternatesNinePathRings() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        AtomicInteger calls = new AtomicInteger();
        HQProgressiveOcclusionModel.OcclusionSampler sampler = (sx, sy, sz, lx, ly, lz) -> {
            calls.incrementAndGet();
            return 0.5;
        };

        var first = model.evaluate(sampler, 0, 0, 0, 3, 0, 0, 1.0, 4.0);
        assertTrue(first.fullRefresh());
        assertEquals(17, first.sampledPaths());
        assertEquals(17, calls.get());

        calls.set(0);
        var second = model.evaluate(sampler, 0, 0, 0, 3.05, 0, 0, 1.0, 4.0);
        assertFalse(second.fullRefresh());
        assertEquals(9, second.sampledPaths());
        assertEquals(9, calls.get());

        calls.set(0);
        var third = model.evaluate(sampler, 0, 0, 0, 3.10, 0, 0, 1.0, 4.0);
        assertFalse(third.fullRefresh());
        assertEquals(9, third.sampledPaths());
        assertEquals(9, calls.get());
    }

    @Test
    void clearCenterKeepsOnlyTwentyPercentRingInfluence() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        var result = model.evaluate(
            (sx, sy, sz, lx, ly, lz) -> {
                boolean center = Math.abs(sx) < 1.0e-9 && Math.abs(sy) < 1.0e-9 && Math.abs(sz) < 1.0e-9;
                return center ? 0.0 : 1.0;
            },
            0, 0, 0, 3, 0, 0,
            1.0, 4.0);

        // C=0, I=8, O=8, R=.2 => (0 + 8*.2 + 8*.5*.2) / 16 = .15.
        assertEquals(0.15, result.rawOcclusion(), 1.0e-9);
    }

    @Test
    void directTargetsMatchApprovedFormula() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        var result = model.evaluate(
            (sx, sy, sz, lx, ly, lz) -> 1.0,
            0, 0, 0, 3, 0, 0,
            1.0, 4.0);

        assertEquals(1.0, result.rawOcclusion(), 1.0e-9);
        assertEquals(Math.exp(-1.0 * 0.35 * 3.0), result.directCutoff(), 1.0e-6);
        assertEquals(Math.exp(-1.0 * 0.50 * 0.3), result.directGain(), 1.0e-6);
    }

    @Test
    void individualPathsAreNotClampedBeforeWeightedBlend() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        var result = model.evaluate(
            (sx, sy, sz, lx, ly, lz) -> 5.0,
            0, 0, 0, 3, 0, 0,
            1.0, 4.0);

        assertEquals(5.0, result.rawOcclusion(), 1.0e-9);
        assertEquals(Math.exp(-1.75 * 3.0), result.directCutoff(), 1.0e-6);
        assertEquals(Math.exp(-2.5 * 0.3), result.directGain(), 1.0e-6);
    }

    @Test
    void centerDeltaAndListenerTravelForceFreshSeventeenPaths() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        double[] center = { 0.2 };
        var sampler = new HQProgressiveOcclusionModel.OcclusionSampler() {
            @Override
            public double sample(double sx, double sy, double sz, double lx, double ly, double lz) {
                boolean isCenter = Math.abs(sx) < 1.0e-9 && Math.abs(sy) < 1.0e-9 && Math.abs(sz) < 1.0e-9;
                return isCenter ? center[0] : 0.2;
            }
        };

        assertTrue(model.evaluate(sampler, 0, 0, 0, 3, 0, 0, 1.0, 4.0).fullRefresh());

        center[0] = 0.41;
        assertTrue(model.evaluate(sampler, 0, 0, 0, 3.05, 0, 0, 1.0, 4.0).fullRefresh());

        // Small movements add up to the accepted 0.5 block full-cache refresh threshold.
        assertFalse(model.evaluate(sampler, 0, 0, 0, 3.20, 0, 0, 1.0, 4.0).fullRefresh());
        assertFalse(model.evaluate(sampler, 0, 0, 0, 3.35, 0, 0, 1.0, 4.0).fullRefresh());
        assertFalse(model.evaluate(sampler, 0, 0, 0, 3.50, 0, 0, 1.0, 4.0).fullRefresh());
        assertTrue(model.evaluate(sampler, 0, 0, 0, 3.70, 0, 0, 1.0, 4.0).fullRefresh());
    }

    @Test
    void anyMeaningfulSourceMoveForcesFullRefresh() {
        HQProgressiveOcclusionModel model = new HQProgressiveOcclusionModel();
        var sampler = (HQProgressiveOcclusionModel.OcclusionSampler) (sx, sy, sz, lx, ly, lz) -> 0.3;

        model.evaluate(sampler, 0, 0, 0, 3, 0, 0, 1.0, 4.0);
        var moved = model.evaluate(sampler, 0.01, 0, 0, 3, 0, 0, 1.0, 4.0);

        assertTrue(moved.fullRefresh());
        assertEquals(17, moved.sampledPaths());
    }
}
