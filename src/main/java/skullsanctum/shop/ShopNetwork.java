package skullsanctum.shop;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import skullsanctum.Config;
import skullsanctum.SkullSanctum;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.altar.Sanctum;
import skullsanctum.atlas.HeadAtlasBlockEntity;
import skullsanctum.portable.PortableAtlasBlockEntity;
import skullsanctum.tank.SoulTankBlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The head browser's packets. The client never holds the catalog: it asks for one page at a time (a category, a
 * search or the favourites) and the server answers with the heads on it. A search word matches the start of a word
 * of a head's name or tags; heads matched by name come first ({@link #score}). Every request names its anchor, the
 * block that was clicked: a Head Atlas, which belongs to the sanctum around it, or a portable Head Atlas, which is
 * bound to an altar anywhere. Buying is checked on the server: the player must stand near the anchor, the sanctum
 * must be awake, the souls come from its tank first, then from the player's inventory. On join the client learns the
 * server's tank capacity and player head price for the guide book ({@link Settings}).
 */
public final class ShopNetwork {
    /** Heads per page: the browser's grid of 9 by 7. */
    public static final int PAGE = 63;
    public static final int MODE_ALL = -1, MODE_FAVOURITES = -2;
    /** The index of the entry that stands for the head of the player named in the search. */
    public static final int PLAYER_ENTRY = -1;
    public static final double REACH = 8.0;
    /** The {@link Result} message when the sanctum is out of reach or asleep: the client closes the browser. */
    public static final String GONE = "gui.skullsanctum.browser.gone";
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private ShopNetwork() {
    }

    /** Server → client on join: what the server's config says, for the guide book. */
    public record Settings(int tankCapacity, int playerHeadPrice) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(SkullSanctum.id("settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Settings::tankCapacity,
                ByteBufCodecs.VAR_INT, Settings::playerHeadPrice,
                Settings::new);

        @Override
        public Type<Settings> type() {
            return TYPE;
        }
    }

    public record CategoryInfo(String name, int count, int price) {
        static final StreamCodec<RegistryFriendlyByteBuf, CategoryInfo> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), CategoryInfo::name,
                ByteBufCodecs.VAR_INT, CategoryInfo::count,
                ByteBufCodecs.VAR_INT, CategoryInfo::price,
                CategoryInfo::new);
    }

    public record Entry(int index, String name, String uuid, String texture, int price, boolean favourite) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::index,
                ByteBufCodecs.stringUtf8(256), Entry::name,
                ByteBufCodecs.stringUtf8(64), Entry::uuid,
                ByteBufCodecs.stringUtf8(2048), Entry::texture,
                ByteBufCodecs.VAR_INT, Entry::price,
                ByteBufCodecs.BOOL, Entry::favourite,
                Entry::new);
    }

    /**
     * Server → client: open the browser at this anchor (a portable Head Atlas, or the sanctum's own); the tank's souls
     * and capacity fill its vial.
     */
    public record Open(BlockPos anchor, boolean portable, List<CategoryInfo> categories, int playerHeadPrice, int balance, int tankSouls,
                       int tankCapacity) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(SkullSanctum.id("open_browser"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::anchor,
                ByteBufCodecs.BOOL, Open::portable,
                CategoryInfo.CODEC.apply(ByteBufCodecs.list(256)), Open::categories,
                ByteBufCodecs.VAR_INT, Open::playerHeadPrice,
                ByteBufCodecs.VAR_INT, Open::balance,
                ByteBufCodecs.VAR_INT, Open::tankSouls,
                ByteBufCodecs.VAR_INT, Open::tankCapacity,
                Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Client → server: a page of a category ({@code mode} ≥ 0), of all heads or of the favourites. */
    public record Query(BlockPos anchor, int mode, String search, int page) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(SkullSanctum.id("query_heads"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Query::anchor,
                ByteBufCodecs.VAR_INT, Query::mode,
                ByteBufCodecs.stringUtf8(64), Query::search,
                ByteBufCodecs.VAR_INT, Query::page,
                Query::new);

        @Override
        public Type<Query> type() {
            return TYPE;
        }
    }

    /** Server → client: the heads of one page. */
    public record Page(int mode, String search, int page, int pages, int total, List<Entry> entries, int balance, int tankSouls)
            implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(SkullSanctum.id("head_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Page::mode,
                ByteBufCodecs.stringUtf8(64), Page::search,
                ByteBufCodecs.VAR_INT, Page::page,
                ByteBufCodecs.VAR_INT, Page::pages,
                ByteBufCodecs.VAR_INT, Page::total,
                Entry.CODEC.apply(ByteBufCodecs.list(PAGE)), Page::entries,
                ByteBufCodecs.VAR_INT, Page::balance,
                ByteBufCodecs.VAR_INT, Page::tankSouls,
                Page::new);

        @Override
        public Type<Page> type() {
            return TYPE;
        }
    }

    /** Client → server: buy {@code count} of the catalog head at {@code index}. */
    public record Buy(BlockPos anchor, int index, String uuid, int count) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(SkullSanctum.id("buy_head"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Buy::anchor,
                ByteBufCodecs.VAR_INT, Buy::index,
                ByteBufCodecs.stringUtf8(64), Buy::uuid,
                ByteBufCodecs.VAR_INT, Buy::count,
                Buy::new);

        @Override
        public Type<Buy> type() {
            return TYPE;
        }
    }

    /** Client → server: buy {@code count} heads of the player with this name. */
    public record BuyPlayer(BlockPos anchor, String name, int count) implements CustomPacketPayload {
        public static final Type<BuyPlayer> TYPE = new Type<>(SkullSanctum.id("buy_player_head"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BuyPlayer> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, BuyPlayer::anchor,
                ByteBufCodecs.stringUtf8(16), BuyPlayer::name,
                ByteBufCodecs.VAR_INT, BuyPlayer::count,
                BuyPlayer::new);

        @Override
        public Type<BuyPlayer> type() {
            return TYPE;
        }
    }

    /** Client → server: mark a head as favourite or not. */
    public record Favourite(String uuid, boolean on) implements CustomPacketPayload {
        public static final Type<Favourite> TYPE = new Type<>(SkullSanctum.id("favourite_head"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Favourite> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Favourite::uuid,
                ByteBufCodecs.BOOL, Favourite::on,
                Favourite::new);

        @Override
        public Type<Favourite> type() {
            return TYPE;
        }
    }

    /** Server → client: what a purchase did (a translation key and its argument), the souls left and the tank's. */
    public record Result(boolean bought, String message, String argument, int balance, int tankSouls) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(SkullSanctum.id("purchase_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Result::bought,
                ByteBufCodecs.stringUtf8(128), Result::message,
                ByteBufCodecs.stringUtf8(256), Result::argument,
                ByteBufCodecs.VAR_INT, Result::balance,
                ByteBufCodecs.VAR_INT, Result::tankSouls,
                Result::new);

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Settings.TYPE, Settings.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Page.TYPE, Page.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Result.TYPE, Result.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Query.TYPE, Query.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Buy.TYPE, Buy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuyPlayer.TYPE, BuyPlayer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Favourite.TYPE, Favourite.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sender.sendPacket(new Settings(Config.get().tankCapacity(), Config.get().playerHeadPrice())));
        ServerPlayNetworking.registerGlobalReceiver(Query.TYPE, (payload, context) -> {
            SacrificialAltarBlockEntity altar = altarInReach(context.player(), payload.anchor());
            ServerPlayNetworking.send(context.player(), altar == null ? gone(context.player()) : page(context.player(), altar, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(Buy.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            SacrificialAltarBlockEntity altar = altarInReach(player, payload.anchor());
            if (altar == null) {
                ServerPlayNetworking.send(player, gone(player));
                return;
            }
            List<HeadCatalog.Head> heads = HeadCatalog.heads();
            int index = payload.index();
            // the catalog may have been loaded anew since the page was sent: find the head by its uuid then
            if (index < 0 || index >= heads.size() || !heads.get(index).uuid().equals(payload.uuid())) {
                index = HeadCatalog.indexOf(payload.uuid());
            }
            if (index < 0) return;
            HeadCatalog.Head head = heads.get(index);
            int price = Config.get().price(HeadCatalog.categories().get(head.category()).name());
            ServerPlayNetworking.send(player, buy(player, altar, HeadStacks.of(head, 1), head.name(), price, payload.count()));
        });
        ServerPlayNetworking.registerGlobalReceiver(BuyPlayer.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String name = payload.name().trim();
            if (altarInReach(player, payload.anchor()) == null) {
                ServerPlayNetworking.send(player, gone(player));
                return;
            }
            if (!PLAYER_NAME.matcher(name).matches()) {
                ServerPlayNetworking.send(player, new Result(false, "gui.skullsanctum.browser.bad_name", name, Wallet.balance(player, null), 0));
                return;
            }
            MinecraftServer server = player.level().getServer();
            CompletableFuture.supplyAsync(() -> server.services().profileResolver().fetchByName(name), Util.nonCriticalIoPool())
                    .whenCompleteAsync((profile, error) -> {
                        if (player.isRemoved()) return;
                        SacrificialAltarBlockEntity altar = altarInReach(player, payload.anchor());
                        if (altar == null) {
                            ServerPlayNetworking.send(player, gone(player));
                            return;
                        }
                        if (error != null || profile == null || profile.isEmpty()) {
                            ServerPlayNetworking.send(player, new Result(false, "gui.skullsanctum.browser.no_player", name,
                                    Wallet.balance(player, altar), tankSouls(altar)));
                            return;
                        }
                        String found = profile.get().name();
                        ServerPlayNetworking.send(player, buy(player, altar, HeadStacks.player(found, 1), found,
                                Config.get().playerHeadPrice(), payload.count()));
                    }, server);
        });
        ServerPlayNetworking.registerGlobalReceiver(Favourite.TYPE, (payload, context) -> {
            if (HeadCatalog.indexOf(payload.uuid()) < 0) return;
            Favourites.get(context.player().level().getServer()).set(context.player().getUUID(), payload.uuid(), payload.on());
        });
    }

    /** A Head Atlas at {@code anchor} (the sanctum's own, or a portable one bound to {@code altar}) was used: the browser opens. */
    public static void openBrowser(ServerPlayer player, BlockPos anchor, SacrificialAltarBlockEntity altar, boolean portable) {
        List<CategoryInfo> infos = new ArrayList<>();
        List<HeadCatalog.Category> categories = HeadCatalog.categories();
        int[] counts = new int[categories.size()];
        for (HeadCatalog.Head head : HeadCatalog.heads()) counts[head.category()]++;
        for (int i = 0; i < categories.size() && i < 256; i++) {
            String name = categories.get(i).name();
            infos.add(new CategoryInfo(name, counts[i], Config.get().price(name)));
        }
        ServerPlayNetworking.send(player, new Open(anchor, portable, infos, Config.get().playerHeadPrice(),
                Wallet.balance(player, altar), tankSouls(altar), Config.get().tankCapacity()));
    }

    /** The sanctum is out of reach or asleep: the client closes the browser. */
    private static Result gone(ServerPlayer player) {
        return new Result(false, GONE, "", Wallet.balance(player, null), 0);
    }

    /** The souls in the sanctum's tank, 0 without one. */
    static int tankSouls(SacrificialAltarBlockEntity altar) {
        SoulTankBlockEntity tank = altar.tank();
        return tank == null ? 0 : tank.souls();
    }

    public static Page page(ServerPlayer player, SacrificialAltarBlockEntity altar, Query query) {
        List<HeadCatalog.Head> heads = HeadCatalog.heads();
        List<HeadCatalog.Category> categories = HeadCatalog.categories();
        Set<String> favourites = Favourites.get(player.level().getServer()).of(player.getUUID());
        String typed = query.search().trim();
        // one pass over the catalog keeps the page asked for and counts the rest; a page past the end is asked again
        Window window = window(heads, favourites, query.mode(), typed, Math.max(0, query.page()));
        int pages = Math.max(1, (window.total() + PAGE - 1) / PAGE);
        if (window.page() >= pages) window = window(heads, favourites, query.mode(), typed, pages - 1);
        List<Entry> entries = new ArrayList<>();
        for (int index : window.indices()) {
            if (index == PLAYER_ENTRY) {
                entries.add(new Entry(PLAYER_ENTRY, typed, "", "", Config.get().playerHeadPrice(), false));
                continue;
            }
            HeadCatalog.Head head = heads.get(index);
            entries.add(new Entry(index, head.name(), head.uuid(), head.texture(),
                    Config.get().price(categories.get(head.category()).name()), favourites.contains(head.uuid())));
        }
        return new Page(query.mode(), query.search(), window.page(), pages, window.total(), entries, Wallet.balance(player, altar), tankSouls(altar));
    }

    /** The catalog indices on page {@code page} of what matches, and how many match in all. */
    private record Window(int page, int total, List<Integer> indices) {
    }

    /** Keeps match number {@code position} when it lies on page {@code page}. */
    private static void offer(List<Integer> indices, int page, int position, int index) {
        if (position >= page * PAGE && position < (page + 1) * PAGE) indices.add(index);
    }

    private static Window window(List<HeadCatalog.Head> heads, Set<String> favourites, int mode, String typed, int page) {
        String search = HeadCatalog.key(typed);
        String[] words = search.isEmpty() ? new String[0] : search.split(" ");
        List<Integer> indices = new ArrayList<>(PAGE);
        int total = 0;
        // a search that could be a player's name offers that player's head first
        if (mode != MODE_FAVOURITES && PLAYER_NAME.matcher(typed).matches()) {
            offer(indices, page, total++, PLAYER_ENTRY);
        }
        // without a search the catalog's order (category, name) is the order; a search ranks its matches
        LongArrayList ranked = new LongArrayList();
        int first = total;
        if (mode == MODE_FAVOURITES) {
            for (String uuid : favourites) {
                int index = HeadCatalog.indexOf(uuid);
                if (index < 0) continue;
                int score = score(heads.get(index), search, words);
                if (score < 0) continue;
                if (words.length == 0) offer(indices, page, total, index);
                else ranked.add((long) score << 32 | index);
                total++;
            }
        } else {
            for (int i = 0; i < heads.size(); i++) {
                HeadCatalog.Head head = heads.get(i);
                if (mode >= 0 && head.category() != mode) continue;
                int score = score(head, search, words);
                if (score < 0) continue;
                if (words.length == 0) offer(indices, page, total, i);
                else ranked.add((long) score << 32 | i);
                total++;
            }
        }
        if (words.length > 0) {
            long[] sorted = ranked.elements();
            Arrays.sort(sorted, 0, ranked.size()); // by score, then by catalog order
            for (int k = Math.max(0, page * PAGE - first); k < ranked.size() && first + k < (page + 1) * PAGE; k++) {
                offer(indices, page, first + k, (int) sorted[k]);
            }
        }
        return new Window(page, total, indices);
    }

    /**
     * How well a head matches the search ({@code words} are {@code search} split): -1 not at all, 0 its name is the
     * search, 1 its name starts with it, 2 every word starts a word of its name, 3 every word starts a word of its
     * name or its tags.
     */
    static int score(HeadCatalog.Head head, String search, String[] words) {
        if (words.length == 0) return 0;
        String text = head.search();
        boolean byName = true;
        for (String word : words) {
            int at = wordStart(text, word);
            if (at < 0) return -1;
            if (at >= head.nameEnd()) byName = false;
        }
        if (!byName) return 3;
        if (!text.startsWith(search)) return 2;
        return head.nameEnd() == search.length() ? 0 : 1;
    }

    /** Where {@code word} first starts a word of {@code text}, or -1. */
    private static int wordStart(String text, String word) {
        if (text.startsWith(word)) return 0;
        int at = text.indexOf(" " + word);
        return at < 0 ? -1 : at + 1;
    }

    /** Charges {@code count × price} souls and hands over {@code count} of {@code head}, or says why not. */
    public static Result buy(ServerPlayer player, SacrificialAltarBlockEntity altar, ItemStack head, String name, int price, int count) {
        count = Math.clamp(count, 1, head.getMaxStackSize());
        int cost = price * count;
        if (Wallet.balance(player, altar) < cost) {
            return new Result(false, "gui.skullsanctum.browser.too_poor", String.valueOf(cost), Wallet.balance(player, altar), tankSouls(altar));
        }
        Wallet.pay(player, altar, cost);
        ItemStack bought = head.copyWithCount(count);
        if (!player.getInventory().add(bought)) player.drop(bought, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        SoulTankBlockEntity tank = altar.tank();
        SkullSanctum.LOGGER.debug("{} bought {}x {} for {} souls (tank left {})", player.getName().getString(), count, name,
                cost, tank == null ? 0 : tank.souls());
        return new Result(true, "gui.skullsanctum.browser.bought", count + "× " + name, Wallet.balance(player, altar), tankSouls(altar));
    }

    /**
     * The awake altar behind the anchor at {@code pos} if the player stands near it, otherwise null: a Head Atlas
     * names the sanctum around it, a portable Head Atlas the altar it is bound to (in whatever dimension), and the
     * altar itself stands for its sanctum.
     */
    public static SacrificialAltarBlockEntity altarInReach(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) return null;
        BlockEntity anchor = player.level().getBlockEntity(pos);
        SacrificialAltarBlockEntity altar = switch (anchor) {
            case SacrificialAltarBlockEntity own -> own;
            case HeadAtlasBlockEntity table -> Sanctum.altarOfTable(player.level(), pos);
            case PortableAtlasBlockEntity skull -> skull.altar(player.level().getServer());
            case null, default -> null;
        };
        return altar != null && altar.formed() ? altar : null;
    }
}
