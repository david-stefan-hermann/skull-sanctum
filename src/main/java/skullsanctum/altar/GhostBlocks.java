package skullsanctum.altar;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import skullsanctum.SkullSanctum;
import skullsanctum.mixin.BlockDisplayAccessor;
import skullsanctum.mixin.DisplayAccessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The missing blocks of a sanctum, shown for a few seconds as small glowing block displays where they belong. The
 * displays are tagged; any that outlive their time (a chunk saved meanwhile) are removed when they load again.
 */
public final class GhostBlocks {
    public static final String TAG = "skullsanctum_ghost";
    private static final int LIFETIME = 120;
    private static final float SCALE = 0.75f;
    private static final int GLOW = 0x62E077;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    /** Ghosts of an earlier session that came back with their chunk; removed on the next tick, not while loading. */
    private static final List<Entity> STRAYS = new ArrayList<>();

    private record Ghost(Display.BlockDisplay entity, long until) {
    }

    private GhostBlocks() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            STRAYS.forEach(Entity::discard);
            STRAYS.clear();
            for (Iterator<Ghost> it = GHOSTS.iterator(); it.hasNext(); ) {
                Ghost ghost = it.next();
                if (ghost.entity().isRemoved() || ghost.entity().level().getGameTime() >= ghost.until()) {
                    ghost.entity().discard();
                    it.remove();
                } else if (ghost.entity().level() instanceof ServerLevel level && level.getGameTime() % 10 == 0) {
                    level.sendParticles(SkullSanctum.SOUL_FLAME, ghost.entity().getX() + 0.5, ghost.entity().getY() + 0.5,
                            ghost.entity().getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
                }
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && GHOSTS.stream().noneMatch(g -> g.entity() == entity)) STRAYS.add(entity);
        });
        // the lists must not outlive their world: a ghost of a closed world would never expire
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static void show(ServerLevel level, List<SanctumPattern.Part> missing) {
        long until = level.getGameTime() + LIFETIME;
        for (SanctumPattern.Part part : missing) {
            if (GHOSTS.stream().anyMatch(g -> g.entity().level() == level && g.entity().blockPosition().equals(part.pos()))) continue;
            Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (display == null) continue;
            ((BlockDisplayAccessor) display).skullsanctum$setBlockState(part.ghost());
            float offset = (1 - SCALE) / 2;
            ((DisplayAccessor) display).skullsanctum$setTransformation(new Transformation(new Vector3f(offset, offset, offset),
                    new Quaternionf(), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf()));
            ((DisplayAccessor) display).skullsanctum$setGlowColorOverride(GLOW);
            ((DisplayAccessor) display).skullsanctum$setBrightnessOverride(Brightness.FULL_BRIGHT);
            display.setGlowingTag(true);
            display.addTag(TAG);
            display.setPos(part.pos().getX(), part.pos().getY(), part.pos().getZ());
            GHOSTS.add(new Ghost(display, until)); // before it joins the level: the load event discards strays
            level.addFreshEntity(display);
        }
    }

    /** For the self-test. */
    public static int shown() {
        return GHOSTS.size();
    }

    public static void clear() {
        GHOSTS.forEach(g -> g.entity().discard());
        GHOSTS.clear();
        STRAYS.clear();
    }
}
