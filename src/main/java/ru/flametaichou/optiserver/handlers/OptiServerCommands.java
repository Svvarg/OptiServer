package ru.flametaichou.optiserver.handlers;

import java.util.*;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import ru.flametaichou.optiserver.util.ConfigHelper;
import ru.flametaichou.optiserver.OptiServer;
import ru.flametaichou.optiserver.util.JVMUtils;
import ru.flametaichou.optiserver.util.OptiServerUtils;
import static ru.flametaichou.optiserver.util.OptiServerUtils.argsCount;
import static ru.flametaichou.optiserver.util.OptiServerUtils.hasOpt;
import static ru.flametaichou.optiserver.util.OptiServerUtils.isCmd;
import static ru.flametaichou.optiserver.util.OptiServerUtils.stringToChunkCoord;

public class OptiServerCommands extends CommandBase
{
    private final List<String> aliases;
    private final String usage =
            "/optiserver <tps/chunks/clear/mem/largest/status/entitydebug/duplicates/tpsstat/memstat/map/chunk/cause/save/reload/persists/config/world-obj/chunk-obj/item-class-by-id/entity-obj/class/jvm>";
    private final String[] tabCompletion;

    public OptiServerCommands()
    {
        aliases = new ArrayList<String>();
        aliases.add("optiserver");
        aliases.add("os");        
        tabCompletion = OptiServerUtils.getSubBetween(usage, "<", ">").split("/");
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    @Override
    public int compareTo(Object o)
    {
        return 0;
    }

    @Override
    public String getCommandName()
    {
        return "optiserver";
    }

    @Override
    public String getCommandUsage(ICommandSender var1)
    {
        return usage;
    }

    @Override
    public List<String> getCommandAliases()
    {
        return this.aliases;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] argString) {
        World world = sender.getEntityWorld();

        if (!world.isRemote) {
            String cmd;
            if (argString.length == 0 || (cmd = argString[0]) == null || cmd.isEmpty() || "help".equals(cmd)) {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                return;
            }

            if (cmd.equals("tps")) {
                cmdTPS(sender);
            }
            else if (cmd.equals("chunks")) {
                cmdChunks(sender);
            }
            else if (cmd.equals("largest")) {
                cmdLargest(sender);
            }
            else if (isCmd(cmd, "clear", "clean")) {
                cmdClear(argString);
            }
            else if (cmd.equals("mem")) {
                cmdMem(sender);
            }
            else if (cmd.equals("status")) {
                cmdStatus(sender, argString);
            }
            else if (cmd.equals("hoppers")) {
                cmdHoppers(sender);
            }
            else if (cmd.equals("entitydebug")) {
                cmdEntityDebug(sender);
            }
            else if (isCmd(cmd, "duplicates", "d")) {
                cmdDuplicates(sender);
            }
            else if (cmd.equals("tpsstat")) {
                cmdTpsStat(argString, sender);
            }
            else if (cmd.equals("memstat")) {
                cmdMemStat(argString, sender);
            }

            //причина последней очистки
            else if (isCmd(cmd, "cause")) {
                sender.addChatMessage(new ChatComponentText(WorldEventHandler.getInstance().getCleanCause()));
            }

            //Config
            else if (isCmd(cmd, "save")) {
                sender.addChatMessage(new ChatComponentText("Saved: " + ConfigHelper.saveCurrentState()));
            }
            else if (cmd.equals("reload")) {
                cmdReload(sender, argString);
            }

            else if ("map".equals(cmd)) {
                cmdUpLoadedChunksMap(sender, argString);
            }
            else if ("chunk".equals(cmd)) {
                cmdChunkInfo(sender, argString);
            }
            else if (isCmd(cmd, "persists", "p")) {
                cmdPersistsEntityAndItems(sender, argString);
            }
            else if (isCmd(cmd, "config")) {
                OptiServerUtils.cmdClassFieldInteract(sender, argString, ConfigHelper.class, null, "os "+cmd, /*Can edit*/false, true);
            }
            else if (isCmd(cmd, "item-class-by-id", "ic")) {
                cmdGetItemClassById(sender, argString);
            }

            //--- эксперементальные для выявления состояния обьектов ---

            else if (isCmd(cmd, "class")) {
                cmdClass(sender, argString);
            }
            //os world-obj list
            else if (isCmd(cmd, "world-obj", "wo")) {
                OptiServerUtils.cmdClassFieldInteract(sender, argString, WorldServer.class, sender.getEntityWorld(), "os "+cmd, /*readOnly*/true, false);
            }
            //os chunk-obj 10 12 list
            else if (isCmd(cmd, "chunk-obj", "co")) {
                cmdChunkObj(sender, argString);
            }
            else if (isCmd(cmd, "entity-obj", "eo")) {
                cmdEntityObj(sender, argString);
            }
            else if (isCmd(cmd, "jvm")) {
                cmdJVM(sender, argString);
            }

        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender var1)
    {
        return true;
    }

    @Override
    public List<?> addTabCompletionOptions(ICommandSender var1, String[] var2)
    {
        return var2.length == 1 ? getListOfStringsMatchingLastWord(var2, tabCompletion) : null;
    }

    @Override
    public boolean isUsernameIndex(String[] var1, int var2)
    {
        return false;
    }


    private void cmdMemStat(String[] argString, ICommandSender sender) throws NumberFormatException {
        int count = 12;
        if (argString.length > 1) {
            count = Integer.parseInt(argString[1]);
        }
        OptiServer os = OptiServer.getInstance();
        for (int percent = 100; percent > 0; percent -= 20) {
            String message = getPercentMessage(os.memoryStatsMap, percent, count, OptiServerUtils.getMaxMemoryMB());
            if (!(sender instanceof EntityPlayer)) {
                message = OptiServerUtils.reformatMessageForConsole(message);
                sender.addChatMessage(new ChatComponentText(message));
            } else {
                sender.addChatMessage(new ChatComponentTranslation(message));
            }
        }

        String stringMessage = getLineString(os.memoryStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(stringMessage));

        String memoryMessage = "|";
        int counter = 0;
        int missCounter = 0;
        for (Date date : os.memoryStatsMap.keySet()) {
            if (os.memoryStatsMap.keySet().size() > count) {
                if (missCounter < os.memoryStatsMap.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }
            double memory = os.memoryStatsMap.get(date);
            int memoryPercent = ((Double)(100F / OptiServerUtils.getMaxMemoryMB() * memory)).intValue();
            memoryMessage += (OptiServerUtils.getTwoSymbolsNumber(memoryPercent) + "|");

            counter++;
            if (counter >= count) {
                break;
            }
        }
        sender.addChatMessage(new ChatComponentTranslation(memoryMessage));

        sender.addChatMessage(new ChatComponentTranslation(stringMessage));

        String hours = getHoursMessage(os.memoryStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(hours));

        String minutes = getMinutesMessage(os.memoryStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(minutes));
    }

    private void cmdTpsStat(String[] argString, ICommandSender sender) throws NumberFormatException {
        int count = 12;
        if (argString.length > 1) {
            count = Integer.parseInt(argString[1]);
        }

        for (int percent = 100; percent > 0; percent -= 20) {
            String message = getPercentMessage(OptiServer.getInstance().tpsStatsMap, percent, count, 20F);
            if (!(sender instanceof EntityPlayer)) {
                message = OptiServerUtils.reformatMessageForConsole(message);
                System.out.println(message);
            } else {
                sender.addChatMessage(new ChatComponentTranslation(message));
            }
        }

        String stringMessage = getLineString(OptiServer.getInstance().tpsStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(stringMessage));

        String tpsMessage = "|";
        int counter = 0;
        int missCounter = 0;
        OptiServer os = OptiServer.getInstance();
        for (Date date : os.tpsStatsMap.keySet()) {
            if (os.tpsStatsMap.keySet().size() > count) {
                if (missCounter < os.tpsStatsMap.keySet().size() - count) {
                    missCounter++;
                    continue;
                }
            }
            int tps = os.tpsStatsMap.get(date).intValue();
            tpsMessage += (OptiServerUtils.getTwoSymbolsNumber(tps) + "|");

            counter++;
            if (counter >= count) {
                break;
            }
        }
        sender.addChatMessage(new ChatComponentTranslation(tpsMessage));

        sender.addChatMessage(new ChatComponentTranslation(stringMessage));

        String hours = getHoursMessage(os.tpsStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(hours));

        String minutes = getMinutesMessage(os.tpsStatsMap, count);
        sender.addChatMessage(new ChatComponentTranslation(minutes));
    }

    private void cmdDuplicates(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
        sender.addChatMessage(new ChatComponentTranslation("DUPLICATES:"));

        //List<String> duplicates = WorldEventHandler.findDuplicates();
        List<String> duplicates = new ArrayList<String>();
        OptiServerUtils.findDuplicates0(duplicates);
        for (String s : duplicates) {
            sender.addChatMessage(new ChatComponentTranslation(s));
        }

        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
    }

    /**
     * Проверить сколько из существующих etity, во всех мирах
     * может быть удалено очистко
     */
    private void cmdEntityDebug(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));

        Map<Class, Integer> entitiesMap = new HashMap<Class, Integer>();
        List<Class> persist = new ArrayList<Class>();
        long wETotal = 0;//всего существ во всех мирах
        long wPETotal = 0; //количетсво существ которые не будет удалять очистка
        long wUETotal = 0; //будут удалины очисткой
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

        sender.addChatMessage(new ChatComponentTranslation("ENTITIES REMOVE DEBUG:"));
        sender.addChatMessage(new ChatComponentText("Total Entities Count in All Worlds: " +  wETotal));
        //список классов существ которые будут удалены при очистке
        for (Map.Entry<Class, Integer> e : entitiesMap.entrySet()) {
            int count = e.getValue();
            sender.addChatMessage(new ChatComponentTranslation(e.getKey().getSimpleName() + " " + count));
        }        
        if (wUETotal > 0 || entitiesMap.size() > 0) {            
            sender.addChatMessage(new ChatComponentText(String.format("Total toClean Objects: %s Classes: %s", wUETotal, entitiesMap.size())));
        }
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
        //классы существ которые не удаляет очистка
        sender.addChatMessage(new ChatComponentText("- PERSIST ENTITIES:"));
        for (int i = 0; i < persist.size(); i++) {
            Class cl = persist.get(i);
            sender.addChatMessage(new ChatComponentText(cl.getCanonicalName()));
        }
        sender.addChatMessage(new ChatComponentText("Total Persist Entities: " + wPETotal));
        sender.addChatMessage(new ChatComponentText("-------------------"));
    }

    private void cmdHoppers(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            for (Object obj : ws.loadedTileEntityList) {
                if (obj instanceof TileEntityHopper) {
                    sender.addChatMessage(new ChatComponentTranslation(obj.toString()));
                }
            }
        }
    }

    private void cmdStatus(ICommandSender sender, String[] args) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));

        int itemsOnGround = 0;
        int mobsAlive = 0;
        int friendlyAlive = 0;
        int customNpcs = 0;
        int playersAlive = 0;
        int chunksLoaded = 0;
        int activeMobSpawners = 0;
        int hoppers = 0;
        int activeHoppers = 0;
        int projectile = 0;
        int otherEntity = 0;

        //Map<String, Integer> friendlyMobsMap = new HashMap<String, Integer>();
        long chTotalEntities = 0;
        long wTotalTE = 0;
        long chTotalTE = 0;
        int worlds = 0;
        boolean showOther = hasOpt(args, "-show-other");

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            worlds++;
            //лист существ зарегистрированных миром, отличается от листов в чанке должен синхронизироваться
            
            wTotalTE += ws.loadedTileEntityList.size();
            
            // for check sync entity count via world & chunks
            List chunksList = ws.theChunkProviderServer.loadedChunks;
            for (Object oc : chunksList) {
                Chunk chunk = (Chunk)oc;
                chTotalEntities += OptiServerUtils.getEntityCountAtChunk(chunk);
                chTotalTE += chunk.chunkTileEntityMap.size();
            }

            chunksLoaded += chunksList.size();
            for (Object obj : ws.loadedEntityList) {
                if (obj instanceof EntityItem) {
                    itemsOnGround++;
                } else if (obj instanceof IProjectile) {
                    projectile++;
                } else if (obj instanceof EntityMob) {
                    mobsAlive++;
                } else if (obj instanceof EntityPlayer) {
                    playersAlive++;
                } else if (obj instanceof EntityLiving) {
                    String className = obj.getClass().getSimpleName();
                    if (className.equals("EntityCustomNpc")) {
                        customNpcs++;
                    } else {
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
                } else {
                    otherEntity++;
                    if (showOther) {
                        if (otherEntity==1) {
                            sender.addChatMessage(new ChatComponentText("--- OTHER ENITIES ---"));
                        }
                        sender.addChatMessage(new ChatComponentText(obj.getClass().getCanonicalName()));
                    }
                }
            }

            // TODO
            for (Object obj : ws.loadedTileEntityList) {
                if (obj instanceof TileEntityMobSpawner) {
                    activeMobSpawners++;
                } else if (obj instanceof TileEntityHopper) {
                    hoppers++;
                    TileEntityHopper hopper = (TileEntityHopper) obj;
                    int itemsCount = 0;
                    for (int i = 0; i < hopper.getSizeInventory(); i++) {
                        if (hopper.getStackInSlot(i) != null) {
                            itemsCount++;
                        }
                    }
                    if (itemsCount > 0) {
                        activeHoppers++;
                    }
                }
            }
        }
        
        if (showOther && otherEntity>0) {
            sender.addChatMessage(new ChatComponentText("-------------------"));
        }

        sender.addChatMessage(new ChatComponentTranslation("Items on the ground: " + itemsOnGround));
        sender.addChatMessage(new ChatComponentTranslation("Mobs alive: " + mobsAlive));
        sender.addChatMessage(new ChatComponentTranslation("Friendly-mobs alive: " + friendlyAlive));
        sender.addChatMessage(new ChatComponentTranslation("Custom NPCs alive: " + customNpcs));
        sender.addChatMessage(new ChatComponentTranslation("Projectile: " + projectile));
        sender.addChatMessage(new ChatComponentTranslation("OtherEntity: " + otherEntity));
        sender.addChatMessage(new ChatComponentTranslation("Players alive: " + playersAlive));
        sender.addChatMessage(new ChatComponentTranslation("Chunks loaded: " + chunksLoaded));
        sender.addChatMessage(new ChatComponentTranslation("Active hoppers: " + activeHoppers));
        sender.addChatMessage(new ChatComponentTranslation("Idle hoppers: " + (hoppers - activeHoppers)));
        sender.addChatMessage(new ChatComponentTranslation("Active mob spawners: " + activeMobSpawners));

        sender.addChatMessage(new ChatComponentText("-------------------"));
        sender.addChatMessage(new ChatComponentText("Totals (All Worlds)"));
        int wETotal = playersAlive + itemsOnGround + mobsAlive + friendlyAlive + customNpcs + projectile + otherEntity;

        sender.addChatMessage(new ChatComponentText("Worlds:   " + worlds));
        sender.addChatMessage(new ChatComponentText("Entities(chunks): " + chTotalEntities));
        sender.addChatMessage(new ChatComponentText("Entities(worlds): " + wETotal));
        sender.addChatMessage(new ChatComponentText("TE(worlds): " + wTotalTE));
        sender.addChatMessage(new ChatComponentText("TE(chunks): " + chTotalTE));
        
        if (chTotalEntities != wETotal) {
            //показывать только если число не сошлось
            sender.addChatMessage(new ChatComponentText("#DIFF# TotalEntities chunks/worlds "+
                    chTotalEntities +"/"+wETotal +" ("+(chTotalEntities-wETotal)+")"));
        }
        //todo acessors track...
        sender.addChatMessage(new ChatComponentText("-------------------"));
        /*
        sender.addChatMessage(new ChatComponentTranslation("FRIENDLY DEBUG:"));
        for (Map.Entry e : friendlyMobsMap.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        */
    }

    private void cmdMem(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("------ MEMORY -----"));

        sender.addChatMessage(new ChatComponentTranslation("Max:   " + OptiServerUtils.getMaxMemoryMB() + " MB"));
        sender.addChatMessage(new ChatComponentTranslation("Total: " + OptiServerUtils.getTotalMemoryMB() + " MB"));
        sender.addChatMessage(new ChatComponentTranslation("Used:  " + OptiServerUtils.getUsedMemMB() + " MB"));
        sender.addChatMessage(new ChatComponentTranslation("Free:  " + OptiServerUtils.getFreeMemMB() + " MB"));

        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
    }
    
    /**
     * флаг '-now' позволяет вызвать очистку без установленного в конфиге ожидания
     * после оповещения (очисти сейчас)
     */
    private void cmdClear(String[] args) {
        boolean clearNow = hasOpt(args, "-now");
        int last = ConfigHelper.secBeforeClean;
        if (clearNow) {
            ConfigHelper.secBeforeClean = 3;
        }

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            if (dimensionId == 0) {
                WorldEventHandler.getInstance().setCleanCause("Command");
                WorldEventHandler.getInstance().sheduleClean();
            }
        }

        if (clearNow) {
            ConfigHelper.secBeforeClean = last;
        }
    }

    private void cmdLargest(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));

        List<String> mostHeavyChunks = new ArrayList<String>();

        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            for (Object obj : ws.theChunkProviderServer.loadedChunks) {
                Chunk chunk = (Chunk) obj;
                int entitiesCount = 0;
                for (List l : chunk.entityLists) {
                    for (Object o : l) {
                        entitiesCount++;
                    }
                }
                mostHeavyChunks.add("DIM" + dimensionId + " X:" + chunk.xPosition + " (" + chunk.xPosition * 16 + ") Z:" + chunk.zPosition + " (" + chunk.zPosition * 16 + ")|" + entitiesCount);
            }
        }
        mostHeavyChunks.sort(new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return Integer.parseInt(s2.split("\\|")[1]) - Integer.parseInt(s1.split("\\|")[1]);
            }
        });
        int showChunks = 5;
        for (int i = 0; i < showChunks; i++) {
            String[] parts = mostHeavyChunks.get(i).split("\\|");
            sender.addChatMessage(new ChatComponentTranslation(parts[1] + " entities at chunk in " + parts[0]));
        }
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
    }

    private void cmdChunks(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
        int overallChunksCount = 0;
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            int chunksCount = ws.theChunkProviderServer.loadedChunks.size();
            overallChunksCount += chunksCount;
            sender.addChatMessage(new ChatComponentTranslation("World: DIM" + dimensionId + " Chunks: " + chunksCount));
        }
        sender.addChatMessage(new ChatComponentTranslation("Overall chunks: " + overallChunksCount));
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
    }

    private void cmdTPS(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
        double averageTps = 0;
        for (WorldServer ws : MinecraftServer.getServer().worldServers) {
            int dimensionId = ws.provider.dimensionId;
            double worldTickTime =  OptiServerUtils.mean(MinecraftServer.getServer().worldTickTimes.get(dimensionId)) * 1.0E-6D;
            double worldTPS = Math.min(1000.0 / worldTickTime, 20);

            sender.addChatMessage(new ChatComponentTranslation("World: DIM" + dimensionId + " TPS: " + worldTPS));
            averageTps += worldTPS;
        }

        averageTps = averageTps / MinecraftServer.getServer().worldServers.length;
        sender.addChatMessage(new ChatComponentTranslation("Total TPS: " + averageTps));
        sender.addChatMessage(new ChatComponentTranslation("-------------------"));
    }

    public void cmdReload(ICommandSender sender, String[] args) {
        ConfigHelper.reload();
        OptiServerUtils.cmdClassFieldInteract(sender, new String[] {"os","list"}, ConfigHelper.class, null, "os reload", /*Can edit*/false, true);
    }

    /**
     * Для указанной точки координат рисуем примитивную карту для соседних чанков
     * узнать загружены они в память или нет
     * @param args 1й аргумент координата блока по x второй по z блока а не чанка
     * @return
     */
    public void cmdUpLoadedChunksMap(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        if (sender == null || argsCount == 0) return;

        WorldServer ws = (WorldServer) sender.getEntityWorld();
        int pointX = 0, pointZ = 0,       //вводимая координата из аргрументов - точка на карте
            senderChX = 0, senderChZ = 0; //чанк в котором стоит отправитель команды
        
        boolean hasPoint = false;
        if (args.length > 2) {
            //флаг того что указаны координаты блока а не чанка - их нужно конвертировать
            boolean needConvert = hasOpt(args, "-block");
            pointX = stringToChunkCoord(args, 1, needConvert);
            pointZ = stringToChunkCoord(args, 2, needConvert);
            hasPoint = true;
        }

        if (sender instanceof EntityPlayer) {
            senderChX = (int) (((EntityPlayer) sender).posX)>>4;
            senderChZ = (int) (((EntityPlayer) sender).posZ)>>4;
            if (!hasPoint) {
                //центр карты будет положение написавшего запрос если точка не задана
                pointX = senderChX;
                pointZ = senderChZ;
            }
        } 
        //запрос из консоли центр карты - итересующая точка
        else {
            if (args.length < 3) {
                sender.addChatMessage(new ChatComponentTranslation("map <x> <z> -block | map <chunk_x> <chunk_z>"));
                return;
            }
        }

        //размеры карты
        final int rw = 30;//радиус ширины
        final int rh = 7;//радиус высоты

        StringBuilder sb = new StringBuilder(rw*rh*4 + rh);
        sender.addChatMessage(new ChatComponentTranslation(EnumChatFormatting.YELLOW + "  ============ MAP ============"));
        //выбор от какой точки рисовать центр карты
        //центр карты от указанной точки
        final int nz = pointZ - rh;//top (north)
        final int sz = pointZ + rh;//bottom (soutch)
        final int wx = pointX - rw;//left (west)
        final int ex = pointX + rw;//rigth (east)
        sender.addChatMessage(new ChatComponentTranslation(
                "  NW: " + nz + ":" + wx + "  SE: " + sz + ":" + ex + //края карты topleft bottomrigth
                "  Sender: "+senderChX + ":" + senderChZ + //чанк в котором стоит отправивший команду
                "  Point: "+pointX+":"+pointZ)); //интересующая точка корды которой указаны через аргументы
        boolean reset = false;
        boolean pointOutOfBounds = true;
        int count = 0;
        IChunkProvider chunkProvider = ws.getChunkProvider();
        for (int z = nz; z < sz; z++) {
            for (int x = wx; x < ex; x++) {

                /* Данный метод проверяет именно из листа загруженных чанков
                   68: return this.loadedChunkHashMap.containsItem(ChunkCoordIntPair.chunkXZ2Int(x, z));  */
                boolean loaded = chunkProvider.chunkExists(x, z);

                char c = '-'; //чанка нет в памяти

                if (x == senderChX && z == senderChZ) {
                    //указанная точка на карте совпадает с чанком отправителя
                    if (x == pointX && z== pointZ) {
                        sb.append(EnumChatFormatting.GOLD);
                        pointOutOfBounds = false;
                        reset = true;
                    }
                    c = '@'; //чанк в котором стоит отправивший команду
                }
                else if (x == pointX && z== pointZ) {
                    //если провайдер чанков говорит что он не загружен - отобразить красным иначе зелёным
                    sb.append(loaded ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);
                    c = 'X';//чанк по указанной координате "интересующая точка"
                    pointOutOfBounds = false;
                    reset = true;
                }
                else if (loaded) {
                    //sb.append(EnumChatFormatting.GREEN);
                    c = '#';
                    count++; //количество отображенных на карте загруженных чанков
                    //todo цветом можно показывать градацию нагрузки на чанк
                }

                sb.append(c);
                if (reset) {
                    sb.append(EnumChatFormatting.RESET);
                    reset = false;
                }
            }
            sender.addChatMessage(new ChatComponentTranslation(sb.toString()));
            sb.setLength(0);
        }

        int all = chunkProvider.getLoadedChunkCount();
        sender.addChatMessage(new ChatComponentTranslation("Showed Loaded Chunks: " + count +"/"+all));
        if (pointOutOfBounds) {
            sender.addChatMessage(new ChatComponentTranslation("Point out of map bound"));
        }
    }
    /**
     * Проверяем существ в заданном чанке
     * По координатам чанка 
     * - (поиск с одинаковыми идишниками) на данный момент только выводит лист кратких свойст Entity в данном чанке
     * - общее количество тайл-энтити в чанке
     * @param sender
     * @param args 
     */
    public void cmdChunkInfo(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        if (sender == null) return;
        boolean here = hasOpt(args, "-here") && sender instanceof EntityPlayer;
        if (!here && argsCount < 3 ) {
            sender.addChatMessage(new ChatComponentTranslation("chunk <chunkX> <chunkZ> [-here]"));
        } else {
            int chX, chZ;
            if (here) { // os chunk -here  
                chX = MathHelper.floor_double(((EntityPlayer)sender).posX / 16.0D);
                chZ = MathHelper.floor_double(((EntityPlayer)sender).posZ / 16.0D);
            }
            else {
                boolean needConvert = hasOpt(args, "-block");//флаг того что указаны координаты блока а не чанка - их нужно конвертировать
                chX = stringToChunkCoord(args, 1, needConvert);
                chZ = stringToChunkCoord(args, 2, needConvert);
            }
            //выводить полное имя класса существа
            boolean shortClassName = hasOpt(args, "-short-name");
            //if (sender.getEntityWorld().chunkExists
            //принимает координаты чанка а не блока в чанке!
            boolean loaded = sender.getEntityWorld().getChunkProvider().chunkExists(chX, chZ); // ChunkProviderServer
            /* вот тут еще большие вопросы он его достанет из диска или с памяти!*/
            Chunk chunk = !loaded ? null : sender.getEntityWorld().getChunkFromChunkCoords(chX, chZ);
            if (chunk == null) {
                sender.addChatMessage(new ChatComponentTranslation("Chunk not loaded  "+chX+":"+chZ));
            } 
            else {
                List[] aList = chunk.entityLists;
                if (aList != null) {
                    StringBuilder sb = new StringBuilder();
                    boolean empty = true;
                    for (int i = 0; i < aList.length; i++) {
                        List list = aList[i];
                        if (list != null && !list.isEmpty()) {
                            sender.addChatMessage(new ChatComponentTranslation("Chunk list #" + i));

                            for (int j = 0; j < list.size(); j++) {
                                Object el = list.get(j);
                                if (el instanceof Entity) {
                                    Entity e = (Entity) el;
                                    //индекс существа в листе чанка и инфа о существе
                                    OptiServerUtils.appendEntityInfo(e, !shortClassName, sb.append(j).append(' '));
                                    sender.addChatMessage(new ChatComponentText(sb.toString()));
                                    sb.setLength(0);
                                    empty = false;
                                }
                            }
                        }
                    }
                    if (empty) {
                        sender.addChatMessage(new ChatComponentText("EntitiesList: empty"));
                    }
                }
                sender.addChatMessage(new ChatComponentTranslation("TileEntity Count: " + chunk.chunkTileEntityMap.size()));
                //todo лист тайлов подробный
            }
        }
    }

    /**
     * Взаимодействие со списками существ и вещей которые нельзя удалять при очистке (persists*)
     * и списками существ, которые не проверяются при EntityJoinWorldEvent (ignoreSpawnCheck)
     */
    private void cmdPersistsEntityAndItems(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        if (argsCount <= 1) {
            sender.addChatMessage(new ChatComponentTranslation("os persists [list|add|remove] [Item-id|Entity-ClassName] [-allow-swarm-spawn]"));
            return;
        }
        String cmd = args[1];
        String response = "?";
        /*работать с листом существ спавн которых не проверяется на дублирование*/
        
        if (isCmd(cmd, "list", "l")) {
            sender.addChatMessage(new ChatComponentText("-------------------"));
            sender.addChatMessage(new ChatComponentText("---- Entities -----"));
            //классы существ которые не проверяются на дублирование при спавне
            sender.addChatMessage(new ChatComponentText("- AllowSwarmSpawn -"));
            final int sz = ConfigHelper.allowedSwarmSpawnEntitiesList.size();
            for (int i = 0; i < sz; i++) {
                sender.addChatMessage(new ChatComponentText(ConfigHelper.allowedSwarmSpawnEntitiesList.get(i).getCanonicalName()));
            }

            //существа которые не не удаляемые при очистке
            sender.addChatMessage(new ChatComponentText("---- Persistens -----"));
            final int sz2 = ConfigHelper.persistEntitiesList.size();
            for (int i = 0; i < sz2; i++) {
                sender.addChatMessage(new ChatComponentText(ConfigHelper.persistEntitiesList.get(i).getCanonicalName()));
            }

            //items все вещи которые не удаляются при очистке
            List list2 = ConfigHelper.persistItemsList;
            if (list2 != null && !list2.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("-- Persists Items ---"));
                for (int i = 0; i < list2.size(); i++) {
                    Item item = (Item)list2.get(i);                    
                    sender.addChatMessage(new ChatComponentText(OptiServerUtils.getItemInfo(item)));
                }
            }
            sender.addChatMessage(new ChatComponentText("-------------------"));
            return;
        }
        // add | remove
        else {
            if (argsCount < 3) {
                sender.addChatMessage(new ChatComponentText("Need Arg3: full canonical class name or (int) ItemId"));
            }
            else {
                String clName = args[2];
                /*добавлять по имени класса в лист в allowedSwarmSpawnEntitiesList a не persistEntitiesList*/
                boolean forAllowSwarmSpawn = hasOpt(args, "-allow-swarm-spawn");

                if (isCmd(cmd, "add", "a")) {
                    //добавить вещь - если указано число
                    if (OptiServerUtils.isDigitsOnly(clName)) {
                        //ConfigHelper.persistItemsList
                        int id = Integer.parseInt(clName);
                        if (id > 0) {
                            Item item = Item.getItemById(id);
                            if (item == null) {
                                response = "not found";
                            } else {
                                if (!ConfigHelper.persistItemsList.contains(item)) {
                                    ConfigHelper.persistItemsList.add(item);
                                    response = OptiServerUtils.getItemInfo(item);
                                } else {
                                    response = "Already contained in the list " + id;
                                }
                            }
                        }
                    }
                    //добавить существо по полному имени класса
                    else {
                        Class cl = OptiServerUtils.getClassForName(clName);
                        if (cl != null) {
                            List<Class> list = forAllowSwarmSpawn
                                    ? ConfigHelper.allowedSwarmSpawnEntitiesList
                                    : ConfigHelper.persistEntitiesList;

                            if (forAllowSwarmSpawn) {
                                sender.addChatMessage(new ChatComponentText("- AllowSwarmSpawn -"));
                            }

                            if (!list.contains(cl)) {
                                list.add(cl);
                                response = "Entity Class added to list";
                            } else {
                                response = "Entity Class Already contains";
                            }
                        } else {
                            response = "Entity Class not found: "+ clName;
                        }
                    }
                }
                else if (isCmd(cmd, "remove", "r")) {
                    //remove item object
                    if (OptiServerUtils.isDigitsOnly(clName)) {
                        int id = Integer.parseInt(clName);
                        if (id > 0) {
                            Item item = Item.getItemById(id);
                            if (item != null) {
                                boolean removed = ConfigHelper.persistItemsList.remove(item);
                                response = "Item "+(removed? "Removed" : "Not contains");
                            } else {
                                response = "not found " + id +" "+clName;
                            }
                        }
                    }
                    //remove entity class
                    else {
                        Class cl = OptiServerUtils.getClassForName(clName);
                        if (cl != null) {
                            List<Class> list = forAllowSwarmSpawn
                                    ? ConfigHelper.allowedSwarmSpawnEntitiesList
                                    : ConfigHelper.persistEntitiesList;

                            if (forAllowSwarmSpawn) {
                                sender.addChatMessage(new ChatComponentText("-- IgnoreSpawnCheck --"));
                            }
                            
                            response = "Entity Class " + (list.remove(cl) ? "Removed" : "Not contained");
                        } else {
                            response = "Entity Class not found: "+ clName;
                        }
                    }
                }
                //net.minecraft.entity.monster.EntitySpider
            }
        }
        sender.addChatMessage(new ChatComponentText(response));
    }
    /**
     * os chunk-obj cx cz (-block for world block coords)
     * @param sender
     * @param args
     */
    private void cmdChunkObj(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        /*для возможности быстро задать текущие координаты*/
        boolean here = hasOpt(args, "-here") && sender instanceof EntityPlayer;
        if (!here && argsCount < 3 ) {
            sender.addChatMessage(new ChatComponentTranslation("chunk <chunkX> <chunkZ> [-here]"));
            return;
        }

        int chX, chZ;
        if (here) { // os chunk -here
            chX = MathHelper.floor_double(((EntityPlayer)sender).posX / 16.0D);
            chZ = MathHelper.floor_double(((EntityPlayer)sender).posZ / 16.0D);
        }
        else {
            boolean needConvert = hasOpt(args, "-block");//флаг того что указаны координаты блока а не чанка - их нужно конвертировать
            chX = stringToChunkCoord(args, 1, needConvert);
            chZ = stringToChunkCoord(args, 2, needConvert);
        }

        Chunk chunk = OptiServerUtils.getLoadedChunkOfNull(sender.getEntityWorld(), chX, chZ);
        if (chunk != null) {
            String[] subArgs = OptiServerUtils.subArgsFrom(args, !here ? 2 : 1);//0 игнориться обычно там команда
            OptiServerUtils.cmdClassFieldInteract(sender, subArgs, Chunk.class, chunk, "os chunk-obj", /*readOnly*/true, false);
        } else {
            sender.addChatMessage(new ChatComponentText("not loaded chunk "+ chX+":"+chZ));
        }
    }
    
    public void cmdGetItemClassById(ICommandSender sender, String[] args) {
        boolean player = (sender instanceof EntityPlayer );
        int argsCount = argsCount(args);
        String response = "?";
        Item item = null;
        if (argsCount <= 1 && player) {
            ItemStack hIS = ((EntityPlayer)sender).getHeldItem();
            if (hIS != null) item = hIS.getItem();
        } else {
            int id = Integer.parseInt(args[1]);
            item = Item.getItemById(id);
        }
        response = (item == null)
                ? "not found"
                : "id: " + OptiServerUtils.getItemInfo(item); // id class unlocName
        sender.addChatMessage(new ChatComponentText(response));
    }

    /**
     * Получить обьект существа по его id | кордам мира | по кордам sender`a
     * Для найденого существа:
     *  - получть nbt
     *  - доступ к полям класса список интерфейсов
     *  - проверить будет ли существо удаляться при очистках (isEntityCanBeUnloaded)
     * Доступ к полям существа: просмотр редактирование
     * @param sender
     * @param args
     */
    public void cmdEntityObj(ICommandSender sender, String[] args) {
        boolean player = (sender instanceof EntityPlayer);
        int argsCount = argsCount(args);
        if (argsCount == 0) {
            sender.addChatMessage(new ChatComponentText("os entity-obj [-here|x y z|id #] [unload-check|nbt|edit|captured-drops]"));
        }
        boolean here = player && hasOpt(args, "-here");// os entity-obj -here edit
        Entity entity = null;
        int n = 0;
        if (here) {
            EntityPlayer pl = (EntityPlayer)sender;
            entity = OptiServerUtils.getEntityByPosition(pl.worldObj, pl.posX, pl.posY, pl.posZ, Arrays.asList(new Entity[]{pl}));
            n = 2;
        }
        else if (argsCount > 2 && isCmd(args[1], "id")) {
            //os entity-obj id 123 ? edit
            int id = OptiServerUtils.isDigitsOnly(args[2]) ? Integer.parseInt(args[2]) : 0;
            if (id > 0) {
                entity = OptiServerUtils.getEntityById(sender.getEntityWorld(), id);
            }
            n = 3;
        }
        else if (argsCount > 3) {
            //os entity-obj x y z edit
            double posX = Double.parseDouble(args[1]);
            double posY = Double.parseDouble(args[2]);
            double posZ = Double.parseDouble(args[3]);
            entity = OptiServerUtils.getEntityByPosition(sender.getEntityWorld(), posX, posY, posZ, null);
            n = 4;
        } else {
            sender.addChatMessage(new ChatComponentText("os entity-obj x y z"));
            return;
        }

        if (entity==null) {
            sender.addChatMessage(new ChatComponentText("not found"));
            return;
        }

        //вывести поля существа        
        String cmd = n < args.length ? args[n] : "";

        if (isCmd(cmd, "check", "is-unloadable")) {
            boolean b = OptiServerUtils.isEntityCanBeUnloaded(entity);
            sender.addChatMessage(new ChatComponentText( ("isEntityCanBeUnloaded: " + b) + "  "+entity.getClass().getCanonicalName()));
        }

        else if (isCmd(cmd, "captured-drops")) {
            sender.addChatMessage(new ChatComponentText("captureDrops" + (entity.captureDrops?'+':'-')));
            if (entity.capturedDrops != null) {
                List<EntityItem> list = entity.capturedDrops;
                for (int i = 0; i < list.size(); i++) {
                    EntityItem ei = list.get(i);
                    if (ei != null && ei.getEntityItem() != null) {
                        sender.addChatMessage(new ChatComponentText(ei.getEntityItem().toString()));
                    }
                }
            }
        }
        else if (isCmd(cmd, "nbt")) {
            NBTTagCompound nbtData = new NBTTagCompound();
            if (entity instanceof EntityLivingBase) {
                ((EntityLivingBase)entity).writeEntityToNBT(nbtData);
            } else {
                entity.writeToNBT(nbtData);
            }
            sender.addChatMessage(new ChatComponentText(nbtData.toString()));
        }
        //чтение-изменение полей инстанса, просмотр реализуемых интерфейсов класса
        else if (isCmd(cmd, "edit", "/")) {
            String[] subArgs = OptiServerUtils.subArgsFrom(args, n); //!here ? 3 : 1);//i
            OptiServerUtils.cmdClassFieldInteract(sender, subArgs, entity.getClass(), entity,"os entity-obj", /*ReadOnly:*/false, /*OnlyPublic*/false);
        }
        else if (isCmd(cmd, "inventory", "inv")) {
            sender.addChatMessage(new ChatComponentText("TODO"));
        }
        //краткие данные о существе
        else {
            sender.addChatMessage(new ChatComponentText(OptiServerUtils.appendEntityInfo(entity, player, new StringBuilder()).toString()));
        }
    }

    public void cmdClass(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        if (argsCount < 0) return;
        String className = args[1];

        //найти классы наследуемые от от указанного  [реализованные интерфейсы наследника искомого предка]
        if (isCmd(className,"extends", "has-parent") && argsCount >= 3) {
            Class parentClass = null;
            //boolean showIntegerfases = hasOpt(args, "-show-interfaces");
            try {
                parentClass = Class.forName(args[2]);
                sender.addChatMessage(new ChatComponentText("----- <" + parentClass + "> -----"));
                sender.addChatMessage(new ChatComponentText("----- Inherited Classes: ------"));
            }
            catch (ClassNotFoundException ex) {
                sender.addChatMessage(new ChatComponentText("Not found Class for " + args[2]));
                return;
            }
            Iterator it = OptiServerUtils.getAllClassesIteratorFor(Thread.currentThread().getContextClassLoader());
            if (it != null) {
                StringBuilder sb = new StringBuilder();
                while(it.hasNext()) {
                    Object obj = it.next();
                    if (obj instanceof Class) {
                        Class cl = ((Class)obj);                        
                        if (parentClass == cl.getSuperclass()) {
                            Class[] interfaces = cl.getInterfaces();
                            sb.append(cl.getCanonicalName()).append(" [");
                            if (interfaces != null && interfaces.length>0) {
                                for (int i = 0; i < interfaces.length; i++) {
                                    Class aInterface = interfaces[i];
                                    sb.append(aInterface.getCanonicalName()).append(' ');
                                }
                            }
                            sb.append("]");
                            sender.addChatMessage(new ChatComponentText(sb.toString()));
                            sb.setLength(0);
                        }
                    }
                }
            }
        }
        else if (isCmd(className,"has-interface", "implements") && argsCount >= 3) {
            //найти класы реализовывающие указанный интерфейс
            Class interfaceClass = null;
            try {
                interfaceClass = Class.forName(args[2]);
                sender.addChatMessage(new ChatComponentText("----- [" + interfaceClass + "] -----"));
                sender.addChatMessage(new ChatComponentText("----- Classes that Implement : -----")); 
            }
            catch (ClassNotFoundException ex) {
                sender.addChatMessage(new ChatComponentText("Not found Interface for " + args[2]));
                return;
            }

            Iterator it = OptiServerUtils.getAllClassesIteratorFor(Thread.currentThread().getContextClassLoader());
            if (it != null) {
                while(it.hasNext()) {
                    Object obj = it.next();
                    if (obj instanceof Class) {
                        Class cl = ((Class)obj);                        
                        Class[] interfaces = cl.getInterfaces();
                        if (interfaces!=null) {
                            for (int i = 0; i < interfaces.length; i++) {
                                Class aInterface = interfaces[i];
                                if (aInterface == interfaceClass) {
                                    sender.addChatMessage(new ChatComponentText(cl.getCanonicalName()));
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            sender.addChatMessage(new ChatComponentText("----- (" + className + ") -----"));
            sender.addChatMessage(new ChatComponentText(OptiServerUtils.getClassInfo(className)));
        }
    }


    private static final String JVM_USAGE = "<uptime/pid/args/system-prop/rtc>";
    private void cmdJVM(ICommandSender sender, String[] args) {
        int argsCount = argsCount(args);
        if (argsCount <= 1) {
            sender.addChatMessage(new ChatComponentText(JVM_USAGE));
            return;
        }
        String cmd = args[1];
        if (isCmd(cmd, "args")) {
            List<String> list2  = JVMUtils.getArgsList();
            sender.addChatMessage(new ChatComponentText("----- JVM-ARGS ------"));
            if (list2!=null) {
                for (int i = 0; i < list2.size(); i++) {
                    sender.addChatMessage(new ChatComponentText(list2.get(i)));
                }
            }
            sender.addChatMessage(new ChatComponentText("---------------------"));
        }
        else if (isCmd(cmd, "pid")) {
            sender.addChatMessage(new ChatComponentText("PID: "+ JVMUtils.getOwnPid()));
        }
        else if (isCmd(cmd, "uptime")) {
            long uptime = JVMUtils.getUpTime();
            sender.addChatMessage(new ChatComponentText(
                    OptiServerUtils.millisToReadable(uptime, "Uptime:")));
        }
        else if (isCmd(cmd, "runtime-mx-bean-class", "rtc")) {
            sender.addChatMessage(new ChatComponentText(JVMUtils.getRuntimeMXBeanClassName()));
        }
        else if (isCmd(cmd, "system-prop", "sp") && argsCount > 2) {
            String key = args[2];
            String value = JVMUtils.getSystemProperty(key);
            sender.addChatMessage(new ChatComponentText(value==null?"not found":value));
        }
        else if (isCmd(cmd, "histo") && argsCount > 2) {
            sender.addChatMessage(new ChatComponentText("todo"));
        }
    }


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
