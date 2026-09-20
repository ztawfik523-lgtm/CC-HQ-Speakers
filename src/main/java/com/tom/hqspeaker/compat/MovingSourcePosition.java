package com.tom.hqspeaker.compat;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.vs2.VS2TransformHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves one speaker block center into current world space without movement packets.
 *
 * <p>Sable Companion is the first path because it is a lightweight embedded compatibility API with a safe
 * no-Sable fallback. Sable stores sub-level contents in plots owned by the parent Minecraft Level, so this
 * transforms plot-space coordinates without changing the Level/dimension identity. VS2 remains the second optional
 * path. Otherwise the block center is already world space.</p>
 */
public final class MovingSourcePosition {
    private static final AtomicBoolean SABLE_WARNING_EMITTED = new AtomicBoolean();

    private MovingSourcePosition() {}

    public static Vector3d resolve(Level level, BlockPos blockPos, Vector3d dest) {
        if (level == null) throw new NullPointerException("level");
        if (blockPos == null) throw new NullPointerException("blockPos");
        if (dest == null) throw new NullPointerException("dest");

        dest.set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);

        try {
            if (SableCompanion.INSTANCE.getContaining(level, blockPos) != null) {
                return SableCompanion.INSTANCE.projectOutOfSubLevel(level, dest, dest);
            }
        } catch (RuntimeException | LinkageError failure) {
            if (SABLE_WARNING_EMITTED.compareAndSet(false, true)) {
                HQSpeakerMod.warn("Sable moving-speaker position lookup failed; falling back: " + safeMessage(failure));
            }
        }

        try {
            if (VS2TransformHelper.isVS2Loaded()) {
                Object ship = VS2TransformHelper.getShipManagingBlock(level, blockPos);
                if (ship != null) {
                    Matrix4dc matrix = VS2TransformHelper.getShipToWorldMatrix(ship);
                    if (matrix != null) matrix.transformPosition(dest);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            HQSpeakerMod.warn("VS2 moving-speaker position lookup failed: " + safeMessage(failure));
        }

        return dest;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
