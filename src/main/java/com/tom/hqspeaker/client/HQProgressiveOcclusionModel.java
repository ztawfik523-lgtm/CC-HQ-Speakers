package com.tom.hqspeaker.client;

/**
 * Pure progressive direct-occlusion model ported from the last runtime-approved compat tuning.
 *
 * <p>The model owns only direct/dry occlusion. Sound Physics Remastered remains authoritative for
 * room/reverb/reflection evaluation. The caller supplies SPR's own runOcclusion result for each path.</p>
 */
final class HQProgressiveOcclusionModel {
    static final double INNER_VARIATION = 0.20;
    static final double OUTER_VARIATION = 0.49;
    static final double OPEN_CENTER_RING_SCALE = 0.20;

    static final double CENTER_WEIGHT = 4.0;
    static final double INNER_WEIGHT = 1.0;
    static final double OUTER_WEIGHT = 0.5;
    static final double TOTAL_WEIGHT = CENTER_WEIGHT + 8.0 * INNER_WEIGHT + 8.0 * OUTER_WEIGHT;

    static final double CUTOFF_OCCLUSION_SCALE = 0.35;
    static final double GAIN_OCCLUSION_SCALE = 0.50;

    static final double CENTER_FULL_REFRESH_DELTA = 0.20;
    static final double LISTENER_FULL_REFRESH_TRAVEL = 0.50;
    private static final double SOURCE_CHANGE_EPSILON_SQUARED = 1.0e-12;

    @FunctionalInterface
    interface OcclusionSampler {
        double sample(
            double sourceX, double sourceY, double sourceZ,
            double listenerX, double listenerY, double listenerZ
        );
    }

    record Result(
        double rawOcclusion,
        float directCutoff,
        float directGain,
        int sampledPaths,
        boolean fullRefresh
    ) {}

    private boolean ringsValid;
    private double innerSum;
    private double outerSum;
    private boolean updateInnerNext = true;

    private boolean positionsValid;
    private double lastFullSourceX;
    private double lastFullSourceY;
    private double lastFullSourceZ;
    private double lastListenerX;
    private double lastListenerY;
    private double lastListenerZ;
    private double listenerTravelSinceFull;
    private double lastFullCenter;

    Result evaluate(
        OcclusionSampler sampler,
        double sourceX, double sourceY, double sourceZ,
        double listenerX, double listenerY, double listenerZ,
        double blockAbsorption,
        double maxOcclusion
    ) {
        if (sampler == null) throw new NullPointerException("sampler");
        requireFinite(sourceX, "sourceX");
        requireFinite(sourceY, "sourceY");
        requireFinite(sourceZ, "sourceZ");
        requireFinite(listenerX, "listenerX");
        requireFinite(listenerY, "listenerY");
        requireFinite(listenerZ, "listenerZ");
        requireFinite(blockAbsorption, "blockAbsorption");
        requireFinite(maxOcclusion, "maxOcclusion");

        double absorption = Math.max(0.0, blockAbsorption);
        double maximum = Math.max(0.0, maxOcclusion);

        if (positionsValid) {
            listenerTravelSinceFull += distance(
                listenerX, listenerY, listenerZ,
                lastListenerX, lastListenerY, lastListenerZ);
        }
        lastListenerX = listenerX;
        lastListenerY = listenerY;
        lastListenerZ = listenerZ;

        boolean sourceChanged = positionsValid && distanceSquared(
            sourceX, sourceY, sourceZ,
            lastFullSourceX, lastFullSourceY, lastFullSourceZ) > SOURCE_CHANGE_EPSILON_SQUARED;

        double center = sample(
            sampler,
            sourceX, sourceY, sourceZ,
            listenerX, listenerY, listenerZ);

        boolean full = !ringsValid
            || sourceChanged
            || listenerTravelSinceFull >= LISTENER_FULL_REFRESH_TRAVEL
            || Math.abs(center - lastFullCenter) >= CENTER_FULL_REFRESH_DELTA;

        int sampledPaths = 1;
        if (full) {
            innerSum = sampleRing(
                sampler, INNER_VARIATION,
                sourceX, sourceY, sourceZ,
                listenerX, listenerY, listenerZ);
            outerSum = sampleRing(
                sampler, OUTER_VARIATION,
                sourceX, sourceY, sourceZ,
                listenerX, listenerY, listenerZ);
            sampledPaths = 17;
            ringsValid = true;
            updateInnerNext = true;

            lastFullCenter = center;
            lastFullSourceX = sourceX;
            lastFullSourceY = sourceY;
            lastFullSourceZ = sourceZ;
            listenerTravelSinceFull = 0.0;
            positionsValid = true;
        } else if (updateInnerNext) {
            innerSum = sampleRing(
                sampler, INNER_VARIATION,
                sourceX, sourceY, sourceZ,
                listenerX, listenerY, listenerZ);
            sampledPaths = 9;
            updateInnerNext = false;
        } else {
            outerSum = sampleRing(
                sampler, OUTER_VARIATION,
                sourceX, sourceY, sourceZ,
                listenerX, listenerY, listenerZ);
            sampledPaths = 9;
            updateInnerNext = true;
        }

        // The approved doorway behavior deliberately gates only the ring numerator.
        // The denominator stays fixed, so a clear center ray can strongly reduce surrounding-wall influence.
        double c = clamp(center, 0.0, 1.0);
        double smooth = c * c * (3.0 - 2.0 * c);
        double ringScale = OPEN_CENTER_RING_SCALE + (1.0 - OPEN_CENTER_RING_SCALE) * smooth;

        double numerator =
            center * CENTER_WEIGHT
                + innerSum * INNER_WEIGHT * ringScale
                + outerSum * OUTER_WEIGHT * ringScale;
        double raw = Math.max(0.0, numerator / TOTAL_WEIGHT);

        double cutoffOcc = Math.min(maximum, raw * CUTOFF_OCCLUSION_SCALE);
        double gainOcc = Math.min(maximum, raw * GAIN_OCCLUSION_SCALE);

        float cutoff = (float) Math.exp(-cutoffOcc * absorption * 3.0);
        float gain = (float) Math.exp(-gainOcc * absorption * 0.3);
        return new Result(raw, cutoff, gain, sampledPaths, full);
    }

    void reset() {
        ringsValid = false;
        positionsValid = false;
        innerSum = 0.0;
        outerSum = 0.0;
        updateInnerNext = true;
        listenerTravelSinceFull = 0.0;
        lastFullCenter = 0.0;
    }

    private static double sampleRing(
        OcclusionSampler sampler,
        double variation,
        double sourceX, double sourceY, double sourceZ,
        double listenerX, double listenerY, double listenerZ
    ) {
        double sum = 0.0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    double ox = sx * variation;
                    double oy = sy * variation;
                    double oz = sz * variation;
                    sum += sample(
                        sampler,
                        sourceX + ox, sourceY + oy, sourceZ + oz,
                        listenerX + ox, listenerY + oy, listenerZ + oz);
                }
            }
        }
        return sum;
    }

    private static double sample(
        OcclusionSampler sampler,
        double sourceX, double sourceY, double sourceZ,
        double listenerX, double listenerY, double listenerZ
    ) {
        double value = sampler.sample(sourceX, sourceY, sourceZ, listenerX, listenerY, listenerZ);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("SPR occlusion sampler returned a non-finite value");
        }
        // Match the runtime-approved Beta3/Beta5 model: individual runOcclusion paths stay raw.
        // SPR maxOcclusion is applied only after the weighted path blend, separately to cutoff/gain.
        return value;
    }

    private static double distance(
        double ax, double ay, double az,
        double bx, double by, double bz
    ) {
        return Math.sqrt(distanceSquared(ax, ay, az, bx, by, bz));
    }

    private static double distanceSquared(
        double ax, double ay, double az,
        double bx, double by, double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
