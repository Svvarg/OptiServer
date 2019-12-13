package ru.flametaichou.optiserver.handlers;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.OptiServer;
import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;

import java.util.*;

public class WorldEventHandler {

    private static long lastMemoryCheckTime = 0;
    private static long secondsBeforeClean = 30;
    private static long cleanTime = 0;
    private static long cleanAfterMessageTime = 0;

    private static long lastUsedMem = 0;
    private static int unloadedEntities = 0;
    private static int removedBreedingEntities = 0;
    private static int loadedChunks = 0;

    private static long lastGetStatsTime = 0;

    private static int maxMapSize = 144;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {

    }

    public static void sheduleClean() {
        if (cleanTime == 0) {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                    new ChatComponentTranslation(ConfigHelper.beforeClearMessage, String.valueOf(secondsBeforeClean))
            );

            cleanTime = MinecraftServer.getSystemTimeMillis() + (secondsBeforeClean * 1000);
        } else {
            Logger.warn("Clean is already scheduled!");
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == Side.SERVER) {

            // Stats
            if (MinecraftServer.getSystemTimeMillis() - lastGetStatsTime >= (ConfigHelper.statsInterval * 60 * 1000)) {
                lastGetStatsTime = MinecraftServer.getSystemTimeMillis();

                Date now = new Date();

                double tps = OptiServerUtils.getMinTps();
                double memory = OptiServerUtils.getFreeMemMB();

                OptiServer.tpsStatsMap.put(now, tps);
                OptiServer.memoryStatsMap.put(now, memory);

                // Clean
                List<Map<Date, Double>> maps = Arrays.asList(OptiServer.memoryStatsMap, OptiServer.tpsStatsMap);
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

            // Clean check
            if (cleanTime == 0 && MinecraftServer.getSystemTimeMillis() - lastMemoryCheckTime > (ConfigHelper.checkInterval * 60 * 1000)) {
                lastMemoryCheckTime = MinecraftServer.getSystemTimeMillis();

                long freeMem = OptiServerUtils.getFreeMemMB();

                //double averageTps = 0;
                double minTps = OptiServerUtils.getMinTps();

                //averageTps = averageTps / MinecraftServer.getServer().worldServers.length;

                if (freeMem < ConfigHelper.memoryLimit || minTps < ConfigHelper.tpsLimit) {

                    if (freeMem < ConfigHelper.memoryLimit) {
                        Logger.warn("Memory limit! Free mem = " + freeMem);
                    }
                    if (minTps < ConfigHelper.tpsLimit) {
                        Logger.warn("Low TPS! TPS = " + minTps);
                    }

                    sheduleClean();
                }
            }

            // Clean
            if (cleanTime != 0 && MinecraftServer.getSystemTimeMillis() >= cleanTime) {
                cleanTime = 0;

                lastUsedMem = OptiServerUtils.getUsedMemMB();

                unloadedEntities = 0;
                for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                    List<Entity> loadedEntities = ws.loadedEntityList;
                    Iterator iterator = loadedEntities.iterator();

                    List<Entity> entitiesForUnload = new ArrayList<Entity>();

                    while (iterator.hasNext()) {
                        Entity e = (Entity) iterator.next();
                        if (e instanceof EntityLiving) {
                            EntityLiving entityLiving = (EntityLiving) e;
                            if (OptiServerUtils.entityCanBeUnloaded(entityLiving)) {
                                entitiesForUnload.add(e);
                                e.setDead();
                            }
                        } else if (e instanceof EntityItem) {
                            EntityItem entityItem = (EntityItem) e;

                            // Check ids
                            String itemId = String.valueOf(Item.getIdFromItem(entityItem.getEntityItem().getItem()));
                            if (!ConfigHelper.itemBlacklist.contains(itemId)) {
                                entitiesForUnload.add(e);
                                e.setDead();
                            } else {
                                Logger.log("Skip entityItem " + entityItem);
                            }
                        } else if (e instanceof EntityFallingBlock || e instanceof IProjectile) {
                            entitiesForUnload.add(e);
                            e.setDead();
                        }
                    }

                    unloadedEntities += entitiesForUnload.size();
                    ws.unloadEntities(entitiesForUnload);
                }
                Logger.log(String.format("Unloaded %s entities!", unloadedEntities));

                //Remove breeding entities
                removedBreedingEntities = 0;
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
                            Logger.log(String.format("Unloading breding entity: %s (%s)", e.getCommandSenderName(), Logger.getCoordinatesString(e)));
                            OptiServerUtils.unloadEntity(e);
                            removedBreedingEntities++;
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
                            Logger.log(String.format("Unloading breding entity: %s (%s)", te.getClass().getSimpleName(), Logger.getCoordinatesString(te)));
                            iteratorTE.remove();
                            removedBreedingEntities++;
                        } else {
                            breedingEntitiesMap.put(key, 1);
                        }
                    }
                }
                Logger.log(String.format("Unloaded %s breding entities!", removedBreedingEntities));

                // Unload Chunks
                loadedChunks = 0;
                for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                    loadedChunks += ws.theChunkProviderServer.loadedChunks.size();
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
                            MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
                            ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
                        }
                    }
                    ws.theChunkProviderServer.unloadQueuedChunks();
                }

                // GC
                //Runtime.getRuntime().gc();
                System.gc();

                cleanAfterMessageTime = MinecraftServer.getSystemTimeMillis() + 1000;
            }

            // Message after clean
            if (cleanAfterMessageTime != 0 && MinecraftServer.getSystemTimeMillis() >= cleanAfterMessageTime) {
                cleanAfterMessageTime = 0;

                int afterChunksCount = 0;
                for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                    afterChunksCount += ws.theChunkProviderServer.loadedChunks.size();
                }

                Logger.log(String.format("Unloaded %s chunks!", loadedChunks - afterChunksCount));

                long usedMemAfterClean = OptiServerUtils.getUsedMemMB();
                Logger.log(String.format("Memory clean profit = %s MB!", lastUsedMem - usedMemAfterClean));

                MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                        new ChatComponentTranslation(
                                ConfigHelper.clearMessage,
                                String.valueOf(unloadedEntities + removedBreedingEntities),
                                String.valueOf(loadedChunks - afterChunksCount),
                                String.valueOf(lastUsedMem - usedMemAfterClean)
                        )
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntityJoinWorldEvent event) {
        int radius = 1;
        List nearestEntities = event.entity.worldObj.getEntitiesWithinAABB(
                event.entity.getClass(),
                AxisAlignedBB.getBoundingBox(
                        event.entity.posX-radius,
                        event.entity.posY-radius,
                        event.entity.posZ-radius,
                        (event.entity.posX + radius),
                        (event.entity.posY + radius),
                        (event.entity.posZ + radius)
                )
        );

        if (!nearestEntities.isEmpty()) {
            Iterator iterator = nearestEntities.iterator();
            while (iterator.hasNext()) {
                Entity e = (Entity) iterator.next();
                if (e.getCommandSenderName().equals(event.entity.getCommandSenderName())
                        && approximatelyEquals(e.posX, event.entity.posX)
                        && approximatelyEquals(e.posY, event.entity.posY)
                        && approximatelyEquals(e.posZ, event.entity.posZ)) {

                    Logger.log(String.format("Unloading breding entity on spawn: %s (%s)", e.getCommandSenderName(), Logger.getCoordinatesString(e)));
                    OptiServerUtils.unloadEntity(e);
                }
            }
        }
    }

    private static boolean approximatelyEquals(double d1, double d2) {
        return Math.abs(d1 - d2) < 0.01;
    }
}
