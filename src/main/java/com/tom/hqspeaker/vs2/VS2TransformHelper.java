package com.tom.hqspeaker.vs2;

import com.tom.hqspeaker.HQSpeakerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4dc;

public final class VS2TransformHelper {
    private static volatile Boolean vs2Available;

    private VS2TransformHelper() {}

    public static boolean isVS2Loaded() {
        Boolean cached = vs2Available;
        if (cached != null) return cached;
        try {
            Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            vs2Available = true;
            HQSpeakerMod.log("Valkyrien Skies 2 detected - VS2 integration enabled");
            return true;
        } catch (ClassNotFoundException e) {
            vs2Available = false;
            HQSpeakerMod.log("Valkyrien Skies 2 not detected - using standard coordinate system");
            return false;
        } catch (LinkageError | RuntimeException e) {
            vs2Available = false;
            HQSpeakerMod.warn("Error checking for Valkyrien Skies 2: " + safeMessage(e));
            return false;
        }
    }

    public static Object getShipManagingBlock(Level level, BlockPos pos) {
        if (!isVS2Loaded()) return null;
        try {
            Class<?> vsGameUtils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            return vsGameUtils.getMethod("getShipManagingPos", Level.class, BlockPos.class)
                .invoke(null, level, pos);
        } catch (ReflectiveOperationException | LinkageError e) {
            disableForApiMismatch("getShipManagingPos", e);
            return null;
        }
    }

    public static Matrix4dc getShipToWorldMatrix(Object ship) {
        if (ship == null || !isVS2Loaded()) return null;

        // ClientShip exposes a render transform. Prefer it so audio follows the visually interpolated ship position.
        try {
            var renderTransformMethod = ship.getClass().getMethod("getRenderTransform");
            Object renderTransform = renderTransformMethod.invoke(ship);
            if (renderTransform != null) {
                Object matrix = renderTransform.getClass().getMethod("getShipToWorldMatrix").invoke(renderTransform);
                if (matrix instanceof Matrix4dc result) return result;
            }
        } catch (NoSuchMethodException ignored) {
            // Server-side/non-client Ship: use the direct current transform below.
        } catch (ReflectiveOperationException | LinkageError e) {
            disableForApiMismatch("render ship-to-world transform", e);
            return null;
        }

        try {
            Object matrix = ship.getClass().getMethod("getShipToWorld").invoke(ship);
            if (matrix instanceof Matrix4dc result) return result;
            disableForApiMismatch("ship-to-world transform type", null);
        } catch (ReflectiveOperationException | LinkageError e) {
            disableForApiMismatch("ship-to-world transform", e);
        }
        return null;
    }

    private static void disableForApiMismatch(String operation, Throwable failure) {
        vs2Available = false;
        String suffix = failure == null ? "" : ": " + safeMessage(failure);
        HQSpeakerMod.warn("VS2 integration disabled; incompatible " + operation + suffix);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
