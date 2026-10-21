package skullsanctum.tank;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import skullsanctum.SkullSanctum;

/**
 * The tank's souls seen through one slot: it shows up to a stack of soul items. Taking from it takes souls out of the
 * tank, putting soul items into it puts them back. The menu sometimes grows or shrinks the shown stack in place;
 * {@link #sync} turns such a change into souls before the slot is shown again.
 */
public final class SoulWindow implements Container {
    private final SoulTankBlockEntity tank;
    private ItemStack shown = ItemStack.EMPTY;
    private int shownCount;

    public SoulWindow(SoulTankBlockEntity tank) {
        this.tank = tank;
    }

    private void sync() {
        int now = shown.is(SkullSanctum.SOUL) ? shown.getCount() : 0;
        if (now > shownCount) tank.add(now - shownCount);
        else if (now < shownCount) tank.take(shownCount - now);
        shownCount = Math.min(64, tank.souls());
        shown = shownCount == 0 ? ItemStack.EMPTY : new ItemStack(SkullSanctum.SOUL, shownCount);
    }

    /** Puts {@code count} souls straight into the tank; returns how many fit. */
    public int insert(int count) {
        sync();
        int added = tank.add(count);
        shownCount = 0;
        shown = ItemStack.EMPTY;
        sync();
        return added;
    }

    /** How many soul items the slot takes: what is shown plus the tank's room, up to a stack. */
    public int limit() {
        sync();
        return Math.min(64, shownCount + tank.room());
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        sync();
        return shown.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        sync();
        return shown;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        sync();
        int taken = tank.take(Math.min(count, shownCount));
        sync();
        return taken == 0 ? ItemStack.EMPTY : new ItemStack(SkullSanctum.SOUL, taken);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        sync();
        int wanted = stack.is(SkullSanctum.SOUL) ? stack.getCount() : 0;
        if (wanted > shownCount) tank.add(wanted - shownCount);
        else if (wanted < shownCount) tank.take(shownCount - wanted);
        shownCount = 0;
        shown = ItemStack.EMPTY;
        sync();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(SkullSanctum.SOUL);
    }

    @Override
    public void setChanged() {
        sync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(tank, player);
    }

    @Override
    public void clearContent() {
    }
}
