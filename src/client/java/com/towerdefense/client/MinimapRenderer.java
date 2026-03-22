package com.towerdefense.client;

import com.towerdefense.network.MinimapPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Renders a 320×320 minimap in the top-right corner.
 * Shows walls, towers, nexus, mob paths (dotted) and mob positions.
 */
public class MinimapRenderer {

    private static final int MAP_SIZE   = 320;
    private static final int PADDING    = 6;
    private static final int BORDER     = 1;

    // Dot sizes (px)
    private static final int MOB_DOT       = 4;
    private static final int PATH_DOT      = 2;
    private static final int WALL_DOT      = 3;
    private static final int TOWER_DOT     = 5;
    private static final int SPAWNER_DOT   = 5;
    private static final int GENERATOR_DOT = 5;
    private static final int NEXUS_DOT     = 9;

    // Colors
    private static final int COL_BG              = 0x88111111;
    private static final int COL_BORDER          = 0x99000000;
    private static final int COL_MIDLINE         = 0x66FFFFFF;
    private static final int COL_OWN_MOB         = 0xFF33FF33;
    private static final int COL_ENEMY_MOB       = 0xFFFF3333;
    private static final int COL_OWN_PATH        = 0x9933CC33;
    private static final int COL_ENEMY_PATH      = 0x99CC3333;
    private static final int COL_OWN_WALL        = 0xFF4488FF;
    private static final int COL_ENEMY_WALL      = 0xFFFF8844;
    private static final int COL_OWN_TOWER       = 0xFF0055FF;
    private static final int COL_ENEMY_TOWER     = 0xFFFF5500;
    private static final int COL_OWN_SPAWNER     = 0xFFCC44FF;
    private static final int COL_ENEMY_SPAWNER   = 0xFFFF44CC;
    private static final int COL_OWN_GENERATOR   = 0xFFFFDD00;
    private static final int COL_ENEMY_GENERATOR = 0xFFFF9900;
    private static final int COL_OWN_NEXUS       = 0xFF00FFAA;
    private static final int COL_ENEMY_NEXUS     = 0xFFFF0055;

    private static volatile MinimapPayload last = null;

    public static void update(MinimapPayload payload) { last = payload; }
    public static void clear() { last = null; }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> render(graphics));
    }

    private static void render(GuiGraphics g) {
        MinimapPayload d = last;
        if (d == null || d.arenaSize() <= 0) return;

        int mapX = PADDING + BORDER;
        int mapY = PADDING + BORDER;

        // Border + background
        g.fill(mapX - BORDER, mapY - BORDER, mapX + MAP_SIZE + BORDER, mapY + MAP_SIZE + BORDER, COL_BORDER);
        g.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, COL_BG);

        // Midline
        int midZ = mapY + MAP_SIZE / 2;
        for (int x = mapX; x < mapX + MAP_SIZE; x += 6) {
            g.fill(x, midZ, x + 3, midZ + 1, COL_MIDLINE);
        }

        int size = d.arenaSize();

        // Structures
        drawXZList(g, d.ownWalls(),       size, mapX, mapY, WALL_DOT,      COL_OWN_WALL);
        drawXZList(g, d.enemyWalls(),     size, mapX, mapY, WALL_DOT,      COL_ENEMY_WALL);
        drawXZList(g, d.ownTowers(),      size, mapX, mapY, TOWER_DOT,     COL_OWN_TOWER);
        drawXZList(g, d.enemyTowers(),    size, mapX, mapY, TOWER_DOT,     COL_ENEMY_TOWER);
        drawXZList(g, d.ownSpawners(),    size, mapX, mapY, SPAWNER_DOT,   COL_OWN_SPAWNER);
        drawXZList(g, d.enemySpawners(),  size, mapX, mapY, SPAWNER_DOT,   COL_ENEMY_SPAWNER);
        drawXZList(g, d.ownGenerators(),  size, mapX, mapY, GENERATOR_DOT, COL_OWN_GENERATOR);
        drawXZList(g, d.enemyGenerators(),size, mapX, mapY, GENERATOR_DOT, COL_ENEMY_GENERATOR);

        // Nexus
        drawDot(g, d.ownNexus(),   size, mapX, mapY, NEXUS_DOT, COL_OWN_NEXUS);
        drawDot(g, d.enemyNexus(), size, mapX, mapY, NEXUS_DOT, COL_ENEMY_NEXUS);

        // Paths (dotted)
        for (List<int[]> path : d.ownPaths())   drawDottedPath(g, path, size, mapX, mapY, COL_OWN_PATH);
        for (List<int[]> path : d.enemyPaths()) drawDottedPath(g, path, size, mapX, mapY, COL_ENEMY_PATH);

        // Mobs (on top of everything)
        drawXZList(g, d.ownMobs(),   size, mapX, mapY, MOB_DOT, COL_OWN_MOB);
        drawXZList(g, d.enemyMobs(), size, mapX, mapY, MOB_DOT, COL_ENEMY_MOB);

        // Money display — bottom-right, scale 2
        drawMoney(g, d.ownMoney());
    }

    private static void drawMoney(GuiGraphics g, int money) {
        var mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        String text = "$" + money;
        int textW = mc.font.width(text);

        float scale = 2.5f;
        int scaledW = (int)(textW * scale);
        int scaledH = (int)(mc.font.lineHeight * scale);

        int pad    = 6;   // inner padding between text and box edge
        int margin = 12;  // gap between box and screen edge

        int bgX = screenW - scaledW - pad * 2 - margin;
        int bgY = screenH - scaledH - pad * 2 - margin;

        // Background
        g.fill(bgX, bgY, screenW - margin, screenH - margin, 0xAA000000);

        // Text at 2.5× scale
        g.pose().pushPose();
        g.pose().translate(bgX + pad, bgY + pad, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(mc.font, text, 0, 0, 0xFFFFDD00, true);
        g.pose().popPose();
    }

    private static void drawXZList(GuiGraphics g, List<int[]> list, int arenaSize,
                                    int mapX, int mapY, int dotSize, int color) {
        for (int[] pos : list) drawDot(g, pos, arenaSize, mapX, mapY, dotSize, color);
    }

    private static void drawDot(GuiGraphics g, int[] pos, int arenaSize,
                                 int mapX, int mapY, int dotSize, int color) {
        if (pos == null) return;
        int px = mapX + pos[0] * MAP_SIZE / arenaSize;
        int pz = mapY + pos[1] * MAP_SIZE / arenaSize;
        int half = dotSize / 2;
        px = Math.max(mapX, Math.min(mapX + MAP_SIZE - dotSize, px - half));
        pz = Math.max(mapY, Math.min(mapY + MAP_SIZE - dotSize, pz - half));
        g.fill(px, pz, px + dotSize, pz + dotSize, color);
    }

    /** Draws dotted lines between consecutive path nodes. */
    private static void drawDottedPath(GuiGraphics g, List<int[]> nodes, int arenaSize,
                                        int mapX, int mapY, int color) {
        if (nodes.size() < 2) return;
        int[] prev = null;
        for (int[] node : nodes) {
            int px = mapX + node[0] * MAP_SIZE / arenaSize;
            int pz = mapY + node[1] * MAP_SIZE / arenaSize;
            if (prev != null) {
                int dx = px - prev[0], dz = pz - prev[1];
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                int steps = Math.max(1, (int) (len / 6)); // dot every 6px
                for (int s = 0; s <= steps; s++) {
                    int ix = prev[0] + dx * s / steps;
                    int iz = prev[1] + dz * s / steps;
                    // clip to map bounds
                    ix = Math.max(mapX, Math.min(mapX + MAP_SIZE - PATH_DOT, ix));
                    iz = Math.max(mapY, Math.min(mapY + MAP_SIZE - PATH_DOT, iz));
                    g.fill(ix, iz, ix + PATH_DOT, iz + PATH_DOT, color);
                }
            }
            prev = new int[]{px, pz};
        }
    }
}
