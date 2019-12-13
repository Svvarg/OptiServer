package ru.flametaichou.optiserver.util;

import net.minecraftforge.common.config.Configuration;

import java.util.Arrays;
import java.util.List;

public class ConfigHelper {

	public ConfigHelper() {
	}

	public static int memoryLimit;
	public static int checkInterval;
	public static int statsInterval;
	public static int tpsLimit;
	public static String beforeClearMessage;
	public static String clearMessage;
	public static List<String> itemBlacklist;
	public static boolean debugMode = false;
	
	public static void setupConfig(Configuration config) {
		try {
			config.load();
			memoryLimit = config.get("Settings", "MemoryLimit", 3000).getInt(3000);
			checkInterval = config.get("Settings", "CheckInterval", 2).getInt(2);
			statsInterval = config.get("Settings", "StatsInterval", 5).getInt(5);
			tpsLimit = config.get("Settings", "TpsLimit", 18).getInt(18);
			beforeClearMessage = config.getString("Settings", "BeforeClearMessage", "" +
					"Server overloaded! Memory clear after §b%s§f seconds!",
					"Message that will be shown to players before cleaning.");
			clearMessage = config.getString("Settings", "ClearMessage", "" +
							"Memory cleaned! §b%s§f objects and §b%s§f chunks removed. Releases §b%s§fMB RAM.",
					"Message that will be shown to players after cleaning.");

			itemBlacklist = Arrays.asList(config.getStringList("Settings", "ItemBlacklist", new String[]{"264"}, "IDs of items that should not be deleted."));
			
		} catch(Exception e) {
			Logger.error("A severe error has occured when attempting to load the config file for this mod!");
		} finally {
			if(config.hasChanged()) {
				config.save();
			}
		}
	}
}