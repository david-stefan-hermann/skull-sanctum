package skullsanctum.shop;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import skullsanctum.SkullSanctum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Each player's favourite heads (catalog uuids), kept with the world. */
public final class Favourites extends SavedData {
    public static final int MAX = 1000;
    private static final Codec<Favourites> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf())
            .xmap(Favourites::new, data -> {
                Map<UUID, List<String>> out = new HashMap<>();
                data.players.forEach((player, heads) -> out.put(player, new ArrayList<>(heads)));
                return out;
            }).fieldOf("players").codec();
    public static final SavedDataType<Favourites> TYPE = new SavedDataType<>(SkullSanctum.id("favourites"),
            Favourites::new, CODEC, null);

    private final Map<UUID, Set<String>> players = new HashMap<>();

    public Favourites() {
    }

    private Favourites(Map<UUID, List<String>> stored) {
        stored.forEach((player, heads) -> players.put(player, new LinkedHashSet<>(heads)));
    }

    public static Favourites get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Set<String> of(UUID player) {
        return players.getOrDefault(player, Set.of());
    }

    public void set(UUID player, String head, boolean on) {
        Set<String> heads = players.computeIfAbsent(player, p -> new LinkedHashSet<>());
        boolean changed = on ? heads.size() < MAX && heads.add(head) : heads.remove(head);
        if (changed) setDirty();
    }
}
