package com.tom.hqspeaker.peripheral;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.core.methods.MethodSupplier;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The physical CC speaker exposed to Lua. Standard speaker calls are delegated to CC:T's original
 * SpeakerPeripheral while HQ-specific calls remain available through the inherited implementation.
 */
public final class HQSpeakerCompositePeripheral implements IDynamicPeripheral {
    private static final MethodSupplier<PeripheralMethod> METHOD_SUPPLIER = PeripheralMethodSupplier.create(List.of());
    private static final Set<String> STANDARD = Set.of("playNote", "playSound", "playAudio", "stop");
    private static final Set<String> FINITE_CONTROLS = Set.of(
        "audioStatus", "audioPause", "audioResume", "audioSeek", "audioSetVolume", "audioSetLooping", "audioStop"
    );
    private static final Set<String> FINITE_START = Set.of(
        "speakMp3", "speakOgg", "speakAudio", "speakFile", "speakPacked", "speakWav"
    );
    private static final Set<String> RAW_START = Set.of("speakPCM");
    private static final Set<String> STREAM_START = Set.of("speakStream", "speakHLS", "speakTS");

    private final HQSpeakerPeripheral legacy;
    private final SpeakerPeripheral vanilla;
    private final HQFiniteMediaServer finite;
    private final Map<String, PeripheralMethod> legacyMethods;
    private final String[] dynamicNames;
    private final Map<IComputerAccess, IComputerAccess> legacyComputerViews = new ConcurrentHashMap<>();

    public HQSpeakerCompositePeripheral(HQSpeakerPeripheral legacy, SpeakerPeripheral vanilla, HQFiniteMediaServer finite) {
        this.legacy = legacy;
        this.vanilla = vanilla;
        this.finite = finite;
        this.legacyMethods = METHOD_SUPPLIER.getSelfMethods(legacy);
        LinkedHashSet<String> names = new LinkedHashSet<>(legacyMethods.keySet());
        names.addAll(STANDARD);
        dynamicNames = names.toArray(String[]::new);
    }

    @Override public String getType() { return "speaker"; }
    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }
    @Override public String[] getMethodNames() { return dynamicNames.clone(); }

    boolean usesVanilla(SpeakerPeripheral candidate) { return vanilla == candidate; }

    @Override
    public void attach(IComputerAccess computer) {
        vanilla.attach(computer);
        finite.attach(computer);
        IComputerAccess filtered = legacyComputerViews.computeIfAbsent(computer, HQSpeakerCompositePeripheral::filteredLegacyAccess);
        legacy.attach(filtered);
    }

    @Override
    public void detach(IComputerAccess computer) {
        finite.detach(computer);
        IComputerAccess filtered = legacyComputerViews.remove(computer);
        if (filtered != null) legacy.detach(filtered);
        vanilla.detach(computer);
    }

    public void cleanup() {
        finite.cleanup();
        legacy.cleanup();
        legacyComputerViews.clear();
    }

    /**
     * Legacy HQ used speaker_audio_empty as a generic queue heartbeat. That breaks CC:T's documented
     * playAudio backpressure contract. Standard events must therefore come exclusively from the native
     * SpeakerPeripheral; the old synthetic event is swallowed at this boundary.
     */
    private static IComputerAccess filteredLegacyAccess(IComputerAccess delegate) {
        return (IComputerAccess) Proxy.newProxyInstance(
            IComputerAccess.class.getClassLoader(), new Class<?>[]{ IComputerAccess.class },
            (proxy, method, args) -> {
                if ("queueEvent".equals(method.getName()) && args != null && args.length > 0
                        && "speaker_audio_empty".equals(args[0])) return null;
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    @LuaFunction
    public final String audioMountPath(IComputerAccess computer) throws LuaException {
        return finite.mountPath(computer);
    }

    @LuaFunction(mainThread = true)
    public final boolean audioPlayStaged(IComputerAccess computer, String path,
                                         Optional<Double> volume, Optional<Boolean> consume) throws LuaException {
        if (legacy.speakIsPlaying() || vanilla.madeSound() || finite.isActive()) return false;
        return finite.playStaged(computer, path, volume.orElse(1.0), consume.orElse(true));
    }

    @LuaFunction
    public final long audioMaxStagedBytes() {
        return HQFiniteMediaServer.MOUNT_CAPACITY_BYTES;
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments args) throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        if (STANDARD.contains(name)) return callStandard(name, context, args);

        if (FINITE_CONTROLS.contains(name)) {
            MethodResult local = callFiniteControl(name, args);
            if (local != null) return local;
        }

        if ("speakStop".equals(name)) {
            finite.stop();
            vanilla.stop();
            return invokeLegacy(name, computer, context, args);
        }
        if ("speakIsPlaying".equals(name)) {
            return MethodResult.of(finite.isActive() || legacy.speakIsPlaying() || vanilla.madeSound());
        }
        if ("setLooping".equals(name) && finite.isActive()) {
            return MethodResult.of(finite.setLooping(args.getBoolean(0)));
        }

        if ((FINITE_START.contains(name) || STREAM_START.contains(name)) && (finite.isActive() || vanilla.madeSound())) {
            return MethodResult.of(false);
        }
        if (RAW_START.contains(name) && finite.isActive()) return MethodResult.of(false);
        if (FINITE_START.contains(name) && legacy.speakIsPlaying()) return MethodResult.of(false);

        return invokeLegacy(name, computer, context, args);
    }

    private MethodResult callStandard(String name, ILuaContext context, IArguments args) throws LuaException {
        return switch (name) {
            case "playNote" -> MethodResult.of(vanilla.playNote(context, args.getString(0), args.optDouble(1), args.optDouble(2)));
            case "playSound" -> {
                if (finite.isActive() || legacy.speakIsPlaying()) yield MethodResult.of(false);
                yield MethodResult.of(vanilla.playSound(context, args.getString(0), args.optDouble(1), args.optDouble(2)));
            }
            case "playAudio" -> {
                if (finite.isActive() || legacy.speakIsPlaying()) yield MethodResult.of(false);
                yield MethodResult.of(vanilla.playAudio(context, args.getTableUnsafe(0), args.optDouble(1)));
            }
            case "stop" -> {
                vanilla.stop();
                finite.stop();
                legacy.speakStop();
                yield MethodResult.of();
            }
            default -> throw new LuaException("No such method " + name);
        };
    }

    private @Nullable MethodResult callFiniteControl(String name, IArguments args) throws LuaException {
        if (!finite.hasStatus()) return null;
        return switch (name) {
            case "audioStatus" -> MethodResult.of(finite.status());
            case "audioPause" -> MethodResult.of(finite.pause());
            case "audioResume" -> MethodResult.of(finite.resume());
            case "audioSeek" -> MethodResult.of(finite.seek(args.getDouble(0)));
            case "audioSetVolume" -> MethodResult.of(finite.setVolume(args.getDouble(0)));
            case "audioSetLooping" -> MethodResult.of(finite.setLooping(args.getBoolean(0)));
            case "audioStop" -> { finite.stop(); yield MethodResult.of(); }
            default -> null;
        };
    }

    private MethodResult invokeLegacy(String name, IComputerAccess computer, ILuaContext context, IArguments args) throws LuaException {
        PeripheralMethod legacyMethod = legacyMethods.get(name);
        if (legacyMethod == null) throw new LuaException("No such method " + name);
        return legacyMethod.apply(legacy, context, computer, args);
    }
}
