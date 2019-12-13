package ru.flametaichou.optiserver.util;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

public class Logger {

    private static final String prefix = "[OptiServer] ";
    private static final String prefixInfo = "(INFO) ";
    private static final String prefixWarn= "(WARNING) ";
    private static final String prefixEror= "(ERROR) ";
    private static final String preffixDebug= "(DEBUG) ";

    public static void log(String string) {
        System.out.println(prefix + prefixInfo + string);
    }

    public static void warn(String string) {
        System.out.println(prefix + prefixWarn + string);
    }

    public static void error(String string) {
        System.err.println(prefix + prefixEror + string);
    }

    public static void debug(String string) {
        if (ConfigHelper.debugMode) System.out.println(prefix + preffixDebug + string);
    }

    public static String getCoordinatesString(int x, int y, int z) {
        return "x:" + x + " y:" + y + " z:" + z;
    }

    public static String getCoordinatesString(Entity e) {
        return "xyz: " + (int)e.posX + " " + (int)e.posY + " " + (int)e.posZ;
    }

    public static String getCoordinatesString(TileEntity e) {
        return "xyz: " + e.xCoord + " " + e.yCoord + " " + e.zCoord;
    }
}
