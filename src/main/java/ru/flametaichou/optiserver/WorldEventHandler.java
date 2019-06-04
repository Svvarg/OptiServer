package ru.flametaichou.optiserver;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Команды: тпс по мирам
// Инфо, кол-во загруженных чанков, сущностей

public class WorldEventHandler {

    private static long lastMemoryCheckTime = 0;
    private static long secondsBeforeClean = 30;
    private static long cleanTime = 0;
    private static long cleanAfterMessageTime = 0;

    private static long lastUsedMem = 0;
    private static int unloadedEntities = 0;
    private static int unloadedChunks = 0;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.world.provider.dimensionId == 0) {
            if (event.world.getTotalWorldTime() > lastMemoryCheckTime) {
                lastMemoryCheckTime = event.world.getTotalWorldTime();

                if (cleanTime == 0 && event.world.getTotalWorldTime() % ConfigHelper.checkInterval == 0) {
                    long freeMem =  OptiServerUtils.getFreeMemMB();

                    double averageTps = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        int dimensionId = ws.provider.dimensionId;
                        double worldTickTime =  OptiServerUtils.mean(MinecraftServer.getServer().worldTickTimes.get(dimensionId)) * 1.0E-6D;
                        double worldTPS = Math.min(1000.0 / worldTickTime, 20);
                        averageTps += worldTPS;
                    }

                    averageTps = averageTps / MinecraftServer.getServer().worldServers.length;

                    if (freeMem < ConfigHelper.memoryLimit || averageTps < ConfigHelper.tpsLimit) {

                        if (freeMem < ConfigHelper.memoryLimit) {
                            System.out.println("Memory limit! Free mem = " + freeMem);
                        }
                        if (averageTps < ConfigHelper.tpsLimit) {
                            System.out.println("Low TPS! TPS = " + averageTps);
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
                                if (entityCanBeUnloaded(entityLiving)) {
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

                    // Unload Chunks
                    unloadedChunks = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        unloadedChunks += ws.theChunkProviderServer.loadedChunks.size();
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
                                ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
                            }
                        }
                        ws.theChunkProviderServer.unloadQueuedChunks();
                    }

                    // GC
                    Runtime.getRuntime().gc();

                    cleanAfterMessageTime = event.world.getTotalWorldTime() + 20;
                }

                if (event.world.getTotalWorldTime() == cleanAfterMessageTime) {
                    cleanAfterMessageTime = 0;

                    int afterChunksCount = 0;
                    for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                        afterChunksCount += ws.theChunkProviderServer.loadedChunks.size();
                    }

                    System.out.println(String.format("Unloaded %s chunks!", unloadedChunks - afterChunksCount));

                    long usedMemAfterClean = OptiServerUtils.getUsedMemMB();
                    System.out.println(String.format("Memory clean profit = %s MB!",  lastUsedMem - usedMemAfterClean));

                    MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                            new ChatComponentTranslation(
                                    ConfigHelper.clearMessage,
                                    String.valueOf(unloadedEntities),
                                    String.valueOf(unloadedChunks - afterChunksCount),
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

    private boolean entityCanBeUnloaded(EntityLiving entityLiving) {
        return (!(entityLiving instanceof IEntityOwnable) &&
                !(entityLiving instanceof EntityAnimal) &&
                !entityLiving.isNoDespawnRequired() &&
                !entityLiving.getClass().getName().contains("CustomNpc"));
    }
}
