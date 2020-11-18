package ru.flametaichou.optiserver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import ru.flametaichou.optiserver.handlers.EntitySpawnHandler;
import ru.flametaichou.optiserver.handlers.OptiServerCommands;
import ru.flametaichou.optiserver.handlers.WorldEventHandler;
import ru.flametaichou.optiserver.util.ConfigHelper;

import java.util.Map;
import java.util.Date;
import java.util.TreeMap;
import javax.annotation.Nonnull;

import static ru.flametaichou.optiserver.OptiServer.MODID;


@Mod (modid = MODID, name = "Ordinary OptiServer", version = "0.2.1", acceptableRemoteVersions = "*")
public class OptiServer {
        public static final String MODID = "optiserver";
        
        @Nonnull
        private static OptiServer INSTANCE;
        @Nonnull
        @Mod.InstanceFactory
        public static OptiServer getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new OptiServer();
            }
            return INSTANCE;
        }

	public Map<Date, Double> tpsStatsMap =  new TreeMap<Date, Double>();
	public Map<Date, Double> memoryStatsMap =  new TreeMap<Date, Double>();

        
	@EventHandler
	public void initialize(FMLServerStartingEvent event)
	{
		MinecraftForge.EVENT_BUS.register(new EntitySpawnHandler());
		FMLCommonHandler.instance().bus().register(WorldEventHandler.getInstance());
                event.registerServerCommand(new OptiServerCommands());
	}
	
	@EventHandler
	public void preLoad(FMLPreInitializationEvent event)
	{
		ConfigHelper.setupConfig(new Configuration(event.getSuggestedConfigurationFile()));
	}
        
        @EventHandler
        public void postInit(FMLPostInitializationEvent event)
        {
            ConfigHelper.createPersistsEtitiesAndItems();
        }
}
