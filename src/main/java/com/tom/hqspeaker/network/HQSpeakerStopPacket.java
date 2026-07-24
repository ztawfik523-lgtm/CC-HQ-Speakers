package com.tom.hqspeaker.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.tom.hqspeaker.HQSpeakerMod;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;


public class HQSpeakerStopPacket implements CustomPacketPayload {

    public static final Type<HQSpeakerStopPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HQSpeakerMod.MOD_ID, "stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HQSpeakerStopPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public HQSpeakerStopPacket decode(RegistryFriendlyByteBuf buf) {
                return HQSpeakerStopPacket.decode(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, HQSpeakerStopPacket pkt) {
                HQSpeakerStopPacket.encode(pkt, buf);
            }
        };

    public final UUID source;

    public HQSpeakerStopPacket(UUID source) {
        this.source = source;
    }

    public static void encode(HQSpeakerStopPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.source);
    }

    public static HQSpeakerStopPacket decode(FriendlyByteBuf buf) {
        return new HQSpeakerStopPacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HQSpeakerStopPacket pkt, IPayloadContext ctx) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.stop(pkt.source);
    }
}
