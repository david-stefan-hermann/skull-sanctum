package skullsanctum.shop;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import skullsanctum.SkullSanctum;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.tank.SoulTankBlockEntity;

/** The souls a player can spend at a sanctum: those in its tank and the soul items they carry. */
public final class Wallet {
    private Wallet() {
    }

    public static int balance(Player player, SacrificialAltarBlockEntity altar) {
        SoulTankBlockEntity tank = altar == null ? null : altar.tank();
        long total = (tank == null ? 0 : tank.souls()) + carried(player.getInventory());
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static int carried(Inventory inventory) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (stack.is(SkullSanctum.SOUL)) count += stack.getCount();
        }
        return count;
    }

    /** Takes {@code amount} souls, from the tank first, then from the inventory; the caller checked the balance. */
    public static void pay(Player player, SacrificialAltarBlockEntity altar, int amount) {
        SoulTankBlockEntity tank = altar.tank();
        int left = amount - (tank == null ? 0 : tank.take(amount));
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(SkullSanctum.SOUL)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        inventory.setChanged();
    }
}
