package skullsanctum;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Development helper, only active with -Dskullsanctum.dev.views=x,y,z,yaw,pitch;... (set by `-PdevScreenshot`): the
 * server moves every joining player to the first view point after one second and to the next one every five seconds,
 * so the client can take one screenshot at each (skullsanctum.client.DevClientHooks).
 */
public final class DevHooks {
    public static final String VIEWS_PROPERTY = "skullsanctum.dev.views";
    public static final int FIRST_TICK = 20, TICKS_PER_VIEW = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(SkullSanctum.MOD_ID);

    private DevHooks() {
    }

    public static void initServer() {
        double[][] views = parseViews();
        if (views == null) return;
        LOGGER.info("Dev view points active: {}", System.getProperty(VIEWS_PROPERTY));
        Map<ServerPlayer, Integer> seen = new WeakHashMap<>();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int age = seen.merge(player, 1, Integer::sum) - FIRST_TICK;
                if (age < 0 || age % TICKS_PER_VIEW != 0 || age / TICKS_PER_VIEW >= views.length) continue;
                double[] view = views[age / TICKS_PER_VIEW];
                player.teleportTo(server.overworld(), view[0], view[1], view[2], Set.<Relative>of(),
                        (float) view[3], (float) view[4], false);
                LOGGER.info("Dev view {}: teleported {}", age / TICKS_PER_VIEW + 1, player.getName().getString());
            }
        });
    }

    public static double[][] parseViews() {
        String raw = System.getProperty(VIEWS_PROPERTY);
        if (raw == null) return null;
        String[] entries = raw.split(";");
        double[][] views = new double[entries.length][];
        for (int v = 0; v < entries.length; v++) {
            String[] parts = entries[v].split(",");
            if (parts.length != 5) throw new IllegalArgumentException("view point needs x,y,z,yaw,pitch: " + entries[v]);
            views[v] = new double[5];
            for (int i = 0; i < 5; i++) views[v][i] = Double.parseDouble(parts[i].trim());
        }
        return views;
    }
}
