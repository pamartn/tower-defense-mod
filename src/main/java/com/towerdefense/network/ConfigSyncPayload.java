package com.towerdefense.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.towerdefense.config.TDConfig;
import com.towerdefense.TowerDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfigSyncPayload(TDConfig config) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "config_sync");
    public static final Type<ConfigSyncPayload> TYPE = new Type<>(ID);

    private static final Gson GSON = new GsonBuilder().create();

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.of(
            ConfigSyncPayload::write,
            ConfigSyncPayload::read
    );

    public static void write(FriendlyByteBuf buf, ConfigSyncPayload p) {
        buf.writeUtf(GSON.toJson(p.config));
    }

    public static ConfigSyncPayload read(FriendlyByteBuf buf) {
        String json = buf.readUtf();
        TDConfig config = GSON.fromJson(json, TDConfig.class);
        return new ConfigSyncPayload(config);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
