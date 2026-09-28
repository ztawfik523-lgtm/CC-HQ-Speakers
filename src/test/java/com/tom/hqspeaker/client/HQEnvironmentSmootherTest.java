package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HQEnvironmentSmootherTest {
    @Test
    void firstEnvironmentIsAppliedExactly() {
        HQEnvironmentSmoother smoother = new HQEnvironmentSmoother();
        var e = smoother.update(
            new float[]{ .1f, .2f, .3f, .4f },
            new float[]{ .9f, .8f, .7f, .6f },
            .5f, .75f);

        assertArrayEquals(new float[]{ .1f, .2f, .3f, .4f }, e.sendGains(), 1.0e-6f);
        assertArrayEquals(new float[]{ .9f, .8f, .7f, .6f }, e.sendCutoffs(), 1.0e-6f);
        assertEquals(.5f, e.directCutoff(), 1.0e-6f);
        assertEquals(.75f, e.directGain(), 1.0e-6f);
    }

    @Test
    void mufflingUsesThirtyPercentLinearApproach() {
        HQEnvironmentSmoother smoother = new HQEnvironmentSmoother();
        smoother.update(new float[4], new float[]{1,1,1,1}, 1f, 1f);

        var e = smoother.update(new float[4], new float[]{1,1,1,1}, .2f, .4f);
        assertEquals(.76f, e.directCutoff(), 1.0e-6f);
        assertEquals(.82f, e.directGain(), 1.0e-6f);
    }

    @Test
    void clearingUsesSeparateLogSpaceCutoffAndGainAlphas() {
        HQEnvironmentSmoother smoother = new HQEnvironmentSmoother();
        smoother.update(new float[4], new float[]{1,1,1,1}, .25f, .25f);

        var e = smoother.update(new float[4], new float[]{1,1,1,1}, 1f, 1f);

        double cutoffExpected = Math.exp(Math.log(.25) + (Math.log(1.0) - Math.log(.25)) * .18);
        double gainExpected = Math.exp(Math.log(.25) + (Math.log(1.0) - Math.log(.25)) * .16);
        assertEquals(cutoffExpected, e.directCutoff(), 1.0e-6);
        assertEquals(gainExpected, e.directGain(), 1.0e-6);
        assertTrue(e.directCutoff() > e.directGain(), "cutoff clears slightly faster than gain");
    }

    @Test
    void roomSendsUseTwentyTwoPercentLinearApproach() {
        HQEnvironmentSmoother smoother = new HQEnvironmentSmoother();
        smoother.update(new float[]{0,0,0,0}, new float[]{0,0,0,0}, 1f, 1f);

        var e = smoother.update(new float[]{1,1,1,1}, new float[]{1,1,1,1}, 1f, 1f);
        assertArrayEquals(new float[]{.22f,.22f,.22f,.22f}, e.sendGains(), 1.0e-6f);
        assertArrayEquals(new float[]{.22f,.22f,.22f,.22f}, e.sendCutoffs(), 1.0e-6f);
    }
}
