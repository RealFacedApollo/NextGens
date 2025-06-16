package com.muhammaddaffa.nextgens.commands;

import com.muhammaddaffa.mdlib.commandapi.CommandAPIBukkit;
import com.muhammaddaffa.mdlib.commandapi.CommandAPICommand;
import com.muhammaddaffa.mdlib.commandapi.arguments.PlayerArgument;
import com.muhammaddaffa.mdlib.utils.*;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.managers.UserManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class SellCommand {

    public static void register(UserManager userManager) {
        SellCommand command = new SellCommand(userManager);
        // check if sell command is enabled
        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("sell-command")) {
            return;
        }
        Executor.sync(() -> {
            Logger.info("Sell command is enabled, overriding and registering sell command...");
            // unregister the command
            try {
                CommandAPIBukkit.unregister("sell", true, true);
            } catch (Exception ignored) { }
            // register back the command
            command.register();
        });
    }

    private final UserManager userManager;
    private final CommandAPICommand command;
    public SellCommand(UserManager userManager) {
        this.userManager = userManager;

        // get variables we need
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        String mainCommand = config.getString("commands.sell.command");
        List<String> aliases = config.getStringList("commands.sell.aliases");

        this.command = new CommandAPICommand(mainCommand)
                .withOptionalArguments(new PlayerArgument("target"))
                .executes((sender, args) -> {
                    Player target = (Player) args.get("target");
                    Player finalTarget = null;
                    if (target == null) {
                        if (!sender.hasPermission("nextgens.sell")) {
                            NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.no-permission");
                            return;
                        }
                        if (!(sender instanceof Player player)) {
                            Common.sendMessage(sender, "&cUsage: /{command} <player>", new Placeholder()
                                    .add("{command}", mainCommand));
                            return;
                        }
                        // sell the item to the player
                        finalTarget = player;
                    } else {
                        if (!sender.hasPermission("nextgens.sell.others")) {
                            NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.no-permission");
                            return;
                        }
                        finalTarget = target;
                    }
                    // perform the sell
                    NextGens.getInstance().getSellManager().performSell(finalTarget, null, finalTarget.getInventory());
                });
        this.command.setAliases(aliases.toArray(new String[0]));
    }

    public void register() {
        this.command.register();
    }

}
