package skullsanctum.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import skullsanctum.shop.HeadStacks;
import skullsanctum.shop.ShopNetwork;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The head browser of a sanctum's Head Atlas, an ossuary: bone plaques for the tabs on the left (all heads, favourites,
 * the catalog's categories), a wall of nine by seven burial niches with a head in each, pages below, and on the right
 * a vial of the sanctum's soul tank, bubbling, with the souls written up its side. A click picks a head, the Buy button
 * buys one of it, or a stack of 64 while Shift is held (the button says so), a right click marks it as favourite. The
 * page arrows repeat while held, the page bar is a slider. The server sends one page at a time; the pages next to the shown one are fetched ahead and their
 * textures asked for, and pages already seen are kept, so that turning pages shows heads at once.
 */
public class HeadBrowserScreen extends Screen {
    // the ossuary in GUI pixels (314 × 216): the tabs' column sets the height, the grid, the vial and the padding the width
    private static final int WIDTH = 314, HEIGHT = 216, PAD = 8;
    private static final int HEADER_Y = 8, HEADER_H = 12; // the search and Buy keep the frame's distance at the top as at the sides
    private static final int TABS_X = PAD, TABS_W = 90, TAB_H = 12, TAB_STEP = 14;
    private static final int COLS = 9, ROWS = ShopNetwork.PAGE / COLS, CELL_W = 20, CELL_H = 24, NICHE_W = 18, NICHE_H = 22;
    private static final int GRID_X = TABS_X + TABS_W + 6, GRID_Y = 24, GRID_W = COLS * CELL_W, GRID_H = ROWS * CELL_H;
    private static final int PAGER_Y = GRID_Y + GRID_H + 5, PAGER_H = 11, PAGER_BUTTON_W = 15;
    private static final int BODY_H = PAGER_Y + PAGER_H - GRID_Y;
    private static final int VIAL_X = GRID_X + GRID_W + 7, VIAL_W = 15, VIAL_Y = GRID_Y, VIAL_H = BODY_H;
    private static final int BUY_W = 40, BUY_X = WIDTH - PAD - BUY_W;
    private static final int SEARCH_DELAY = 6;
    private static final int HOLD_DELAY = 10, HOLD_REPEAT = 3, SLIDE_DELAY = 2;
    /** Pages kept, and how many pages on either side of the shown one are fetched ahead. */
    private static final int CACHE_PAGES = 12, PREFETCH = 2;
    private static final int TILE = 16; // the texture's own size: cut by the scissor, never scaled

    private final ShopNetwork.Open open;
    private final List<String> tabs = new ArrayList<>();
    private int mode = ShopNetwork.MODE_ALL;
    private int page;
    private int pages = 1;
    private int total;
    private int balance;
    private int tankSouls;
    private List<ShopNetwork.Entry> entries = List.of();
    private List<ItemStack> stacks = List.of();
    private final Map<PageKey, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PageKey, Cached> eldest) {
            return size() > CACHE_PAGES;
        }
    };
    private final Set<PageKey> asked = new HashSet<>();
    /** The head a click picked, bought by the Buy button; null when none. */
    private ShopNetwork.Entry picked;
    private boolean waiting;
    private int searchTimer = -1;
    /** A page arrow held down: which way, and the ticks until it turns again (a pause first, then quickly). */
    private int held, heldTimer;
    /** The page bar is being dragged like a slider; the page it points at is fetched once the drag rests a moment. */
    private boolean sliding;
    private int slideTimer = -1;
    private Component message;
    private int messageColor;
    private int messageTicks;
    private int left, top;
    private EditBox search;
    /** Bubbles in the vial. */
    private final SoulBubbles bubbles = new SoulBubbles(28);

    public HeadBrowserScreen(ShopNetwork.Open open) {
        super(Component.translatable("gui.skullsanctum.browser"));
        this.open = open;
        this.balance = open.balance();
        this.tankSouls = open.tankSouls();
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String previousSearch = search == null ? "" : search.getValue();
        int searchX = searchX();
        search = addRenderableWidget(new EditBox(font, left + searchX + 4, top + HEADER_Y + 2, BUY_X - 6 - searchX - 8, 9,
                Component.translatable("gui.skullsanctum.browser.search")));
        search.setBordered(false);
        search.setTextColor(SanctumUi.SOUL);
        search.setHint(Component.translatable("gui.skullsanctum.browser.search"));
        search.setMaxLength(48);
        search.setValue(previousSearch);
        search.setResponder(text -> {
            searchTimer = SEARCH_DELAY;
            // a search looks through all heads; only the favourites keep their own
            if (!text.isBlank() && mode >= 0) {
                mode = ShopNetwork.MODE_ALL;
                entries = List.of();
                stacks = List.of();
            }
        });
        tabs.clear();
        tabs.add("gui.skullsanctum.browser.all");
        tabs.add("gui.skullsanctum.browser.favourites");
        if (entries.isEmpty()) query();
    }

    /** Where the search recess starts: after the skull and the title. */
    private int searchX() {
        return PAD + 19 + font.width(title) + 6;
    }

    // ---- server traffic ------------------------------------------------------------------------------------------

    /** A page as asked for: the tab, the search as typed, the page number. */
    private record PageKey(int mode, String search, int page) {
    }

    /** A page as received, with its heads as item stacks. */
    private record Cached(ShopNetwork.Page page, List<ItemStack> stacks) {
    }

    private PageKey wanted() {
        return new PageKey(mode, search == null ? "" : search.getValue(), page);
    }

    /** Shows the wanted page from the cache, or asks the server for it. */
    private void query() {
        Cached cached = cache.get(wanted());
        if (cached != null) {
            show(cached);
            return;
        }
        waiting = true;
        ask(wanted());
    }

    private void ask(PageKey key) {
        if (asked.add(key)) ClientPlayNetworking.send(new ShopNetwork.Query(open.altar(), key.mode(), key.search(), key.page()));
    }

    public void accept(ShopNetwork.Page received) {
        PageKey key = new PageKey(received.mode(), received.search(), received.page());
        // the server clamps a page past the end: that answers every page asked beyond it too
        asked.removeIf(k -> k.mode() == key.mode() && k.search().equals(key.search()) && (k.page() == key.page() || k.page() >= received.pages()));
        List<ItemStack> stacks = new ArrayList<>();
        for (ShopNetwork.Entry entry : received.entries()) {
            stacks.add(entry.index() == ShopNetwork.PLAYER_ENTRY ? HeadStacks.player(entry.name(), 1)
                    : HeadStacks.of(entry.name(), entry.uuid(), entry.texture(), 1));
        }
        Cached cached = new Cached(received, stacks);
        cache.put(key, cached);
        warm(stacks);
        PageKey want = wanted();
        if (key.mode() == want.mode() && key.search().equals(want.search()) && (key.page() == want.page() || want.page() >= received.pages())) {
            show(cached);
        }
    }

    private void show(Cached cached) {
        ShopNetwork.Page received = cached.page();
        waiting = false;
        page = received.page();
        pages = received.pages();
        total = received.total();
        balance = received.balance();
        tankSouls = received.tankSouls();
        entries = received.entries();
        stacks = cached.stacks();
        // the pages next to this one are fetched now, so that turning to them shows heads at once
        for (int d = 1; d <= PREFETCH; d++) {
            for (int p : new int[]{page + d, page - d}) {
                PageKey key = new PageKey(mode, search.getValue(), p);
                if (p >= 0 && p < pages && !cache.containsKey(key)) ask(key);
            }
        }
    }

    /** Asks the skin cache for every head's texture now: the downloads run while the page is not yet looked at. */
    private void warm(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            if (profile != null) minecraft.playerSkinRenderCache().lookup(profile);
        }
    }

    public void result(ShopNetwork.Result result) {
        if (ShopNetwork.GONE.equals(result.message())) {
            gone();
            return;
        }
        balance = result.balance();
        tankSouls = result.tankSouls();
        message = Component.translatable(result.message(), result.argument());
        messageColor = result.bought() ? SanctumUi.SOUL : SanctumUi.WARN;
        messageTicks = 60;
    }

    /** The sanctum is out of reach or asleep: the browser closes with a word on the HUD. */
    private void gone() {
        minecraft.gui.hud.setOverlayMessage(Component.translatable(ShopNetwork.GONE), false);
        onClose();
    }

    private void turn(int by) {
        int target = Math.clamp(page + by, 0, pages - 1);
        if (target == page) return;
        page = target;
        query();
    }

    private void select(int newMode) {
        if (newMode == mode) return;
        mode = newMode;
        page = 0;
        entries = List.of();
        stacks = List.of();
        query();
    }

    private void buy(ShopNetwork.Entry entry, boolean stack) {
        if (entry.index() == ShopNetwork.PLAYER_ENTRY) {
            ClientPlayNetworking.send(new ShopNetwork.BuyPlayer(open.altar(), entry.name(), stack ? 64 : 1));
        } else {
            ClientPlayNetworking.send(new ShopNetwork.Buy(open.altar(), entry.index(), entry.uuid(), stack ? 64 : 1));
        }
    }

    private void toggleFavourite(int cell) {
        ShopNetwork.Entry entry = entries.get(cell);
        if (entry.index() == ShopNetwork.PLAYER_ENTRY) return;
        boolean on = !entry.favourite();
        ClientPlayNetworking.send(new ShopNetwork.Favourite(entry.uuid(), on));
        List<ShopNetwork.Entry> changed = new ArrayList<>(entries);
        changed.set(cell, new ShopNetwork.Entry(entry.index(), entry.name(), entry.uuid(), entry.texture(), entry.price(), on));
        entries = changed;
        // the other cached pages may show the head too, with the old mark: only this one stays
        ShopNetwork.Page current = new ShopNetwork.Page(mode, search.getValue(), page, pages, total, entries, balance, tankSouls);
        cache.clear();
        asked.clear();
        cache.put(wanted(), new Cached(current, stacks));
    }

    private static boolean same(ShopNetwork.Entry a, ShopNetwork.Entry b) {
        return a != null && b != null && a.index() == b.index() && a.uuid().equals(b.uuid()) && a.name().equals(b.name());
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft.player != null && minecraft.player.distanceToSqr(Vec3.atCenterOf(open.altar())) > ShopNetwork.REACH * ShopNetwork.REACH) {
            gone();
            return;
        }
        if (searchTimer > 0 && --searchTimer == 0) {
            page = 0;
            query();
        }
        if (held != 0 && --heldTimer <= 0) {
            heldTimer = HOLD_REPEAT;
            turn(held);
        }
        if (slideTimer > 0 && --slideTimer == 0) query();
        if (messageTicks > 0) messageTicks--;
        bubbles.tick(fillHeight());
    }

    // ---- the vial ------------------------------------------------------------------------------------------------

    /** The height of the souls in the vial, in pixels of its inside. */
    private int fillHeight() {
        int inside = VIAL_H - 2; // every row between the glass's top and bottom
        return open.tankCapacity() <= 0 ? 0 : (int) Math.round((double) Math.min(tankSouls, open.tankCapacity()) / open.tankCapacity() * inside);
    }

    /** How far each row of the vial's rounded top steps in from the sides, top row first. */
    private static final int[] VIAL_ARCH = {5, 3, 2, 1, 1};

    /** How far the vial's inside steps in from its sides in row {@code dy}: the glass is one pixel thick. */
    private static int vialInside(int dy) {
        int in = dy < VIAL_ARCH.length ? VIAL_ARCH[dy] : 0;
        int above = dy > 0 && dy - 1 < VIAL_ARCH.length ? VIAL_ARCH[dy - 1] : 0;
        return Math.max(in, above) + 1;
    }

    private void drawVial(GuiGraphicsExtractor g) {
        int x = left + VIAL_X, y = top + VIAL_Y;
        for (int dy = 0; dy < VIAL_H; dy++) {
            int in = dy < VIAL_ARCH.length ? VIAL_ARCH[dy] : dy == VIAL_H - 1 ? 1 : 0;
            g.fill(x + in, y + dy, x + VIAL_W - in, y + dy + 1, SanctumUi.STONE_LINE);
            if (dy > 0 && dy < VIAL_H - 1) {
                int inside = vialInside(dy);
                g.fill(x + inside, y + dy, x + VIAL_W - inside, y + dy + 1, SanctumUi.STONE_DEEP);
            }
        }
        int fill = fillHeight();
        int floor = y + VIAL_H - 1; // the glass's bottom row, not filled
        int surface = floor - fill;
        if (fill > 0) {
            // the texture in tiles anchored at the glass's top so that they do not drift with the level, cut to the
            // inside of the glass band by band (the rounded top narrows row by row)
            TextureAtlasSprite essence = minecraft.getAtlasManager().get(SanctumUi.SOUL_VIAL);
            for (int row = surface; row < floor; ) {
                int inside = vialInside(row - y), rows = 1;
                while (row + rows < floor && vialInside(row + rows - y) == inside) rows++;
                g.enableScissor(x + inside, row, x + VIAL_W - inside, row + rows);
                for (int tileY = y + 1; tileY < row + rows; tileY += TILE) {
                    if (tileY + TILE > row) g.blitSprite(RenderPipelines.GUI_TEXTURED, essence, x + 1, tileY, TILE, TILE);
                }
                g.disableScissor();
                row += rows;
            }
            g.fill(x + vialInside(surface - y), surface, x + VIAL_W - vialInside(surface - y), surface + 1, 0xFFE6FFE9);
            for (SoulBubbles.Bubble bubble : bubbles.all()) {
                int bx = x + 1 + Math.round(bubble.across() * (VIAL_W - 3)), by = floor - Math.round(bubble.height());
                g.fill(bx, by - bubble.size(), bx + bubble.size(), by, SoulBubbles.COLOUR);
            }
        }
    }

    // ---- input ---------------------------------------------------------------------------------------------------

    /** Tab {@code i}: 0 all, 1 favourites, then the categories. */
    private int tabMode(int i) {
        return i == 0 ? ShopNetwork.MODE_ALL : i == 1 ? ShopNetwork.MODE_FAVOURITES : i - 2;
    }

    private int tabCount() {
        return 2 + open.categories().size();
    }

    private int tabAt(double mouseX, double mouseY) {
        int x = (int) mouseX - left - TABS_X - 3, y = (int) mouseY - top - GRID_Y - 2;
        if (x < 0 || x >= TABS_W - 6 || y < 0) return -1;
        int i = y / TAB_STEP;
        return y % TAB_STEP < TAB_H + 1 && i < tabCount() ? i : -1;
    }

    private int cellAt(double mouseX, double mouseY) {
        int x = (int) mouseX - left - GRID_X, y = (int) mouseY - top - GRID_Y;
        if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return -1;
        int cell = y / CELL_H * COLS + x / CELL_W;
        return cell < entries.size() ? cell : -1;
    }

    private boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= left + x && mouseX < left + x + w && mouseY >= top + y && mouseY < top + y + h;
    }

    private boolean overBuy(double mouseX, double mouseY) {
        return over(mouseX, mouseY, BUY_X, HEADER_Y, BUY_W, HEADER_H);
    }

    private boolean overPrevious(double mouseX, double mouseY) {
        return over(mouseX, mouseY, GRID_X, PAGER_Y, PAGER_BUTTON_W, PAGER_H);
    }

    private boolean overNext(double mouseX, double mouseY) {
        return over(mouseX, mouseY, GRID_X + GRID_W - PAGER_BUTTON_W, PAGER_Y, PAGER_BUTTON_W, PAGER_H);
    }

    private static final int BAR_X = GRID_X + PAGER_BUTTON_W + 4, BAR_W = GRID_W - 2 * (PAGER_BUTTON_W + 4);

    private boolean overBar(double mouseX, double mouseY) {
        return over(mouseX, mouseY, BAR_X, PAGER_Y, BAR_W, PAGER_H);
    }

    /** The page bar as a slider: the page under the mouse is shown at once and fetched once the mouse rests. */
    private void slide(double mouseX) {
        int target = Math.clamp((int) ((mouseX - left - BAR_X - 1) * pages / (BAR_W - 2)), 0, pages - 1);
        if (target == page) return;
        page = target;
        slideTimer = SLIDE_DELAY;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tab = tabAt(event.x(), event.y());
        if (tab >= 0 && event.button() == 0) {
            select(tabMode(tab));
            return true;
        }
        int cell = cellAt(event.x(), event.y());
        if (cell >= 0) {
            if (event.button() == 1) toggleFavourite(cell);
            else if (event.button() == 0) picked = entries.get(cell);
            return true;
        }
        if (event.button() == 0) {
            if (overBuy(event.x(), event.y())) {
                if (picked != null) buy(picked, shiftHeld());
                return true;
            }
            if (overPrevious(event.x(), event.y()) || overNext(event.x(), event.y())) {
                held = overNext(event.x(), event.y()) ? 1 : -1;
                heldTimer = HOLD_DELAY;
                turn(held);
                return true;
            }
            if (overBar(event.x(), event.y())) {
                sliding = true;
                slide(event.x());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (sliding) {
            slide(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        held = 0;
        if (sliding) {
            sliding = false;
            if (slideTimer != 0 && !cache.containsKey(wanted())) slideTimer = 1; // the page the drag ended on
            return true;
        }
        return super.mouseReleased(event);
    }

    /** The inventory key (E) closes the atlas, as it closes a chest; while typing in the search it is a letter. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!search.isFocused() && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (over(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        SanctumUi.stone(g, left, top, WIDTH, HEIGHT);
        SanctumUi.recess(g, left + searchX(), top + HEADER_Y - 1, BUY_X - 6 - searchX(), HEADER_H + 1);
        SanctumUi.recess(g, left + TABS_X, top + GRID_Y, TABS_W, BODY_H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        SanctumUi.header(g, font, left, top, title);
        drawBuy(g, mouseX, mouseY);
        drawTabs(g, mouseX, mouseY);
        drawGrid(g, mouseX, mouseY);
        drawPager(g, mouseX, mouseY);
        drawVial(g);
        if (over(mouseX, mouseY, VIAL_X, VIAL_Y, VIAL_W, VIAL_H)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.skullsanctum.tank.amount", tankSouls, open.tankCapacity())), mouseX, mouseY);
        }
    }

    /** Shift is held: the Buy button buys a stack and says so. */
    private boolean shiftHeld() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private void drawBuy(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean active = picked != null;
        int x = left + BUY_X, y = top + HEADER_Y;
        if (active) {
            SanctumUi.plaque(g, x, y, BUY_W, HEADER_H - 1, true, false);
            if (overBuy(mouseX, mouseY)) g.fill(x, y, x + BUY_W, y + HEADER_H - 2, 0x30FFFFFF);
        } else {
            g.fill(x, y + HEADER_H - 1, x + BUY_W, y + HEADER_H, SanctumUi.EDGE);
            g.fill(x, y, x + BUY_W, y + HEADER_H - 1, SanctumUi.PANEL_LIGHT);
            g.fill(x, y + HEADER_H - 2, x + BUY_W, y + HEADER_H - 1, SanctumUi.PANEL);
        }
        g.centeredText(font, Component.translatable(shiftHeld() ? "gui.skullsanctum.browser.buy_stack" : "gui.skullsanctum.browser.buy"),
                x + BUY_W / 2, y + 2, active ? 0xFFE6FFE9 : SanctumUi.SOFT);
        if (active && overBuy(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(tooltip(picked)), mouseX, mouseY);
        }
    }

    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hovered = tabAt(mouseX, mouseY);
        int x = left + TABS_X + 3, w = TABS_W - 6;
        for (int i = 0; i < tabCount(); i++) {
            int y = top + GRID_Y + 2 + i * TAB_STEP;
            boolean selected = tabMode(i) == mode;
            SanctumUi.plaque(g, x, y, w, TAB_H, selected, i == hovered);
            Component label;
            int price = -1; // the category's price in souls, at the right; the first two tabs have none
            if (i < 2) {
                label = Component.translatable(tabs.get(i));
            } else {
                ShopNetwork.CategoryInfo category = open.categories().get(i - 2);
                label = categoryName(category.name());
                price = category.price();
            }
            int ink = selected ? 0xFFE6FFE9 : SanctumUi.INK;
            g.text(font, font.plainSubstrByWidth(label.getString(), price >= 0 ? w - 6 - font.width(String.valueOf(price)) - 2 : w - 6), x + 3, y + 2, ink, false);
            if (price >= 0) {
                String text = String.valueOf(price);
                g.text(font, text, x + w - 3 - font.width(text), y + 2, selected ? 0xFFE6FFE9 : SanctumUi.INK_GREEN, false);
            }
        }
    }

    /** A category's name in the player's language when the mod knows it, else as the catalog writes it. */
    private Component categoryName(String name) {
        String key = "gui.skullsanctum.category." + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(name);
    }

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int gx = left + GRID_X, gy = top + GRID_Y;
        int hovered = cellAt(mouseX, mouseY);
        for (int cell = 0; cell < COLS * ROWS; cell++) {
            int x = gx + cell % COLS * CELL_W + 1, y = gy + cell / COLS * CELL_H + 1;
            boolean filled = cell < stacks.size();
            SanctumUi.niche(g, x, y, NICHE_H, filled && same(entries.get(cell), picked), filled && cell == hovered);
            if (!filled) continue;
            g.item(stacks.get(cell), x + 1, y + 4);
            if (entries.get(cell).favourite()) g.text(font, "★", x + NICHE_W - 6, y + 1, 0xFFFFD34A, true);
        }
        Component empty = emptyText();
        if (empty != null) {
            g.textWithWordWrap(font, empty, gx + 10, gy + GRID_H / 2 - 10, GRID_W - 20, SanctumUi.SOFT, false);
            return;
        }
        if (hovered >= 0) g.setComponentTooltipForNextFrame(font, List.of(tooltip(entries.get(hovered))), mouseX, mouseY);
    }

    /** One line: the head's name and its price. */
    private Component tooltip(ShopNetwork.Entry entry) {
        Component name = entry.index() == ShopNetwork.PLAYER_ENTRY
                ? Component.translatable("gui.skullsanctum.browser.player_head", entry.name()) : Component.literal(entry.name());
        return name.copy().append(" · ").append(Component.translatable("gui.skullsanctum.browser.price", entry.price()).withColor(SanctumUi.SOUL));
    }

    private void drawPager(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = left + GRID_X, y = top + PAGER_Y;
        drawPagerButton(g, x, y, "<", page > 0, overPrevious(mouseX, mouseY));
        drawPagerButton(g, x + GRID_W - PAGER_BUTTON_W, y, ">", page < pages - 1, overNext(mouseX, mouseY));
        int barX = left + BAR_X, barW = BAR_W;
        SanctumUi.recess(g, barX, y, barW, PAGER_H);
        if (total > 0) {
            int progress = Math.max(2, (barW - 2) * (page + 1) / pages);
            g.fill(barX + 1, y + PAGER_H - 3, barX + 1 + progress, y + PAGER_H - 1, sliding || slideTimer > 0 ? SanctumUi.SOUL : SanctumUi.SOUL_DARK);
        }
        if (messageTicks > 0 && message != null) {
            g.centeredText(font, message, barX + barW / 2, y + 2, messageColor);
        } else if (total > 0) {
            g.centeredText(font, (page + 1) + " / " + pages, barX + barW / 2, y + 2, SanctumUi.LABEL);
        }
    }

    private void drawPagerButton(GuiGraphicsExtractor g, int x, int y, String glyph, boolean active, boolean hovered) {
        SanctumUi.recess(g, x, y, PAGER_BUTTON_W, PAGER_H);
        if (active && hovered) g.fill(x + 1, y + 1, x + PAGER_BUTTON_W - 1, y + PAGER_H - 1, SanctumUi.STONE_LINE);
        g.centeredText(font, glyph, x + PAGER_BUTTON_W / 2, y + 2, active ? SanctumUi.TITLE : SanctumUi.SOFT);
    }

    /** Why the grid is empty, or null when it shows heads. */
    private Component emptyText() {
        if (!entries.isEmpty()) return null;
        if (waiting) return Component.translatable("gui.skullsanctum.browser.waiting");
        return Component.translatable(mode == ShopNetwork.MODE_FAVOURITES ? "gui.skullsanctum.browser.no_favourites"
                : "gui.skullsanctum.browser.nothing");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- for the dev screenshots ---------------------------------------------------------------------------------

    public void devSelectTab(int tab) {
        if (tab >= 0 && tab < tabCount()) select(tabMode(tab));
    }

    public void devSearch(String text) {
        search.setValue(text);
    }

    public void devBuy(int cell, boolean stack) {
        if (cell >= 0 && cell < entries.size()) buy(entries.get(cell), stack);
    }

    public void devPick(int cell) {
        if (cell >= 0 && cell < entries.size()) picked = entries.get(cell);
    }

    public void devFavourite(int cell) {
        if (cell >= 0 && cell < entries.size()) toggleFavourite(cell);
    }

    public int devShown() {
        return entries.size();
    }
}
