package com.towerdefense.client;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.IncomeGeneratorType;
import com.towerdefense.shop.ShopScreenHandler;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.shop.WeaponShopItem;
import com.towerdefense.spell.SpellType;
import com.towerdefense.tower.TowerRecipe;
import com.towerdefense.tower.TowerType;
import com.towerdefense.wave.MobType;
import com.towerdefense.wave.MobUpgradeManager;
import com.towerdefense.wave.SpawnerType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopScreen extends AbstractContainerScreen<ShopScreenHandler> {

    private static final int LEFT_W = 160;
    private static final int CENTER_W = 620;
    private static final int RIGHT_W = 130;
    private static final int PANEL_GAP = 6;
    private static final int TOTAL_W = LEFT_W + PANEL_GAP + CENTER_W + PANEL_GAP + RIGHT_W;

    private static final int ROW_H = 22;
    private static final int NAME_W = 130;
    private static final int X5_W = 26;
    private static final int MAX_W = 36;
    private static final int UPGRADE_BTN_W = 48;
    private static final int TOWER_UPGRADE_BTN_W = 90;
    private static final int GAP = 3;
    private static final int SECTION_GAP = 14;
    private static final int LABEL_H = 12;
    private static final int STATS_X_OFFSET = NAME_W + GAP + X5_W + GAP + MAX_W + 6;
    private static final int TOWER_STATS_X_OFFSET = NAME_W + GAP + X5_W + GAP + MAX_W + GAP + TOWER_UPGRADE_BTN_W + 12;
    private static final int SPAWNER_STATS_X_OFFSET = NAME_W + GAP + X5_W + GAP + MAX_W + GAP + UPGRADE_BTN_W * 3 + GAP * 2 + 12;

    private final List<Button> shopButtons = new ArrayList<>();
    private final List<Button> spellButtons = new ArrayList<>();
    private final List<Button> weaponButtons = new ArrayList<>();
    private final List<UpgradeBtn> spawnerUpgradeButtons = new ArrayList<>();
    private final List<UpgradeBtn> towerUpgradeButtons = new ArrayList<>();
    private final List<Map.Entry<SpawnerType, Button>> spawnerTooltipButtons = new ArrayList<>();
    private final List<Map.Entry<TowerType, Button>> towerTooltipButtons = new ArrayList<>();

    private Button nextTierButton = null;
    private int nextTierTarget = 2;

    private int centerHeight;
    private int panelTopY;
    private int leftPanelX;
    private int centerPanelX;
    private int rightPanelX;

    private record UpgradeBtn(Button button, int upgradeIndex) {}

    public ShopScreen(ShopScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = TOTAL_W;

        List<TowerRecipe> towers = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();

        int centerContentH = 40
                + towers.size() * ROW_H + SECTION_GAP + LABEL_H
                + WallShopItem.getAllSortedByPrice().size() * ROW_H + SECTION_GAP + LABEL_H
                + SpawnerType.getAllSortedByPrice().size() * ROW_H + SECTION_GAP + LABEL_H
                + IncomeGeneratorType.getAllSortedByPrice().size() * ROW_H + 30;

        int leftContentH = LABEL_H + 8 + SpellType.getAllSortedByPrice().size() * (ROW_H + 10)
                + SECTION_GAP + LABEL_H + 4 + WeaponShopItem.getAllSortedByPrice().size() * (ROW_H + 14) + 30;

        this.centerHeight = Math.max(centerContentH, leftContentH);
        this.imageHeight = centerHeight;
    }

    @Override
    protected void init() {
        super.init();
        shopButtons.clear();
        spellButtons.clear();
        weaponButtons.clear();
        spawnerUpgradeButtons.clear();
        towerUpgradeButtons.clear();
        spawnerTooltipButtons.clear();
        towerTooltipButtons.clear();
        nextTierButton = null;

        panelTopY = (this.height - centerHeight) / 2;
        int allLeft = (this.width - TOTAL_W) / 2;
        leftPanelX = allLeft;
        centerPanelX = allLeft + LEFT_W + PANEL_GAP;
        rightPanelX = allLeft + LEFT_W + PANEL_GAP + CENTER_W + PANEL_GAP;

        int cx = centerPanelX + 10;
        int cy = panelTopY + 40;

        // ─── Left panel: Spells + Weapons (all items) ───
        int lx = leftPanelX + 6;
        int ly = panelTopY + LABEL_H + 8;

        for (int i = 0; i < SpellType.getAllSortedByPrice().size(); i++) {
            final int idx = i;
            SpellType sp = SpellType.getAllSortedByPrice().get(i);
            addSpellRow(lx, ly, sp, idx);
            ly += ROW_H + 10;
        }

        ly += SECTION_GAP + LABEL_H + 4;
        for (int i = 0; i < WeaponShopItem.getAllSortedByPrice().size(); i++) {
            final int idx = i;
            WeaponShopItem w = WeaponShopItem.getAllSortedByPrice().get(i);
            Button btn = Button.builder(Component.literal(w.name() + " $" + w.price()),
                    b -> buyWeapon(idx, 1))
                    .bounds(lx, ly, LEFT_W - 12, ROW_H - 2).build();
            this.addRenderableWidget(btn);
            weaponButtons.add(btn);
            ly += ROW_H + 14;
        }

        // ─── Center panel (shop, all items) ───
        for (int i = 0; i < TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice().size(); i++) {
            final int idx = i;
            TowerRecipe r = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice().get(i);
            addTowerRow(cx, cy, r, idx);
            cy += ROW_H;
        }
        cy += SECTION_GAP + LABEL_H;

        for (int i = 0; i < WallShopItem.getAllSortedByPrice().size(); i++) {
            final int idx = i;
            WallShopItem w = WallShopItem.getAllSortedByPrice().get(i);
            addShopRow(cx, cy, w.name() + " $" + w.price(),
                    btn -> buyWall(idx, 1), btn -> buyWall(idx, 5), btn -> buyWallMax(idx));
            cy += ROW_H;
        }
        cy += SECTION_GAP + LABEL_H;

        for (int i = 0; i < SpawnerType.getAllSortedByPrice().size(); i++) {
            final int idx = i;
            SpawnerType s = SpawnerType.getAllSortedByPrice().get(i);
            addSpawnerRow(cx, cy, s, idx);
            cy += ROW_H;
        }
        cy += SECTION_GAP + LABEL_H;

        for (int i = 0; i < IncomeGeneratorType.getAllSortedByPrice().size(); i++) {
            final int idx = i;
            IncomeGeneratorType g = IncomeGeneratorType.getAllSortedByPrice().get(i);
            addShopRow(cx, cy, g.getName() + " $" + g.getPrice(),
                    btn -> buyGenerator(idx, 1), btn -> buyGenerator(idx, 5), btn -> buyGeneratorMax(idx));
            cy += ROW_H;
        }

        // ─── Right panel: Tiers ───
        int rx = rightPanelX + 6;
        int ry = panelTopY + LABEL_H + 8;
        int currentTierInit = Math.max(1, menu.getTierCurrent());
        if (currentTierInit < 3) {
            nextTierTarget = currentTierInit + 1;
            int cost = nextTierTarget == 2 ? ConfigManager.getInstance().getTier2Cost() : ConfigManager.getInstance().getTier3Cost();
            nextTierButton = Button.builder(Component.literal("Tier " + nextTierTarget + " $" + cost),
                            b -> ShopClientNetworking.sendTierBuyPacket(nextTierTarget))
                    .bounds(rx, ry, RIGHT_W - 12, ROW_H - 2).build();
            this.addRenderableWidget(nextTierButton);
        }

        updateButtonStates();
    }

    private void addSpellRow(int x, int y, SpellType sp, int idx) {
        int btnW = LEFT_W - 12;
        Button btn = Button.builder(Component.literal(sp.getName() + " $" + sp.getPrice()),
                b -> buySpell(idx, 1))
                .bounds(x, y, btnW, ROW_H - 2).build();
        this.addRenderableWidget(btn);
        spellButtons.add(btn);
    }

    private void addTowerRow(int x, int y, TowerRecipe r, int idx) {
        int baseX = x + NAME_W + GAP + X5_W + GAP + MAX_W + GAP;
        int beforeSize = shopButtons.size();
        addShopRow(x, y, r.name() + " $" + r.price(),
                btn -> buyTower(idx, 1), btn -> buyTower(idx, 5), btn -> buyTowerMax(idx));
        towerTooltipButtons.add(Map.entry(r.type(), shopButtons.get(beforeSize)));
        Button ub = Button.builder(Component.literal("UP"), b -> buyTowerUpgrade(idx))
                .bounds(baseX, y, TOWER_UPGRADE_BTN_W, ROW_H - 2).build();
        this.addRenderableWidget(ub);
        towerUpgradeButtons.add(new UpgradeBtn(ub, idx));
    }

    private void addSpawnerRow(int x, int y, SpawnerType s, int idx) {
        int baseX = x + NAME_W + GAP + X5_W + GAP + MAX_W + GAP;
        int mobOrd = s.getMobType().ordinal();
        int beforeSize = shopButtons.size();
        addShopRow(x, y, s.getName() + " $" + s.getPrice(),
                btn -> buySpawner(idx, 1), btn -> buySpawner(idx, 5), btn -> buySpawnerMax(idx));
        if (spawnerSpecialTooltip(s) != null) {
            spawnerTooltipButtons.add(Map.entry(s, shopButtons.get(beforeSize)));
        }
        String[] labels = {"+HP", "+SPD", "+DMG"};
        for (int u = 0; u < 3; u++) {
            final int upgradeIdx = mobOrd * 3 + u;
            Button ub = Button.builder(Component.literal(labels[u]), b -> buyUpgrade(upgradeIdx))
                    .bounds(baseX + u * (UPGRADE_BTN_W + GAP), y, UPGRADE_BTN_W, ROW_H - 2).build();
            this.addRenderableWidget(ub);
            spawnerUpgradeButtons.add(new UpgradeBtn(ub, upgradeIdx));
        }
    }

    private List<Component> spawnerSpecialTooltip(SpawnerType s) {
        return switch (s) {
            case CREEPER_SPAWNER -> List.of(
                    Component.literal("Special: Random Explosion").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Randomly explodes while walking,").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("destroying nearby enemy buildings.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Dies on explosion.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case RAVAGER_SPAWNER -> List.of(
                    Component.literal("Special: Block Breach").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Destroys blocks ahead when jumping,").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("breaching walls and towers.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case WITCH_SPAWNER -> List.of(
                    Component.literal("Special: Allied Heal").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Periodically heals nearby").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("allied mobs.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            default -> null;
        };
    }

    private List<Component> towerTooltip(TowerType t) {
        return switch (t) {
            case BASIC -> List.of(
                    Component.literal("Basic Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Shoots nearby mobs.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("No special effect.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
            );
            case ARCHER -> List.of(
                    Component.literal("Archer Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Long range, faster fire rate.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("No special effect.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
            );
            case CANNON -> List.of(
                    Component.literal("Cannon Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("High damage, slow reload.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Splash damage on impact.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case LASER -> List.of(
                    Component.literal("Laser Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Very long range, fast fire rate,").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("high single-target DPS.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case FIRE -> List.of(
                    Component.literal("Fire Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Sets mobs on fire on hit.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Deals burn damage over time.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case SLOW -> List.of(
                    Component.literal("Slow Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Applies Slowness to mobs on hit.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Deals no direct damage.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
            );
            case POISON -> List.of(
                    Component.literal("Poison Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Poisons mobs on hit.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Deals poison damage over time.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case SNIPER -> List.of(
                    Component.literal("Sniper Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Extreme range and damage,").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("very slow reload.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Best against high-HP targets.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case CHAIN_LIGHTNING -> List.of(
                    Component.literal("Chain Lightning Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Hits primary target then chains").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("to nearby mobs.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
            case AOE -> List.of(
                    Component.literal("AOE Tower").withStyle(net.minecraft.ChatFormatting.YELLOW),
                    Component.literal("Deals damage in a large area").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("around the target.").withStyle(net.minecraft.ChatFormatting.GRAY)
            );
        };
    }

    private void addShopRow(int x, int y, String label, Button.OnPress buy1, Button.OnPress buy5, Button.OnPress buyMax) {
        Button b1 = Button.builder(Component.literal(label), buy1).bounds(x, y, NAME_W, ROW_H - 2).build();
        Button b5 = Button.builder(Component.literal("x5"), buy5).bounds(x + NAME_W + GAP, y, X5_W, ROW_H - 2).build();
        Button bMax = Button.builder(Component.literal("MAX"), buyMax).bounds(x + NAME_W + GAP + X5_W + GAP, y, MAX_W, ROW_H - 2).build();
        this.addRenderableWidget(b1);
        this.addRenderableWidget(b5);
        this.addRenderableWidget(bMax);
        shopButtons.add(b1);
        shopButtons.add(b5);
        shopButtons.add(bMax);
    }

    @Override
    protected void containerTick() {
        updateButtonStates();
    }

    private void updateButtonStates() {
        int money = menu.getMoney();
        var entries = MobUpgradeManager.getAllUpgradeEntries();
        int tier = Math.max(1, menu.getTierCurrent());
        int pending = menu.getTierPending();

        // Shop items: active only if tier unlocked AND enough money
        int idx = 0;
        for (TowerRecipe r : TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice()) {
            boolean tierOk = r.type().getTier() <= tier;
            setTriple(idx, tierOk, money >= r.price(), r.type().getTier());
            idx += 3;
        }
        for (WallShopItem w : WallShopItem.getAllSortedByPrice()) {
            boolean tierOk = w.getTier() <= tier;
            setTriple(idx, tierOk, money >= w.price(), w.getTier());
            idx += 3;
        }
        for (SpawnerType s : SpawnerType.getAllSortedByPrice()) {
            boolean tierOk = s.getTier() <= tier;
            setTriple(idx, tierOk, money >= s.getPrice(), s.getTier());
            idx += 3;
        }
        for (IncomeGeneratorType g : IncomeGeneratorType.getAllSortedByPrice()) {
            boolean tierOk = g.getTier() <= tier;
            setTriple(idx, tierOk, money >= g.getPrice(), g.getTier());
            idx += 3;
        }

        for (int i = 0; i < spellButtons.size(); i++) {
            SpellType sp = SpellType.getAllSortedByPrice().get(i);
            spellButtons.get(i).active = sp.getTier() <= tier && money >= sp.getPrice();
        }

        for (int i = 0; i < weaponButtons.size(); i++) {
            WeaponShopItem w = WeaponShopItem.getAllSortedByPrice().get(i);
            weaponButtons.get(i).active = w.getTier() <= tier && money >= w.price();
        }

        for (UpgradeBtn ub : spawnerUpgradeButtons) {
            if (ub.upgradeIndex() >= entries.size()) continue;
            var e = entries.get(ub.upgradeIndex());
            SpawnerType spawnerForMob = java.util.Arrays.stream(SpawnerType.values())
                    .filter(s -> s.getMobType() == e.mob()).findFirst().orElse(null);
            boolean tierOk = spawnerForMob == null || spawnerForMob.getTier() <= tier;
            int level = menu.getUpgradeLevel(ub.upgradeIndex());
            int baseCost = ConfigManager.getInstance().getUpgradeBaseCost(e.upgrade());
            int cost = baseCost + level * baseCost;
            ub.button().active = tierOk && money >= cost;
            String lbl = switch (e.upgrade()) { case HP -> "+HP"; case SPEED -> "+SPD"; case DAMAGE -> "+DMG"; };
            ub.button().setMessage(Component.literal(lbl + " $" + cost));
        }

        var towerRecipes = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();
        for (UpgradeBtn ub : towerUpgradeButtons) {
            if (ub.upgradeIndex() >= towerRecipes.size()) continue;
            TowerRecipe r = towerRecipes.get(ub.upgradeIndex());
            boolean tierOk = r.type().getTier() <= tier;
            int level = menu.getTowerUpgradeLevel(ub.upgradeIndex());
            int baseCost = ConfigManager.getInstance().getTowerUpgradeBaseCost();
            int cost = baseCost + level * baseCost;
            ub.button().active = tierOk && money >= cost;
            ub.button().setMessage(Component.literal("UP Lv" + (level + 1) + " $" + cost));
        }

        // Single next-tier button: show only when no pending and tier < 3
        if (nextTierButton != null) {
            if (tier >= 3 || pending > 0) {
                nextTierButton.visible = false;
            } else {
                int nextTier = tier + 1;
                nextTierTarget = nextTier;
                int cost = nextTier == 2 ? ConfigManager.getInstance().getTier2Cost() : ConfigManager.getInstance().getTier3Cost();
                nextTierButton.visible = true;
                nextTierButton.active = money >= cost;
                nextTierButton.setMessage(Component.literal("Tier " + nextTier + " $" + cost));
            }
        }
    }

    private void setTriple(int startIdx, boolean tierOk, boolean affordable, int requiredTier) {
        boolean active = tierOk && affordable;
        if (startIdx < shopButtons.size()) shopButtons.get(startIdx).active = active;
        if (startIdx + 1 < shopButtons.size()) {
            Button b = shopButtons.get(startIdx + 1);
            b.active = active;
            b.setMessage(Component.literal(tierOk ? "x5" : "T" + requiredTier));
        }
        if (startIdx + 2 < shopButtons.size()) {
            Button b = shopButtons.get(startIdx + 2);
            b.active = active;
            b.setMessage(Component.literal(tierOk ? "MAX" : "T" + requiredTier));
        }
    }

    // ─── Buy actions ───

    private void buyTower(int idx, int qty) {
        var t = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();
        if (idx >= t.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, t.get(idx).price())));
        if (q > 0) ShopClientNetworking.sendBuyPacket(t.get(idx).type(), q);
    }
    private void buyTowerMax(int idx) { buyTower(idx, 64); }

    private void buyWall(int idx, int qty) {
        var w = WallShopItem.getAllSortedByPrice();
        if (idx >= w.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, w.get(idx).price())));
        if (q > 0) ShopClientNetworking.sendWallBuyPacket(idx, q);
    }
    private void buyWallMax(int idx) { buyWall(idx, 64); }

    private void buySpawner(int idx, int qty) {
        var s = SpawnerType.getAllSortedByPrice();
        if (idx >= s.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, s.get(idx).getPrice())));
        if (q > 0) ShopClientNetworking.sendSpawnerBuyPacket(idx, q);
    }
    private void buySpawnerMax(int idx) { buySpawner(idx, 64); }

    private void buyGenerator(int idx, int qty) {
        var g = IncomeGeneratorType.getAllSortedByPrice();
        if (idx >= g.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, g.get(idx).getPrice())));
        if (q > 0) ShopClientNetworking.sendGeneratorBuyPacket(idx, q);
    }
    private void buyGeneratorMax(int idx) { buyGenerator(idx, 64); }

    private void buySpell(int idx, int qty) {
        var s = SpellType.getAllSortedByPrice();
        if (idx >= s.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, s.get(idx).getPrice())));
        if (q > 0) ShopClientNetworking.sendSpellBuyPacket(idx, q);
    }

    private void buyWeapon(int idx, int qty) {
        var w = WeaponShopItem.getAllSortedByPrice();
        if (idx >= w.size()) return;
        int q = Math.min(qty, Math.min(64, menu.getMoney() / Math.max(1, w.get(idx).price())));
        if (q > 0) ShopClientNetworking.sendWeaponBuyPacket(idx, q);
    }

    private void buyUpgrade(int idx) { ShopClientNetworking.sendUpgradeBuyPacket(idx); }
    private void buyTowerUpgrade(int idx) { ShopClientNetworking.sendTowerUpgradeBuyPacket(idx); }

    // ─── Rendering ───

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        // Left panel (Spells + Weapons)
        graphics.fill(leftPanelX, panelTopY, leftPanelX + LEFT_W, panelTopY + centerHeight, 0xDD000000);
        graphics.fill(leftPanelX + 2, panelTopY + 2, leftPanelX + LEFT_W - 2, panelTopY + LABEL_H + 4, 0xFF331111);

        // Center panel
        graphics.fill(centerPanelX, panelTopY, centerPanelX + CENTER_W, panelTopY + centerHeight, 0xDD000000);
        graphics.fill(centerPanelX + 2, panelTopY + 2, centerPanelX + CENTER_W - 2, panelTopY + 35, 0xFF222222);

        // Right panel (Tiers)
        graphics.fill(rightPanelX, panelTopY, rightPanelX + RIGHT_W, panelTopY + centerHeight, 0xDD000000);
        graphics.fill(rightPanelX + 2, panelTopY + 2, rightPanelX + RIGHT_W - 2, panelTopY + LABEL_H + 4, 0xFF111133);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);

        int statsX = centerPanelX + 10 + STATS_X_OFFSET;
        int towerStatsX = centerPanelX + 10 + TOWER_STATS_X_OFFSET;

        // ─── Left panel (Spells + Weapons) ───
        graphics.drawCenteredString(this.font, "SPELLS", leftPanelX + LEFT_W / 2, panelTopY + 3, 0xFF5555);

        graphics.enableScissor(leftPanelX, panelTopY, leftPanelX + LEFT_W, panelTopY + centerHeight);
        int sy = panelTopY + LABEL_H + 8;
        for (SpellType sp : SpellType.getAllSortedByPrice()) {
            graphics.drawString(this.font, sp.getDescription(), leftPanelX + 6, sy + ROW_H, 0x888888);
            sy += ROW_H + 10;
        }
        sy += SECTION_GAP;
        graphics.drawString(this.font, "-- Weapons --", leftPanelX + 6, sy, 0xFFAAAA);
        sy += LABEL_H + 4;
        for (WeaponShopItem w : WeaponShopItem.getAllSortedByPrice()) {
            graphics.drawString(this.font, w.description(), leftPanelX + 6, sy + ROW_H, 0x888888);
            sy += ROW_H + 14;
        }
        graphics.disableScissor();

        // ─── Right panel (Tiers) ───
        int tier = Math.max(1, menu.getTierCurrent());
        int pendingTier = menu.getTierPending();
        int ticksLeft = menu.getTierTicksRemaining();
        graphics.drawCenteredString(this.font, "TIERS", rightPanelX + RIGHT_W / 2, panelTopY + 3, 0xFFAA55);
        int ry = panelTopY + LABEL_H + ROW_H + 12;
        graphics.drawString(this.font, "T1: OK", rightPanelX + 6, ry, 0x00FF00);
        ry += ROW_H;
        if (tier >= 2) {
            graphics.drawString(this.font, "T2: OK", rightPanelX + 6, ry, 0x00FF00);
            ry += ROW_H;
        } else if (pendingTier == 2 && ticksLeft > 0) {
            int secs = (ticksLeft + 19) / 20;
            graphics.drawString(this.font, "T2: " + secs + "s...", rightPanelX + 6, ry, 0xFFAA00);
            ry += ROW_H;
        }
        if (tier >= 3) {
            graphics.drawString(this.font, "T3: OK", rightPanelX + 6, ry, 0x00FF00);
        } else if (pendingTier == 3 && ticksLeft > 0) {
            int secs = (ticksLeft + 19) / 20;
            graphics.drawString(this.font, "T3: " + secs + "s...", rightPanelX + 6, ry, 0xFFAA00);
        }

        // ─── Center panel ───
        graphics.drawCenteredString(this.font, "SHOP", centerPanelX + CENTER_W / 2, panelTopY + 8, 0xFFD700);
        graphics.drawString(this.font, "$ " + menu.getMoney(), centerPanelX + 10, panelTopY + 22, 0x00FF00);

        var towers = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();
        var cfg = ConfigManager.getInstance();
        int y = panelTopY + 40;
        for (int i = 0; i < towers.size(); i++) {
            TowerRecipe r = towers.get(i);
            int level = menu.getTowerUpgradeLevel(i);
            int power = (int) Math.round(r.power() * (1.0 + level * cfg.getTowerPowerMultiplierPerLevel()));
            int fireRate = Math.max(1, (int) Math.round(r.fireRateInTicks() * Math.max(0.1, 1.0 - level * cfg.getTowerFireRateMultiplierPerLevel())));
            String lvlStr = level > 0 ? " Lv" + (level + 1) : "";
            graphics.drawString(this.font, "DMG:" + power + " RNG:" + (int) r.range() + " SPD:" + String.format("%.1f", fireRate / 20.0) + "s" + lvlStr,
                    towerStatsX, y + 3, 0xAAAAAA);
            y += ROW_H;
        }
        y += SECTION_GAP;
        graphics.drawString(this.font, "-- Walls --", centerPanelX + 10, y, 0x55FFFF);
        y += LABEL_H + WallShopItem.getAllSortedByPrice().size() * ROW_H + SECTION_GAP;
        graphics.drawString(this.font, "-- Spawners --", centerPanelX + 10, y, 0xFF55FF);
        y += LABEL_H;

        int spawnerStatsX = centerPanelX + 10 + SPAWNER_STATS_X_OFFSET;
        double hpMult = ConfigManager.getInstance().getHpMultiplierPerLevel();
        double spdMult = ConfigManager.getInstance().getSpeedMultiplierPerLevel();
        for (SpawnerType s : SpawnerType.getAllSortedByPrice()) {
            MobType m = s.getMobType();
            int mobIdx = m.ordinal();
            int hpLvl = menu.getUpgradeLevel(mobIdx * 3);
            int spdLvl = menu.getUpgradeLevel(mobIdx * 3 + 1);
            int dmgLvl = menu.getUpgradeLevel(mobIdx * 3 + 2);
            int hp = (int) (m.getBaseHp() * (1.0 + hpLvl * hpMult));
            int dmg = m.getNexusDamage() + dmgLvl;
            String spdStr = String.format("%.2f", m.getSpeed() * (1.0 + spdLvl * spdMult));
            graphics.drawString(this.font, "HP:" + hp + " DMG:" + dmg + " SPD:" + spdStr + " /" + (s.getSpawnIntervalTicks() / 20) + "s",
                    spawnerStatsX, y + 3, 0xAAAAAA);
            y += ROW_H;
        }
        y += SECTION_GAP;
        graphics.drawString(this.font, "-- Generators --", centerPanelX + 10, y, 0xFFFF55);
        y += LABEL_H;

        for (IncomeGeneratorType g : IncomeGeneratorType.getAllSortedByPrice()) {
            graphics.drawString(this.font, "+$" + g.getIncomeAmount() + " / " + (g.getIncomeIntervalTicks() / 20) + "s",
                    statsX, y + 3, 0xAAAAAA);
            y += ROW_H;
        }

        // Tower tooltips
        for (var entry : towerTooltipButtons) {
            if (entry.getValue().isHovered()) {
                List<Component> lines = towerTooltip(entry.getKey());
                if (lines != null) graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }
        // Spawner special ability tooltips
        for (var entry : spawnerTooltipButtons) {
            if (entry.getValue().isHovered()) {
                List<Component> lines = spawnerSpecialTooltip(entry.getKey());
                if (lines != null) graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                break;
            }
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}
    @Override public boolean isPauseScreen() { return false; }
}
