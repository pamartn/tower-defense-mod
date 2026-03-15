package com.towerdefense.client;

import com.towerdefense.network.ShopNetworking;
import com.towerdefense.tower.TowerType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ShopClientNetworking {

    public static void sendBuyPacket(TowerType type, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.ShopBuyPayload(type.ordinal(), quantity));
    }

    public static void sendWallBuyPacket(int wallItemIndex, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.WallBuyPayload(wallItemIndex, quantity));
    }

    public static void sendSpawnerBuyPacket(int spawnerIndex, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.SpawnerBuyPayload(spawnerIndex, quantity));
    }

    public static void sendGeneratorBuyPacket(int genIndex, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.GeneratorBuyPayload(genIndex, quantity));
    }

    public static void sendWeaponBuyPacket(int weaponIndex, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.WeaponBuyPayload(weaponIndex, quantity));
    }

    public static void sendSpellBuyPacket(int spellIndex, int quantity) {
        ClientPlayNetworking.send(new ShopNetworking.SpellBuyPayload(spellIndex, quantity));
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
