package com.towerdefense.shop;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameManager;
import com.towerdefense.game.TierManager;
import com.towerdefense.game.IncomeGeneratorType;
import com.towerdefense.game.MoneyManager;
import com.towerdefense.game.PlayerKit;
import com.towerdefense.game.PlayerState;
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

    public ShopScreenHandler(int syncId, Inventory playerInventory, MoneyManager moneyManager, TowerRegistry towerRegistry) {
        super(TowerDefenseMod.SHOP_SCREEN_HANDLER_TYPE, syncId);
        this.player = playerInventory.player;
        this.towerRegistry = towerRegistry;
        this.moneyManager = moneyManager;

        if (moneyManager != null && player instanceof ServerPlayer sp) {
            GameManager gm = TowerDefenseMod.getInstance().getGameManager();
            MobUpgradeManager mgr = gm.getMobUpgradeManager();
            PlayerState ps = gm.getPlayerState(sp);
            int teamId = ps != null ? ps.getSide() : 1;

            TowerUpgradeManager towerMgr = gm.getTowerUpgradeManager();
            TierManager tierMgr = gm.getTierManager();
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
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        var recipes = towerRegistry.getRecipes();
        if (towerIndex < 0 || towerIndex >= recipes.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TowerUpgradeManager mgr = gm.getTowerUpgradeManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps == null || mgr == null) return false;

        TowerType type = recipes.get(towerIndex).type();
        int cost = mgr.getUpgradeCost(ps.getSide(), type);
        if (!moneyManager.canAfford(cost)) return false;

        moneyManager.spend(cost);
        mgr.addLevel(ps.getSide(), type);
        return true;
    }

    public boolean buyTierUpgrade(int targetTier) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps == null) return false;

        return tierMgr.buyTierUpgrade(serverPlayer, moneyManager, targetTier);
    }

    public boolean buyTower(TowerType type, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && type.getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

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

    public boolean buyWallBlock(int wallItemIndex, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        List<WallShopItem> walls = WallShopItem.getAllSortedByPrice();
        if (wallItemIndex < 0 || wallItemIndex >= walls.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && walls.get(wallItemIndex).getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

        WallShopItem item = walls.get(wallItemIndex);
        int totalCost = item.price() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);
        ItemStack stack = new ItemStack(item.block().asItem(), count);
        if (!serverPlayer.getInventory().add(stack)) serverPlayer.drop(stack, false);
        return true;
    }

    public boolean buySpawner(int spawnerIndex, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        List<SpawnerType> spawners = SpawnerType.getAllSortedByPrice();
        if (spawnerIndex < 0 || spawnerIndex >= spawners.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && spawners.get(spawnerIndex).getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

        SpawnerType type = spawners.get(spawnerIndex);
        int totalCost = type.getPrice() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);
        ItemStack stack = new ItemStack(type.getTriggerBlock().asItem(), count);
        if (!serverPlayer.getInventory().add(stack)) serverPlayer.drop(stack, false);
        return true;
    }

    public boolean buyGenerator(int genIndex, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        List<IncomeGeneratorType> gens = IncomeGeneratorType.getAllSortedByPrice();
        if (genIndex < 0 || genIndex >= gens.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && gens.get(genIndex).getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

        IncomeGeneratorType type = gens.get(genIndex);
        int totalCost = type.getPrice() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);
        ItemStack stack = new ItemStack(type.getTriggerBlock().asItem(), count);
        if (!serverPlayer.getInventory().add(stack)) serverPlayer.drop(stack, false);
        return true;
    }

    public boolean buyWeapon(int weaponIndex, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        List<WeaponShopItem> weapons = WeaponShopItem.getAllSortedByPrice();
        if (weaponIndex < 0 || weaponIndex >= weapons.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && weapons.get(weaponIndex).getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

        WeaponShopItem weapon = weapons.get(weaponIndex);
        int totalCost = weapon.price() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);
        ItemStack stack = new ItemStack(weapon.item(), count);

        if (weapon.enchanted()) {
            var registry = serverPlayer.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> sharpness = registry.getOrThrow(Enchantments.SHARPNESS);
            stack.enchant(sharpness, 5);
        }

        if (!serverPlayer.getInventory().add(stack)) serverPlayer.drop(stack, false);
        return true;
    }

    public boolean buySpell(int spellIndex, int count) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        List<SpellType> spells = SpellType.getAllSortedByPrice();
        if (spellIndex < 0 || spellIndex >= spells.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        TierManager tierMgr = gm.getTierManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps != null && spells.get(spellIndex).getTier() > tierMgr.getCurrentTier(ps.getSide())) return false;

        SpellType type = spells.get(spellIndex);
        int totalCost = type.getPrice() * count;
        if (!moneyManager.canAfford(totalCost)) return false;

        moneyManager.spend(totalCost);

        ItemStack stack = new ItemStack(type.getItem(), count);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.getName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        if (!serverPlayer.getInventory().add(stack)) serverPlayer.drop(stack, false);
        return true;
    }

    public boolean buyUpgrade(int upgradeIndex) {
        if (moneyManager == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        var entries = MobUpgradeManager.getAllUpgradeEntries();
        if (upgradeIndex < 0 || upgradeIndex >= entries.size()) return false;

        GameManager gm = TowerDefenseMod.getInstance().getGameManager();
        MobUpgradeManager mgr = gm.getMobUpgradeManager();
        PlayerState ps = gm.getPlayerState(serverPlayer);
        if (ps == null || mgr == null) return false;

        var entry = entries.get(upgradeIndex);
        int cost = mgr.getUpgradeCost(ps.getSide(), entry.mob(), entry.upgrade());
        if (!moneyManager.canAfford(cost)) return false;

        moneyManager.spend(cost);
        mgr.addLevel(ps.getSide(), entry.mob(), entry.upgrade());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return TowerDefenseMod.getInstance().getGameManager().isActive();
    }
}
