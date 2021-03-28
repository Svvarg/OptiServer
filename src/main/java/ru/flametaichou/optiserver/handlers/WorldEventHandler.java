package ru.flametaichou.optiserver.handlers;

import java.util.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.util.OptiServerUtils;

import org.swarg.jvmutil.JvmUtil;
import org.swarg.mcforge.util.XServer;
import org.swarg.mcforge.util.XChunk;
import org.swarg.mcforge.util.XEntity;
import org.swarg.mcforge.util.WorldChunkUnloader;
import org.swarg.mcforge.statistic.CleanupEntry;
import org.swarg.mcforge.statistic.StatsContainer;
import static ru.flametaichou.optiserver.OptiServer.LOG;
import static org.swarg.common.AUtil.safe;
import static org.swarg.common.AUtil.unBox;
import static org.swarg.tracking.StackTraceWrapper.appendStackTrace;
import static org.swarg.mcforge.statistic.StatsContainer.CleanPhase.*;

 /* TODO
    страховочный пояс если сервер уходит в штопор и начинает постоянное
    запускать очистки предпренять меры ( если упал тпс не запускать очистку если
    она не так давно была) уменьшать дальность видимости - количество отправляемых игрокам чанков
    статистика данных о полях которые могут забивать память - листы существ мапы и прочее  Swarg */
public class WorldEventHandler {

    /*время следующей проверки на необходимость очистки*/
    private long сleanupCheckTime = 0;
    /*время когда должна стартовать запланированная очистка (если не 0)*/
    private long cleanupTime = 0;
    /*время вывода сообщения-репорта о прошедшей очистке*/
    private long cleanupMessageReportTime = 0;

    //время следующего добавления в статистику данных о состоянии сервера
    private long gettingServerStatTime = 0;
    private int maxTimingStatSize = 144;//частота сбора статистики - 5 минут, при 144 это 12 часов
    /* Статистика состояния сервера в разных точках связанных с очисткой и
       с планавыми замерами состояния сервера*/
    private StatsContainer serverStats;
    
    /*Запись содержащая данные о состоянии сервера на разных этапах очистки
      "живёт" от фазы инициации до фазы вывода сообщения о прошедшей очистке
      в другое время - null. Помещается в статистику в момент завершения очистки
      до вывода сообщения. Устанавливается в null после вывода сообщения об
      очистке.
      Используется для мониторинга изменения состояния сервера на всех этапах
    связанных с очисткой:
    (От инициации до стартка очистки - резальтата очистки - вывод глоб. сообщения)*/
    private CleanupEntry currentCleanupStat;


    private static WorldEventHandler INSTANSE;
    public static WorldEventHandler instance () {
        if (INSTANSE == null) {
            INSTANSE = new WorldEventHandler();
        }
        return INSTANSE;
    }

    public WorldEventHandler() {
        serverStats = new StatsContainer(LOG, 32, maxTimingStatSize);
    }

    public StatsContainer getServerStats() {
        return serverStats;
    }
    /*
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWorldTick(TickEvent.WorldTickEvent event) {
    }
    */
    /*
        FMLCommonHandler.onPreServerTick()
        bus().post(new TickEvent.ServerTickEvent(Phase.START))
        FMLCommonHandler.onPostServerTick()
        bus().post(new TickEvent.ServerTickEvent(Phase.END))   <<<

        еще есть TickEvent.WorldTickEvent(Side.SERVER, Phase.END, world));
    */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == Side.SERVER && event.phase == TickEvent.Phase.END) {

            long nowSysMillis = System.currentTimeMillis();

            //сбор статистики не относящийся к очисткам
            addStatRowAboutUsedMemAndTps(nowSysMillis);

            if (checkIsCleanupNeeded(nowSysMillis)) {
                sheduleClean();
            }

            cleaningEntityAndChunks(nowSysMillis);

            notificationMessageAfterCleaning(nowSysMillis);
        }
    }

    /**
     * Statistics: Used memory and TPS online chunks entities te
     * based on ServerStat
     */
    private void addStatRowAboutUsedMemAndTps(long nowSysMillis) {
        if (nowSysMillis >= gettingServerStatTime) {
            gettingServerStatTime = nowSysMillis + ConfigHelper.statsInterval * 60 * 1000;
            serverStats.addTimingServerStat();
        }
    }

    /**
     * Проверить текущее состояние сервера нужна ли очистка
     * -по количеству свободной памяти
     * -по минимальному тпс по всем мирам
     * -по количеству обьектов не имеющих под собой загруженные чанки(утечки)
     * -по количеству дублированных обьектов
     * в статистику пойдёт первая проходящая услове причина.
     * т.е. если мало памяти - то проверяться кол-во дублей идт обьектов-утечек
     * не будет
     * TickEvent.Phase.END! обязателен т.е. после тика сервера
     * иначе возможны казусы
     * TODO замеры сколько потребляет времени проверка на необходимость очистки
     * @param nowSysMillis System.currentTimeMillis()
     */
    private boolean checkIsCleanupNeeded(long nowSysMillis) {

        if (cleanupTime == 0 && nowSysMillis >= сleanupCheckTime) {
            //расчитать время следующей проверки на необходимость очистки.
            сleanupCheckTime = nowSysMillis + ConfigHelper.checkInterval * 60 * 1000;

            long freeMem = OptiServerUtils.getFreeMemMB(); //Max-Total  +  Free
            double minTps = OptiServerUtils.getMinTps();

            // Запускать чистку если найдено более 100 дублирующихся сущностей
            long duplicatesCount = 0;
            long leaksObjects = 0;
            long counter = 0; //может указывать как на количество дубликатов так и на количество утечек зависит от причины cause
            int[] box = new int[XServer.BOX_SIZE];//для доп значений
            final int noChunkObjectsLimit = 50;
            final int duplicatesLimit = 100;
            
            //проверка на необходимость очистки от наиболее простого к более сложному
            //в статистику пойдёт первая проходящая услове причина. (т.е. если мало памяти - то проверяться кол-во дублей не будет)
            if (freeMem < ConfigHelper.memoryLimit ||
                minTps < ConfigHelper.tpsLimit ||
                /*проверка на наличие обьектов без чанков( утечки ) по идеи их
                  вообще не должно быть т.к. данный код идёт после тика сервера т.е. после его чисток*/
                (leaksObjects = XServer.getObjectsWithOutChunks(box, noChunkObjectsLimit, null, null, false)) >= noChunkObjectsLimit ||
                 //поиск дубликатов без создания отчёта(null) только подсчёт количества повторов
                (duplicatesCount = XEntity.findEntitiesDuplicates0(null, duplicatesLimit)) >= duplicatesLimit )
                /*TODO - если большое количество существ-тайлов в очереди на
                  выгрузку и они не выгружаются долго...
                  -огромное количество существ-падающих блоков... актуально для тфк + WordlEdit*/
            {

                byte cause = 0; //unknown; force=1

                if (freeMem < ConfigHelper.memoryLimit) {
                    LOG.warn("Memory limit! Free mem: {} Mb", freeMem);
                    counter = freeMem;
                    cause = 2;//memLimit
                }
                else if (minTps < ConfigHelper.tpsLimit) {
                    LOG.warn("Low TPS! TPS: {}", minTps);
                    cause = 3;//lowTps
                }
                else if (duplicatesCount > 100) {
                    LOG.warn("Found {} duplicated entities!", duplicatesCount);
                    counter = duplicatesCount;
                    cause = 4;//duplicates
                }
                //TickEvent.Phase.END! обязателен т.е. после тика сервера
                else if (leaksObjects > 100) {
                    counter = leaksObjects;
                    int entsLeaks = unBox(box, XServer.I_W_ENTS_NO_CHUNK);
                    int tilesLeaks = unBox(box, XServer.I_W_TILES_NO_CHUNK);
                    LOG.warn("Found {} leaks Objects (Ents {} & Tiles {} without chunks)", leaksObjects, entsLeaks, tilesLeaks);
                    cause = 5;//leaks
                }

                /*Добавлаю в статистику очисток причину из-за которой было
                  принято решение запустить очистку(case) и данные о состоянии сервера */
                this.currentCleanupStat = serverStats.newClenupMeasuring(INIT_LAG, cause, minTps, counter);
                /*DEBUG*/
                if (currentCleanupStat == null) {
                    LOG.debug("### Not Created new CleanupEntry at check IsCleanupNeeded! ");
                }
                
                return true;//sheduleClean();
            }
        }
        return false;
    }

    
    public void sheduleClean() {
        if (cleanupTime == 0) {
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                    new ChatComponentTranslation(ConfigHelper.beforeClearMessage, String.valueOf(ConfigHelper.secBeforeClean))
            );
            //установка времени когда должна быть вызвана очистка
            cleanupTime = System.currentTimeMillis() + (ConfigHelper.secBeforeClean * 1000);

            //todo конфиг выключать или не выключать спавн мобов до очистки
            WorldServer[] aws = MinecraftServer.getServer().worldServers;
            if (aws != null && aws.length > 0) {
                //достаточно установки в для первого мира т.к. worldInfo - один инстанс на все миры
                aws[0].getGameRules().setOrCreateGameRule("doMobSpawning", "false");
                //не создавать новых существ до очистки, обратное включение будет после оповещения
                LOG.debug("[doMobSpawning] turned OFF before Clearing");
            }
        }
        else {
            LOG.warn("Clean is already scheduled!");
        }
    }

    /**
     * Запланирована очистка, либо сообщение отчёт о прошедшей очистке еще было
     * отображено. ( Для запрета спавна сущесвт которые всё равно будут удалены
     * очисткой)
     * @return
     */
    public boolean isCleanupSheduled() {
        return this.cleanupTime != 0 || cleanupMessageReportTime !=0;
    }

    /**
     * Прямая выгрузка всех обьектов какие только можно выгрузить
     * если их будет достаточно много - создаст "остановку мира"
     * @param nowSysMillis 
     */
    private void cleaningEntityAndChunks(long nowSysMillis) {

        if (cleanupTime != 0 && nowSysMillis >= cleanupTime) {
            cleanupTime = 0;
            if (currentCleanupStat == null) {//по задумке такого быть не должно, но как предохранение
                LOG.debug("### No CleanupEntry! Is NULL");
                currentCleanupStat = new CleanupEntry((byte)0);
            }
            //lastUsedMem = OptiServerUtils.getUsedMemMB();
            /*Замеры состояния сервера и всех миров до очистки*/
            serverStats.updateStat(currentCleanupStat, BEFORE_CLEAN);


            WorldServer[] worldServers = MinecraftServer.getServer().worldServers;
            List<Entity> entitiesToUnload = new ArrayList<Entity>();
            int leaksEntities = 0;//List<Entity> leaksEntities = new ArrayList<Entity>();
            int actualyUnloadedChunks = 0;//из числа тех которые были отмечены на выгрузку

            for (WorldServer ws : worldServers) {
                //Entities
                if ( safe(ws.loadedEntityList).size() > 0) {
                    Object[] loadedEntities = ws.loadedEntityList.toArray();

                    for (int i = 0; i < loadedEntities.length; i++) {
                        Entity e = (Entity) loadedEntities[i];
                        if (OptiServerUtils.isEntityCanBeUnloaded(e)) {
                            entitiesToUnload.add(e);
                            e.setDead();
                        }
                        /*если существо находится в мире вне загруженного чанка
                        - тоже выгружать из памяти. (Чанк выгрузили, а "существо осталось")*/
                        else if (XChunk.getHolderChunkListIndex(e) < 0 ) {
                            entitiesToUnload.add(e);
                            leaksEntities++;//.add(e);
                        }
                    }
                }
                //unloadedEntities += entitiesForUnload.size();уже подсчитано в measuring
                /*Данный метод только добавляет в очередь но не выгружает.
                  Поэтоу здесь ниже будет прямая выгружка, а не отложенная на
                  следующий тик*/
                //ws.unloadEntities(entitiesForUnload);

                //Chunks брать из измерения
                //loadedChunks += ws.theChunkProviderServer.getLoadedChunkCount();
                
                WorldChunkUnloader unloader = new WorldChunkUnloader(LOG, ws);
                /*определить какие чанки можно выгрузить и добавить в очередь на выргрузку*/
                unloader.populateChunksToUnload();
                /*прямая выгрузка из мира и чанков существ разрешенных к выгрузке*/
                unloader.unloadEntities(entitiesToUnload);

                /* выгрузка чанков из мира - это их сохранение на диск - оно
                   так же требует памяти, здесь существа были удалены из памяти
                   и перед выгрузкой чанков - можно освободить место от них
                TODO поставить условие на вызов GC если свободной памяти реально 
                мало а существ было выгружено порядком - то имеет смысл вызов GC
                */
                //System.gc();

                /*Выгружаем из памяти чанки поставленные в очередь на выгрузку
                  Так же, если это возможно, полностью выгружает из памяти мир
                  Это может вызвать StopTheWorld задержку если чанков на выгрузку очень много*/
                unloader.unloadQueuedChunks();
                //проверяю из какие чанки из тех которые были поставлены на выгрузку
                //были реально выгружены а какие остались в памяти
                actualyUnloadedChunks += unloader.getActualyUnloadedChunksCount();

                /*после выгрузки чанков обьекты entities и tiles содержащиеся в них
                  добавляются в листы на выгрузку, но не выгрудаются из листов мира
                  выгрузка происходит только в World.updateEntities()
                  если например игроков нет - вызова метода обновления существ не будет
                  и обьекты так и останутся висеть в памяти. поэтому идёт прямой
                  вызов и очистка именно здесь                */
                unloader.cleanUpObjectsQueuedToUnload();


                //для следующего мира
                entitiesToUnload.clear();

                //утечки (оставщиеся без чанков существа в текущем мире)
                if (leaksEntities > 0) {
                    LOG.debug("Found {} Entities in World[{}].loadedEntityList without exists Chunks", leaksEntities, ws.provider.dimensionId);
                    leaksEntities = 0;//По каждому миру отдельное сообщение
                }
            }
            

            // GC
            //Runtime.getRuntime().gc();
            System.gc();
            
            //в этой точке обьекты(существа и чанки) уже должны быть удалены с памяти
            serverStats.updateStat(currentCleanupStat, AFTER_CLEAN_AND_GC);//inside: serverStats.insertCleanEntry(clean);
            
            //установка времени вывода сообщения о прошедшей очистке
            cleanupMessageReportTime = System.currentTimeMillis() + 1000;

            //DEBUG
            int chunksBeforeClear = this.currentCleanupStat.bcChunks;
            int chunksAfterClear = this.currentCleanupStat.acChunks;
            LOG.debug("[CleanupResult] LoadedChunks BeforeClear: {} - AfterClear: {} = {}  ActualUnloaded: {}",
                    chunksBeforeClear, chunksAfterClear, 
                    (chunksBeforeClear - chunksAfterClear), //разница в количестве загруженных чанков до и после очистки
                    actualyUnloadedChunks//чанки реально выгруженные из направленых unloader`oм на выгрузку
            );


            //пересчитать время следующей проверки на необходимость очистки ??
            сleanupCheckTime = nowSysMillis + ConfigHelper.checkInterval * 60 * 1000;
        }
    }

    


    /**
     * Вывод сообщения подводящего итог после очистки
     * Message after cleanup
     * @param nowSysMillis
     */
    private void notificationMessageAfterCleaning(long nowSysMillis) {

        if (cleanupMessageReportTime != 0 && nowSysMillis >= cleanupMessageReportTime) {

            cleanupMessageReportTime = 0;

            if (this.currentCleanupStat == null) {
                if (LOG.isDebugOwn()) {
                    StringBuilder sb = new StringBuilder();
                    appendStackTrace(new Throwable().getStackTrace(), sb);
                    LOG.debug("### No CurrentCleanupStat instance! {}", sb);
                    this.currentCleanupStat = new CleanupEntry((byte)0);/// anti null
                }
            }

            int nowChunksLoaded = 0;
            int nowChunksLoaded2 = 0;
            int nowObjectsLoaded = 0;
            WorldServer[] worldServers = MinecraftServer.getServer().worldServers;
            for (WorldServer ws : worldServers) {
                nowChunksLoaded += ws.theChunkProviderServer.getLoadedChunkCount(); //map
                nowChunksLoaded2 += ws.theChunkProviderServer.loadedChunks.size();  //list
                nowObjectsLoaded += ws.loadedEntityList.size() + ws.loadedTileEntityList.size();
            }
            /*сколько было очищено именно самой очисткой: т.к. между моментом,
              когда было принято решение запуска очистки и самим запуском очистки
              мог пройти 900й тик сервера очищающий память */
            int clearedChunks = currentCleanupStat.bcChunks - currentCleanupStat.acChunks;
            //общее изменение количества чанков от инициации до текущего стостояни (вывод сообщения)
            int changedChunksTotal = currentCleanupStat.chunks - nowChunksLoaded;
            int clearedObjects = (currentCleanupStat.bcEntities - currentCleanupStat.acEntities) +
                    (currentCleanupStat.bcTiles - currentCleanupStat.acTiles);

            //сколько выгрузила сама очистка и сколько всего выгружено от момента инициации очистки до текущего состояния
            LOG.info("Cleanup unload {} Chunks; Total Unloaded {} chunks!", clearedChunks, changedChunksTotal);

            int usedMemNow = (int) JvmUtil.getUsedMemMb();//OptiServerUtils.getUsedMemMB();
            int usedMemOnCleanupInit = currentCleanupStat.memUsed;
            //разница в использованной памяти от момента запуска таймера на очистку до текущего значения исп. памяти
            int memCleanProfit = usedMemOnCleanupInit - usedMemNow;
            // реально было освобождено очисткой
            int memCleanProfitByOwnCleanup = currentCleanupStat.bcMemUsed - currentCleanupStat.acMemUsed;

            LOG.info("Memory clean profit = {} MB!  Exactly Cleanup Work Profit: {}",
                    memCleanProfit, memCleanProfitByOwnCleanup);

            //DEBUG подробности работы
            if (LOG.isDebugOwn()) {
                //от момента инициации до непосредственно самой очистке было очищено (сервером или другими плагинами)
                int memClearedByServer = currentCleanupStat.memUsed - currentCleanupStat.bcMemUsed;
                //изменение в количестве чанков от момента инициации очистки до ее запуска
                int chunksClearedByServer = currentCleanupStat.chunks - currentCleanupStat.bcChunks;
                int objectsClearedByServer = (currentCleanupStat.entities - currentCleanupStat.bcEntities) +
                        (currentCleanupStat.tiles - currentCleanupStat.bcTiles);
                
                /*Разница состояний между точками - момент инициации очистки(запуск таймера) и моментом перед самой очисткой
                  для возможности убидиться очищалась ли кем-то еще память сервера*/
                LOG.info("Diff Stats (CheckAndInitCleanup - BeforeCleanupStarting)  Mem:{} Chunks:{} Objects:{}", //~60sec (900th server tick)
                        memClearedByServer, 
                        chunksClearedByServer,
                        objectsClearedByServer);

                /*разница состояний сервера  Момент вывода сообщения о прошедшей очистке - сразу после завершения очистки и работы GC
                  для мониторинга что произошло сразу после очистки и до вывода сообщения(спавн новых мобов) */
                LOG.info("Diff Stats (ReportMsgNotific(Now) - immediatelyAfterCleanupComplete)  Mem:{} Chunks:{} Objects:{}", // 1 sec ~20ticks
                        usedMemNow - currentCleanupStat.acMemUsed,
                        nowChunksLoaded2 - currentCleanupStat.acChunks,
                        nowObjectsLoaded - (currentCleanupStat.acEntities + currentCleanupStat.acTiles)
                        );

                /*DEBUG Для проверок синхронизации мапы и листа содержащих чанки*/
                if (nowChunksLoaded != nowChunksLoaded2) {
                    LOG.info("DeSync loadedChunks Count in list and LongHashMap", nowChunksLoaded2, nowChunksLoaded);
                }
            }
            //long initTime = getCurrentCleanupEntryInitTime();
            

            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(
                    new ChatComponentTranslation(
                            ConfigHelper.clearMessage,
                            String.valueOf(clearedObjects),//unloadedEntities + removedDuplicatedEntities
                            String.valueOf(clearedChunks),
                            String.valueOf(memCleanProfit)
                    )
            );

            LOG.debug("Set to NULL ref to Cleanup Entry");
            /*Обнуление сылки на текущее измерение очистки сам обьект уже
              находиться в servertStat массиве*/
            this.currentCleanupStat = null;

            //обратное включение спавн существ TODO если оно было выключено перед очисткой!
            WorldServer[] aws = MinecraftServer.getServer().worldServers;
            if (aws != null && aws.length > 0) {
                //достаточно установки в для первого мира т.к. worldInfo - один инстанс на все миры
                aws[0].getGameRules().setOrCreateGameRule("doMobSpawning", "true");
                //не создавать новых существ до очистки, обратное включение будет после оповещения
                LOG.debug("[doMobSpawning] turned ON");
            }
        }
    }

    //======================================================================\\

    public CleanupEntry getCurrentCleanupStat() {
        return currentCleanupStat;
    }


    /**
     * Принудительно вызвать проверку на необходимость очистки
     */
    public void forcedCheckIsCleanupNeeded() {
        this.сleanupCheckTime = 0;
    }

    /**
     * Initiating a forced cleanup, using the command
     * @param s через сколько секунд запускать очистку (минимально 1 секунда)
     * @return
     */
    public boolean forcedCleanup(int s) {
        boolean b = false;
        if (s < 1) s = 1;
        if (cleanupTime == 0) {
            XServer.sendGlobalChatMessage("Warning! Forced Cleaning will start in %s seconds", false, s);
            cleanupTime = System.currentTimeMillis() + s * 1000;
            b = true;
            this.currentCleanupStat = serverStats.newClenupMeasuring(FORCED, 0, 0, 0);
        }
        //ускорить запланированную чтобы не ждать стандартного кулдауна
        else {
            cleanupTime = System.currentTimeMillis() + s * 1000;
            //измерение уже инициировано ранее
        }
        return b;
    }

    /*Отменить запланированную очистку */
    public boolean cancelCleanup() {
        boolean canceled = cleanupTime!=0;
        if (cleanupTime != 0) {
            cleanupTime = 0;
        }
        return canceled;
    }

    /**
     * Для принудительного добавления новых статистических данных о состоянии
     * сервера
     */
    public void forcedAddStatEntry() {
        this.gettingServerStatTime = 0;
        this.addStatRowAboutUsedMemAndTps(1);
    }


}


//    // TODO: в os largest не попадают дублирующиеся сущности. Почему?
//    @Deprecated
//    public static List<String> findDuplicates() {
//        List<String> resultList = new ArrayList<String>();
//        Map<String, Integer> entitiesMap = new HashMap<String, Integer>();
//
//        // по ws.loadedEntityList
//        /*
//        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
//            List<Entity> loadedEntities = new ArrayList<Entity>(ws.loadedEntityList);
//            Iterator iterator = loadedEntities.iterator();
//            while (iterator.hasNext()) {
//                Entity e = (Entity) iterator.next();
//                String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
//                if (entitiesMap.get(key) != null) {
//                    entitiesMap.put(key, entitiesMap.get(key) + 1);
//                } else {
//                    entitiesMap.put(key, 1);
//                }
//            }
//
//            List<TileEntity> loadedTileEntities = new ArrayList<TileEntity>(ws.loadedTileEntityList);
//            Iterator iteratorTE = loadedTileEntities.iterator();
//            while (iteratorTE.hasNext()) {
//                TileEntity te = (TileEntity) iteratorTE.next();
//                String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
//                if (entitiesMap.get(key) != null) {
//                    entitiesMap.put(key, entitiesMap.get(key) + 1);
//                } else {
//                    entitiesMap.put(key, 1);
//                }
//            }
//        }
//        */
//
//        // по чанкам
//        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
//            for (Object obj : ws.theChunkProviderServer.loadedChunks) {
//                Chunk chunk = (Chunk) obj;
//
//                for (List l : chunk.entityLists) {
//                    for (Object o : l) {
//                        Entity e = (Entity) o;
//
//                        String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
//                        if (entitiesMap.get(key) != null) {
//                            entitiesMap.put(key, entitiesMap.get(key) + 1);
//                        } else {
//                            entitiesMap.put(key, 1);
//                        }
//                    }
//                }
//
//
//                for (Object o : chunk.chunkTileEntityMap.values()) {
//                    TileEntity te = (TileEntity) o;
//
//                    String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
//                    if (entitiesMap.get(key) != null) {
//                        entitiesMap.put(key, entitiesMap.get(key) + 1);
//                    } else {
//                        entitiesMap.put(key, 1);
//                    }
//                }
//            }
//        }
//
//        for (Map.Entry e : entitiesMap.entrySet()) {
//            if ((Integer) e.getValue() > 1) {
//                resultList.add((String)e.getKey());
//            }
//        }
//
//        return resultList;
//    }




/* закомиченое из cleaningEntityAndChunks
            Logger.log(String.format("Unloaded %s entities!", unloadedEntities));

            //Remove breeding entities
            /*
            removedDuplicatedEntities = 0;
            Map<String, Integer> breedingEntitiesMap = new HashMap<String, Integer>();

            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            List<Entity> loadedEntities = ws.loadedEntityList;
            Iterator iterator = loadedEntities.iterator();

            //List<Entity> entitiesForUnload = new ArrayList<Entity>();

            while (iterator.hasNext()) {
            Entity e = (Entity) iterator.next();
            String key = e.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + e.posX + " " + e.posY + " " + e.posZ;
            if (breedingEntitiesMap.get(key) != null) {
            //entitiesForUnload.add(e);
            //iterator.remove();
            //e.setDead();
            Logger.log(String.format("Unloading duplicated entity: %s (%s)", e.getCommandSenderName(), Logger.getCoordinatesString(e)));
            OptiServerUtils.unloadEntity(e);
            removedDuplicatedEntities++;
            } else {
            breedingEntitiesMap.put(key, 1);
            }
            }

            //ws.unloadEntities(entitiesForUnload);

            List<Entity> loadedTileEntities = ws.loadedTileEntityList;
            Iterator iteratorTE = loadedTileEntities.iterator();
            while (iteratorTE.hasNext()) {
            TileEntity te = (TileEntity) iteratorTE.next();
            String key = te.getClass().getSimpleName() + " DIM" + ws.provider.dimensionId + " " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
            if (breedingEntitiesMap.get(key) != null) {
            Logger.log(String.format("Unloading duplicated entity: %s (%s)", te.getClass().getSimpleName(), Logger.getCoordinatesString(te)));
            iteratorTE.remove();
            removedDuplicatedEntities++;
            } else {
            breedingEntitiesMap.put(key, 1);
            }
            }
            }
            Logger.log(String.format("Unloaded %s breeding entities!", removedDuplicatedEntities));
            */



//
//            // Unload Chunks
//            loadedChunks = 0;
//            for (WorldServer ws : MinecraftServer.getServer().worldServers) {
//                //loadedChunks += ws.theChunkProviderServer.loadedChunks.size();
//                loadedChunks += ws.theChunkProviderServer.getLoadedChunkCount();
//
                /*
                Iterator iterator = ws.theChunkProviderServer.loadedChunks.iterator();

                while (iterator.hasNext()) {
                boolean needUnload = true;
                Chunk chunk = (Chunk) iterator.next();
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
                //chunk.onChunkUnload();
                MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
                ws.theChunkProviderServer.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
                }
                }
                */

//                WorldChunkUnloader worldChunkUnloader = new WorldChunkUnloader(ws);







