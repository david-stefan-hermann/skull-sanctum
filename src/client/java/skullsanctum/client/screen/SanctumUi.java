package skullsanctum.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import skullsanctum.SkullSanctum;

/** The sanctum's screens in one look: polished blackstone panels, dark slots, a soul-green gauge, bone plaques. */
public final class SanctumUi {
    public static final int PANEL = 0xFF2C2933, PANEL_LIGHT = 0xFF4B4655, PANEL_SHADOW = 0xFF16141B, EDGE = 0xFF0C0B0F;
    public static final int SLOT = 0xFF1A181F, SLOT_LIGHT = 0xFF57515F, SLOT_SHADOW = 0xFF0E0D12;
    public static final int TITLE = 0xFFE6DCC8, LABEL = 0xFFB9AEC8, SOFT = 0xFF8E859A;
    public static final int SOUL = 0xFF62E077, SOUL_DARK = 0xFF1C6A2E, SOUL_DEEP = 0xFF13401F, SOUL_BRIGHT = 0xFF8CFF6E;
    public static final int WARN = 0xFFFF8A7A;
    /** The ossuary: a wall of dark stone, bone plaques with dark ink, stone outlines. */
    public static final int STONE = 0xFF1A181F, STONE_DARK = 0xFF121016, STONE_LINE = 0xFF3A3542, STONE_DEEP = 0xFF0A090C;
    public static final int BONE = 0xFFE6DCC8, BONE_DARK = 0xFFC9BDA3, BONE_EDGE = 0xFFA59C88, BONE_LIGHT = 0xFFF1ECDF;
    public static final int INK = 0xFF2C2933, INK_GREEN = 0xFF13401F;
    /** The skull of the mod icon: dark bone, its shadow, and the ink of its eye sockets; the eyes are SKULL_EYE and SKULL_EYE_BRIGHT. */
    public static final int SKULL_BONE = 0xFF6A6A74, SKULL_SHADOW = 0xFF4A4A52, SKULL_INK = 0xFF121016, SKULL_EYE = 0xFF55E06A, SKULL_EYE_BRIGHT = 0xFFD8FFD0;
    /** The souls as a liquid: the tank's animation in the soul item's colours, a block texture so that it animates. */
    public static final SpriteId SOUL_VIAL = Sheets.BLOCKS_MAPPER.apply(SkullSanctum.id("soul_vial"));
    /** The height of the title bar of the sanctum's screens. */
    public static final int HEADER_H = 20;

    private SanctumUi() {
    }

    /** A wall of dark stone bricks (courses every 10, joints every 20, offset course by course) in a chiselled frame. */
    public static void stone(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, STONE);
        for (int row = 0; (row + 1) * 10 < h - 3; row++) {
            int top = y + 3 + row * 10;
            g.fill(x + 3, top + 9, x + w - 3, top + 10, STONE_DARK);
            for (int joint = (row % 2) * 10; joint < w - 6; joint += 20) {
                g.fill(x + 3 + joint, top, x + 4 + joint, top + 9, STONE_DARK);
            }
        }
        frame(g, x, y, w, h);
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int inset, int colour) {
        g.fill(x + inset, y + inset, x + w - inset, y + inset + 1, colour);
        g.fill(x + inset, y + h - inset - 1, x + w - inset, y + h - inset, colour);
        g.fill(x + inset, y + inset, x + inset + 1, y + h - inset, colour);
        g.fill(x + w - inset - 1, y + inset, x + w - inset, y + h - inset, colour);
    }

    /** The chiselled frame of a stone wall: a bevel lit from the top left, a groove inside it, carved corner blocks. */
    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        ring(g, x, y, w, h, 1, PANEL_LIGHT);
        ring(g, x, y, w, h, 2, PANEL);
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, PANEL_SHADOW);
        ring(g, x, y, w, h, 3, STONE_DEEP);
        ring(g, x, y, w, h, 4, PANEL_LIGHT);
        for (int[] c : new int[][]{{x, y}, {x + w - 9, y}, {x, y + h - 9}, {x + w - 9, y + h - 9}}) {
            g.fill(c[0], c[1], c[0] + 9, c[1] + 9, EDGE);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 8, c[1] + 8, PANEL_LIGHT);
            g.fill(c[0] + 2, c[1] + 2, c[0] + 8, c[1] + 8, PANEL);
            g.fill(c[0] + 3, c[1] + 3, c[0] + 6, c[1] + 6, STONE_DEEP);
            g.fill(c[0] + 4, c[1] + 4, c[0] + 5, c[1] + 5, SOUL_DARK);
        }
    }

    /** A sunken 18 × 18 slot whose inside starts at {@code x + 1, y + 1}. */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        sunken(g, x, y, 18, 18);
    }

    public static void sunken(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT);
        g.fill(x, y, x + w, y + 1, SLOT_SHADOW);
        g.fill(x, y, x + 1, y + h, SLOT_SHADOW);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_LIGHT);
    }

    /** A dark box let into the stone, outlined in lighter stone. */
    public static void recess(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, STONE_LINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_SHADOW);
    }

    /** A bone plaque (or a soul-green one when selected) with a shadow below. */
    public static void plaque(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        int top = selected ? 0xFF2F9C4A : hovered ? BONE_LIGHT : BONE;
        int bottom = selected ? SOUL_DARK : hovered ? BONE : BONE_DARK;
        int edge = selected ? SOUL_DEEP : BONE_EDGE;
        g.fill(x, y + h, x + w, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h / 2, top);
        g.fill(x, y + h / 2, x + w, y + h - 1, bottom);
        g.fill(x, y + h - 1, x + w, y + h, edge);
    }

    /** How far each row of an arch of this width steps in from the sides, top row first. */
    private static final int[] ARCH = {6, 4, 3, 2, 1, 1};

    /** A burial niche, 18 wide and {@code h} high, its floor lit soul-green when selected. */
    public static void niche(GuiGraphicsExtractor g, int x, int y, int h, boolean selected, boolean hovered) {
        int inner = selected ? 0xFF10231A : hovered ? 0xFF262230 : SLOT;
        int floor = selected ? SOUL : STONE_LINE;
        for (int dy = 0; dy < h; dy++) {
            int in = dy < ARCH.length ? ARCH[dy] : 0;
            g.fill(x + in, y + dy, x + 18 - in, y + dy + 1, STONE_DEEP);
            if (dy > 0) {
                int inside = Math.max(in, dy < ARCH.length ? ARCH[dy - 1] : 0) + 1;
                g.fill(x + inside, y + dy, x + 18 - inside, y + dy + 1, dy >= h - 2 ? floor : inner);
            }
        }
        if (selected) g.fill(x + 1, y + h - 6, x + 17, y + h - 2, SOUL_DEEP);
    }

    /** The mod's skull, 12 × 12 pixels in the colours of the mod icon's skull, drawn with its top left corner at {@code x, y}. */
    public static void skull(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y, x + 11, y + 3, SKULL_BONE);          // the crown
        g.fill(x, y + 3, x + 12, y + 8, SKULL_BONE);          // the face
        g.fill(x + 2, y + 8, x + 10, y + 10, SKULL_BONE);     // the jaw
        g.fill(x + 3, y + 10, x + 5, y + 12, SKULL_BONE);     // the teeth
        g.fill(x + 7, y + 10, x + 9, y + 12, SKULL_BONE);
        g.fill(x + 1, y + 2, x + 11, y + 3, SKULL_SHADOW);    // the brow's shadow
        g.fill(x + 2, y + 9, x + 10, y + 10, SKULL_SHADOW);
        g.fill(x + 2, y + 4, x + 5, y + 7, SKULL_EYE);        // the eyes
        g.fill(x + 7, y + 4, x + 10, y + 7, SKULL_EYE);
        g.fill(x + 3, y + 5, x + 4, y + 6, SKULL_EYE_BRIGHT);
        g.fill(x + 8, y + 5, x + 9, y + 6, SKULL_EYE_BRIGHT);
        g.fill(x + 5, y + 7, x + 7, y + 8, SKULL_INK);        // the nose
    }

    /** The title of a sanctum screen: the skull and the title at the top of its panel. */
    public static void header(GuiGraphicsExtractor g, Font font, int x, int y, Component title) {
        // the same in every screen: the skull 10 in and 8 down, the title 17 after it, level with the Head Atlas's search
        skull(g, x + 10, y + 8);
        g.text(font, title, x + 27, y + 10, TITLE, false);
    }

    /**
     * How far row {@code row} of a witch's cauldron {@code w} wide and {@code h} high steps in from the sides: a
     * broad rolled lip, a neck with a band round it, then a belly swelling out to the full width and rounding as
     * fully into a flat bottom half as wide.
     */
    public static int potInset(int row, int w, int h) {
        double t = (row + 0.5) / h, half;
        if (t < 0.065) half = 1.0;                                                          // the lip
        else if (t < 0.11) half = 0.84;                                                     // the neck
        else if (t < 0.15) half = 0.88;                                                     // the band
        else if (t < 0.5) half = 0.84 + 0.16 * Math.sin((t - 0.15) / 0.35 * Math.PI / 2);  // the upper belly
        else half = 0.5 + 0.5 * Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.5) / 0.5, 2))); // into the bottom
        return (int) Math.round((1 - half) * w / 2.0);
    }

    /** Where the inside of the cauldron starts in row {@code row}: two pixels of wall, more where it slants and at the lip. */
    public static int potInside(int row, int w, int h) {
        int here = potInset(row, w, h);
        int above = row > 0 ? potInset(row - 1, w, h) : here;
        int below = row < h - 1 ? potInset(row + 1, w, h) : here;
        boolean lip = (row + 0.5) / h < 0.065;
        return Math.max(here, Math.max(above, below)) + (lip ? 4 : 2);
    }

    /**
     * The cauldron's handles and feet as pixel pictures, drawn for the left side and mirrored, a shade darker, for
     * the right: L light, H highlight, M mid, S dim, D shadow, E edge, '.' nothing. The handle is a scroll hanging
     * from under the lip; the feet are short and splay outwards.
     */
    private static final String[] HANDLE = {"....LLLL", "..LLMM..", ".LM.....", "LM......", "LM......", "LM......", "LM......",
            ".LM.....", "..LM....", "..LM....", ".LM.....", "LM..LL..", "LM.L.M..", "MD..MD..", ".DMMD...", "..DD...."};
    private static final String[] FOOT = {"....LLLLLL", "...LMMMMMD", "..LMMMMD..", ".LMMMD....", "LMMD......", "LMD.......", ".D........"};
    private static final int HANDLE_ROW = 4, FOOT_IN = 1;

    private static void picture(GuiGraphicsExtractor g, String[] rows, int x, int y, boolean mirrored) {
        int width = rows[0].length();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                if (ch == '.') continue;
                int level = switch (ch) { case 'H' -> 6; case 'L' -> 5; case 'M' -> 3; case 'S' -> 2; case 'D' -> 1; default -> 0; };
                if (mirrored) level = Math.max(level - 1, 0);
                int px = mirrored ? x + width - 1 - c : x + c;
                g.fill(px, y + r, px + 1, y + r + 1, RAMP[level]);
            }
        }
    }

    /** The handles and feet, drawn before the body so that the body covers where they join it. */
    private static void fittings(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int handleIn = potInset(HANDLE_ROW, w, h), hw = HANDLE[0].length();
        picture(g, HANDLE, x + handleIn - hw, y + HANDLE_ROW, false);
        picture(g, HANDLE, x + w - handleIn, y + HANDLE_ROW, true);
        int floor = potInset(h - 1, w, h), fw = FOOT[0].length();
        picture(g, FOOT, x + floor + FOOT_IN, y + h, false);
        picture(g, FOOT, x + w - floor - FOOT_IN - fw, y + h, true);
    }

    /**
     * A cauldron of blackstone in cross-section, {@code w} by {@code h} with two scroll handles and two feet, holding {@code fill}
     * rows of souls (the texture, tiled from the mouth down so that it does not drift with the level).
     */
    public static void cauldron(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, TextureAtlasSprite souls) {
        fittings(g, x, y, w, h);
        { // a soft shadow cast on the wall behind, under the belly
            int foot = potInset(h - 1, w, h);
            int[] narrower = {0, 1, 3, 6, 10};
            for (int k = 0; k < narrower.length; k++) {
                int half = w / 2 - foot + 5 - narrower[k];
                g.fill(x + w / 2 - half, y + h + k, x + w / 2 + half, y + h + k + 1, 0x80000000);
            }
        }
        for (int row = 0; row < h; row++) {
            int in = potInset(row, w, h), inside = potInside(row, w, h);
            if (row < h - 2 && inside < w - inside) {
                int n = inside - in;
                for (int k = 0; k < n; k++) {
                    g.fill(x + in + k, y + row, x + in + k + 1, y + row + 1, RAMP[wall(true, k, n, row, h)]);
                    g.fill(x + w - in - k - 1, y + row, x + w - in - k, y + row + 1, RAMP[wall(false, k, n, row, h)]);
                }
                if (row > 0) {
                    g.fill(x + inside, y + row, x + w - inside, y + row + 1, STONE_DEEP);
                    if (row > 3) { // the far inner wall catches the light that falls in
                        g.fill(x + w - inside - 4, y + row, x + w - inside - 2, y + row + 1, 0xFF131117);
                        g.fill(x + w - inside - 2, y + row, x + w - inside, y + row + 1, 0xFF1C1A22);
                    }
                }
            } else {
                for (int px = in; px < w - in; px++) {
                    double f = (px - in + 0.5) / (w - 2 * in);
                    int level = f < 0.5 ? 2 : 1;
                    if (row == h - 1) level = Math.max(level - 1, 1);
                    g.fill(x + px, y + row, x + px + 1, y + row + 1, RAMP[level]);
                }
            }
        }
        int bottom = y + h - 2;
        int surface = bottom - fill;
        for (int row = surface; row < bottom; ) {
            int inside = potInside(row - y, w, h), rows = 1;
            while (row + rows < bottom && potInside(row + rows - y, w, h) == inside) rows++;
            g.enableScissor(x + inside, row, x + w - inside, row + rows);
            for (int tileY = y + 1; tileY < row + rows; tileY += 16) {
                for (int tileX = x; tileX < x + w; tileX += 16) {
                    if (tileY + 16 > row) g.blitSprite(RenderPipelines.GUI_TEXTURED, souls, tileX, tileY, 16, 16);
                }
            }
            g.disableScissor();
            row += rows;
        }
        if (fill > 0) g.fill(x + potInside(surface - y, w, h), surface, x + w - potInside(surface - y, w, h), surface + 1, 0xFFE6FFE9);
        for (int row = surface + 1; row < bottom; row++) { // the souls darker where they meet the walls and the floor
            int inside = potInside(row - y, w, h);
            g.fill(x + inside, row, x + inside + 1, row + 1, 0x60000000);
            g.fill(x + w - inside - 2, row, x + w - inside, row + 1, 0x60000000);
            if (row >= bottom - 2) g.fill(x + inside + 1, row, x + w - inside - 2, row + 1, 0x30000000);
        }
    }

    /** The blackstone from its darkest to the glint of light on it. */
    private static final int[] RAMP = {EDGE, PANEL_SHADOW, 0xFF211E27, PANEL, STONE_LINE, PANEL_LIGHT, 0xFF625B70, 0xFF8A82A0, 0xFFB4ABC8};

    /**
     * How light pixel {@code k} (0 the outside) of a wall {@code n} thick is in row {@code row}, as a step of
     * {@link #RAMP}. The light falls from the upper left.
     */
    private static int wall(boolean left, int k, int n, int row, int h) {
        double t = (row + 0.5) / h;
        boolean outer = k == 0, inner = k == n - 1;
        if (row == 0) return left ? 6 : 5;                                  // the top of the rim
        int level = t < 0.15 ? 6 : t < 0.35 ? 5 : t < 0.55 ? 4 : t < 0.72 ? 3 : t < 0.86 ? 2 : 1; // light at the rim, dark under the belly
        if (left && outer) level = t > 0.2 && t < 0.4 ? 8 : level + 1;    // a glint high on the lit flank
        if (left && k == 1 && t > 0.22 && t < 0.38) level = 6;
        if (!left && outer && t > 0.5) level--;                             // the shaded flank darker below
        if (inner) level = left ? 5 : 3;                                    // the inner edges
        return Math.clamp(level, 1, 8);
    }

    /** How many rows of a cauldron {@code h} high are liquid for {@code souls} of {@code capacity}. */
    public static int potFill(int h, int souls, int capacity) {
        int inside = h - 3;
        return capacity <= 0 ? 0 : (int) Math.round((double) Math.clamp(souls, 0, capacity) / capacity * inside);
    }
}
