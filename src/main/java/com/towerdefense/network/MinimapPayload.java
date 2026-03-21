package com.towerdefense.network;

import com.towerdefense.TowerDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client packet sent every 10 ticks with mob positions for the minimap.
 * Positions are relative to the arena origin (0..arenaSize-1).
 */
public record MinimapPayload(
        int arenaOriginX, int arenaOriginZ, int arenaSize,
        int playerTeamId,
        List<int[]> ownMobs,
        List<int[]> enemyMobs
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "minimap");
    public static final Type<MinimapPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, MinimapPayload> CODEC =
            StreamCodec.of(MinimapPayload::write, MinimapPayload::read);

    public static void write(FriendlyByteBuf buf, MinimapPayload p) {
        buf.writeInt(p.arenaOriginX);
        buf.writeInt(p.arenaOriginZ);
        buf.writeInt(p.arenaSize);
        buf.writeInt(p.playerTeamId);
        writeMobList(buf, p.ownMobs);
        writeMobList(buf, p.enemyMobs);
    }

    public static MinimapPayload read(FriendlyByteBuf buf) {
        int x = buf.readInt(), z = buf.readInt(), size = buf.readInt(), team = buf.readInt();
        List<int[]> own = readMobList(buf);
        List<int[]> enemy = readMobList(buf);
        return new MinimapPayload(x, z, size, team, own, enemy);
    }

    private static void writeMobList(FriendlyByteBuf buf, List<int[]> mobs) {
        buf.writeInt(mobs.size());
        for (int[] pos : mobs) {
            buf.writeShort(pos[0]);
            buf.writeShort(pos[1]);
        }
    }

    private static List<int[]> readMobList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<int[]> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new int[]{buf.readShort(), buf.readShort()});
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
