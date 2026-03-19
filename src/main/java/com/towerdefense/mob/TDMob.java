package com.towerdefense.mob;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wrapper around a vanilla {@link Mob} that owns all TD-specific per-mob behavior.
 *
 * <p>Subclasses self-register to {@link ServerTickEvents#END_SERVER_TICK} on construction
 * and self-prune when the wrapped entity dies. SpawnerManager carries no knowledge of the
 * tick lifecycle after the point of creation — it just calls {@code new TDWitch(mob, team)}.
 *
 * <p>The IS-A relationship between TD mobs and vanilla Minecraft mob classes is intentionally
 * avoided: a TD ravager doesn't behave like a vanilla ravager; it only looks like one.
 * Composition (wrapping the entity) is used instead of inheritance.
 */
public abstract class TDMob {

    private static final List<TDMob> REGISTRY = new CopyOnWriteArrayList<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            REGISTRY.removeIf(m -> !m.entity.isAlive());
            for (TDMob m : REGISTRY) {
                if (m.entity.level() instanceof ServerLevel level) {
                    m.tick(level);
                }
            }
        });
    }

    protected final Mob entity;
    protected final int teamId;

    protected TDMob(Mob entity, int teamId) {
        this.entity = entity;
        this.teamId = teamId;
        REGISTRY.add(this);
    }

    /** Called every server tick while the wrapped entity is alive. */
    protected abstract void tick(ServerLevel level);

    /**
     * Forcibly clears all wrappers from the registry.
     * Call on game reset as a safety net alongside {@code killAllMobs()}.
     */
    public static void clearAll() {
        REGISTRY.clear();
    }
}
