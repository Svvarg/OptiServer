package ru.flametaichou.optiserver.handlers;

import com.google.common.collect.Lists;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EntitySpawnHandler {

    private List<Integer> removedEntitiesList = new ArrayList<Integer>();

    private static List<String> unloadBlacklist = Arrays.asList(
            new String[] {
                    "ru.flamesword.ordinaryores.entities.EntityUndeadSpidy",
                    "ru.flamesword.ordinaryores.entities.EntitySprout",
                    "net.daveyx0.primitivemobs.entity.monster.EntityDMinion"
            }
    );

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntityJoinWorldEvent event) {

        if (event.entity instanceof EntityItem
                || event.entity instanceof EntityXPOrb
                || event.entity instanceof IProjectile
                || (event.entity instanceof EntityAgeable && ((EntityAgeable) event.entity).isChild())
                || unloadBlacklist.contains(event.entity.getClass().getName())) {
            return;
        }

        List<Entity> duplicates = OptiServerUtils.findDuplicates(event.entity);
        if (!duplicates.isEmpty()) {
            for (Entity duplicate : duplicates) {
                if (!removedEntitiesList.contains(duplicate.getEntityId())) {
                    removedEntitiesList.add(duplicate.getEntityId());
                    Logger.log(String.format("Unloading duplicated entity: %s (%s)", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity)));
                    OptiServerUtils.unloadEntity(duplicate);
                } else {
                    Logger.error(String.format("Entity already removed: %s (%s)", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity)));
                }
            }
        }
    }
}
