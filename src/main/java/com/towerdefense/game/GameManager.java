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
import com.towerdefense.network.MinimapPayload;
import com.towerdefense.wave.MobTags;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class GameManager implements StructureEventSink, IGameSession {

    public enum GameState { IDLE, LOBBY, PREP_PHASE, PLAYING, GAME_OVER }

    // Package-private so LobbyManager and GamePhaseManager can access them directly.
    GameState state = GameState.IDLE;
    int tickCounter;
    int passiveIncomeTicker;
    int incomeDisplayTicker;
    int waveEventTicker;
    int minimapTicker;
    int restoreHealthTicker;
    final java.util.Random eventRandom = new java.util.Random();

    ServerLevel world;
    TeamState team1;
    TeamState team2;
    final Map<UUID, PlayerState> playerStates = new HashMap<>();
    GameLobby lobby;
    /** target -> requester: player who can accept (target) and player who wants to join (requester) */
    final Map<UUID, UUID> pendingJoinRequests = new HashMap<>();

    // Package-private so LobbyManager and GamePhaseManager can access them directly.
    final ArenaBuilder arenaBuilder;
    final ArenaManager arenaManager;
    final PathValidator pathValidator = new PathValidator();
    final PlayerKit playerKit = new PlayerKit();
    final SpawnerManager spawnerManager = new SpawnerManager();
    final IncomeGeneratorManager incomeGeneratorManager = new IncomeGeneratorManager();
    final MobUpgradeManager mobUpgradeManager = new MobUpgradeManager();
    final TowerUpgradeManager towerUpgradeManager = new TowerUpgradeManager();
    final HudManager hudManager = new HudManager();
    final NexusBossBarManager nexusBossBarManager = new NexusBossBarManager();
    final SpectatorManager spectatorManager = new SpectatorManager();
    final SpellManager spellManager = new SpellManager();
    final TierManager tierManager = new TierManager();
    final OreManager oreManager = new OreManager(this);
    final WallBlockManager wallBlockManager = new WallBlockManager();

    TowerManager towerManager;
    int playingPhaseTicks;

    GameMode gameMode = GameMode.PVP;
    SoloAI soloAI;
    Villager aiVillagerEntity;

    private final LobbyManager lobbyManager = new LobbyManager(this);
    private final GamePhaseManager gamePhaseManager = new GamePhaseManager(this);

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

    // ─── Start / Lobby / Add Player (delegates to LobbyManager) ───

    /** Host creates lobby. Host is in team 1. */
    public void startGame(ServerPlayer host) { lobbyManager.startGame(host); }

    /** Legacy: start with 2 players (no lobby). Used when td start is called with 2 players online. */
    public void startGame(ServerPlayer p1, ServerPlayer p2) { lobbyManager.startGame(p1, p2); }

    /** Host manually starts the game from lobby. Requires 2+ players. Returns true if started. */
    public boolean startGameFromLobby(ServerPlayer caller) { return lobbyManager.startGameFromLobby(caller); }

    /** Start solo mode: host alone vs AI. */
    public void startSoloMode(ServerPlayer host) { lobbyManager.startSoloMode(host); }

    /** Start test mode: one player controls both sides. Crossing the midline auto-switches teams. */
    public void startTestMode(ServerPlayer host) { lobbyManager.startTestMode(host); }

    /** Add player to team. In LOBBY, host must use /td start to begin. */
    public boolean addPlayerToGame(ServerPlayer player, int team) {
        return lobbyManager.addPlayerToGame(player, team);
    }

    public void removePlayerFromGame(UUID uuid) {
        PlayerState ps = playerStates.remove(uuid);
        if (ps == null) return;
        TeamState team = ps.getTeam();
        team.removeMember(uuid);
        if (ps.getPlayer() != null && !ps.getPlayer().isRemoved()) {
            nexusBossBarManager.removePlayer(ps.getPlayer());
            playerKit.resetPlayer(ps.getPlayer());
        }

        if (team.getMemberCount() == 0 && state != GameState.LOBBY && gameMode != GameMode.TEST) {
            TeamState winner = team == team1 ? team2 : team1;
            TeamState loser = team;
            gamePhaseManager.triggerVictory(winner, loser);
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

        nexusBossBarManager.removeAllPlayers();
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

        if (gamePhaseManager.checkDisconnect()) return;

        gamePhaseManager.tick();

        spectatorManager.tick();
        if (world != null) spellManager.tick(world);
        updateHud();

        // Restore food and health every 3 seconds
        restoreHealthTicker++;
        if (restoreHealthTicker >= 60) {
            restoreHealthTicker = 0;
            for (PlayerState ps : playerStates.values()) {
                ServerPlayer p = ps.getPlayer();
                if (p.isAlive()) {
                    p.setHealth(p.getMaxHealth());
                    p.getFoodData().setFoodLevel(20);
                    p.getFoodData().setSaturation(20.0f);
                }
            }
        }

        // Send minimap packet every 10 ticks
        minimapTicker++;
        if (minimapTicker >= 10) {
            minimapTicker = 0;
            sendMinimapPackets();
        }
    }

    // tickPrepPhase / tickPlaying / tickGameOver / triggerWaveEvent / checkMobsAtNexus
    // / triggerVictory / checkDisconnect / hasAnyAlivePlayer  →  GamePhaseManager

    // ─── Disconnect ───

    // checkDisconnect / hasAnyAlivePlayer  →  GamePhaseManager

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

    private void sendMinimapPackets() {
        if (world == null || team1 == null || team2 == null) return;
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();

        // Mob positions
        List<int[]> team1Mobs = new ArrayList<>();
        List<int[]> team2Mobs = new ArrayList<>();
        List<List<int[]>> team1Paths = new ArrayList<>();
        List<List<int[]>> team2Paths = new ArrayList<>();

        for (Mob mob : spawnerManager.getAliveMobs()) {
            int teamId = MobTags.getTeamId(mob);
            int rx = mob.blockPosition().getX() - origin.getX();
            int rz = mob.blockPosition().getZ() - origin.getZ();
            List<int[]> path = extractPath(mob, origin);
            if (teamId == 1) {
                team1Mobs.add(new int[]{rx, rz});
                team1Paths.add(path);
            } else if (teamId == 2) {
                team2Mobs.add(new int[]{rx, rz});
                team2Paths.add(path);
            }
        }

        // Structures
        List<int[]> walls1 = toRelative(wallBlockManager.getWallXZByTeam(1), origin);
        List<int[]> walls2 = toRelative(wallBlockManager.getWallXZByTeam(2), origin);
        List<int[]> towers1 = new ArrayList<>();
        List<int[]> towers2 = new ArrayList<>();
        if (towerManager != null) {
            for (var t : towerManager.getTowers()) {
                int rx = t.basePos().getX() - origin.getX();
                int rz = t.basePos().getZ() - origin.getZ();
                (t.teamId() == 1 ? towers1 : towers2).add(new int[]{rx, rz});
            }
        }

        List<int[]> spawners1 = new ArrayList<>();
        List<int[]> spawners2 = new ArrayList<>();
        for (var s : spawnerManager.getSpawners()) {
            int rx = s.basePos().getX() - origin.getX();
            int rz = s.basePos().getZ() - origin.getZ();
            (s.teamId() == 1 ? spawners1 : spawners2).add(new int[]{rx, rz});
        }

        List<int[]> generators1 = new ArrayList<>();
        List<int[]> generators2 = new ArrayList<>();
        for (var g : incomeGeneratorManager.getGenerators()) {
            int rx = g.basePos().getX() - origin.getX();
            int rz = g.basePos().getZ() - origin.getZ();
            (g.teamId() == 1 ? generators1 : generators2).add(new int[]{rx, rz});
        }

        BlockPos n1 = GameConfig.getPlayer1NexusCenter();
        BlockPos n2 = GameConfig.getPlayer2NexusCenter();
        int[] nexus1 = {n1.getX() - origin.getX(), n1.getZ() - origin.getZ()};
        int[] nexus2 = {n2.getX() - origin.getX(), n2.getZ() - origin.getZ()};

        for (PlayerState ps : playerStates.values()) {
            int side = ps.getSide();
            boolean isTeam1 = side == 1;
            ServerPlayNetworking.send(ps.getPlayer(), new MinimapPayload(
                    origin.getX(), origin.getZ(), size, side,
                    isTeam1 ? team1Mobs : team2Mobs,
                    isTeam1 ? team2Mobs : team1Mobs,
                    isTeam1 ? team1Paths : team2Paths,
                    isTeam1 ? team2Paths : team1Paths,
                    isTeam1 ? walls1 : walls2,
                    isTeam1 ? walls2 : walls1,
                    isTeam1 ? towers1 : towers2,
                    isTeam1 ? towers2 : towers1,
                    isTeam1 ? spawners1 : spawners2,
                    isTeam1 ? spawners2 : spawners1,
                    isTeam1 ? generators1 : generators2,
                    isTeam1 ? generators2 : generators1,
                    isTeam1 ? nexus1 : nexus2,
                    isTeam1 ? nexus2 : nexus1,
                    ps.getMoneyManager().getMoney()
            ));
        }
    }

    private List<int[]> extractPath(Mob mob, BlockPos origin) {
        var path = mob.getNavigation().getPath();
        if (path == null) return java.util.Collections.emptyList();
        List<int[]> nodes = new ArrayList<>();
        int count = path.getNodeCount();
        int nextIdx = path.getNextNodeIndex();
        // Sample remaining path nodes, max 20
        for (int i = nextIdx; i < count && nodes.size() < 20; i++) {
            var node = path.getNode(i);
            nodes.add(new int[]{node.x - origin.getX(), node.z - origin.getZ()});
        }
        return nodes;
    }

    private List<int[]> toRelative(List<int[]> absXZ, BlockPos origin) {
        List<int[]> rel = new ArrayList<>(absXZ.size());
        for (int[] pos : absXZ) {
            rel.add(new int[]{pos[0] - origin.getX(), pos[1] - origin.getZ()});
        }
        return rel;
    }

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

        nexusBossBarManager.update(
                team1.getNexusManager().getHp(), team1.getNexusManager().getMaxHp(),
                team2.getNexusManager().getHp(), team2.getNexusManager().getMaxHp());
    }

    // ─── Helpers ───

    void killAllNonPlayerEntities() {
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

    void sendAllPlayers(Component message) {
        for (PlayerState ps : playerStates.values()) {
            if (ps.getPlayer() != null) ps.getPlayer().sendSystemMessage(message);
        }
    }

    MoneyManager getMoneyManagerForTeam(int teamId) {
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
        lobbyManager.transitionLobbyToPrep();
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
