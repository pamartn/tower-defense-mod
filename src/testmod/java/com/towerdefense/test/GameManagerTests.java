package com.towerdefense.test;

import com.towerdefense.arena.ArenaBuilder;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.GameConfig;
import com.towerdefense.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * State-machine tests for {@link GameManager}.
 * Uses {@link ArenaBuilder#noop()} to skip expensive arena block generation.
 */
public class GameManagerTests {

    /** Convenience: origin placed safely in the test structure. */
    private static BlockPos testOrigin(GameTestHelper ctx) {
        // Put origin at (0,0,0) relative to the structure, absolute in world
        return ctx.absolutePos(new BlockPos(0, 0, 0));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testInitialStateIsIdle(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ctx.assertTrue(gm.getState() == GameManager.GameState.IDLE, "GameManager should start in IDLE");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testStartGameForTestingEntersLobby(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));

        ctx.assertTrue(gm.getState() == GameManager.GameState.LOBBY, "Should be LOBBY after startGameForTesting");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testForceStartPrepEntersPrepPhase(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        gm.forceStartPrep();

        ctx.assertTrue(gm.getState() == GameManager.GameState.PREP_PHASE, "Should be PREP_PHASE after forceStartPrep");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 800)
    public void testPrepPhaseTransitionsToPlaying(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        gm.forceStartPrep();

        ctx.assertTrue(gm.getState() == GameManager.GameState.PREP_PHASE, "Should be PREP_PHASE");

        int prepTicks = ConfigManager.getInstance().getPrepPhaseTicks();
        // Tick one more than prep to ensure transition
        for (int i = 0; i <= prepTicks + 2; i++) {
            gm.tick();
        }

        ctx.assertTrue(gm.getState() == GameManager.GameState.PLAYING, "Should be PLAYING after prep ticks complete");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 900)
    public void testStopGameResetsToIdle(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        gm.forceStartPrep();

        int prepTicks = ConfigManager.getInstance().getPrepPhaseTicks();
        for (int i = 0; i <= prepTicks + 2; i++) {
            gm.tick();
        }
        ctx.assertTrue(gm.getState() == GameManager.GameState.PLAYING, "Should reach PLAYING");

        gm.stopGame();
        ctx.assertTrue(gm.getState() == GameManager.GameState.IDLE, "Should be IDLE after stopGame");
        ctx.assertTrue(!gm.isActive(), "isActive should return false after stop");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 900)
    public void testNexusDeathTriggersGameOver(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        gm.forceStartPrep();

        int prepTicks = ConfigManager.getInstance().getPrepPhaseTicks();
        for (int i = 0; i <= prepTicks + 2; i++) {
            gm.tick();
        }
        ctx.assertTrue(gm.getState() == GameManager.GameState.PLAYING, "Should be PLAYING");

        // Destroy team 1's nexus
        gm.getTeam1().getNexusManager().damage(level, Integer.MAX_VALUE);
        ctx.assertTrue(gm.getTeam1().getNexusManager().isDead(), "Team 1 nexus should be dead");

        // One tick to trigger victory check
        gm.tick();
        ctx.assertTrue(gm.getState() == GameManager.GameState.GAME_OVER, "Should be GAME_OVER after nexus destroyed");

        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testStopGameFromLobbyResetsToIdle(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        ctx.assertTrue(gm.getState() == GameManager.GameState.LOBBY, "Should be LOBBY");

        gm.stopGame();
        ctx.assertTrue(gm.getState() == GameManager.GameState.IDLE, "Should be IDLE after stopping from lobby");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void testGameModeDefaultIsPvp(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ctx.assertTrue(!gm.isSoloMode(), "Default game mode should be PVP, not solo");
        ctx.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 800)
    public void testSoloModeAfterStop(GameTestHelper ctx) {
        GameManager gm = new GameManager(ArenaBuilder.noop());
        ServerLevel level = ctx.getLevel();

        GameConfig.arenaOrigin = testOrigin(ctx);
        gm.startGameForTesting(level, testOrigin(ctx));
        gm.forceStartPrep(); // goes to PREP as PVP; we just test state resets on stop

        gm.stopGame();
        ctx.assertFalse(gm.isSoloMode(), "Solo mode flag should be reset after stopGame");
        ctx.succeed();
    }
}
