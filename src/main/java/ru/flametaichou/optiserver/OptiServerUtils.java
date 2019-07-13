package ru.flametaichou.optiserver;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.INpc;
import net.minecraft.entity.passive.EntityAnimal;

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
}
