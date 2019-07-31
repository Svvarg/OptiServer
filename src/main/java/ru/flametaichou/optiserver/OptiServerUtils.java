package ru.flametaichou.optiserver;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.INpc;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

}
