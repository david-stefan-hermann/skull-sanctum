package skullsanctum.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import skullsanctum.SkullSanctum;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.altar.Sanctum;
import skullsanctum.shop.ShopNetwork;

/**
 * The Head Atlas: what the sanctum's enchanting table turns into when the sanctum awakens. As large as the table,
 * with something floating above it (see HeadAtlasRenderer); a right click opens the head browser. Never crafted,
 * breaking it gives the enchanting table back, and so does a click on one whose sanctum is gone.
 */
public class HeadAtlasBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public HeadAtlasBlock(Properties properties) {
        super(properties);
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
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        SacrificialAltarBlockEntity altar = Sanctum.altarOfTable(level, pos);
        if (altar == null) {
            // no awake altar claims it: its sanctum went without a word (a world editor, a lost chunk), so it is a table again
            level.setBlockAndUpdate(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("message.skullsanctum.orphan"));
        } else if (player instanceof ServerPlayer serverPlayer) {
            ShopNetwork.openBrowser(serverPlayer, altar);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** The sanctum's green flames rise from it, as from the altar (a Head Atlas only exists in an awake sanctum). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        level.addParticle(SkullSanctum.SOUL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.78,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeadAtlasBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, SkullSanctum.ATLAS_BLOCK_ENTITY, HeadAtlasBlockEntity::clientTick) : null;
    }
}
