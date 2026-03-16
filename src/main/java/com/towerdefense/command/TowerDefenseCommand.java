package com.towerdefense.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.towerdefense.TowerDefenseMod;
import com.towerdefense.game.GameLobby;
import com.towerdefense.game.GameManager;
import com.towerdefense.game.MoneyManager;
import com.towerdefense.game.PlayerState;
import com.towerdefense.shop.ShopScreenHandler;
import com.towerdefense.tower.TowerRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.UUID;

public class TowerDefenseCommand {

    private final GameManager gameManager;
    private final TowerRegistry towerRegistry;

    public TowerDefenseCommand(GameManager gameManager, TowerRegistry towerRegistry) {
        this.gameManager = gameManager;
        this.towerRegistry = towerRegistry;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("td")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.literal("start").executes(this::start))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::invite)))
                        .then(Commands.literal("join")
                                .executes(this::joinLobby)
                                .then(Commands.argument("team", IntegerArgumentType.integer(1, 2))
                                        .executes(ctx -> joinSwitchTeam(ctx, IntegerArgumentType.getInteger(ctx, "team"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::joinRequest)))
                        .then(Commands.literal("accept").executes(this::accept))
                        .then(Commands.literal("stop").executes(this::stop))
                        .then(Commands.literal("status").executes(this::status))
                        .then(Commands.literal("shop").executes(this::shop))
                        .then(Commands.literal("test")
                                .requires(source -> source.hasPermission(2))
                                .executes(this::testMode))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("money")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(this::setMoney)))
                                .then(Commands.literal("tier")
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                                .executes(this::setTier))))
        );
    }

    private int start(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        if (!gameManager.isActive()) {
            gameManager.startGame(caller);
            return 1;
        }
        if (gameManager.isLobby() && gameManager.isHost(caller.getUUID())) {
            int t1 = gameManager.getTeam1() != null ? gameManager.getTeam1().getMemberCount() : 0;
            int t2 = gameManager.getTeam2() != null ? gameManager.getTeam2().getMemberCount() : 0;
            GameLobby lobby = gameManager.getLobby();
            if (lobby != null && lobby.canStartSolo(t1, t2)) {
                gameManager.startSoloMode(caller);
                return 1;
            }
            if (gameManager.startGameFromLobby(caller)) {
                return 1;
            }
            caller.sendSystemMessage(Component.literal("Need at least 2 players to start.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        caller.sendSystemMessage(Component.literal("A game is already running!")
                .withStyle(ChatFormatting.RED));
        return 0;
    }

    private int invite(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer host = ctx.getSource().getPlayer();
        if (host == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }

        if (!gameManager.isLobby()) {
            host.sendSystemMessage(Component.literal("No lobby to invite to. Use /td start first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        GameLobby lobby = gameManager.getLobby();
        if (lobby == null || !gameManager.isHost(host.getUUID())) {
            host.sendSystemMessage(Component.literal("Only the host can invite players.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        lobby.invite(host.getUUID(), target.getUUID());
        host.sendSystemMessage(Component.literal("Invited " + target.getName().getString() + " to the game.")
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(host.getName().getString() + " invited you! Use /td accept to join.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int joinLobby(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("No game running. Someone must use /td start first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!gameManager.isLobby()) {
            caller.sendSystemMessage(Component.literal("Game already started. Use /td join <player> to request joining.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (gameManager.getPlayerState(caller) != null) {
            caller.sendSystemMessage(Component.literal("You are already in the game! Use /td join 1 or /td join 2 to switch teams.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        int t1 = gameManager.getTeam1() != null ? gameManager.getTeam1().getMemberCount() : 0;
        int t2 = gameManager.getTeam2() != null ? gameManager.getTeam2().getMemberCount() : 0;
        int targetTeam = t1 <= t2 ? 1 : 2;
        gameManager.addPlayerToGame(caller, targetTeam);
        caller.sendSystemMessage(Component.literal("You joined Team " + targetTeam + "! Host can start with /td start when ready.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int joinSwitchTeam(CommandContext<CommandSourceStack> ctx, int teamArg) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("No game running. Someone must use /td start first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerState ps = gameManager.getPlayerState(caller);
        if (ps == null) {
            caller.sendSystemMessage(Component.literal("Use /td join <player> to request joining a game, then they use /td accept.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int currentTeam = ps.getSide();
        if (teamArg == currentTeam) {
            caller.sendSystemMessage(Component.literal("You are already in Team " + teamArg + "!")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (!gameManager.isLobby() && !gameManager.isPrepPhase()) {
            caller.sendSystemMessage(Component.literal("You can only switch teams during lobby or prep phase.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        gameManager.removePlayerFromGame(caller.getUUID());
        gameManager.addPlayerToGame(caller, teamArg);
        caller.sendSystemMessage(Component.literal("Switched to Team " + teamArg + "!")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int joinRequest(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("No game running. Someone must use /td start first.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (gameManager.getPlayerState(caller) != null) {
            caller.sendSystemMessage(Component.literal("You are already in the game! Use /td join 1 or /td join 2 to switch teams.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (!gameManager.isPlayerInGame(target.getUUID())) {
            caller.sendSystemMessage(Component.literal(target.getName().getString() + " is not in a game.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (caller.getUUID().equals(target.getUUID())) {
            caller.sendSystemMessage(Component.literal("You cannot request to join yourself.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        gameManager.addPendingJoinRequest(caller.getUUID(), target.getUUID());
        caller.sendSystemMessage(Component.literal("Join request sent to " + target.getName().getString() + ". Wait for them to /td accept.")
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(caller.getName().getString() + " wants to join your game. Use /td accept to accept.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int accept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("No game running.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Case 1: Someone requested to join caller (td join <caller>)
        UUID requester = gameManager.getAndRemovePendingJoinRequest(caller.getUUID());
        if (requester != null) {
            ServerPlayer requesterPlayer = ctx.getSource().getServer().getPlayerList().getPlayer(requester);
            if (requesterPlayer != null && requesterPlayer.isAlive()) {
                int t1 = gameManager.getTeam1() != null ? gameManager.getTeam1().getMemberCount() : 0;
                int t2 = gameManager.getTeam2() != null ? gameManager.getTeam2().getMemberCount() : 0;
                int targetTeam = t1 <= t2 ? 1 : 2;
                gameManager.addPlayerToGame(requesterPlayer, targetTeam);
                requesterPlayer.sendSystemMessage(Component.literal("You joined Team " + targetTeam + "!")
                        .withStyle(ChatFormatting.GREEN));
                caller.sendSystemMessage(Component.literal(requesterPlayer.getName().getString() + " joined the game!")
                        .withStyle(ChatFormatting.GREEN));
                return 1;
            }
        }

        // Case 2: Caller was invited (td invite <caller>)
        GameLobby lobby = gameManager.getLobby();
        if (lobby != null && lobby.getMostRecentInviteFor(caller.getUUID()) != null) {
            lobby.removeInvite(caller.getUUID());
            int t1 = gameManager.getTeam1() != null ? gameManager.getTeam1().getMemberCount() : 0;
            int t2 = gameManager.getTeam2() != null ? gameManager.getTeam2().getMemberCount() : 0;
            int targetTeam = t1 <= t2 ? 1 : 2;
            gameManager.addPlayerToGame(caller, targetTeam);
            caller.sendSystemMessage(Component.literal("You joined Team " + targetTeam + "!")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }

        caller.sendSystemMessage(Component.literal("No pending join request or invite.")
                .withStyle(ChatFormatting.YELLOW));
        return 0;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("No game running.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (gameManager.isLobby() && !gameManager.isHost(caller.getUUID())) {
            caller.sendSystemMessage(Component.literal("Only the host can stop the lobby.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        gameManager.stopGame();
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) return 0;
        caller.sendSystemMessage(Component.literal("\u2694 " + gameManager.getStatusText())
                .withStyle(ChatFormatting.AQUA));
        if (gameManager.isActive() && gameManager.getTeam1() != null && gameManager.getTeam2() != null) {
            int t1 = gameManager.getTeam1().getMemberCount();
            int t2 = gameManager.getTeam2().getMemberCount();
            caller.sendSystemMessage(Component.literal("  Team 1: " + t1 + " | Team 2: " + t2)
                    .withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private int testMode(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        if (!gameManager.isLobby()) {
            ctx.getSource().sendFailure(Component.literal("Test mode can only be started from the lobby (/td start first, then /td test)."));
            return 0;
        }
        if (!gameManager.isHost(caller.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Only the lobby host can start test mode."));
            return 0;
        }
        gameManager.startTestMode(caller);
        return 1;
    }

    private int setMoney(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        if (!gameManager.isActive()) {
            ctx.getSource().sendFailure(Component.literal("No game running."));
            return 0;
        }
        PlayerState ps = gameManager.getPlayerState(caller);
        if (ps == null) {
            ctx.getSource().sendFailure(Component.literal("You are not part of the current game."));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ps.getMoneyManager().setMoney(amount);
        caller.sendSystemMessage(Component.literal("Money set to $" + amount).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int setTier(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) {
            ctx.getSource().sendFailure(Component.literal("Player-only command"));
            return 0;
        }
        if (!gameManager.isActive()) {
            ctx.getSource().sendFailure(Component.literal("No game running."));
            return 0;
        }
        PlayerState ps = gameManager.getPlayerState(caller);
        if (ps == null) {
            ctx.getSource().sendFailure(Component.literal("You are not part of the current game."));
            return 0;
        }
        int tier = IntegerArgumentType.getInteger(ctx, "tier");
        gameManager.getTierManager().forceSetTier(ps.getSide(), tier);
        caller.sendSystemMessage(Component.literal("Tier set to " + tier + " for your team.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int shop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer caller = ctx.getSource().getPlayer();
        if (caller == null) return 0;

        if (!gameManager.isActive()) {
            caller.sendSystemMessage(Component.literal("Start a game first with /td start")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PlayerState ps = gameManager.getPlayerState(caller);
        if (ps == null) {
            caller.sendSystemMessage(Component.literal("You are not part of the current game!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        MoneyManager moneyManager = ps.getMoneyManager();

        caller.openMenu(new ExtendedScreenHandlerFactory<Integer>() {
            @Override public Component getDisplayName() { return Component.literal("Tower Shop"); }
            @Override public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                return new ShopScreenHandler(syncId, inv, moneyManager, towerRegistry);
            }
            @Override public Integer getScreenOpeningData(ServerPlayer p) { return moneyManager.getMoney(); }
        });

        return 1;
    }
}
