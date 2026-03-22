package com.towerdefense.arena;

/** Shared numeric constants for arena generation. */
public final class ArenaConstants {
    private ArenaConstants() {}

    // ── Stand / ring geometry ────────────────────────────────────────────────
    public static final int INNER_WALL_HEIGHT = 5;
    public static final int STAND_ROWS        = 10;
    public static final int RING_BASE_OFFSET  = 2;   // ringOffset = RING_BASE_OFFSET + STAND_ROWS

    /** Top of the bleachers / base of the parapet (= INNER_WALL_HEIGHT + 1 + STAND_ROWS). */
    public static final int STAND_TOP_Y       = INNER_WALL_HEIGHT + 1 + STAND_ROWS;          // 16
    /** Outward offset of the top ring and velarium poles (= RING_BASE_OFFSET + STAND_ROWS). */
    public static final int STAND_RING_OFFSET = RING_BASE_OFFSET + STAND_ROWS;               // 12

    // ── Inner wall ───────────────────────────────────────────────────────────
    public static final int ARCH_SPACING    = 8;
    public static final int ARCH_WIDTH      = 3;
    public static final int ARCH_EDGE_MARGIN = 4;

    // ── Velarium ─────────────────────────────────────────────────────────────
    public static final int CANOPY_DEPTH          = 8;
    public static final int VELARIUM_FENCE_SPACING = 6;
    public static final int CANOPY_ABOVE_TOP       = 10;   // canopyY = STAND_TOP_Y + CANOPY_ABOVE_TOP

    // ── Grand entrances ──────────────────────────────────────────────────────
    public static final int ENTRANCE_TIERS       = 4;
    public static final int ENTRANCE_TIER_HEIGHT = 3;
    public static final int ENTRANCE_TIER_DEPTH  = 3;
    public static final int GATE_WIDTH           = 5;

    /** Top-Y of entrance structure (= INNER_WALL_HEIGHT + 1 + ENTRANCE_TIERS * ENTRANCE_TIER_HEIGHT). */
    public static final int ENTRANCE_TOP_Y = INNER_WALL_HEIGHT + 1 + ENTRANCE_TIERS * ENTRANCE_TIER_HEIGHT; // 18

    // ── Parapet / corner tower ────────────────────────────────────────────────
    /** Height of quartz pillar columns in the top-ring parapet. */
    public static final int PARAPET_PILLAR_HEIGHT = 4;
    /** Y offset above topY where lanterns and corner-tower tops are placed (pillar + slab). */
    public static final int LANTERN_ABOVE_TOP     = 6;   // PARAPET_PILLAR_HEIGHT + slab (1) + lantern (1)

    // ── Decorations ──────────────────────────────────────────────────────────
    public static final int RAIL_HEIGHT    = 6;
    public static final int TORCH_SPACING  = 4;
    public static final int COLUMN_SPACING = 6;
    public static final int MERLON_SPACING = 3;
    public static final int BANNER_SPACING = 8;
}
