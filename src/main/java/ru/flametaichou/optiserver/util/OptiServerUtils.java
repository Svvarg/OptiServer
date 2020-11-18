package ru.flametaichou.optiserver.util;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldManager;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Vector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.LongHashMap;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import static ru.flametaichou.optiserver.util.ConfigHelper.checkTileEntityDuplicates;

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

    public static boolean isEntityLivingCanBeUnloaded(EntityLiving entityLiving) {
        return ( !entityLiving.isNoDespawnRequired() &&
                !(entityLiving instanceof IEntityOwnable) &&
                !(entityLiving instanceof EntityAnimal) &&
                !(entityLiving instanceof INpc) &&
                !(entityLiving instanceof IMerchant) &&
                !ConfigHelper.persistEntitiesList.contains(entityLiving.getClass()) //noppes.npcs.entity.EntityCustomNpc добавляется через конфиг, уст-ся по дефолту
                //TODO у CustomNpc есть настройка естественное исчезновение...
                );
    }
    
    /**
     * Только проверка без каких либо действий
     * Выгружать может EntityLiving EntityItem IProjectile EntityFallingBlock
     * @param entity
     * @return
     */
    public static boolean isEntityCanBeUnloaded(Entity entity) {
        boolean toClean = false;
        if (entity instanceof net.minecraft.entity.item.EntityMinecart) {
            entity=entity;
        }
        if (entity instanceof EntityLiving) {
            EntityLiving entityLiving = (EntityLiving) entity;
            toClean = OptiServerUtils.isEntityLivingCanBeUnloaded(entityLiving);
        }
        else if (entity instanceof EntityItem) {
            EntityItem entityItem = (EntityItem) entity;

            // Check Objects of Item //ids
            Item item = entityItem.getEntityItem().getItem();//String itemId = String.valueOf(Item.getIdFromItem(entityItem.getEntityItem().getItem()));
            if (!ConfigHelper.persistItemsList.contains(item)) { //if (!ConfigHelper.itemBlacklist.contains(itemId)) {
                toClean = true;
            } else {
                Logger.log("Skip entityItem " + entityItem);
            }
        }
        else if (entity instanceof IProjectile) {
            toClean = true;
        }
        //todo fallig toplvl anvil....
        else if (entity instanceof EntityFallingBlock) {
            toClean = true;
        }
        return toClean;
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

    private static void makeCustomNpcDespawnable(Entity entity) {
        NBTTagCompound data = new NBTTagCompound();
        entity.writeToNBT(data);
        data.setInteger("SpawnCycle", 3);
        entity.readFromNBT(data);
    }

    public static void unloadEntity(Entity entity) {
        if (entity.getClass().getSimpleName().equals("EntityCustomNpc")) {
            makeCustomNpcDespawnable(entity);
        }
        entity.setDead();

        if (!entity.worldObj.isRemote) {
            WorldManager worldManager = new WorldManager(MinecraftServer.getServer(), (WorldServer) entity.worldObj);
            worldManager.onEntityDestroy(entity);

        }
        //entity.worldObj.removeEntity(entity);
        //entity.worldObj.onEntityRemoved(entity);
        //entity.worldObj.unloadEntities(Arrays.asList(entity));

        //WorldServer worldServer = (WorldServer) entity.worldObj;
        //worldServer.getEntityTracker().removeEntityFromAllTrackingPlayers(entity);
        //WorldManager worldManager = new WorldManager(MinecraftServer.getServer(), (WorldServer) entity.worldObj);
        //worldManager.onEntityDestroy(entity);
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
    
    /**
     * Возможно это избыточный метод пока не нашел другого простого способа
     * получения всех существ в чанке
     * @param chunk
     * @return
     */
    public static int getEntityCountAtChunk(Chunk chunk) {
        if (chunk == null || chunk.entityLists == null) return 0;
        int count = 0;
        for (int i = 0; i < chunk.entityLists.length; i++) {
            List list = chunk.entityLists[i];
            if (list == null) continue;
            count += list.size();
        }
        return count;
    }

    public static StringBuilder appendEntityInfo(Entity e, boolean fullName, StringBuilder sb) {
        if (e==null) return sb;
        sb
          .append("id: ").append(e.getEntityId())
          .append(" (") .append(fullName ? e.getClass().getName() : e.getClass().getSimpleName()).append(')')          
          .append(" Age: ").append(e.ticksExisted)
          .append(" (");//Coords:
        appendDouble(sb, e.posX, 2);
        appendDouble(sb, e.posY, 2);
        appendDouble(sb, e.posZ, 2);
        sb.append(')');
        { /*DEBUG -->*/
            sb.append(" PES").append(e.preventEntitySpawning ? '+' : '-');
            if (e instanceof EntityItem) {
                EntityItem entityItem = (EntityItem) e;
                sb.append(" [").append( getItemInfo(entityItem.getEntityItem().getItem()) ).append(']');
            }
            else if (e instanceof EntityLiving){
                boolean canDespawn = ((EntityLiving)e).isNoDespawnRequired();
                sb.append(" D").append(canDespawn?'+':'-');
            }
        } /*<--DEBUG*/

        boolean b = OptiServerUtils.isEntityCanBeUnloaded(e);
        sb.append(" U").append(b?'+':'-'); //isEntityCanBeUnloaded

        return sb;
    }

    
    public static String getItemInfo(Item item) {
        if (item==null) return "null";
        int id = Item.getIdFromItem(item);
        return id + " "+ item.getClass().getCanonicalName() + " " + item.getUnlocalizedName();
    }


    /**
     * ДОбавление без постредника в виде String
     * Недостаток - погрешность точности на мелких значениях
     * 9.003 - > 9.002
     * @param sb
     * @param d число которое нужно добавить
     * @param n число знаков после запятой
     */
    public static void appendDouble(StringBuilder sb, double d, int n) {
        if (sb == null) return;
        long iPart = (long) d;
        sb.append(iPart);
        if (n > 0 && n < 10) {
            int nn = (int)Math.pow(10, n);
            long fPart = (long) ((d - iPart) * nn);
            sb.append('.');

            int l = sb.length();
            sb.append(fPart);
            int rem = n - (sb.length() - l);
            if (rem < n) {
                for (int i = 0; i < rem; i++) {
                    sb.insert(l, '0');
                }
            }
        }
        sb.append(' ');
    }

    /**
     * Получить чанк из памяти, в котором находится или в который хочет войти
     * существо по заданным координатам posXZ
     * С проверкой на соответствие координат чанка и координат точки мира для существ
     * уже добавленных в чанк
     * Если чанк не загружен в память вернёт null, но не будет его загружать\генерировать
     * @param e
     * @return
     */
    public static Chunk getLoadedChunkOrNullForEntity(Entity e) {
        if (e == null || e.worldObj == null) return null;
        int cx = MathHelper.floor_double(e.posX / 16.0D);
        int cz = MathHelper.floor_double(e.posZ / 16.0D);
        if (e.addedToChunk) {
            if (cx != e.chunkCoordX || cz != e.chunkCoordZ) {
                /*DEBUG*/
                Logger.error(String.format("Not equals posXZ & chunkCoordXZ in Entity %s (%s) ", e.getCommandSenderName(), Logger.getCoordinatesString(e)));
                cx = e.chunkCoordX;
                cz = e.chunkCoordZ;
            }
        }
        return getLoadedChunkOfNull(e.worldObj, cx, cz);
    }

    public static Chunk getLoadedChunkOfNull(World worldObj, int chunkX, int chunkZ) {
        return worldObj.getChunkProvider().chunkExists(chunkX, chunkZ)
                ? worldObj.getChunkFromChunkCoords(chunkX, chunkZ)
                : null;
    }

    /**
     * Получить конкретный лист существ из Chunk.entitylists[] по posY
     * @param chunk
     * @param posY
     * @return
     */
    public static List getEntityListForPosYAt(Chunk chunk, double posY) {
        if (chunk != null && chunk.entityLists != null) {
            int k = MathHelper.floor_double(posY / 16.0D);
            if (k < chunk.entityLists.length) {
                return chunk.entityLists[k];
            }
        }
        return Collections.emptyList();
    }

    /**
     * Chunk.entitylist по координатом точки в мире
     * Лист Существ (из самого чанка) в куске размером 16x16x16 (Например по кордам существа)
     * Выдаст только в том случае если чанк уже загружен иначе пустой лист
     */
    public static List getChunkEntityListForCoords(World worldObj, double posX, double posY, double posZ) {
        if (worldObj != null) {
            int cx = MathHelper.floor_double(posX / 16.0D);
            int cz = MathHelper.floor_double(posZ / 16.0D);
            if (worldObj.getChunkProvider().chunkExists(cx, cz)) {
                Chunk chunk = worldObj.getChunkFromChunkCoords(cx, cz);
                return getEntityListForPosYAt(chunk, posY);
            }
        }
        return Collections.emptyList();
    }
    /**
     * Получить первое существо не ignoreInstance надодящееся внутри заданных координат
     * todo пересечение с кубом корординаты?
     */
    public static Entity getEntityByPosition(World worldObj, double posX, double posY, double posZ, List<Entity> ignoreInstances) {
        if (worldObj==null) return null;
        List list = getChunkEntityListForCoords(worldObj, posX, posY, posZ);
        if (list!=null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                Entity e = (Entity) list.get(i);
                if (
                    Math.abs(posX - e.posX) <= 1 &&
                    Math.abs(posY - e.posY) <= 1 &&
                    Math.abs(posZ - e.posZ) <= 1) {
                    if (ignoreInstances == null || ignoreInstances!=null && !ignoreInstances.contains(e))
                        return e;
                }
            }
        }
        return null;
    }
    
    public static Entity getEntityById(World worldObj, int id) {
        if (worldObj==null || worldObj.loadedEntityList==null) return null;
        for (int i = 0; i < worldObj.loadedEntityList.size(); i++) {
            Entity e = (Entity) worldObj.loadedEntityList.get(i);
            if (e != null && e.getEntityId() == id)
                return e;
        }
        return null;
    }
    /**
     * Проверяю реальное наличие обьекта существа (по его кордам) в Chunk.entityList
     * Для проверки случая когда например заспавнившееся существо еще не
     * добавлено ни в один из чанков
     * @param e
     * @return
     * Проверка подлинности флага e.addedToChunk
     */
    public static boolean isRealyEntityAddedToChunk(Entity e) {
        Chunk chunk = getLoadedChunkOrNullForEntity(e);
        List list = getEntityListForPosYAt(chunk, e.posY);
        if (list != null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                Object el = list.get(i);
                if (el == e) return true;
            }
        }
        return false;
    }

    /**
     * Получение возможных двойников-клонов заданного entity
     * Получить всех существ с таким же классом как переденное существо,
     * находящиеся почти на тех же координатах что заданное (не дальше чем на 0.1)
     * @param entity новое спавнящиеся существо как загруженое с диска так и сгенерированное в рантайме
     * addedToChunk
     * @param outList
     * @param clear
     * @return
     */
    public static int getDuplicatePossibleEntitesFor(Entity entity, List<Entity> outList, boolean clear) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(outList, "Out list is null");
        if (clear) {
            outList.clear();
        }
        List list = getChunkEntityListForCoords(entity.worldObj, entity.posX, entity.posY, entity.posZ);
        if (list == null || list.isEmpty()) return 0;
        int last = outList.size();
        int oldestIndex = -1;
        long oldestTickAge = -1L;
        for (int i = 0; i < list.size(); i++) {
            Entity en = (Entity)list.get(i);
            if (isDuplicateEx(en, entity)) {
                outList.add(en);
                //замена последующей сортировки для удаления из листа самого старого существа
                if (en.ticksExisted > oldestTickAge) {
                    oldestIndex = outList.size() -1;
                    oldestTickAge = en.ticksExisted;
                }
                /*Это случай спавна нового существа прямо на корды уже существующего
                  добавляю в лист дубрирующихся, чтобы оставить старое */
                if (!entity.addedToChunk && !outList.contains(entity)) {
                    outList.add(entity);
                }
            }
        }
        /* Убираем самое старое существо из претендентов на смерть
        TODO тут одна тонкость может быть, замечал такие случаи когда,
        новое спавнящееся существо еще не добавлено в чанк и получится, что если
        там на тех же кордах уже есть такого же класса существо, то оно
        убирётся со списка на удаление, а новое будет добавлено*/
        if (oldestIndex >= 0) {
            outList.remove(oldestIndex);
        }
        return outList.size() - last;
    }

    /**
     * Одного класса координаты почти идентичны - клоны
     */
    public static boolean isDuplicateEx(Entity e1, Entity e2) {
        return (e1 != null && e2 != null &&
                e1 != e2 && //должны быть разные обьекты
                // isAssignableFrom()  en.boundingBox.intersectsWith(p_76618_2_)
                e1.getClass() == e2.getClass()
                && OptiServerUtils.approximatelyEquals(e1.posX, e2.posX)
                && OptiServerUtils.approximatelyEquals(e1.posY, e2.posY)
                && OptiServerUtils.approximatelyEquals(e1.posZ, e2.posZ));
    }

    /**
     * Получить количество вероятных клонов существ в конкретном листе чанка
     * Chunk.entitylist (16x16x16)
     * list должен содержать инстансы Entity
     * @param list
     * @param report если не null - Добавлять читабельную информацию о ходе
     * поиска клонированных существ
     * @return
     */
    public static int getEntitesDuplicatesCountIn(List list, List<String> report) {
        if (list == null || list.size() <= 1) return 0;//1 существо на весь кусок чанка не может дублироваться
        int count = 0;
        boolean mkReport = report != null;
        /*для исключения лишних проверок*/
        boolean[] duplicates = new boolean[list.size()];

        for (int i = 0; i < list.size(); i++) {
            if (duplicates[i]) continue;
            Entity e1 = (Entity) list.get(i);
            //if (e1.isDead) continue;Столит ли проверять мёртвые тела??
            for (int j = 0; j < list.size(); j++) {
                if (j == i || duplicates[j]) continue;
                Entity e2 = (Entity) list.get(i);
                if (isDuplicateEx(e1, e2)) {
                    count++;
                    duplicates[i] = true;
                    duplicates[j] = true;
                    if (mkReport) {
                        String line = "DIM" + e2.worldObj.provider.dimensionId + " " + e2.getClass().getSimpleName() + " " + e2.posX + " " + e2.posY + " " + e2.posZ;
                        if (!report.contains(line)) {
                            report.add(line);
                        }
                    }
                }
            }
        }
        return count;
    }


    /**
     * Поиск дублировавшихся инстансов существ по всем мирам сервера
     * @param report если НЕ нужен читаемый отчёт - передавай null
     * @return
     */
    public static long findDuplicates0(List<String> report) {
        // по чанкам
        long total = 0;
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int sz = ws.theChunkProviderServer.loadedChunks.size();
            // обычный цилк чтобы не создавать инстанс List.Iterator
            for (int i = 0; i < sz; i++) {
                Chunk chunk = (Chunk) ws.theChunkProviderServer.loadedChunks.get(i);
                for (List l : chunk.entityLists) {
                    total += OptiServerUtils.getEntitesDuplicatesCountIn(l, report);
                }

                //TODO TileEntity
                if (checkTileEntityDuplicates) {

                }
            }
        }
        return total;
    }

    /**
     * Если не находит возвращает null
     * @param classname
     * @return
     */
    public static Class getClassForName(String classname) {
        Class cl = null;
        try { cl = Class.forName(classname); }
        catch (ClassNotFoundException ex) {}
        return cl;
    }


    /* -=[<>]=--------------- COMMAND-LINE TOOLS -------------------=[<>]=--- */

    
    public static int argsCount (String[] args) {
        return args == null ? 0 : args.length;
    }

    public static boolean isDigitsOnly(String line) {
        if (line==null || line.isEmpty()) return false;
        for (int i = 0; i < line.length(); i++) {
            int code = line.charAt(i);
            if (!( code >= 48 && code <= 57)) return false;
        }
        return true;
    }
    /**
     * В наборе аргементов присутствует опциональный параметр c указанным именем
     * @param args
     * @param name должен начинаться с тире(минуса)
     * @return
     */
    public static boolean hasOpt(String[] args, String name) {
        if (args==null || args.length==0 || name==null || name.isEmpty()) return false;
        for (int i = args.length - 1; i >= 0; i--) {
            String arg = args[i];
            if (arg!=null && arg.length() > 1 && arg.charAt(0) == '-' && name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    //String... нет смысла т.к. либо полное имя либо сокращенное 1 или 2
    public static boolean isCmd(String cmdName, String name1, String name2) {
        if (cmdName==null || cmdName.isEmpty()) return false;
        return name1!=null && cmdName.equalsIgnoreCase(name1) || name2!=null && cmdName.equalsIgnoreCase(name2);
    }

    public static boolean isCmd(String cmdName, String name) {
        return isCmd(cmdName, name, null);
    }

    /**
     *    0          1  2  3
     * os chunk-obj 10 10 sub cmd param  -> от 3 {sub cmd param}
     * simple pipeline
     * @param args
     * @param i
     * @return
     */
    public static String[] subArgsFrom(String[] args, int i) {
        if (args == null) return null;
        int lastCount = args.length;
        int rem = lastCount - i;
        if (rem < 0) rem = 0;
        String[] subArgs = new String[rem];
        for (int j = 0; j < rem; j++) {
            subArgs[j] = args[i+j];
        }
        return subArgs;
    }

    public static String getSubBetween(String source, String tagO, String tagC) {
        if (source==null || source.isEmpty()) return "";
        int s = 0, e = source.length();
        if (tagO!=null && !tagO.isEmpty()) {
            int p = source.indexOf(tagO);
            if (p>0) s = p;
        }
        if (tagC!=null && !tagC.isEmpty()) {
            int p = source.indexOf(tagC, s);
            if (p > 0) e = p;
        }
        return source.substring(s, e);
    }

    /**
     * Работа со значениями полей класса в рантайме: установка-чтение
     * Только для открытых полей класса, закрытые оставил не доступными.
     * @param sender
     * @param argString fieldName [set] [value]  "list" вместо value выводит список полей
     * @param clazz класс поле которого интересует
     * @param instance null если нужно установить значение в статическое поле
     * @param prefix for full usage
     * @param readOnly блокировать попытку изменять значения полей
     */
    public static void cmdClassFieldInteract(ICommandSender sender, String[] argString, Class clazz, Object instance, String prefix, boolean readOnly, boolean onlyPublic) {
        int argsCount = OptiServerUtils.argsCount(argString);
        if (clazz == null) {
            throw new IllegalStateException("Class not defined");
        }
        if (argsCount <= 1) {
            sender.addChatMessage(new ChatComponentTranslation(prefix + " <filedName|list> [set] [value]"));
        }
        else {

            String fName = argString[1];
            //все реализованные инфетфейсы
            if (isCmd(fName, "implements", "interface")) {
                do {
                    sender.addChatMessage(new ChatComponentText(" --- ("+clazz.toString()+") ---"));
                    Class[] interfaces = clazz.getInterfaces();
                    if (interfaces != null && interfaces.length > 0) {
                        sender.addChatMessage(new ChatComponentText(" --- IMPLEMENTS: ---"));
                        for (Class aInterface : interfaces) {
                            sender.addChatMessage(new ChatComponentText(aInterface.getCanonicalName()));
                        }
                        sender.addChatMessage(new ChatComponentText("---------------------"));
                    }
                    clazz = clazz.getSuperclass(); //ныряем в предка
                } while (clazz != null && clazz != Object.class);//inheritanse
                sender.addChatMessage(new ChatComponentText("---------------------"));                
            }
            //все поля данного класса и его родителей (поле = значение)
            else if (isCmd(fName, "list")) {
                do {
                    Field[] fields = onlyPublic ? clazz.getFields() : clazz.getDeclaredFields();
                    sender.addChatMessage(new ChatComponentText(" ------- ("+clazz.toString()+") -------"));

                    for (int i = 0; i < fields.length; i++) {
                        Object value = "[Reading Failed]";
                        try {
                            if (!onlyPublic && !fields[i].isAccessible()) {
                                fields[i].setAccessible(true);
                            }
                            value = fields[i].get(instance);
                        }
                        catch (Exception ex) {
                        }
                        if (value != null) {
                            int size = -1;
                            if (value instanceof List) {
                                size = ((List)value).size();
                            }
                            //for Chunk.entitiesList
                            if (value instanceof List[]) {
                                List[] al = (List[])value;
                                size = 0;
                                for (int j = 0; j < al.length; j++) {
                                    List l = al[j];
                                    if (l != null && !l.isEmpty()) {
                                        size += l.size();
                                    }
                                }
                            }
                            else if (value instanceof Map) {
                                size = ((Map)value).size();
                            }
                            else if (value instanceof IntHashMap || value instanceof LongHashMap) {
                                //size = ((IntHashMap)value). TODO
                                size = -1; //"?"
                            }
                            if (size > -1) {
                                value = "size = " + String.valueOf(size);//-1 not supported type "?"
                            }
                        }
                        String msg = "("+fields[i].getType().getCanonicalName() + ") " +  fields[i].getName() + " = " + String.valueOf(value);
                        sender.addChatMessage(new ChatComponentText(msg));//здесь форматирование цвета в строках конфига роняет клиент если через ChatComponentTranslation
                    }
                    clazz = clazz.getSuperclass(); //ныряем в предка
                } while (clazz != null && clazz != Object.class);//inheritanse
                sender.addChatMessage(new ChatComponentText("---------------------"));
            }

            else if (fName != null && !fName.isEmpty()) {
                String response = null;
                Field field = null;
                try {
                    field = clazz.getField(fName);
                }
                catch (Exception ex) {
                }
                if (field == null) {
                    response = "not found: "+ fName;
                } else {
                    if (argsCount > 3 && isCmd(argString[2], "set", "=")) {
                        if (!readOnly) {
                            boolean set = OptiServerUtils.setValue(instance, field, argString[3]);
                            response = "set " + (set ? "success" : "fail");
                        } else {
                            response = "Read only!";
                        }
                    } else {
                        try {
                            response = String.valueOf(field.get(instance));
                        }
                        catch (Exception ex) {
                            response = "read fail";
                        }
                    }
                }
                sender.addChatMessage(new ChatComponentText(response));
            }
        }
    }

    public static boolean setValue(Object instance, Field field, Object value) {
        try {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            Class type = field.getType();
            String s = value instanceof String ? (String) value : null;
            if (s != null) {
                if (type ==  boolean.class) value = Boolean.valueOf(s);
                else if (type == int.class) value = Integer.valueOf(s);
                else if (type == long.class) value = Long.valueOf(s);
                else if (type == short.class) value = Short.valueOf(s);
                else if (type == double.class) value = Double.valueOf(s);
                else if (type == float.class) value = Float.valueOf(s);
                else if (type == byte.class) value = Byte.valueOf(s);
                else if (type == String.class) value = s;
            }
            field.set(instance, value);
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

    public static int stringToChunkCoord(String[] args, int index, boolean needConvertToChunkPos) {
        if (args == null || args.length <= index) return 0;
        return needConvertToChunkPos
                ? MathHelper.floor_double(Double.parseDouble(args[index]) / 16.0D)
                : Integer.parseInt(args[index]);
    }

    // 123000 -> 2m 3s
    public static String millisToReadable(long millis, String prefix) {
        long sec = millis / 1000L;
        int d = (int) (sec / 86400L);//24*60*60);
        if (d > 0) sec -= d * 86400L;
        int h = (int) (sec / 3600L);//60*60
        if (h > 0) sec -= h * 3600L;
        int m = (int) (sec / 60L);
        if (m > 0) sec -= m * 60L;
        int s = (int)(sec);
        StringBuilder sb = new StringBuilder();
        if (prefix!=null) sb.append(prefix).append(' ');

        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0) sb.append(s).append("s ");
        sb.append(" (").append(millis).append("ms)");
        return sb.toString();
    }

    public static String getClassInfo(String className) {
        if (className==null || className.length()<0) return "empty";
        try {
            Class clazz = Class.forName(className);
            //clazz.getClassLoader()
            StringBuilder sb = new StringBuilder();
            while (clazz != null && clazz != Object.class) {
                sb.append(clazz.getCanonicalName()).append(' ');
                Class[] interfaces = clazz.getInterfaces();
                if (interfaces!=null && interfaces.length>0) {
                    sb.append("Interfaces:[");
                    for (int i = 0; i < interfaces.length; i++) {
                        Class aInterface = interfaces[i];
                        if (i>0)sb.append(' ');
                        sb.append(aInterface.getCanonicalName());

                    }
                    sb.append(']');
                }
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz == Object.class) {
                    break;
                }
                sb.append(" > ");//наследование от
            }
            return sb.toString();
        }
        catch (ClassNotFoundException ex) {
        }
        return "not found";
    }


    public static Iterator getAllClassesIteratorFor(ClassLoader classLoader) {
        if (classLoader == null) return null;

        Class classLoaderClass = classLoader.getClass();
        while (classLoaderClass != java.lang.ClassLoader.class) {
            classLoaderClass = classLoaderClass.getSuperclass();
        }
        try {
            java.lang.reflect.Field field = classLoaderClass.getDeclaredField("classes");
            field.setAccessible(true);
            Vector classes = (Vector) field.get(classLoader);
            return classes.iterator();
        } catch (Exception e) {
            return null;
        }
    }

}
