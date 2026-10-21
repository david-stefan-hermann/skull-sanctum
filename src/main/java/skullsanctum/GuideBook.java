package skullsanctum;

import bcf.guidebook.Book;
import bcf.guidebook.BookTheme;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section;
import bcf.guidebook.Section.Break;
import bcf.guidebook.Section.Heading;
import bcf.guidebook.Section.ItemLine;
import bcf.guidebook.Section.Quote;
import bcf.guidebook.Section.Text;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import skullsanctum.altar.SacrificialAltarBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The content of the guide book, as data: chapters of sections, which the screen of bcf-guide-book-lib wraps and
 * breaks into pages. Texts are translation keys {@code skullsanctum.book.<key>} in the language files; the self-test
 * checks every key the book uses against both of them. The numbers in it are the server's (see Config.setShown).
 */
public final class GuideBook {
    /** A polished blackstone panel with soul-green tabs and a pale bone page. */
    public static final Book BOOK = Book.of(SkullSanctum.MOD_ID, BookTheme.builder()
            .panel(0xFF2C2933, 0xFF4B4655, 0xFF16141B).tabs(0xFF221F28, 0xFF1C6A2E)
            .page(0xFFF1ECDF, 0xFFC4BBA6).ink(0xFF2C2933, 0xFF237A3A, 0xFF26222B, 0xFF6B6474)
            .slots(0xFFE6DFCF, 0xFFA59C88, 0xFF4CC963)
            .buttons(0xFF237A3A, 0xFF2F9C4A, 0xFF77727D, 0xFF0C0B0F, 0xFF62E077, 0xFF13401F, 0xFFFFFFFF, 0xFFC9C3CF)
            .build(), GuideBook::chapters);

    private GuideBook() {
    }

    public static String key(String name) {
        return BOOK.key(name);
    }

    /** Built when the book opens: item stacks need the registries. */
    public static List<Chapter> chapters() {
        ItemStack altar = new ItemStack(SkullSanctum.SACRIFICIAL_ALTAR_ITEM);
        ItemStack soul = new ItemStack(SkullSanctum.SOUL);
        ItemStack cauldron = new ItemStack(Items.CAULDRON);
        ItemStack table = new ItemStack(Items.ENCHANTING_TABLE);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);

        List<Section> sanctum = new ArrayList<>(List.of(new Text(key("sanctum.text")), new Heading(key("heading.blocks"))));
        for (Map.Entry<Block, Integer> part : SacrificialAltarBlockEntity.bill().entrySet()) {
            sanctum.add(new ItemLine(List.of(new ItemStack(part.getKey())), key("sanctum.count"), part.getValue(),
                    part.getKey().getName()));
        }
        sanctum.add(new Break());
        sanctum.add(new Heading(key("heading.layout")));
        sanctum.add(new Text(key("sanctum.back")));
        sanctum.add(new Text(key("sanctum.front")));
        sanctum.add(new Text(key("sanctum.form")));

        return List.of(
                new Chapter(key("start"), new ItemStack(SkullSanctum.GUIDE_BOOK), Chapter.Side.RIGHT, List.of(
                        new Text(key("start.text")), new Quote(key("start.motto")),
                        new Heading(key("heading.steps")),
                        new ItemLine(List.of(altar), key("start.step1")),
                        new ItemLine(List.of(new ItemStack(Items.WITHER_SKELETON_SKULL)), key("start.step2")),
                        new ItemLine(List.of(soul), key("start.step3")),
                        new ItemLine(List.of(table, head), key("start.step4")),
                        new Heading(key("heading.crafting")), BOOK.recipe("guide_book"),
                        new Text(key("start.recipe")))),
                new Chapter(key("sacrificial_altar"), altar, Chapter.Side.LEFT, List.of(
                        new Text(key("sacrificial_altar.text")), new Quote(key("sacrificial_altar.motto")),
                        new Heading(key("heading.crafting")), BOOK.recipe("sacrificial_altar"),
                        new Text(key("sacrificial_altar.recipe")),
                        new Break(),
                        new Heading(key("heading.use")),
                        new Text(key("sacrificial_altar.form")),
                        new ItemLine(List.of(new ItemStack(Items.DIAMOND)), key("sacrificial_altar.screen")),
                        new ItemLine(List.of(new ItemStack(Items.DROPPER)), key("sacrificial_altar.throw")),
                        new Text(key("sacrificial_altar.break")))),
                new Chapter(key("soul_tank"), cauldron, Chapter.Side.LEFT, List.of(
                        new Text(key("soul_tank.text"), Config.shownTankCapacity()), new Quote(key("soul_tank.motto")),
                        new Heading(key("heading.crafting")),
                        new ItemLine(List.of(cauldron), key("soul_tank.recipe")),
                        new Break(),
                        new Heading(key("heading.use")),
                        new ItemLine(List.of(soul), key("soul_tank.screen")),
                        new ItemLine(List.of(new ItemStack(Items.HOPPER)), key("soul_tank.hopper")),
                        new Text(key("soul_tank.break")))),
                new Chapter(key("soul"), soul, Chapter.Side.LEFT, List.of(
                        new Text(key("soul.text")), new Quote(key("soul.motto")),
                        new Heading(key("heading.crafting")),
                        new ItemLine(List.of(altar), key("soul.recipe")),
                        new Break(),
                        new Heading(key("heading.use")),
                        new Text(key("soul.use")))),
                new Chapter(key("sanctum"), new ItemStack(Items.CHISELED_POLISHED_BLACKSTONE), Chapter.Side.RIGHT, sanctum),
                new Chapter(key("sacrifices"), new ItemStack(Items.DIAMOND), Chapter.Side.RIGHT, List.of(
                        new Text(key("sacrifices.text")),
                        new ItemLine(List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_LOG)), key("sacrifices.common")),
                        new ItemLine(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND)), key("sacrifices.ores")),
                        new ItemLine(List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.WITHER_SKELETON_SKULL)), key("sacrifices.trophies")),
                        new ItemLine(List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)), key("sacrifices.rarity")),
                        new Text(key("sacrifices.fraction")))),
                new Chapter(key("heads"), head, Chapter.Side.RIGHT, List.of(
                        new Text(key("heads.text")),
                        new ItemLine(List.of(table), key("heads.browse")),
                        new ItemLine(List.of(head), key("heads.buy")),
                        new ItemLine(List.of(new ItemStack(Items.NETHER_STAR)), key("heads.favourite")),
                        new ItemLine(List.of(new ItemStack(Items.NAME_TAG)), key("heads.player"), Config.shownPlayerHeadPrice()),
                        new Text(key("heads.pay")))));
    }
}
