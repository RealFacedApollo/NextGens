package com.muhammaddaffa.nextgens.commands;

import com.muhammaddaffa.mdlib.commandapi.CommandAPICommand;
import com.muhammaddaffa.mdlib.commandapi.arguments.PlayerArgument;
import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.gui.ShopInventory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class ShopCommand {

    public static void register(GeneratorManager generatorManager) {
        // check if the command is enabled
        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("commands.shop.enabled")) {
            return;
        }
        ShopCommand command = new ShopCommand(generatorManager);
        // register the command
        command.register();
    }

    private final GeneratorManager generatorManager;
    private final CommandAPICommand command;
    public ShopCommand(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;

        // get variables we need
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        String mainCommand = config.getString("commands.shop.command");
        List<String> aliases = config.getStringList("commands.shop.aliases");

        this.command = new CommandAPICommand(mainCommand)
                .withOptionalArguments(new PlayerArgument("target"))
                .executes((sender, args) -> {
                    Player target = (Player) args.get("target");
                    Player actualTarget = null;
                    if (target == null) {
                        if (!sender.hasPermission("nextgens.shop")) {
                            NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.no-permission");
                            return;
                        }
                        if (!(sender instanceof Player player)) {
                            Common.sendMessage(sender, "&cUsage: /{command} <player>", new Placeholder()
                                    .add("{command}", mainCommand));
                            return;
                        }
                        actualTarget = player;

                    } else {
                        if (!sender.hasPermission("nextgens.shop.others")) {
                            NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.no-permission");
                            return;
                        }
                        actualTarget = target;
                    }
                    // open the gui for the target
                    ShopInventory.openInventory(actualTarget, this.generatorManager);
                });
        this.command.setAliases(aliases.toArray(new String[0]));
    }

    public void register() {
        this.command.register();
    }

}
