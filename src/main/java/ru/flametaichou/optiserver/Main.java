package ru.flametaichou.optiserver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;


@Mod (modid = "optiserver", name = "Ordinary OptiServer", version = "0.1", acceptableRemoteVersions = "*")

public class Main {
	
	@EventHandler
	public void initialize(FMLInitializationEvent event)
	{
		FMLCommonHandler.instance().bus().register(new WorldEventHandler());
	}
	
	@EventHandler
	public void preLoad(FMLPreInitializationEvent event)
	{
		ConfigHelper.setupConfig(new Configuration(event.getSuggestedConfigurationFile()));
	}
}
