package com.towerdefense.client;

import com.towerdefense.network.MinimapPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Renders a minimap in the top-right corner showing the arena with
 * green dots for own mobs and red dots for enemy mobs.
 */
public class MinimapRenderer {

    private static final int MAP_SIZE   = 80;  // screen pixels
    private static final int PADDING    = 4;
    private static final int BORDER     = 1;
    private static final int DOT_SIZE   = 2;

    private static volatile MinimapPayload last = null;

    public static void update(MinimapPayload payload) {
        last = payload;
    }

    public static void clear() {
        last = null;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        MinimapPayload data = last;
        if (data == null || data.arenaSize() <= 0) return;

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int mapX = screenW - MAP_SIZE - PADDING - BORDER * 2;
        int mapY = PADDING + BORDER;

        // Black border
        graphics.fill(mapX - BORDER, mapY - BORDER,
                mapX + MAP_SIZE + BORDER, mapY + MAP_SIZE + BORDER,
                0xFF000000);

        // Arena background
        graphics.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0xAA222222);

        // Midline (splits the two halves)
        int mid = mapY + MAP_SIZE / 2;
        graphics.fill(mapX, mid, mapX + MAP_SIZE, mid + 1, 0x88FFFFFF);

        // Mob dots
        int arenaSize = data.arenaSize();
        drawDots(graphics, data.ownMobs(),   arenaSize, mapX, mapY, 0xFF22EE22); // green
        drawDots(graphics, data.enemyMobs(), arenaSize, mapX, mapY, 0xFFEE2222); // red
    }

    private static void drawDots(GuiGraphics graphics, List<int[]> mobs,
                                  int arenaSize, int mapX, int mapY, int color) {
        for (int[] pos : mobs) {
            int px = mapX + Math.round((float) pos[0] / arenaSize * MAP_SIZE);
            int pz = mapY + Math.round((float) pos[1] / arenaSize * MAP_SIZE);
            px = Math.max(mapX, Math.min(mapX + MAP_SIZE - DOT_SIZE, px));
            pz = Math.max(mapY, Math.min(mapY + MAP_SIZE - DOT_SIZE, pz));
            graphics.fill(px, pz, px + DOT_SIZE, pz + DOT_SIZE, color);
        }
    }
}
