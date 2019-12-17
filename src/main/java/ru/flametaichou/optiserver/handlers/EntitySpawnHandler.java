package ru.flametaichou.optiserver.handlers;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;

import java.util.List;

public class EntitySpawnHandler {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntityJoinWorldEvent event) {

        if (event.entity instanceof EntityItem || event.entity instanceof EntityXPOrb || event.entity instanceof IProjectile || (event.entity instanceof EntityAnimal && ((EntityAnimal) event.entity).isChild())) {
            return;
        }

        int radius = 1;
        List nearestEntities = event.entity.worldObj.getEntitiesWithinAABB(
                event.entity.getClass(),
                AxisAlignedBB.getBoundingBox(
                        event.entity.posX-radius,
                        event.entity.posY-radius,
                        event.entity.posZ-radius,
                        (event.entity.posX + radius),
                        (event.entity.posY + radius),
                        (event.entity.posZ + radius)
                )
        );

        if (!nearestEntities.isEmpty()) {
            for (Object nearestEntity : nearestEntities) {
                Entity e = (Entity) nearestEntity;
                if (e.getCommandSenderName().equals(event.entity.getCommandSenderName())
                        && e.getEntityId() != event.entity.getEntityId()
                        && OptiServerUtils.approximatelyEquals(e.posX, event.entity.posX)
                        && OptiServerUtils.approximatelyEquals(e.posY, event.entity.posY)
                        && OptiServerUtils.approximatelyEquals(e.posZ, event.entity.posZ)) {

                    Logger.log(String.format("Unloading breding entity on spawn: %s (%s)", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity)));
                    OptiServerUtils.unloadEntity(event.entity);
                    break;
                }
            }
        }
    }
}
