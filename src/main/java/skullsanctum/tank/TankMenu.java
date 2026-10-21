package skullsanctum.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import skullsanctum.SkullSanctum;

/**
 * The soul tank's screen: one slot in the middle of the cauldron shows the tank's souls as a stack. Taking from it
 * takes souls out, soul items put into it go into the tank at once (as far as it has room); the player's inventory
 * below.
 */
public class TankMenu extends AbstractContainerMenu {
    /** The slot in the middle of the cauldron. */
    public static final int SLOT_X = 80, SLOT_Y = 48, PLAYER_X = 8, PLAYER_Y = 110; // the inventory where vanilla keeps it
    public static final int SLOT = 0, FIRST_PLAYER = 1;
    private final Container window;
    private final ContainerData data;

    public static TankMenu client(int containerId, Inventory playerInventory, BlockPos pos) {
        return new TankMenu(containerId, playerInventory, new SimpleContainer(1), new SimpleContainerData(2));
    }

    public TankMenu(int containerId, Inventory playerInventory, Container window, ContainerData data) {
        super(SkullSanctum.TANK_MENU, containerId);
        this.window = window;
        this.data = data;
        addSlot(new Slot(window, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(SkullSanctum.SOUL);
            }

            @Override
            public int getMaxStackSize() {
                return window instanceof SoulWindow soulWindow ? soulWindow.limit() : 64;
            }

            /** Souls dropped onto the slot go straight into the tank, past the stack it shows. */
            @Override
            public ItemStack safeInsert(ItemStack stack, int count) {
                if (!(window instanceof SoulWindow soulWindow) || !mayPlace(stack)) return super.safeInsert(stack, count);
                stack.shrink(soulWindow.insert(Math.min(count, stack.getCount())));
                setChanged();
                return stack;
            }
        });
        addStandardInventorySlots(playerInventory, PLAYER_X, PLAYER_Y);
        addDataSlots(data);
    }

    public int souls() {
        return data.get(0);
    }

    public int capacity() {
        return Math.max(1, data.get(1));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index == SLOT) {
            // out of the tank: one stack (as much of it as the inventory takes), then stop; vanilla would repeat
            ItemStack moving = stack.copy();
            if (moveItemStackTo(moving, FIRST_PLAYER, slots.size(), true)) window.removeItem(0, stack.getCount() - moving.getCount());
            return ItemStack.EMPTY;
        }
        if (!stack.is(SkullSanctum.SOUL)) return ItemStack.EMPTY;
        // into the tank: straight in, as far as it has room
        if (window instanceof SoulWindow soulWindow) {
            int added = soulWindow.insert(stack.getCount());
            if (added == 0) return ItemStack.EMPTY;
            stack.shrink(added);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return window.stillValid(player);
    }
}
