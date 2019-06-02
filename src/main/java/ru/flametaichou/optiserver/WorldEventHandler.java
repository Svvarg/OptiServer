package ru.flametaichou.optiserver;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.Sys;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.LongStream;

// Команды: тпс по мирам
// Инфо, кол-во загруженных чанков, сущностей

public class WorldEventHandler {

    private static long lastMemoryCheckTime = 0;
    private static long secondsBeforeClean = 30;
    private static long cleanTime = 0;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.provider.dimensionId == 0) {
            if (event.world.getTotalWorldTime() > lastMemoryCheckTime) {
                lastMemoryCheckTime = event.world.getTotalWorldTime();

                if (cleanTime == 0 && event.world.getTotalWorldTime() % ConfigHelper.checkInterval == 0) {
                    long usedMem = getUsedMemMB();

                    double averageTps = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        int dimensionId = ws.provider.dimensionId;
                        double worldTickTime = mean(MinecraftServer.getServer().worldTickTimes.get(dimensionId)) * 1.0E-6D;
                        double worldTPS = Math.min(1000.0 / worldTickTime, 20);
                        averageTps += worldTPS;
                    }

                    averageTps = averageTps / MinecraftServer.getServer().worldServers.length;

                    if (usedMem >= ConfigHelper.memoryLimit || averageTps < ConfigHelper.tpsLimit) {

                        MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                                new ChatComponentTranslation(ConfigHelper.beforeClearMessage, String.valueOf(secondsBeforeClean))
                        );

                        cleanTime = event.world.getTotalWorldTime() + (secondsBeforeClean * 20);
                    }
                }

                if (event.world.getTotalWorldTime() == cleanTime) {
                    cleanTime = 0;
                    long usedMem = getUsedMemMB();

                    // Unload Chunks
                    int chunksCount = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        chunksCount += ws.theChunkProviderServer.loadedChunks.size();
                        Iterator iterator = ws.theChunkProviderServer.loadedChunks.iterator();

                        while (iterator.hasNext())
                        {
                            Chunk chunk = (Chunk)iterator.next();
                            ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
                        }
                        ws.theChunkProviderServer.unloadQueuedChunks();
                    }

                    int afterChunksCount = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        afterChunksCount += ws.theChunkProviderServer.loadedChunks.size();
                    }

                    System.out.println(String.format("Unloaded %s chunks!", chunksCount - afterChunksCount));

                    // Clear
                    int entitiesCount = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        List<Entity> loadedEntities = ws.loadedEntityList;
                        Iterator iterator = loadedEntities.iterator();

                        List<Entity> entitiesForUnload = new ArrayList<Entity>();

                        while (iterator.hasNext())
                        {
                            Entity e = (Entity) iterator.next();
                            if (e instanceof EntityLiving) {
                                EntityLiving entityLivingBase = (EntityLiving) e;
                                if (!(entityLivingBase instanceof EntityAnimal) && !entityLivingBase.isNoDespawnRequired()) {
                                    System.out.println("Want to unload " + entityLivingBase);
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

                        entitiesCount += entitiesForUnload.size();
                        ws.unloadEntities(entitiesForUnload);
                    }
                    System.out.println(String.format("Unloaded %s entities!", entitiesCount));

                    // GC
                    Runtime.getRuntime().gc();


                    long usedMemAfterClean = getUsedMemMB();
                    System.out.println(String.format("Memory clean profit = %s MB!",  usedMem - usedMemAfterClean));


                    MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                            new ChatComponentTranslation(ConfigHelper.clearMessage, String.valueOf(entitiesCount), String.valueOf(chunksCount), String.valueOf(usedMem - usedMemAfterClean))
                    );
                }
            }
        }
    }

    public static long mean(long[] values) {
        long average = 0;
        for (long l : values) {
            average += l;
        }
        return average / values.length;
    }

    private static long getUsedMemMB() {
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return usedMem / 1000000;
    }
}
