package com.towerdefense.game;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.ai.SoloAI;
import com.towerdefense.config.ConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;

import java.util.UUID;

/**
 * Handles lobby creation, player joining, and the transition from lobby → prep phase.
 * Holds a reference to the owning {@link GameManager} for shared state access.
 */
class LobbyManager {

    private final GameManager gm;

    LobbyManager(GameManager gm) {
        this.gm = gm;
    }

    // ─── Lobby creation ───

    /** Host creates lobby. Host is placed in team 1. */
    void startGame(ServerPlayer host) {
        if (gm.state != GameManager.GameState.IDLE) return;

        gm.world = host.serverLevel();
        BlockPos origin = host.blockPosition().below()
                .offset(-GameConfig.ARENA_SIZE() / 2, 0, -GameConfig.ARENA_SIZE() / 2);
        origin = new BlockPos(origin.getX(), GameConfig.ARENA_Y(), origin.getZ());
        GameConfig.arenaOrigin = origin;

        BlockPos nexus1 = GameConfig.getPlayer1NexusCenter();
        BlockPos nexus2 = GameConfig.getPlayer2NexusCenter();
        gm.team1 = new TeamState(1, nexus1, nexus2);
        gm.team2 = new TeamState(2, nexus2, nexus1);

        gm.lobby = new GameLobby(host.getUUID());
        gm.state = GameManager.GameState.LOBBY;
        addPlayerToGame(host, 1);

        host.sendSystemMessage(Component.literal(
                "Lobby created! Use /td invite <player> or players can /td join to join.")
                .withStyle(ChatFormatting.GREEN));
        TowerDefenseMod.LOGGER.info("Tower Defense lobby created by {}", host.getName().getString());
    }

    /** Legacy: start with 2 players (no lobby). */
    void startGame(ServerPlayer p1, ServerPlayer p2) {
        startGame(p1);
        addPlayerToGame(p2, 2);
    }

    /** Host manually starts the game from lobby. Requires 2+ players. Returns true if started. */
    boolean startGameFromLobby(ServerPlayer caller) {
        if (gm.state != GameManager.GameState.LOBBY || gm.lobby == null) return false;
        if (!gm.lobby.getHostUUID().equals(caller.getUUID())) return false;
        if (!gm.lobby.canStart(gm.team1.getMemberCount(), gm.team2.getMemberCount())) return false;

        transitionLobbyToPrep();
        return true;
    }

    /** Start solo mode: host alone vs AI. */
    void startSoloMode(ServerPlayer host) {
        if (gm.state != GameManager.GameState.LOBBY || gm.lobby == null) return;
        if (!gm.lobby.getHostUUID().equals(host.getUUID())) return;

        gm.gameMode = GameMode.SOLO;
        transitionLobbyToPrep();
        host.sendSystemMessage(Component.literal("Solo mode! You vs AI. Good luck!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        TowerDefenseMod.LOGGER.info("Tower Defense solo mode started by {}", host.getName().getString());
    }

    /** Start test mode: one player controls both sides. Crossing the midline auto-switches teams. */
    void startTestMode(ServerPlayer host) {
        if (gm.state != GameManager.GameState.LOBBY || gm.lobby == null) return;
        if (!gm.lobby.getHostUUID().equals(host.getUUID())) return;

        gm.gameMode = GameMode.TEST;
        transitionLobbyToPrep();
        host.sendSystemMessage(Component.literal("Test mode! Cross the midline to switch teams.")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        TowerDefenseMod.LOGGER.info("Tower Defense test mode started by {}", host.getName().getString());
    }

    // ─── Player management ───

    /** Add player to team. Returns false if the game has not started yet. */
    boolean addPlayerToGame(ServerPlayer player, int team) {
        if (gm.state == GameManager.GameState.IDLE) return false;
        TeamState targetTeam = team == 1 ? gm.team1 : gm.team2;
        if (targetTeam == null) return false;
        if (targetTeam.contains(player.getUUID())) return true;

        targetTeam.addMember(player.getUUID());
        PlayerState ps = new PlayerState(player, targetTeam);
        gm.playerStates.put(player.getUUID(), ps);

        setupMoneyNotification(targetTeam);

        if (gm.state == GameManager.GameState.LOBBY) {
            if (gm.lobby == null || !gm.lobby.getHostUUID().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "Joined! Host can start with /td start when ready.")
                        .withStyle(ChatFormatting.GREEN));
            }
            return true;
        }

        // PREP or PLAYING: join anytime
        gm.nexusBossBarManager.addPlayer(player, team);
        gm.playerKit.setupPlayer(player);
        if (gm.state == GameManager.GameState.PREP_PHASE) {
            gm.playerKit.giveStartingKit(player);
        }
        targetTeam.getMoneyManager().reset();
        BlockPos spawn = team == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
        player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        gm.tierManager.initTeam(team);
        HudManager.sendTitle(player,
                Component.literal("You joined the game!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal("").withStyle(ChatFormatting.GRAY), 5, 40, 10);
        return true;
    }

    // ─── Private helpers ───

    private void setupMoneyNotification(TeamState team) {
        team.getMoneyManager().setOnMoneyAdded(amount -> {
            for (UUID uuid : team.getMembers()) {
                ServerPlayer p = gm.world.getServer().getPlayerList().getPlayer(uuid);
                if (p != null && p.isAlive()) {
                    HudManager.sendActionBar(p,
                            Component.literal("+$" + amount).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            }
        });
    }

    /** Finalises the lobby and transitions to the prep phase. */
    void transitionLobbyToPrep() {
        gm.arenaBuilder.generate(gm.world, GameConfig.arenaOrigin);
        gm.world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, gm.world.getServer());
        gm.world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, gm.world.getServer());
        gm.world.setDayTime(18000L);
        gm.killAllNonPlayerEntities();
        gm.spectatorManager.spawnSpectators(gm.world, GameConfig.arenaOrigin);

        BlockPos nexus1 = GameConfig.getPlayer1NexusCenter();
        BlockPos nexus2 = GameConfig.getPlayer2NexusCenter();
        gm.team1.getNexusManager().build(gm.world, nexus1);
        gm.team2.getNexusManager().build(gm.world, nexus2);

        if (gm.towerManager != null) gm.towerManager.removeAll();
        gm.spawnerManager.removeAll();
        gm.spawnerManager.killAllMobs();
        gm.incomeGeneratorManager.removeAll();
        gm.mobUpgradeManager.reset();
        gm.towerUpgradeManager.reset();
        gm.tierManager.reset();
        gm.tierManager.initTeam(1);
        gm.tierManager.initTeam(2);

        gm.incomeGeneratorManager.setMoneyManagerLookup(gm::getMoneyManagerForTeam);
        gm.incomeGeneratorManager.setIncomeMultiplierForAITeam(() ->
                gm.gameMode == GameMode.SOLO
                        ? ConfigManager.getInstance().getSoloModeGeneratorMultiplier()
                        : 1.0);
        gm.spawnerManager.setUpgradeManager(gm.mobUpgradeManager);
        if (gm.towerManager != null) gm.towerManager.setTowerUpgradeManager(gm.towerUpgradeManager);

        for (PlayerState ps : gm.playerStates.values()) {
            gm.playerKit.setupPlayer(ps.getPlayer());
            gm.playerKit.giveStartingKit(ps.getPlayer());
            ps.getTeam().getMoneyManager().reset();
            BlockPos spawn = ps.getSide() == 1
                    ? GameConfig.getPlayer1SpawnPoint()
                    : GameConfig.getPlayer2SpawnPoint();
            ps.getPlayer().teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }

        gm.hudManager.setup(gm.world.getScoreboard());
        gm.nexusBossBarManager.removeAllPlayers();
        for (PlayerState ps : gm.playerStates.values()) {
            gm.nexusBossBarManager.addPlayer(ps.getPlayer(), ps.getSide());
            gm.hudManager.setupPlayer(gm.world.getScoreboard(), ps.getPlayer(), ps.getSide());
        }

        gm.state = GameManager.GameState.PREP_PHASE;
        gm.tickCounter = GameConfig.PREP_PHASE_TICKS();
        gm.passiveIncomeTicker = GameConfig.BASE_PASSIVE_INTERVAL();
        gm.incomeDisplayTicker = 0;
        gm.waveEventTicker = ConfigManager.getInstance().getWaveEventIntervalTicks();
        gm.minimapTicker = 0;
        gm.restoreHealthTicker = 0;

        for (PlayerState ps : gm.playerStates.values()) {
            HudManager.sendTitle(ps.getPlayer(),
                    Component.literal(gm.gameMode == GameMode.SOLO ? "SOLO MODE" : "PVP TOWER DEFENSE")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(gm.gameMode == GameMode.SOLO ? "You vs AI!" : "30s to prepare!")
                            .withStyle(ChatFormatting.YELLOW),
                    10, 60, 20);
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "═══════════════════════════════").withStyle(ChatFormatting.GOLD));
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "  \u2694 " + (gm.gameMode == GameMode.SOLO ? "Solo vs AI" : "Tower Defense PvP"))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "  Defend your Nexus! Destroy the enemy!").withStyle(ChatFormatting.YELLOW));
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "  You are Team " + ps.getSide()).withStyle(ChatFormatting.AQUA));
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "  Prep phase: 30 seconds").withStyle(ChatFormatting.GRAY));
            ps.getPlayer().sendSystemMessage(Component.literal(
                    "═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        }

        if (gm.gameMode == GameMode.SOLO) {
            int startMoney = (int) (GameConfig.STARTING_MONEY()
                    * ConfigManager.getInstance().getSoloModeStartingMultiplier());
            gm.team2.getMoneyManager().resetWithAmount(startMoney);
            gm.soloAI = new SoloAI(gm);
            spawnAIVillager();
        }

        if (gm.gameMode == GameMode.TEST) {
            gm.team2.getMoneyManager().reset();
        }

        gm.lobby = null;
        TowerDefenseMod.LOGGER.info("Tower Defense prep phase started"
                + (gm.gameMode == GameMode.SOLO ? " (solo mode)" : "")
                + (gm.gameMode == GameMode.TEST ? " (test mode)" : ""));
    }

    private void spawnAIVillager() {
        if (gm.world == null || gm.team2 == null) return;
        BlockPos spawn = GameConfig.getPlayer2SpawnPoint();
        gm.aiVillagerEntity = new Villager(EntityType.VILLAGER, gm.world);
        gm.aiVillagerEntity.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        gm.aiVillagerEntity.setInvulnerable(true);
        gm.aiVillagerEntity.setCustomName(Component.literal("AI"));
        gm.aiVillagerEntity.setCustomNameVisible(true);
        gm.aiVillagerEntity.addTag("td_ai_villager");
        gm.world.addFreshEntity(gm.aiVillagerEntity);
    }
}
