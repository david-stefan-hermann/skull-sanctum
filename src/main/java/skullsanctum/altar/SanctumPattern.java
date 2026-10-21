package skullsanctum.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import skullsanctum.SkullSanctum;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What wakes the altar, and the shrine the altar suggests.
 * <p>
 * The altar awakens when a cauldron (empty or not) and an enchanting table stand with it inside one 3 × 3 × 3 box
 * ({@link #findCore}).
 * Everything else is decoration. An altar that cannot awaken shows the suggested shrine as ghosts ({@link #template}),
 * seen from its front (the player stands there), with the altar at the left of the middle row:
 * <pre>
 *   top      slab   stairs (upside down, its open side towards the player)   slab   back row only
 *   2        wall   .                                         wall         back row only
 *   1        gilded cauldron  bricks        (back)
 *            ALTAR  wither skeleton skull  enchanting table   (middle)
 *   floor    bricks cracked bricks  bricks  (back)
 *            bricks cracked bricks  gilded  (middle)
 *            cracked gilded  bricks         (front)
 * </pre>
 */
public final class SanctumPattern {
    /** The box the altar, the cauldron and the enchanting table must share. */
    public static final int BOX = 3;

    private SanctumPattern() {
    }

    /** One block of the shrine: where it goes, what a ghost shows there, and what counts as it. */
    public record Part(BlockPos pos, BlockState ghost, Predicate<BlockState> matches) {
    }

    /** The two blocks that wake the altar. */
    public record Core(BlockPos tank, BlockPos table) {
    }

    /** The suggested shrine for an altar facing {@code front}; the altar itself is not one of its parts. */
    public static List<Part> template(BlockPos altar, Direction front) {
        Direction right = front.getCounterClockWise(); // the right hand of a player standing in front
        BlockPos middle = altar.relative(right);       // the middle of the middle row, on the altar's level
        List<Part> parts = new ArrayList<>();
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        // the floor, row by row from the back
        BlockState[][] floor = {{bricks, cracked, bricks}, {bricks, cracked, gilded}, {cracked, gilded, bricks}};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                BlockState state = floor[row][col];
                parts.add(new Part(at(middle, front, right, col - 1, row - 1, -1), state, s -> s.is(state.getBlock())));
            }
        }
        // the altar's level: the back row, then the skull and the enchanting table beside the altar
        parts.add(new Part(at(middle, front, right, -1, -1, 0), gilded, s -> s.is(Blocks.GILDED_BLACKSTONE)));
        parts.add(new Part(at(middle, front, right, 0, -1, 0), Blocks.CAULDRON.defaultBlockState(), SanctumPattern::isCauldron));
        parts.add(new Part(at(middle, front, right, 1, -1, 0), bricks, s -> s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)));
        parts.add(new Part(middle, Blocks.WITHER_SKELETON_SKULL.defaultBlockState()
                .setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(front.getOpposite())),
                s -> s.is(Blocks.WITHER_SKELETON_SKULL)));
        parts.add(new Part(at(middle, front, right, 1, 0, 0), Blocks.ENCHANTING_TABLE.defaultBlockState(),
                s -> s.is(Blocks.ENCHANTING_TABLE) || s.is(SkullSanctum.HEAD_ATLAS)));
        // the pillars and the lintel over the cauldron
        for (int col : new int[]{-1, 1}) {
            parts.add(new Part(at(middle, front, right, col, -1, 1), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
                    s -> s.is(Blocks.POLISHED_BLACKSTONE_BRICK_WALL)));
            parts.add(new Part(at(middle, front, right, col, -1, 2), Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), s -> s.is(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB)));
        }
        parts.add(new Part(at(middle, front, right, 0, -1, 2), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, front.getOpposite()).setValue(StairBlock.HALF, Half.TOP),
                s -> s.is(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS)));
        return parts;
    }

    /** A cauldron with whatever is in it, or the soul tank it became. */
    public static boolean isCauldron(BlockState state) {
        return state.getBlock() instanceof AbstractCauldronBlock || state.is(SkullSanctum.SOUL_TANK);
    }

    /** Column {@code col} (-1 left, 1 right), row {@code row} (-1 back, 1 front), {@code dy} above the altar. */
    private static BlockPos at(BlockPos middle, Direction front, Direction right, int col, int row, int dy) {
        return middle.relative(right, col).relative(front, row).above(dy);
    }

    /** The parts of the shrine that are not there. */
    public static List<Part> missing(Level level, BlockPos altar, Direction front) {
        List<Part> missing = new ArrayList<>();
        for (Part part : template(altar, front)) {
            if (!part.matches().test(level.getBlockState(part.pos()))) missing.add(part);
        }
        return missing;
    }

    /**
     * A cauldron (or the soul tank it became) and an enchanting table (or the Head Atlas it became) that fit into one 3 × 3 × 3 box with the
     * altar; the closest pair when there are several.
     */
    public static Optional<Core> findCore(Level level, BlockPos altar) {
        List<BlockPos> tanks = new ArrayList<>(), tables = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(altar.offset(-BOX + 1, -BOX + 1, -BOX + 1), altar.offset(BOX - 1, BOX - 1, BOX - 1))) {
            BlockState state = level.getBlockState(pos);
            if (isCauldron(state)) tanks.add(pos.immutable());
            else if (state.is(Blocks.ENCHANTING_TABLE) || state.is(SkullSanctum.HEAD_ATLAS)) tables.add(pos.immutable());
        }
        Core best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos tank : tanks) {
            for (BlockPos table : tables) {
                if (!fits(altar, tank, table)) continue;
                double distance = altar.distSqr(tank) + altar.distSqr(table);
                if (distance < bestDistance) {
                    best = new Core(tank, table);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether these blocks fit into one box of {@link #BOX} blocks a side. */
    public static boolean fits(BlockPos... positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        return maxX - minX < BOX && maxY - minY < BOX && maxZ - minZ < BOX;
    }
}
