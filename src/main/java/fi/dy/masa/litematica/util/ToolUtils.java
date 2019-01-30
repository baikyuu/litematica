package fi.dy.masa.litematica.util;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class ToolUtils
{
    public static void fillSelectionVolumes(Minecraft mc, IBlockState state, @Nullable IBlockState stateToReplace)
    {
        if (mc.player != null && mc.player.capabilities.isCreativeMode)
        {
            final AreaSelection area = DataManager.getSelectionManager().getCurrentSelection();

            if (area != null && area.getAllSubRegionBoxes().size() > 0)
            {
                Box currentBox = area.getSelectedSubRegionBox();
                final ImmutableList<Box> boxes = currentBox != null ? ImmutableList.of(currentBox) : ImmutableList.copyOf(area.getAllSubRegionBoxes());

                if (mc.isSingleplayer())
                {
                    final WorldServer world = mc.getIntegratedServer().getWorld(mc.player.getEntityWorld().provider.getDimensionType().getId());

                    world.addScheduledTask(new Runnable()
                    {
                        public void run()
                        {
                            WorldUtils.setShouldPreventOnBlockAdded(true);

                            if (fillSelectionVolumesDirect(world, boxes, state, stateToReplace))
                            {
                                StringUtils.printActionbarMessage("litematica.message.area_filled");
                            }
                            else
                            {
                                StringUtils.printActionbarMessage("litematica.message.area_fill_fail");
                            }

                            WorldUtils.setShouldPreventOnBlockAdded(false);
                        }
                    });

                    StringUtils.printActionbarMessage("litematica.message.scheduled_task_added");
                }
                else if (fillSelectionVolumesCommand(boxes, state, stateToReplace, mc))
                {
                    StringUtils.printActionbarMessage("litematica.message.area_filled");
                }
                else
                {
                    StringUtils.printActionbarMessage("litematica.message.area_fill_fail");
                }
            }
            else
            {
                StringUtils.printActionbarMessage("litematica.message.error.no_area_selected");
            }
        }
    }

    public static boolean fillSelectionVolumesDirect(World world, Collection<Box> boxes, IBlockState state, @Nullable IBlockState stateToReplace)
    {
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        for (Box box : boxes)
        {
            BlockPos posMin = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            BlockPos posMax = PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());

            for (int z = posMin.getZ(); z <= posMax.getZ(); ++z)
            {
                for (int x = posMin.getX(); x <= posMax.getX(); ++x)
                {
                    for (int y = posMax.getY(); y >= posMin.getY(); --y)
                    {
                        posMutable.setPos(x, y, z);

                        if (stateToReplace == null || world.getBlockState(posMutable) == stateToReplace)
                        {
                            TileEntity te = world.getTileEntity(posMutable);

                            if (te instanceof IInventory)
                            {
                                ((IInventory) te).clear();
                            }

                            world.setBlockState(posMutable, state, 0x12);
                        }
                    }
                }
            }
        }

        return true;
    }

    public static boolean fillSelectionVolumesCommand(Collection<Box> boxes, IBlockState state, @Nullable IBlockState stateToReplace, Minecraft mc)
    {
        ResourceLocation rl = Block.REGISTRY.getNameForObject(state.getBlock());

        if (rl == null)
        {
            return false;
        }

        String blockName = rl.toString();
        String strCommand = blockName + " " + String.valueOf(state.getBlock().getMetaFromState(state));

        if (stateToReplace != null)
        {
            rl = Block.REGISTRY.getNameForObject(stateToReplace.getBlock());

            if (rl == null)
            {
                return false;
            }

            strCommand += " replace " + rl.toString();
        }

        for (Box box : boxes)
        {
            BlockPos posMin = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            BlockPos posMax = PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());

            String cmd = String.format("/fill %d %d %d %d %d %d %s",
                    posMin.getX(), posMin.getY(), posMin.getZ(),
                    posMax.getX(), posMax.getY(), posMax.getZ(), strCommand);

            mc.player.sendChatMessage(cmd);
        }

        return true;
    }

    public static void deleteSelectionVolumes(Minecraft mc, boolean removeEntities)
    {
        if (mc.player != null && mc.player.capabilities.isCreativeMode)
        {
            final AreaSelection area = DataManager.getSelectionManager().getCurrentSelection();

            if (area != null)
            {
                if (mc.isSingleplayer())
                {
                    final WorldServer world = mc.getIntegratedServer().getWorld(mc.player.getEntityWorld().provider.getDimensionType().getId());

                    world.addScheduledTask(new Runnable()
                    {
                        public void run()
                        {
                            Box currentBox = area.getSelectedSubRegionBox();
                            Collection<Box> boxes;

                            if (currentBox != null)
                            {
                                boxes = ImmutableList.of(currentBox);
                            }
                            else
                            {
                                boxes = area.getAllSubRegionBoxes();
                            }

                            if (deleteSelectionVolumes(world, boxes, removeEntities))
                            {
                                StringUtils.printActionbarMessage("litematica.message.area_cleared");
                            }
                            else
                            {
                                StringUtils.printActionbarMessage("litematica.message.area_clear_fail");
                            }
                        }
                    });

                    StringUtils.printActionbarMessage("litematica.message.scheduled_task_added");
                }
                else
                {
                    StringUtils.printActionbarMessage("litematica.message.only_works_in_single_player");
                }
            }
            else
            {
                StringUtils.printActionbarMessage("litematica.message.error.no_area_selected");
            }
        }
    }

    public static boolean deleteSelectionVolumes(World world, Collection<Box> boxes, boolean removeEntities)
    {
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState barrier = Blocks.BARRIER.getDefaultState();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        for (Box box : boxes)
        {
            BlockPos posMin = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
            BlockPos posMax = PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());

            for (int z = posMin.getZ(); z <= posMax.getZ(); ++z)
            {
                for (int x = posMin.getX(); x <= posMax.getX(); ++x)
                {
                    for (int y = posMax.getY(); y >= posMin.getY(); --y)
                    {
                        posMutable.setPos(x, y, z);
                        TileEntity te = world.getTileEntity(posMutable);

                        if (te instanceof IInventory)
                        {
                            ((IInventory) te).clear();
                            world.setBlockState(posMutable, barrier, 0x12);
                        }

                        world.setBlockState(posMutable, air, 0x12);
                    }
                }
            }

            if (removeEntities)
            {
                AxisAlignedBB bb = PositionUtils.createEnclosingAABB(posMin, posMax);
                List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, bb);

                for (Entity entity : entities)
                {
                    if ((entity instanceof EntityPlayer) == false)
                    {
                        entity.setDead();
                    }
                }
            }
        }

        return true;
    }
}