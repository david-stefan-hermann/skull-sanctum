package skullsanctum.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/** Which awake sanctum a Head Atlas or a soul tank belongs to. */
public final class Sanctum {
    private Sanctum() {
    }

    public static void init() {
        GhostBlocks.init();
    }

    /** The awake altar whose sanctum this Head Atlas belongs to, or null. */
    public static SacrificialAltarBlockEntity altarOfTable(Level level, BlockPos table) {
        return altarNear(level, table, altar -> altar.tablePos().equals(table));
    }

    /** The awake altar whose sanctum this soul tank belongs to, or null. */
    public static SacrificialAltarBlockEntity altarOfTank(Level level, BlockPos tank) {
        return altarNear(level, tank, altar -> altar.tankPos().equals(tank));
    }

    /** An awake altar within the sanctum's box around {@code pos} that passes {@code test}. */
    private static SacrificialAltarBlockEntity altarNear(Level level, BlockPos pos, Predicate<SacrificialAltarBlockEntity> test) {
        int reach = SanctumPattern.BOX - 1;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, reach, reach))) {
            if (level.getBlockEntity(near) instanceof SacrificialAltarBlockEntity altar && altar.formed() && test.test(altar)) {
                return altar;
            }
        }
        return null;
    }
}
