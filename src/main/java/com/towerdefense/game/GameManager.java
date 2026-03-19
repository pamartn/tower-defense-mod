package com.towerdefense.game;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.ai.SoloAI;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.arena.ArenaBuilder;
import com.towerdefense.arena.ArenaManager;
import com.towerdefense.arena.NexusManager;
import com.towerdefense.arena.WallBlockManager;
import com.towerdefense.arena.SpectatorManager;
import com.towerdefense.spell.SpellManager;
import com.towerdefense.pathfinding.PathValidator;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.tower.TowerUpgradeManager;
import com.towerdefense.wave.MobType;
import com.towerdefense.wave.MobUpgradeManager;
import com.towerdefense.wave.SpawnerManager;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class GameManager implements StructureEventSink, IGameSession {

    public enum GameState { IDLE, LOBBY, PREP_PHASE, PLAYING, GAME_OVER }

    private GameState state = GameState.IDLE;
    private int tickCounter;
    private int passiveIncomeTicker;
    private int incomeDisplayTicker;
    private int waveEventTicker;
    private final java.util.Random eventRandom = new java.util.Random();

    private ServerLevel world;
    private TeamState team1;
    private TeamState team2;
    private final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private GameLobby lobby;
    /** target -> requester: player who can accept (target) and player who wants to join (requester) */
    private final Map<UUID, UUID> pendingJoinRequests = new HashMap<>();

    private final ArenaBuilder arenaBuilder;
    private final ArenaManager arenaManager;
    private final PathValidator pathValidator = new PathValidator();
    private final PlayerKit playerKit = new PlayerKit();
    private final SpawnerManager spawnerManager = new SpawnerManager();
    private final IncomeGeneratorManager incomeGeneratorManager = new IncomeGeneratorManager();
    private final MobUpgradeManager mobUpgradeManager = new MobUpgradeManager();
    private final TowerUpgradeManager towerUpgradeManager = new TowerUpgradeManager();
    private final HudManager hudManager = new HudManager();
    private final SpectatorManager spectatorManager = new SpectatorManager();
    private final SpellManager spellManager = new SpellManager();
    private final TierManager tierManager = new TierManager();
    private final OreManager oreManager = new OreManager(this);
    private final WallBlockManager wallBlockManager = new WallBlockManager();

    private TowerManager towerManager;
    private int playingPhaseTicks;

    private GameMode gameMode = GameMode.PVP;
    private SoloAI soloAI;
    private Villager aiVillagerEntity;

    /** Production constructor: uses a real {@link ArenaManager}. */
    public GameManager() {
        this.arenaManager = new ArenaManager();
        this.arenaBuilder = arenaManager;
    }

    /**
     * Test/injection constructor. Pass {@link ArenaBuilder#noop()} to skip expensive
     * arena generation. {@code clearArena} is also skipped when arenaManager is absent.
     */
    public GameManager(ArenaBuilder arenaBuilder) {
        this.arenaBuilder = arenaBuilder;
        this.arenaManager = (arenaBuilder instanceof ArenaManager am) ? am : null;
    }

    public void setTowerManager(TowerManager towerManager) {
        this.towerManager = towerManager;
        if (towerManager != null) towerManager.setTowerUpgradeManager(towerUpgradeManager);
    }

    // ─── Start / Lobby / Add Player ───

    /** Host creates lobby. Host is in team 1. */
    public void startGame(ServerPlayer host) {
        if (state != GameState.IDLE) return;

        this.world = host.serverLevel();
        BlockPos origin = host.blockPosition().below().offset(-GameConfig.ARENA_SIZE() / 2, 0, -GameConfig.ARENA_SIZE() / 2);
        origin = new BlockPos(origin.getX(), GameConfig.ARENA_Y(), origin.getZ());
        GameConfig.arenaOrigin = origin;

        BlockPos nexus1 = GameConfig.getPlayer1NexusCenter();
        BlockPos nexus2 = GameConfig.getPlayer2NexusCenter();
        team1 = new TeamState(1, nexus1, nexus2);
        team2 = new TeamState(2, nexus2, nexus1);

        lobby = new GameLobby(host.getUUID());
        state = GameState.LOBBY;
        addPlayerToGame(host, 1);

        host.sendSystemMessage(Component.literal("Lobby created! You're in Team 1. Use /td invite <player> or players can /td join to join.")
                .withStyle(ChatFormatting.GREEN));
        TowerDefenseMod.LOGGER.info("Tower Defense lobby created by {}", host.getName().getString());
    }

    /** Legacy: start with 2 players (no lobby). Used when td start is called with 2 players online. */
    public void startGame(ServerPlayer p1, ServerPlayer p2) {
        startGame(p1);
        addPlayerToGame(p2, 2);
    }

    /** Host manually starts the game from lobby. Requires 2+ players. Returns true if started. */
    public boolean startGameFromLobby(ServerPlayer caller) {
        if (state != GameState.LOBBY || lobby == null) return false;
        if (!lobby.getHostUUID().equals(caller.getUUID())) return false;
        if (!lobby.canStart(team1.getMemberCount(), team2.getMemberCount())) return false;

        transitionLobbyToPrep();
        return true;
    }

    /** Start solo mode: host alone vs AI. */
    public void startSoloMode(ServerPlayer host) {
        if (state != GameState.LOBBY || lobby == null) return;
        if (!lobby.getHostUUID().equals(host.getUUID())) return;

        gameMode = GameMode.SOLO;
        transitionLobbyToPrep();
        host.sendSystemMessage(Component.literal("Solo mode! You vs AI. Good luck!")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        TowerDefenseMod.LOGGER.info("Tower Defense solo mode started by {}", host.getName().getString());
    }

    /** Start test mode: one player controls both sides. Crossing the midline auto-switches teams. */
    public void startTestMode(ServerPlayer host) {
        if (state != GameState.LOBBY || lobby == null) return;
        if (!lobby.getHostUUID().equals(host.getUUID())) return;

        gameMode = GameMode.TEST;
        transitionLobbyToPrep();
        host.sendSystemMessage(Component.literal("Test mode! Cross the midline to switch teams.")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        TowerDefenseMod.LOGGER.info("Tower Defense test mode started by {}", host.getName().getString());
    }

    /** Add player to team. In LOBBY, host must use /td start to begin. */
    public boolean addPlayerToGame(ServerPlayer player, int team) {
        if (state == GameState.IDLE) return false;
        TeamState targetTeam = team == 1 ? team1 : team2;
        if (targetTeam == null) return false;
        if (targetTeam.contains(player.getUUID())) return true;

        targetTeam.addMember(player.getUUID());
        PlayerState ps = new PlayerState(player, targetTeam);
        playerStates.put(player.getUUID(), ps);

        setupMoneyNotification(targetTeam);

        if (state == GameState.LOBBY) {
            if (lobby == null || !lobby.getHostUUID().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Joined! Host can start with /td start when ready.")
                        .withStyle(ChatFormatting.GREEN));
            }
            return true;
        }

        // PREP or PLAYING: join anytime
        playerKit.setupPlayer(player);
        if (state == GameState.PREP_PHASE) {
            playerKit.giveStartingKit(player);
        }
        targetTeam.getMoneyManager().reset();
        BlockPos spawn = team == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
        player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        tierManager.initTeam(team);
        HudManager.sendTitle(player,
                Component.literal("You joined Team " + team + "!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                Component.literal("").withStyle(ChatFormatting.GRAY), 5, 40, 10);
        return true;
    }

    private void setupMoneyNotification(TeamState team) {
        team.getMoneyManager().setOnMoneyAdded(amount -> {
            for (UUID uuid : team.getMembers()) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
                if (p != null && p.isAlive()) {
                    HudManager.sendActionBar(p, Component.literal("+$" + amount).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            }
        });
    }

    private void transitionLobbyToPrep() {
        arenaBuilder.generate(world, GameConfig.arenaOrigin);
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, world.getServer());
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, world.getServer());
        world.setDayTime(18000L);
        killAllNonPlayerEntities();
        spectatorManager.spawnSpectators(world, GameConfig.arenaOrigin);

        BlockPos nexus1 = GameConfig.getPlayer1NexusCenter();
        BlockPos nexus2 = GameConfig.getPlayer2NexusCenter();
        team1.getNexusManager().build(world, nexus1);
        team2.getNexusManager().build(world, nexus2);

        if (towerManager != null) towerManager.removeAll();
        spawnerManager.removeAll();
        spawnerManager.killAllMobs();
        incomeGeneratorManager.removeAll();
        mobUpgradeManager.reset();
        towerUpgradeManager.reset();
        tierManager.reset();
        tierManager.initTeam(1);
        tierManager.initTeam(2);

        incomeGeneratorManager.setMoneyManagerLookup(this::getMoneyManagerForTeam);
        incomeGeneratorManager.setIncomeMultiplierForAITeam(() ->
                gameMode == GameMode.SOLO ? ConfigManager.getInstance().getSoloModeGeneratorMultiplier() : 1.0);
        spawnerManager.setUpgradeManager(mobUpgradeManager);
        if (towerManager != null) towerManager.setTowerUpgradeManager(towerUpgradeManager);

        for (PlayerState ps : playerStates.values()) {
            playerKit.setupPlayer(ps.getPlayer());
            playerKit.giveStartingKit(ps.getPlayer());
            ps.getTeam().getMoneyManager().reset();
            BlockPos spawn = ps.getSide() == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
            ps.getPlayer().teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }

        hudManager.setup(world.getScoreboard());
        state = GameState.PREP_PHASE;
        tickCounter = GameConfig.PREP_PHASE_TICKS();
        passiveIncomeTicker = GameConfig.BASE_PASSIVE_INTERVAL();
        incomeDisplayTicker = 0;
        waveEventTicker = ConfigManager.getInstance().getWaveEventIntervalTicks();

        for (PlayerState ps : playerStates.values()) {
            HudManager.sendTitle(ps.getPlayer(),
                    Component.literal(gameMode == GameMode.SOLO ? "SOLO MODE" : "PVP TOWER DEFENSE").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(gameMode == GameMode.SOLO ? "You vs AI!" : "30s to prepare!").withStyle(ChatFormatting.YELLOW),
                    10, 60, 20);
            ps.getPlayer().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
            ps.getPlayer().sendSystemMessage(Component.literal("  \u2694 " + (gameMode == GameMode.SOLO ? "Solo vs AI" : "Tower Defense PvP")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            ps.getPlayer().sendSystemMessage(Component.literal("  Defend your Nexus! Destroy the enemy!").withStyle(ChatFormatting.YELLOW));
            ps.getPlayer().sendSystemMessage(Component.literal("  You are Team " + ps.getSide()).withStyle(ChatFormatting.AQUA));
            ps.getPlayer().sendSystemMessage(Component.literal("  Prep phase: 30 seconds").withStyle(ChatFormatting.GRAY));
            ps.getPlayer().sendSystemMessage(Component.literal("═══════════════════════════════").withStyle(ChatFormatting.GOLD));
        }

        if (gameMode == GameMode.SOLO) {
            int startMoney = (int) (GameConfig.STARTING_MONEY() * ConfigManager.getInstance().getSoloModeStartingMultiplier());
            team2.getMoneyManager().resetWithAmount(startMoney);
            soloAI = new SoloAI(this);
            spawnAIVillager();
        }

        if (gameMode == GameMode.TEST) {
            team2.getMoneyManager().reset();
        }

        lobby = null;
        TowerDefenseMod.LOGGER.info("Tower Defense prep phase started" + (gameMode == GameMode.SOLO ? " (solo mode)" : "") + (gameMode == GameMode.TEST ? " (test mode)" : ""));
    }

    private void spawnAIVillager() {
        if (world == null || team2 == null) return;
        BlockPos spawn = GameConfig.getPlayer2SpawnPoint();
        aiVillagerEntity = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, world);
        aiVillagerEntity.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        aiVillagerEntity.setInvulnerable(true);
        aiVillagerEntity.setCustomName(Component.literal("AI"));
        aiVillagerEntity.setCustomNameVisible(true);
        aiVillagerEntity.addTag("td_ai_villager");
        world.addFreshEntity(aiVillagerEntity);
    }

    public void removePlayerFromGame(UUID uuid) {
        PlayerState ps = playerStates.remove(uuid);
        if (ps == null) return;
        TeamState team = ps.getTeam();
        team.removeMember(uuid);
        if (ps.getPlayer() != null && !ps.getPlayer().isRemoved()) {
            playerKit.resetPlayer(ps.getPlayer());
        }

        if (team.getMemberCount() == 0 && state != GameState.LOBBY && gameMode != GameMode.TEST) {
            TeamState winner = team == team1 ? team2 : team1;
            TeamState loser = team;
            triggerVictory(winner, loser);
        }
    }

    public boolean isHost(UUID uuid) {
        return lobby != null && lobby.getHostUUID().equals(uuid);
    }

    /** True if player is host (in lobby) or in the game. */
    public boolean isPlayerInGame(UUID uuid) {
        if (state == GameState.IDLE) return false;
        if (isHost(uuid)) return true;
        return playerStates.containsKey(uuid);
    }

    public void addPendingJoinRequest(UUID requester, UUID target) {
        pendingJoinRequests.put(target, requester);
    }

    public UUID getAndRemovePendingJoinRequest(UUID target) {
        return pendingJoinRequests.remove(target);
    }

    public boolean hasPendingJoinRequest(UUID target) {
        return pendingJoinRequests.containsKey(target);
    }

    public GameLobby getLobby() { return lobby; }

    // ─── Stop ───

    public void stopGame() {
        if (state == GameState.IDLE) return;

        spawnerManager.killAllMobs();
        spawnerManager.removeAll();
        incomeGeneratorManager.removeAll();
        if (world != null) oreManager.removeAll(world);
        spectatorManager.removeAll();
        spellManager.removeAll();
        if (towerManager != null) towerManager.removeAll();
        wallBlockManager.clearAll();

        if (world != null) {
            killAllNonPlayerEntities();
            if (arenaManager != null) arenaManager.clearArena(world);
            hudManager.cleanup(world.getScoreboard());
            if (team1 != null) team1.getNexusManager().cleanup();
            if (team2 != null) team2.getNexusManager().cleanup();
            world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, world.getServer());
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, world.getServer());
        }

        for (PlayerState ps : playerStates.values()) {
            if (ps.getPlayer() != null) playerKit.resetPlayer(ps.getPlayer());
        }

        if (aiVillagerEntity != null && aiVillagerEntity.isAlive()) {
            aiVillagerEntity.discard();
            aiVillagerEntity = null;
        }
        soloAI = null;
        gameMode = GameMode.PVP;

        state = GameState.IDLE;
        sendAllPlayers(Component.literal("Game stopped.").withStyle(ChatFormatting.RED));
        team1 = null;
        team2 = null;
        playerStates.clear();
        lobby = null;
        pendingJoinRequests.clear();

        TowerDefenseMod.LOGGER.info("Tower Defense stopped.");
    }

    // ─── Tick ───

    public void tick() {
        if (state == GameState.IDLE || state == GameState.LOBBY) return;
        if (world == null) return;

        for (PlayerState ps : new ArrayList<>(playerStates.values())) {
            if (ps.getPlayer().isAlive()) {
                if (gameMode == GameMode.TEST) {
                    autoSwitchTeamOnBoundary(ps.getPlayer());
                } else {
                    arenaManager.confinePlayer(ps.getPlayer(), ps.getSide());
                }
            }
        }
        if (gameMode == GameMode.SOLO && aiVillagerEntity != null && aiVillagerEntity.isAlive()) {
            arenaManager.confineEntity(aiVillagerEntity, 2);
        }
        if (gameMode == GameMode.SOLO && soloAI != null) {
            soloAI.tick(world);
        }

        if (checkDisconnect()) return;

        switch (state) {
            case PREP_PHASE -> tickPrepPhase();
            case PLAYING -> tickPlaying();
            case GAME_OVER -> tickGameOver();
            default -> {}
        }

        spectatorManager.tick();
        if (world != null) spellManager.tick(world);
        updateHud();
    }

    private void tickPrepPhase() {
        spawnerManager.tick(world, true);
        incomeGeneratorManager.tick();
        tierManager.tick(team1, team2);

        tickCounter--;
        if (tickCounter <= 0) {
            state = GameState.PLAYING;
            playingPhaseTicks = 0;
            for (PlayerState ps : playerStates.values()) {
                HudManager.sendTitle(ps.getPlayer(),
                        Component.literal("FIGHT!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        Component.literal("Spawners activated!").withStyle(ChatFormatting.GRAY),
                        5, 40, 10);
            }
            world.playSound(null, GameConfig.getArenaCenter(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.0f);
        } else if (tickCounter % 20 == 0) {
            int seconds = tickCounter / 20;
            for (PlayerState ps : playerStates.values()) {
                HudManager.sendActionBar(ps.getPlayer(),
                        Component.literal("Game starts in " + seconds + "s...").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private void tickPlaying() {
        playingPhaseTicks++;
        spawnerManager.tick(world, false);
        incomeGeneratorManager.tick();
        oreManager.tick(world, playingPhaseTicks);

        team1.getNexusManager().tickShield();
        team2.getNexusManager().tickShield();
        checkMobsAtNexus(team1);
        checkMobsAtNexus(team2);

        if (team1.getNexusManager().isDead()) {
            triggerVictory(team2, team1);
            return;
        }
        if (team2.getNexusManager().isDead()) {
            triggerVictory(team1, team2);
            return;
        }

        tierManager.tick(team1, team2);

        passiveIncomeTicker--;
        if (passiveIncomeTicker <= 0) {
            passiveIncomeTicker = GameConfig.BASE_PASSIVE_INTERVAL();
            team1.getMoneyManager().addMoney(GameConfig.BASE_PASSIVE_INCOME());
            int t2Income = GameConfig.BASE_PASSIVE_INCOME();
            if (gameMode == GameMode.SOLO) {
                t2Income = (int) (t2Income * ConfigManager.getInstance().getSoloModeIncomeMultiplier());
            }
            team2.getMoneyManager().addMoney(t2Income);
        }

        waveEventTicker--;
        if (waveEventTicker <= 0) {
            waveEventTicker = ConfigManager.getInstance().getWaveEventIntervalTicks();
            triggerWaveEvent();
        }
    }

    private void triggerWaveEvent() {
        int event = eventRandom.nextInt(3);
        switch (event) {
            case 0 -> {
                int bonus = ConfigManager.getInstance().getBonusMoney();
                team1.getMoneyManager().addMoney(bonus);
                team2.getMoneyManager().addMoney(bonus);
                for (PlayerState ps : playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("BONUS!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                            Component.literal("Free $50 for everyone!").withStyle(ChatFormatting.GRAY), 5, 40, 10);
                }
            }
            case 1 -> {
                int extra = GameConfig.BASE_PASSIVE_INCOME() * ConfigManager.getInstance().getDoubleIncomeMultiplier();
                team1.getMoneyManager().addMoney(extra);
                team2.getMoneyManager().addMoney(extra);
                for (PlayerState ps : playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("DOUBLE INCOME!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                            Component.literal("Income boost for everyone!").withStyle(ChatFormatting.GRAY), 5, 40, 10);
                }
            }
            case 2 -> {
                for (Mob mob : spawnerManager.getAliveMobs()) {
                    if (mob.isAlive()) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, ConfigManager.getInstance().getSpeedBoostDurationTicks(), 1));
                    }
                }
                for (PlayerState ps : playerStates.values()) {
                    HudManager.sendTitle(ps.getPlayer(),
                            Component.literal("SPEED BOOST!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                            Component.literal("All mobs move faster!").withStyle(ChatFormatting.GRAY), 5, 40, 10);
                }
            }
        }
    }

    private void tickGameOver() {
        tickCounter--;
        if (tickCounter <= 0) {
            stopGame();
        }
    }

    // ─── Mob Nexus Check ───

    private void checkMobsAtNexus(TeamState defender) {
        NexusManager nexus = defender.getNexusManager();
        if (nexus.getCenter() == null) return;
        int defenderSide = defender.getSide();

        for (Mob mob : spawnerManager.getAliveMobs()) {
            if (!mob.isAlive()) continue;
            int mobTeam = com.towerdefense.wave.MobTags.getTeamId(mob);
            if (mobTeam < 0 || mobTeam == defenderSide) continue;
            nexus.checkAndApplyMobImpact(world, mob, mobUpgradeManager);
        }
    }

    // ─── Victory ───

    private void triggerVictory(TeamState winner, TeamState loser) {
        if (state == GameState.GAME_OVER) return;
        state = GameState.GAME_OVER;
        tickCounter = GameConfig.DEFEAT_DELAY_TICKS();

        spawnerManager.killAllMobs();
        loser.getNexusManager().destroy(world);

        for (UUID uuid : winner.getMembers()) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.isAlive()) {
                HudManager.sendTitle(p,
                        Component.literal("VICTORY!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        Component.literal("Enemy Nexus destroyed!").withStyle(ChatFormatting.GRAY),
                        5, 80, 20);
                world.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
            }
        }
        for (UUID uuid : loser.getMembers()) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.isAlive()) {
                HudManager.sendTitle(p,
                        Component.literal("DEFEAT").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal("Your Nexus was destroyed!").withStyle(ChatFormatting.RED),
                        5, 80, 20);
                world.playSound(null, p.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.5f, 0.5f);
            }
        }
    }

    // ─── Disconnect ───

    private boolean checkDisconnect() {
        if (team1 == null || team2 == null) return false;
        if (gameMode == GameMode.TEST) return false;
        boolean team1Gone = team1.getMemberCount() == 0 || !hasAnyAlivePlayer(team1);
        boolean team2Gone = gameMode == GameMode.SOLO ? false : (team2.getMemberCount() == 0 || !hasAnyAlivePlayer(team2));

        if (team1Gone && team2Gone) {
            stopGame();
            return true;
        }
        if (team1Gone) {
            triggerVictory(team2, team1);
            return true;
        }
        if (team2Gone) {
            triggerVictory(team1, team2);
            return true;
        }
        return false;
    }

    private boolean hasAnyAlivePlayer(TeamState team) {
        for (UUID uuid : team.getMembers()) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null && !p.isRemoved() && p.isAlive()) return true;
        }
        return false;
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        if (state == GameState.IDLE) return;
        if (state == GameState.LOBBY && isHost(player.getUUID())) {
            sendAllPlayers(Component.literal("Host disconnected! Lobby cancelled.").withStyle(ChatFormatting.RED));
            stopGame();
            return;
        }
        removePlayerFromGame(player.getUUID());
        sendAllPlayers(Component.literal(player.getName().getString() + " disconnected!").withStyle(ChatFormatting.RED));
    }

    // ─── Path Check ───

    public boolean checkPathBlocked(PlayerState defender) {
        return checkPathBlockedForTeam(defender.getSide());
    }

    public boolean checkPathBlockedForTeam(int defenderTeamId) {
        if (state == GameState.IDLE || world == null) return false;
        BlockPos midlineEntry = GameConfig.arenaOrigin.offset(GameConfig.ARENA_SIZE() / 2, 1, GameConfig.getMidlineZ());
        BlockPos nexusCenter = defenderTeamId == 1 ? team1.getNexusManager().getCenter() : team2.getNexusManager().getCenter();
        if (nexusCenter == null) return false;
        return !pathValidator.hasPath(world, midlineEntry, nexusCenter);
    }

    // ─── Mob Death / Structure Destroyed ───

    public void onStructureDestroyed(int victimTeamId) {
        if (state != GameState.PLAYING) return;
        TeamState attacker = victimTeamId == 1 ? team2 : team1;
        if (attacker != null) {
            attacker.getMoneyManager().addMoney(ConfigManager.getInstance().getStructureDestroyedBounty());
            for (UUID uuid : attacker.getMembers()) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(
                        Component.literal("+$10 Bounty! Enemy structure destroyed!").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    public void onMobKilled(Mob mob) {
        if (state != GameState.PLAYING) return;
        if (!mob.getTags().contains(com.towerdefense.wave.MobTags.MOB)) return;

        int mobTeam = com.towerdefense.wave.MobTags.getTeamId(mob);
        if (mobTeam < 0) return;

        TeamState defender = mobTeam == 1 ? team2 : team1;
        if (defender == null) return;

        String typeName = com.towerdefense.wave.MobTags.getType(mob);
        if (typeName == null) return;
        try {
            int reward = MobType.valueOf(typeName).getMoneyReward();
            if (reward > 0) defender.getMoneyManager().addMoney(reward);
        } catch (IllegalArgumentException ignored) {}
    }

    // ─── HUD ───

    private void updateHud() {
        if (world == null || team1 == null || team2 == null) return;

        int t1Income = GameConfig.BASE_PASSIVE_INCOME() + incomeGeneratorManager.getIncomeRateForTeam(1);
        int t2Income = GameConfig.BASE_PASSIVE_INCOME() + incomeGeneratorManager.getIncomeRateForTeam(2);

        hudManager.update(world.getScoreboard(),
                team1.getMoneyManager().getMoney(),
                team1.getNexusManager().getHp(), team1.getNexusManager().getMaxHp(),
                spawnerManager.getSpawnerCountForTeam(1),
                incomeGeneratorManager.getGeneratorCountForTeam(1),
                t1Income,
                team2.getMoneyManager().getMoney(),
                team2.getNexusManager().getHp(), team2.getNexusManager().getMaxHp(),
                spawnerManager.getSpawnerCountForTeam(2),
                incomeGeneratorManager.getGeneratorCountForTeam(2),
                t2Income,
                state == GameState.PREP_PHASE ? tickCounter / 20 : -1,
                gameMode == GameMode.SOLO);
    }

    // ─── Helpers ───

    private void killAllNonPlayerEntities() {
        if (world == null) return;
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();
        AABB arenaBox = new AABB(
                origin.getX() - 10, origin.getY() - 5, origin.getZ() - 10,
                origin.getX() + size + 10, origin.getY() + 30, origin.getZ() + size + 10
        );
        for (Entity entity : world.getEntities((Entity) null, arenaBox, e -> !(e instanceof Player) && e.isAlive())) {
            entity.discard();
        }
    }

    private void sendAllPlayers(Component message) {
        for (PlayerState ps : playerStates.values()) {
            if (ps.getPlayer() != null) ps.getPlayer().sendSystemMessage(message);
        }
    }

    private MoneyManager getMoneyManagerForTeam(int teamId) {
        TeamState t = teamId == 1 ? team1 : team2;
        return t != null ? t.getMoneyManager() : null;
    }

    public PlayerState getPlayerState(ServerPlayer player) {
        return playerStates.get(player.getUUID());
    }

    /** Returns the team side (1 or 2) for the given player UUID, or -1 if unknown. */
    @Override
    public int getTeamSide(UUID uuid) {
        PlayerState ps = playerStates.get(uuid);
        return ps != null ? ps.getSide() : -1;
    }

    /**
     * For testing: directly initialise teams and enter LOBBY state without a real player.
     * Call {@link #forceStartPrep()} next to advance to PREP_PHASE.
     */
    public void startGameForTesting(ServerLevel world, BlockPos origin) {
        if (state != GameState.IDLE) return;
        this.world = world;
        GameConfig.arenaOrigin = origin;
        BlockPos nexus1 = GameConfig.getPlayer1NexusCenter();
        BlockPos nexus2 = GameConfig.getPlayer2NexusCenter();
        team1 = new TeamState(1, nexus1, nexus2);
        team2 = new TeamState(2, nexus2, nexus1);
        lobby = new GameLobby(new UUID(0, 0));
        state = GameState.LOBBY;
    }

    /**
     * For testing: skip lobby requirements and advance directly to PREP_PHASE.
     * Only valid when the game is in LOBBY state.
     */
    public void forceStartPrep() {
        if (state != GameState.LOBBY) throw new IllegalStateException("Must be in LOBBY state to force prep");
        transitionLobbyToPrep();
    }

    /** In test mode: if the player is in the wrong half, switch their team to match their position. */
    private void autoSwitchTeamOnBoundary(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        PlayerState ps = playerStates.get(player.getUUID());
        if (ps == null) return;

        if (!GameConfig.isInsideArena(pos)) {
            // Outside arena: teleport to current side's spawn
            BlockPos spawn = ps.getSide() == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
            player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            return;
        }

        int relZ = pos.getZ() - GameConfig.arenaOrigin.getZ();
        int positionSide = relZ < GameConfig.getMidlineZ() ? 1 : 2;
        if (positionSide == ps.getSide()) return;

        // Player crossed the midline — switch teams
        (ps.getSide() == 1 ? team1 : team2).removeMember(player.getUUID());
        TeamState newTeam = positionSide == 1 ? team1 : team2;
        newTeam.addMember(player.getUUID());
        playerStates.put(player.getUUID(), new PlayerState(player, newTeam));
        HudManager.sendActionBar(player, Component.literal("\u21c4 Team " + positionSide)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    /**
     * Toggle the player between team 1 and team 2 for test/debug purposes.
     * Does not reset money. Returns the new side (1 or 2), or -1 on failure.
     */
    public int switchTestSide(ServerPlayer player) {
        if (team1 == null || team2 == null) return -1;

        PlayerState current = getPlayerState(player);
        int currentSide = current != null ? current.getSide() : 0;
        int newSide = currentSide == 2 ? 1 : 2;

        // Remove from current team
        if (current != null) {
            (currentSide == 1 ? team1 : team2).removeMember(player.getUUID());
        }

        // Add to new team and register new PlayerState
        TeamState newTeam = newSide == 1 ? team1 : team2;
        newTeam.addMember(player.getUUID());
        playerStates.put(player.getUUID(), new PlayerState(player, newTeam));

        // Teleport to new side's spawn
        BlockPos spawn = newSide == 1 ? GameConfig.getPlayer1SpawnPoint() : GameConfig.getPlayer2SpawnPoint();
        player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);

        return newSide;
    }

    public TeamState getTeamForUUID(UUID uuid) {
        if (team1 != null && team1.contains(uuid)) return team1;
        if (team2 != null && team2.contains(uuid)) return team2;
        return null;
    }

    // ─── Getters (backward compat) ───

    public GameState getState() { return state; }
    public boolean isActive() { return state != GameState.IDLE; }
    public boolean isPrepPhase() { return state == GameState.PREP_PHASE; }
    public boolean isLobby() { return state == GameState.LOBBY; }

    public PlayerState getPlayer1() {
        if (team1 == null || team1.getMembers().isEmpty()) return null;
        UUID first = team1.getMembers().get(0);
        return playerStates.get(first);
    }
    public PlayerState getPlayer2() {
        if (team2 == null || team2.getMembers().isEmpty()) return null;
        UUID first = team2.getMembers().get(0);
        return playerStates.get(first);
    }

    public TeamState getTeam1() { return team1; }
    public TeamState getTeam2() { return team2; }
    public Map<UUID, PlayerState> getPlayerStates() { return playerStates; }

    public SpawnerManager getSpawnerManager() { return spawnerManager; }
    public IncomeGeneratorManager getIncomeGeneratorManager() { return incomeGeneratorManager; }
    public OreManager getOreManager() { return oreManager; }
    public TowerManager getTowerManager() { return towerManager; }
    public MobUpgradeManager getMobUpgradeManager() { return mobUpgradeManager; }
    public TowerUpgradeManager getTowerUpgradeManager() { return towerUpgradeManager; }
    public SpellManager getSpellManager() { return spellManager; }
    public WallBlockManager getWallBlockManager() { return wallBlockManager; }
    public TierManager getTierManager() { return tierManager; }
    public PathValidator getPathValidator() { return pathValidator; }

    public MoneyManager getMoneyManager() {
        return team1 != null ? team1.getMoneyManager() : null;
    }

    public String getStatusText() {
        return switch (state) {
            case IDLE -> "No game running";
            case LOBBY -> "Lobby - invite players with /td invite <player>";
            case PREP_PHASE -> "Prep phase - " + (tickCounter / 20) + "s remaining" + (gameMode == GameMode.SOLO ? " (Solo)" : "");
            case PLAYING -> gameMode == GameMode.SOLO ? "Solo - You vs AI" : "PvP in progress";
            case GAME_OVER -> "Game over";
        };
    }

    public boolean isSoloMode() { return gameMode == GameMode.SOLO; }
    public GameMode getGameMode() { return gameMode; }
    public static int getAITeamId() { return 2; }
}
