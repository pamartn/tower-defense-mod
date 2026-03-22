package com.towerdefense.tower;

/** Numeric constants for tower attack calculations and visual effects. */
public final class TowerConstants {
    private TowerConstants() {}

    /** Y offset added to the marker position to derive the shot origin. */
    public static final double ATTACK_ORIGIN_Y         = 0.5;
    /** Fraction of target bounding-box height used to aim at the centre. */
    public static final double TARGET_CENTER_FRACTION  = 0.5;

    /** Number of END_ROD particles drawn along the laser beam. */
    public static final int    LASER_PARTICLE_COUNT    = 30;
    /** Number of CRIT particles drawn along the sniper beam. */
    public static final int    SNIPER_PARTICLE_COUNT   = 40;

    /**
     * Y delta applied to ctx.origin() (= markerY + ATTACK_ORIGIN_Y) to bring
     * the arrow spawn down to the centre of the top structural block.
     * marker is 1 block above the top block, ATTACK_ORIGIN_Y adds 0.5 → delta = -1.0.
     */
    public static final double ARROW_ORIGIN_Y_DELTA    = -1.0;
    /**
     * How many blocks the arrow origin is shifted horizontally toward the mob
     * so it spawns outside the tower's 1×1 footprint.
     */
    public static final double ARROW_ORIGIN_SHIFT      = 1.0;

    /** Delta-movement magnitude for basic arrow projectiles. */
    public static final double ARROW_SPEED             = 2.0;
    /** Delta-movement magnitude for archer double-arrow projectiles. */
    public static final double DOUBLE_ARROW_SPEED      = 2.5;
    /** Perpendicular spread distance between the two archer arrows. */
    public static final double DOUBLE_ARROW_SPREAD     = 0.3;
    /** Scale factor applied to the perpendicular offset when adjusting arrow direction. */
    public static final double DOUBLE_ARROW_OFFSET_SCALE = 0.15;
}
