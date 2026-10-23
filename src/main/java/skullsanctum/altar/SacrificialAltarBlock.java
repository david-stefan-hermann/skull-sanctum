package skullsanctum.altar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import skullsanctum.SkullSanctum;

/**
 * The sacrificial altar. Before the sanctum stands, a right click checks it and shows the blocks that are missing as
 * ghosts; once it is complete the same click forms it. A formed altar opens its sacrifice screen and burns what is
 * thrown onto it; a click on it with a portable Head Atlas in hand binds that skull to the sanctum instead.
 */
public class SacrificialAltarBlock extends BaseEntityBlock {
    public static final MapCodec<SacrificialAltarBlock> CODEC = simpleCodec(SacrificialAltarBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The sanctum around it is formed: the altar glows. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** The size of an enchanting table: a full block, 12 pixels high. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public SacrificialAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<SacrificialAltarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SacrificialAltarBlockEntity altar && player instanceof ServerPlayer serverPlayer) {
            altar.use(serverPlayer);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A portable Head Atlas in hand is bound to an awake altar: from then on it opens this sanctum's heads anywhere. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(SkullSanctum.PORTABLE_ATLAS_ITEM) || !state.getValue(FORMED)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        stack.set(SkullSanctum.BOUND_ALTAR, GlobalPos.of(level.dimension(), pos));
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
        ((ServerLevel) level).sendParticles(SkullSanctum.SOUL_FLAME, pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("message.skullsanctum.portable.bound"));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FORMED) || random.nextInt(3) != 0) return;
        level.addParticle(SkullSanctum.SOUL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.78,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SacrificialAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, SkullSanctum.ALTAR_BLOCK_ENTITY, (l, p, s, altar) -> altar.serverTick((ServerLevel) l));
    }
}
