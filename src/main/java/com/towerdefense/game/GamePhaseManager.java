package com.towerdefense.game;

import com.towerdefense.arena.NexusManager;
import com.towerdefense.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * Handles all per-tick game logic for the PREP_PHASE, PLAYING, and GAME_OVER states,
 * including passive income, wave events, nexus checking, disconnect detection, and victory.
 * Holds a reference to the owning {@link GameManager} for shared state access.
 */
class GamePhaseManager {

    private final GameManager gm;

    GamePhaseManager(GameManager gm) {
        this.gm = gm;
    }

    // ─── Top-level tick dispatcher ───

    void tick() {
        switch (gm.state) {
            case PREP_PHASE -> tickPrepPhase();
            case PLAYING    -> tickPlaying();
            case GAME_OVER  -> tickGameOver();
            default -> {}
        }
    }

    // ─── Disconnect ───

    /**
     * Returns true (and acts) when all human players on one or both sides are gone.
     * Call this before the state-tick so we don't run game logic on an empty game.
     */
    boolean checkDisconnect() {
        if (gm.team1 == null || gm.team2 == null) return false;
        if (gm.gameMode == GameMode.TEST) return false;

        boolean team1Gone = gm.team1.getMemberCount() == 0 || !hasAnyAlivePlayer(gm.team1);
        boolean team2Gone = gm.gameMode == GameMode.SOLO
                ? false
                : (gm.team2.getMemberCount() == 0 || !hasAnyAlivePlayer(gm.team2));

        if (team1Gone && team2Gone) {
            gm.stopGame();
            return true;
        }
        if (team1Gone) {
            triggerVictory(gm.team2, gm.team1);
            return true;
        }
        if (team2Gone) {
            triggerVictory(gm.team1, gm.team2);
            return true;
        }
        return false;
    }

    private boolean hasAnyAlivePlayer(TeamState team) {
        for (UUID uuid : team.getMembers()) {
            ServerPlayer p = gm.world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && !p.isRemoved() && p.isAlive()) return true;
        }
        return false;
    }

    // ─── Phase ticks ───

    private void tickPrepPhase() {
        gm.spawnerManager.tick(gm.world, true);
        gm.incomeGeneratorManager.tick();
        gm.tierManager.tick(gm.team1, gm.team2);

        gm.tickCounter--;
        if (gm.tickCounter <= 0) {
            gm.state = GameManager.GameState.PLAYING;
            gm.playingPhaseTicks = 0;
            for (PlayerState ps : gm.playerStates.values()) {
                HudManager.sendTitle(ps.getPlayer(),
                        Component.literal("FIGHT!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        Component.literal("Spawners activated!").withStyle(ChatFormatting.GRAY),
                        5, 40, 10);
            }
            gm.world.playSound(null, GameConfig.getArenaCenter(),
                    SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.0f);
        } else if (gm.tickCounter % 20 == 0) {
            int seconds = gm.tickCounter / 20;
            for (PlayerState ps : gm.playerStates.values()) {
                HudManager.sendActionBar(ps.getPlayer(),
                        Component.literal("Game starts in " + seconds + "s...").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private void tickPlaying() {
        gm.playingPhaseTicks++;
        gm.spawnerManager.tick(gm.world, false);
        gm.incomeGeneratorManager.tick();
        gm.oreManager.tick(gm.world, gm.playingPhaseTicks);

        gm.team1.getNexusManager().tickShield();
        gm.team2.getNexusManager().tickShield();
        checkMobsAtNexus(gm.team1);
        checkMobsAtNexus(gm.team2);

        if (gm.team1.getNexusManager().isDead()) {
            triggerVictory(gm.team2, gm.team1);
            return;
        }
        if (gm.team2.getNexusManager().isDead()) {
            triggerVictory(gm.team1, gm.team2);
            return;
        }

        gm.tierManager.tick(gm.team1, gm.team2);

        gm.passiveIncomeTicker--;
        if (gm.passiveIncomeTicker <= 0) {
            gm.passiveIncomeTicker = GameConfig.BASE_PASSIVE_INTERVAL();
            gm.team1.getMoneyManager().addMoney(GameConfig.BASE_PASSIVE_INCOME());
            int t2Income = GameConfig.BASE_PASSIVE_INCOME();
            if (gm.gameMode == GameMode.SOLO) {
                t2Income = (int) (t2Income * ConfigManager.getInstance().getSoloModeIncomeMultiplier());
            }
            gm.team2.getMoneyManager().addMoney(t2Income);
        }

        gm.waveEventTicker--;
        if (gm.waveEventTicker <= 0) {
            gm.waveEventTicker = ConfigManager.getInstance().getWaveEventIntervalTicks();
            triggerWaveEvent();
        }
    }

    private void tickGameOver() {
        gm.tickCounter--;
        if (gm.tickCounter <= 0) {
            gm.stopGame();
        }
    }

    // ─── Wave events ───

    private void triggerWaveEvent() {
        int event = gm.eventRandom.nextInt(3);
        switch (event) {
            case 0 -> {
                int bonus = ConfigManager.getInstance().getBonusMoney();
                gm.team1.getMoneyManager().addMoney(bonus);
                gm.team2.getMoneyManager().addMoney(bonus);
                for (PlayerState ps : gm.playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("BONUS!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                            Component.literal("Free $50 for everyone!").withStyle(ChatFormatting.GRAY),
                            5, 40, 10);
                }
            }
            case 1 -> {
                int extra = GameConfig.BASE_PASSIVE_INCOME()
                        * ConfigManager.getInstance().getDoubleIncomeMultiplier();
                gm.team1.getMoneyManager().addMoney(extra);
                gm.team2.getMoneyManager().addMoney(extra);
                for (PlayerState ps : gm.playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("DOUBLE INCOME!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                            Component.literal("Income boost for everyone!").withStyle(ChatFormatting.GRAY),
                            5, 40, 10);
                }
            }
            case 2 -> {
                for (Mob mob : gm.spawnerManager.getAliveMobs()) {
                    if (mob.isAlive()) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                                ConfigManager.getInstance().getSpeedBoostDurationTicks(), 1));
                    }
                }
                for (PlayerState ps : gm.playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("SPEED BOOST!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                            Component.literal("All mobs move faster!").withStyle(ChatFormatting.GRAY),
                            5, 40, 10);
                }
            }
        }
    }

    // ─── Nexus check ───

    void checkMobsAtNexus(TeamState defender) {
        NexusManager nexus = defender.getNexusManager();
        if (nexus.getCenter() == null) return;
        int defenderSide = defender.getSide();

        for (Mob mob : gm.spawnerManager.getAliveMobs()) {
            if (!mob.isAlive()) continue;
            int mobTeam = com.towerdefense.wave.MobTags.getTeamId(mob);
            if (mobTeam < 0 || mobTeam == defenderSide) continue;
            nexus.checkAndApplyMobImpact(gm.world, mob, gm.mobUpgradeManager);
        }
    }

    // ─── Victory ───

    void triggerVictory(TeamState winner, TeamState loser) {
        if (gm.state == GameManager.GameState.GAME_OVER) return;
        gm.state = GameManager.GameState.GAME_OVER;
        gm.tickCounter = GameConfig.DEFEAT_DELAY_TICKS();

        gm.spawnerManager.killAllMobs();
        loser.getNexusManager().destroy(gm.world);

        for (UUID uuid : winner.getMembers()) {
            ServerPlayer p = gm.world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.isAlive()) {
                HudManager.sendTitle(p,
                        Component.literal("VICTORY!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        Component.literal("Enemy Nexus destroyed!").withStyle(ChatFormatting.GRAY),
                        5, 80, 20);
                gm.world.playSound(null, p.blockPosition(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
            }
        }
        for (UUID uuid : loser.getMembers()) {
            ServerPlayer p = gm.world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.isAlive()) {
                HudManager.sendTitle(p,
                        Component.literal("DEFEAT").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal("Your Nexus was destroyed!").withStyle(ChatFormatting.RED),
                        5, 80, 20);
                gm.world.playSound(null, p.blockPosition(),
                        SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.5f, 0.5f);
            }
        }
    }
}
