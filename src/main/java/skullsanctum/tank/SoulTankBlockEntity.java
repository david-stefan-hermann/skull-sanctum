package skullsanctum.tank;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.altar.Sanctum;

import java.util.List;
import skullsanctum.Config;
import skullsanctum.SkullSanctum;

/**
 * The souls of a sanctum, as a number up to the configured capacity. The block entity is no container on purpose:
 * hoppers and pipes find nothing to take. Players reach the souls through the tank's screen ({@link SoulWindow}), the
 * altar puts them in, the head browser takes them out.
 */
public class SoulTankBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    private int souls;
    private int burning;
    private int age;

    /** Souls and capacity for the screens' gauges (16-bit data slots, see {@link Config#MAX_TANK}). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? souls : capacity();
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SoulTankBlockEntity(BlockPos pos, BlockState state) {
        super(SkullSanctum.TANK_BLOCK_ENTITY, pos, state);
    }

    public int souls() {
        return souls;
    }

    public int capacity() {
        return Config.get().tankCapacity();
    }

    public int room() {
        return Math.max(0, capacity() - souls);
    }

    /** Puts up to {@code amount} souls in; returns how many went in. */
    public int add(int amount) {
        int added = Math.clamp(amount, 0, room());
        if (added > 0) {
            souls += added;
            changed();
        }
        return added;
    }

    /** Takes up to {@code amount} souls out; returns how many came out. */
    public int take(int amount) {
        int taken = Math.clamp(amount, 0, souls);
        if (taken > 0) {
            souls -= taken;
            changed();
        }
        return taken;
    }

    public void setSouls(int souls) {
        this.souls = Math.clamp(souls, 0, capacity());
        changed();
    }

    /** The green flame after a sacrifice. */
    public void burn() {
        burning = Config.get().burnTicks();
        updateState();
    }

    public boolean isBurning() {
        return burning > 0;
    }

    public int signal() {
        return souls == 0 ? 0 : 1 + (int) ((long) souls * 14 / capacity());
    }

    /** The souls as items on the ground, in stacks: when the sanctum falls apart or the tank is broken. */
    public void dropSouls() {
        if (level == null) return;
        while (souls > 0) {
            int count = Math.min(souls, 64);
            souls -= count;
            Block.popResource(level, worldPosition, new ItemStack(SkullSanctum.SOUL, count));
        }
        setChanged();
    }

    public void serverTick(ServerLevel level, BlockState state) {
        if (burning > 0 && --burning == 0) updateState();
        if (++age % 5 == 0) burnThrown(level);
    }

    /** Items thrown into the tank are sacrificed, as on the altar. */
    private void burnThrown(ServerLevel level) {
        AABB inside = new AABB(worldPosition.getX() + 0.125, worldPosition.getY() + 0.2, worldPosition.getZ() + 0.125,
                worldPosition.getX() + 0.875, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.875);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, inside, ItemEntity::isAlive);
        if (items.isEmpty()) return;
        SacrificialAltarBlockEntity altar = Sanctum.altarOfTank(level, worldPosition);
        if (altar == null) return;
        SacrificialAltarBlockEntity.Sacrifice result = altar.sacrifice(items.stream().map(ItemEntity::getItem).toList());
        for (ItemEntity item : items) {
            if (item.getItem().isEmpty()) item.discard();
        }
        if (result.items() > 0) altar.celebrate(level, result);
    }

    private void changed() {
        setChanged();
        updateState();
    }

    /** Keeps the fill level and the flame of the block state in step with the souls. */
    private void updateState() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.is(SkullSanctum.SOUL_TANK)) return;
        int shown = souls == 0 ? 0 : Math.max(1, (int) Math.ceil(souls * 4.0 / capacity()));
        BlockState next = state.setValue(SoulTankBlock.LEVEL, Math.min(4, shown)).setValue(SoulTankBlock.BURNING, burning > 0);
        if (next != state) level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropSouls();
    }

    // ---- menu ----------------------------------------------------------------------------------------------------

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skullsanctum.soul_tank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TankMenu(containerId, playerInventory, new SoulWindow(this), data);
    }

    // ---- persistence ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("souls", souls);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        souls = Math.max(0, in.getIntOr("souls", 0));
    }
}
