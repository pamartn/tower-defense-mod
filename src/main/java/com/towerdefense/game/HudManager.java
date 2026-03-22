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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Manages the right-side scoreboard sidebar showing both players' stats.
 *
 * Uses two objectives + two team sidebar slots so each player sees
 * "You"/"Opponent" labels rather than generic "Player 1"/"Player 2".
 *
 * How the team-sidebar trick works:
 *  - Minecraft has per-team sidebar display slots (sidebar.team.COLOR).
 *  - A player sees the objective registered in the slot that matches their
 *    scoreboard team's color.
 *  - Registering both objectives in distinct team slots adds both to
 *    ServerScoreboard.trackedObjectives, so score update packets are
 *    broadcast automatically (unlike the per-player packet approach).
 */
public class HudManager {

    private static final String OBJ_T1   = "td_hud_t1";
    private static final String OBJ_T2   = "td_hud_t2";
    private static final String TEAM_T1  = "td_team1";
    private static final String TEAM_T2  = "td_team2";

    // Team colors — must be distinct; their sidebar slots are used for display.
    private static final ChatFormatting COLOR_T1 = ChatFormatting.AQUA;
    private static final ChatFormatting COLOR_T2 = ChatFormatting.LIGHT_PURPLE;

    private Objective objT1;
    private Objective objT2;

    private long prevHash = -1;

    public void setup(Scoreboard scoreboard) {
        cleanup(scoreboard);

        Component title = Component.literal("\u2694 TD PvP")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        objT1 = scoreboard.addObjective(OBJ_T1, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);
        objT2 = scoreboard.addObjective(OBJ_T2, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);

        // Register each objective in its team's sidebar slot.
        // This adds both to trackedObjectives so score packets are broadcast.
        scoreboard.setDisplayObjective(DisplaySlot.teamColorToSlot(COLOR_T1), objT1);
        scoreboard.setDisplayObjective(DisplaySlot.teamColorToSlot(COLOR_T2), objT2);

        PlayerTeam team1 = scoreboard.addPlayerTeam(TEAM_T1);
        team1.setColor(COLOR_T1);
        PlayerTeam team2 = scoreboard.addPlayerTeam(TEAM_T2);
        team2.setColor(COLOR_T2);
    }

    /** Assign the player to their team so they see the correct sidebar. */
    public void setupPlayer(Scoreboard scoreboard, ServerPlayer player, int side) {
        String teamName = side == 1 ? TEAM_T1 : TEAM_T2;
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team != null) scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }

    /** Overload kept for call sites that don't pass the scoreboard. */
    public void setupPlayer(ServerPlayer player, int side) {}

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
        if (objT1 == null) return;

        long hash = hash(p1Money, p1Hp, p1MaxHp, p1Spawners, p1Gens, p1IncomeRate,
                p2Money, p2Hp, p2MaxHp, p2Spawners, p2Gens, p2IncomeRate, prepTimeSeconds);
        if (hash == prevHash) return;
        prevHash = hash;

        String oppLabel = soloMode ? "AI" : "Opponent";

        // Team 1's perspective: "You" = team1, opponent = team2
        writeObjective(scoreboard, objT1,
                p1Money, p1Hp, p1MaxHp, p1Spawners, p1Gens, p1IncomeRate,
                p2Money, p2Hp, p2MaxHp, p2Spawners, p2Gens, p2IncomeRate,
                prepTimeSeconds, oppLabel);

        // Team 2's perspective: "You" = team2, opponent = team1
        writeObjective(scoreboard, objT2,
                p2Money, p2Hp, p2MaxHp, p2Spawners, p2Gens, p2IncomeRate,
                p1Money, p1Hp, p1MaxHp, p1Spawners, p1Gens, p1IncomeRate,
                prepTimeSeconds, oppLabel);
    }

    private void writeObjective(Scoreboard scoreboard, Objective obj,
                                int youMoney, int youHp, int youMaxHp, int youSpawners, int youGens, int youIncome,
                                int oppMoney, int oppHp, int oppMaxHp, int oppSpawners, int oppGens, int oppIncome,
                                int prepTimeSeconds, String oppLabel) {
        for (var entry : scoreboard.listPlayerScores(obj)) {
            scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry.owner()), obj);
        }

        int line = 14;

        setLine(scoreboard, obj, ChatFormatting.AQUA + "-- You --", line--);
        setLine(scoreboard, obj, ChatFormatting.GREEN + "  $ " + ChatFormatting.WHITE + youMoney
                + ChatFormatting.DARK_GREEN + " (+" + youIncome + "/10s)", line--);
        setLine(scoreboard, obj, ChatFormatting.RED + "  \u2764 Nexus: " + hpColor(youHp, youMaxHp) + youHp + "/" + youMaxHp, line--);
        setLine(scoreboard, obj, ChatFormatting.GRAY + "  S:" + youSpawners + " G:" + youGens, line--);

        setLine(scoreboard, obj, " ", line--);

        setLine(scoreboard, obj, ChatFormatting.LIGHT_PURPLE + "-- " + oppLabel + " --", line--);
        setLine(scoreboard, obj, ChatFormatting.GREEN + "  $ " + ChatFormatting.WHITE + oppMoney
                + ChatFormatting.DARK_GREEN + " (+" + oppIncome + "/10s)", line--);
        setLine(scoreboard, obj, ChatFormatting.RED + "  \u2764 Nexus: " + hpColor(oppHp, oppMaxHp) + oppHp + "/" + oppMaxHp, line--);
        setLine(scoreboard, obj, ChatFormatting.GRAY + "  S:" + oppSpawners + " G:" + oppGens, line--);

        if (prepTimeSeconds >= 0) {
            setLine(scoreboard, obj, "  ", line--);
            setLine(scoreboard, obj, ChatFormatting.YELLOW + "Prep: " + ChatFormatting.WHITE + prepTimeSeconds + "s", line--);
        }
    }

    private void setLine(Scoreboard scoreboard, Objective obj, String text, int score) {
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(text), obj).set(score);
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

    public void cleanup(Scoreboard scoreboard) {
        removeObj(scoreboard, OBJ_T1);
        removeObj(scoreboard, OBJ_T2);

        PlayerTeam t1 = scoreboard.getPlayerTeam(TEAM_T1);
        if (t1 != null) scoreboard.removePlayerTeam(t1);
        PlayerTeam t2 = scoreboard.getPlayerTeam(TEAM_T2);
        if (t2 != null) scoreboard.removePlayerTeam(t2);

        // Legacy names from previous versions
        for (String legacy : new String[]{"td_hud", "td_hud_1", "td_hud_2"}) {
            removeObj(scoreboard, legacy);
        }

        objT1 = null;
        objT2 = null;
        prevHash = -1;
    }

    private void removeObj(Scoreboard scoreboard, String name) {
        Objective obj = scoreboard.getObjective(name);
        if (obj != null) scoreboard.removeObjective(obj);
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
