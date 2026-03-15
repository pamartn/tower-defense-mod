package com.towerdefense.mob;

import com.towerdefense.TowerDefenseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.slf4j.Logger;

import java.util.EnumSet;

public class MoveToNexusGoal extends Goal {

    private static final Logger LOGGER = TowerDefenseMod.LOGGER;

    private final Mob mob;
    private final BlockPos nexusPos;
    private final double speed;
    private int recalcCooldown;
    private int debugTicks = 0;

    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public MoveToNexusGoal(Mob mob, BlockPos nexusPos, double speed) {
        this.mob = mob;
        this.nexusPos = nexusPos;
        this.speed = speed;
        this.targetX = nexusPos.getX() + 0.5;
        this.targetY = nexusPos.getY();
        this.targetZ = nexusPos.getZ() + 0.5;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        boolean canUse = mob.isAlive();
        if (debugTicks == 0) {
            LOGGER.info("[DEBUG] MoveToNexusGoal.canUse() mobId={} alive={}", mob.getId(), canUse);
        }
        return canUse;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isAlive() && mob.distanceToSqr(targetX, targetY, targetZ) > 4.0;
    }

    @Override
    public void start() {
        recalcCooldown = 0;
        LOGGER.info("[DEBUG] MoveToNexusGoal.start() mobId={} at ({}, {}, {}) target=({}, {}, {})",
            mob.getId(), mob.getX(), mob.getY(), mob.getZ(), targetX, targetY, targetZ);
    }

    @Override
    public void tick() {
        debugTicks++;
        boolean navigationIdle = mob.getNavigation().isDone();

        if (debugTicks % 20 == 0) {
            LOGGER.info("[DEBUG] Mob {} at ({}, {}, {}) nav.isDone={}, path={}, onGround={}",
                mob.getId(),
                String.format("%.1f", mob.getX()),
                String.format("%.1f", mob.getY()),
                String.format("%.1f", mob.getZ()),
                navigationIdle,
                mob.getNavigation().getPath() != null ? "exists" : "null",
                mob.onGround());
        }

        recalcCooldown--;
        if (recalcCooldown <= 0 || navigationIdle) {
            recalcCooldown = 10;

            boolean pathFound = mob.getNavigation().moveTo(targetX, targetY, targetZ, 1.0);

            if (!pathFound) {
                LOGGER.warn("[DEBUG] Mob {} pathfinding FAILED at ({}, {}, {}), using MoveControl fallback",
                    mob.getId(),
                    String.format("%.1f", mob.getX()),
                    String.format("%.1f", mob.getY()),
                    String.format("%.1f", mob.getZ()));
                mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 1.0);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
