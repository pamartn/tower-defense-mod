package com.towerdefense.network;

import com.towerdefense.TowerDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client minimap packet sent every 10 ticks.
 * All positions are relative to arena origin (0..arenaSize-1).
 */
public record MinimapPayload(
        int arenaOriginX, int arenaOriginZ, int arenaSize,
        int playerTeamId,
        // Mobs
        List<int[]> ownMobs,
        List<int[]> enemyMobs,
        // Mob paths: each element is a list of [x,z] waypoints for one mob
        List<List<int[]>> ownPaths,
        List<List<int[]>> enemyPaths,
        // Structures
        List<int[]> ownWalls,
        List<int[]> enemyWalls,
        List<int[]> ownTowers,
        List<int[]> enemyTowers,
        List<int[]> ownSpawners,
        List<int[]> enemySpawners,
        List<int[]> ownGenerators,
        List<int[]> enemyGenerators,
        int[] ownNexus,    // [x,z]
        int[] enemyNexus,  // [x,z]
        int ownMoney
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
        writeXZList(buf, p.ownMobs);
        writeXZList(buf, p.enemyMobs);
        writePathList(buf, p.ownPaths);
        writePathList(buf, p.enemyPaths);
        writeXZList(buf, p.ownWalls);
        writeXZList(buf, p.enemyWalls);
        writeXZList(buf, p.ownTowers);
        writeXZList(buf, p.enemyTowers);
        writeXZList(buf, p.ownSpawners);
        writeXZList(buf, p.enemySpawners);
        writeXZList(buf, p.ownGenerators);
        writeXZList(buf, p.enemyGenerators);
        writeXZ(buf, p.ownNexus);
        writeXZ(buf, p.enemyNexus);
        buf.writeInt(p.ownMoney);
    }

    public static MinimapPayload read(FriendlyByteBuf buf) {
        int ox = buf.readInt(), oz = buf.readInt(), size = buf.readInt(), team = buf.readInt();
        List<int[]> om = readXZList(buf), em = readXZList(buf);
        List<List<int[]>> op = readPathList(buf), ep = readPathList(buf);
        List<int[]> ow = readXZList(buf), ew = readXZList(buf);
        List<int[]> ot = readXZList(buf), et = readXZList(buf);
        List<int[]> osp = readXZList(buf), esp = readXZList(buf);
        List<int[]> og = readXZList(buf), eg = readXZList(buf);
        int[] on = readXZ(buf), en = readXZ(buf);
        int money = buf.readInt();
        return new MinimapPayload(ox, oz, size, team, om, em, op, ep, ow, ew, ot, et, osp, esp, og, eg, on, en, money);
    }

    private static void writeXZ(FriendlyByteBuf buf, int[] pos) {
        buf.writeShort(pos != null ? pos[0] : 0);
        buf.writeShort(pos != null ? pos[1] : 0);
    }

    private static int[] readXZ(FriendlyByteBuf buf) {
        return new int[]{buf.readShort(), buf.readShort()};
    }

    private static void writeXZList(FriendlyByteBuf buf, List<int[]> list) {
        buf.writeInt(list.size());
        for (int[] pos : list) { buf.writeShort(pos[0]); buf.writeShort(pos[1]); }
    }

    private static List<int[]> readXZList(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<int[]> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new int[]{buf.readShort(), buf.readShort()});
        return list;
    }

    private static void writePathList(FriendlyByteBuf buf, List<List<int[]>> paths) {
        buf.writeInt(paths.size());
        for (List<int[]> path : paths) writeXZList(buf, path);
    }

    private static List<List<int[]>> readPathList(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<List<int[]>> paths = new ArrayList<>(n);
        for (int i = 0; i < n; i++) paths.add(readXZList(buf));
        return paths;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
