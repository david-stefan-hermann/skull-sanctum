package skullsanctum.altar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import skullsanctum.SkullSanctum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What an item is worth on the altar, in souls (fractions add up in the altar). Data packs set it in
 * {@code data/<namespace>/sacrifice_values/*.json}: {@code {"values": {"minecraft:diamond": 6, "#minecraft:logs": 0.1}}}.
 * Files are read in name order, a later one overrides an earlier one, {@code "replace": true} drops what came before,
 * {@code "default"} sets what an item named nowhere gives. An item named directly wins over its tags; an item named
 * nowhere is worth its rarity's souls ({@link #rarityValue}), a common one the default. A soul gives one soul.
 */
public final class SacrificeValues {
    private static Map<Item, Double> items = Map.of();
    private static List<Map.Entry<TagKey<Item>, Double>> tags = List.of();
    /** What a common item named nowhere gives: a stack is about a soul. */
    public static final double DEFAULT = 1.0 / 64;
    private static double fallback = DEFAULT;

    private SacrificeValues() {
    }

    record Entry(boolean replace, java.util.Optional<Double> fallback, Map<String, Double> values) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(Entry::replace),
                Codec.DOUBLE.optionalFieldOf("default").forGetter(Entry::fallback),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("values").forGetter(Entry::values)
        ).apply(i, Entry::new));
    }

    /** Souls one of this item gives; 0 (only when a data pack says so) means the altar does not take it. */
    public static double value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(SkullSanctum.SOUL)) return 1;
        Double direct = items.get(stack.getItem());
        if (direct != null) return direct;
        for (Map.Entry<TagKey<Item>, Double> tag : tags) {
            if (stack.is(tag.getKey())) return tag.getValue();
        }
        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        return rarity == Rarity.COMMON ? fallback : rarityValue(rarity);
    }

    static double rarityValue(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> fallback;
            case UNCOMMON -> 2;
            case RARE -> 5;
            case EPIC -> 10;
        };
    }

    /** For the self-test. */
    public static int size() {
        return items.size() + tags.size();
    }

    static void apply(Map<Identifier, Entry> files) {
        Map<String, Double> merged = new LinkedHashMap<>();
        double newFallback = DEFAULT;
        for (Entry entry : new TreeMap<>(files).values()) {
            if (entry.replace()) {
                merged.clear();
                newFallback = DEFAULT;
            }
            if (entry.fallback().isPresent()) newFallback = Math.max(0, entry.fallback().get());
            merged.putAll(entry.values());
        }
        fallback = newFallback;
        Map<Item, Double> newItems = new HashMap<>();
        List<Map.Entry<TagKey<Item>, Double>> newTags = new ArrayList<>();
        merged.forEach((key, value) -> {
            double souls = Math.max(0, value);
            if (key.startsWith("#")) {
                Identifier id = Identifier.tryParse(key.substring(1));
                if (id != null) newTags.add(Map.entry(TagKey.create(Registries.ITEM, id), souls));
                return;
            }
            Identifier id = Identifier.tryParse(key);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                SkullSanctum.LOGGER.debug("Sacrifice value for unknown item {}", key);
                return;
            }
            newItems.put(BuiltInRegistries.ITEM.getValue(id), souls);
        });
        items = newItems;
        tags = newTags;
        SkullSanctum.LOGGER.info("Sacrifice values: {} items, {} tags", newItems.size(), newTags.size());
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<Entry> {
        public Loader() {
            super(Entry.CODEC, FileToIdConverter.json("sacrifice_values"));
        }

        @Override
        protected void apply(Map<Identifier, Entry> files, ResourceManager manager, ProfilerFiller profiler) {
            SacrificeValues.apply(files);
        }
    }
}
