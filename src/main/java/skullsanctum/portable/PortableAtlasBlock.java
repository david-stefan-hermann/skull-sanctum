package skullsanctum.portable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import skullsanctum.SkullSanctum;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.shop.ShopNetwork;

/**
 * The portable Head Atlas: a skull that floats and turns where it is set down (PortableAtlasRenderer draws it) and
 * opens the head browser of the sanctum it is bound to, wherever that sanctum is. Any player may use it; breaking it
 * gives the skull back, still bound. Bound to nothing, or to a sanctum that sleeps, it says so and opens nothing.
 */
public class PortableAtlasBlock extends BaseEntityBlock {
    public static final MapCodec<PortableAtlasBlock> CODEC = simpleCodec(PortableAtlasBlock::new);
    /** The skull's 8 pixels, in the middle of the block; it floats around that. */
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    /** Bone when broken, stepped on or hit; setting it down has a sound of its own ({@link #setPlacedBy}). */
    public static final SoundType SOUND = new SoundType(1.0f, 1.0f, SoundEvents.BONE_BLOCK_BREAK, SoundEvents.BONE_BLOCK_STEP,
            SoundEvents.EMPTY, SoundEvents.BONE_BLOCK_HIT, SoundEvents.BONE_BLOCK_FALL);

    public PortableAtlasBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PortableAtlasBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PortableAtlasBlockEntity skull) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!skull.bound()) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.skullsanctum.portable.unbound"));
            return InteractionResult.SUCCESS_SERVER;
        }
        SacrificialAltarBlockEntity altar = skull.altar(level.getServer());
        if (altar == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.skullsanctum.portable.asleep"));
        } else {
            ShopNetwork.openBrowser(serverPlayer, pos, altar, true);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Set down, it sounds like the sanctum awakening (the altar's wither roar), shorter and higher. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.3f, 1.8f);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableAtlasBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, SkullSanctum.PORTABLE_BLOCK_ENTITY, PortableAtlasBlockEntity::clientTick) : null;
    }
}
