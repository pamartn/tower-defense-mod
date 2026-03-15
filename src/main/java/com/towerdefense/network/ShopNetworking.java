package com.towerdefense.network;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.game.IncomeGeneratorType;
import com.towerdefense.game.MoneyManager;
import com.towerdefense.game.PlayerState;
import com.towerdefense.spell.SpellType;
import com.towerdefense.wave.MobUpgradeManager;
import com.towerdefense.shop.ShopScreenHandler;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.shop.WeaponShopItem;
import com.towerdefense.tower.TowerType;
import com.towerdefense.wave.SpawnerType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class ShopNetworking {

    public static final ResourceLocation BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_buy");
    public static final ResourceLocation WALL_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_wall_buy");
    public static final ResourceLocation SPAWNER_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_spawner_buy");
    public static final ResourceLocation GENERATOR_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_generator_buy");
    public static final ResourceLocation WEAPON_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_weapon_buy");
    public static final ResourceLocation SPELL_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_spell_buy");
    public static final ResourceLocation UPGRADE_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_upgrade_buy");
    public static final ResourceLocation TOWER_UPGRADE_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_tower_upgrade_buy");
    public static final ResourceLocation TIER_BUY_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "shop_tier_buy");
    public static final ResourceLocation OPEN_SHOP_PACKET_ID = ResourceLocation.fromNamespaceAndPath(TowerDefenseMod.MOD_ID, "open_shop");

    public record ShopBuyPayload(int towerTypeOrdinal, int quantity) implements CustomPacketPayload {
        public static final Type<ShopBuyPayload> TYPE = new Type<>(BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, ShopBuyPayload> CODEC =
            StreamCodec.of(ShopBuyPayload::write, ShopBuyPayload::read);
        public static ShopBuyPayload read(FriendlyByteBuf buf) { return new ShopBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, ShopBuyPayload p) { buf.writeInt(p.towerTypeOrdinal); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WallBuyPayload(int wallItemIndex, int quantity) implements CustomPacketPayload {
        public static final Type<WallBuyPayload> TYPE = new Type<>(WALL_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, WallBuyPayload> CODEC =
            StreamCodec.of(WallBuyPayload::write, WallBuyPayload::read);
        public static WallBuyPayload read(FriendlyByteBuf buf) { return new WallBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, WallBuyPayload p) { buf.writeInt(p.wallItemIndex); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SpawnerBuyPayload(int spawnerIndex, int quantity) implements CustomPacketPayload {
        public static final Type<SpawnerBuyPayload> TYPE = new Type<>(SPAWNER_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, SpawnerBuyPayload> CODEC =
            StreamCodec.of(SpawnerBuyPayload::write, SpawnerBuyPayload::read);
        public static SpawnerBuyPayload read(FriendlyByteBuf buf) { return new SpawnerBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, SpawnerBuyPayload p) { buf.writeInt(p.spawnerIndex); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GeneratorBuyPayload(int genIndex, int quantity) implements CustomPacketPayload {
        public static final Type<GeneratorBuyPayload> TYPE = new Type<>(GENERATOR_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, GeneratorBuyPayload> CODEC =
            StreamCodec.of(GeneratorBuyPayload::write, GeneratorBuyPayload::read);
        public static GeneratorBuyPayload read(FriendlyByteBuf buf) { return new GeneratorBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, GeneratorBuyPayload p) { buf.writeInt(p.genIndex); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WeaponBuyPayload(int weaponIndex, int quantity) implements CustomPacketPayload {
        public static final Type<WeaponBuyPayload> TYPE = new Type<>(WEAPON_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, WeaponBuyPayload> CODEC =
            StreamCodec.of(WeaponBuyPayload::write, WeaponBuyPayload::read);
        public static WeaponBuyPayload read(FriendlyByteBuf buf) { return new WeaponBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, WeaponBuyPayload p) { buf.writeInt(p.weaponIndex); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SpellBuyPayload(int spellIndex, int quantity) implements CustomPacketPayload {
        public static final Type<SpellBuyPayload> TYPE = new Type<>(SPELL_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, SpellBuyPayload> CODEC =
            StreamCodec.of(SpellBuyPayload::write, SpellBuyPayload::read);
        public static SpellBuyPayload read(FriendlyByteBuf buf) { return new SpellBuyPayload(buf.readInt(), buf.readInt()); }
        public static void write(FriendlyByteBuf buf, SpellBuyPayload p) { buf.writeInt(p.spellIndex); buf.writeInt(p.quantity); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpgradeBuyPayload(int upgradeIndex) implements CustomPacketPayload {
        public static final Type<UpgradeBuyPayload> TYPE = new Type<>(UPGRADE_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, UpgradeBuyPayload> CODEC =
            StreamCodec.of(UpgradeBuyPayload::write, UpgradeBuyPayload::read);
        public static UpgradeBuyPayload read(FriendlyByteBuf buf) { return new UpgradeBuyPayload(buf.readInt()); }
        public static void write(FriendlyByteBuf buf, UpgradeBuyPayload p) { buf.writeInt(p.upgradeIndex); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TowerUpgradeBuyPayload(int towerIndex) implements CustomPacketPayload {
        public static final Type<TowerUpgradeBuyPayload> TYPE = new Type<>(TOWER_UPGRADE_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, TowerUpgradeBuyPayload> CODEC =
            StreamCodec.of(TowerUpgradeBuyPayload::write, TowerUpgradeBuyPayload::read);
        public static TowerUpgradeBuyPayload read(FriendlyByteBuf buf) { return new TowerUpgradeBuyPayload(buf.readInt()); }
        public static void write(FriendlyByteBuf buf, TowerUpgradeBuyPayload p) { buf.writeInt(p.towerIndex); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TierBuyPayload(int targetTier) implements CustomPacketPayload {
        public static final Type<TierBuyPayload> TYPE = new Type<>(TIER_BUY_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, TierBuyPayload> CODEC =
            StreamCodec.of(TierBuyPayload::write, TierBuyPayload::read);
        public static TierBuyPayload read(FriendlyByteBuf buf) { return new TierBuyPayload(buf.readInt()); }
        public static void write(FriendlyByteBuf buf, TierBuyPayload p) { buf.writeInt(p.targetTier); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenShopPayload() implements CustomPacketPayload {
        public static final Type<OpenShopPayload> TYPE = new Type<>(OPEN_SHOP_PACKET_ID);
        public static final StreamCodec<FriendlyByteBuf, OpenShopPayload> CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenShopPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopBuyPayload.TYPE, ShopBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WallBuyPayload.TYPE, WallBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpawnerBuyPayload.TYPE, SpawnerBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GeneratorBuyPayload.TYPE, GeneratorBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WeaponBuyPayload.TYPE, WeaponBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpellBuyPayload.TYPE, SpellBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpgradeBuyPayload.TYPE, UpgradeBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TowerUpgradeBuyPayload.TYPE, TowerUpgradeBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TierBuyPayload.TYPE, TierBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenShopPayload.TYPE, OpenShopPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ShopBuyPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.containerMenu instanceof ShopScreenHandler sh) {
                    TowerType[] types = TowerType.values();
                    if (payload.towerTypeOrdinal >= 0 && payload.towerTypeOrdinal < types.length) {
                        sh.buyTower(types[payload.towerTypeOrdinal], payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(WallBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.wallItemIndex >= 0 && payload.wallItemIndex < WallShopItem.getAllSortedByPrice().size()) {
                        sh.buyWallBlock(payload.wallItemIndex, payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SpawnerBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.spawnerIndex >= 0 && payload.spawnerIndex < SpawnerType.getAllSortedByPrice().size()) {
                        sh.buySpawner(payload.spawnerIndex, payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GeneratorBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.genIndex >= 0 && payload.genIndex < IncomeGeneratorType.getAllSortedByPrice().size()) {
                        sh.buyGenerator(payload.genIndex, payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(WeaponBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.weaponIndex >= 0 && payload.weaponIndex < WeaponShopItem.getAllSortedByPrice().size()) {
                        sh.buyWeapon(payload.weaponIndex, payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SpellBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.spellIndex >= 0 && payload.spellIndex < SpellType.getAllSortedByPrice().size()) {
                        sh.buySpell(payload.spellIndex, payload.quantity);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpgradeBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.upgradeIndex >= 0 && payload.upgradeIndex < MobUpgradeManager.getAllUpgradeEntries().size()) {
                        sh.buyUpgrade(payload.upgradeIndex);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TowerUpgradeBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    var recipes = TowerDefenseMod.getInstance().getTowerRegistry().getRecipesSortedByPrice();
                    if (payload.towerIndex >= 0 && payload.towerIndex < recipes.size()) {
                        sh.buyTowerUpgrade(payload.towerIndex);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TierBuyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof ShopScreenHandler sh) {
                    if (payload.targetTier >= 2 && payload.targetTier <= 3) {
                        sh.buyTierUpgrade(payload.targetTier);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenShopPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                PlayerState ps = TowerDefenseMod.getInstance().getGameManager().getPlayerState(player);
                if (ps == null) {
                    player.sendSystemMessage(Component.literal("Shop is not available - start a game first!"));
                    return;
                }
                MoneyManager moneyManager = ps.getMoneyManager();

                ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigManager.getInstance().getConfig()));
                player.openMenu(new ExtendedScreenHandlerFactory<Integer>() {
                    @Override public Component getDisplayName() { return Component.literal("Tower Shop"); }
                    @Override public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                        return new ShopScreenHandler(syncId, inv, moneyManager, TowerDefenseMod.getInstance().getTowerRegistry());
                    }
                    @Override public Integer getScreenOpeningData(ServerPlayer p) { return moneyManager.getMoney(); }
                });
            });
        });
    }
}
