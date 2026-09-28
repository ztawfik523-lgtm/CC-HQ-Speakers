package com.tom.hqspeaker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HQSoundPhysicsIntegrationSafetyTest {
    private static String read(String... parts) throws Exception {
        return Files.readString(Path.of(parts[0], java.util.Arrays.copyOfRange(parts, 1, parts.length)));
    }

    @Test
    void schedulerUsesPhysicalSpeakerPositionNotReflectedRenderPosition() throws Exception {
        String source = read(
            "src", "main", "java", "com", "tom", "hqspeaker", "client",
            "HQSoundPhysicsRefreshClient.java");

        int loop = source.indexOf("for (Map.Entry<UUID, Binding>");
        int due = source.indexOf("binding.policy.due(", loop);
        String window = source.substring(loop, Math.min(source.length(), due + 350));

        assertTrue(window.contains("AcousticPosition physical = physicalPosition(binding.sound())"));
        assertTrue(window.contains("physical.x(), physical.y(), physical.z()"));
        assertFalse(window.contains("binding.sound().getX(), binding.sound().getY(), binding.sound().getZ()"));
    }

    @Test
    void finalHqEnvironmentUsesPrivatePerSourceFiltersWithNativeFallback() throws Exception {
        String mixin = read(
            "src", "main", "java", "com", "tom", "hqspeaker", "mixin", "client",
            "SoundPhysicsEnvironmentMixin.java");
        String privateEfx = read(
            "src", "main", "java", "com", "tom", "hqspeaker", "client",
            "HQPrivateEfxClient.java");

        assertTrue(mixin.contains("method = \"setEnvironment(IFFFFFFFFFF)V\""));
        assertTrue(mixin.contains("at = @At(\"HEAD\")"));
        assertTrue(mixin.contains("HQSoundPhysicsAcousticsClient.applyEnvironment"));
        assertTrue(mixin.contains("if (handled) ci.cancel()"),
            "successful private EFX must prevent SPR's shared filter objects from being reattached");

        assertTrue(privateEfx.contains("final int[] sendFilters = new int[4]"));
        assertTrue(privateEfx.contains("int directFilter"));
        assertTrue(privateEfx.contains("EXTEfx.alGenFilters()"));
        assertTrue(privateEfx.contains("AL11.alSource3i("));
        assertTrue(privateEfx.contains("AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, state.directFilter)"));
        assertTrue(privateEfx.contains("Reattach every application"));
    }

    @Test
    void privateEfxIsNeverCreatedBeforePlayingOrPaused() throws Exception {
        String source = read(
            "src", "main", "java", "com", "tom", "hqspeaker", "client",
            "HQPrivateEfxClient.java");

        int stateCheck = source.indexOf("sourceState != AL_PLAYING && sourceState != AL_PAUSED");
        int create = source.indexOf("if (!state.ready && !create(state))");
        assertTrue(stateCheck >= 0 && create > stateCheck,
            "private EFX creation must stay behind the PLAYING/PAUSED lifecycle gate");
    }

    @Test
    void reflectionWritesAreStabilizedImmediatelyAndPersistedForMinecraftTicks() throws Exception {
        String source = read(
            "src", "main", "java", "com", "tom", "hqspeaker", "client",
            "HQSoundPhysicsRefreshClient.java");

        int method = source.indexOf("static void applyAcousticPosition");
        String window = source.substring(method, Math.min(source.length(), method + 900));
        assertTrue(window.contains("hqspeaker$setAcousticPosition"));
        assertTrue(window.contains("AL10.alSource3f"));
    }
}
