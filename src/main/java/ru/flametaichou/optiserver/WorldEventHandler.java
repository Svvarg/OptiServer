package ru.flametaichou.optiserver;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

import java.util.*;

// Команды: тпс по мирам
// Инфо, кол-во загруженных чанков, сущностей

public class WorldEventHandler {

    private static long lastMemoryCheckTime = 0;
    private static long secondsBeforeClean = 30;
    private static long cleanTime = 0;
    private static long cleanAfterMessageTime = 0;

    private static long lastUsedMem = 0;
    private static int unloadedEntities = 0;
    private static int removedBreedingEntities = 0;
    private static int loadedChunks = 0;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.provider.dimensionId == 0) {
            if (event.world.getTotalWorldTime() > lastMemoryCheckTime) {
                lastMemoryCheckTime = event.world.getTotalWorldTime();

                if (cleanTime == 0 && event.world.getTotalWorldTime() % ConfigHelper.checkInterval == 0) {
                    long freeMem =  OptiServerUtils.getFreeMemMB();

                    //double averageTps = 0;
                    double minTps = 20;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        int dimensionId = ws.provider.dimensionId;
                        double worldTickTime =  OptiServerUtils.mean(MinecraftServer.getServer().worldTickTimes.get(dimensionId)) * 1.0E-6D;
                        double worldTPS = Math.min(1000.0 / worldTickTime, 20);
                        if (worldTPS < minTps) {
                            minTps = worldTPS;
                        }
                        //averageTps += worldTPS;
                    }

                    //averageTps = averageTps / MinecraftServer.getServer().worldServers.length;

                    if (freeMem < ConfigHelper.memoryLimit || minTps < ConfigHelper.tpsLimit) {

                        if (freeMem < ConfigHelper.memoryLimit) {
                            System.out.println("Memory limit! Free mem = " + freeMem);
                        }
                        if (minTps < ConfigHelper.tpsLimit) {
                            System.out.println("Low TPS! TPS = " + minTps);
                        }

                        sheduleClean(event.world);
                    }
                }

                if (event.world.getTotalWorldTime() == cleanTime) {
                    cleanTime = 0;
                    lastUsedMem =  OptiServerUtils.getUsedMemMB();

                    // Clear
                    unloadedEntities = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        List<Entity> loadedEntities = ws.loadedEntityList;
                        Iterator iterator = loadedEntities.iterator();

                        List<Entity> entitiesForUnload = new ArrayList<Entity>();

                        while (iterator.hasNext())
                        {
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
                                    System.out.println("Skip entityItem " + entityItem);
                                }
                            } else if (e instanceof EntityFallingBlock || e instanceof IProjectile) {
                                entitiesForUnload.add(e);
                                e.setDead();
                            }
                        }

                        unloadedEntities += entitiesForUnload.size();
                        ws.unloadEntities(entitiesForUnload);
                    }
                    System.out.println(String.format("Unloaded %s entities!", unloadedEntities));

                    //Remove breeding entities
                    removedBreedingEntities = 0;
                    Map<String, Integer> breedingEntitiesMap = new HashMap<String, Integer>();

                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        List<Entity> loadedEntities = ws.loadedEntityList;
                        Iterator iterator = loadedEntities.iterator();

                        List<Entity> entitiesForUnload = new ArrayList<Entity>();

                        while (iterator.hasNext()) {
                            Entity e = (Entity) iterator.next();
                            String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
                            if (breedingEntitiesMap.get(key) != null) {
                                entitiesForUnload.add(e);
                                e.setDead();
                                removedBreedingEntities++;
                            } else {
                                breedingEntitiesMap.put(key, 1);
                            }
                        }

                        ws.unloadEntities(entitiesForUnload);

                        List<Entity> loadedTileEntities = ws.loadedTileEntityList;
                        Iterator iteratorTE = loadedTileEntities.iterator();
                        while (iteratorTE.hasNext()) {
                            TileEntity te = (TileEntity) iteratorTE.next();
                            String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
                            if (breedingEntitiesMap.get(key) != null) {
                                iteratorTE.remove();
                                removedBreedingEntities++;
                            } else {
                                breedingEntitiesMap.put(key, 1);
                            }
                        }
                    }
                    System.out.println(String.format("Unloaded %s breding entities!", removedBreedingEntities));

                    // Unload Chunks
                    loadedChunks = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        loadedChunks += ws.theChunkProviderServer.loadedChunks.size();
                        Iterator iterator = ws.theChunkProviderServer.loadedChunks.iterator();

                        while (iterator.hasNext())
                        {
                            boolean needUnload = true;
                            Chunk chunk = (Chunk)iterator.next();
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

                    cleanAfterMessageTime = event.world.getTotalWorldTime() + 20;
                }

                if (event.world.getTotalWorldTime() == cleanAfterMessageTime) {
                    cleanAfterMessageTime = 0;

                    int afterChunksCount = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        afterChunksCount += ws.theChunkProviderServer.loadedChunks.size();
                    }

                    System.out.println(String.format("Unloaded %s chunks!", loadedChunks - afterChunksCount));

                    long usedMemAfterClean = OptiServerUtils.getUsedMemMB();
                    System.out.println(String.format("Memory clean profit = %s MB!",  lastUsedMem - usedMemAfterClean));

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
    }

    public static void sheduleClean(World world) {
        MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                new ChatComponentTranslation(ConfigHelper.beforeClearMessage, String.valueOf(secondsBeforeClean))
        );

        cleanTime = world.getTotalWorldTime() + (secondsBeforeClean * 20);
    }
}
