package com.towerdefense;

import com.towerdefense.game.MoneyManager;
import com.towerdefense.game.TierManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TierManager} tier purchase logic.
 * Pure Java — no Minecraft server required.
 */
class TierManagerTest {

    private TierManager tiers;
    private MoneyManager money;

    @BeforeEach
    void setUp() {
        tiers = new TierManager();
        tiers.reset();
        tiers.initTeam(1);
        tiers.initTeam(2);

        money = new MoneyManager();
        money.resetWithAmount(10_000);
    }

    @Test
    void initialTierIsOne() {
        assertEquals(1, tiers.getCurrentTier(1));
        assertEquals(1, tiers.getCurrentTier(2));
    }

    @Test
    void cannotBuyCurrentOrLowerTier() {
        assertFalse(tiers.canBuyTier(1, 1), "already at tier 1");
        assertFalse(tiers.canBuyTier(1, 0), "tier 0 out of range");
        assertFalse(tiers.canBuyTier(1, 4), "tier 4 out of range");
    }

    @Test
    void buyTier2DeductsMoney() {
        int cost = tiers.getTierCost(2);
        boolean bought = tiers.buyTierUpgradeForTeam(money, 1, 2);

        assertTrue(bought);
        assertEquals(10_000 - cost, money.getMoney());
        assertEquals(2, tiers.getPendingUnlockTier(1));
    }

    @Test
    void buyTier2FailsWithNoMoney() {
        money.resetWithAmount(0);

        boolean bought = tiers.buyTierUpgradeForTeam(money, 1, 2);

        assertFalse(bought);
        assertEquals(0, tiers.getPendingUnlockTier(1), "no pending tier after failed purchase");
        assertEquals(0, money.getMoney(), "money unchanged");
    }

    @Test
    void cannotBuyTierWhilePendingUnlock() {
        tiers.buyTierUpgradeForTeam(money, 1, 2);
        assertEquals(2, tiers.getPendingUnlockTier(1));

        boolean bought3 = tiers.buyTierUpgradeForTeam(money, 1, 3);
        assertFalse(bought3, "tier 3 blocked while tier 2 is pending");
    }

    @Test
    void forceSetTierIsImmediateAndClearsPending() {
        tiers.forceSetTier(1, 3);

        assertEquals(3, tiers.getCurrentTier(1));
        assertEquals(0, tiers.getPendingUnlockTier(1), "no pending after force set");
    }
}
