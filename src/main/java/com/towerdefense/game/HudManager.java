package com.towerdefense.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class HudManager {

    private static final String OBJ_NAME = "td_hud";
    private Objective objective;

    private long prevHash = -1;

    public void setup(Scoreboard scoreboard) {
        cleanup(scoreboard);

        objective = scoreboard.addObjective(
                OBJ_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("\u2694 TD PvP").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER,
                true,
                BlankFormat.INSTANCE
        );
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
    }

    public void update(Scoreboard scoreboard,
                        int p1Money, int p1Hp, int p1MaxHp, int p1Spawners, int p1Gens, int p1IncomeRate,
                        int p2Money, int p2Hp, int p2MaxHp, int p2Spawners, int p2Gens, int p2IncomeRate,
                        int prepTimeSeconds) {
        update(scoreboard, p1Money, p1Hp, p1MaxHp, p1Spawners, p1Gens, p1IncomeRate,
                p2Money, p2Hp, p2MaxHp, p2Spawners, p2Gens, p2IncomeRate, prepTimeSeconds, false);
    }

    public void update(Scoreboard scoreboard,
                        int p1Money, int p1Hp, int p1MaxHp, int p1Spawners, int p1Gens, int p1IncomeRate,
                        int p2Money, int p2Hp, int p2MaxHp, int p2Spawners, int p2Gens, int p2IncomeRate,
                        int prepTimeSeconds, boolean soloMode) {
        if (objective == null) return;

        long hash = hash(p1Money, p1Hp, p1MaxHp, p1Spawners, p1Gens, p1IncomeRate,
                          p2Money, p2Hp, p2MaxHp, p2Spawners, p2Gens, p2IncomeRate, prepTimeSeconds);
        if (hash == prevHash) return;
        prevHash = hash;

        for (var entry : scoreboard.listPlayerScores(objective)) {
            scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective);
        }

        int line = 14;

        setLine(scoreboard, ChatFormatting.AQUA + "-- " + (soloMode ? "You" : "Player 1") + " --", line--);
        setLine(scoreboard, ChatFormatting.GREEN + "  $ " + ChatFormatting.WHITE + p1Money + ChatFormatting.DARK_GREEN + " (+" + p1IncomeRate + "/10s)", line--);
        ChatFormatting hp1Color = hpColor(p1Hp, p1MaxHp);
        setLine(scoreboard, ChatFormatting.RED + "  \u2764 Nexus: " + hp1Color + p1Hp + "/" + p1MaxHp, line--);
        setLine(scoreboard, ChatFormatting.GRAY + "  S:" + p1Spawners + " G:" + p1Gens, line--);

        setLine(scoreboard, " ", line--);

        setLine(scoreboard, ChatFormatting.LIGHT_PURPLE + "-- " + (soloMode ? "AI" : "Player 2") + " --", line--);
        setLine(scoreboard, ChatFormatting.GREEN + "  $ " + ChatFormatting.WHITE + p2Money + ChatFormatting.DARK_GREEN + " (+" + p2IncomeRate + "/10s)", line--);
        ChatFormatting hp2Color = hpColor(p2Hp, p2MaxHp);
        setLine(scoreboard, ChatFormatting.RED + "  \u2764 Nexus: " + hp2Color + p2Hp + "/" + p2MaxHp, line--);
        setLine(scoreboard, ChatFormatting.GRAY + "  S:" + p2Spawners + " G:" + p2Gens, line--);

        if (prepTimeSeconds >= 0) {
            setLine(scoreboard, "  ", line--);
            setLine(scoreboard, ChatFormatting.YELLOW + "Prep: " + ChatFormatting.WHITE + prepTimeSeconds + "s", line--);
        }
    }

    private long hash(int... values) {
        long h = 0;
        for (int v : values) h = h * 31 + v;
        return h;
    }

    private ChatFormatting hpColor(int hp, int maxHp) {
        if (maxHp <= 0) return ChatFormatting.GRAY;
        float ratio = (float) hp / maxHp;
        return ratio > 0.6f ? ChatFormatting.GREEN : ratio > 0.3f ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    private void setLine(Scoreboard scoreboard, String text, int score) {
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(text), objective).set(score);
    }

    public void cleanup(Scoreboard scoreboard) {
        Objective existing = scoreboard.getObjective(OBJ_NAME);
        if (existing != null) scoreboard.removeObjective(existing);

        Objective old1 = scoreboard.getObjective("td_hud_1");
        if (old1 != null) scoreboard.removeObjective(old1);
        Objective old2 = scoreboard.getObjective("td_hud_2");
        if (old2 != null) scoreboard.removeObjective(old2);

        objective = null;
        prevHash = -1;
    }

    public static void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (title != null) player.connection.send(new ClientboundSetTitleTextPacket(title));
        if (subtitle != null) player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    public static void sendActionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}
