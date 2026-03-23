package com.towerdefense;

import com.towerdefense.command.TowerDefenseCommand;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.shop.WallShopItem;
import com.towerdefense.game.GameManager;
import com.towerdefense.handler.TowerPlaceHandler;
import com.towerdefense.network.ConfigSyncPayload;
import com.towerdefense.network.ShopNetworking;
import com.towerdefense.shop.ShopScreenHandler;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.tower.TowerRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.towerdefense.config.ConfigWebServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TowerDefenseMod implements ModInitializer {

    public static final String MOD_ID = "towerdefense";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TowerDefenseMod instance;

    private final TowerRegistry towerRegistry = new TowerRegistry();
    private final TowerManager towerManager = new TowerManager();
    private final GameManager gameManager;

    public TowerDefenseMod() {
        ConfigManager.getInstance().init(FabricLoader.getInstance().getConfigDir());
        this.gameManager = new GameManager();
    }

    public static MenuType<ShopScreenHandler> SHOP_SCREEN_HANDLER_TYPE;

    public static TowerDefenseMod getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TowerManager getTowerManager() {
        return towerManager;
    }

    public TowerRegistry getTowerRegistry() {
        return towerRegistry;
    }

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Tower Defense mod loading...");

        SHOP_SCREEN_HANDLER_TYPE = Registry.register(
                BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "shop"),
                new ExtendedScreenHandlerType<>(
                        (syncId, inventory, data) -> {
                            ShopScreenHandler handler = new ShopScreenHandler(syncId, inventory, null, getInstance().getTowerRegistry(), null);
                            handler.setInitialMoney(data);
                            return handler;
                        },
                        StreamCodec.of(
                                (buf, data) -> buf.writeInt(data),
                                buf -> buf.readInt()
                        )
                )
        );

        ShopNetworking.registerServer();

        towerRegistry.registerDefaults();

        gameManager.setTowerManager(towerManager);
        towerManager.setGameManager(gameManager);

        TowerPlaceHandler handler = new TowerPlaceHandler(towerRegistry, towerManager, gameManager);
        handler.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> ConfigWebServer.start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ConfigWebServer.stop());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            towerManager.tick();
            gameManager.tick();
        });

        TowerDefenseCommand command = new TowerDefenseCommand(gameManager, towerRegistry);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                command.register(dispatcher));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer sp && gameManager.isActive()) {
                if (gameManager.getSpellManager().tryUseSpell(sp, hand)) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Mob mob && mob.getTags().contains("td_mob")) {
                gameManager.onMobKilled(mob);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((netHandler, sender, server) ->
                sender.sendPacket(new ConfigSyncPayload(ConfigManager.getInstance().getConfig())));

        ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) -> {
            ServerPlayer player = netHandler.getPlayer();
            if (player != null) gameManager.onPlayerDisconnect(player);
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;
            if (WallShopItem.findByBlock(state.getBlock()) != null && gameManager.isActive()) {
                gameManager.getWallBlockManager().removeBlock(pos);
            }
        });

        LOGGER.info("Tower Defense mod loaded! {} tower recipes registered.", towerRegistry.getRecipes().size());
        for (var recipe : towerRegistry.getRecipes()) {
            LOGGER.info("  - {}: power={}, range={}, rate={}t, price=${}",
                    recipe.name(), recipe.power(), (int) recipe.range(), recipe.fireRateInTicks(), recipe.price());
        }
    }
}
