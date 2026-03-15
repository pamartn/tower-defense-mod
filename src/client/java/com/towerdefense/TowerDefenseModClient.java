package com.towerdefense;

import com.mojang.blaze3d.platform.InputConstants;
import com.towerdefense.client.ShopScreen;
import com.towerdefense.config.ConfigManager;
import com.towerdefense.network.ConfigSyncPayload;
import com.towerdefense.network.ShopNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

public class TowerDefenseModClient implements ClientModInitializer {

    private static KeyMapping shopKeyBinding;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ConfigManager.getInstance().applyServerConfig(payload.config());
                TowerDefenseMod.getInstance().getTowerRegistry().reloadFromConfig();
            });
        });

        MenuScreens.register(TowerDefenseMod.SHOP_SCREEN_HANDLER_TYPE, ShopScreen::new);

        shopKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.towerdefense.shop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.towerdefense"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (shopKeyBinding.consumeClick()) {
                if (client.player != null && client.getConnection() != null) {
                    ClientPlayNetworking.send(new ShopNetworking.OpenShopPayload());
                }
            }
        });
    }
}
