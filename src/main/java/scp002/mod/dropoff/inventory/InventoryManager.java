package scp002.mod.dropoff.inventory;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.*;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import scp002.mod.dropoff.config.DropOffConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryManager {

    private final EntityPlayerMP player;
    private final World world;

    public InventoryManager(EntityPlayerMP player) {
        this.player = player;
        world = player.getEntityWorld();
    }

    EntityPlayerMP getPlayer() {
        return player;
    }

    public <T extends TileEntity & IInventory> List<InventoryData> getNearbyInventories() {
        int minX = (int) (player.posX - DropOffConfig.INSTANCE.scanRadius);
        int maxX = (int) (player.posX + DropOffConfig.INSTANCE.scanRadius);

        int minY = (int) (player.posY - DropOffConfig.INSTANCE.scanRadius);
        int maxY = (int) (player.posY + DropOffConfig.INSTANCE.scanRadius);

        int minZ = (int) (player.posZ - DropOffConfig.INSTANCE.scanRadius);
        int maxZ = (int) (player.posZ + DropOffConfig.INSTANCE.scanRadius);

        List<InventoryData> inventoryDataList = new ArrayList<>();

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    TileEntity currentEntity = world.getTileEntity(x, y, z);

                    InventoryData currentInvData;

                    if (currentEntity instanceof IInventory) {
                        //noinspection unchecked
                        currentInvData = getInventoryData((T) currentEntity);
                    } else if (currentEntity instanceof TileEntityEnderChest) {
                        currentInvData = getInventoryData((TileEntityEnderChest) currentEntity);
                    } else {
                        continue;
                    }

                    int listSize = inventoryDataList.size();

                    if (listSize > 0) {
                        InventoryData previousInvData = inventoryDataList.get(listSize - 1);

                        // Check for duplicates generated from double chests.
                        if (previousInvData.getEntities().contains(currentEntity)) {
                            continue;
                        }
                    }

                    if (currentInvData.getInventory().isUseableByPlayer(player) && isInventoryValid(currentInvData)) {
                        inventoryDataList.add(currentInvData);
                    }
                }
            }
        }

        return inventoryDataList;
    }

    boolean isStacksEqual(ItemStack left, ItemStack right) {
        NBTTagCompound leftTag = left.getTagCompound();
        NBTTagCompound rightTag = right.getTagCompound();

        return left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage() &&
                ((leftTag == null && rightTag == null) || (leftTag != null && leftTag.equals(rightTag)));
    }

    /**
     * This method returns the name of the block that appears in the tooltip when you move the mouse over the item that
     * corresponds to it.
     */
    String getItemStackName(IInventory inventory) {
        if (inventory instanceof InventoryLargeChest) {
            return Block.getBlockById(54).getLocalizedName();
        }

        if (inventory instanceof TileEntityBrewingStand) {
            return StatCollector.translateToLocal(Item.getItemById(379).getUnlocalizedName() + ".name");
        }

        if (inventory instanceof TileEntity) {
            TileEntity entity = (TileEntity) inventory;
            ItemStack itemStack = new ItemStack(entity.getBlockType(), 1, entity.getBlockMetadata());

            return itemStack.getDisplayName();
        }

        return StatCollector.translateToLocal(inventory.getInventoryName());
    }

    int getMaxAllowedStackSize(IInventory inventory, ItemStack stack) {
        return Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());
    }

    /**
     * This method checks the config to determine whether to process the inventory of the specified type or not.
     */
    private boolean isInventoryValid(InventoryData inventoryData) {
        TileEntity entity = inventoryData.getEntities().get(0);

        if (entity instanceof TileEntityBeacon) {
            return DropOffConfig.INSTANCE.checkBeacons;
        }

        if (entity instanceof TileEntityBrewingStand) {
            return DropOffConfig.INSTANCE.checkBrewingStands;
        }

        if (entity instanceof TileEntityChest) {
            return DropOffConfig.INSTANCE.checkChests;
        }

        if (entity instanceof TileEntityDispenser) {
            if (entity instanceof TileEntityDropper) {
                return DropOffConfig.INSTANCE.checkDroppers;
            }

            return DropOffConfig.INSTANCE.checkDispensers;
        }

        if (entity instanceof TileEntityEnderChest) {
            return DropOffConfig.INSTANCE.checkEnderChests;
        }

        if (entity instanceof TileEntityFurnace) {
            return DropOffConfig.INSTANCE.checkFurnaces;
        }

        if (entity instanceof TileEntityHopper) {
            return DropOffConfig.INSTANCE.checkHoppers;
        }

        String inventoryName = getItemStackName(inventoryData.getInventory());

        return isInventoryNameValid(inventoryName) || DropOffConfig.INSTANCE.dropOffEveryPlace;
    }

    /**
     * This method checks the config text field to determine whether to process the inventory with the specified name
     * or not.
     */
    private boolean isInventoryNameValid(String name) {
        String[] containerNames =
                StringUtils.split(DropOffConfig.INSTANCE.processContainersWithNames, DropOffConfig.INSTANCE.delimiter);

        for (String containerName : containerNames) {
            String regex = containerName.replace("*", ".*").trim();

            if (name.matches(regex)) {
                return true;
            }
        }

        return false;
    }

    // Implemented without a loop, because the order of the arguments in the "new InventoryLargeChest()" is important.
    private <T extends TileEntity & IInventory> InventoryData getInventoryData(T leftEntity) {
        List<TileEntity> entities = new ArrayList<>();

        if (leftEntity instanceof TileEntityChest) {
            String chestName = "container.chestDouble";

            TileEntity rightEntity = world.getTileEntity(leftEntity.xCoord - 1, leftEntity.yCoord,
                    leftEntity.zCoord);

            // ----------------------------------------Check for trapped chests-----------------------------------------
            if (leftEntity.getBlockType().canProvidePower()) {
                if (rightEntity instanceof TileEntityChest && rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, (IInventory) rightEntity,
                            leftEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord + 1, leftEntity.yCoord,
                        leftEntity.zCoord);

                if (rightEntity instanceof TileEntityChest && rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, leftEntity,
                            (IInventory) rightEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord, leftEntity.yCoord,
                        leftEntity.zCoord - 1);

                if (rightEntity instanceof TileEntityChest && rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, (IInventory) rightEntity,
                            leftEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord, leftEntity.yCoord,
                        leftEntity.zCoord + 1);

                if (rightEntity instanceof TileEntityChest && rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, leftEntity,
                            (IInventory) rightEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }
            } else { // ------------------------------------Check for regular chests------------------------------------
                if (rightEntity instanceof TileEntityChest && !rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, (IInventory) rightEntity,
                            leftEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord + 1, leftEntity.yCoord,
                        leftEntity.zCoord);

                if (rightEntity instanceof TileEntityChest && !rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, leftEntity,
                            (IInventory) rightEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord, leftEntity.yCoord,
                        leftEntity.zCoord - 1);

                if (rightEntity instanceof TileEntityChest && !rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, (IInventory) rightEntity,
                            leftEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }

                rightEntity = world.getTileEntity(leftEntity.xCoord, leftEntity.yCoord,
                        leftEntity.zCoord + 1);

                if (rightEntity instanceof TileEntityChest && !rightEntity.getBlockType().canProvidePower()) {
                    InventoryLargeChest largeChest = new InventoryLargeChest(chestName, leftEntity,
                            (IInventory) rightEntity);

                    entities.add(leftEntity);
                    entities.add(rightEntity);

                    return new InventoryData(entities, largeChest, InteractionResult.DROPOFF_FAIL);
                }
            }
        }

        entities.add(leftEntity);

        return new InventoryData(entities, leftEntity, InteractionResult.DROPOFF_FAIL);
    }

    private InventoryData getInventoryData(TileEntityEnderChest entity) {
        List<TileEntity> entities = Collections.singletonList(entity);

        return new InventoryData(entities, player.getInventoryEnderChest(), InteractionResult.DROPOFF_FAIL);
    }

    public abstract class Slots {

        public static final int LAST = -1;
        public static final int FIRST = 0;
        public static final int FURNACE_FUEL = 1;
        public static final int FURNACE_OUT = 2;
        public static final int PLAYER_INVENTORY_FIRST = 9;
        public static final int PLAYER_INVENTORY_LAST = 36;

    }

}
