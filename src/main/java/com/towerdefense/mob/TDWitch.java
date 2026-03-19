package com.towerdefense.mob;

import com.towerdefense.config.ConfigManager;
import com.towerdefense.wave.MobTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TD behavior for a witch mob: periodic heal aura affecting nearby allies.
 *
 * <p>Heal amount = witchHealPercent% of target max HP, decayed by witchHealDecayFactor^n
 * where n = number of consecutive cycles the target has already been healed. Floors at
 * witchHealMinPercent%, always at least 1 HP. Witch-to-witch heals are debuffed to 25%.
 *
 * <p>The static maps are shared across all TDWitch instances so that:
 * <ul>
 *   <li>A target is healed at most once per cycle regardless of how many witches cover it.</li>
 *   <li>The consecutive-cycle decay is tracked globally per target UUID.</li>
 * </ul>
 */
public class TDWitch extends TDMob {

    // Shared across all TDWitch instances — keyed by target mob UUID
    private static final Map<UUID, Long> lastHealedAt     = new HashMap<>();
    private static final Map<UUID, Integer> consecutiveCounts = new HashMap<>();

    private int cooldown;

    public TDWitch(Mob entity, int teamId) {
        super(entity, teamId);
        this.cooldown = ConfigManager.getInstance().getWitchHealIntervalTicks();
    }

    @Override
    protected void tick(ServerLevel level) {
        if (--cooldown > 0) return;
        cooldown = ConfigManager.getInstance().getWitchHealIntervalTicks();
        performHealPass(level, level.getGameTime());
    }

    /**
     * Executes one full heal pass for this witch.
     * Public so tests can trigger it directly without waiting for the cooldown.
     */
    public void performHealPass(ServerLevel level) {
        performHealPass(level, level.getGameTime());
    }

    /**
     * Heal pass with an explicit {@code gameTime}, allowing tests to simulate
     * multiple consecutive cycles by passing synthetic timestamps.
     */
    public void performHealPass(ServerLevel level, long gameTime) {
        double basePercent = ConfigManager.getInstance().getWitchHealPercent();
        double minPercent  = ConfigManager.getInstance().getWitchHealMinPercent();
        double decayFactor = ConfigManager.getInstance().getWitchHealDecayFactor();
        int    boxSize     = ConfigManager.getInstance().getWitchHealBoxSize();
        long   interval    = ConfigManager.getInstance().getWitchHealIntervalTicks();

        AABB healBox = AABB.ofSize(entity.position(), boxSize, 4, boxSize);

        for (Entity e : level.getEntities((Entity) null, healBox,
                ent -> ent instanceof LivingEntity && ent.isAlive()
                        && ent.getTags().contains(MobTags.MOB))) {

            if (!MobTags.isAllyOf(e, teamId)) continue;

            LivingEntity target = (LivingEntity) e;
            UUID uuid = target.getUUID();

            // One heal per target per cycle across ALL witches
            Long prev = lastHealedAt.get(uuid);
            if (prev != null && (gameTime - prev) < interval) continue;

            // Consecutive decay: reset count if the target missed a cycle
            int consecutive = consecutiveCounts.getOrDefault(uuid, 0);
            if (prev == null || (gameTime - prev) > interval * 1.5) {
                consecutive = 0;
            }

            double effectivePercent = Math.max(minPercent,
                    basePercent * Math.pow(decayFactor, consecutive));

            // Witch-to-witch heals debuffed — prevents witch clusters being unkillable
            if (target.getTags().contains(MobTags.typeTag("WITCH"))) {
                effectivePercent *= 0.25;
            }

            float healAmount = Math.max(1f, (float) (target.getMaxHealth() * effectivePercent));
            float hpBefore = target.getHealth();
            target.heal(healAmount);
            boolean actuallyHealed = target.getHealth() > hpBefore;

            // Always mark as attempted so other witches skip this target this cycle
            lastHealedAt.put(uuid, gameTime);
            // Only count toward decay if HP actually increased — a full-health mob
            // should not be penalized when it gets wounded later in the same wave
            if (actuallyHealed) {
                consecutiveCounts.put(uuid, consecutive + 1);
            }
        }

        // Prune entries for mobs not seen in 3+ cycles to keep the maps bounded
        lastHealedAt.entrySet().removeIf(entry -> gameTime - entry.getValue() > interval * 3);
        consecutiveCounts.keySet().retainAll(lastHealedAt.keySet());
    }

    /**
     * Resets all shared heal state.
     * Call between tests or on game reset to prevent state leaking between sessions.
     */
    public static void resetHealState() {
        lastHealedAt.clear();
        consecutiveCounts.clear();
    }
}
