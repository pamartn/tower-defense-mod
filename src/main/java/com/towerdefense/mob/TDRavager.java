package com.towerdefense.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** TD behavior for a ravager mob: block-breaching on jump. */
public class TDRavager extends TDMob {

    private final RavagerBreachBehavior breachBehavior = new RavagerBreachBehavior();

    public TDRavager(Mob entity, int teamId) {
        super(entity, teamId);
    }

    @Override
    protected void tick(ServerLevel level) {
        breachBehavior.tick(entity, level);
    }
}
