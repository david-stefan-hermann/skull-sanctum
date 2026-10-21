package skullsanctum.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import skullsanctum.SkullSanctum;

/**
 * The Head Atlas's floating book (or skull): turns towards the nearest player and opens for them, as the enchanting
 * table's book does. Client side only, nothing is saved.
 */
public class HeadAtlasBlockEntity extends BlockEntity {
    public int time;
    public float flip, oFlip, flipT, flipA, open, oOpen, rot, oRot, tRot;

    public HeadAtlasBlockEntity(BlockPos pos, BlockState state) {
        super(SkullSanctum.ATLAS_BLOCK_ENTITY, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HeadAtlasBlockEntity atlas) {
        atlas.oOpen = atlas.open;
        atlas.oRot = atlas.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false);
        RandomSource random = level.getRandom();
        if (player != null) {
            atlas.tRot = (float) Mth.atan2(player.getZ() - (pos.getZ() + 0.5), player.getX() - (pos.getX() + 0.5));
            atlas.open += 0.1f;
            if (atlas.open < 0.5f || random.nextInt(40) == 0) {
                float before = atlas.flipT;
                do {
                    atlas.flipT += random.nextInt(4) - random.nextInt(4);
                } while (before == atlas.flipT);
            }
        } else {
            atlas.tRot += 0.02f;
            atlas.open -= 0.1f;
        }
        atlas.rot = Mth.wrapDegrees(atlas.rot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        atlas.tRot = Mth.wrapDegrees(atlas.tRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float turn = Mth.wrapDegrees((atlas.tRot - atlas.rot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        atlas.rot += turn * 0.4f;
        atlas.open = Mth.clamp(atlas.open, 0.0f, 1.0f);
        atlas.time++;
        atlas.oFlip = atlas.flip;
        float step = Mth.clamp((atlas.flipT - atlas.flip) * 0.4f, -0.2f, 0.2f);
        atlas.flipA += (step - atlas.flipA) * 0.9f;
        atlas.flip += atlas.flipA;
    }
}
