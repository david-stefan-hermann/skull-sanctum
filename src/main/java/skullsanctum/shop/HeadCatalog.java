package skullsanctum.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import skullsanctum.SkullSanctum;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * The heads on offer, all from the jar: the classics (the MHF heads, {@code skullsanctum/classics.json}) and the
 * Heads Database by TheSilentPro (CC0 1.0, {@code skullsanctum/heads.tsv.gz}, written by {@code tools/make-heads.py}):
 * one head per line, texture hash, category, name and tags separated by tabs.
 */
public final class HeadCatalog {
    /** The category of the classics, first in the browser. */
    public static final Category CLASSICS = new Category(0, "classics");
    /** The Heads Database's categories in the browser's order, after the classics; make-heads.py lists the same. */
    public static final List<String> CATEGORIES = List.of("alphabet", "animals", "blocks", "decoration", "food-drinks",
            "humans", "humanoid", "miscellaneous", "monsters", "plants");
    private static final String DATABASE = "/skullsanctum/heads.tsv.gz";

    /**
     * One head: its catalog uuid (the texture hash, {@code classic-<account>} for the classics), name, texture hash on
     * textures.minecraft.net, category index and search text: the name's words, then the tags' ({@link #key}); the
     * name's share ends at {@code nameEnd}.
     */
    public record Head(String uuid, String name, String hash, int category, String search, int nameEnd) {
        /** The base64 texture property of the head's profile. */
        public String texture() {
            return textureValue(hash);
        }
    }

    public record Category(int id, String name) {
    }

    /** A head as the files list it: {@code category} is a name of {@link #CATEGORIES} or {@code classics}. */
    public record Entry(String uuid, String name, String hash, String category, String tags) {
    }

    private static volatile List<Head> heads = List.of();
    private static volatile List<Category> categories = List.of();
    private static volatile Map<String, Integer> byUuid = Map.of();

    private HeadCatalog() {
    }

    public static List<Head> heads() {
        return heads;
    }

    public static List<Category> categories() {
        return categories;
    }

    /** The head with this catalog uuid, or -1. */
    public static int indexOf(String uuid) {
        return byUuid.getOrDefault(uuid, -1);
    }

    public static void start(MinecraftServer server) {
        long begin = System.nanoTime();
        List<Entry> all = new ArrayList<>(classics());
        all.addAll(database());
        install(all);
        SkullSanctum.LOGGER.info("Head catalog: {} heads in {} categories loaded in {} ms", heads.size(),
                categories.size(), (System.nanoTime() - begin) / 1_000_000);
    }

    /** The classics, read from the jar (every start: the files are not kept, the catalog built from them is). */
    private static List<Entry> classics() {
        List<Entry> list = new ArrayList<>();
        try (InputStream in = HeadCatalog.class.getResourceAsStream("/skullsanctum/classics.json")) {
            if (in == null) throw new IOException("missing from the jar");
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("heads")) {
                JsonObject head = element.getAsJsonObject();
                String account = head.get("account").getAsString();
                list.add(new Entry("classic-" + account.toLowerCase(Locale.ROOT), head.get("name").getAsString(),
                        head.get("texture").getAsString(), CLASSICS.name(), "classic mhf " + account));
            }
        } catch (IOException | RuntimeException e) {
            SkullSanctum.LOGGER.warn("Could not read the classic heads: {}", e.toString());
        }
        return list;
    }

    /** The Heads Database, read from the jar; empty (with a warning) when it cannot be read. */
    private static List<Entry> database() {
        List<Entry> list = new ArrayList<>();
        int skipped = 0;
        try (InputStream raw = HeadCatalog.class.getResourceAsStream(DATABASE)) {
            if (raw == null) throw new IOException("missing from the jar");
            BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw, 1 << 16), StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                Entry entry = parseLine(line);
                if (entry == null) skipped++;
                else list.add(entry);
            }
        } catch (IOException | RuntimeException e) {
            SkullSanctum.LOGGER.warn("Could not read the head database {}: {}", DATABASE, e.toString());
            list.clear();
        }
        if (skipped > 0) SkullSanctum.LOGGER.warn("Head database: {} lines skipped", skipped);
        return list;
    }

    /** One line of heads.tsv.gz ({@code hash \t category \t name \t tags}), or null when it is not one. */
    public static Entry parseLine(String line) {
        String[] parts = line.split("\t", 4);
        if (parts.length < 3 || parts[0].isEmpty() || parts[2].isBlank() || !CATEGORIES.contains(parts[1])) return null;
        return new Entry(parts[0], parts[2], parts[0], parts[1], parts.length > 3 ? parts[3] : "");
    }

    /** Puts the classics and {@code entries} on offer, sorted by category, then name. */
    private static void install(List<Entry> entries) {
        List<Category> newCategories = new ArrayList<>();
        newCategories.add(CLASSICS);
        for (String name : CATEGORIES) newCategories.add(new Category(newCategories.size(), name));
        Map<String, Integer> categoryIndex = new HashMap<>();
        for (Category category : newCategories) categoryIndex.put(category.name(), category.id());
        List<Head> newHeads = new ArrayList<>(entries.size());
        Set<String> seen = new HashSet<>(entries.size() * 2);
        for (Entry e : entries) {
            Integer category = categoryIndex.get(e.category());
            if (e.hash() == null || e.hash().isEmpty() || e.name() == null || category == null) continue;
            if (!seen.add(e.uuid())) continue;
            String tags = e.tags() == null ? "" : e.tags();
            String name = key(e.name());
            newHeads.add(new Head(e.uuid(), e.name(), e.hash(), category, name + " " + key(tags), name.length()));
        }
        newHeads.sort((a, b) -> a.category() != b.category() ? Integer.compare(a.category(), b.category())
                : a.name().compareToIgnoreCase(b.name()));
        Map<String, Integer> newByUuid = new HashMap<>(newHeads.size() * 2);
        for (int i = 0; i < newHeads.size(); i++) newByUuid.put(newHeads.get(i).uuid(), i);
        categories = List.copyOf(newCategories);
        heads = List.copyOf(newHeads);
        byUuid = Map.copyOf(newByUuid);
    }

    /**
     * A text as the search sees it: lower case, letters and digits only, one space between words, so that a search
     * word is matched against the starts of words ({@code olive} finds Olive Pizza and "Olive (black)", not Hololive).
     */
    public static String key(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean space = true; // no space at the start, none doubled
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') end--;
        return out.substring(0, end);
    }

    /** The base64 texture property for a texture hash on textures.minecraft.net. */
    public static String textureValue(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** For the self-test: the classics and these heads only. */
    public static void installForTest(List<Entry> testHeads) {
        List<Entry> all = new ArrayList<>(classics());
        all.addAll(testHeads);
        install(all);
    }
}
