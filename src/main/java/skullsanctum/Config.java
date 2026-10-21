package skullsanctum;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code config/skullsanctum.json}, read by the server: the head prices and the soul tank. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** The tank's souls travel to the screen in a 16-bit data slot. */
    public static final int MAX_TANK = 30_000;
    private static Values values = new Values();
    /** What the server this client plays on uses, told on join (ShopNetwork.Settings); its own values until then. */
    private static int shownTankCapacity = -1, shownPlayerHeadPrice = -1;

    /** Gson fills the fields; missing ones keep these defaults. */
    public static final class Values {
        /** Souls one soul tank holds. */
        int tankCapacity = 4096;
        /** Souls a head costs in a category not listed in {@link #categoryPrices}. */
        int defaultPrice = 2;
        /** Souls a head costs, by the catalog's category name. */
        Map<String, Integer> categoryPrices = defaultPrices();
        /** Souls the head of a player looked up by name costs. */
        int playerHeadPrice = 5;
        /** How long the soul tank burns after a sacrifice, in ticks. */
        int burnTicks = 50;

        private static Map<String, Integer> defaultPrices() {
            Map<String, Integer> prices = new LinkedHashMap<>();
            prices.put("classics", 2);
            prices.put("alphabet", 1);
            prices.put("animals", 3);
            prices.put("blocks", 2);
            prices.put("decoration", 3);
            prices.put("food-drinks", 2);
            prices.put("humans", 3);
            prices.put("humanoid", 3);
            prices.put("miscellaneous", 2);
            prices.put("monsters", 4);
            prices.put("plants", 2);
            return prices;
        }

        public int tankCapacity() {
            return Math.clamp(tankCapacity, 64, MAX_TANK);
        }

        public int price(String category) {
            Integer price = categoryPrices == null ? null : categoryPrices.get(category);
            return Math.clamp(price == null ? defaultPrice : price, 0, 10_000);
        }

        public int playerHeadPrice() {
            return Math.clamp(playerHeadPrice, 0, 10_000);
        }

        public int burnTicks() {
            return Math.clamp(burnTicks, 1, 1200);
        }
    }

    private Config() {
    }

    public static Values get() {
        return values;
    }

    /** For the guide book on a client: the tank capacity of the server it plays on. */
    public static int shownTankCapacity() {
        return shownTankCapacity < 0 ? values.tankCapacity() : shownTankCapacity;
    }

    /** For the guide book on a client: the player head price of the server it plays on. */
    public static int shownPlayerHeadPrice() {
        return shownPlayerHeadPrice < 0 ? values.playerHeadPrice() : shownPlayerHeadPrice;
    }

    public static void setShown(int tankCapacity, int playerHeadPrice) {
        shownTankCapacity = tankCapacity;
        shownPlayerHeadPrice = playerHeadPrice;
    }

    /** For the self-test: other values without touching the file. */
    static void set(Values values) {
        Config.values = values;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(SkullSanctum.MOD_ID + ".json");
        try {
            if (Files.exists(file)) {
                Values read = GSON.fromJson(Files.readString(file), Values.class);
                if (read != null) values = read;
            }
            Files.writeString(file, GSON.toJson(values));
        } catch (IOException | RuntimeException e) {
            SkullSanctum.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
        }
    }
}
