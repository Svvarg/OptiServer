package ru.flametaichou.optiserver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.config.Configuration;


@Mod (modid = "optiserver", name = "Ordinary OptiServer", version = "0.1", acceptableRemoteVersions = "*")

public class Main {

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
