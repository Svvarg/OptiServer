package ru.flametaichou.optiserver.handlers;

import java.util.*;
import java.time.ZoneId;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Function;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.util.OptiServerUtils;


import org.swarg.cmds.CmdUtil;
import org.swarg.mcforge.util.XItem;
import org.swarg.mcforge.util.XChunk;
import org.swarg.mcforge.util.XEntity;
import org.swarg.mcforge.util.XServer;
import org.swarg.mcforge.util.command.ACommandBase;
import org.swarg.mcforge.util.WorldChunkUnloader;
import org.swarg.mcforge.statistic.StatsFormatter;
import org.swarg.mcforge.statistic.StatEntry;
import org.swarg.mcforge.common.command.ClassInteractCommand;//org.swarg.mc.rt.cmd.ResearchTool;
import org.swarg.common.ReflectionUtil;//alib
//import org.swarg.mc.rt.cmd.ResearchTool;//mc research tool
import static org.swarg.common.AUtil.*;
import static org.swarg.mcforge.util.XChunk.*;
import static org.swarg.common.Strings.DT_FORMAT;

import static ru.flametaichou.optiserver.OptiServer.LOG;
import static ru.flametaichou.optiserver.OptiServer.MODID;
import static ru.flametaichou.optiserver.OptiServer.VERSION;
import static net.minecraft.util.StringUtils.isNullOrEmpty;
import org.swarg.mcforge.util.command.ObjectDefiner;

public class OptiServerCommands extends ACommandBase {

    /*Конфиг и текущие настройки для ResearchTool*/
    private final Map<String, Object> rtoolCnfg = new HashMap<String, Object>();

    private static final String USAGE =
            "/optiserver <version/tps/mem/clear/largest/chunks/status/entity-debug/duplicates/tpsstat/memstat/gc/"+
            "config/"+//для работы с конфигом из рантайма включает в себя persists
            "stat/check-entity/"+ //3e - работа с данными о прошлых очистках
            "now/map/fire/"
            ;

    public OptiServerCommands() {
        super ( MODID, LOG, USAGE );
        this.aliases.add("optiserver");
        this.aliases.add("os");

//        //кастомизирование вывода информации о Entity во всех командах ResearchTool под специфику OptiServer
//        rtoolCnfg.put(ResearchTool.CONFIG_K_ENTITY_INFO_FORMATTER, new BiConsumer<Entity, StringBuilder>() {
//            @Override
//            public void accept(Entity e, StringBuilder out) {
//                OptiServerUtils.appendEntityInfo(e, true, out);
//            }
//        });
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }

    @Override
    public String getCommandName() {
        return "optiserver";
    }


    @Override
    public boolean processCommandEx(ICommandSender sender, String[] argString) {
        //World world = sender.getEntityWorld();
        //if (world.isRemote) return true;
        if (!isServerSide()) return true;

        if (noArgs() || isCmd("help")) {
            return toSender( getCommandUsage(sender) );
        }
        else if (isCmd("version", "-v" )) cmdVersion();

        else if (isCmd("tps"))     cmdTPS();
        else if (isCmd("mem"))     cmdMem();
        //forced - запустить cancel - остановить stat - статистика очисток (help)
        else if (isCmd("clear"))   cmdForcedClean();
        else if (isCmd("chunks"))  cmdChunks();
        else if (isCmd("largest", "l")) cmdLargest();
        else if (isCmd("status", "st")) cmdStatus();
        else if (isCmd("hoppers")) cmdHoppers();
        else if (isCmd("entity-debug","ed")) cmdEntityDebug();
        else if (isCmd("duplicates", "d"))  cmdDuplicates();
        else if (isCmd("tpsstat")) cmdTpsStat();//stat
        else if (isCmd("memstat")) cmdMemStat();//stat
        
        //просмотр данных о состояниях сервера собранные по таймингу
        else if (isCmd("stat", "sa")) cmdStat();

        /*просмотр и изменение текущей конфигурации, в том числе настройка
        списков неудаляемых вещей и предметов, и существ которым разрешено
        спавниться в одной точке стаей (config persists help)
        os config save | os config reload
        os config -list*/
        else if (isCmd("config")) cmdConfig();

        //текстовая карта загруженных чанков  todo графическая генерация png
        else if (isCmd("map")) cmdUpLoadedChunksMap();       //FIXME
        //получение времени на сервере если задать числовой аргумент преобразует его в читаемую дату
        else if (isCmd("now" )) cmdNowTime();

        //--- эксперементальные - исследовательские инструменты ---
        //может ли быть выгружено очисткой кастомНпс
        else if (isCmd("check-entity", "ce")) cmdCheckEntity();


        ///----эксперементальное для отладки
        else if (isCmd("echo")) toSender(arg(ai));//for tests
        
        else toSender("Uknown command");
        return true;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public boolean isUsernameIndex(String[] var1, int var2) {
        return false;
    }

    
    /* < ----------------------------------------------
                            --- C O M M A N D S ---
                         --------------------------------------------------> */
    
    public boolean cmdVersion() {
        return toSender(MODID +"-"+ VERSION);
    }

    public static final String C_STAT_PAGE_HELP =
            "<list / table (count-on-page) (start-index) [-objects] / add-forced>  ";//Default <12> <-1> for output latest data
    /**
     * Просмотр данных о состоянии сервера в виде страниц по 12 записей
     * Вывод страницы с замерами нагрузки на сервер
     * время-тпс-память-online,
     * TODO  page N -count x
     * отображать количество страниц если их более 1й
     * если указан ключ -objects
     * количество: chunks, entities, WorldTileEntities(Updating on ticks)
     * os stat mem
     * os stat tps
     */
    private boolean cmdStat() {
        if (isCmd("help")) {
            return toSender(C_STAT_PAGE_HELP);
        }
        else if (isCmd("tps")) {
            cmdTpsStat();
        }
        else if (isCmd("mem")) {
            cmdMemStat();
        }
        //вывод накопившейся статистики о состоянии сервера в виде таблицы
        //ключ -objects - показывать все собранные данные - число чанков - ТЕшек, сущностей
        //os stat table 
        else if (noArgs() || isCmd("table", "t")) {
            int count = (int) argL(ai++, 12); //размер страницы данных
            //начальный индекс в листе с которого начинать построение страницы данных размером count-записей
            int start = (int) argL(ai++, -1); //-1 - для вывода последней страницы(самые свежие данные);  для вывода самых старых данных - start=0)
            List timingList = WorldEventHandler.instance().getServerStats().getTimingList();
            int timingCapacity = WorldEventHandler.instance().getServerStats().getTimingCapacity();

            boolean showObjectsCount = hasOpt("-objects");
            StringBuilder out = StatsFormatter.getRawPageStat(timingList, start, count, showObjectsCount);
            if (safe(timingList).size() > count && count > 1) {
                out.append('\n').append("Total StatEntries: ").append(timingList.size()).append('/').append( timingCapacity );
            }
            toSender(out);
        }

        //вывести в обычном текстовом виде по 10 записей на страницу
        else if (isCmd("list")) {
            List timingList = WorldEventHandler.instance().getServerStats().getTimingList();
            final int page = 10;//размер страницы - кол-во записей
            int sz = safe(timingList).size();
            if (sz > 0) {
                int s = argI(ai++, -1);//start
                if (s < 0 || s > sz) s = sz - page;
                if (s < 0) s = 0;
                int e = Math.min(s + page, sz);
                StringBuilder sb = new StringBuilder();

                if (sz >= page) {
                    int allpages = sz / page;
                    int pageNum = s / page;
                                    //начальный индекс записи | индекс страницы / всего страниц
                    sb.append(" --- PAGE [s:").append(s).append('|').append(pageNum).append('/').append(allpages).append("] ---\n");
                }
                for (int i = s; i < e; i++) {
                    Object obj = timingList.get(i);
                    if (obj instanceof StatEntry) {
                        sb.append((StatEntry)obj).append('\n');
                    }
                }
                toSender(sb);
            }
        }

        /*DEBUG* принудительно добавить указанное количество записей
          StatEntry (timing) - данные состояния сервера, собираемые по таймеру*/
        else if (isCmd("add-forced")) {
            int n = (int) argL(ai++, 1);
            for (int i = 1; i < n; i++) {
                WorldEventHandler.instance().forcedAddStatEntry();
            }
            toSender("Added New StatEntries: " + n);
        }
        else {
            toSender(UNKNOWN);
        }
        return true;
    }


    private boolean cmdMemStat() {
        int count = (int) argL(1, 12);
        int start = (int) argL(2, -1);
        List timingList = WorldEventHandler.instance().getServerStats().getTimingList();
        StringBuilder out = StatsFormatter.getRawMemStat(timingList, start, count, isPlayer());
        return toSender(out);
    }

    private boolean cmdTpsStat() {
        int count = (int) argL(1, 12);
        int start = (int) argL(2, -1);
        List timingList = WorldEventHandler.instance().getServerStats().getTimingList();
        StringBuilder out = StatsFormatter.getRawTpsStat(timingList, start, count, isPlayer());
        return toSender(out);
    }

    private boolean cmdDuplicates() {
        List<String> list = new ArrayList<String>();
        list.add("DUPLICATES:");
        //List<String> duplicates = WorldEventHandler.findDuplicates();
        List<String> duplicates = new ArrayList<String>();
        int noLimit = -1;
        //поиск в листах чанка    TODO в листе мира
        XEntity.findEntitiesDuplicates0(duplicates, noLimit);
        for (String s : duplicates) {
            list.add(s);
        }
        return this.toSender(list, WRAP);
    }

    /**
     * Проверить сколько из существующих entity, во всех мирах
     * может быть удалено очисткой
     */
    private boolean cmdEntityDebug() {
        List<String> list = new ArrayList<String>();

        Map<Class, Integer> entitiesMap = new HashMap<Class, Integer>();
        List<Class> persist = new ArrayList<Class>();
        long wETotal = 0;//всего существ во всех мирах
        long wPETotal = 0; //количетсво существ которые не будет удалять очистка Persistent
        long wUETotal = 0; //будут удалины очисткой Unload
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            List<Entity> loadedEntities = ws.loadedEntityList;
            wETotal += ws.loadedEntityList.size();
            Iterator iterator = loadedEntities.iterator();
            while (iterator.hasNext()) {
                Entity e = (Entity) iterator.next();
                if (OptiServerUtils.isEntityCanBeUnloaded(e)) {
                    //String key = e.getClass().getSimpleName();
                    Class key = e.getClass();
                    if (entitiesMap.get(key) == null) {
                        entitiesMap.put(key, 1);
                    } else {
                        entitiesMap.put(key, entitiesMap.get(key) + 1);
                    }
                    wUETotal++;
                }
                else {
                    if (!persist.contains(e.getClass())) {
                        persist.add(e.getClass());
                    }
                    wPETotal++;
                }
            }
        }

        list.add("ENTITIES REMOVE DEBUG:");
        list.add("Total Entities Count in All Worlds: " +  wETotal);
        //список классов существ которые будут удалены при очистке
        for (Map.Entry<Class, Integer> e : entitiesMap.entrySet()) {
            int count = e.getValue();
            list.add(e.getKey().getSimpleName() + " " + count);
        }
        if (wUETotal > 0 || entitiesMap.size() > 0) {
            list.add(String.format("Total toClean Objects: %s Classes: %s", wUETotal, entitiesMap.size()));
        }
        list.add(__________);
        //классы существ которые не удаляет очистка
        list.add("---- UnLoadable -----");
        for (int i = 0; i < persist.size(); i++) {
            Class cl = persist.get(i);
            list.add(cl.getName());
        }
        list.add("Total Persist(Unloadable) Entities: " + wPETotal);
        return toSender(list, WRAP);
    }

    private boolean cmdHoppers() {
        List<String> list = new ArrayList<String>();

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            for (Object obj : ws.loadedTileEntityList) {
                if (obj instanceof TileEntityHopper) {
                    list.add(obj.toString());
                }
            }
        }
        return this.toSender(list, WRAP);
    }
    /*текущее состояние сервера*/
    private boolean cmdStatus() {
        
        if (isCmd("help")) {
            return toSender("[-other] [-other-class]");
        }
        /*показывать классы существ не входящие в известные группы*/
        boolean showOther = hasOpt("-other","-o");

        //добавить измерение состояния сервера
        //оборачивать будет внутри метода
        int lWorlds = 0;
        int lChunks = 0;
        int itemsOnGround = 0;
        int mobsAlive = 0;
        int friendlyAlive = 0;
        int customNpcs = 0;
        int playersAlive = 0;
        int activeMobSpawners = 0;
        int hoppers = 0;
        int activeHoppers = 0;
        int projectile = 0;

        int zaScent = 0;
        int corpse = 0;
        int fallingBlock = 0;//todo

        int otherEntity = 0;

        //Map<String, Integer> friendlyMobsMap = new HashMap<String, Integer>();
        int wTotalEntities = 0;
        int chTotalEnts = 0;
        int wTotalTiles = 0;
        int chTotalTiles = 0;

        //для возможности подсчитать количество существ заданных классов
        String calcClassName = optValue(null, "-other-class", "-oc");
        Class calcClass = null;
        int calcClassCounter = 0;
        if (!isNullOrEmpty(calcClassName)) {
            calcClass = ReflectionUtil.getClassByName(calcClassName);
        }

        List<String> list = new ArrayList<String>();//ответ Sender`у
        List<Class> showedOtherEntityClasses = null;

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            lWorlds++;
            //лист загруженых существ мира, отличается от листов существ чанка (должен синхронизироваться)

            lChunks += ws.theChunkProviderServer.loadedChunks.size();
            wTotalTiles += ws.loadedTileEntityList.size();
            wTotalEntities += ws.loadedEntityList.size();

            List loadedChunks = ws.theChunkProviderServer.loadedChunks;
            for (int i = 0; i < loadedChunks.size(); i++) {
                Object oc = loadedChunks.get(i);
                Chunk chunk = (Chunk) oc;
                chTotalEnts += getEntityCountAtChunk(chunk);
                chTotalTiles += safe(chunk.chunkTileEntityMap).size();
            }

            for (int i = 0; i < ws.loadedEntityList.size(); i++) {
                Object obj = ws.loadedEntityList.get(i);
                if (obj == null) continue;

                Class clazz = obj.getClass();

                if (obj instanceof EntityItem) {
                    itemsOnGround++;
                } else if (obj instanceof IProjectile) {
                    projectile++;
                } else if (obj instanceof EntityMob) {
                    mobsAlive++;
                } else if (obj instanceof EntityPlayer) {
                    playersAlive++;
                } else if (obj instanceof EntityLiving) {
                    if (XEntity.isCustomNpc((Entity)obj)) {
                        customNpcs++;
                    }
                    //Cyano lootable body "Corpse"
                    else if (clazz == XEntity.CL_CYANO_ENTITY_LOOTABLE_BODY) {
                        corpse++;
                    }
                    else {
                        friendlyAlive++;
                    }
                    /*
                    String key = obj.getClass().getSimpleName();
                    if (friendlyMobsMap.get(key) != null) {
                    friendlyMobsMap.put(key, friendlyMobsMap.get(key) + 1);
                    } else {
                    friendlyMobsMap.put(key, 1);
                    }
                    */
                }//entityliving

                else if (obj instanceof EntityFallingBlock
                        || clazz == XEntity.CL_TFC_ENTITY_FALLING_BLOCK) {
                    fallingBlock++;
                }
                
               //ZombieAwareness Scent  "невидимки"
                else if (clazz == XEntity.CL_ZA_ENTITY_SCENT) {
                    zaScent++;
                }

                else {
                    otherEntity++;
                    if (showOther) {
                        if (otherEntity == 1) {
                            list.add("--- OTHER ENITIES ---");
                            showedOtherEntityClasses = new ArrayList<Class>();
                        }
                        Class entityClass = obj.getClass();
                        if (showedOtherEntityClasses == null || !showedOtherEntityClasses.contains(entityClass)) {
                            list.add(entityClass.getName());
                            showedOtherEntityClasses.add(entityClass);
                        }
                    }
                }

                if (calcClass != null && obj!=null && obj.getClass() == calcClass) {
                    calcClassCounter++;
                }
            }

            for (int k = 0; k < ws.loadedTileEntityList.size(); k++) {
                Object obj = ws.loadedTileEntityList.get(k);
                if (obj instanceof TileEntityMobSpawner) {
                    activeMobSpawners++;
                }
                else if (obj instanceof TileEntityHopper) {
                    hoppers++;
                    TileEntityHopper hopper = (TileEntityHopper) obj;
                    int itemsCount = 0;
                    for (int i = 0; i < hopper.getSizeInventory(); i++) {
                        if (hopper.getStackInSlot(i) != null) {
                            itemsCount++;
                            //если хотябы 1 предмет есть - считается активной
                            break;
                        }
                    }
                    if (itemsCount > 0) {
                        activeHoppers++;
                    }
                }
            }
        }

        if (showOther && otherEntity > 0) {
            list.add(__________);
        }

        list.add("Sender World DIM: " + this.world().provider.dimensionId);//+ " / Worlds: "+worlds
        //list.add("NextEntityID: " + );
        list.add(__________);
        list.add("Chunks loaded: " + lChunks);
        list.add("Items on the ground: " + itemsOnGround);
        //TODO FailingBlocks TFC
        list.add("Mobs alive: " + mobsAlive);
        list.add("Friendly-mobs alive: " + friendlyAlive);
        list.add("Custom NPCs alive: " + customNpcs);
        list.add("Projectile: " + projectile);
        list.add("FallingBlock: " + fallingBlock);

        if (XEntity.IS_ZOMBIE_AWARENESS_LOADED) {
            list.add("ZA Scent: " + zaScent);
        }
        if (XEntity.IS_CYANO_LOOTABLE_BODY_LOADED) {
            list.add("Corpse: " + corpse);
        }
        list.add("OtherEntity: " + otherEntity);

        list.add("Players alive: " + playersAlive);
        list.add("Active hoppers: " + activeHoppers);
        list.add("Idle hoppers: " + (hoppers - activeHoppers));
        list.add("Active mob spawners: " + activeMobSpawners);
        int wTotalEnts = playersAlive + itemsOnGround + mobsAlive + friendlyAlive + customNpcs + projectile + zaScent + corpse + fallingBlock + otherEntity;

        if (wTotalEnts != wTotalEntities) {//проверка
            list.add(__________);
            //сумма количества разных типов подсчитанных существ не сходиться с суммой общего количесва существ в листе мира
            list.add("[#WURNING#] The sum of different types of Entities in all worlds does not equals the sum of loadedEntityList.size " + wTotalEnts + "/" + wTotalEntities);
        }

        list.add(__________);
        list.add("Place     Count  Entities     Tiles");
        list.add(String.format("inWorlds: % ,5d  % ,8d  % ,8d", lWorlds, wTotalEnts, wTotalTiles));
        list.add(String.format("inChunks: % ,5d  % ,8d  % ,8d", lChunks, chTotalEnts, chTotalTiles));
        int diffEnts = wTotalEnts - chTotalEnts;
        //int diffTiles = wTotalTiles - chTotalTiles;оно полюбому не сойдётся т.к. в листе мира обычно только тикающие те.
        if (diffEnts != 0) {
            //показывать только если сумма не сошлась
            list.add(String.format("### Diff:        % ,8d", diffEnts));
        }

        /*количество сущест заданного в мире (если был задан поиск по имени класса*/
        if (calcClass != null) {
            list.add(__________);
            list.add(calcClass.getName() +": "+calcClassCounter);
        }

        //todo acessors track...
        /*
        sender.addChatMessage(new ChatComponentTranslation("FRIENDLY DEBUG:"));
        for (Map.Entry e : friendlyMobsMap.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        */
        return this.toSender(list, WRAP);
    }


    private boolean cmdMem() {
        List<String> list = new ArrayList<String>();
        list.add("------ MEMORY ------");
        list.add("Max:   " + OptiServerUtils.getMaxMemoryMB() + " MB");
        list.add("Total: " + OptiServerUtils.getTotalMemoryMB() + " MB");
        list.add("Used:  " + OptiServerUtils.getUsedMemMB() + " MB");
        list.add("Free:  " + OptiServerUtils.getFreeMemMB() + " MB");//Max-Total + free
        return this.toSender(list, WRAP);
    }

    
    public static final String C_CLEAR_HELP = "os clear <status/check/forced/cancel/stat/chunk-unloader>";
    /**
     * Принудительный вызов очистки либо если она уже запланирована её ускорение
     * 1 аргумент сколько до очистки секунд кд
     */
    private boolean cmdForcedClean() {
        String response = UNKNOWN;
        if (isHelpCmdOrNoArgs()) {
            response = C_CLEAR_HELP;
        }

        else if (isCmd("status", "st")) {
            StringBuilder sb = new StringBuilder();
            boolean sheduledCleanup = WorldEventHandler.instance().isCleanupSheduled();
            sb.append("Sheduled Cleanup: ").append(sheduledCleanup).append('\n');
            if (sheduledCleanup) {
                //через сколько будет
            }
            long cleanups = WorldEventHandler.instance().getServerStats().getCleanupCounter();
            sb.append("\nCleanupCounter: ").append(cleanups); //сколько было очисток с начала старта сервера
            sb.append("\nLastCleanupTime: ").append(cleanups); //сколько было очисток с начала старта сервера

            //скольким существам было запрещено спавниться/входить в мир (дубли / мусор)
            EntitySpawnHandler esh = EntitySpawnHandler.instance();
            sb.append("\nDenyPotentialSpawnsDuplicates: ").append(esh.denyPotentialSpawnsDuplicates);
            sb.append("\nDenySpawnDuplicates: ").append(esh.denySpawnDuplicates);
            sb.append("\nDenyJoinDuplicates:  ").append(esh.denyJoinDuplicates);
            //спавн которых отменён в ожидании очистки
            sb.append("\nDenySpawnGarbageMobs: ").append(esh.denySpawnGarbageMobs);
            //пока только счёт их никак не удаляет
            sb.append("\nDuplicatesChunkEntering: ").append(esh.chunkEnteringDuplicates);
            response = sb.toString();
        }

        /*инициировать проверку на необходимость очистки (сбросам таймера)*/
        else if (isCmd("check")) {
            WorldEventHandler.instance().forcedCheckIsCleanupNeeded();
            response = "Starting CheckIsCleanupNeeded";
        }
        //принудительный вызов очистки
        else if (isCmd("forced", "go")) {
            int waitSecondsBeforClean = argI(ai, 10); //по умолчанию ставлю 10 секунд перед самой очисткой
            WorldEventHandler.instance().forcedCleanup(waitSecondsBeforClean);
            response = "Starting Forced Clearing";
        }
        //отменить запланированную очистку
        else if (isCmd("cancel", "stop")) {
            boolean canceled = WorldEventHandler.instance().cancelCleanup();
            if (canceled) {
                return XServer.sendGlobalChatMessage("Cleanup Canceled", true);
                //здесь будет отосланно глобальное сообщение поэтому ответа лично отправителю нет
            } else {
                response = "Clearing was not planned";
            }
        }
        //просмотр статистики очисток
        else if (isCmd("stat", "sa")) {
            return StatsFormatter.cmdCleanupStat(this, WorldEventHandler.instance().getServerStats());
        } 
        //WorldChunkUnloader
        else if (isCmd("chunk-unloader")) {
            cmdChunkUnloader();
            return true;
        }
        
        return toSender(response);
    }

    /**
     * Тяжесть чанков дефолтно по количеству существ
     * так же можно задать по количеству тешек
     * TODO другие определители тяжести через Function если понадобятся
     * @return
     */
    private boolean cmdLargest() {
        
        //возможность задать сколько чанков отображать в топе по умолчанию 5
        int top = (int) argL(ai, 5);
        //как будем определять тяжесть чанка
        String funcType = (optValue("entities", "-func", "-f"));
        String DisplType = "Entities";
        Function<Chunk, Integer> func = XChunk.CHUNK_FUNC_ENTITY_COUNT;
        if (!isNullOrEmpty(funcType)) {
            if (CmdUtil.equal(funcType, "entities", "e")) {
                func = XChunk.CHUNK_FUNC_ENTITY_COUNT;
                DisplType = "Entities";
            }
            else if (CmdUtil.equal(funcType, "tileentities", "te")) {
                func = XChunk.CHUNK_FUNC_TE_COUNT;
                DisplType = "TileEntities"; //размер слова скоратил для пропорциональности при выводе
            }
        }
        List<String> list = new ArrayList<String>();
        //определить количество чего выведено можно по шапке DisplType
        list.add("-- mostHeavyChunks by " + DisplType + " Count --"); //Entities
        list.add("Count      [DIM]  ChunkXZ (blockXZCoords)");

        List<SimpleEntry<Integer, Chunk>> mostHeavyChunks = XChunk.getTopHeavyChunksBy(func);
        //сколько строк отображать
        int lines = top < 1 ? mostHeavyChunks.size() : Math.min(mostHeavyChunks.size(), top);
        for (int i = 0; i < lines; i++) {
           SimpleEntry<Integer, Chunk> e = mostHeavyChunks.get(i);
           Chunk chunk = (Chunk) e.getValue();
           int entitiesCount = e.getKey();
           int dimensionId = chunk.worldObj.provider.dimensionId;
           String line = String.format("% 8d  [% 3d]  % 3d % 3d  (%s %s)",
                   entitiesCount, dimensionId, chunk.xPosition, chunk.zPosition, chunk.xPosition * 16, chunk.zPosition * 16);
           list.add(line);
        }
        return this.toSender(list, WRAP);
    }


    private boolean cmdChunks() {
        int overallChunksCount = 0;
        List<String> list = new ArrayList<String>();
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            int chunksCount = ws.theChunkProviderServer.loadedChunks.size();
            overallChunksCount += chunksCount;
            list.add("World: DIM" + dimensionId + " Chunks: " + chunksCount);
        }
        list.add("Overall chunks: " + overallChunksCount);
        return toSender(list, WRAP);
    }

    private boolean cmdTPS() {
        double averageTps = 0;
        List<String> list = new ArrayList<String>();
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            double worldTickTime =  OptiServerUtils.mean(MinecraftServer.getServer().worldTickTimes.get(dimensionId)) * 1.0E-6D;
            double worldTPS = Math.min(1000.0 / worldTickTime, 20);

            list.add("World: DIM" + dimensionId + " TPS: " + worldTPS);
            averageTps += worldTPS;
        }
        averageTps = averageTps / MinecraftServer.getServer().worldServers.length;
        list.add("Total TPS: " + averageTps);
        return toSender(list, WRAP);
    }

    public static final String C_CONFIG_HELP = "config <help/save/reload/-list/persists>";
    /**
     * Просмотр настройка и сохранение конфига в рантайме через консоль
     * @return
     */
    private boolean cmdConfig() {
        if (noArgs() || isCmd("help")) {
            toSender(C_CONFIG_HELP);
        }
        //os config save
        else if (isCmd("save")) {
            toSender("Saved: " + ConfigHelper.saveCurrentState());
        }
        //os config reload
        else if (isCmd("reload") ) {
            ConfigHelper.reload();
            toSender("Configuraton Reloaded");
        }

        //работа со списками persists|canSpawnPack entitities, unloaded items
        //[add|remove] <unload-item|persist-ent|spawn-pack-ent> <ItemId|EntityClassName
        else if (isCmd("persists", "p")) {
            cmdConfigPersistsEntitiesAndItems();
        }

        // interact "\"
        else {
            StringBuilder out = new StringBuilder();
            ClassInteractCommand.cmdClassFieldInteract(this, ConfigHelper.class, /*static*/null,/*onlyPublic:*/true, /*Can edit*/true, out, rtoolCnfg);
            //ResearchTool.instance().cmdClassFieldInteract(this, ConfigHelper.class, /*static*/null,/*onlyPublic:*/true, /*Can edit*/true, out, rtoolCnfg);
            toSender(out, WRAP);
        }
        return true;
    }

    public static final String C_CONFIG_PERSISTS_HELP =
            //"<list|add|remove> [Item-id|held-item|EntityClassName] [-can-spawn-pack]";
            "<list|add|remove> <item|entity|can-spawn-pack> <ItemId|EntityClassName>";
    /**
     * Конфигурирование OptiServer в рантайме
     * Взаимодействие со списками существ и вещей которые нельзя удалять при очистке (persists*)
     * и списками существ,спавн которых не блокируется при EntityJoinWorldEvent (ignoreSpawnCheck)
     */
    private boolean cmdConfigPersistsEntitiesAndItems() {
        if (noArgs() || isCmd("help")) {
            return toSender(C_CONFIG_PERSISTS_HELP);
        }

        final String __CAN_SPAWN_PACK   = "- CanSpawnPack Ents -";
        final String __PERSISTANTS_ENTS = "- Persists Entities -";
        final String __PERSISTANTS_ITEMS= "-- Persists Items ---";

        List<String> response;
        if (isCmd("list", "ls")) {
            response = new ArrayList<String>();
            response.add("---- Entities -----");
            //классы существ которые не проверяются на дублирование при спавне
            //им разрешено спавниться стаей в одной точке
            response.add(__CAN_SPAWN_PACK  );
            final int sz = ConfigHelper.allowedSwarmSpawnEntitiesList.size();
            for (int i = 0; i < sz; i++) {
                response.add(ConfigHelper.allowedSwarmSpawnEntitiesList.get(i).getName());
            }

            //существа не удаляемые при очистке
            response.add(__PERSISTANTS_ENTS  );
            final int sz2 = ConfigHelper.persistEntitiesList.size();
            for (int i = 0; i < sz2; i++) {
                response.add(ConfigHelper.persistEntitiesList.get(i).getName());
            }

            //items все вещи которые не удаляются при очистке
            response.add(__PERSISTANTS_ITEMS  );
            List listPI = ConfigHelper.persistItemsList;
            if (safe(listPI).size() > 0) {
                for (int i = 0; i < listPI.size(); i++) {
                    Item item = (Item) listPI.get(i);
                    response.add( XItem.getItemInfo(item) );
                }
            }
            toSender(response, WRAP);
        }
        // [add|remove]   item|entity|spawn-pack-entity   id|className
        else if(isCmd(ai, "add", "remove")) {
            response = new ArrayList<String>();

            boolean add = isCmd("add");
            boolean remove = isCmd("remove");
            //unload-item|persist-ent|spawn-pack-ent
            if (!add && !remove) {
                toSender(C_CONFIG_PERSISTS_HELP);
            }
            //добавить предмет не удаляемый очисткой
            else if (isCmd("item", "i")) {
                boolean itemFromHand = isPlayer() && isCmd("from-hand", "held-item");
                response.add(__PERSISTANTS_ITEMS);
                Item item = null;
                int id;
                //из руки оператора
                if (itemFromHand) {
                    ItemStack hIS = ((EntityPlayer)sender).getHeldItem();
                    if (hIS != null) {
                        item = hIS.getItem();
                        if (item != null) {
                            id = Item.getIdFromItem(item);
                            toSender("Taked Item from Sender hand Id: "+ id+ " "+item.getUnlocalizedName());
                        }
                    }
                } 
                //по идишнику
                else {
                    id = argI(ai, -1);
                    if (id > 0) {
                        item = Item.getItemById(id);
                    }
                }
                //
                if (item == null) {
                    response.add(NOT_FOUND);
                } else {
                    if (add) {
                        response.add(ConfigHelper.addPersitItem(item) ? "Added": "Already Contained");
                    }
                    else if (remove) {
                        response.add(ConfigHelper.removePersitItem(item) ? "Removed ": NOT_FOUND );
                    }
                    response.add( XItem.getItemInfo(item) );
                }
            }

            //Entity по полному имени класса неудаляемые или которым разрешено спавниться роем
            else {
                /*добавлять по имени класса в лист в allowedSwarmSpawnEntitiesList a не в persistEntitiesList*/
                //boolean forAllowSwarmSpawn = hasOpt("-allow-swarm-spawn");
                boolean toCanSpawnPack = isCmd("can-spawn-pack", "csp");
                isCmd("entity", "e"); //ai++ если оно будет указано сьест аргумент чтобы имя класса было взято верно

                Class cl = argClass(ai++);//Class cl = getClassByName(eClassName, "RunTimeConfiguring", LOG.logger(), Level.DEBUG);
                if (cl != null) {
                    response.add( toCanSpawnPack ? __CAN_SPAWN_PACK : __PERSISTANTS_ENTS);

                    if (add) {
                        response.add( ConfigHelper.addEntityClass(cl, toCanSpawnPack) ? "Added" : "Already Contained");
                    }
                    else if (remove) {
                        response.add( ConfigHelper.removeEntityClass(cl, toCanSpawnPack) ? "Removed" : NOT_FOUND );
                    }
                    response.add("Entity Class:");
                    response.add(cl.getSimpleName());
                } else {
                    response.add("Entity Class Not Found:");
                    response.add(arg(ai-1));
                }
            }
            toSender(response, WRAP);
        }
        else {
            toSender(UNKNOWN);
        }
        
        return true;
    }


    
    private static final String C_UNLOADCHUNKS_HELP = "go <dim>";
    //os clear chunk-unloader
    public void cmdChunkUnloader() {
        if (noArgs() || isCmd("help")) {
            toSender(C_UNLOADCHUNKS_HELP);
        }
        else if (isCmd("go")) {
            int dim = argI(ai, 0);
            WorldServer ws = XServer.getWorldServer(dim);
            if (ws != null) {
                WorldChunkUnloader wcu = new WorldChunkUnloader(LOG, ws);
                int beforeUnloadChunkCount = wcu.getLoadedChunkCount();
                wcu.populateChunksToUnload();
                int coordsToUnloadCount = wcu.getCoordsOfChunksToUnloadSize();
                int unloadQueuedChunks = XServer.getQueuedToUnloadChunksCount(ws);
                
                wcu.unloadQueuedChunks();

                int afterUnloadChunkCount = wcu.getLoadedChunkCount();
                StringBuilder sb = new StringBuilder();
                //можно проверить все ли помеченые на выгрузку выгрузились! выгрузился ли весь мир
                sb.append("WorldChunksUnloader: {")
                        .append(" CoordsToUnloadCount: ").append(coordsToUnloadCount)
                        .append(" UnloadQueuedChunks: ").append(unloadQueuedChunks) //количество сколько реально было добавлено в очередь из определенных в унлоадере
                        .append(" Chunks beforeUnload: ").append(beforeUnloadChunkCount)
                        .append(" afterUnload: ").append(afterUnloadChunkCount)
                        .append(" was unloaded: ").append(beforeUnloadChunkCount - afterUnloadChunkCount)
                        .append('\n');
                //проверка и вывод списка чанков которые остались в памяти хотя были помечены для выгрузки
                sb.append("Not unloaded Chunks (CoordsToUnload) after cleaning:");
                wcu.checkExistsChunkMarkedForUnloading(sb);
                sb.append('}');

                toSender(sb);
            }
            else {
                toSender(NOT_FOUND + " World for dimension: "+ arg(ai));
            }
        } else {
            toSender(UNKNOWN);
        }
    }


    //текущее или заданное время
    /**
     * Вывод текущего серверного времени в форматированном виде и (millis),
     * если задан целочисленный аргумент - получить для него дату и время
     * то
     * @return
     */
    private boolean cmdNowTime() {
        long millis = argL(ai++, System.currentTimeMillis());
        Instant instant = Instant.ofEpochMilli(millis);
        // кастомное | стандарт | миллис
        return toSender( instant.atZone(ZoneId.systemDefault()).format(DT_FORMAT) + " | " + instant + " | " + millis );
    }

    public static final String C_MAP_HELP =
            "map <x> <z> -block | map <chunk_x> <chunk_z> | map -here | map spawn [-dim N] [-show-chunks-can-unloaded|-u] [-no-mobs]";
    /**
     * Простая текстовая карта загруженых чанков
     * @return
     * os map -6 15 -show-chunks-can-unloaded
     */
    public boolean cmdUpLoadedChunksMap() {
        WorldServer ws = (WorldServer) world();//sender.getEntityWorld();
        if (ws == null) {
            toSender("World Not Found");
        }

        int pointX = 0, pointZ = 0; //вводимая координата из аргрументов - центр карты
        
        // если указываешь координаты указывай до опциональных ключей
        //os map x z -opt -opt2 ...
        if (noArgs() || isCmd("help")) {
            return toSender(C_MAP_HELP);
        }

        // os map x z
        else if (argsCount() > 2 && isDoubleArg(ai) && isDoubleArg(ai+1) ) {//isIntNum
            //флаг того что указаны координаты блока а не чанка - их нужно конвертировать
            boolean needConvert = hasOpt("-block");
            pointX = argChunkCoord(ai++, needConvert);
            pointZ = argChunkCoord(ai++, needConvert);
            //hasPoint = true;
        }
        //os map -here
        else if (isCmd("-here")) {
            if (isPlayer()) {
                pointX = (int) MathHelper.floor_double( player().posX / 16.0D);
                pointZ = (int) MathHelper.floor_double( player().posZ / 16.0D);//player().posZ >> 4;
            } else {
                //для запросов из консоли центр карты всегда 0 0
            }
        }
        //центр карты - координаты спавна
        else if (isCmd("spawn")) {
            ChunkCoordinates s = ws.getSpawnPoint();
            if (s != null) {
                pointX = (int) MathHelper.floor_double( s.posX / 16.0D);
                pointZ = (int) MathHelper.floor_double( s.posZ / 16.0D);
            } else {
                return toSender(NOT_FOUND + " SpawnPoint");
            }
        }

        else {
            return toSender(C_MAP_HELP);
        }

        //указать измерение для которого создавать карту
        int dim = (int) optValueLongOrDef(0, "-dim");
        if (dim != 0) {
            ws = XServer.getWorldServer(dim);
            if (ws == null) {
                return toSender("Not Found world with dim" + dim);
            }
        }

        /*НЕ показывать в загруженных чанках (#) код количества мобов*/
        boolean noMobCount = hasOpt("-no-mobs");
        //показать чанки которые могут быть выгружены очисткой
        boolean showCanBeUnloaded = hasOpt("-show-chunks-can-unloaded", "-u");
        boolean addTimeStamp = hasOpt("-timestamp");

        long[] coordsOfChunksToUnload = null;
        if (showCanBeUnloaded) {
            long t0 = System.nanoTime();
            //os map -6 5 -show-chunks-can-unloaded -h 40
            coordsOfChunksToUnload = new WorldChunkUnloader(LOG, ws).determineCoordsOfChunksToUnload();
            long t1 = System.nanoTime();
            long n = t1-t0;
            long m = n /1000000;
            toSender("Determining Chunks for Unloading took " + n +"nanos  "+ (m) +"ms ");
        }
        
        //размеры карты
        int w = 64;//ширина
        int h = isPlayer() ? 12 : 24;//высота
        
        // можно задать через опциональный ключ 'os map x z -w 100 -h 50'
        w = (int) optValueLongOrDef(w, "-width", "-w");
        h = (int) optValueLongOrDef(h, "-heigth", "-h");
        if (isPlayer()) {
            toSender(EnumChatFormatting.YELLOW + "  ============ MAP ============");
        }
        StringBuilder map = XChunk.getLoadedChunksMap(ws, pointX, pointZ, w, h, noMobCount, coordsOfChunksToUnload);
        if (addTimeStamp) {
            map.append("\nTimeStamp:").append(System.currentTimeMillis()).append('\n');
        }
        return toSender( map );
    }




    /**
     * Проверяем существ в заданном чанке
     * По координатам чанка
     * - (поиск с одинаковыми идишниками) на данный момент только выводит лист кратких свойст Entity в данном чанке
     * - общее количество тайл-энтити в чанке
     * - по дефолту выдаёт для указанных координат только уже загруженные в память чанки
     *   для принудительной загрузки используй -provide
     */
    public boolean cmdChunkInfo() {
        int argsCount = argsCount();
        boolean here = hasOpt("-here");// && isPlayer();
        if (!here && argsCount < 2 ) {
            //помощь о том, как можно указать какой чанк нужен для исследования
            return toSender(ObjectDefiner.C_DEFINE_CHUNK);
        }
        else {
            int chX = 0, chZ = 0;

            Object[] box = newBox(3);//для передачи Action
            Chunk chunk = ObjectDefiner.cmdDefineChunk(this, box);
            String action = (String) unBox(box, 0, String.class);
            chX = (Integer) unBoxNum(box, 1, Integer.class);
            chZ = (Integer) unBoxNum(box, 2, Integer.class);

            if (chunk == null) {
                toSender("Chunk Not " + action + " "+chX+":"+chZ);
            }
            else if (chunk.entityLists == null) {
                toSender("Chunk has null entityLists[]! "+chX+":"+chZ);
            }
            else {
                List[] aList = chunk.entityLists;
                StringBuilder out = new StringBuilder();

                out.append("--- Chunk ").append(chX).append(' ').append(chZ).append(" [").append(action).append("] ---\n");

                boolean onlyCount = hasOpt("-only-count");
                boolean empty = true;
                for (int i = 0; i < aList.length; i++) {
                    List list = aList[i];
                    if (list != null && !list.isEmpty()) {
                        //индекс листа в чанке + расшифровка для каких координат он работает  16x16x16
                        out.append("- Chunk list #").append(i).append("   ").append(i*16).append(" > Y > ").append(i*16+15);
                        out.append("  Entities: ").append(list.size()).append(" -\n");

                        if (!onlyCount) {
                            //выводить полное имя класса существа
                            boolean shortClassName = hasOpt("-short-name");

                            for (int j = 0; j < list.size(); j++) {
                                Object el = list.get(j);
                                if (el != null && el instanceof Entity) {
                                    Entity e = (Entity) el;
                                    //индекс существа в листе чанка и инфа о существе
                                    out.append(j).append(' '); //индекс в листе
                                    OptiServerUtils.appendEntityInfo(e, !shortClassName, out);
                                    empty = false;
                                }
                                out.append('\n');
                            }
                        }
                    }
                }
                if (empty) {
                    out.append("EntitiesList: empty");
                }
                out.append("TileEntity Count: ").append(chunk.chunkTileEntityMap.size());
                //todo лист тайлов подробный
                return this.toSender(out, WRAP);
            }
        }
        return true;
    }



    /**
     * Для проверки алгоритма очистки памяти от существ-обьектов
     * Специфика работы OptiServer
     * @return
     */ //os check-entity
    private boolean cmdCheckEntity() {
        String response = UNKNOWN;
        if (isHelpCmdOrNoArgs()) {
            response = "[DefineEntity] <info/make-custom-npc-despawnable/ uload-log/unload-report>";
        }

        else
        {
            //стандартный метод указания Entity - координаты, идишник и проч.
            //Entity entity = ResearchTool.instance().cmdDefineEntity(this);
            Entity entity = ObjectDefiner.cmdDefineEntity(this);

            if (entity == null) {
                response = "Not Found";
            }


            //from EntitySpawnHandler experimental
            else if (isCmd("unload-entity", "ue")) {
                OptiServerUtils.unloadEntity(entity);
                response = "OptiServer Unload Entity: "+ entity;
            }
            //os obj entity id N custom-pnc spawn-cycle = 3
            else if (isCmd("make-custom-npc-despawnable", "mcnd")) {
                if (XEntity.isCustomNpc(entity)) {
                    boolean b = XEntity.makeCustomNpcDespawnable(entity);
                    response = "[Despawnable]: "+ b +" " + entity;
                } else {
                    response = "it not CustomNpc";
                }
            }
            //проверка будет ли удалено данное существо при очистке (с текущими настройками)
            else { //if (isCmd("info")) {

                boolean isPersists = safe(ConfigHelper.persistEntitiesList).size() > 0 && ConfigHelper.persistEntitiesList.contains(entity.getClass());
                boolean isCustomNpc = XEntity.isCustomNpc(entity);
                boolean isNoDespawnRequired = entity instanceof EntityLiving && ((EntityLiving)entity).isNoDespawnRequired();

                response =  entity.getClass().getName() +
                          " Entity: isCanBeUnloaded(OS):" + OptiServerUtils.isEntityCanBeUnloaded(entity) +
                          " isPersists(OS):" + isPersists +
                          " isAllowSwarmSpawn(OS):" + OptiServerUtils.isAllowedSwarmSpawn(entity) +
                          " isCustomNpc:" + isCustomNpc +
                          " Name: '" + entity.getCommandSenderName() +"'"+ // это правильно отобразит имя и для CustomNpc (XEntity.getCustomNpcName(entity))
                          " isNoDespawnRequired(EntityLiving):"+ isNoDespawnRequired;
            }
        }
        return toSender(response);
    }




    //      -- Deprecated --
    @Deprecated
    private String getPercentMessage(Map<Date, Double> map, int percent, int count, double maxValue) {
        int counter = 0;
        int missCounter = 0;

        String message = "|";
        for (Date date : map.keySet()) {
            if (map.keySet().size() > count) {
                if (missCounter < map.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }

            double value = map.get(date);

            double valuePercent =  100F / maxValue * value;


            EnumChatFormatting color;
            if (valuePercent >= 75) {
                color = EnumChatFormatting.GREEN;
            } else if (valuePercent >= 50) {
                color = EnumChatFormatting.YELLOW;
            } else if (valuePercent >= 25) {
                color = EnumChatFormatting.RED;
            } else {
                color = EnumChatFormatting.DARK_RED;
            }

            if (valuePercent >= percent) {
                message += color + "##";
            } else if (valuePercent >= (percent - 10)) {
                message += color + "#" + EnumChatFormatting.DARK_GRAY + "#";
            } else {
                message += EnumChatFormatting.DARK_GRAY + "##";
            }

            message += EnumChatFormatting.WHITE;
            message += "|";

            counter++;
            if (counter >= count) {
                break;
            }
        }
        return message;
    }
    @Deprecated
    private String getLineString(Map<Date, Double> map, int count) {
        int counter = 0;
        int missCounter = 0;

        String message = "|";
        for (Object key : map.keySet()) {
            if (map.keySet().size() > count) {
                if (missCounter < map.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }
            message += "--|";

            counter++;
            if (counter >= count) {
                break;
            }
        }
        return message;
    }
    @Deprecated
    private String getMinutesMessage(Map<Date, Double> map, int count) {
        int counter = 0;
        int missCounter = 0;

        String message = "|";
        for (Date key : map.keySet()) {
            if (map.keySet().size() > count) {
                if (missCounter < map.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }
            int minutes = key.getMinutes();
            message += (OptiServerUtils.getTwoSymbolsNumber(minutes) + "|");

            counter++;
            if (counter >= count) {
                break;
            }
        }
        return message;
    }
    @Deprecated
    private String getHoursMessage(Map<Date, Double> map, int count) {
        int counter = 0;
        int missCounter = 0;

        String message = "|";
        for (Date key : map.keySet()) {
            if (map.keySet().size() > count) {
                if (missCounter < map.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }
            int hours = key.getHours();
            message += (OptiServerUtils.getTwoSymbolsNumber(hours) + "|");

            counter++;
            if (counter >= count) {
                break;
            }
        }
        return message;
    }
}
