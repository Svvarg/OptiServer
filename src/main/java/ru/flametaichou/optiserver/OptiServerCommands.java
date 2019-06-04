package ru.flametaichou.optiserver;

import java.util.*;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

public class OptiServerCommands extends CommandBase
{
    private final List<String> aliases;

    public OptiServerCommands()
    {
        aliases = new ArrayList<String>();
        aliases.add("optiserver");
        aliases.add("os");
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
        return "/optiserver <tps/chunks/clear/mem/largest/status>";
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
            if (argString.length == 0) {
                sender.addChatMessage(new ChatComponentText("/optiserver <tps/chunks/clear/mem/largest/status>"));
                return;
            }
            if (argString[0].equals("tps")) {

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
                return;
            }

            if (argString[0].equals("chunks")) {

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

                return;
            }

            if (argString[0].equals("largest")) {

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

                return;
            }

            if (argString[0].equals("clear")) {
                for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                    int dimensionId = ws.provider.dimensionId;
                    if (dimensionId == 0) {
                        Main.worldEventHandler.sheduleClean(ws);
                    }
                }
                return;
            }

            if (argString[0].equals("mem")) {

                sender.addChatMessage(new ChatComponentTranslation("-------------------"));

                sender.addChatMessage(new ChatComponentTranslation("Max memory: " + OptiServerUtils.getMaxMemoryMB() + "MB"));
                sender.addChatMessage(new ChatComponentTranslation("Total memory: " + OptiServerUtils.getTotalMemoryMB() + "MB"));
                sender.addChatMessage(new ChatComponentTranslation("Used memory: " + OptiServerUtils.getUsedMemMB() + "MB"));
                sender.addChatMessage(new ChatComponentTranslation("Free memory: " + OptiServerUtils.getFreeMemMB() + "MB"));

                sender.addChatMessage(new ChatComponentTranslation("-------------------"));
                return;
            }

            if (argString[0].equals("status")) {

                sender.addChatMessage(new ChatComponentTranslation("-------------------"));

                int itemsOnGround = 0;
                int mobsAlive = 0;
                int friendlyAlive = 0;
                int playersAlive = 0;
                int chunksLoaded = 0;
                int activeMobSpawners = 0;
                for (WorldServer ws : MinecraftServer.getServer().worldServers) {
                    chunksLoaded += ws.theChunkProviderServer.loadedChunks.size();
                    for (Object obj : ws.loadedEntityList) {
                        if (obj instanceof EntityItem) {
                            itemsOnGround++;
                        } else if (obj instanceof EntityMob) {
                            mobsAlive++;
                        } else if (obj instanceof EntityPlayer) {
                            playersAlive++;
                        } else if (obj instanceof EntityLiving) {
                            friendlyAlive++;
                        }
                    }

                    for (Object obj : ws.loadedTileEntityList) {
                        if (obj instanceof TileEntityMobSpawner) {
                            activeMobSpawners++;
                        }
                    }
                }

                sender.addChatMessage(new ChatComponentTranslation("Items on the ground: " + itemsOnGround));
                sender.addChatMessage(new ChatComponentTranslation("Mobs alive: " + mobsAlive));
                sender.addChatMessage(new ChatComponentTranslation("Friendly-mobs alive: " + friendlyAlive));
                sender.addChatMessage(new ChatComponentTranslation("Players alive: " + playersAlive));
                sender.addChatMessage(new ChatComponentTranslation("Chunks loaded: " + chunksLoaded));
                sender.addChatMessage(new ChatComponentTranslation("Active mob spawners: " + activeMobSpawners));

                sender.addChatMessage(new ChatComponentTranslation("-------------------"));

                return;
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
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] var1, int var2)
    {
        return false;
    }
}
