package skullsanctum.altar;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import skullsanctum.SkullSanctum;
import skullsanctum.tank.SoulTankBlockEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The altar's memory of its sanctum: whether it is awake, where its soul tank and its enchanting table stand, and the
 * fraction of a soul left over from the last sacrifices. An awake altar checks every second that the three still
 * share a 3 × 3 × 3 box and falls asleep when one of them is gone.
 */
public class SacrificialAltarBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
    private static final int CHECK_TICKS = 20;
    private static final int THROWN_TICKS = 5;

    private boolean formed;
    private BlockPos tankPos = BlockPos.ZERO;
    private BlockPos tablePos = BlockPos.ZERO;
    private double residue;
    private int age;

    public SacrificialAltarBlockEntity(BlockPos pos, BlockState state) {
        super(SkullSanctum.ALTAR_BLOCK_ENTITY, pos, state);
    }

    public boolean formed() {
        return formed;
    }

    public BlockPos tankPos() {
        return tankPos;
    }

    public BlockPos tablePos() {
        return tablePos;
    }

    /** Which way the suggested shrine faces: the altar's front, towards the player who set it down. */
    public Direction front() {
        return getBlockState().getValue(SacrificialAltarBlock.FACING);
    }

    /** The soul tank of an awake sanctum, or null. */
    public SoulTankBlockEntity tank() {
        if (!formed || level == null) return null;
        return level.getBlockEntity(tankPos) instanceof SoulTankBlockEntity tank ? tank : null;
    }

    /**
     * A right click on the altar: the sacrifice screen of an awake sanctum (sneaking: the shrine's missing decoration
     * as ghosts), otherwise wake it, or show the suggested shrine as ghosts when the cauldron or the table is missing.
     */
    public void use(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) level;
        if (formed) {
            if (player.isShiftKeyDown()) {
                GhostBlocks.show(serverLevel, SanctumPattern.missing(serverLevel, worldPosition, front()));
            } else {
                player.openMenu(this);
            }
            return;
        }
        Optional<SanctumPattern.Core> core = SanctumPattern.findCore(serverLevel, worldPosition);
        if (core.isEmpty()) {
            List<SanctumPattern.Part> missing = SanctumPattern.missing(serverLevel, worldPosition, front());
            GhostBlocks.show(serverLevel, missing);
            player.sendOverlayMessage(Component.translatable("message.skullsanctum.missing"));
            return;
        }
        form(serverLevel, core.get());
        player.sendOverlayMessage(Component.translatable("message.skullsanctum.formed"));
    }

    private void form(ServerLevel level, SanctumPattern.Core core) {
        formed = true;
        tankPos = core.tank();
        tablePos = core.table();
        residue = 0;
        if (!level.getBlockState(tankPos).is(SkullSanctum.SOUL_TANK)) {
            level.setBlockAndUpdate(tankPos, SkullSanctum.SOUL_TANK.defaultBlockState());
        }
        if (!level.getBlockState(tablePos).is(SkullSanctum.HEAD_ATLAS)) {
            level.setBlockAndUpdate(tablePos, SkullSanctum.HEAD_ATLAS.defaultBlockState());
        }
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(SacrificialAltarBlock.FORMED, true));
        level.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.4f, 1.4f);
        for (BlockPos pos : List.of(worldPosition, tankPos, tablePos)) {
            level.sendParticles(SkullSanctum.SOUL_FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.01);
        }
        setChanged();
        SkullSanctum.LOGGER.debug("Sanctum awake at {}: tank {}, table {}", worldPosition.toShortString(),
                tankPos.toShortString(), tablePos.toShortString());
    }

    /**
     * The tank or the Head Atlas is gone (or the altar goes): the tank turns back into a cauldron and drops its souls,
     * the Head Atlas back into an enchanting table.
     */
    public void unform(ServerLevel level) {
        if (!formed) return;
        formed = false;
        if (level.getBlockEntity(tankPos) instanceof SoulTankBlockEntity tank) {
            tank.dropSouls();
            level.setBlockAndUpdate(tankPos, Blocks.CAULDRON.defaultBlockState());
        }
        if (level.getBlockState(tablePos).is(SkullSanctum.HEAD_ATLAS)) {
            level.setBlockAndUpdate(tablePos, Blocks.ENCHANTING_TABLE.defaultBlockState());
        }
        if (level.getBlockState(worldPosition).is(SkullSanctum.SACRIFICIAL_ALTAR)) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(SacrificialAltarBlock.FORMED, false));
        }
        level.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0f, 0.6f);
        setChanged();
    }

    /** The tank and the Head Atlas are still in place and share the box with the altar. */
    private boolean intact(ServerLevel level) {
        return level.getBlockState(tankPos).is(SkullSanctum.SOUL_TANK) && level.getBlockState(tablePos).is(SkullSanctum.HEAD_ATLAS)
                && SanctumPattern.fits(worldPosition, tankPos, tablePos);
    }

    public void serverTick(ServerLevel level) {
        if (!formed) return;
        age++;
        if (age % CHECK_TICKS == 0 && !intact(level)) {
            unform(level);
            return;
        }
        if (age % THROWN_TICKS == 0) burnThrown(level);
    }

    /** Items lying on top of the altar are sacrificed, as far as the tank has room. */
    private void burnThrown(ServerLevel level) {
        AABB top = new AABB(worldPosition.getX(), worldPosition.getY() + 0.75, worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1.5, worldPosition.getZ() + 1);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, top, ItemEntity::isAlive);
        if (items.isEmpty()) return;
        Sacrifice result = sacrifice(items.stream().map(ItemEntity::getItem).toList());
        for (ItemEntity item : items) {
            if (item.getItem().isEmpty()) item.discard();
        }
        if (result.items() > 0) celebrate(level, result);
    }

    /** What a sacrifice burned: how many items, and the whole souls that went into the tank. */
    public record Sacrifice(int items, int souls) {
    }

    /**
     * Burns what is worth souls from these stacks (they shrink), as long as the tank has room; the rest stays. The
     * souls go into the tank, a fraction of a soul waits in the altar for the next sacrifice.
     */
    public Sacrifice sacrifice(Iterable<ItemStack> stacks) {
        SoulTankBlockEntity tank = tank();
        if (tank == null) return new Sacrifice(0, 0);
        int room = tank.room(), burned = 0, souls = 0;
        for (ItemStack stack : stacks) {
            double value = SacrificeValues.value(stack);
            if (value <= 0) continue;
            while (!stack.isEmpty() && Math.floor(residue + value + 1e-9) <= room - souls) {
                residue += value;
                stack.shrink(1);
                burned++;
                int whole = (int) Math.floor(residue + 1e-9);
                souls += whole;
                residue -= whole;
            }
        }
        if (burned > 0) {
            tank.add(souls);
            setChanged();
        }
        return new Sacrifice(burned, souls);
    }

    public double residue() {
        return residue;
    }

    /** Flames and sound after a sacrifice; the tank burns green for a moment. */
    public void celebrate(ServerLevel level, Sacrifice result) {
        level.sendParticles(SkullSanctum.SOUL_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 0.85,
                worldPosition.getZ() + 0.5, 8 + Math.min(result.items(), 24), 0.25, 0.1, 0.25, 0.02);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.7f);
        SoulTankBlockEntity tank = tank();
        if (tank != null) tank.burn();
    }

    /** Before the altar goes: its sanctum goes with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) unform(serverLevel);
    }

    // ---- menu ----------------------------------------------------------------------------------------------------

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skullsanctum.sacrificial_altar");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AltarMenu(containerId, playerInventory, this);
    }

    // ---- client sync: the enchanting table and the screens ask whether the sanctum is formed ---------------------------------

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    // ---- persistence ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("formed", formed);
        out.putLong("tank", tankPos.asLong());
        out.putLong("table", tablePos.asLong());
        out.putDouble("residue", residue);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        formed = in.getBooleanOr("formed", false);
        tankPos = BlockPos.of(in.getLongOr("tank", BlockPos.ZERO.asLong()));
        tablePos = BlockPos.of(in.getLongOr("table", BlockPos.ZERO.asLong()));
        residue = Math.clamp(in.getDoubleOr("residue", 0), 0, 1);
    }

    /** For the guide book and the self-test: the blocks of the suggested shrine and how many of each. */
    public static Map<Block, Integer> bill() {
        Map<Block, Integer> bill = new LinkedHashMap<>();
        for (SanctumPattern.Part part : SanctumPattern.template(BlockPos.ZERO, Direction.NORTH)) {
            bill.merge(part.ghost().getBlock(), 1, Integer::sum);
        }
        return bill;
    }
}
