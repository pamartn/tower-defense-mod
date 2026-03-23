package com.towerdefense.mob;

import com.towerdefense.pathfinding.PathValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/**
 * Guides a mob toward the enemy nexus using BFS waypoints computed from the live
 * world state, so all solid blocks (towers, walls, any player-placed block) are
 * treated as obstacles.  The path is recomputed periodically and immediately when
 * the current navigation segment finishes.
 *
 * <p>Vanilla {@code moveTo()} is given a waypoint at most {@link #WAYPOINT_LOOKAHEAD}
 * steps ahead — always within the pathfinder's effective range — so no MoveControl
 * fallback is needed.
 */
public class MoveToNexusGoal extends Goal {

    /** Re-compute the BFS path every N ticks (also triggered when nav segment finishes). */
    private static final int RECALC_INTERVAL = 20;

    /**
     * How many BFS steps ahead to target. Keeping this below Minecraft's default
     * pathfinder range (~16 blocks) ensures {@code moveTo()} always succeeds.
     */
    private static final int WAYPOINT_LOOKAHEAD = 12;

    private final Mob mob;
    private final BlockPos nexusPos;
    private final double speed;

    private int recalcTimer;
    private List<BlockPos> waypoints;

    public MoveToNexusGoal(Mob mob, BlockPos nexusPos, double speed) {
        this.mob = mob;
        this.nexusPos = nexusPos;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isAlive() &&
               mob.distanceToSqr(nexusPos.getX() + 0.5, nexusPos.getY(), nexusPos.getZ() + 0.5) > 4.0;
    }

    @Override
    public void start() {
        recalcTimer = 0;
        waypoints = null;
    }

    @Override
    public void tick() {
        // Trigger immediate recalc when the current nav segment finishes
        if (mob.getNavigation().isDone()) recalcTimer = 0;

        if (--recalcTimer > 0) return;
        recalcTimer = RECALC_INTERVAL;

        ServerLevel level = (ServerLevel) mob.level();
        waypoints = new PathValidator().computePath(level, mob.blockPosition(), nexusPos);

        BlockPos target;
        if (waypoints != null && waypoints.size() > 1) {
            // Pick the waypoint LOOKAHEAD steps ahead (or the last one when close to nexus)
            target = waypoints.get(Math.min(WAYPOINT_LOOKAHEAD, waypoints.size() - 1));
        } else {
            // BFS found no path (fully blocked by non-mod blocks) — let vanilla try directly
            target = nexusPos;
        }

        mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
