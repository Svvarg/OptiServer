package ru.flametaichou.optiserver.util;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.Item;
import org.apache.logging.log4j.Level;

import org.swarg.mcforge.util.XItem;
import org.swarg.mcforge.util.XServer;
import org.swarg.mcforge.util.XEntity;
import static ru.flametaichou.optiserver.OptiServer.LOG;
import static org.swarg.common.Strings.appendDouble;
import static org.swarg.mcforge.util.XEntity.isCustomNpcCanBeUnloaded;
import static org.swarg.mcforge.util.XEntity.isLootableBodyCanBeUnloaded;



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
        return usedMem / 1048576;
    }

    public static long getFreeMemMB() {
        long freeMem = Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
        return freeMem / 1048576;
    }

    public static long getTotalMemoryMB() {
        return Runtime.getRuntime().totalMemory() / 1048576;
    }

    public static long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / 1048576;
    }

    /**
     *
     * @param entityLiving
     * @return
     */
    public static boolean isEntityLivingCanBeUnloaded(EntityLiving entityLiving) {
        return (
                (!entityLiving.isNoDespawnRequired() &&
                 !(entityLiving instanceof IEntityOwnable) &&
                 !(entityLiving instanceof EntityAnimal) &&
                 !(entityLiving instanceof INpc) &&
                 !(entityLiving instanceof IMerchant)
                 //&& !ConfigHelper.persistEntitiesList.contains(entityLiving.getClass())
                )
                //noppes.npcs.entity.EntityCustomNpc можно удалять те у которых снят респавн и стоит естественное исчезновение
                || isCustomNpcCanBeUnloaded(entityLiving)  //??
                //пустые трупы
                || isLootableBodyCanBeUnloaded(entityLiving)
                );
    }

    /**
     * Будет ли заданный класс entityLiving удалён при очистке
     * Для запрета создания инстансов entityLiving существа в моменты времени
     * когда ожидается(запланирована) очистка (всё равно будут удалены)
     * Для PotentialSpawn - даже не пытаться спавнить мусор который будет удалён.
     * Исключает КастомНПС трупы, спискок классов из конфига которые не удалять при очистке
     * @param entityLivingClass
     * @return
     * TODO проверить на совместимость с тфк (животные и т.д)
     */
    public static boolean isEntityLivingClassCanBeUnloaded(Class entityLivingClass) {
        //net.minecraft.entity.monster.EntityMob
        return ( EntityLiving.class.isAssignableFrom(entityLivingClass) &&  //защита от провероки на возможность удаление очисткой не для EntityLiving

                 //not persists
                 !ConfigHelper.persistEntitiesList.contains(entityLivingClass) &&

                 !(IEntityOwnable.class.isAssignableFrom(entityLivingClass)) &&
                 !(EntityAnimal.class.isAssignableFrom(entityLivingClass)) &&
                 !(INpc.class.isAssignableFrom(entityLivingClass)) &&
                 !(IMerchant.class.isAssignableFrom(entityLivingClass)) &&
                 //not CustomNPC
                 !(XEntity.IS_CUSTOM_NPC_LOADED && XEntity.CL_NOPPES_EntityNPCInterface.isAssignableFrom(entityLivingClass)) &&
                 //not Corps
                 !(XEntity.IS_CYANO_LOOTABLE_BODY_LOADED && XEntity.CL_CYANO_ENTITY_LOOTABLE_BODY.isAssignableFrom(entityLivingClass))
                 
                );
    }


    /**
     * Может ли существо быть удалено очисткой
     * Только проверка без каких либо действий
     * Выгружать может EntityLiving EntityItem IProjectile EntityFallingBlock
     * По наблюдениям почти сразу после удаления существ идёт их спавн в пустующие
     * чанки..
     * @param entity
     * @return
     */
    public static boolean isEntityCanBeUnloaded(Entity entity) {
        boolean cleanable = false;
        Class entityClass = entity.getClass();

        //список классов существ которые нельзя удалять
        if ( ConfigHelper.persistEntitiesList.contains( entityClass ) ) {
            cleanable = false;
        }

        else if (entity instanceof EntityLiving) {
            EntityLiving entityLiving = (EntityLiving) entity;
            //можно удалять: CustomNPC у которых стоит естественное исчезновение + выключен респавн; + пустые трупы
            cleanable = OptiServerUtils.isEntityLivingCanBeUnloaded(entityLiving);
        }
        else if (entity instanceof EntityItem) {
            EntityItem entityItem = (EntityItem) entity;

            // Check Objects of Item //ids
            Item item = entityItem.getEntityItem().getItem();//String itemId = String.valueOf(Item.getIdFromItem(entityItem.getEntityItem().getItem()));
            if (!ConfigHelper.persistItemsList.contains(item)) { //if (!ConfigHelper.itemBlacklist.contains(itemId)) {
                cleanable = true;
            } else {
                LOG.logItem(Level.DEBUG, "An Undeletable Item [{}] is recognized and left in place", item, false);
            }
        }
        //?? toplvl metall spears arrows bolts?
        else if (entity instanceof IProjectile) {
            cleanable = true;
        }
        //TFC-падающие блоки сюда не входят...
        else if (entity instanceof EntityFallingBlock) {
            cleanable = true;
        } 
        //пдающие блоки из ТФК extends Entity implements IEntityAdditionalSpawnData
        //TODO falling toplvl anvil?...
        else if (entityClass == XEntity.CL_TFC_ENTITY_FALLING_BLOCK) {
            cleanable = true;
        }
        //ZombieAwarenasess Scent неведимки притягивающие зомбарей
        else if (entityClass == XEntity.CL_ZA_ENTITY_SCENT) {//XEntity.IS_ZOMBIE_AWARENESS_LOADED
            cleanable = true;
        }

        return cleanable;
    }
    
    /**
     * Получить минимальный тпс по всем загруженым мирам
     * @return
     */
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

    /**
     * Данному классу существ разрешено спавниться по несколько штук в одних и
     * тех же координатах (стаей Pack)
     * Проверить может ли entity спавниться на координатах, на которых уже
     * существует такого же типа entity
     * Для блокировки дублирования при JoinEntityWorld и\или CheckSpawn
     * @param entity
     * @return
     */
    public static boolean isAllowedSwarmSpawn(Entity entity) {
        return entity instanceof EntityItem ||
               entity instanceof EntityXPOrb ||
               entity instanceof IProjectile ||
               (entity instanceof EntityAgeable && ((EntityAgeable) entity).isChild()) ||
               ConfigHelper.allowedSwarmSpawnEntitiesList.contains( entity.getClass() );
    }

//    public static boolean isAllowedSwarmSpawn(Class eClass) {
//        return eClass != null && (
//               EntityItem.class.isAssignableFrom(eClass) ||
//               EntityXPOrb.class.isAssignableFrom(eClass) ||
//               IProjectile.class.isAssignableFrom(eClass) ||
//               EntityAgeable.class.isAssignableFrom(eClass) || //&& ((EntityAgeable) entity).isChild()) ||
//               ConfigHelper.allowedSwarmSpawnEntitiesList.contains( eClass ));
//    }


//    //todo проверить возможно нужно использовать родителя
//    @Deprecated
//    public static boolean isCustomNpcEntity(Entity entity) {
//        return entity != null && classCustomNpcEntity != null &&
//               entity.getClass() == classCustomNpcEntity;
//    }
//
//    /*Чтобы после смерти не появлялся*/
//    @Deprecated
//    public static void makeCustomNpcDespawnable(Entity entity) {
//        NBTTagCompound data = new NBTTagCompound();
//        entity.writeToNBT(data);
//        data.setInteger("SpawnCycle", 3);
//        entity.readFromNBT(data);
//    }

    /**
     * Выгрузить существо из мира, чанков и всех worldAccesses
     * ("удалить")
     * Только для серверной стороны
     * @param entity
     */
    public static void unloadEntity(Entity entity) {
        if (entity != null && !entity.worldObj.isRemote) {
            
            WorldServer ws = (WorldServer) entity.worldObj;
            //для того чтобы существо не респавнилось после "удаления" ?? а вообще как оно может респавниться если оно удаяется из листа мира и чанка??
            XEntity.makeCustomNpcDespawnable(entity);//сработает только для катом-нпс-существ
            //"маркерует" существо в очередь на удаление
            ws.removeEntity(entity); //entity.setDead(); / ridden / mount

            //прямое удаление инстанса существа из мира и далее из листа чанка
            ws.loadedEntityList.remove(entity);
            int cx = entity.chunkCoordX;
            int cz = entity.chunkCoordZ;
            if (entity.addedToChunk && ws.theChunkProviderServer.chunkExists(cx, cz)) {
                ws.getChunkFromChunkCoords(cx, cz).removeEntity(entity);
            }
            ws.onEntityRemoved(entity); // удаление существа из всех worldAccesses

            //??удаление существа из очереди на удаление если оно вдруг там было
            List unloadedEntityList = XServer.getUnloadedEntitiesList(ws);
            if (unloadedEntityList != null) {
                unloadedEntityList.remove(entity);
            }
            //TODO проверить как выгружается существо
            //ws.getEntityTracker().removeEntityFromAllTrackingPlayers(entity);//это вроде как относится только к игрокам
        }
    }

    public static boolean approximatelyEquals(double d1, double d2) {
        return Math.abs(d1 - d2) < 0.001;
    }

//    public static boolean isDuplicate(Entity entity) {
//        int radius = 1;
//        List nearestEntities = entity.worldObj.getEntitiesWithinAABB(
//                entity.getClass(),
//                AxisAlignedBB.getBoundingBox(
//                        entity.posX-radius,
//                        entity.posY-radius,
//                        entity.posZ-radius,
//                        (entity.posX + radius),
//                        (entity.posY + radius),
//                        (entity.posZ + radius)
//                )
//        );
//
//        if (!nearestEntities.isEmpty()) {
//            for (Object nearestEntity : nearestEntities) {
//                Entity e = (Entity) nearestEntity;
//                if (e.getCommandSenderName().equals(entity.getCommandSenderName())
//                        && e.getEntityId() != entity.getEntityId()
//                        && OptiServerUtils.approximatelyEquals(e.posX, entity.posX)
//                        && OptiServerUtils.approximatelyEquals(e.posY, entity.posY)
//                        && OptiServerUtils.approximatelyEquals(e.posZ, entity.posZ)) {
//
//                    return true;
//                }
//            }
//        }
//
//        return false;
//    }
    
//    @Deprecated
//    public static List<Entity> findDuplicates(Entity entity) {
//        List<Entity> duplicates = new ArrayList<Entity>();
//        int radius = 1;
//        List nearestEntities = entity.worldObj.getEntitiesWithinAABB(
//                entity.getClass(),
//                AxisAlignedBB.getBoundingBox(
//                        entity.posX-radius,
//                        entity.posY-radius,
//                        entity.posZ-radius,
//                        (entity.posX + radius),
//                        (entity.posY + radius),
//                        (entity.posZ + radius)
//                )
//        );
//
//
//        if (nearestEntities != null && nearestEntities.size() > 1) {
//            for (Object nearestEntity : nearestEntities) {
//                Entity e = (Entity) nearestEntity;
//                if (e.getCommandSenderName().equals(entity.getCommandSenderName())
//                        //&& e.getEntityId() != entity.getEntityId()
//                        && OptiServerUtils.approximatelyEquals(e.posX, entity.posX)
//                        && OptiServerUtils.approximatelyEquals(e.posY, entity.posY)
//                        && OptiServerUtils.approximatelyEquals(e.posZ, entity.posZ)) {
//
//                    duplicates.add(e);
//                }
//            }
//        }
//
//        if (duplicates.size() > 1) {
//            duplicates.sort(new Comparator<Entity>() {
//                @Override
//                public int compare(Entity entity1, Entity entity2) {
//                    return Integer.compare(entity1.ticksExisted, entity2.ticksExisted);
//                }
//            });
//
//            // Removing oldest entity
//            duplicates.remove(duplicates.size() - 1);
//            return duplicates;
//        } else {
//            return Collections.emptyList();
//        }
//    }
    

    public static StringBuilder appendEntityInfo(Entity e, boolean fullName, StringBuilder sb) {
        if (e == null) return sb;
        sb.append("id: ").append(e.getEntityId())
          .append(" (") .append(fullName ? e.getClass().getName() : e.getClass().getSimpleName()).append(')')          
          .append(" Age: ").append(e.ticksExisted)
          .append(" (");//Coords:
        appendDouble(sb, e.posX, 1).append(' ');//10.5
        appendDouble(sb, e.posY, 1).append(' ');
        appendDouble(sb, e.posZ, 1);
        sb.append(')');
        { /*DEBUG -->*/
            sb.append(" PES").append(e.preventEntitySpawning ? '+' : '-');
            if (e instanceof EntityItem) {
                EntityItem entityItem = (EntityItem) e;
                sb.append(" [");
                XItem.appendItemInfo(entityItem.getEntityItem().getItem(), true, sb);
                sb.append(']');
            }
            else if (e instanceof EntityLiving) {
                boolean canDespawn = ((EntityLiving)e).isNoDespawnRequired();
                sb.append(" D").append(canDespawn?'+':'-');
            }
        } /*<--DEBUG*/

        boolean u = OptiServerUtils.isEntityCanBeUnloaded(e);
        sb.append(" U").append(u?'+':'-'); 

        //данному классу существ разрешено спавниться по несколько штук в одних и тех же координатах MaxPackSize Spawn
        boolean a = OptiServerUtils.isAllowedSwarmSpawn(e);
        sb.append(" S").append(a?'+':'-');

        return sb;
    }




//    public static String getCoordinatesString(int x, int y, int z) {
//        return "x:" + x + " y:" + y + " z:" + z;
//    }
//
//    public static String getCoordinatesString(Entity e) {
//        return "xyz: " + (int)e.posX + " " + (int)e.posY + " " + (int)e.posZ;
//    }
//
//    public static String getCoordinatesString(TileEntity e) {
//        return "xyz: " + e.xCoord + " " + e.yCoord + " " + e.zCoord;
//    }

}
