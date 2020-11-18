package ru.flametaichou.optiserver.handlers;

import java.util.List;
import java.util.ArrayList;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;
import ru.flametaichou.optiserver.util.ConfigHelper;

public class EntitySpawnHandler {

    ///*DEBUG*/private static List<Integer> removedEntitiesList = new ArrayList<Integer>();
    /*использую один контейнер для всех поисков.*/
    private List<Entity> duplicates = new ArrayList<Entity>();

    /**
     * Существо только появляется в мире, срабатывает при
     * - при загрузке чанка в память
     *    оно уже добавлено в Chunck.entityList, но еще не добавлено в World.loadedEntityList
     *    после обрабоки данного метода если шине не выдать отбой оно вызывает добавление в мир
     *    3621:
     *     World.loadedEntityList.add(entity)
     *     World.onEntityAdded(entity);  здесь для каждого ассерера
     *       ((IWorldAccess)World.worldAccesses.get(i)).onEntityCreate(p_72923_1_);
     *        #On server worlds, adds the entity to the entity tracker.??
     * - генерации в рантайме (враждебные и мирные мобы)
     * - входе игрока на сервер
     * На данный момент стратегия такая - "убить" дублирующееся существо на старте (при спавне)
     * чтобы мир сам его удалил при седующих тиках
     * Дубрирующимися существами считаются существа с одинаковым классом в одних координатах
     * Заметил такую вещь (через новую команду os chunk), что дублированные существа обычно
     * появляются на крайних загруженных чанках, поле tickExisted у дублей равны и не тикают,(мб игрок далеко?)
     * идишники обычно так же имеют близкие значения.
     *
     * идишник присваивается существу при создании, при этом глобальный генератор уникальных
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
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntityJoinWorldEvent event) {

        if (event.entity instanceof EntityItem
                || event.entity instanceof EntityXPOrb
                || event.entity instanceof IProjectile
                || (event.entity instanceof EntityAgeable && ((EntityAgeable) event.entity).isChild())
                || ConfigHelper.allowedSwarmSpawnEntitiesList.contains(event.entity.getClass())
                ) {
            return;
        }

        /*--- DEBUG --->*/
//        Entity e = event.entity;
//        ///*DEBUG*/String info = OptiServerUtils.appendEntityInfo(e, new StringBuilder()).toString();
//        boolean addedToChunk = e.addedToChunk;
//        boolean realyAddedToChunk = OptiServerUtils.isRealyEntityAddedToChunk(event.entity);
//        if (e.chunkCoordY == 0 && e.chunkCoordX == 0 && e.chunkCoordZ==0) {
//            Logger.debug(String.format("### Entity JoinToWorld without chunkCoordX-Y-Z : %s (%s) id:%s (addedToChunk%sRealy%s) Y:%s",
//                    event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity), event.entity.getEntityId(),
//                    addedToChunk ? '+':'-', realyAddedToChunk ? '+':'-', e.chunkCoordY));
//            if (e.addedToChunk) {
//
//            }
//        }
//        if (addedToChunk != realyAddedToChunk) {
//            Logger.error(String.format("### addedToChunk != realyAddedToChunk: %s (%s) %s (addedToChunk%sRealy%s)",
//                    event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity), event.entity.getEntityId(),
//                    addedToChunk ? '+':'-', realyAddedToChunk ? '+':'-'));
//        }
//        //ChunkProviderServer --> loadChunk если чанка нет в памяти берёт с диска, здесь же только запрос чанков из памяти
//        //а если каким-то боком идёт спавн существа одного и того же класса на одни и те же самые корды...
//        Chunk chunk = OptiServerUtils.getLoadedChunkOrNullForEntity(e);
//        if (chunk == null) {
//            Logger.error(String.format("### The Entity Trying to spawn in NOT Loaded Chunk: %s (%s) id:%s", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity), event.entity.getEntityId()));
//        }
        /*<--- DEBUG ---*/
        
        OptiServerUtils.getDuplicatePossibleEntitesFor(event.entity, duplicates, true);
        boolean spawnCancelledSpawn = false;
        if (!duplicates.isEmpty()) {
            for (int i = 0; i < duplicates.size(); i++) {
                Entity duplicate = duplicates.get(i);
                int id = duplicate.getEntityId();
                //if (!removedEntitiesList.contains(id))
                {
                    //removedEntitiesList.add(id);
                    Logger.log(String.format("Unloading duplicated entity: %s (%s) id:%s", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity), id));

                    OptiServerUtils.unloadEntity(duplicate);
                    /*spawnCancelledSpawn*/
                    if (!event.entity.addedToChunk && !spawnCancelledSpawn) {
                        event.setCanceled(true);//чтобы не добавляло еще не добавленное в лист существующих т.к. его "уже удалили" из EntityTracker (возможно оно туда даже еще и не попадало)
                        spawnCancelledSpawn = true;
                        //return ??
                    }
                    
                }
//                else {
//                    Logger.error(String.format("Entity already removed: %s (%s) id:%s", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity), id ));
//                }
            }
            //обязательно. использую только на одну "сессию" 
            //removedEntitiesList.clear();
        }
    }
}
