package com.towerdefense.wave;

import net.minecraft.world.entity.Entity;

public final class MobTags {
    public static final String MOB = "td_mob";
    public static final String AI_VILLAGER = "td_ai_villager";

    public static String typeTag(String type) { return "td_type_" + type; }
    public static String ownerTag(int teamId) { return "td_owner_" + teamId; }

    /** Returns the team id from the entity's td_owner_ tag, or -1 if absent. */
    public static int getTeamId(Entity e) {
        for (String tag : e.getTags()) {
            if (tag.startsWith("td_owner_")) {
                try { return Integer.parseInt(tag.substring(9)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    /** Returns the mob type name (e.g. "ZOMBIE") from the entity's td_type_ tag, or null if absent. */
    public static String getType(Entity e) {
        for (String tag : e.getTags()) {
            if (tag.startsWith("td_type_")) return tag.substring(8);
        }
        return null;
    }

    /** True if the entity belongs to teamId. */
    public static boolean isAllyOf(Entity e, int teamId) {
        return e.getTags().contains(ownerTag(teamId));
    }

    private MobTags() {}
}
