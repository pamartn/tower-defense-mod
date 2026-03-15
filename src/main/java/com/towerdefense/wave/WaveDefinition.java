package com.towerdefense.wave;

import com.towerdefense.game.GameConfig;

import java.util.ArrayList;
import java.util.List;

public record WaveDefinition(int waveNumber, List<MobEntry> mobs) {

    public record MobEntry(MobType type, int count) {}

    public static WaveDefinition forWave(int wave) {
        List<MobEntry> entries = new ArrayList<>();

        int totalMobs = (int) (GameConfig.BASE_MOB_COUNT * Math.pow(GameConfig.MOB_COUNT_SCALING, (wave - 1) / 3.0));

        if (wave <= 3) {
            entries.add(new MobEntry(MobType.ZOMBIE, totalMobs));
        } else if (wave <= 6) {
            int zombies = (int) (totalMobs * 0.6);
            int skeletons = totalMobs - zombies;
            entries.add(new MobEntry(MobType.ZOMBIE, zombies));
            entries.add(new MobEntry(MobType.SKELETON, skeletons));
        } else if (wave <= 9) {
            int zombies = (int) (totalMobs * 0.4);
            int skeletons = (int) (totalMobs * 0.3);
            int spiders = totalMobs - zombies - skeletons;
            entries.add(new MobEntry(MobType.ZOMBIE, zombies));
            entries.add(new MobEntry(MobType.SKELETON, skeletons));
            entries.add(new MobEntry(MobType.SPIDER, spiders));
        } else {
            int zombies = (int) (totalMobs * 0.3);
            int skeletons = (int) (totalMobs * 0.25);
            int spiders = (int) (totalMobs * 0.25);
            int remaining = totalMobs - zombies - skeletons - spiders;
            entries.add(new MobEntry(MobType.ZOMBIE, zombies));
            entries.add(new MobEntry(MobType.SKELETON, skeletons));
            entries.add(new MobEntry(MobType.SPIDER, spiders));

            int ravagers = Math.max(1, remaining / 3);
            int extraZombies = remaining - ravagers;
            entries.add(new MobEntry(MobType.RAVAGER, ravagers));
            if (extraZombies > 0) {
                entries.set(0, new MobEntry(MobType.ZOMBIE, zombies + extraZombies));
            }
        }

        return new WaveDefinition(wave, entries);
    }

    public int totalMobCount() {
        return mobs.stream().mapToInt(MobEntry::count).sum();
    }

    public double hpMultiplier() {
        return 1.0 + GameConfig.MOB_HP_SCALING * (waveNumber - 1);
    }
}
