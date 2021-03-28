package ru.flametaichou.optiserver.handlers;

import java.util.List;
import java.util.ArrayList;
import org.apache.logging.log4j.Level;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.swarg.mcforge.util.XEntity;
import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.util.OptiServerUtils;
import static ru.flametaichou.optiserver.OptiServer.LOG;

/**
 * Хуки на события:
 * перед созданием инстанса существа и спавном,
 * перед добавлением уже созданного инстанса существа (спавн)
 * "входа" Entity в мир, чанк.
 * Назначение - предотвращать дублирование существ на одинаковых координатах
 * Дубли - существа одного класса с раными координатами с точностью до 0.001
 * Здесь реализована 5 контурная проверка на дублирование существ и запрет на
 * спавн подобных случаев.
 *
 * TODO Идеи.
 * -есть событие на размер спавнящейся стаи если высокая нагрузка на
 * сервер можно уменьшать размер стаи  MaxCanSpawnHook
 * -в событии потенциальный спавн в листе вариантов на спавн SpawnListEntry
 * есть размер групп maxGroupCount возможно тоже можно через него при высокой
 * нагрузке на сервер как-то влиять на количество существ. (проверить)
 *
 * -"борьба за место под солнцем" для большого скопления животных в одном месте
 * ("естественное" самоочищение для перегруженных домашними животными чанках)
 * включать агресию сильных на слабых
 *
 * Во время когда запланирована и ожидается очистка ( промежуток времени между
 * двумя глобальными оповещениями в чат) отключать спавн существ через правило
 * doMobSpawning. (Это реализовано в WorldEventHandler)
 * @author Swarg
 */
public class EntitySpawnHandler {

    private static EntitySpawnHandler INSTANSE;
    public static EntitySpawnHandler instance () {
        if (INSTANSE == null) {
            INSTANSE = new EntitySpawnHandler();
        }
        return INSTANSE;
    }
    
    /*/DEBUG/ счётчики отмененных дублирующихся существ */
    public long denyPotentialSpawnsDuplicates;//количество отмененных спавнов сщуеств потенциальных дубликатов (до создания инстанса существа) SpawnAnimals.findChunkForSpawn(): w.spawnRandomCreature()
    public long denySpawnDuplicates;          //отмененных спавнов уже после создания инстанса существа ( проходят т.к.
    public long denySpawnGarbageMobs;         //мобы спавн которых отменён в предверии очистки
    public long denyJoinDuplicates;           //количество дубликатов которым был запрещен вход в мир
    public long chunkEnteringDuplicates;

    /*Временный Контейнер для получения списка существующих в конкретной координате существ
      для обнаружения потенциальных дубликатов. */
    public final List<Entity> tmpList = new ArrayList<Entity>();
    
    /**
     * Событие на проверку какие НОВЫЕ существа могут быть заспавнены в конкретной точке мира.
     * Запрет на создание инстанса потенциального существа-дубликата через проверку
     * на наличие в координате куда хотят спавнить существо другого(других) живого существа
     * Срабатывает в SpawnerAnimal(spawnRandomCreature) 
     * до проверки на возможность спавна(CheckSpawn) (т.е. до создания инстанса entity)
     * и до проверки на возможность входа в мир (JoinEntityWorld)
     * Событие Имеет конкретные координаты куда спавнить и лист потенциально возможных
     * к спавну классов существ. Можно отменять событие, но ПОХОЖЕ нельзя изменять содержимое
     * листа ивента т.к. лист не генерируюется каждый раз заново а берётся из некого источника
     * @param event
     * Потенциальные Существа для "естественного" спавна не ниже (EntityLiving)
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPotencialSpawns(WorldEvent.PotentialSpawns event) {
        if (!event.world.isRemote && !event.isCanceled()) {

            /*Содержит варианты классов существ для спавна в конкретной точке мира event.(x y z)
            (Важно: классы существ наследники EntityLiving! см. SpawnerAnimals)
            Из листа после обработки события будет случайным образом выбрана одна
            запись и взят конкретный класс существа для спавна*/
            List<BiomeGenBase.SpawnListEntry> spawnList = event.list;
            if (spawnList != null && spawnList.size() > 0) {

                //если запланирована очистка - не создавать те классы существ которые всё равно будут удалены
                //Надёжнее отмена спавна на время очистки через GameRule:doMobSpawning
                if (WorldEventHandler.instance().isCleanupSheduled()) {
                    for (int i = 0; i < spawnList.size(); i++) {
                        BiomeGenBase.SpawnListEntry se = spawnList.get(i);
                        if (se != null) {
                            Class clazz = se.entityClass;
                            //если существо данного класса всё равно будет удалено в скорой запланированной очистке - не создавать инстанс существа данного класса
                            //отменить весь лист классов существ (вариантов на спавн)
                            if (OptiServerUtils.isEntityLivingClassCanBeUnloaded(clazz)) {
                                denySpawnGarbageMobs++;
                                event.setResult(Event.Result.DENY);//запрет на спавн;  отмена всего события
                                if (LOG.isDebugOwn()) {
                                    //здесь не использую LOG.debug т.к. у остальных хуков заточеных под entity нет префикса дебаг, чтобы небыло в разнобой
                                    LOG.info("[PotentialSpawn] DENY. Garbage Mob bedore Cleanup {} ({} {} {})", clazz, event.x+0.5D, event.y, event.z+0.5D);
                                }
                                return;
                            }
                        }
                    }
                }


                /*проверка на дубликаты перед созданием инстанса и спавном.
                по уже существующим живым существам в чанке по координате события
                и по листу вариантов (классов) существ, которые могут быть заспавнены в конкретной координате события*/

                final boolean onlyAlive = true;
                final double delta = 0.001;
                tmpList.clear();
                //получаю список конкретных существ уже существующих в координате на которую собираются спавнить некое новое случайное существо из event.list
                if ( XEntity.getEntitiesInCoords(event.world, event.x + 0.5D, event.y, event.z + 0.5D, delta, onlyAlive, tmpList) > 0) {
                    int packSpawnEntities = 0;
                    for (int i = 0; i < tmpList.size(); i++) {
                        /* живое Entity находящиееся на координатах эвента куда собираются спавнить
                        некое новое случайное существо из вариантов классов-существ описанных в event.list */
                        Entity e = tmpList.get(i);
                        if (e != null) {
                            //существо которым разрешено спавниться стаей (по несколько штук в одной точке)
                            if (OptiServerUtils.isAllowedSwarmSpawn(e)) {
                                packSpawnEntities++;
                            }
                            else {
                                /*Проверяю есть ли в вариантах для спавна нового существа класс существа уже существующего
                                на координате евента (куда хотят заспавнить случайное новое существо) т.е. (Потенциальный дубликат) */
                                int sei = XEntity.indexOfSpawnListEntryForClass(spawnList, e.getClass());
                                if (sei > -1 ) {
                                    denyPotentialSpawnsDuplicates++;
                                    /*Если есть хотябы один класс существа который может создать дубликат - Запрещаю всё событие
                                    т.е. если в списке будет класс которому можно создавать стаю - он тоже не пройдёт
                                    Поле event.list - финальное, а сам обьект листа - не пересоздаётся, а берёться из некого хранилища
                                    поэтому удалять из него варианты классов для спавна нельзя. 
                                    Как варинат, рефлексией насильно снимать final с поля и передавать туда новый лист
                                    без потенциальных дубликатов... Пока остановился просто на отмене события.*/
                                    event.setResult(Event.Result.DENY); //spawnList.remove(sei);
                                    if (event.isCancelable()) {
                                        event.setCanceled(true);
                                    }
                                    //[кол-во стайных классов сществ, которые не прошли из-за дубля] (не полное количество а те которые были до текущего класса
                                    if (LOG.isDebugOwn()) {
                                        LOG.info("[PotentialSpawn] DENY Duplicate {} ({} {} {}) [{}]", e.getClass(), event.x+0.5D, event.y, event.z+0.5D, packSpawnEntities);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                    tmpList.clear();//не держать ссылки на существа в памяти(чтобы не мешать GC)
                }
            }
        }
    }



    /**
     * Событие перед спавнам некого НОВОГО существа в мир.
     * Оно еще не добавлено ни в лист мира и в лист чанка. Можно отменить спавн.
     * Второй контур после WorldEvent.PotentialSpawns
     * Не всегда вызывается, например при прямом спавне существ (например в ZA
     * данное событие не будет создаваться
     *
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {

        if (!event.world.isRemote) {
            Entity entity = event.entity;

            /*DEBUG*/ if (entity.worldObj == null) {LOG.logEntity(Level.INFO, "NO WorldObj in {}", entity);}

            if (isSheduledCleanupDenyGarbageMobSpawn(event)) {//эксперементальный
                return;
            }

            if (!OptiServerUtils.isAllowedSwarmSpawn(entity)) {//Swarm - Pack стая, если существо относится к тем кому запрешено спавниться по несколько штук в одной точке
                //запрещать спавн существ-дублей если на месте спавна уже существуюет такое же существо с такими же координатами и классом
                Entity oldest = XEntity.getFirstPossibleDuplicateFor(entity);
                if (oldest != null ) {
                    event.setResult(Event.Result.DENY);
                    denySpawnDuplicates++;
                    if (LOG.isDebugOwn()) {
                        LOG.logEntity(Level.INFO, "[CheckSpawn] DENY. Duplicate: {}", event.entity);//
                    }
                }
            }
        }
    }



    /**
     * Существо "входит" в мир, срабатывает при
     * - при загрузке чанка в память
     *    оно уже добавлено в Chunck.entityList, но еще не добавлено в World.loadedEntityList
     *    после обрабоки данного метода если шине не выдать отбой оно вызывает добавление в мир
     *    3621:
     *     World.loadedEntityList.add(entity)
     *     World.onEntityAdded(entity);  здесь для каждого worldAccesses(игроки(?))
     *       ((IWorldAccess)World.worldAccesses.get(i)).onEntityCreate(p_72923_1_);
     *        #On server worlds, adds the entity to the entity tracker.??
     * - генерация в рантайме (враждебные и мирные мобы)
     * - вход игрока на сервер
     *
     * На данный момент стратегия такая -
     * При попытке входа существа в мир, на координаты уже существующего дургого
     * такого же живого существа (раные класс) считать новое существо - дублем и
     * отменить его вход в мир (отмена входа + поместить в очередь на выгрузку (setDead)
     * Проверяет на наличие хотя бы одного такого же уже существующего entity,
     * полную проверку на дубли здесь не производить (только в очистке)
     * -Работает совместно с хуком события спавна существа (ForgeEventFactory.canEntitySpawn),
     * которое так же запрещает спавн дубликатов. (Либо мусора в момент запланированной
     * и ожидаемой очистки ("Всё равно оно будет удалено"))
     *
     * Дубрирующимися существами считаются существа одного класса с одинаковыми
     * координатами (точность до 0.001)
     *
     * @param event
     * NOTES: Наблюдения:
     * Дубликаты достаточно частое явление. Спавн существ часто "псевдо случайным"
     * образом выкидывает координаты на котором есть старое существо. Это подтверждает
     * Логгирование этих двух событий JoinWorld и CheckSpawn. А вот дубли в хуке
     * onEnteringChunk() пока не надлюдал
     *
     * Идишники дублей обычно отличаются незначительно
     *
     * Идишник присваивается существу при создании, при этом глобальный генератор уникальных
     * идишников просто инкремирует его. т.е. если существо умирает его идишник по сути
     * становиться свободным, но никогда более (в текущем сеансе работы сервера!) не будет вновь
     * использован*(?проверить)
     * chunkCoordX-Y-Z у нового существа не заданы (нули) (??всегда ли?)
     *
     * Наблюдения:
     * Сразу после очистки идёт пересоздание существ...
     * видимо есть какое-то условие и если в чанке меньше положенного то идёт "заполнение"
     *
     * интересное для проверки
     * WorldServer.entityIdMap;
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote) {

            if (isSheduledCleanupDenyGarbageMobSpawn(event)) {//эксперементальный
                return;
            }

            Entity entity = event.entity;
            if (!OptiServerUtils.isAllowedSwarmSpawn(entity)) {//Swarm - Pack стая, если существо относится к тем кому запрешено спавниться по несколько штук в одной точке
                //проверяю существует ли такое же(класс) другое живое существо на координатах нового входящего в мир существа(поиск дубликатов)
                Entity oldest = XEntity.getFirstPossibleDuplicateFor(entity);
                if (oldest != null ) {
                    denyJoinDuplicates++;
                    /*в данной точке уже есть такое же другое существо -
                    Входящее в мир существо - дубликат - запретить вход*/
                    if (LOG.isDebugOwn()) {
                        LOG.logEntity(Level.INFO, "[JoinWorld] CANCEL Duplicate {}", entity);
                        //LOG.logEntity(Level.INFO, "[JoinWorld] Exist Oldest     {}", oldest);
                    }

                    XEntity.makeCustomNpcDespawnable(entity);//сработает только для катом-нпс-существ (убрать респавн после смерти)
                    entity.setDead();//в очередь на удаление в следующем тике если существо всё же пройдёт через отмену события

                    //только отменять событие - никаких удаление из чанков и мира т.к. текущее дублированное существо
                    //еще не добавлено в листы чанка и мира. здесь в  OptiServerUtils.unloadEntity(entity) нет смысла
                    //Дубликаты выявлять и удалять в очистке
                    event.setResult(Event.Result.DENY);
                    if (event.isCancelable()) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }


    
    /**
     * Событие входа существа в чанк, не обязательно через первое появление в мир
     * или загрузку с диска, это событие вызывается как я понимаю так же при
     * переходе самого существа из чанка в чанк.
     * Отменить это событие и добавление существа в чанк невозможно. 
     * см Chunk.addEntity (TODO глянуть а как там в KCauldron`е?)
     * Пока только обнаружение дубликатов в лог без каких-либо действий
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEnteringChunk(EntityEvent.EnteringChunk event) {
        if (!event.entity.worldObj.isRemote ) {//SERVER SIDE!
            if (!OptiServerUtils.isAllowedSwarmSpawn(event.entity)) {
                int chX = event.newChunkX;
                int chZ = event.newChunkZ;
                //это событие отправляется из уже загруженого в память чанка поэтому проверки загружен ли он здесь нет
                ChunkProviderServer cps = ((WorldServer)event.entity.worldObj).theChunkProviderServer;
                if (cps.chunkExists(chX, chZ)) {
                    Chunk chunk = ((WorldServer)event.entity.worldObj).theChunkProviderServer.provideChunk(chX, chZ);
                    int k = MathHelper.floor_double(event.entity.posY / 16.0D);
                    if (k < 0) {
                        k = 0;
                    }
                    if (k >= chunk.entityLists.length) {
                        k = chunk.entityLists.length -1;
                    }
                    Entity oldest = XEntity.getFirstPossibleDuplicate(event.entity, chunk.entityLists[k]);
                    if (oldest != null) {//если есть существо с таким же классом и такимиже кордами -
                        //считать дубликатом нужно то которое младше по event.entity.ticksExisted
                        //хотя можно и то которое "пытается войти" на место уже существующего!
                        chunkEnteringDuplicates++;
                        /*в данной точке уже есть такое же другое существо -
                        Входящее в мир существо - дубликат - запретить вход*/
                        if (LOG.isDebugOwn()) {
                            LOG.logEntity(Level.INFO, "[EnteringChunk] Duplicate {}", event.entity);
                            LOG.logEntity(Level.INFO, "[EnteringChunk] Oldest    {}", oldest);
                        }
                    }
                }
            }
        }
    }


    /**
     * Запланирована очистка памяти. Ожидается её запуск. Cущество в событии - мусорное
     * (будет удалено очисткой - запрет на спавн)
     *
     * Запрет на спавн ЧАСТИ! living-существ, удаляемых очисткой.
     * начиная от момента когда очистка запланирована до вывода сообщения о прошедней очистке
     * Только EntityLiving и ZAScent, разрешая спавн EntityItem, IProjectile, EntityFallingBlock... И других существ.
     * Лучшая альтернатива выключение естественного спавна мобов на время запланированой очистки,
     * но тот же ZA всё равно бдует спавнить TODO найти способ выключить его спавн временно
     * костыль.
     * Из имбового на время очистки например не будут спавниться временные враждебные мобы
     * это возможно можно будет как-то багаюзить
     * @param event
     * @return
     * =Эксперементальный=
     */
    public boolean isSheduledCleanupDenyGarbageMobSpawn(EntityEvent event) {
        if (WorldEventHandler.instance().isCleanupSheduled()) {
            Entity entity = event.entity;
            Class entityClass = event.entity.getClass();
            
            if ( !ConfigHelper.persistEntitiesList.contains(entityClass) &&
                    (  entityClass == XEntity.CL_ZA_ENTITY_SCENT ||
                       entity instanceof EntityLiving && OptiServerUtils.isEntityLivingCanBeUnloaded((EntityLiving)entity)
                      //TODO проверить будет ли рождение tfc-детёныша-животного 
                    )
               )
            {
                denySpawnGarbageMobs++;
                //запрет события спавна или входа в мир
                event.setResult(Event.Result.DENY);//запрет на спавн;  отмена всего события - event.setCanceled(true) но не всегда его можно отменить!
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
                if (LOG.isDebugOwn()) {
                    LOG.logEntity(Level.INFO, " DENY. Cleanable Mob before the Sheduled Cleaning: {}", event.getClass().getSimpleName(), event.entity);
                }
                return true;
            }
        }
        return false;
    }

}




/*
        прямые наследники от (net.minecraft.entity.Entity)
         net.minecraft.entity.EntityLivingBase
         net.minecraft.entity.item.EntityItem
         net.minecraft.entity.item.EntityXPOrb
         net.minecraft.entity.item.EntityFallingBlock
         net.minecraft.entity.item.EntityTNTPrimed
         net.minecraft.entity.projectile.EntityArrow
         net.minecraft.entity.projectile.EntityThrowable
         net.minecraft.entity.EntityHanging
         net.minecraft.entity.item.EntityMinecart
         net.minecraft.entity.item.EntityBoat
         net.minecraft.entity.projectile.EntityFishHook
         net.minecraft.entity.item.EntityEnderEye
         net.minecraft.entity.item.EntityFireworkRocket
         net.minecraft.entity.projectile.EntityFireball
         net.minecraft.entity.item.EntityEnderCrystal
         net.minecraft.entity.effect.EntityWeatherEffect
         com.bioxx.tfc.Entities.EntityFallingBlockTFC
         com.bioxx.tfc.Entities.EntityBarrel
         jds.bibliocraft.entity.EntitySeat
         astikoor.entity.EntityCart
         de.sanandrew.mods.betterboat.entity.EntityBetterBoat
         noppes.npcs.entity.EntityChairMount
         com.creativemd.creativecore.common.entity.EntitySit
         tektor.minecraft.talldoors.entities.AbstractLockable
         tektor.minecraft.talldoors.entities.drawbridge.DrawbridgeBase
         tektor.minecraft.talldoors.entities.drawbridge.DrawbridgeMachine
         tektor.minecraft.talldoors.entities.workbenches.KeyMaker
         tektor.minecraft.talldoors.entities.trapdoors.TrapDoor
         tektor.minecraft.talldoors.entities.drawbridge.EntityConnector
         tektor.minecraft.talldoors.entities.FakeEntity
         tektor.minecraft.talldoors.doorworkshop.entity.doorparts.AbstractDoorPart
         ZombieAwareness.EntityScent
         net.minecraft.entity.boss.EntityDragonPart
*/



    //эксперементальный для проверки создаются ли новые листы или исп-ся старые
    //Map<EnumCreatureType, List> psMap = new HashMap<EnumCreatureType, List>();

            //DEBUG >>>>
//            if (psMap.containsKey(event.type)) {
//                List prev = psMap.get(event.type);
//                if (prev == event.list) {
//                    LOG.debug("prev == current {}", event.type);
//                } else {
//                    LOG.debug("prev != current {}", event.type);
//                }
//            }
//            psMap.put(event.type, event.list);
            //<<<< DEBUG
