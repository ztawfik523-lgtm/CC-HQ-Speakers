package com.tom.hqspeaker.client;

/**
 * Pure reflected-source position stabilizer using the last runtime-approved compat defaults.
 */
final class HQReflectionStabilizer {
    static final double REFLECTION_THRESHOLD = 0.45;
    static final double REFLECTION_BLEND = 0.35;
    static final double MAX_REFLECTION_OFFSET = 2.5;
    static final double REDIRECT_ALPHA = 0.22;
    static final double CLEAR_TO_REAL_ALPHA = 0.28;
    static final double FLIP_TO_CENTER_ALPHA = 0.35;
    static final double MATERIAL_OFFSET = 0.20;

    record Point(double x, double y, double z) {
        Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("point coordinates must be finite");
            }
        }

        Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        Point subtract(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        Point scale(double factor) { return new Point(x * factor, y * factor, z * factor); }
        double dot(Point other) { return x * other.x + y * other.y + z * other.z; }
        double lengthSquared() { return dot(this); }
        double length() { return Math.sqrt(lengthSquared()); }
    }

    private boolean initialized;
    private Point current;
    private Point lastPhysical;

    Point update(Point physical, Point reflected, double rawOcclusion) {
        if (physical == null) throw new NullPointerException("physical");
        if (!Double.isFinite(rawOcclusion)) throw new IllegalArgumentException("rawOcclusion must be finite");

        if (!initialized) {
            current = physical;
            lastPhysical = physical;
            initialized = true;
        } else {
            // Carry the existing acoustic offset along with a moving physical source.
            Point physicalDelta = physical.subtract(lastPhysical);
            current = current.add(physicalDelta);
            lastPhysical = physical;
        }

        Point currentOffset = current.subtract(physical);
        boolean redirect = reflected != null && rawOcclusion >= REFLECTION_THRESHOLD;
        Point target;
        double alpha;

        if (!redirect) {
            target = physical;
            alpha = CLEAR_TO_REAL_ALPHA;
        } else {
            Point reflectedOffset = reflected.subtract(physical);
            double reflectedLength = reflectedOffset.length();
            if (reflectedLength > MAX_REFLECTION_OFFSET && reflectedLength > 0.0) {
                reflectedOffset = reflectedOffset.scale(MAX_REFLECTION_OFFSET / reflectedLength);
            }
            Point desiredOffset = reflectedOffset.scale(REFLECTION_BLEND);

            boolean flip = currentOffset.dot(desiredOffset) < 0.0
                && currentOffset.length() > MATERIAL_OFFSET
                && desiredOffset.length() > MATERIAL_OFFSET;
            if (flip) {
                target = physical;
                alpha = FLIP_TO_CENTER_ALPHA;
            } else {
                target = physical.add(desiredOffset);
                alpha = REDIRECT_ALPHA;
            }
        }

        current = lerp(current, target, alpha);
        if (current.subtract(target).lengthSquared() < 1.0e-10) current = target;
        return current;
    }

    Point currentOrPhysical(Point physical) {
        if (physical == null) throw new NullPointerException("physical");
        return initialized ? current : physical;
    }

    void reset() {
        initialized = false;
        current = null;
        lastPhysical = null;
    }

    private static Point lerp(Point from, Point to, double alpha) {
        return new Point(
            from.x + (to.x - from.x) * alpha,
            from.y + (to.y - from.y) * alpha,
            from.z + (to.z - from.z) * alpha);
    }
}
