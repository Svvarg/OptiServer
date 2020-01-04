package ru.flametaichou.optiserver.util;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldServer;

import java.util.List;

public class OptiServerUtils {

    public static long mean(long[] values) {
        long average = 0;
        for (long l : values) {
            average += l;
        }
        return average / values.length;
    }

    public static long getUsedMemMB() {
        long freeMem = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
        long usedMem = Runtime.getRuntime().maxMemory() - freeMem;
        return usedMem / 1000000;
    }

    public static long getFreeMemMB() {
        long freeMem = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
        return freeMem / 1000000;
    }

    public static long getTotalMemoryMB() {
        return Runtime.getRuntime().totalMemory() / 1000000;
    }

    public static long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / 1000000;
    }

    public static boolean entityCanBeUnloaded(EntityLiving entityLiving) {
        return (!(entityLiving instanceof IEntityOwnable) &&
                !(entityLiving instanceof EntityAnimal) &&
                !(entityLiving instanceof INpc) &&
                !(entityLiving instanceof IMerchant) &&
                !entityLiving.isNoDespawnRequired() &&
                !entityLiving.getClass().getName().contains("CustomNpc"));
    }

    public static Double getMinTps() {
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
        return minTps;
    }

    public static String getTwoSymbolsNumber(int number) {
        return number < 10 ? ("0" + number) : String.valueOf(number);
    }

    public static String generateConsoleColorsString(String str) {
        str = str.replace("§0", "\\033[0;30m");
        str = str.replace("§1", "\\033[0;34m");
        str = str.replace("§2", "\\033[0;32m");
        str = str.replace("§3", "\\033[0;36m");
        str = str.replace("§4", "\\033[0;31m");
        str = str.replace("§5", "\\033[0;35m");
        str = str.replace("§6", "\\033[0;33m");
        str = str.replace("§7", "\\033[0;37m");
        str = str.replace("§8", "\\033[1;30m");
        str = str.replace("§9", "\\033[1;34m");
        str = str.replace("§a", "\\033[1;32m");
        str = str.replace("§b", "\\033[1;36m");
        str = str.replace("§c", "\\033[1;31m");
        str = str.replace("§d", "\\033[1;35m");
        str = str.replace("§e", "\\033[1;33m");
        //str = str.replace("§f", "\\033[1;37m");
        str = str.replace("§f", "\\033[0m");

        return str;
    }

    public static String reformatMessageForConsole(String str) {
        str = str.replace(EnumChatFormatting.GREEN + "##", "##");
        str = str.replace(EnumChatFormatting.YELLOW + "##", "##");
        str = str.replace(EnumChatFormatting.RED + "##", "##");
        str = str.replace(EnumChatFormatting.DARK_RED + "##", "##");
        str = str.replace(EnumChatFormatting.DARK_GRAY + "##", "  ");

        str = str.replace(EnumChatFormatting.GREEN + "#", "#");
        str = str.replace(EnumChatFormatting.YELLOW + "#", "#");
        str = str.replace(EnumChatFormatting.RED + "#", "#");
        str = str.replace(EnumChatFormatting.DARK_RED + "#", "#");
        str = str.replace(EnumChatFormatting.DARK_GRAY + "#", " ");

        str = str.replace("§0", "");
        str = str.replace("§1", "");
        str = str.replace("§2", "");
        str = str.replace("§3", "");
        str = str.replace("§4", "");
        str = str.replace("§5", "");
        str = str.replace("§6", "");
        str = str.replace("§7", "");
        str = str.replace("§8", "");
        str = str.replace("§9", "");
        str = str.replace("§a", "");
        str = str.replace("§b", "");
        str = str.replace("§c", "");
        str = str.replace("§d", "");
        str = str.replace("§e", "");
        str = str.replace("§f", "");

        return str;
    }

    private static void makeCustomNpcDespawnable(Entity entity) {
        NBTTagCompound data = new NBTTagCompound();
        entity.writeToNBT(data);
        data.setInteger("SpawnCycle", 3);
        entity.readFromNBT(data);
    }

    public static void unloadEntity(Entity entity) {
        if (entity.getClass().getSimpleName().equals("EntityCustomNpc")) {
            makeCustomNpcDespawnable(entity);
        }
        entity.setDead();

        if (!entity.worldObj.isRemote) {
            WorldManager worldManager = new WorldManager(MinecraftServer.getServer(), (WorldServer) entity.worldObj);
            worldManager.onEntityDestroy(entity);

        }
        //entity.worldObj.removeEntity(entity);
        //entity.worldObj.onEntityRemoved(entity);
        //entity.worldObj.unloadEntities(Arrays.asList(entity));

        //WorldServer worldServer = (WorldServer) entity.worldObj;
        //worldServer.getEntityTracker().removeEntityFromAllTrackingPlayers(entity);
        //WorldManager worldManager = new WorldManager(MinecraftServer.getServer(), (WorldServer) entity.worldObj);
        //worldManager.onEntityDestroy(entity);
    }

    public static boolean approximatelyEquals(double d1, double d2) {
        return Math.abs(d1 - d2) < 0.001;
    }

    public static boolean isDuplicate(Entity entity) {
        int radius = 1;
        List nearestEntities = entity.worldObj.getEntitiesWithinAABB(
                entity.getClass(),
                AxisAlignedBB.getBoundingBox(
                        entity.posX-radius,
                        entity.posY-radius,
                        entity.posZ-radius,
                        (entity.posX + radius),
                        (entity.posY + radius),
                        (entity.posZ + radius)
                )
        );

        if (!nearestEntities.isEmpty()) {
            for (Object nearestEntity : nearestEntities) {
                Entity e = (Entity) nearestEntity;
                if (e.getCommandSenderName().equals(entity.getCommandSenderName())
                        && e.getEntityId() != entity.getEntityId()
                        && OptiServerUtils.approximatelyEquals(e.posX, entity.posX)
                        && OptiServerUtils.approximatelyEquals(e.posY, entity.posY)
                        && OptiServerUtils.approximatelyEquals(e.posZ, entity.posZ)) {

                    return true;
                }
            }
        }

        return false;
    }
}
