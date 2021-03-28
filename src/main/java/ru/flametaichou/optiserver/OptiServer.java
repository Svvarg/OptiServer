package ru.flametaichou.optiserver;

import javax.annotation.Nonnull;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
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
import org.swarg.mcforge.util.MLog;

import static ru.flametaichou.optiserver.OptiServer.*;


@Mod (modid = MODID, name = "Ordinary OptiServer", version = VERSION, acceptableRemoteVersions = "*")
public class OptiServer {
        public static final String MODID   = "optiserver";
        public static final String VERSION = "0.3.2";
        public static final MLog LOG  = new MLog("OptiServer");  //TODO MODID!!!
        
        @Nonnull
        private static OptiServer INSTANCE;
        /*TEST*/public OptiServerCommands cmds;
        @Nonnull
        @Mod.InstanceFactory
        public static OptiServer instance() {
            if (INSTANCE == null) {
                INSTANCE = new OptiServer();
            }
            return INSTANCE;
        }

        
	@EventHandler
	public void initialize(FMLServerStartingEvent event)
	{
		MinecraftForge.EVENT_BUS.register(EntitySpawnHandler.instance());
		FMLCommonHandler.instance().bus().register(WorldEventHandler.instance());
                event.registerServerCommand(cmds = new OptiServerCommands());
	}
	
	@EventHandler
	public void preLoad(FMLPreInitializationEvent event)
	{
		ConfigHelper.setupConfig(new Configuration(event.getSuggestedConfigurationFile()));
	}
        
	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		ConfigHelper.bindEntitesClassesAndItemsInstances();
	}
}
