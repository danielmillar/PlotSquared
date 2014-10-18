/*
 * Copyright (c) IntellectualCrafters - 2014. You are not allowed to distribute
 * and/or monetize any of our intellectual property. IntellectualCrafters is not
 * affiliated with Mojang AB. Minecraft is a trademark of Mojang AB.
 * 
 * >> File = Main.java >> Generated by: Citymonstret at 2014-08-09 01:43
 */

package com.intellectualcrafters.plot;

import ca.mera.CameraAPI;
import com.intellectualcrafters.plot.Logger.LogLevel;
import com.intellectualcrafters.plot.Settings.Web;
import com.intellectualcrafters.plot.commands.Camera;
import com.intellectualcrafters.plot.commands.MainCommand;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.database.MySQL;
import com.intellectualcrafters.plot.database.PlotMeConverter;
import com.intellectualcrafters.plot.database.SQLite;
import com.intellectualcrafters.plot.events.PlayerTeleportToPlotEvent;
import com.intellectualcrafters.plot.events.PlotDeleteEvent;
import com.intellectualcrafters.plot.generator.DefaultPlotManager;
import com.intellectualcrafters.plot.generator.DefaultPlotWorld;
import com.intellectualcrafters.plot.generator.WorldGenerator;
import com.intellectualcrafters.plot.listeners.PlayerEvents;
import com.intellectualcrafters.plot.listeners.WorldEditListener;
import com.intellectualcrafters.plot.listeners.WorldGuardListener;
import com.intellectualcrafters.plot.uuid.PlotUUIDSaver;
import com.intellectualcrafters.plot.uuid.UUIDSaver;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import me.confuser.barapi.BarAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @awesome @author Citymonstret, Empire92 PlotMain class.
 */
public class PlotMain extends JavaPlugin {

    private static UUIDSaver uuidSaver;

	/**
	 * settings.properties
	 */
	public static File configFile;
	public static YamlConfiguration config;
	private static int config_ver = 1;
	/**
	 * storage.properties
	 */
	public static File storageFile;
	public static YamlConfiguration storage;
	public static int storage_ver = 1;
	/**
	 * translations.properties
	 */
	public static File translationsFile;
	public static YamlConfiguration translations;
	public static int translations_ver = 1;
	/**
	 * MySQL Object
	 */
	private static MySQL mySQL;
	/**
	 * MySQL Connection
	 */
	public static Connection connection;
	/**
	 * WorldEdit object
	 */
	public static WorldEditPlugin worldEdit = null;
	/**
	 * BarAPI object
	 */
	public static BarAPI barAPI = null;
	/**
	 * CameraAPI object
	 */
	public static CameraAPI cameraAPI;

	public static WorldGuardPlugin worldGuard = null;
	public static WorldGuardListener worldGuardListener = null;

	public static Economy economy;
	public static boolean useEconomy;

	/**
	 * !!WorldGeneration!!
	 */
	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldname, String id) {
		return new WorldGenerator(worldname);
	}

	@SuppressWarnings("deprecation")
	public static void checkForExpiredPlots() {
		final JavaPlugin plugin = PlotMain.getMain();
		Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, new Runnable() {
			@Override
			public void run() {
				checkExpired(plugin, true);
			}
		}, 0l, 12 * 60 * 60 * 20l);
	}

	/**
	 * Check a range of permissions e.g. 'plots.plot.<0-100>'<br>
	 * Returns highest integer in range.
	 * 
	 * @param player
	 * @param stub
	 * @param range
	 * @return
	 */
	public static int hasPermissionRange(Player player, String stub, int range) {
		if (player==null || player.isOp()) {
			return range;
		}
		if (player.hasPermission(stub + ".*")) {
			return range;
		}
		for (int i = range; i > 0; i--) {
			if (player.hasPermission(stub + "." + i)) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * Check a player for a permission<br>
	 * - Op has all permissions <br>
	 * - checks for '*' nodes
	 * 
	 * @param player
	 * @param perms
	 * @return
	 */
	public static boolean hasPermissions(Player player, String[] perms) {
		// Assumes null player is console.
		if (player==null || player.isOp()) {
			return true;
		}
		for (String perm : perms) {
			boolean hasperm = false;
			if (player.hasPermission(perm)) {
				hasperm = true;
			}
			else {
				String[] nodes = perm.split("\\.");
				StringBuilder n = new StringBuilder();
				for (int i = 0; i < (nodes.length - 1); i++) {
					n.append(nodes[i] + ".");
					if (player.hasPermission(n + "*")) {
						hasperm = true;
						break;
					}
				}
			}
			if (!hasperm) {
				return false;
			}
		}

		return true;
	}

    public static void setUUIDSaver(UUIDSaver saver) {
        uuidSaver = saver;
    }

    public static UUIDSaver getUUIDSaver() {
        return uuidSaver;
    }

	/**
	 * Check a player for a permission<br>
	 * - Op has all permissions <br>
	 * - checks for '*' nodes
	 * 
	 * @param player
	 * @param perm
	 * @return
	 */
	public static boolean hasPermission(Player player, String perm) {
		if (player==null || player.isOp()) {
			return true;
		}
		if (player.hasPermission(perm)) {
			return true;
		}
		String[] nodes = perm.split("\\.");
		StringBuilder n = new StringBuilder();
		for (int i = 0; i < (nodes.length - 1); i++) {
			n.append(nodes[i] + ".");
			if (player.hasPermission(n + "*")) {
				return true;
			}
		}

		return false;
	}

	/**
	 * All loaded plots
	 */
	private static HashMap<String, HashMap<PlotId, Plot>> plots;
	/**
	 * All loaded plot worlds
	 */
	private static HashMap<String, PlotWorld> worlds = new HashMap<String, PlotWorld>();
	private static HashMap<String, PlotManager> managers = new HashMap<String, PlotManager>();

	/**
	 * Get all plots
	 * 
	 * @return HashMap containing the plot ID and the plot object.
	 */
	public static Set<Plot> getPlots() {
		ArrayList<Plot> myplots = new ArrayList<Plot>();
		for (HashMap<PlotId, Plot> world : plots.values()) {
			myplots.addAll(world.values());
		}
		return new HashSet<Plot>(myplots);
	}

	/**
	 * @param player
	 * @return
	 */
	public static Set<Plot> getPlots(Player player) {
		UUID uuid = player.getUniqueId();
		ArrayList<Plot> myplots = new ArrayList<Plot>();
		for (HashMap<PlotId, Plot> world : plots.values()) {
			for (Plot plot : world.values()) {
				if (plot.hasOwner()) {
					if (plot.getOwner().equals(uuid)) {
						myplots.add(plot);
					}
				}
			}
		}
		return new HashSet<Plot>(myplots);
	}

	/**
	 * @param world
	 * @param player
	 * @return
	 */
	public static Set<Plot> getPlots(World world, Player player) {
		UUID uuid = player.getUniqueId();
		ArrayList<Plot> myplots = new ArrayList<Plot>();
		for (Plot plot : getPlots(world).values()) {
			if (plot.hasOwner()) {
				if (plot.getOwner().equals(uuid)) {
					myplots.add(plot);
				}
			}
		}
		return new HashSet<Plot>(myplots);
	}

	/**
	 * @param world
	 * @return
	 */
	public static HashMap<PlotId, Plot> getPlots(World world) {
		if (plots.containsKey(world.getName())) {
			return plots.get(world.getName());
		}
		return new HashMap<PlotId, Plot>();
	}

	/**
	 * get all plot worlds
	 */
	public static String[] getPlotWorlds() {
		return (worlds.keySet().toArray(new String[0]));
	}

	/**
	 * @return
	 */
	public static String[] getPlotWorldsString() {
		return plots.keySet().toArray(new String[0]);
	}

	/**
	 * @param world
	 * @return
	 */
	public static boolean isPlotWorld(World world) {
		return (worlds.containsKey(world.getName()));
	}

	/**
	 * @param world
	 * @return
	 */
	public static PlotManager getPlotManager(World world) {
		if (managers.containsKey(world.getName())) {
			return managers.get(world.getName());
		}
		return null;
	}

	/**
	 * @param world
	 * @return
	 */
	public static PlotManager getPlotManager(String world) {
		if (managers.containsKey(world)) {
			return managers.get(world);
		}
		return null;
	}

	/**
	 * @param world
	 * @return
	 */
	public static PlotWorld getWorldSettings(World world) {
		if (worlds.containsKey(world.getName())) {
			return worlds.get(world.getName());
		}
		return null;
	}

	/**
	 * @param world
	 * @return
	 */
	public static PlotWorld getWorldSettings(String world) {
		if (worlds.containsKey(world)) {
			return worlds.get(world);
		}
		return null;
	}

	/**
	 * @param world
	 * @return set containing the plots for a world
	 */
	public static Plot[] getWorldPlots(World world) {
		return (plots.get(world.getName()).values().toArray(new Plot[0]));
	}

	public static boolean removePlot(String world, PlotId id, boolean callEvent) {
		if (callEvent) {
			PlotDeleteEvent event = new PlotDeleteEvent(world, id);
			Bukkit.getServer().getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				event.setCancelled(true);
				return false;
			}
		}
		plots.get(world).remove(id);
		return true;
	}

	/**
	 * Replace the plot object with an updated version
	 * 
	 * @param plot
	 *            plot object
	 */
	public static void updatePlot(Plot plot) {
		String world = plot.world;
		if (!plots.containsKey(world)) {
			plots.put(world, new HashMap<PlotId, Plot>());
		}
		plot.hasChanged = true;
		plots.get(world).put(plot.id, plot);
	}

	/**
	 * TODO: <b>Implement better system The whole point of this system is to
	 * recycle old plots</b> <br>
	 * So why not just allow users to claim old plots, and try to hide the fact
	 * that the are owned. <br>
	 * <br>
	 * Reduce amount of expired plots: <br>
	 * - On /plot <br>
	 * auto<br>
	 * - allow claiming of old plot, clear it so the user doesn't know<br>
	 * - On /plot info,<br>
	 * - show that the plot is expired and allowed to be claimed Have the task
	 * run less often:<br>
	 * - Run the task when there are very little, or no players online (great
	 * for small servers)<br>
	 * - Run the task at startup (also only useful for small servers)<br>
	 * Also, in terms of faster code:<br>
	 * - Have an array of plots, sorted by expiry time.<br>
	 * - Add new plots to the end.<br>
	 * - The task then only needs to go through the first few plots
	 * 
	 * @param plugin
	 *            Plugin
	 * @param async
	 *            Call async?
	 */
	private static void checkExpired(JavaPlugin plugin, boolean async) {
		if (async) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
				@Override
				public void run() {
					for (String world : getPlotWorldsString()) {
						if (plots.containsKey(world)) {
							for (Plot plot : plots.get(world).values()) {
								if (plot.owner == null) {
									continue;
								}
								long lastPlayed = getLastPlayed(plot.owner);
								if (lastPlayed == 0) {
									continue;
								}
								int days = (int) (lastPlayed / (1000 * 60 * 60 * 24));
								if (days >= Settings.AUTO_CLEAR_DAYS) {
									PlotDeleteEvent event = new PlotDeleteEvent(world, plot.id);
									Bukkit.getServer().getPluginManager().callEvent(event);
									if (event.isCancelled()) {
										event.setCancelled(true);
									}
									else {
										DBFunc.delete(world, plot);
									}
								}
							}
						}
					}
				}
			});
		}
		else {
			for (String world : getPlotWorldsString()) {
				if (PlotMain.plots.containsKey(world)) {
					for (Plot plot : PlotMain.plots.get(world).values()) {
						if (PlayerFunctions.hasExpired(plot)) {
							PlotDeleteEvent event = new PlotDeleteEvent(world, plot.id);
							Bukkit.getServer().getPluginManager().callEvent(event);
							if (event.isCancelled()) {
								event.setCancelled(true);
							}
							else {
								DBFunc.delete(world, plot);
							}
						}
					}
				}
			}
		}
	}

	private void setupLogger() {
		File log = new File(getMain().getDataFolder() + File.separator + "logs" + File.separator + "plots.log");
		if (!log.exists()) {
			try {
				if (!new File(getMain().getDataFolder() + File.separator + "logs").mkdirs()) {
					sendConsoleSenderMessage(C.PREFIX.s() + "&cFailed to create logs folder. Do it manually.");
				}
				if (log.createNewFile()) {
					FileWriter writer = new FileWriter(log);
					writer.write("Created at: " + new Date().toString() + "\n\n\n");
					writer.close();
				}
			}
			catch (IOException e) {

				e.printStackTrace();
			}
		}
		Logger.setup(log);
		Logger.add(LogLevel.GENERAL, "Logger enabled");
	}

	private static double getJavaVersion() {
		return Double.parseDouble(System.getProperty("java.specification.version"));
	}

	/**
	 * On Load.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void onEnable() {
        // Init the logger
		setupLogger();

		// Check for outdated java version.
		if (getJavaVersion() < 1.7) {
			sendConsoleSenderMessage(C.PREFIX.s() + "&cYour java version is outdated. Please update to at least 1.7.");
            // Didn't know of any other link :D
			sendConsoleSenderMessage(C.PREFIX.s() + "&cURL: &6https://java.com/en/download/index.jsp");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

        // Setup configurations
		configs();

        // Setup metrics
		if (Settings.METRICS) {
			try {
				Metrics metrics = new Metrics(this);
				metrics.start();
				sendConsoleSenderMessage(C.PREFIX.s() + "&6Metrics enabled.");
			}
			catch (Exception e) {
				sendConsoleSenderMessage(C.PREFIX.s() + "&cFailed to load up metrics.");
			}
		}

        // Kill mobs on roads?
		if (Settings.KILL_ROAD_MOBS) {
			killAllEntities();
		}

        // Enabled<3
		if (C.ENABLED.s().length() > 0) {
			Broadcast(C.ENABLED);
		}

        // Use mysql?
		if (Settings.DB.USE_MYSQL) {
			try {
				mySQL =
						new MySQL(this, Settings.DB.HOST_NAME, Settings.DB.PORT, Settings.DB.DATABASE, Settings.DB.USER, Settings.DB.PASSWORD);
				connection = mySQL.openConnection();
				{
					DatabaseMetaData meta = connection.getMetaData();
					ResultSet res = meta.getTables(null, null, "plot", null);
					if (!res.next()) {
						DBFunc.createTables("mysql", true);
					}
					else {
						res = meta.getTables(null, null, "plot_trusted", null);
						if (!res.next()) {
							DBFunc.createTables("mysql", false);
						}
						else {
							res = meta.getTables(null, null, "plot_ratings", null);
							if (!res.next()) {
								DBFunc.createTables("mysql", false);
							}
						}
					}
				}
			}
			catch (ClassNotFoundException | SQLException e) {
				Logger.add(LogLevel.DANGER, "MySQL connection failed.");
				System.out.print("\u001B[31m[Plots] MySQL is not setup correctly. The plugin will disable itself.\u001B[0m");
				System.out.print("\u001B[36m==== Here is an ugly stacktrace if you are interested in those things ====\u001B[0m");
				e.printStackTrace();
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
			plots = DBFunc.getPlots();

		}
        // TODO: Implement mongo
		else
			if (Settings.DB.USE_MONGO) {
				sendConsoleSenderMessage(C.PREFIX.s() + "MongoDB is not yet implemented");
			}
            // Use Sqlite :D<3
			else
				if (Settings.DB.USE_SQLITE) {
					try {
						connection = new SQLite(this, Settings.DB.SQLITE_DB + ".db").openConnection();
						{
							DatabaseMetaData meta = connection.getMetaData();
							ResultSet res = meta.getTables(null, null, "plot", null);
							if (!res.next()) {
								DBFunc.createTables("sqlite", true);
							}
							else {
								res = meta.getTables(null, null, "plot_trusted", null);
								if (!res.next()) {
									DBFunc.createTables("sqlite", false);
								}
								else {
									res = meta.getTables(null, null, "plot_ratings", null);
									if (!res.next()) {
										DBFunc.createTables("sqlite", false);
									}
								}
							}
						}
					}
					catch (ClassNotFoundException | SQLException e) {
						Logger.add(LogLevel.DANGER, "SQLite connection failed");
						sendConsoleSenderMessage(C.PREFIX.s()
								+ "&cFailed to open SQLite connection. The plugin will disable itself.");
						sendConsoleSenderMessage("&9==== Here is an ugly stacktrace, if you are interested in those things ===");
						e.printStackTrace();
						Bukkit.getPluginManager().disablePlugin(this);
						return;
					}
					plots = DBFunc.getPlots();
				}
				else {
					Logger.add(LogLevel.DANGER, "No storage type is set.");
					sendConsoleSenderMessage(C.PREFIX + "&cNo storage type is set!");
					getServer().getPluginManager().disablePlugin(this);
					return;
				}
		if (getServer().getPluginManager().getPlugin("PlotMe") != null) {
			try {
				new PlotMeConverter(this).runAsync();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}

		getCommand("plots").setExecutor(new MainCommand());
		getCommand("plots").setAliases(new ArrayList<String>() {
			{
				add("p");
                add("ps");
				add("plotme");
				add("plot");
			}
		});

		getServer().getPluginManager().registerEvents(new PlayerEvents(), this);

		if (getServer().getPluginManager().getPlugin("CameraAPI") != null) {
			cameraAPI = CameraAPI.getInstance();
			Camera camera = new Camera();
			MainCommand.subCommands.add(camera);
			getServer().getPluginManager().registerEvents(camera, this);
		}
		if (getServer().getPluginManager().getPlugin("BarAPI") != null) {
			barAPI = (BarAPI) getServer().getPluginManager().getPlugin("BarAPI");
		}
		if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
			worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
			getServer().getPluginManager().registerEvents(new WorldEditListener(), this);
		}
		if (Settings.WORLDGUARD) {
			if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
				worldGuard = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
				worldGuardListener = new WorldGuardListener(this);
				getServer().getPluginManager().registerEvents(worldGuardListener, this);
			}
		}
		if (Settings.AUTO_CLEAR) {
			checkExpired(PlotMain.getMain(), true);
			checkForExpiredPlots();
		}
		if (getServer().getPluginManager().getPlugin("Vault") != null) {
			RegisteredServiceProvider<Economy> economyProvider =
					getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
			if (economyProvider != null) {
				economy = economyProvider.getProvider();
			}
			useEconomy = (economy != null);
		}
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Lag(), 100L, 1L);

		if (Web.ENABLED) {
			sendConsoleSenderMessage(C.PREFIX.s() + "Web Is not implemented yet. Please bear with us.");
		}

		try {
			new SetBlockFast();
			PlotHelper.canSetFast = true;
		}
		catch (Exception e) {
			PlotHelper.canSetFast = false;
		}

        //Setup version + downloads, will not be updated... maybe setup runnable? TODO Let jesse decide...
        com.intellectualcrafters.plot.commands.plugin.setup(this);

        setUUIDSaver(new PlotUUIDSaver());
		getUUIDSaver().globalPopulate();
		//UUIDHandler.startFetch(this);
	}

	/**
	 * Get MySQL Connection
	 * 
	 * @return connection MySQL Connection.
	 */
	@SuppressWarnings("unused")
	public static Connection getConnection() {
		return connection;
	}

	/** .. */

	// Old Stuff
	/*
	 * private static boolean checkForUpdate() throws IOException { URL call =
	 * new URL(Settings.Update.VERSION_URL); InputStream stream =
	 * call.openStream(); BufferedReader reader = new BufferedReader(new
	 * InputStreamReader(stream)); String latest = reader.readLine();
	 * reader.close(); return
	 * !getPlotMain().getDescription().getVersion().equalsIgnoreCase(latest); }
	 * private static String getNextUpdateString() throws IOException { URL call
	 * = new URL(Settings.Update.VERSION_URL); InputStream stream =
	 * call.openStream(); BufferedReader reader = new BufferedReader(new
	 * InputStreamReader(stream)); return reader.readLine(); } private static
	 * void update() throws IOException { sendConsoleSenderMessage(C.PREFIX.s()
	 * + "&c&lThere is an update! New Update: &6&l" + getNextUpdateString() +
	 * "&c&l, Current Update: &6&l" +
	 * getPlotMain().getDescription().getVersion()); }
	 */

	/**
	 * Send a message to the console.
	 * 
	 * @param string
	 *            message
	 */
	public static void sendConsoleSenderMessage(String string) {
		if (getMain().getServer().getConsoleSender() == null) {
			System.out.println(ChatColor.stripColor(ConsoleColors.fromString(string)));
		}
		else {
			getMain().getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', string));
		}
	}

	public static boolean teleportPlayer(Player player, Location from, Plot plot) {
		PlayerTeleportToPlotEvent event = new PlayerTeleportToPlotEvent(player, from, plot);
		Bukkit.getServer().getPluginManager().callEvent(event);
		if (!event.isCancelled()) {
			Location location = PlotHelper.getPlotHome(Bukkit.getWorld(plot.world), plot);
			if ((location.getBlockX() >= 29999999) || (location.getBlockX() <= -29999999)
					|| (location.getBlockZ() >= 299999999) || (location.getBlockZ() <= -29999999)) {
				event.setCancelled(true);
				return false;
			}
			player.teleport(location);
			PlayerFunctions.sendMessage(player, C.TELEPORTED_TO_PLOT);
		}
		return event.isCancelled();
	}

	/**
	 * Send a message to the console
	 * 
	 * @param c
	 *            message
	 */
	@SuppressWarnings("unused")
	public static void sendConsoleSenderMessage(C c) {
		sendConsoleSenderMessage(c.s());
	}

	/**
	 * Broadcast publicly
	 * 
	 * @param c
	 *            message
	 */
	public static void Broadcast(C c) {
		Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', C.PREFIX.s() + c.s()));
	}

	/**
	 * Returns the main class.
	 * 
	 * @return (this class)
	 */
	public static PlotMain getMain() {
		return JavaPlugin.getPlugin(PlotMain.class);
	}

	/**
	 * Broadcast a message to all admins
	 * 
	 * @param c
	 *            message
	 */
	public static void BroadcastWithPerms(C c) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.hasPermission("plots.admin")) {
				PlayerFunctions.sendMessage(player, c);
			}
		}
		System.out.println(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', C.PREFIX.s() + c.s())));
	}

	public static void reloadTranslations() throws IOException {
		translations = YamlConfiguration.loadConfiguration(translationsFile);
	}

	public static long getLastPlayed(UUID uuid) {
		if (uuid == null) {
			return 0;
		}
		OfflinePlayer player;
		if (((player = Bukkit.getOfflinePlayer(uuid)) == null) || !player.hasPlayedBefore()) {
			return 0;
		}
		return player.getLastPlayed();
	}

	/**
	 * Load configuration files
	 */
	@SuppressWarnings("deprecation")
	public static void configs() {
		File folder = new File(getMain().getDataFolder() + File.separator + "config");
		if (!folder.exists() && !folder.mkdirs()) {
			sendConsoleSenderMessage(C.PREFIX.s()
					+ "&cFailed to create the /plugins/config folder. Please create it manually.");
		}
		try {
			configFile =
					new File(getMain().getDataFolder() + File.separator + "config" + File.separator + "settings.yml");
			if (!configFile.exists()) {
				configFile.createNewFile();
			}
			config = YamlConfiguration.loadConfiguration(configFile);
			setupConfig();
		}
		catch (Exception err_trans) {
			Logger.add(LogLevel.DANGER, "Failed to save settings.yml");
			System.out.println("Failed to save settings.yml");
		}
		try {
			storageFile =
					new File(getMain().getDataFolder() + File.separator + "config" + File.separator + "storage.yml");
			if (!storageFile.exists()) {
				storageFile.createNewFile();
			}
			storage = YamlConfiguration.loadConfiguration(storageFile);
			setupStorage();
		}
		catch (Exception err_trans) {
			Logger.add(LogLevel.DANGER, "Failed to save storage.yml");
			System.out.println("Failed to save storage.yml");
		}
		try {
			translationsFile =
					new File(getMain().getDataFolder() + File.separator + "config" + File.separator
							+ "translations.yml");
			if (!translationsFile.exists()) {
				translationsFile.createNewFile();
			}
			translations = YamlConfiguration.loadConfiguration(translationsFile);
			setupTranslations();
		}
		catch (Exception err_trans) {
			Logger.add(LogLevel.DANGER, "Failed to save translations.yml");
			System.out.println("Failed to save translations.yml");
		}

		try {
			config.save(configFile);
			storage.save(storageFile);
			translations.save(translationsFile);
		}
		catch (IOException e) {
			Logger.add(LogLevel.DANGER, "Configuration file saving failed");
			e.printStackTrace();
		}
		{
			Settings.DB.USE_MYSQL = storage.getBoolean("mysql.use");
			Settings.DB.USER = storage.getString("mysql.user");
			Settings.DB.PASSWORD = storage.getString("mysql.password");
			Settings.DB.HOST_NAME = storage.getString("mysql.host");
			Settings.DB.PORT = storage.getString("mysql.port");
			Settings.DB.DATABASE = storage.getString("mysql.database");
			Settings.DB.USE_SQLITE = storage.getBoolean("sqlite.use");
			Settings.DB.SQLITE_DB = storage.getString("sqlite.db");
		}
		{
			Settings.METRICS = config.getBoolean("metrics");
			// Web
			// Web.ENABLED = config.getBoolean("web.enabled");
			// Web.PORT = config.getInt("web.port");

			Settings.AUTO_CLEAR = config.getBoolean("clear.auto.enabled");
			Settings.AUTO_CLEAR_DAYS = config.getInt("clear.auto.days");
		}
		if (Settings.DEBUG) {
			Map<String, String> settings = new HashMap<>();
			settings.put("Kill Road Mobs", "" + Settings.KILL_ROAD_MOBS);
			settings.put("Use Metrics", "" + Settings.METRICS);
			settings.put("Mob Pathfinding", "" + Settings.MOB_PATHFINDING);
			settings.put("Web Enabled", "" + Web.ENABLED);
			settings.put("Web Port", "" + Web.PORT);
			settings.put("DB Mysql Enabled", "" + Settings.DB.USE_MYSQL);
			settings.put("DB SQLite Enabled", "" + Settings.DB.USE_SQLITE);
			settings.put("Auto Clear Enabled", "" + Settings.AUTO_CLEAR);
			settings.put("Auto Clear Days", "" + Settings.AUTO_CLEAR_DAYS);
			for (Entry<String, String> setting : settings.entrySet()) {
				sendConsoleSenderMessage(C.PREFIX.s()
						+ String.format("&cKey: &6%s&c, Value: &6%s", setting.getKey(), setting.getValue()));
			}
		}
	}

	/**
	 * Kill all entities on roads
	 */
	@SuppressWarnings("deprecation")
	public static void killAllEntities() {
		Bukkit.getScheduler().scheduleAsyncRepeatingTask(getMain(), new Runnable() {
			Location location;
			long ticked = 0l;
			long error = 0l;
			{
				sendConsoleSenderMessage(C.PREFIX.s() + "KillAllEntities started.");
			}

			@Override
			public void run() {
				if (this.ticked > 36000l) {
					this.ticked = 0l;
					sendConsoleSenderMessage(C.PREFIX.s() + "KillAllEntities has been running for 60 minutes. Errors: "
							+ this.error);
					this.error = 0l;
				}
				for (String w : getPlotWorlds()) {
					getWorldSettings(w);
					World world = Bukkit.getServer().getWorld(w);
					try {
						if (world.getLoadedChunks().length < 1) {
							continue;
						}
						for (Chunk chunk : world.getLoadedChunks()) {
							Entity[] entities = chunk.getEntities();
							for (int i = entities.length - 1; i >= 0; i--) {
								Entity entity = entities[i];

								if ((entity instanceof Player) || PlayerEvents.isInPlot(entity.getLocation())) {
									continue;
								}

								boolean tamed = false;
								if (Settings.MOB_PATHFINDING) {
									if (entity instanceof Tameable) {
										Tameable tameable = (Tameable) entity;
										if (tameable.isTamed()) {
											tamed = true;
										}
									}
									else
										if (entity instanceof LivingEntity) {
											LivingEntity livingEntity = ((LivingEntity) entity);
											if (livingEntity.getCustomName() != null) {
												tamed = true;
											}
										}
									if (!tamed) {
										entity.remove();
										continue;
									}
									boolean found = false;
									int radius = 1;
									int dir = 0;
									int x = this.location.getBlockX();
									int y = this.location.getBlockY();
									int z = this.location.getBlockZ();
									while (!found && (radius < 4)) {
										Location pos;
										switch (dir) {
										case 0:
											pos = new Location(world, x + radius, y, z);
											dir++;
											break;
										case 1:
											pos = new Location(world, x, y, z + radius);
											dir++;
											break;
										case 2:
											pos = new Location(world, x - radius, y, z);
											dir++;
											break;
										case 3:
											pos = new Location(world, x, y, z - radius);
											dir = 0;
											radius++;
											break;
										default:
											pos = this.location;
											break;

										}
										if (PlayerEvents.isInPlot(pos)) {
											entity.teleport(pos.add(0.5, 0, 0.5));
											found = true;
											break;
										}
									}
									entity.teleport(this.location.subtract(this.location.getDirection().normalize().multiply(2)));
								}
							}
						}
					}
					catch (Exception e) {
						++this.error;
					}
					finally {
						++this.ticked;
					}
				}
			}
		}, 0l, 2l);
	}

	/**
	 * SETUP: settings.yml
	 */
	private static void setupConfig() {
		config.set("version", config_ver);
		Map<String, Object> options = new HashMap<String, Object>();
		options.put("auto_update", false);
		options.put("worldguard.enabled", Settings.WORLDGUARD);
		options.put("kill_road_mobs", Settings.KILL_ROAD_MOBS_DEFAULT);
		options.put("mob_pathfinding", Settings.MOB_PATHFINDING_DEFAULT);
		options.put("web.enabled", Web.ENABLED);
		options.put("web.port", Web.PORT);
		options.put("metrics", true);
		options.put("debug", true);
		options.put("clear.auto.enabled", false);
		options.put("clear.auto.days", 365);
		options.put("max_plots", Settings.MAX_PLOTS);

		for (Entry<String, Object> node : options.entrySet()) {
			if (!config.contains(node.getKey())) {
				config.set(node.getKey(), node.getValue());
			}
		}
		Settings.DEBUG = config.getBoolean("debug");
		if (Settings.DEBUG) {
			sendConsoleSenderMessage(C.PREFIX.s() + "&6Debug Mode Enabled (Default). Edit the config to turn this off.");
		}
		Web.ENABLED = config.getBoolean("web.enabled");
		Web.PORT = config.getInt("web.port");
		Settings.KILL_ROAD_MOBS = config.getBoolean("kill_road_mobs");
		Settings.WORLDGUARD = config.getBoolean("worldguard.enabled");
		Settings.MOB_PATHFINDING = config.getBoolean("mob_pathfinding");
		Settings.METRICS = config.getBoolean("metrics");
		Settings.AUTO_CLEAR_DAYS = config.getInt("clear.auto.days");
		Settings.AUTO_CLEAR = config.getBoolean("clear.auto.enabled");
		Settings.MAX_PLOTS = config.getInt("max_plots");
	}

	public static void createConfiguration(PlotWorld plotworld) {
		Map<String, Object> options = new HashMap<String, Object>();

		for (ConfigurationNode setting : plotworld.getSettingNodes()) {
			setting.getConstant();
			setting.getValue();
		}

		for (Entry<String, Object> node : options.entrySet()) {
			if (!config.contains(node.getKey())) {
				config.set(node.getKey(), node.getValue());
			}
		}

		try {
			config.save(PlotMain.configFile);
		}
		catch (IOException e) {
			PlotMain.sendConsoleSenderMessage("&c[Warning] PlotSquared failed to save the configuration&7 (settings.yml may differ from the one in memory)\n - To force a save from console use /plots save");
		}
	}

	public static void loadWorld(String world, ChunkGenerator generator) {
		if (getWorldSettings(world) != null) {
			return;
		}
		Set<String> worlds;
		if (config.contains("worlds")) {
			worlds = config.getConfigurationSection("worlds").getKeys(false);
		}
		else {
			worlds = new HashSet<String>();
		}
		if (generator != null && generator instanceof PlotGenerator) {
			sendConsoleSenderMessage(C.PREFIX.s() + "&aDetected world load for '" + world + "'.");
			PlotGenerator plotgen = (PlotGenerator) generator;
			PlotWorld plotworld = plotgen.getNewPlotWorld(world);
			PlotManager manager = plotgen.getPlotManager();

			if (!config.contains("worlds." + world)) {
				config.createSection("worlds." + world);
			}
			plotworld.saveConfiguration(config.getConfigurationSection("worlds." + world));

			plotworld.loadDefaultConfiguration(config.getConfigurationSection("worlds." + world));

			try {
				config.save(configFile);
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			addPlotWorld(world, plotworld, manager);

		}
		else {
			if (worlds.contains(world)) {
				sendConsoleSenderMessage("&cWorld '" + world + "' in settings.yml is not using PlotSquared generator!");

				PlotWorld plotworld = new DefaultPlotWorld(world);
				PlotManager manager = new DefaultPlotManager();

				if (!config.contains("worlds." + world)) {
					config.createSection("worlds." + world);
				}
				plotworld.saveConfiguration(config.getConfigurationSection("worlds." + world));

				plotworld.loadConfiguration(config.getConfigurationSection("worlds." + world));

				try {
					config.save(configFile);
				}
				catch (IOException e) {
					e.printStackTrace();
				}

				addPlotWorld(world, plotworld, manager);
			}
		}
	}

	/**
	 * Adds an external world as a recognized PlotSquared world - The PlotWorld
	 * class created is based off the configuration in the settings.yml - Do not
	 * use this method unless the required world is preconfigured in the
	 * settings.yml
	 * 
	 * @param world
	 */
	public static void loadWorld(World world) {
		if (world == null) {
			return;
		}
		ChunkGenerator generator = world.getGenerator();
		loadWorld(world.getName(), generator);
	}

	/**
	 * SETUP: storage.properties
	 */
	private static void setupStorage() {
		storage.set("version", storage_ver);
		Map<String, Object> options = new HashMap<String, Object>();
		options.put("mysql.use", true);
		options.put("sqlite.use", false);
		options.put("sqlite.db", "storage");
		options.put("mysql.host", "localhost");
		options.put("mysql.port", "3306");
		options.put("mysql.user", "root");
		options.put("mysql.password", "password");
		options.put("mysql.database", "plot_db");
		for (Entry<String, Object> node : options.entrySet()) {
			if (!storage.contains(node.getKey())) {
				storage.set(node.getKey(), node.getValue());
			}
		}
	}

	/**
	 * SETUP: translations.properties
	 */
	public static void setupTranslations() {
		translations.set("version", translations_ver);
		for (C c : C.values()) {
			if (!translations.contains(c.toString())) {
				translations.set(c.toString(), c.s());
			}

		}
	}

	/**
	 * On unload
	 */
	@Override
	public void onDisable() {
		Logger.add(LogLevel.GENERAL, "Logger disabled");
		try {
			Logger.write();
		}
		catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			connection.close();
			mySQL.closeConnection();
		}
		catch (NullPointerException | SQLException e) {
			if (connection != null) {
				Logger.add(LogLevel.DANGER, "Could not close mysql connection");
			}
		}
		/*
		 * if(PlotWeb.PLOTWEB != null) { try { PlotWeb.PLOTWEB.stop(); } catch
		 * (Exception e) { e.printStackTrace(); } }
		 */
	}

	public static void addPlotWorld(String world, PlotWorld plotworld, PlotManager manager) {
		worlds.put(world, plotworld);
		managers.put(world, manager);
		if (!plots.containsKey(world)) {
			plots.put(world, new HashMap<PlotId, Plot>());
		}
	}

}
