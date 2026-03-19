package com.towerdefense.shop;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.IGameSession;
import com.towerdefense.game.TierManager;
import com.towerdefense.game.IncomeGeneratorType;
import com.towerdefense.game.MoneyManager;
import com.towerdefense.game.PlayerKit;
import com.towerdefense.spell.SpellType;
import com.towerdefense.wave.MobType;
import com.towerdefense.wave.MobUpgradeManager;
import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerRegistry;
import com.towerdefense.tower.TowerType;
import com.towerdefense.tower.TowerUpgradeManager;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ShopScreenHandler extends AbstractContainerMenu {

    // Slot 0: money, Slots 1-30: mob upgrade levels (10 mobs * 3 types), Slots 31-40: tower upgrade levels (10 towers)
    // Slots 41-43: tier (currentTier, pendingUnlockTier, unlockTicksRemaining)
    public static final int MOB_UPGRADE_COUNT = 30;
    public static final int TOWER_UPGRADE_OFFSET = 31;
    public static final int TIER_CURRENT = 41;
    public static final int TIER_PENDING = 42;
    public static final int TIER_TICKS_REMAINING = 43;
    public static final int DATA_SIZE = 44;

    private final ContainerData data;
    private final MoneyManager moneyManager;
    private final TowerRegistry towerRegistry;
    private final Player player;
    private final IGameSession gameSession;

    /** Full constructor: pass {@code gameSession} on the server, {@code null} for the client-side codec handler. */
    public ShopScreenHandler(int syncId, Inventory playerInventory, MoneyManager moneyManager, TowerRegistry towerRegistry, IGameSession gameSession) {
        super(TowerDefenseMod.SHOP_SCREEN_HANDLER_TYPE, syncId);
        this.player = playerInventory.player;
        this.towerRegistry = towerRegistry;
        this.moneyManager = moneyManager;
        this.gameSession = gameSession;

        if (moneyManager != null && player instanceof ServerPlayer sp && gameSession != null) {
            MobUpgradeManager mgr = gameSession.getMobUpgradeManager();
            final int teamId = Math.max(1, gameSession.getTeamSide(sp.getUUID()));

            TowerUpgradeManager towerMgr = gameSession.getTowerUpgradeManager();
            TierManager tierMgr = gameSession.getTierManager();
            var towerRecipes = towerRegistry.getRecipesSortedByPrice();
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    if (index == 0) return moneyManager.getMoney();
                    int upgradeIdx = index - 1;
                    if (upgradeIdx < MOB_UPGRADE_COUNT) {
                        var entries = MobUpgradeManager.getAllUpgradeEntries();
                        if (upgradeIdx >= 0 && upgradeIdx < entries.size()) {
                            var e = entries.get(upgradeIdx);
                            return mgr.getLevel(teamId, e.mob(), e.upgrade());
                        }
                    }
                    int towerIdx = index - TOWER_UPGRADE_OFFSET;
                    if (towerIdx >= 0 && towerIdx < towerRecipes.size()) {
                        return towerMgr.getLevel(teamId, towerRecipes.get(towerIdx).type());
                    }
                    if (index == TIER_CURRENT) return tierMgr.getCurrentTier(teamId);
                    if (index == TIER_PENDING) return tierMgr.getPendingUnlockTier(teamId);
                    if (index == TIER_TICKS_REMAINING) return tierMgr.getUnlockTicksRemaining(teamId);
                    return 0;
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return DATA_SIZE; }
            };
        } else {
            this.data = new SimpleContainerData(DATA_SIZE);
        }
        this.addDataSlots(this.data);
    }

    public void setInitialMoney(int money) {
        if (data instanceof SimpleContainerData) {
            this.data.set(0, money);
        }
    }

    public int getMoney() {
        return data.get(0);
    }

    public int getUpgradeLevel(int upgradeIndex) {
        return data.get(1 + upgradeIndex);
    }

    public int getTowerUpgradeLevel(int towerIndex) {
        return data.get(TOWER_UPGRADE_OFFSET + towerIndex);
    }

    public int getTierCurrent() { return data.get(TIER_CURRENT); }
    public int getTierPending() { return data.get(TIER_PENDING); }
    public int getTierTicksRemaining() { return data.get(TIER_TICKS_REMAINING); }

    public boolean buyTowerUpgrade(int towerIndex) {
        if (moneyManager == null || gameSession == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        var recipes = towerRegistry.getRecipesSortedByPrice();
        if (towerIndex < 0 || towerIndex >= recipes.size()) return false;

        TowerUpgradeManager mgr = gameSession.getTowerUpgradeManager();
        int teamId = gameSession.getTeamSide(serverPlayer.getUUID());
        if (mgr == null || teamId < 0) return false;

        TowerType type = recipes.get(towerIndex).type();
        int cost = mgr.getUpgradeCost(teamId, type);
        if (!moneyManager.canAfford(cost)) return false;

        moneyManager.spend(cost);
        mgr.addLevel(teamId, type);
        return true;
    }

    public boolean buyTierUpgrade(int targetTier) {
        if (moneyManager == null || gameSession == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        TierManager tierMgr = gameSession.getTierManager();
        int teamId = gameSession.getTeamSide(serverPlayer.getUUID());
        if (teamId < 0) return false;

        return tierMgr.buyTierUpgrade(serverPlayer, moneyManager, targetTier, teamId);
    }

    public boolean buyTower(TowerType type, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        if (gameSession != null) {
            TierManager tierMgr = gameSession.getTierManager();
            int teamSide = gameSession.getTeamSide(serverPlayer.getUUID());
            if (teamSide >= 0 && type.getTier() > tierMgr.getCurrentTier(teamSide)) return false;
        }

        TowerRecipe recipe = null;
        for (TowerRecipe r : towerRegistry.getRecipes()) {
            if (r.type() == type) { recipe = r; break; }
        }
        if (recipe == null) return false;

        int totalCost = recipe.price() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);
        ItemStack towerBlock = PlayerKit.createTowerBlock(recipe, count);
        if (!serverPlayer.getInventory().add(towerBlock)) serverPlayer.drop(towerBlock, false);
        return true;
    }

    /** Shared tier/money/inventory logic for all purchasable items. */
    private boolean tryPurchase(Purchasable item, int count, ItemStack stack, ServerPlayer sp) {
        if (gameSession != null) {
            TierManager tierMgr = gameSession.getTierManager();
            int teamSide = gameSession.getTeamSide(sp.getUUID());
            if (teamSide >= 0 && item.getTier() > tierMgr.getCurrentTier(teamSide)) return false;
        }
        int total = item.getPrice() * count;
        if (!moneyManager.canAfford(total)) return false;
        moneyManager.spend(total);
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        return true;
    }

    public boolean buyWallBlock(int wallItemIndex, int count) {
        if (moneyManager == null || !(player instanceof ServerPlayer sp)) return false;
        List<WallShopItem> walls = WallShopItem.getAllSortedByPrice();
        if (wallItemIndex < 0 || wallItemIndex >= walls.size()) return false;
        WallShopItem item = walls.get(wallItemIndex);
        return tryPurchase(item, count, new ItemStack(item.block().asItem(), count), sp);
    }

    public boolean buySpawner(int spawnerIndex, int count) {
        if (moneyManager == null || !(player instanceof ServerPlayer sp)) return false;
        List<SpawnerType> spawners = SpawnerType.getAllSortedByPrice();
        if (spawnerIndex < 0 || spawnerIndex >= spawners.size()) return false;
        SpawnerType type = spawners.get(spawnerIndex);
        ItemStack spawnerStack = new ItemStack(type.getTriggerBlock().asItem(), count);
        spawnerStack.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.getName()).withStyle(ChatFormatting.AQUA));
        return tryPurchase(type, count, spawnerStack, sp);
    }

    public boolean buyGenerator(int genIndex, int count) {
        if (moneyManager == null || !(player instanceof ServerPlayer sp)) return false;
        List<IncomeGeneratorType> gens = IncomeGeneratorType.getAllSortedByPrice();
        if (genIndex < 0 || genIndex >= gens.size()) return false;
        IncomeGeneratorType type = gens.get(genIndex);
        ItemStack genStack = new ItemStack(type.getTriggerBlock().asItem(), count);
        genStack.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.getName()).withStyle(ChatFormatting.GOLD));
        return tryPurchase(type, count, genStack, sp);
    }

    public boolean buyWeapon(int weaponIndex, int count) {
        if (moneyManager == null || !(player instanceof ServerPlayer sp)) return false;
        List<WeaponShopItem> weapons = WeaponShopItem.getAllSortedByPrice();
        if (weaponIndex < 0 || weaponIndex >= weapons.size()) return false;
        WeaponShopItem weapon = weapons.get(weaponIndex);
        ItemStack stack = new ItemStack(weapon.item(), count);
        if (weapon.enchanted()) {
            var registry = sp.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> sharpness = registry.getOrThrow(Enchantments.SHARPNESS);
            stack.enchant(sharpness, 5);
        }
        return tryPurchase(weapon, count, stack, sp);
    }

    public boolean buySpell(int spellIndex, int count) {
        if (moneyManager == null || !(player instanceof ServerPlayer sp)) return false;
        List<SpellType> spells = SpellType.getAllSortedByPrice();
        if (spellIndex < 0 || spellIndex >= spells.size()) return false;
        SpellType type = spells.get(spellIndex);
        ItemStack stack = new ItemStack(type.getItem(), count);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.getName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        return tryPurchase(type, count, stack, sp);
    }

    public boolean buyUpgrade(int upgradeIndex) {
        if (moneyManager == null || gameSession == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        var entries = MobUpgradeManager.getAllUpgradeEntries();
        if (upgradeIndex < 0 || upgradeIndex >= entries.size()) return false;

        MobUpgradeManager mgr = gameSession.getMobUpgradeManager();
        int teamId = gameSession.getTeamSide(serverPlayer.getUUID());
        if (mgr == null || teamId < 0) return false;

        var entry = entries.get(upgradeIndex);
        int cost = mgr.getUpgradeCost(teamId, entry.mob(), entry.upgrade());
        if (!moneyManager.canAfford(cost)) return false;

        moneyManager.spend(cost);
        mgr.addLevel(teamId, entry.mob(), entry.upgrade());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (gameSession != null) return gameSession.isActive();
        return TowerDefenseMod.getInstance().getGameManager().isActive();
    }
}
