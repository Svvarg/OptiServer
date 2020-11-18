package ru.flametaichou.optiserver.handlers;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;

import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.OptiServer;
import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;
import ru.flametaichou.optiserver.util.WorldChunkUnloader;

import java.util.*;

 /* TODO
    страховочный пояс если сервер уходит в штопор и начинает постоянное
    запускать очистки предпренять меры ( если упал тпс не запускать очистку если
    она не так давно была)
    статистика данных о полях которые могут забивать память - листы существ мапы и прочее  Swarg */
public class WorldEventHandler {

    private long nextMemoryCheckTime = 0;
    private long cleanTime = 0;
    private long cleanAfterMessageTime = 0;

    private long lastUsedMem = 0;
    private int unloadedEntities = 0;
    private int removedDuplicatedEntities = 0;
    private int loadedChunks = 0;

    private long nextGetStatsTime = 0;
    private int maxMapSize = 144;
    private String cleanCause;

    private static WorldEventHandler INSTANSE;
    public static WorldEventHandler getInstance () {
        if (INSTANSE == null) {
            INSTANSE = new WorldEventHandler();
        }
        return INSTANSE;
    }

    /*
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {

    }
    */

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == Side.SERVER) {

            long nowSysMillis = MinecraftServer.getSystemTimeMillis();

            addStatRowAboutUsedMemAndTps(nowSysMillis);

            checkIsNeedCleaning(nowSysMillis);

            cleaningEntityAndChunks(nowSysMillis);

            notificationMessageAfterCleaning(nowSysMillis);
        }
    }

    /**
     * Clean check
     * @param nowSysMillis MinecraftServer.getSystemTimeMillis() //System.currentTimeMillis()
     */
    private void checkIsNeedCleaning(long nowSysMillis) {

        if (cleanTime == 0 && nowSysMillis >= nextMemoryCheckTime) {
            nextMemoryCheckTime = nowSysMillis + ConfigHelper.checkInterval * 60 * 1000;

            long freeMem = OptiServerUtils.getFreeMemMB();

            //double averageTps = 0;
            double minTps = OptiServerUtils.getMinTps();

            //averageTps = averageTps / MinecraftServer.getServer().worldServers.length;

            // Запускать чистку если найдено более 100 дублирующихся сущностей
            //long duplicatesCount = findDuplicates().size();
            long duplicatesCount = 0;

            if (freeMem < ConfigHelper.memoryLimit || minTps < ConfigHelper.tpsLimit ||
                (duplicatesCount = OptiServerUtils.findDuplicates0(null)) > 100) {

                cleanCause = "";
                if (freeMem < ConfigHelper.memoryLimit) {
                    Logger.warn("Memory limit! Free mem = " + freeMem);
                    cleanCause = " Mem: "+freeMem + "/" + ConfigHelper.memoryLimit;
                }
                if (minTps < ConfigHelper.tpsLimit) {
                    
                    Logger.warn("Low TPS! TPS = " + minTps);
                    cleanCause += " LowTps: "+ minTps + "/"+ ConfigHelper.tpsLimit;
                }
                if (duplicatesCount > 100) {                    
                    Logger.warn("Found " + duplicatesCount + " duplicated entities!");
                    cleanCause += " Duplicates: " + duplicatesCount + "/100";
                }

                sheduleClean();
            }
        }
    }

    
    public void sheduleClean() {
        if (cleanTime == 0) {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                    new ChatComponentTranslation(ConfigHelper.beforeClearMessage, String.valueOf(ConfigHelper.secBeforeClean))
            );

            cleanTime = MinecraftServer.getSystemTimeMillis() + (ConfigHelper.secBeforeClean * 1000);
        }
        else {
            Logger.warn("Clean is already scheduled!");
        }
    }

    public String getCleanCause() {
        return this.cleanCause;
    }
    public void setCleanCause(String cause) {
        this.cleanCause = cause;
    }

    /**
     *
     * @param nowSysMillis MinecraftServer.getSystemTimeMillis()
     */
    private void cleaningEntityAndChunks(long nowSysMillis) {

        if (cleanTime != 0 && nowSysMillis >= cleanTime) {
            cleanTime = 0;

            lastUsedMem = OptiServerUtils.getUsedMemMB();

            unloadedEntities = 0;
            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                List<Entity> loadedEntities = ws.loadedEntityList;
                Iterator iterator = loadedEntities.iterator();

                List<Entity> entitiesForUnload = new ArrayList<Entity>();

                while (iterator.hasNext()) {
                    Entity e = (Entity) iterator.next();
                    if (OptiServerUtils.isEntityCanBeUnloaded(e)) {
                        entitiesForUnload.add(e);
                        e.setDead();
                    }
                }

                unloadedEntities += entitiesForUnload.size();
                ws.unloadEntities(entitiesForUnload);
            }
            Logger.log(String.format("Unloaded %s entities!", unloadedEntities));

            //Remove breeding entities
            /*
            removedDuplicatedEntities = 0;
            Map<String, Integer> breedingEntitiesMap = new HashMap<String, Integer>();

            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            List<Entity> loadedEntities = ws.loadedEntityList;
            Iterator iterator = loadedEntities.iterator();

            //List<Entity> entitiesForUnload = new ArrayList<Entity>();

            while (iterator.hasNext()) {
            Entity e = (Entity) iterator.next();
            String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
            if (breedingEntitiesMap.get(key) != null) {
            //entitiesForUnload.add(e);
            //iterator.remove();
            //e.setDead();
            Logger.log(String.format("Unloading duplicated entity: %s (%s)", e.getCommandSenderName(), Logger.getCoordinatesString(e)));
            OptiServerUtils.unloadEntity(e);
            removedDuplicatedEntities++;
            } else {
            breedingEntitiesMap.put(key, 1);
            }
            }

            //ws.unloadEntities(entitiesForUnload);

            List<Entity> loadedTileEntities = ws.loadedTileEntityList;
            Iterator iteratorTE = loadedTileEntities.iterator();
            while (iteratorTE.hasNext()) {
            TileEntity te = (TileEntity) iteratorTE.next();
            String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
            if (breedingEntitiesMap.get(key) != null) {
            Logger.log(String.format("Unloading duplicated entity: %s (%s)", te.getClass().getSimpleName(), Logger.getCoordinatesString(te)));
            iteratorTE.remove();
            removedDuplicatedEntities++;
            } else {
            breedingEntitiesMap.put(key, 1);
            }
            }
            }
            Logger.log(String.format("Unloaded %s breeding entities!", removedDuplicatedEntities));
            */

            // Unload Chunks
            loadedChunks = 0;
            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                //loadedChunks += ws.theChunkProviderServer.loadedChunks.size();
                loadedChunks += ws.theChunkProviderServer.getLoadedChunkCount();

                /*
                Iterator iterator = ws.theChunkProviderServer.loadedChunks.iterator();

                while (iterator.hasNext()) {
                boolean needUnload = true;
                Chunk chunk = (Chunk) iterator.next();
                for (List l : chunk.entityLists) {
                for (Object obj : l) {
                if (obj instanceof EntityPlayerMP) {
                needUnload = false;
                break;
                }
                }
                if (!needUnload) {
                break;
                }
                }

                if (needUnload) {
                // ?
                //chunk.onChunkUnload();
                MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
                ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
                }
                }
                */

                WorldChunkUnloader worldChunkUnloader = new WorldChunkUnloader(ws);
                worldChunkUnloader.unloadChunks();

                ws.theChunkProviderServer.unloadQueuedChunks();

                // Unload world
                if (!DimensionManager.shouldLoadSpawn(ws.provider.dimensionId)
                        && ForgeChunkManager.getPersistentChunksFor(ws).isEmpty()
                        && ws.theChunkProviderServer.getLoadedChunkCount() == 0
                        && ws.playerEntities.isEmpty()
                        && ws.loadedEntityList.isEmpty()
                        && ws.loadedTileEntityList.isEmpty()) {
                    try {
                        ws.saveAllChunks(true, null);
                    } catch (MinecraftException e) {
                        Logger.error("Error on saving all chunks: " + ExceptionUtils.getRootCauseMessage(e));
                    } finally {
                        Logger.log("Unloading world DIM" + ws.provider.dimensionId);
                        MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(ws));
                        ws.flush();
                        DimensionManager.setWorld(ws.provider.dimensionId, null);
                    }
                }
            }

            // GC
            //Runtime.getRuntime().gc();
            System.gc();

            cleanAfterMessageTime = MinecraftServer.getSystemTimeMillis() + 1000;
        }
    }

    

    /**
     * Сбор статистики: Используемая память и TPS
     */
    private void addStatRowAboutUsedMemAndTps(long nowSysMillis) {
        //if (MinecraftServer.getSystemTimeMillis() - lastGetStatsTime >= (ConfigHelper.statsInterval * 60 * 1000)) {
        //if (nowSysMillis - lastGetStatsTime >= ConfigHelper.statsInterval) {
        if (nowSysMillis >= nextGetStatsTime) {
            nextGetStatsTime = nowSysMillis + ConfigHelper.statsInterval * 60 * 1000;//MinecraftServer.getSystemTimeMillis();

            Date now = new Date();

            double tps = OptiServerUtils.getMinTps();
            double memory = OptiServerUtils.getFreeMemMB();
            //StatEntry se = new StatEntry(now, (float) tps, memory);
            //OptiServer.statsList.add(se);
            OptiServer os = OptiServer.getInstance();
            os.tpsStatsMap.put(now, tps);
            os.memoryStatsMap.put(now, memory);

            // Clean
            List<Map<Date, Double>> maps = Arrays.asList(os.memoryStatsMap, os.tpsStatsMap);
            for (Map<Date, Double> map : maps) {
                if (map.size() > maxMapSize) {
                    Date firstDate = now;
                    for (Date e : map.keySet()) {
                        if (e.before(firstDate)) {
                            firstDate = e;
                        }
                    }
                    map.remove(firstDate);
                }
            }
        }
    }

    /**
     *  Message after clean
     * @param nowSysMillis
     */
    private void notificationMessageAfterCleaning(long nowSysMillis) {

        if (cleanAfterMessageTime != 0 && nowSysMillis >= cleanAfterMessageTime) {

            cleanAfterMessageTime = 0;

            int afterChunksCount = 0;
            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                //afterChunksCount += ws.theChunkProviderServer.loadedChunks.size();
                afterChunksCount += ws.theChunkProviderServer.getLoadedChunkCount();
            }

            Logger.log(String.format("Unloaded %s chunks!", loadedChunks - afterChunksCount));

            long usedMemAfterClean = OptiServerUtils.getUsedMemMB();
            Logger.log(String.format("Memory clean profit = %s MB!", lastUsedMem - usedMemAfterClean));

            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                    new ChatComponentTranslation(
                            ConfigHelper.clearMessage,
                            String.valueOf(unloadedEntities + removedDuplicatedEntities),
                            String.valueOf(loadedChunks - afterChunksCount),
                            String.valueOf(lastUsedMem - usedMemAfterClean)
                    )
            );
        }
    }



//    // TODO: в os largest не попадают дублирующиеся сущности. Почему?
//    @Deprecated
//    public static List<String> findDuplicates() {
//        List<String> resultList = new ArrayList<String>();
//        Map<String, Integer> entitiesMap = new HashMap<String, Integer>();
//
//        // по ws.loadedEntityList
//        /*
//        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
//            List<Entity> loadedEntities = new ArrayList<Entity>(ws.loadedEntityList);
//            Iterator iterator = loadedEntities.iterator();
//            while (iterator.hasNext()) {
//                Entity e = (Entity) iterator.next();
//                String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
//                if (entitiesMap.get(key) != null) {
//                    entitiesMap.put(key, entitiesMap.get(key) + 1);
//                } else {
//                    entitiesMap.put(key, 1);
//                }
//            }
//
//            List<TileEntity> loadedTileEntities = new ArrayList<TileEntity>(ws.loadedTileEntityList);
//            Iterator iteratorTE = loadedTileEntities.iterator();
//            while (iteratorTE.hasNext()) {
//                TileEntity te = (TileEntity) iteratorTE.next();
//                String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
//                if (entitiesMap.get(key) != null) {
//                    entitiesMap.put(key, entitiesMap.get(key) + 1);
//                } else {
//                    entitiesMap.put(key, 1);
//                }
//            }
//        }
//        */
//
//        // по чанкам
//        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
//            for (Object obj : ws.theChunkProviderServer.loadedChunks) {
//                Chunk chunk = (Chunk) obj;
//
//                for (List l : chunk.entityLists) {
//                    for (Object o : l) {
//                        Entity e = (Entity) o;
//
//                        String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
//                        if (entitiesMap.get(key) != null) {
//                            entitiesMap.put(key, entitiesMap.get(key) + 1);
//                        } else {
//                            entitiesMap.put(key, 1);
//                        }
//                    }
//                }
//
//
//                for (Object o : chunk.chunkTileEntityMap.values()) {
//                    TileEntity te = (TileEntity) o;
//
//                    String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
//                    if (entitiesMap.get(key) != null) {
//                        entitiesMap.put(key, entitiesMap.get(key) + 1);
//                    } else {
//                        entitiesMap.put(key, 1);
//                    }
//                }
//            }
//        }
//
//        for (Map.Entry e : entitiesMap.entrySet()) {
//            if ((Integer) e.getValue() > 1) {
//                resultList.add((String)e.getKey());
//            }
//        }
//
//        return resultList;
//    }
}
