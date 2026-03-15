package com.towerdefense.mixin;

import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameConfig;
import com.towerdefense.game.GameManager;
import com.towerdefense.game.IncomeGeneratorManager;
import com.towerdefense.game.OreManager;
import com.towerdefense.game.PlayerState;
import com.towerdefense.tower.TowerManager;
import com.towerdefense.wave.SpawnerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBreakMixin {

    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void towerdefense$onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        TowerDefenseMod mod = TowerDefenseMod.getInstance();
        if (mod == null) return;

        GameManager gm = mod.getGameManager();
        if (!gm.isActive()) return;

        if (isColosseumStructure(pos)) {
            cir.setReturnValue(false);
            return;
        }

        if (!GameConfig.isInsideArena(pos)) return;

        if (pos.getY() == GameConfig.ARENA_Y() || isArenaWall(pos)) {
            cir.setReturnValue(false);
            return;
        }

        // Protect both nexus structures
        var team1 = gm.getTeam1();
        var team2 = gm.getTeam2();
        if (team1 != null && team1.getNexusManager().isNexusBlock(pos)) { cir.setReturnValue(false); return; }
        if (team2 != null && team2.getNexusManager().isNexusBlock(pos)) { cir.setReturnValue(false); return; }

        // Tower: sell for 50% cash
        TowerManager tm = mod.getTowerManager();
        TowerManager.ActiveTower tower = tm.findTowerByBlock(pos);
        if (tower != null) {
            PlayerState ownerState = gm.getPlayerState(player);
            if (ownerState == null || tower.teamId() != ownerState.getSide()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Not your tower!").withStyle(ChatFormatting.RED));
                cir.setReturnValue(false);
                return;
            }
            int refund = tower.recipe().price() / 2;
            ownerState.getMoneyManager().addMoney(refund);
            tm.clearAndRemoveTower(tower);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Tower sold for $" + refund).withStyle(ChatFormatting.YELLOW));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.2f);
            cir.setReturnValue(true);
            return;
        }

        // Spawner: refund on break
        SpawnerManager sm = gm.getSpawnerManager();
        SpawnerManager.ActiveSpawner spawner = sm.findByBlock(pos);
        if (spawner != null) {
            PlayerState ps = gm.getPlayerState(player);
            if (ps == null || spawner.teamId() != ps.getSide()) {
                player.sendSystemMessage(Component.literal("You can't break another player's spawner!").withStyle(ChatFormatting.RED));
                cir.setReturnValue(false);
                return;
            }
            player.getInventory().add(new ItemStack(spawner.type().getTriggerBlock().asItem()));
            sm.removeSpawner(level, pos);
            player.sendSystemMessage(Component.literal("\u2716 " + spawner.type().getName() + " removed!").withStyle(ChatFormatting.RED));
            cir.setReturnValue(true);
            return;
        }

        // Generator: refund on break
        IncomeGeneratorManager igm = gm.getIncomeGeneratorManager();
        IncomeGeneratorManager.ActiveGenerator gen = igm.findByBlock(pos);
        if (gen != null) {
            PlayerState psGen = gm.getPlayerState(player);
            if (psGen == null || gen.teamId() != psGen.getSide()) {
                player.sendSystemMessage(Component.literal("You can't break another player's generator!").withStyle(ChatFormatting.RED));
                cir.setReturnValue(false);
                return;
            }
            player.getInventory().add(new ItemStack(gen.type().getTriggerBlock().asItem()));
            igm.removeGenerator(level, pos);
            player.sendSystemMessage(Component.literal("\u2716 " + gen.type().getName() + " removed!").withStyle(ChatFormatting.YELLOW));
            cir.setReturnValue(true);
            return;
        }

        // Ore: give income to player
        OreManager om = gm.getOreManager();
        Integer oreIncome = om.findOreAt(level, pos);
        if (oreIncome != null) {
            PlayerState ps = gm.getPlayerState(player);
            if (ps != null) {
                ps.getMoneyManager().addMoney(oreIncome);
                player.sendSystemMessage(Component.literal("+$" + oreIncome + " from ore!").withStyle(ChatFormatting.GOLD));
            }
            om.removeOre(level, pos);
            cir.setReturnValue(true);
            return;
        }
    }

    private boolean isArenaWall(BlockPos pos) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();
        int relX = pos.getX() - origin.getX();
        int relZ = pos.getZ() - origin.getZ();
        return relX == -1 || relX == size || relZ == -1 || relZ == size;
    }

    private boolean isColosseumStructure(BlockPos pos) {
        BlockPos origin = GameConfig.arenaOrigin;
        int size = GameConfig.ARENA_SIZE();
        int margin = GameConfig.STAND_DEPTH() + 4;
        int relX = pos.getX() - origin.getX();
        int relZ = pos.getZ() - origin.getZ();
        int relY = pos.getY() - GameConfig.ARENA_Y();

        boolean inBounds = relX >= -margin && relX <= size + margin
                && relZ >= -margin && relZ <= size + margin
                && relY >= 0 && relY <= GameConfig.STAND_HEIGHT() + 10;

        if (!inBounds) return false;

        boolean outsidePlayArea = relX < 0 || relX >= size || relZ < 0 || relZ >= size;
        return outsidePlayArea;
    }
}
