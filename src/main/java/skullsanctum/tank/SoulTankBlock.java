package skullsanctum.tank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import skullsanctum.SkullSanctum;

/**
 * The soul tank: the sanctum's cauldron once the sanctum is formed. It shows how full it is ({@link #LEVEL}), its
 * souls bubble like lava, and it burns with a green flame for a moment after every sacrifice ({@link #BURNING}).
 * Items thrown into it are sacrificed. A right click opens its screen, where souls are taken out or put back; hoppers
 * cannot reach it.
 */
public class SoulTankBlock extends BaseEntityBlock {
    /** 0 empty, 1 to 4 a quarter to full. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    public static final BooleanProperty BURNING = BooleanProperty.create("burning");

    public SoulTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(BURNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, BURNING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Blocks.CAULDRON.defaultBlockState().getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Blocks.CAULDRON.defaultBlockState().getInteractionShape(level, pos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SoulTankBlockEntity tank) player.openMenu(tank);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The souls' surface, in blocks above the tank's bottom, by fill level (the content models' heights). */
    private static final double[] SURFACE = {0, 6 / 16.0, 9 / 16.0, 12 / 16.0, 15 / 16.0};

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int fill = state.getValue(LEVEL);
        if (fill > 0) {
            // the souls bubble like lava: little green pops on the surface, now and then a plop
            double y = pos.getY() + SURFACE[fill] + 0.02;
            if (random.nextInt(3) == 0) {
                level.addParticle(SkullSanctum.SOUL_BUBBLE, pos.getX() + 0.2 + random.nextDouble() * 0.6, y,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01 + random.nextDouble() * 0.02, 0);
            }
            if (random.nextInt(60) == 0) {
                level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS,
                        0.25f, 0.7f + random.nextFloat() * 0.3f, false);
            }
        }
        if (random.nextInt(3) == 0) {  // the sanctum's green flames, as above the altar (a tank only exists in an awake sanctum)
            level.addParticle(SkullSanctum.SOUL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
        }
        if (!state.getValue(BURNING)) return;
        for (int i = 0; i < 3; i++) {
            level.addParticle(SkullSanctum.SOUL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.95,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.04 + random.nextDouble() * 0.03, 0);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof SoulTankBlockEntity tank ? tank.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoulTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, SkullSanctum.TANK_BLOCK_ENTITY, (l, p, s, tank) -> tank.serverTick((ServerLevel) l, s));
    }
}
