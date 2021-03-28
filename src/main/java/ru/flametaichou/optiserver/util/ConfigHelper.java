package ru.flametaichou.optiserver.util;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

import net.minecraft.item.Item;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import ru.flametaichou.optiserver.OptiServer;
import org.swarg.mcforge.util.eventshook.MemoryTracker;
import org.swarg.mcforge.hooks.EntityEventHooks;
import org.swarg.mcforge.util.XEntity;
import static ru.flametaichou.optiserver.OptiServer.LOG;
import static org.swarg.mcforge.util.ConfigUtil.*;
import static org.swarg.common.AUtil.safe;


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
    /*нужно найти способ как задавать для своего логгера level а пока так*/
    public static boolean debugMode = true;//<-?->(Mlog)LOG.debug

    public static final List<Class> allowedSwarmSpawnEntitiesList = new ArrayList<Class>();//список классов существ, которым позволено спавниться по несколько штук в "одном блоке"
    public static final List<Class> persistEntitiesList = new ArrayList<Class>();  //список классов существ которых не удалять при очистке
    public static final List<Object> persistItemsList = new ArrayList<Object>();

    private static Configuration config;
    private static final String CAT = "settings"; //auto toLoawcase in config

    //todo framework save load config with reflection and annatations
    private static final String _MemoryLimit   = "MemoryLimit";
    private static final String _CheckInterval = "CheckInterval";
    private static final String _StatsInterval = "StatsInterval";
    private static final String _TpsLimit      = "TpsLimit";
    private static final String _SecBeforeClean= "SecBeforeClean";
    private static final String _CheckTileEntityDuplicates = "CheckTileEntityDuplicates";
    private static final String _MessageBeforeClear = "MessageBeforeClear";
    private static final String _MessageClear  = "MessageClear";
    private static final String _PersistItemsIds= "PersistItemsIds";
    private static final String _PersistEntitiesClassNames = "PersistEntityClassNames";
    private static final String _AllowedSwarmSpawnClassNames = "AllowedSwarmSpawnClassNames"; // canSpawnPack может порождать стаю


    public static void setupConfig(Configuration aConfig) {
        try {
            config = aConfig;
            config.load();
            //                                                   def min max
            memoryLimit    = config.getInt(_MemoryLimit,   CAT, 1000, 10, 9000, "When less than this value of FreeMemory start cleaning (Mb)");
            checkInterval  = config.getInt(_CheckInterval, CAT, 2,  1, 60, "Interval for checking whether cleaning is necessary (Min)");
            statsInterval  = config.getInt(_StatsInterval, CAT, 5,  1, 60, "How often do I take server status measurements (Min)");
            tpsLimit       = config.getInt(_TpsLimit,      CAT, 18,10, 19, "The tps value below which cleaning is plannedTo shedule cleaning");
            secBeforeClean = config.getInt(_SecBeforeClean,CAT, 60, 1, 700, "How many seconds to warn about the upcoming cleaning (Sec)");
            checkTileEntityDuplicates = config.getBoolean("CheckTileEntityDuplicates", CAT, false, "When checking for the need to start cleaning");
            
            beforeClearMessage = config.getString(_MessageBeforeClear, CAT,
                    "Server overloaded! Memory clear after §b%s§f seconds!",
                    "Message that will be shown to players before cleaning.");
            
            clearMessage = config.getString(_MessageClear, CAT,
                    "Memory cleaned! §b%s§f objects and §b%s§f chunks removed. Releases §b%s§fMB RAM.",
                    "Message that will be shown to players after cleaning.");
            
            //смысл в том что здесь идёт установка дефолтных значений если значение не задано конфигом
            //получение самих значений будет в bindEntitesClassesAndItemsInstances
            config.getStringList(_PersistItemsIds, CAT,
                    new String[] {
                        "264", //diamond
                        "328"  //minecart
                    },                    
                    "IDs of items that should not be deleted.");

            config.getStringList(_PersistEntitiesClassNames, CAT,
                    new String[] {
                        //поддержку CUSTOM_NPC добавил на уровне базового функционала XEntity
                        //noppes.npcs.entity.EntityNPCInterface - от него наследуются многие другие мобы в кастоме их там может быть несколько видов
                        XEntity.CN_CYANO_ENTITY_LOOTABLE_BODY //TODO
                    },
                    "Class Names of Entities that should not be deleted.");

            config.getStringList(_AllowedSwarmSpawnClassNames, CAT,
                    new String[] {
                        "ru.flamesword.ordinaryores.entities.EntityUndeadSpidy",
                        "ru.flamesword.ordinaryores.entities.EntitySprout",
                        "net.daveyx0.primitivemobs.entity.monster.EntityDMinion"
                    },
                    "Class Names of Entities that are allowed to spawn in the same coord point (not check atEntityJoinWorldEvent)");

            LOG.setDebugOwn(debugMode);
            //DEBUG>>
                MemoryTracker.instance().register();
                //активирует все хуки на значимые события связанные с существами
                EntityEventHooks.instance().register().setActive(null, true);
            //<<DEBUG
        }
        catch (Exception e) {
            LOG.error("A severe error has occured when attempting to load the config file for this mod!", e);
        }
        finally {
            if (config != null && config.hasChanged()) {
                config.save();
            }
        }
        //bindEntitesClassesAndItemsInstances вызывать только после загрузки всех модов!
    }

    // сохранить текущие настройки в файл конфига
    public static boolean saveCurrentState() {
        try {
            if (config != null &&
                //getConfigFileForModId(OptiServer.MODID) todo??
                //для того чтобы убедиться что метод вызван уже после загрузки существующего конфига
                beforeClearMessage != null && clearMessage != null)
            {
                ConfigCategory settings = config.getCategory(CAT);
                setIntProp(settings, _MemoryLimit,   memoryLimit);
                setIntProp(settings, _CheckInterval, checkInterval);
                setIntProp(settings, _StatsInterval, statsInterval);
                setIntProp(settings, _TpsLimit,      tpsLimit);
                setIntProp(settings, _SecBeforeClean, secBeforeClean);
                setBoolProp(settings,_CheckTileEntityDuplicates,checkTileEntityDuplicates);

                setAStrProp(settings, _PersistItemsIds, restoreItemIds(persistItemsList));
                
                //if (isValidList(persistEntitiesList)) {
                setAStrProp(settings, _PersistEntitiesClassNames, restoreClassNames(persistEntitiesList));
                
                setAStrProp(settings, _AllowedSwarmSpawnClassNames, restoreClassNames(allowedSwarmSpawnEntitiesList));

                config.save();
                return true;
            }
        } catch (Exception e) {
            LOG.error("A severe error has occured on saveCurrentState", e);
        }
        return false;
    }
    
    //перегрузить конфиг без сохранения настроек текущего состояния
    //c полным пересозданием исключений для очистки (существа предметы)
    public static void reload() {
        File modConfigFile =  (config == null)
                ? getConfigFileForModId(OptiServer.MODID)
                : config.getConfigFile();
        setupConfig(new Configuration(modConfigFile));
        bindEntitesClassesAndItemsInstances();
    }

    /**
     * Выполнять только после загрузки всех модов FMLPostInitializationEvent
     * либо при перезагрузки конфига
     * return Loader.isModLoaded("NotEnoughItems");
     *
     * @return
     */
    public static int bindEntitesClassesAndItemsInstances() {
        int resolvedEntityClasses = 0;
        int resolvedItemInstances = 0;

        LOG.info("[CONFIG>>] Getting Started to Bind Entites Classes and Items Instances...");
        Objects.requireNonNull(config,"Config not defined. Invoke Only after setupConfig()");
        
        allowedSwarmSpawnEntitiesList.clear();// = new ArrayList<Class>();
        persistEntitiesList.clear();// = new ArrayList<Class>();
        persistItemsList.clear();// = new ArrayList();

        ConfigCategory settings = config.getCategory(CAT);
        if (settings != null) {

            String[] allowedSwarmSpawnClassNames = getAStrProp(settings, "AllowedSwarmSpawnClassNames");
            String[] persistEntitiesClassNames = getAStrProp(settings, "PersistEntityClassNames");
            String[] persistItemsIds = getAStrProp(settings, "PersistItemsIds");

            //классы существ которым разрешено спавниться роем
            bindClassNamesToClasses(LOG, "AllowedSwarmSpawn Entities", allowedSwarmSpawnClassNames, allowedSwarmSpawnEntitiesList);
            //классы существ которые не удалять при очистке
            bindClassNamesToClasses(LOG, "Persist Entities", persistEntitiesClassNames, persistEntitiesList);
            // не удаляемые очисткой предметы
            bindItemsIdsToInstances(LOG, "Persist Items", persistItemsIds, persistItemsList);

        } else {
            LOG.warn("Not found ConfigCategory: {}", CAT);
        }

        //OptiServerUtils.bindCustomNpsClass();
        //OptiServerUtils.bindLootableBodyClass();

        //подсчёт распознанных классов существ и инстансов предметов по их именам\идишникам
        resolvedItemInstances = safe(persistItemsList).size();
        resolvedEntityClasses = safe(allowedSwarmSpawnEntitiesList).size() + safe(persistEntitiesList).size();
        //if (OptiServerUtils.classCustomNpcEntity != null) resolvedEntityClasses++;
        //if (OptiServerUtils.classLootableBodyEntity != null) resolvedEntityClasses++;

        LOG.info("[<<CONFIG] Resolved Enities Classes: {}  Items Instances: {}", resolvedEntityClasses, resolvedItemInstances);
        return resolvedEntityClasses + resolvedItemInstances;
    }

    //unloaded by clearing
    public static boolean addPersitItem(Item item) {
        if (item != null && !persistItemsList.contains(item)) {
            persistItemsList.add(item);
            return true;
        }
        return false;
    }

    public static boolean removePersitItem(Item item) {
        return (item == null || persistItemsList == null) ? false : persistItemsList.remove(item);
    }
    /**
     * Добавить существо либо в лист не удаляемых, либо в лист разрешенния спавна роем
     * @param entityClass
     * @param toCanSpawnPack добавлять в лист существ которым разрешено спавниться стаей в одной точке
     * иначе в слист существ которых запрешено удалять при очистке
     * @return
     */
    public static boolean addEntityClass(Class entityClass, boolean toCanSpawnPack) {
        if (entityClass==null) return false;
        
        List<Class> list = toCanSpawnPack
                ? allowedSwarmSpawnEntitiesList
                : persistEntitiesList;
        
        if (!list.contains(entityClass)) {
            list.add(entityClass);
            return true;
        }

        return false;
    }

    public static boolean removeEntityClass(Class entityClass, boolean toAllowSwarmSpawn) {
        if (entityClass==null) return false;

        List<Class> list = toAllowSwarmSpawn
                ? allowedSwarmSpawnEntitiesList
                : persistEntitiesList;

        return (list.remove(entityClass));
    }
}
