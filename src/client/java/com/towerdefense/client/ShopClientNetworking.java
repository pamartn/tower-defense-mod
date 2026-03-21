package com.towerdefense.client;

import com.towerdefense.network.ShopNetworking;
import com.towerdefense.tower.TowerType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ShopClientNetworking {

    public static void sendBuyPacket(TowerType type, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.ShopBuyPayload(type.ordinal(), pick));
    }

    public static void sendWallBuyPacket(int wallItemIndex, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.WallBuyPayload(wallItemIndex, pick));
    }

    public static void sendSpawnerBuyPacket(int spawnerIndex, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.SpawnerBuyPayload(spawnerIndex, pick));
    }

    public static void sendGeneratorBuyPacket(int genIndex, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.GeneratorBuyPayload(genIndex, pick));
    }

    public static void sendWeaponBuyPacket(int weaponIndex, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.WeaponBuyPayload(weaponIndex, pick));
    }

    public static void sendSpellBuyPacket(int spellIndex, boolean pick) {
        ClientPlayNetworking.send(new ShopNetworking.SpellBuyPayload(spellIndex, pick));
    }

    public static void sendUpgradeBuyPacket(int upgradeIndex) {
        ClientPlayNetworking.send(new ShopNetworking.UpgradeBuyPayload(upgradeIndex));
    }

    public static void sendTowerUpgradeBuyPacket(int towerIndex) {
        ClientPlayNetworking.send(new ShopNetworking.TowerUpgradeBuyPayload(towerIndex));
    }

    public static void sendTierBuyPacket(int targetTier) {
        ClientPlayNetworking.send(new ShopNetworking.TierBuyPayload(targetTier));
    }
}
