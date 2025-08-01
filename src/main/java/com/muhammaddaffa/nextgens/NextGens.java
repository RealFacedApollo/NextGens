package com.muhammaddaffa.nextgens;

import com.bgsoftware.wildtools.api.WildToolsAPI;
import com.muhammaddaffa.mdlib.MDLib;
import com.muhammaddaffa.mdlib.configupdater.ConfigUpdater;
import com.muhammaddaffa.mdlib.utils.Config;
import com.muhammaddaffa.mdlib.utils.Executor;
import com.muhammaddaffa.mdlib.utils.Logger;
import com.muhammaddaffa.mdlib.utils.updatechecker.UpdateCheckSource;
import com.muhammaddaffa.mdlib.utils.updatechecker.UpdateChecker;
import com.muhammaddaffa.nextgens.api.GeneratorAPI;
import com.muhammaddaffa.nextgens.commands.*;
import com.muhammaddaffa.nextgens.generators.runnables.CorruptionTask;
import com.muhammaddaffa.nextgens.generators.runnables.GeneratorTask;
import com.muhammaddaffa.nextgens.generators.runnables.NotifyTask;
import com.muhammaddaffa.nextgens.hooks.papi.GensExpansion;
import com.muhammaddaffa.nextgens.listeners.*;
import com.muhammaddaffa.nextgens.managers.*;
import com.muhammaddaffa.nextgens.sell.multipliers.SellMultiplierRegistry;
import com.muhammaddaffa.nextgens.users.UserRepository;
import com.muhammaddaffa.nextgens.utils.Settings;
import dev.norska.dsw.DeluxeSellwands;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
    private final GeneratorManager generatorManager = new GeneratorManager(dbm);
    private final UserManager userManager = new UserManager();
    private final UserRepository userRepository = new UserRepository(dbm, userManager);
    private final SellManager sellManager = new SellManager(userManager, eventManager);
    private final RefundManager refundManager = new RefundManager(generatorManager);
    private final SellwandManager sellwandManager = new SellwandManager();
    private final AutosellManager autosellManager = new AutosellManager(userManager);
    private final SellMultiplierRegistry sellMultiplierRegistry = new SellMultiplierRegistry();

    public static Config DEFAULT_CONFIG, GENERATORS_CONFIG, SHOP_CONFIG, UPGRADE_GUI_CONFIG, CORRUPT_GUI_CONFIG, EVENTS_CONFIG, DATA_CONFIG,
            WORTH_CONFIG, SETTINGS_GUI_CONFIG, VIEW_GUI_CONFIG;

    public static boolean STOPPING = false;

    @Override
    public void onLoad() {
        MDLib.inject(this);
    }

    @Override
    public void onEnable() {
        MDLib.onEnable(this);
        // --------------------------------------------
        instance = this;

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

        // initialize keys
        keys();

        // initialize configs and update
        configs();
        update();

        // initialize settings
        Settings.init();

        // connect to database and create the table
        this.dbm.connect();
        this.dbm.createGeneratorTable();
        this.dbm.createUserTable();

        // register commands & listeners
        commands();
        listeners();

        // register task
        tasks();
        // register hook
        hooks();

        // load all generators
        this.generatorManager.loadGenerators();

        // system to reload generators to fix some issues
        Executor.syncLater(20L, () -> {
            // load back the generators
            this.generatorManager.loadGenerators();
            // refresh the active generator
            Executor.async(this.generatorManager::refreshActiveGenerator);
        });

        Executor.asyncLater(3L, () -> {
            // load active generators
            this.generatorManager.loadActiveGenerator();
            // load users
            this.userRepository.loadUsers();

            // load the refund
            this.refundManager.load();

            // load events
            this.eventManager.loadEvents();
            this.eventManager.load();
            this.eventManager.startTask();

            // worth system
            this.worthManager.load();

            // update checker
            updateCheck();
        });
    }

    @Override
    public void onDisable() {
        STOPPING = true;
        // shutdown the lib
        MDLib.shutdown();
        // remove all holograms
        GeneratorTask.flush();
        // save all other things
        save();
        // close the database
        this.dbm.close();
    }

    private void save() {
        // save small things first
        this.refundManager.saveAll();
        this.eventManager.save();
    }

    private void keys() {
        generator_id = new NamespacedKey(this, "nextgens_generator_id");
        drop_value = new NamespacedKey(this, "nextgens_drop_value");
        sellwand_global = new NamespacedKey(this, "nextgens_sellwand_global");
        sellwand_multiplier = new NamespacedKey(this, "nextgens_sellwand_multiplier");
        sellwand_uses = new NamespacedKey(this, "nextgens_sellwand_uses");
        sellwand_total_sold = new NamespacedKey(this, "nextgens_sellwand_total_sold");
        sellwand_total_items = new NamespacedKey(this, "nextgens_sellwand_total_items");
    }

    private void tasks() {
        // start generator task
        GeneratorTask.start(this.generatorManager, this.eventManager, this.userManager);
        // corruption task
        CorruptionTask.start(this.generatorManager);
        // notify task
        NotifyTask.start(this.generatorManager);
        // autosell task
        this.autosellManager.startTask();
    }

    private void hooks() {
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
        if (pm.getPlugin("LuckySkyblock") != null) {
            Logger.info("Found LuckySkyblock! Registering hook...");
            pm.registerEvents(new LuckySkyblockListener(this.generatorManager, this.refundManager), this);
        }
        if (pm.getPlugin("HolographicDisplays") != null) {
            Logger.info("Found HolographicDisplays! Registering hook...");
        }
        if (pm.getPlugin("DecentHolograms") != null) {
            Logger.info("Found DecentHolograms! Registering hook...");
        }
        if (pm.getPlugin("WildTools") != null && NextGens.DEFAULT_CONFIG.getBoolean("sellwand.hooks.wildtools")) {
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
        // register bstats metrics hook
        this.connectMetrics();
    }

    private void update() {
        // check for auto config update
        if (!NextGens.DEFAULT_CONFIG.getConfig().getBoolean("auto-config-update", true)) {
            return;
        }

        File configFile = new File(this.getDataFolder(), "config.yml");
        File eventsFile = new File(this.getDataFolder(), "events.yml");

        try {
            ConfigUpdater.update(this, "config.yml", configFile, List.of("sell-options.sound"));
            ConfigUpdater.update(this, "events.yml", eventsFile, List.of("events.events"));
        } catch (IOException ex) {
            Logger.severe("Failed to update the config.yml!");
            ex.printStackTrace();
        }
        // reload the config afterward
        Config.reload();
    }

    private void configs() {
        DEFAULT_CONFIG          = new Config("config.yml", null, true);
        GENERATORS_CONFIG       = new Config("generators.yml", null, true);
        SHOP_CONFIG             = new Config("shop.yml", null, true);
        UPGRADE_GUI_CONFIG      = new Config("upgrade_gui.yml", "gui", true);
        CORRUPT_GUI_CONFIG      = new Config("corrupt_gui.yml", "gui", true);
        EVENTS_CONFIG           = new Config("events.yml", null, true);
        DATA_CONFIG             = new Config("data.yml", null, false);
        WORTH_CONFIG            = new Config("worth.yml", null, true);
        SETTINGS_GUI_CONFIG     = new Config("settings_gui.yml", "gui", true);
        VIEW_GUI_CONFIG         = new Config("view_gui.yml", "gui", true);
    }

    private void listeners() {
        PluginManager pm = Bukkit.getPluginManager();
        // register events
        pm.registerEvents(new GeneratorBreakListener(this.generatorManager, this.userManager), this);
        pm.registerEvents(new GeneratorPlaceListener(this.generatorManager, this.userManager), this);
        pm.registerEvents(new GeneratorPreventionListener(this.generatorManager), this);
        pm.registerEvents(new GeneratorUpgradeListener(this.generatorManager, this.userManager), this);
        pm.registerEvents(new PlayerJoinListener(this.generatorManager), this);
        pm.registerEvents(new SellwandListener(this.sellwandManager), this);
        pm.registerEvents(new RefundListener(this.refundManager), this);
        pm.registerEvents(new GeneratorWorldDropMultiplier(), this);
    }

    private void commands() {
        // register commands
        MainCommand.register(this.generatorManager, this.userManager, this.eventManager, this.worthManager, this.sellwandManager);
        SellCommand.register(this.userManager);
        ShopCommand.register(this.generatorManager);
        PickupCommand.register(this.generatorManager);
        WorthCommand.registerThis();
        PlayerSettingsCommand.register(this.userManager);
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
        // single line chart
        metrics.addCustomChart(new SingleLineChart("total_generators", () -> this.generatorManager.getActiveGenerator().size()));
    }

    private SimplePie createSimplePie(String id, String path) {
        FileConfiguration config = NextGens.DEFAULT_CONFIG.getConfig();
        return new SimplePie(id, () -> this.yesOrNo(config.getBoolean(path)));
    }

    private void updateCheck(){
        // Disable Update Checker
    }

    private String yesOrNo(boolean status) {
        if (status) {
            return "yes";
        } else {
            return "no";
        }
    }

    public static GeneratorAPI getApi() {
        // initialize the api
        if (api == null) {
            NextGens plugin = NextGens.getInstance();
            api = new GeneratorAPI(plugin.getGeneratorManager(), plugin.getRefundManager(), plugin.getUserManager(),
                    plugin.getWorthManager(), plugin.getSellwandManager(), plugin.getEventManager());
        }
        return api;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public RefundManager getRefundManager() {
        return refundManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public WorthManager getWorthManager() {
        return worthManager;
    }

    public SellwandManager getSellwandManager() {
        return sellwandManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public SellManager getSellManager() {
        return sellManager;
    }

    public SellMultiplierRegistry getMultiplierRegistry() {
        return sellMultiplierRegistry;
    }

    public DatabaseManager getDatabaseManager() {
        return dbm;
    }

    public static NextGens getInstance() {
        return instance;
    }

}
