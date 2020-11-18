package ru.flametaichou.optiserver.util;

import java.util.List;
import java.util.ArrayList;

import cpw.mods.fml.common.FMLLog;

import net.minecraft.item.Item;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class ConfigHelper {

    public ConfigHelper() {
    }

    public static int memoryLimit;
    public static int checkInterval;
    public static int statsInterval;
    public static int tpsLimit;
    public static int secBeforeClean;
    public static String beforeClearMessage;
    public static String clearMessage;
    public static boolean checkTileEntityDuplicates;
    public static boolean debugMode = true;

    /*имена ресурсов из конфига для преобразование в конкретные классы и обьекты в postInit*/
    private static String[] allowedSwarmSpawnClassNames;
    private static String[] persistEntityClassNames;
    private static String[] persistItemsIds;
    /* --- For RunTime --- */
    //список классов существ, которым позволено спавниться по несколько штук в "одном блоке"
    public static List<Class> allowedSwarmSpawnEntitiesList;
    public static List<Class> persistEntitiesList;
    public static List<Object> persistItemsList;

    public static Configuration config;
    public static final String CAT = "settings"; //auto toLoawcase in config

    public static Class classEntityCustomNpc;
    public static Class classEntityLootableBody;

    public static void setupConfig(Configuration aConfig) {
        try {
            config = aConfig;
            config.load();
            //                                                   def min max
            memoryLimit    = config.getInt("MemoryLimit",   CAT, 1000, 10, 9000, "When less than this value of FreeMemory start cleaning (Mb)");
            checkInterval  = config.getInt("CheckInterval", CAT, 2,  1, 60, "Interval for checking whether cleaning is necessary (Min)");
            statsInterval  = config.getInt("StatsInterval", CAT, 5,  1, 60, "How often do I take server status measurements (Min)");
            tpsLimit       = config.getInt("TpsLimit",      CAT, 18,10, 19, "The tps value below which cleaning is plannedTo shedule cleaning");
            secBeforeClean = config.getInt("secBeforeClean",CAT, 60, 1, 700, "How many seconds to warn about the upcoming cleaning (Sec)");
            checkTileEntityDuplicates = config.getBoolean("CheckTileEntityDuplicates", CAT, false, "When checking for the need to start cleaning");
            
            beforeClearMessage = config.getString("MessageBeforeClear", CAT,
                    "Server overloaded! Memory clear after §b%s§f seconds!",
                    "Message that will be shown to players before cleaning.");
            
            clearMessage = config.getString("MessageClear", CAT,
                    "Memory cleaned! §b%s§f objects and §b%s§f chunks removed. Releases §b%s§fMB RAM.",
                    "Message that will be shown to players after cleaning.");
            
            persistItemsIds = config.getStringList("persistItemsIds", CAT,
                    new String[] {
                        "264", //diamond
                        "328"  //minecart
                    },                    
                    "IDs of items that should not be deleted.");

            persistEntityClassNames = config.getStringList("PersistEntityClassNames", CAT,
                    new String[] {
                        "noppes.npcs.entity.EntityCustomNpc", //noppes.npcs.entity.EntityNPCInterface - от него наследуются многие другие мобы в кастоме их там может быть несколько видов
                        "cyano.lootable.entities.EntityLootableBody"
                    },
                    "Class Names of Entities that should not be deleted.");

            allowedSwarmSpawnClassNames = config.getStringList("AllowedSwarmSpawnClassNames", CAT,
                    new String[] {
                        "ru.flamesword.ordinaryores.entities.EntityUndeadSpidy",
                        "ru.flamesword.ordinaryores.entities.EntitySprout",
                        "net.daveyx0.primitivemobs.entity.monster.EntityDMinion"
                    },
                    "Class Names of Entities that are allowed to spawn in the same coord point (not check atEntityJoinWorldEvent)");
            
        }
        catch (Exception e) {
            Logger.error("A severe error has occured when attempting to load the config file for this mod!");
        }
        finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static Property getProperty(ConfigCategory cat, String key) {
        if (cat != null && !cat.isEmpty() || key!=null || !key.isEmpty()) {
            return cat.get(key);
        }
        return null;
    }
    /**
     * Только на уже существующие т.е. строго после setupConfig
     * т.к. если установочного ключа в категории не будет он не поставит дефолтное значение
     * @param cat
     * @param key
     * @param value
     */
    public static boolean setIntProp(ConfigCategory cat, String key, int value) {
        Property p = getProperty(cat, key);
        if (p != null) {
            p.set(value);
            return true;
        }
        return false;
    }

    public static boolean setBoolProp(ConfigCategory cat, String key, boolean value) {
        Property p = getProperty(cat, key);
        if (p != null) {
            p.set(value);
            return true;
        }
        return false;
    }
    
    public static boolean setAStrProp(ConfigCategory cat, String key, String[] value) {
        Property p = getProperty(cat, key);
        if (p != null) {
            p.set(value);
            return true;
        }
        return false;
    }

    public static void reload() {
        setupConfig(config);
    }

    public static boolean saveCurrentState() {
        try {            
            if (config != null && config.hasChanged() ) {
                ConfigCategory settings = config.getCategory(CAT); //с малой буквы т.к. внутри делает lowToCase
                setIntProp(settings, "MemoryLimit",   memoryLimit);
                setIntProp(settings, "CheckInterval", checkInterval);
                setIntProp(settings, "StatsInterval", statsInterval);
                setIntProp(settings, "TpsLimit",      tpsLimit);
                setIntProp(settings, "SecBeforeClean", secBeforeClean);
                setBoolProp(settings,"CheckTileEntityDuplicates",checkTileEntityDuplicates);

                if (isValidList(persistItemsList)) {
                    //todo добавлять к старым...
                    persistItemsIds = restoreItemIds(persistItemsList);
                    setAStrProp(settings, "PersistItemsIds", persistItemsIds);
                }
                if (isValidList(persistEntitiesList)) {
                    persistEntityClassNames = restoreClassNames(persistEntitiesList);
                    setAStrProp(settings, "PersistEntitiesClassNames", persistItemsIds);
                }
                if (isValidList(allowedSwarmSpawnEntitiesList)) {
                    allowedSwarmSpawnClassNames = restoreClassNames(allowedSwarmSpawnEntitiesList);
                    setAStrProp(settings, "AllowedSwarmSpawnClassNames", persistItemsIds);
                }

                if (config.hasChanged()) {
                    config.save();
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.error("A severe error has occured when attempting to load the config file for this mod!");
        }
        return false;
    }

    /**
     * Выполнять только после загрузки всех модов FMLPostInitializationEvent
     * return Loader.isModLoaded("NotEnoughItems");
     *
     * @return
     */
    public static int createPersistsEtitiesAndItems() {
        int count = 0;

        FMLLog.warning("Attention search classes for Persisten Entities");
        persistEntitiesList = new ArrayList<Class>();

        //классы существ которые не удалять при очистке воссоздаються по именам класса
        for (int i = 0; i < persistEntityClassNames.length; i++) {
            String name = persistEntityClassNames[i];
            try {
                Class cl = Class.forName(name);
                if (cl != null && !persistEntitiesList.contains(cl)) {
                    persistEntitiesList.add(cl);
                    FMLLog.fine("Class of Persisten Entity Founded %s", name);
                }
                count++;
            }
            catch (ClassNotFoundException ex) {
                FMLLog.warning("Not Found Class: %s", name);
            }
        }
        //persistEntityClassNames = null;

        FMLLog.warning("Attention search classes for ignorSpawnCheckEntities");
        allowedSwarmSpawnEntitiesList = new ArrayList<Class>();
        //классы существ которые не удалять при очистке воссоздаються по именам класса
        for (int i = 0; i < allowedSwarmSpawnClassNames.length; i++) {
            String name = allowedSwarmSpawnClassNames[i];
            try {
                Class cl = Class.forName(name);
                if (cl != null && !allowedSwarmSpawnEntitiesList.contains(cl)) {
                    allowedSwarmSpawnEntitiesList.add(cl);
                    FMLLog.fine("Class of IgnoreSpawnCheck Entity Founded %s", name);
                }
                count++;
            }
            catch (ClassNotFoundException ex) {
                FMLLog.warning("Not Found Class: %s", name);
            }
        }
        //allowedSwarmSpawnClassNames = null;//??

        FMLLog.warning("Attention Recreate Objects of Persistents Items by ids");
        persistItemsList = new ArrayList();
        //обьекты вещей которые не удалять при очистках воссоздаются по их идишникам
        for (int i = 0; i < persistItemsIds.length; i++) {
            String sId = persistItemsIds[i];
            if (sId == null || sId.isEmpty()) {
                continue;
            }
            try {
                int id = Integer.parseInt(sId);
                if (id > 0) {
                    Object item = Item.getItemById(id);
                    if (item != null) {
                        if (!persistItemsList.contains(item)) {
                            persistItemsList.add(item);
                            count++;
                        }
                        else {
                            FMLLog.warning("Item already added: %s", sId);
                        }
                    }
                    else {
                        FMLLog.warning("Item with %s id not found", sId);
                    }
                }
            }
            catch (NumberFormatException nfe) {
                FMLLog.warning("illegal Item Id: %s", sId);
            }
        }
        //persistItemsIds = null;//?

        return count;
    }

    public static List<Class> restoreClassesFromNases(String[] names) {
        return null;
    }

    public static boolean isValidList(List l) {
        if (l==null || l.size()==0) return false;
        for (int i = 0; i < l.size(); i++) {
            if (l==null) return false;
        }
        return true;
    }
    
    public static String[] restoreClassNames(List<Class> classes) {
        int size = classes == null ? 0: classes.size();
        String[] names = new String[size];
        for (int i = 0; i < size; i++) {
            Class cl = classes.get(i);
            names[i] = cl==null?"":cl.getCanonicalName();
        }
        return names;
    }
    
    public static String[] restoreItemIds(List<Object> items) {
        int size = items == null ? 0: items.size();
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            Object obj = items.get(i);
            String value;
            if (obj instanceof Item) {
                value = String.valueOf(Item.getIdFromItem((Item)obj));
            } else {
                value = "";
            }
            ids[i] = value;            
        }
        return ids;
    }
}
