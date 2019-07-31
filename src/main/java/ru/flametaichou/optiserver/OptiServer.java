package ru.flametaichou.optiserver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.config.Configuration;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;


@Mod (modid = "optiserver", name = "Ordinary OptiServer", version = "0.1", acceptableRemoteVersions = "*")

public class OptiServer {

	public static Map<Date, Double> tpsStatsMap =  new TreeMap<Date, Double>();
	public static Map<Date, Double> memoryStatsMap =  new TreeMap<Date, Double>();

	public static WorldEventHandler worldEventHandler = new WorldEventHandler();
	
	@EventHandler
	public void initialize(FMLServerStartingEvent event)
	{
		FMLCommonHandler.instance().bus().register(worldEventHandler);
        event.registerServerCommand(new OptiServerCommands());
	}
	
	@EventHandler
	public void preLoad(FMLPreInitializationEvent event)
	{
		ConfigHelper.setupConfig(new Configuration(event.getSuggestedConfigurationFile()));
	}

}
