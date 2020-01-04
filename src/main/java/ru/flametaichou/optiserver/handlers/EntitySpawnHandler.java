package ru.flametaichou.optiserver.handlers;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import ru.flametaichou.optiserver.util.Logger;
import ru.flametaichou.optiserver.util.OptiServerUtils;

public class EntitySpawnHandler {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntityJoinWorldEvent event) {

        if (event.entity instanceof EntityItem || event.entity instanceof EntityXPOrb || event.entity instanceof IProjectile || (event.entity instanceof EntityAgeable && ((EntityAgeable) event.entity).isChild())) {
            return;
        }

        if (OptiServerUtils.isDuplicate(event.entity)) {
            Logger.log(String.format("Unloading duplicated entity on spawn: %s (%s)", event.entity.getCommandSenderName(), Logger.getCoordinatesString(event.entity)));
            OptiServerUtils.unloadEntity(event.entity);
        }
    }
}
