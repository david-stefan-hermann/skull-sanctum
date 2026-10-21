package skullsanctum.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import skullsanctum.tank.SoulTankBlockEntity;

/**
 * The altar's sacrifice screen: nine offering slots, what they would give, the tank's gauge, and the button that burns
 * them. The offerings belong to the player until they burn; closing the screen gives back what is left.
 */
public class AltarMenu extends AbstractContainerMenu {
    public static final int SLOTS = 9;
    public static final int PLAYER_X = 8;
    /** Where the grid's first slot and the player's inventory sit; the screen draws its niche round the grid. */
    public static final int SLOT_X = 14, SLOT_Y = 41, PLAYER_Y = 120, HEIGHT = PLAYER_Y + 82;
    public static final int BUTTON_SACRIFICE = 0;
    /** preview souls, tank souls, tank capacity, preview fraction in hundredths */
    private static final int DATA = 4;

    private final Container offerings;
    private final ContainerData data;
    private final SacrificialAltarBlockEntity altar;
    private final BlockPos pos;

    public static AltarMenu client(int containerId, Inventory playerInventory, BlockPos pos) {
        return new AltarMenu(containerId, playerInventory, null, pos, new SimpleContainerData(DATA));
    }

    public AltarMenu(int containerId, Inventory playerInventory, SacrificialAltarBlockEntity altar) {
        this(containerId, playerInventory, altar, altar.getBlockPos(), new SimpleContainerData(DATA));
    }

    private AltarMenu(int containerId, Inventory playerInventory, SacrificialAltarBlockEntity altar, BlockPos pos, ContainerData data) {
        super(SkullSanctum.ALTAR_MENU, containerId);
        this.altar = altar;
        this.pos = pos;
        this.data = data;
        this.offerings = new SimpleContainer(SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(this);
            }
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(offerings, row * 3 + col, SLOT_X + col * 18, SLOT_Y + row * 18));
            }
        }
        addStandardInventorySlots(playerInventory, PLAYER_X, PLAYER_Y);
        addDataSlots(data);
        updateData();
    }

    public BlockPos pos() {
        return pos;
    }

    public int preview() {
        return data.get(0);
    }

    public int souls() {
        return data.get(1);
    }

    public int capacity() {
        return Math.max(1, data.get(2));
    }

    /** The fraction of a soul the preview leaves over, in hundredths. */
    public int previewFraction() {
        return data.get(3);
    }

    public boolean isOffering(Slot slot) {
        return slot.container == offerings;
    }

    private void updateData() {
        if (altar == null) return;
        SoulTankBlockEntity tank = altar.tank();
        double total = altar.residue();
        for (ItemStack stack : offerings) total += SacrificeValues.value(stack) * stack.getCount();
        data.set(0, (int) Math.min(Short.MAX_VALUE, Math.floor(total + 1e-9)));
        data.set(1, tank == null ? 0 : tank.souls());
        data.set(2, tank == null ? 0 : tank.capacity());
        data.set(3, (int) ((total - Math.floor(total + 1e-9)) * 100 + 1e-6) % 100); // cut, not rounded: 2.999 shows as 2.99
    }

    @Override
    public void broadcastChanges() {
        updateData();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_SACRIFICE || altar == null || !(player.level() instanceof ServerLevel level)) return false;
        SacrificialAltarBlockEntity.Sacrifice result = altar.sacrifice(offerings);
        offerings.setChanged();
        if (result.items() > 0) altar.celebrate(level, result);
        updateData();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (altar != null) clearContainer(player, offerings);
    }

    @Override
    public boolean stillValid(Player player) {
        return altar == null || !altar.isRemoved() && altar.formed()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
