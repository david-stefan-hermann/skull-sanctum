package skullsanctum;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import skullsanctum.altar.AltarMenu;
import skullsanctum.altar.GhostBlocks;
import skullsanctum.altar.SacrificeValues;
import skullsanctum.altar.SacrificialAltarBlock;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.altar.Sanctum;
import skullsanctum.altar.SanctumPattern;
import skullsanctum.portable.PortableAtlasBlockEntity;
import skullsanctum.shop.Favourites;
import skullsanctum.shop.HeadCatalog;
import skullsanctum.shop.HeadStacks;
import skullsanctum.shop.ShopNetwork;
import skullsanctum.shop.Wallet;
import skullsanctum.tank.SoulTankBlock;
import skullsanctum.tank.SoulTankBlockEntity;
import skullsanctum.tank.SoulWindow;
import skullsanctum.tank.TankMenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Dev-only check, active with {@code -Dskullsanctum.selftest} ({@code ./gradlew runServer -PselfTest}): builds
 * sanctums on platforms high above spawn in every orientation, forms and breaks them, sacrifices by screen and by
 * throwing, takes souls out of the tank and checks that hoppers cannot, buys heads from a test catalog, binds a
 * portable Head Atlas and uses it from the Nether, checks the recipes and the book's keys, writes
 * run/skullsanctum-selftest/summary.txt and stops the server.
 */
final class DevSelfTest {
    private static final String PROPERTY = "skullsanctum.selftest";
    private static final int Y = 200;
    private static final int SLOTS = 20;
    private static final int WARM_UP = 20 * 120;
    private static int ticks;
    private static boolean done;

    private final ServerLevel level;
    private final FakePlayer alice;
    private final List<String> lines = new ArrayList<>();
    private int failures, checks, slot;

    private DevSelfTest(ServerLevel level) {
        this.level = level;
        alice = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "alice"));
    }

    static void register() {
        if (System.getProperty(PROPERTY) == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (int i = 0; i < SLOTS; i++) server.overworld().setChunkForced(i * 2, 0, true);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done || ++ticks % 20 != 0) return;
            ServerLevel level = server.overworld();
            boolean ready = true;
            for (int i = 0; i < SLOTS; i++) ready &= level.isPositionEntityTicking(new BlockPos(i * 32 + 8, Y, 8));
            if (ready || ticks >= WARM_UP) {
                done = true;
                run(server);
            }
        });
    }

    private static void run(MinecraftServer server) {
        DevSelfTest test = new DevSelfTest(server.overworld());
        Config.Values defaults = Config.get();
        try {
            test.all(server);
        } catch (RuntimeException e) {
            test.check("no exception", false, e.toString());
            SkullSanctum.LOGGER.error("self-test crashed", e);
        } finally {
            Config.set(defaults);
        }
        test.lines.add(String.format("RESULT: %d checks, %d failures", test.checks, test.failures));
        Path out = FabricLoader.getInstance().getGameDir().resolve("skullsanctum-selftest");
        try {
            Files.createDirectories(out);
            Files.write(out.resolve("summary.txt"), test.lines);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        test.lines.forEach(l -> System.out.println("[skullsanctum-selftest] " + l));
        server.halt(false);
    }

    private void all(MinecraftServer server) {
        lines.add("platform chunks ready after " + ticks + " ticks");
        Config.set(new Config.Values());
        check("sacrifice values loaded", SacrificeValues.size() > 50, String.valueOf(SacrificeValues.size()));
        forming();
        orientations();
        SacrificialAltarBlockEntity altar = formedSanctum(Direction.SOUTH);
        sacrifices(altar);
        thrown(altar);
        tank(altar);
        shop(server, altar);
        portable(server, altar);
        breaking();
        recipes(server);
        heads();
        bookKeys();
    }

    // ------------------------------------------------------------------ the sanctum

    private void forming() {
        int shrine = SanctumPattern.template(BlockPos.ZERO, Direction.NORTH).size();
        check("the suggested shrine has 19 blocks", shrine == 19, SacrificialAltarBlockEntity.bill().toString());

        BlockPos lonely = platform();
        level.setBlockAndUpdate(lonely, altarFacing(Direction.SOUTH));
        SacrificialAltarBlockEntity alone = altar(lonely);
        alone.use(alice);
        check("an altar alone does not wake", !alone.formed(), "");
        check("it shows the whole shrine as ghosts", GhostBlocks.shown() == shrine, String.valueOf(GhostBlocks.shown()));
        GhostBlocks.clear();

        // only the core: a cauldron below and to the left, the table up and to the right, all in one 3x3x3 box
        BlockPos core = platform().above();
        level.setBlockAndUpdate(core, altarFacing(Direction.NORTH));
        level.setBlockAndUpdate(core.offset(-1, -1, 1), Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(core.offset(1, 1, -1), Blocks.ENCHANTING_TABLE.defaultBlockState());
        SacrificialAltarBlockEntity bare = altar(core);
        bare.use(alice);
        check("altar, cauldron and table in one 3x3x3 box wake it, without any decoration", bare.formed()
                && level.getBlockState(core.offset(-1, -1, 1)).is(SkullSanctum.SOUL_TANK), "");
        check("its enchanting table becomes the Head Atlas and belongs to it", level.getBlockState(core.offset(1, 1, -1)).is(SkullSanctum.HEAD_ATLAS)
                && Sanctum.altarOfTable(level, core.offset(1, 1, -1)) == bare, level.getBlockState(core.offset(1, 1, -1)).toString());
        check("its tank belongs to it", Sanctum.altarOfTank(level, core.offset(-1, -1, 1)) == bare, "");

        BlockPos far = platform();
        level.setBlockAndUpdate(far, altarFacing(Direction.NORTH));
        level.setBlockAndUpdate(far.offset(-2, 0, 0), Blocks.CAULDRON.defaultBlockState());
        level.setBlockAndUpdate(far.offset(1, 0, 0), Blocks.ENCHANTING_TABLE.defaultBlockState());
        SacrificialAltarBlockEntity apart = altar(far);
        apart.use(alice);
        check("cauldron and table four blocks apart do not wake it", !apart.formed(), "");
        GhostBlocks.clear();

        BlockPos wet = platform();
        level.setBlockAndUpdate(wet, altarFacing(Direction.NORTH));
        level.setBlockAndUpdate(wet.west(), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        level.setBlockAndUpdate(wet.east(), Blocks.ENCHANTING_TABLE.defaultBlockState());
        SacrificialAltarBlockEntity filled = altar(wet);
        filled.use(alice);
        check("a cauldron with water in it wakes it too", filled.formed() && level.getBlockState(wet.west()).is(SkullSanctum.SOUL_TANK), "");

        BlockPos orphan = platform();
        level.setBlockAndUpdate(orphan, SkullSanctum.HEAD_ATLAS.defaultBlockState());
        level.getBlockState(orphan).useWithoutItem(level, alice, new BlockHitResult(Vec3.atCenterOf(orphan), Direction.UP, orphan, false));
        check("a Head Atlas without a sanctum becomes an enchanting table when clicked", level.getBlockState(orphan).is(Blocks.ENCHANTING_TABLE),
                level.getBlockState(orphan).toString());

        SacrificialAltarBlockEntity full = formedSanctum(Direction.SOUTH);
        check("the full shrine wakes it, the cauldron becomes the tank", full.formed()
                && level.getBlockState(full.tankPos()).is(SkullSanctum.SOUL_TANK)
                && level.getBlockState(full.getBlockPos()).getValue(SacrificialAltarBlock.FORMED), full.tankPos().toShortString());
        check("nothing of the shrine is missing", SanctumPattern.missing(level, full.getBlockPos(), Direction.SOUTH).isEmpty(), "");
        level.removeBlock(full.getBlockPos().east(), false); // the skull beside the altar
        alice.setShiftKeyDown(true);
        full.use(alice);
        alice.setShiftKeyDown(false);
        check("sneaking, the awake altar shows its missing decoration", full.formed() && GhostBlocks.shown() == 1,
                String.valueOf(GhostBlocks.shown()));
        GhostBlocks.clear();
    }

    private void orientations() {
        List<String> failed = new ArrayList<>();
        for (Direction front : Direction.Plane.HORIZONTAL) {
            SacrificialAltarBlockEntity altar = formedSanctum(front);
            BlockPos expectedTank = altar.getBlockPos().relative(front.getCounterClockWise()).relative(front.getOpposite());
            if (!altar.tankPos().equals(expectedTank)) failed.add(front.toString());
        }
        check("the shrine works facing every way", failed.isEmpty(), String.join(", ", failed));
    }

    private void sacrifices(SacrificialAltarBlockEntity altar) {
        SoulTankBlockEntity tank = altar.tank();
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64), diamonds = new ItemStack(Items.DIAMOND, 2);
        SacrificialAltarBlockEntity.Sacrifice result = altar.sacrifice(List.of(cobble, diamonds));
        check("64 cobblestone and 2 diamonds give 12 souls", result.souls() == 12 && tank.souls() == 12,
                result + ", tank " + tank.souls());
        check("they are burned", cobble.isEmpty() && diamonds.isEmpty(), "");
        check("the fraction waits in the altar", Math.abs(altar.residue() - 0.96) < 1e-6, String.valueOf(altar.residue()));
        check("an item named nowhere still gives a 64th of a soul", SacrificeValues.value(new ItemStack(Items.STICK)) == 1.0 / 64, "");
        check("a soul gives one soul", SacrificeValues.value(new ItemStack(SkullSanctum.SOUL)) == 1, "");
        ItemStack souls = new ItemStack(SkullSanctum.SOUL, 7);
        int beforeSouls = tank.souls();
        altar.sacrifice(List.of(souls));
        check("7 sacrificed souls give 7 souls", souls.isEmpty() && tank.souls() == beforeSouls + 7, "tank " + tank.souls());
        check("an epic item without a value gives its rarity's souls",
                SacrificeValues.value(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)) > 0, "");
        altar.celebrate(level, result);
        check("the tank burns after a sacrifice", tank.isBurning() && level.getBlockState(tank.getBlockPos()).getValue(SoulTankBlock.BURNING), "");
        tank.setSouls(tank.capacity() - 1);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        altar.sacrifice(List.of(diamond));
        check("a full tank leaves what does not fit", diamond.getCount() == 1 && tank.souls() == tank.capacity() - 1, "");
        check("the fill level shows", level.getBlockState(tank.getBlockPos()).getValue(SoulTankBlock.LEVEL) == 4, "");
        tank.setSouls(0);

        AltarMenu menu = new AltarMenu(0, alice.getInventory(), formedSanctum(Direction.NORTH));
        menu.slots.get(0).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.slots.get(1).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.slots.get(2).set(new ItemStack(Items.COBBLESTONE, 5));
        menu.broadcastChanges();
        check("the screen cuts the preview to hundredths: 133 cobblestone are 1.99 souls", menu.preview() == 1 && menu.previewFraction() == 99,
                menu.preview() + "." + menu.previewFraction());
    }

    private void thrown(SacrificialAltarBlockEntity altar) {
        BlockPos pos = altar.getBlockPos();
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new ItemStack(Items.GOLD_INGOT, 3));
        level.addFreshEntity(item);
        int before = altar.tank().souls();
        for (int i = 0; i < 10; i++) altar.serverTick(level);
        check("gold thrown onto the altar burns into 3 souls", item.isRemoved() && altar.tank().souls() == before + 3,
                "removed " + item.isRemoved() + ", souls " + altar.tank().souls());
    }

    private void tank(SacrificialAltarBlockEntity altar) {
        SoulTankBlockEntity tank = altar.tank();
        tank.setSouls(0);
        BlockPos tankPos = tank.getBlockPos();
        ItemEntity thrown = new ItemEntity(level, tankPos.getX() + 0.5, tankPos.getY() + 0.5, tankPos.getZ() + 0.5,
                new ItemStack(Items.IRON_INGOT, 4));
        level.addFreshEntity(thrown);
        for (int i = 0; i < 6; i++) tank.serverTick(level, level.getBlockState(tankPos));
        check("iron thrown into the tank burns into 2 souls", thrown.isRemoved() && tank.souls() == 2,
                "removed " + thrown.isRemoved() + ", souls " + tank.souls());
        tank.setSouls(100);
        SoulWindow window = new SoulWindow(tank);
        check("the tank's slot shows a stack of its 100 souls", window.getItem(0).getCount() == 64, String.valueOf(window.getItem(0).getCount()));
        ItemStack taken = window.removeItem(0, 10);
        check("taking 10 takes them out of the tank", taken.getCount() == 10 && taken.is(SkullSanctum.SOUL) && tank.souls() == 90, "tank " + tank.souls());
        window.getItem(0).grow(5);
        window.setChanged();
        check("5 put back go into the tank", tank.souls() == 95, "tank " + tank.souls());
        window.setItem(0, ItemStack.EMPTY);
        check("emptying the slot takes what it showed", tank.souls() == 31, "tank " + tank.souls());
        check("a hopper finds no container in the tank", HopperBlockEntity.getContainerAt(level, tank.getBlockPos()) == null, "");
        tank.setSouls(200);
        alice.getInventory().clearContent();
        TankMenu menu = new TankMenu(0, alice.getInventory(), new SoulWindow(tank), tank.data);
        menu.clicked(TankMenu.SLOT, 0, ContainerInput.QUICK_MOVE, alice);
        check("shift-clicking the tank's slot takes out one stack", alice.getInventory().countItem(SkullSanctum.SOUL) == 64
                && tank.souls() == 136, "carried " + alice.getInventory().countItem(SkullSanctum.SOUL) + ", tank " + tank.souls());
        alice.getInventory().setItem(0, new ItemStack(SkullSanctum.SOUL, 40));
        menu.clicked(TankMenu.FIRST_PLAYER + 27, 0, ContainerInput.QUICK_MOVE, alice); // the hotbar's first slot
        check("shift-clicking souls in the inventory puts them into the tank, past a full shown stack", tank.souls() == 176
                && alice.getInventory().getItem(0).isEmpty(), "tank " + tank.souls() + ", left " + alice.getInventory().getItem(0));
        ItemStack dropped = menu.slots.get(TankMenu.SLOT).safeInsert(new ItemStack(SkullSanctum.SOUL, 30));
        check("souls dropped onto the slot go into the tank past the shown stack", tank.souls() == 206 && dropped.isEmpty(),
                "tank " + tank.souls() + ", left " + dropped);
        tank.setSouls(tank.capacity());
        dropped = menu.slots.get(TankMenu.SLOT).safeInsert(new ItemStack(SkullSanctum.SOUL, 5));
        check("a full tank leaves them in the hand", dropped.getCount() == 5, "left " + dropped);
        menu.removed(alice);
        BlockState unlit = level.getBlockState(tank.getBlockPos()).setValue(SoulTankBlock.BURNING, false);
        check("the filled tank gives no light of its own (only its souls glow)", unlit.getValue(SoulTankBlock.LEVEL) > 0
                && unlit.getLightEmission() == 0, unlit + " -> " + unlit.getLightEmission());
        alice.getInventory().clearContent();
        tank.setSouls(0);
    }

    private void shop(MinecraftServer server, SacrificialAltarBlockEntity altar) {
        long classics = HeadCatalog.heads().stream().filter(h -> h.category() == 0).count();
        int decoration = 1 + HeadCatalog.CATEGORIES.indexOf("decoration");
        long decorations = HeadCatalog.heads().stream().filter(h -> h.category() == decoration).count();
        check("the 43 classics come first, then the 10 categories of the Heads Database",
                HeadCatalog.categories().get(0).equals(HeadCatalog.CLASSICS) && classics == 43
                        && HeadCatalog.categories().size() == 11, classics + " classics, " + HeadCatalog.categories().size() + " categories");
        check("the Heads Database is on offer: over 80000 heads, over 15000 decorations", HeadCatalog.heads().size() > 80_000
                && decorations > 15_000, HeadCatalog.heads().size() + " heads, " + decorations + " decorations");
        int barrel = HeadCatalog.indexOf("ffb985962f46e055f53d8ee35b11b8a62f397dad9db9feaeff4829630d9d9");
        check("a known head is there under its texture hash", barrel >= 0 && HeadCatalog.heads().get(barrel).name().equals("Beer Barrel")
                && HeadCatalog.heads().get(barrel).category() == decoration, barrel < 0 ? "missing" : HeadCatalog.heads().get(barrel).toString());
        ShopNetwork.Page second = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "", 1));
        check("the second page of all heads holds the " + ShopNetwork.PAGE + " heads after the first " + ShopNetwork.PAGE, second.page() == 1
                && second.entries().size() == ShopNetwork.PAGE && second.entries().get(0).index() == ShopNetwork.PAGE
                && second.total() == HeadCatalog.heads().size(), second.page() + ", " + second.entries().size());
        ShopNetwork.Page beyond = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "", 1_000_000));
        check("a page past the end is the last page", beyond.page() == beyond.pages() - 1 && !beyond.entries().isEmpty(),
                beyond.page() + " of " + beyond.pages());
        ShopNetwork.Page byName = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), decoration, "beer barrel", 0));
        ShopNetwork.Page byTag = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), decoration, "medieval tavern", 0));
        check("the database's heads are found by name and by tag", byName.entries().stream().anyMatch(e -> e.name().equals("Beer Barrel"))
                && byTag.total() > 1, byName.total() + " by name, " + byTag.total() + " by tag");
        HeadCatalog.installForTest(List.of(
                new HeadCatalog.Entry("11111111-1111-1111-1111-111111111111", "Zombie Pigman", "aa", "monsters", "zombie nether"),
                new HeadCatalog.Entry("22222222-2222-2222-2222-222222222222", "Creeper", "bb", "monsters", "green"),
                new HeadCatalog.Entry("33333333-3333-3333-3333-333333333333", "Letter A", "cc", "alphabet", "letter"),
                new HeadCatalog.Entry("44444444-4444-4444-4444-444444444444", "Usada Pekora", "dd", "humans", "Hololive Female"),
                new HeadCatalog.Entry("55555555-5555-5555-5555-555555555555", "Olive Pizza", "ee", "food-drinks", "Fast Food"),
                new HeadCatalog.Entry("66666666-6666-6666-6666-666666666666", "Bucket (olives)", "ff", "decoration", "Bucket Fruit"),
                new HeadCatalog.Entry("77777777-7777-7777-7777-777777777777", "Dove", "gg", "animals", "Bird Olive Branch"),
                new HeadCatalog.Entry("88888888-8888-8888-8888-888888888888", "Olive", "hh", "plants", "Fruit")));
        ShopNetwork.Page classicPage = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), 0, "", 0));
        check("with the test heads the classics stay first", classicPage.total() == 43, String.valueOf(classicPage.total()));
        int monstersTab = 1 + HeadCatalog.CATEGORIES.indexOf("monsters");
        ShopNetwork.Page monsters = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), monstersTab, "", 0));
        check("the monsters category holds its 2 test heads", monsters.total() == 2, String.valueOf(monsters.total()));
        ShopNetwork.Page search = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "nether", 0));
        check("a search finds a head by its tag (after the player head of that name)", search.total() == 2
                && search.entries().get(1).name().equals("Zombie Pigman"), String.valueOf(search.entries()));
        Favourites.get(server).set(alice.getUUID(), "33333333-3333-3333-3333-333333333333", true);
        ShopNetwork.Page favourites = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_FAVOURITES, "", 0));
        check("favourites list the marked head", favourites.total() == 1 && favourites.entries().get(0).favourite(), "");
        ShopNetwork.Page player = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "Notch", 0));
        check("a search for a player's name offers that player's head first", player.total() == 1
                && player.entries().get(0).index() == ShopNetwork.PLAYER_ENTRY && player.entries().get(0).name().equals("Notch"), "");
        ShopNetwork.Page phrase = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "letter a", 0));
        check("a search that is no name offers no player head", phrase.total() == 1 && phrase.entries().get(0).index() >= 0, "");
        ShopNetwork.Page olive = ShopNetwork.page(alice, altar, new ShopNetwork.Query(altar.getBlockPos(), ShopNetwork.MODE_ALL, "olive", 0));
        List<String> names = olive.entries().stream().map(ShopNetwork.Entry::name).toList();
        check("a search matches word starts and ranks names before tags: olive finds Olive, Olive Pizza, the olives and the dove, not Hololive",
                names.equals(List.of("olive", "Olive", "Olive Pizza", "Bucket (olives)", "Dove")), String.join(", ", names));
        check("the search key drops punctuation and case", HeadCatalog.key("  Olive Drab (#6b8e23) ").equals("olive drab 6b8e23"), HeadCatalog.key("  Olive Drab (#6b8e23) "));

        alice.getInventory().clearContent();
        alice.getInventory().add(new ItemStack(SkullSanctum.SOUL, 10));
        altar.tank().setSouls(5);
        check("the balance is tank plus inventory", Wallet.balance(alice, altar) == 15, String.valueOf(Wallet.balance(alice, altar)));
        HeadCatalog.Head creeper = HeadCatalog.heads().get(HeadCatalog.indexOf("22222222-2222-2222-2222-222222222222"));
        int price = Config.get().price("monsters");
        ShopNetwork.Result bought = ShopNetwork.buy(alice, altar, HeadStacks.of(creeper, 1), creeper.name(), price, 3);
        int heads = alice.getInventory().countItem(Items.PLAYER_HEAD);
        check("3 heads for " + 3 * price + " souls: the tank pays first", bought.bought() && heads == 3
                && altar.tank().souls() == 0 && Wallet.carried(alice.getInventory()) == 15 - 3 * price,
                bought + ", heads " + heads + ", tank " + altar.tank().souls());
        ShopNetwork.Result poor = ShopNetwork.buy(alice, altar, HeadStacks.of(creeper, 1), creeper.name(), price, 64);
        check("too few souls: nothing is bought or paid", !poor.bought() && Wallet.carried(alice.getInventory()) == 15 - 3 * price, poor.toString());
        HeadCatalog.start(server); // the real catalog again
    }

    private void portable(MinecraftServer server, SacrificialAltarBlockEntity altar) {
        BlockPos altarPos = altar.getBlockPos();
        ItemStack skull = new ItemStack(SkullSanctum.PORTABLE_ATLAS_ITEM);
        alice.setItemInHand(InteractionHand.MAIN_HAND, skull);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(altarPos), Direction.UP, altarPos, false);
        level.getBlockState(altarPos).useItemOn(skull, level, alice, InteractionHand.MAIN_HAND, hit);
        GlobalPos bound = skull.get(SkullSanctum.BOUND_ALTAR);
        check("a right click on the awake altar binds the skull to it", GlobalPos.of(level.dimension(), altarPos).equals(bound), String.valueOf(bound));

        ServerLevel nether = server.getLevel(Level.NETHER);
        BlockPos far = new BlockPos(1000, 100, 1000);
        nether.setBlockAndUpdate(far, SkullSanctum.PORTABLE_ATLAS.defaultBlockState());
        PortableAtlasBlockEntity inNether = (PortableAtlasBlockEntity) nether.getBlockEntity(far);
        inNether.applyComponentsFromItemStack(skull);
        check("set down in the Nether, it finds its altar in the overworld", inNether.altar(server) == altar, String.valueOf(inNether.boundAltar()));

        BlockPos near = platform();
        level.setBlockAndUpdate(near, SkullSanctum.PORTABLE_ATLAS.defaultBlockState());
        PortableAtlasBlockEntity placed = (PortableAtlasBlockEntity) level.getBlockEntity(near);
        placed.applyComponentsFromItemStack(skull);
        alice.setPos(near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5);
        check("the browser's requests from the skull reach the sanctum", ShopNetwork.altarInReach(alice, near) == altar, "");
        alice.setPos(near.getX() + 12.5, near.getY() + 1, near.getZ() + 0.5);
        check("not from 12 blocks away", ShopNetwork.altarInReach(alice, near) == null, "");
        alice.setPos(near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5);
        level.destroyBlock(near, true, alice);
        ItemStack dropped = ItemStack.EMPTY;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(2))) {
            if (item.getItem().is(SkullSanctum.PORTABLE_ATLAS_ITEM)) dropped = item.getItem();
        }
        check("breaking the skull drops it still bound", !dropped.isEmpty() && GlobalPos.of(level.dimension(), altarPos).equals(dropped.get(SkullSanctum.BOUND_ALTAR)),
                dropped.toString());

        BlockPos loose = platform();
        level.setBlockAndUpdate(loose, SkullSanctum.PORTABLE_ATLAS.defaultBlockState());
        alice.setPos(loose.getX() + 0.5, loose.getY() + 1, loose.getZ() + 0.5);
        check("an unbound skull opens nothing", ShopNetwork.altarInReach(alice, loose) == null, "");

        SacrificialAltarBlockEntity other = formedSanctum(Direction.NORTH);
        ItemStack second = new ItemStack(SkullSanctum.PORTABLE_ATLAS_ITEM);
        second.set(SkullSanctum.BOUND_ALTAR, GlobalPos.of(level.dimension(), other.getBlockPos()));
        BlockPos asleep = platform();
        level.setBlockAndUpdate(asleep, SkullSanctum.PORTABLE_ATLAS.defaultBlockState());
        ((PortableAtlasBlockEntity) level.getBlockEntity(asleep)).applyComponentsFromItemStack(second);
        alice.setPos(asleep.getX() + 0.5, asleep.getY() + 1, asleep.getZ() + 0.5);
        check("a skull of another sanctum reaches that one", ShopNetwork.altarInReach(alice, asleep) == other, "");
        other.unform(level);
        check("once that sanctum sleeps the skull opens nothing", ShopNetwork.altarInReach(alice, asleep) == null, "");
        alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private void breaking() {
        SacrificialAltarBlockEntity altar = formedSanctum(Direction.EAST);
        BlockPos tankPos = altar.tankPos(), tablePos = altar.tablePos();
        altar.tank().setSouls(70);
        level.removeBlock(tablePos, false);
        for (int i = 0; i < 20; i++) altar.serverTick(level);
        int dropped = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(tankPos).inflate(2))) {
            if (item.getItem().is(SkullSanctum.SOUL)) dropped += item.getItem().getCount();
        }
        check("without its Head Atlas the sanctum falls asleep", !altar.formed()
                && !level.getBlockState(altar.getBlockPos()).getValue(SacrificialAltarBlock.FORMED), "");
        check("the tank is a cauldron again and dropped its 70 souls", level.getBlockState(tankPos).is(Blocks.CAULDRON)
                && dropped == 70, level.getBlockState(tankPos) + ", dropped " + dropped);

        SacrificialAltarBlockEntity second = formedSanctum(Direction.WEST);
        BlockPos secondTank = second.tankPos(), secondTable = second.tablePos();
        level.destroyBlock(second.getBlockPos(), false);
        check("breaking the altar turns the tank back into a cauldron and the Head Atlas into an enchanting table",
                level.getBlockState(secondTank).is(Blocks.CAULDRON) && level.getBlockState(secondTable).is(Blocks.ENCHANTING_TABLE),
                level.getBlockState(secondTank) + ", " + level.getBlockState(secondTable));
    }

    // ------------------------------------------------------------------ recipes, heads, book

    private void recipes(MinecraftServer server) {
        ItemStack g = new ItemStack(Items.GOLD_INGOT), o = new ItemStack(Items.CRYING_OBSIDIAN);
        ItemStack c = new ItemStack(Items.CHISELED_POLISHED_BLACKSTONE), s = new ItemStack(Items.SOUL_SOIL);
        ItemStack b = new ItemStack(Items.POLISHED_BLACKSTONE_BRICKS);
        CraftingInput grid = CraftingInput.of(3, 3, List.of(g, o, g, c, s, c, b, b, b));
        ItemStack altar = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(h -> h.value().assemble(grid)).orElse(ItemStack.EMPTY);
        check("gold, crying obsidian, blackstone and soul soil give the altar", altar.is(SkullSanctum.SACRIFICIAL_ALTAR_ITEM), altar.toString());
        CraftingInput book = CraftingInput.of(2, 1, List.of(new ItemStack(Items.WITHER_SKELETON_SKULL), new ItemStack(Items.BOOK)));
        ItemStack guide = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, book, level)
                .map(h -> h.value().assemble(book)).orElse(ItemStack.EMPTY);
        check("a wither skeleton skull and a book give the guide", guide.is(SkullSanctum.GUIDE_BOOK), guide.toString());
        ItemStack soul = new ItemStack(SkullSanctum.SOUL), pearl = new ItemStack(Items.ENDER_PEARL), bone = new ItemStack(Items.SKELETON_SKULL);
        CraftingInput portable = CraftingInput.of(3, 3, List.of(soul, pearl, soul, pearl, bone, pearl, soul, pearl, soul));
        ItemStack skull = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, portable, level)
                .map(h -> h.value().assemble(portable)).orElse(ItemStack.EMPTY);
        check("a skeleton skull, ender pearls and souls give the portable Head Atlas", skull.is(SkullSanctum.PORTABLE_ATLAS_ITEM), skull.toString());
    }

    private void heads() {
        HeadCatalog.Entry line = HeadCatalog.parseLine("abc123\tdecoration\tTest Head\tTagged Twice");
        String decoded = line == null ? "" : new String(Base64.getDecoder().decode(HeadCatalog.textureValue(line.hash())));
        check("a line of the database becomes a head with its texture property", line != null && line.name().equals("Test Head")
                && line.category().equals("decoration") && line.tags().equals("Tagged Twice")
                && decoded.contains("textures.minecraft.net/texture/abc123"), decoded);
        check("lines with an unknown category or no name are skipped", HeadCatalog.parseLine("abc\tnope\tX\t") == null
                && HeadCatalog.parseLine("abc\tplants\t \t") == null, "");
        ItemStack head = HeadStacks.of("Test Head", "11111111-1111-1111-1111-111111111111", HeadCatalog.textureValue("dd"), 1);
        boolean saved = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), head).result().isPresent();
        check("a bought head carries its texture and saves", head.has(DataComponents.PROFILE) && saved
                && head.get(DataComponents.PROFILE).partialProfile().properties().containsKey("textures"), "");
    }

    private void bookKeys() {
        List<String> missing = GuideBook.BOOK.missingKeys("en_us", "de_de");
        check("book keys in en_us and de_de", missing.isEmpty(), String.join(", ", missing));
    }

    // ------------------------------------------------------------------ helpers

    private void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        lines.add((ok ? "ok   " : "FAIL ") + what + (detail.isEmpty() ? "" : " (" + detail + ")"));
    }

    private SacrificialAltarBlockEntity altar(BlockPos pos) {
        return (SacrificialAltarBlockEntity) level.getBlockEntity(pos);
    }

    private BlockState altarFacing(Direction front) {
        return SkullSanctum.SACRIFICIAL_ALTAR.defaultBlockState().setValue(SacrificialAltarBlock.FACING, front);
    }

    private SacrificialAltarBlockEntity formedSanctum(Direction front) {
        BlockPos pos = platform();
        build(pos, front);
        SacrificialAltarBlockEntity altar = altar(pos);
        altar.use(alice);
        if (!altar.formed()) throw new IllegalStateException("sanctum at " + pos + " did not form");
        return altar;
    }

    /** The suggested shrine around {@code altar}, the floor first, then the altar. */
    private void build(BlockPos altar, Direction front) {
        List<SanctumPattern.Part> parts = new ArrayList<>(SanctumPattern.template(altar, front));
        parts.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
        for (SanctumPattern.Part part : parts) level.setBlockAndUpdate(part.pos(), part.ghost());
        level.setBlockAndUpdate(altar, altarFacing(front));
    }

    /** A stone platform of its own, 9 by 9, in a force-loaded chunk. */
    private BlockPos platform() {
        if (slot == SLOTS) throw new IllegalStateException("more platforms than SLOTS");
        BlockPos pos = new BlockPos(32 * slot++ + 8, Y, 8);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlockAndUpdate(pos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 4; dy++) level.removeBlock(pos.offset(dx, dy, dz), false);
            }
        }
        return pos;
    }
}
