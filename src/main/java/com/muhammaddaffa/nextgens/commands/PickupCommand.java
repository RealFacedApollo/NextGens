package com.muhammaddaffa.nextgens.commands;

import com.muhammaddaffa.mdlib.commandapi.CommandAPICommand;
import com.muhammaddaffa.mdlib.commandapi.arguments.PlayerArgument;
import com.muhammaddaffa.mdlib.utils.Common;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Placeholder;
import com.muhammaddaffa.mdlib.xseries.XSound;
import com.muhammaddaffa.nextgens.NextGens;
import com.muhammaddaffa.nextgens.objects.ActiveGenerator;
import com.muhammaddaffa.nextgens.managers.GeneratorManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class PickupCommand {

    public static void register(GeneratorManager generatorManager) {
        // check if the command is enabled
        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("commands.pickup.enabled")) {
            return;
        }
        PickupCommand command = new PickupCommand(generatorManager);
        // register the command
        command.register();
    }

    private final GeneratorManager generatorManager;
    private final CommandAPICommand command;
    public PickupCommand(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;

        // get variables we need
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        String mainCommand = config.getString("commands.pickup.command");
        List<String> aliases = config.getStringList("commands.pickup.aliases");

        this.command = new CommandAPICommand(mainCommand)
                .withOptionalArguments(new PlayerArgument("target"))
                .executes((sender, args) -> {
                    Player target = (Player) args.get("target");
                    Player actualTarget;
                    // early stop to prevent dupe
                    if (NextGens.STOPPING) return;
                    if (target == null) {
                        if (!sender.hasPermission("nextgens.pickup")) {
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
                        if (!sender.hasPermission("nextgens.pickup.others")) {
                            NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.no-permission");
                            return;
                        }
                        actualTarget = target;
                    }
                    // get all generators player have
                    List<ActiveGenerator> generators = this.generatorManager.getActiveGenerator(actualTarget);
                    int total = generators.size();
                    // loop generators
                    for (ActiveGenerator active : generators) {
                        // check if broken pickup option is enabled
                        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("broken-pickup") && active.isCorrupted()) {
                            total--;
                            continue;
                        }
                        // unregister the generator
                        this.generatorManager.unregisterGenerator(active.getLocation());
                        // set the block to air
                        active.getLocation().getBlock().setType(Material.AIR);
                        // give the item to the player
                        Common.addInventoryItem(actualTarget, active.getGenerator().createItem(1));
                    }
                    // send message
                    NextGens.DEFAULT_CONFIG.sendMessage(sender, "messages.force-pickup", new Placeholder()
                            .add("{player}", actualTarget.getName())
                            .add("{amount}", Common.digits(total)));
                    NextGens.DEFAULT_CONFIG.sendMessage(actualTarget, "messages.pickup-gens", new Placeholder()
                            .add("{amount}", Common.digits(total)));
                    // send sound
                    actualTarget.playSound(actualTarget.getLocation(), XSound.ENTITY_PLAYER_LEVELUP.get(), 1.0f, 2.0f);
                });
        this.command.setAliases(aliases.toArray(new String[0]));
    }

    public void register() {
        this.command.register();
    }

}
