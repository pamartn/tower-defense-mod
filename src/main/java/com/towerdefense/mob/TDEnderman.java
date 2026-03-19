package com.towerdefense.mob;

import com.towerdefense.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** TD behavior for an enderman mob: periodic random short-range teleport. */
public class TDEnderman extends TDMob {

    private int cooldown;

    public TDEnderman(Mob entity, int teamId) {
        super(entity, teamId);
        this.cooldown = ConfigManager.getInstance().getSpecialMobTickInterval();
    }

    @Override
    protected void tick(ServerLevel level) {
        if (--cooldown > 0) return;
        cooldown = ConfigManager.getInstance().getSpecialMobTickInterval();

        double range = ConfigManager.getInstance().getEndermanTeleportRange();
        double rx = entity.getX() + (level.random.nextDouble() - 0.5) * range;
        double rz = entity.getZ() + (level.random.nextDouble() - 0.5) * range;
        entity.teleportTo(rx, entity.getY(), rz);
    }
}
