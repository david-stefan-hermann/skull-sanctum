package skullsanctum.portable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import skullsanctum.SkullSanctum;
import skullsanctum.altar.SacrificialAltarBlockEntity;

/**
 * The portable Head Atlas's memory: the altar it is bound to, in whatever dimension, which comes with the item
 * ({@link SkullSanctum#BOUND_ALTAR}) and goes back into it when the skull is broken. On the client it counts its
 * ticks for the floating and the turning (see PortableAtlasRenderer), lets the sanctum's green flames rise around it and
 * whispers and laughs
 * now and then.
 */
public class PortableAtlasBlockEntity extends BlockEntity {
    private static final String ALTAR = "altar";
    /** The sanctum's green flame rises beside the skull every so many ticks, each a little further round, this far out. */
    private static final double ORBIT = 0.6;
    private static final int SOUL_EVERY = 2;
    private static final int WHISPER_CHANCE = 140, LAUGH_CHANCE = 900;

    private @Nullable GlobalPos altar;
    /** Client side: ticks since it was set down. */
    public int time;

    public PortableAtlasBlockEntity(BlockPos pos, BlockState state) {
        super(SkullSanctum.PORTABLE_BLOCK_ENTITY, pos, state);
    }

    public @Nullable GlobalPos boundAltar() {
        return altar;
    }

    public boolean bound() {
        return altar != null;
    }

    /** The awake altar it is bound to, or null when it is bound to none, the altar is gone or its sanctum sleeps. */
    public @Nullable SacrificialAltarBlockEntity altar(MinecraftServer server) {
        return altarAt(server, altar);
    }

    /** The awake altar at {@code where}, in whatever dimension; its chunk is loaded for the look if need be. */
    public static @Nullable SacrificialAltarBlockEntity altarAt(MinecraftServer server, @Nullable GlobalPos where) {
        if (where == null) return null;
        ServerLevel level = server.getLevel(where.dimension());
        if (level == null || level.isOutsideBuildHeight(where.pos())) return null;
        // getBlockEntity goes through getChunkAt, which loads the chunk when it is not: the sanctum may be far away
        return level.getBlockEntity(where.pos()) instanceof SacrificialAltarBlockEntity altar && altar.formed() ? altar : null;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PortableAtlasBlockEntity skull) {
        skull.time++;
        RandomSource random = level.getRandom();
        if (skull.time % SOUL_EVERY == 0) {
            double angle = skull.time * 0.08;
            level.addParticle(SkullSanctum.SOUL_FLAME, pos.getX() + 0.5 + Math.cos(angle) * ORBIT, pos.getY() + 0.1 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.5 + Math.sin(angle) * ORBIT, 0, 0.02, 0);
        }
        if (random.nextInt(WHISPER_CHANCE) == 0) {
            level.playLocalSound(pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.5f, 0.5f + random.nextFloat() * 0.4f, false);
        }
        if (random.nextInt(LAUGH_CHANCE) == 0) {
            // the witch's cackle, slowed down: deeper and more wicked
            level.playLocalSound(pos, SoundEvents.WITCH_CELEBRATE, SoundSource.BLOCKS, 1.0f, 0.5f + random.nextFloat() * 0.15f, false);
        }
    }

    // ---- the item's component comes and goes with the block ---------------------------------------------------------

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        altar = components.get(SkullSanctum.BOUND_ALTAR);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (altar != null) builder.set(SkullSanctum.BOUND_ALTAR, altar);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput out) {
        out.discard(ALTAR);
    }

    // ---- persistence and client sync -----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable(ALTAR, GlobalPos.CODEC, altar);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        altar = in.read(ALTAR, GlobalPos.CODEC).orElse(null);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
