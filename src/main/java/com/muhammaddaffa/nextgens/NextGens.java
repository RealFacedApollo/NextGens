package com.muhammaddaffa.nextgens;

import com.bgsoftware.wildtools.api.WildToolsAPI;
import com.muhammaddaffa.mdlib.MDLib;
import com.muhammaddaffa.mdlib.configupdater.ConfigUpdater;
import com.muhammaddaffa.mdlib.hooks.VaultEconomy;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.mdlib.utils.SpigotUpdateChecker;
import com.muhammaddaffa.nextgens.api.GeneratorAPI;
import com.muhammaddaffa.nextgens.commands.*;
import com.muhammaddaffa.nextgens.database.DatabaseManager;
import com.muhammaddaffa.nextgens.events.managers.EventManager;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorListener;
import com.muhammaddaffa.nextgens.generators.managers.GeneratorManager;
import com.muhammaddaffa.nextgens.generators.runnables.CorruptionTask;
import com.muhammaddaffa.nextgens.generators.runnables.GeneratorTask;
import com.muhammaddaffa.nextgens.generators.runnables.NotifyTask;
import com.muhammaddaffa.nextgens.hooks.bento.BentoListener;
import com.muhammaddaffa.nextgens.hooks.papi.GensExpansion;
import com.muhammaddaffa.nextgens.hooks.ssb2.SSB2Listener;
import com.muhammaddaffa.nextgens.refund.RefundManager;
import com.muhammaddaffa.nextgens.sellwand.SellwandListener;
import com.muhammaddaffa.nextgens.sellwand.SellwandManager;
import com.muhammaddaffa.nextgens.users.managers.UserManager;
import com.muhammaddaffa.nextgens.worth.WorthManager;
import dev.norska.dsw.DeluxeSellwands;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NextGens extends JavaPlugin {

    private static final int BSTATS_ID = 19417;
    private static final int SPIGOT_ID = 111857;
    private static final int BUILTBYBIT_ID = 30903;

    private static NextGens instance;
    private static GeneratorAPI api;

    // -----------------------------
    // NamespacedKey Section
    public static NamespacedKey generator_id;
    public static NamespacedKey drop_value;
    public static NamespacedKey sellwand_global;
    public static NamespacedKey sellwand_multiplier;
    public static NamespacedKey sellwand_uses;
    public static NamespacedKey sellwand_total_sold;
    public static NamespacedKey sellwand_total_items;

    // End of NamespacedKey Section
    // ------------------------------

    private final DatabaseManager dbm = new DatabaseManager();
    private final EventManager eventManager = new EventManager();
    private final WorthManager worthManager = new WorthManager();
    private final GeneratorManager generatorManager = new GeneratorManager(this.dbm);
    private final UserManager userManager = new UserManager(this.dbm, this.eventManager);
    private final RefundManager refundManager = new RefundManager(this.generatorManager);
    private final SellwandManager sellwandManager = new SellwandManager(this.userManager);

    @Override
    public void onLoad() {
        MDLib.inject(this);
    }

    @Override
    public void onEnable() {
        MDLib.onEnable(this);
        // --------------------------------------------
        instance = this;
        generator_id = new NamespacedKey(this, "nextgens_generator_id");
        drop_value = new NamespacedKey(this, "nextgens_drop_value");
        sellwand_global = new NamespacedKey(this, "nextgens_sellwand_global");
        sellwand_multiplier = new NamespacedKey(this, "nextgens_sellwand_multiplier");
        sellwand_uses = new NamespacedKey(this, "nextgens_sellwand_uses");
        sellwand_total_sold = new NamespacedKey(this, "nextgens_sellwand_total_sold");
        sellwand_total_items = new NamespacedKey(this, "nextgens_sellwand_total_items");

        // fancy big text
        Logger.info("""
                NextGens plugin by aglerr - starting...
                
                ███╗░░██╗███████╗██╗░░██╗████████╗░██████╗░███████╗███╗░░██╗░██████╗
                ████╗░██║██╔════╝╚██╗██╔╝╚══██╔══╝██╔════╝░██╔════╝████╗░██║██╔════╝
                ██╔██╗██║█████╗░░░╚███╔╝░░░░██║░░░██║░░██╗░█████╗░░██╔██╗██║╚█████╗░
                ██║╚████║██╔══╝░░░██╔██╗░░░░██║░░░██║░░╚██╗██╔══╝░░██║╚████║░╚═══██╗
                ██║░╚███║███████╗██╔╝╚██╗░░░██║░░░╚██████╔╝███████╗██║░╚███║██████╔╝
                ╚═╝░░╚══╝╚══════╝╚═╝░░╚═╝░░░╚═╝░░░░╚═════╝░╚══════╝╚═╝░░╚══╝╚═════╝░
                """);

        // initialize stuff
        Config.registerConfig(new Config("config.yml", null, true));
        Config.registerConfig(new Config("generators.yml", null, true));
        Config.registerConfig(new Config("shop.yml", null, true));
        Config.registerConfig(new Config("upgrade_gui.yml", "gui", true));
        Config.registerConfig(new Config("corrupt_gui.yml", "gui", true));
        Config.registerConfig(new Config("events.yml", null, true));
        Config.registerConfig(new Config("data.yml", null, false));
        Config.registerConfig(new Config("worth.yml", null, true));

        VaultEconomy.init();

        // update config
        this.updateConfig();

        // connect to database and create the table
        this.dbm.connect();
        this.dbm.createGeneratorTable();
        this.dbm.createUserTable();

        Executor.sync(() -> {
            // register commands & listeners
            this.registerCommands();
            this.registerListeners();

            // load all generators
            this.generatorManager.loadGenerators();
            // delayed active generator load
            Executor.asyncLater(3L, this.generatorManager::loadActiveGenerator);

            // load users
            this.userManager.loadUser();

            // load the refund
            this.refundManager.load();
            this.refundManager.startTask();

            // load events
            this.eventManager.loadEvents();
            this.eventManager.load();
            this.eventManager.startTask();

            // worth system
            this.worthManager.load();

            // initialize the api
            api = new GeneratorAPI(this.generatorManager, this.refundManager, this.userManager, this.worthManager, this.sellwandManager);

            // register task
            this.registerTask();
            // register hook
            this.registerHook();
            // update checker
            this.updateCheck();
        });
    }

    @Override
    public void onDisable() {
        // shutdown the lib
        MDLib.shutdown();
        // remove all holograms
        GeneratorTask.flush();
        // save refunds
        this.refundManager.save();
        // save the generators
        this.generatorManager.saveActiveGenerator();
        // save the users
        this.userManager.saveUser();
        // save the events
        this.eventManager.save();
        // close the database
        this.dbm.close();
    }

    private void registerTask() {
        // start generator task
        GeneratorTask.start(this.generatorManager, this.eventManager);
        // start auto-save task
        this.generatorManager.startAutosaveTask();
        // corruption task
        CorruptionTask.start(this.generatorManager);
        // notify task
        NotifyTask.start(this.generatorManager);
    }

    private void registerHook() {
        PluginManager pm = Bukkit.getPluginManager();
        // papi hook
        if (pm.getPlugin("PlaceholderAPI") != null) {
            Logger.info("Found PlaceholderAPI! Registering hook...");
            new GensExpansion(this.generatorManager, this.userManager, this.eventManager).register();
        }
        if (pm.getPlugin("SuperiorSkyblock2") != null) {
            Logger.info("Found SuperiorSkyblock2! Registering hook...");
            pm.registerEvents(new SSB2Listener(this.generatorManager, this.refundManager), this);
        }
        if (pm.getPlugin("BentoBox") != null) {
            Logger.info("Found BentoBox! Registering hook...");
            pm.registerEvents(new BentoListener(this.generatorManager, this.refundManager), this);
        }
        if (pm.getPlugin("HolographicDisplays") != null) {
            Logger.info("Found HolographicDisplays! Registering hook...");
        }
        if (pm.getPlugin("DecentHolograms") != null) {
            Logger.info("Found DecentHolograms! Registering hook...");
        }
        if (pm.getPlugin("WildTools") != null && Config.getFileConfiguration("config.yml").getBoolean("sellwand.hooks.wildtools")) {
            Logger.info("Found WildTools! Registering hook...");
            WildToolsAPI.getWildTools().getProviders().setPricesProvider((player, stack) -> api.getWorth(stack));
        }
        if (pm.getPlugin("DeluxeSellwands") != null) {
            Logger.info("Found DeluxeSellwands! Registering hook...");
            DeluxeSellwands.getInstance().getPriceHandler().registerNewPriceHandler("NEXTGENS", (player, itemStack, i) -> {
                double value = api.getWorth(itemStack);
                return value <= 0 ? null :value;
            });
        }
        if (pm.getPlugin("LWC") != null) {
            Logger.info("Found LWC! Registering hook...");
        }
        // register bstats metrics hook
        this.connectMetrics();
    }

    private void updateConfig() {
        // check for auto config update
        if (!Config.getFileConfiguration("config.yml").getBoolean("auto-config-update", true)) {
            return;
        }

        File configFile = new File(this.getDataFolder(), "config.yml");
        File eventsFile = new File(this.getDataFolder(), "events.yml");

        try {
            ConfigUpdater.update(this, "config.yml", configFile, new ArrayList<>());
            ConfigUpdater.update(this, "events.yml", eventsFile, List.of("events.events"));
        } catch (IOException ex) {
            Logger.severe("Failed to update the config.yml!");
            ex.printStackTrace();
        }

        // reload the config afterward
        Config.reload();
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // register events
        pm.registerEvents(new GeneratorListener(this.generatorManager, this.userManager), this);
        pm.registerEvents(new SellwandListener(this.sellwandManager), this);
    }

    private void registerCommands() {
        // register commands
        MainCommand.register(this.generatorManager, this.userManager, this.eventManager, this.worthManager, this.sellwandManager);
        SellCommand.register(this.userManager);
        ShopCommand.register(this.generatorManager);
        PickupCommand.register(this.generatorManager);
        WorthCommand.registerThis();
    }

    private void connectMetrics() {
        // connect to bstats metrics
        Metrics metrics = new Metrics(this, BSTATS_ID);
        // add custom charts
        Map<String, String> data = new HashMap<>();
        data.put("corruption", "corruption.enabled");
        data.put("auto_save", "auto-save.enabled");
        data.put("place_permission", "place-permission");
        data.put("online_only", "online-only");
        data.put("anti_explosion", "anti-explosion");
        data.put("disable_drop_place", "disable-drop-place");
        data.put("shift_pickup", "shift-pickup");
        data.put("island_pickup", "island-pickup");
        data.put("upgrade_gui", "upgrade-gui");
        data.put("close_on_purchase", "close-on-purchase");
        data.put("close_on_no_money", "close-on-no-money");
        data.put("hook_shopguiplus", "sell-options.hook_shopguiplus");
        data.put("drop_on_break", "drop-on-break");
        data.put("broken_pickup", "broken-pickup");
        // create a custom charts
        data.forEach((id, path) -> {
            metrics.addCustomChart(this.createSimplePie(id, path));
        });
    }

    private SimplePie createSimplePie(String id, String path) {
        FileConfiguration config = Config.getFileConfiguration("config.yml");
        return new SimplePie(id, () -> this.yesOrNo(config.getBoolean(path)));
    }

    private void updateCheck(){
        Executor.async(() -> {
            SpigotUpdateChecker.init(this, SPIGOT_ID).requestUpdateCheck().whenComplete((result, exception) -> {
                if (result.requiresUpdate()) {
                    Logger.warning(
                            "----------------------------------------------------------------",
                            String.format("An update is available! NextGens %s may be downloaded on SpigotMC", result.getNewestVersion()),
                            String.format("* Current Version: %s", this.getDescription().getVersion()),
                            String.format("* Latest Version: %s", result.getNewestVersion()),
                            " ",
                            "Update the plugin at:",
                            "SpigotMC: https://www.spigotmc.org/resources/111857/",
                            "BuiltByBit: https://builtbybit.com/resources/30903/",
                            "----------------------------------------------------------------"
                    );
                    return;
                }

                if (result.getReason() == SpigotUpdateChecker.UpdateReason.UP_TO_DATE) {
                    Logger.finest(String.format("Your version of NextGens (%s) is up to date!", result.getNewestVersion()));
                } else if (result.getReason() == SpigotUpdateChecker.UpdateReason.UNRELEASED_VERSION) {
                    Logger.warning(
                            "----------------------------------------------------------------",
                            String.format("Your version of NextGens (%s) is more recent than the", result.getNewestVersion()),
                            "one publicly available. Are you on development build?",
                            "----------------------------------------------------------------"
                    );
                } else {
                    Logger.severe("Could not check for a new version of NextGens. Reason: " + result.getReason());
                }
            });
        });
    }

    private String yesOrNo(boolean status) {
        if (status) {
            return "yes";
        } else {
            return "no";
        }
    }

    public static GeneratorAPI getApi() {
        return api;
    }

    public static NextGens getInstance() {
        return instance;
    }

}
