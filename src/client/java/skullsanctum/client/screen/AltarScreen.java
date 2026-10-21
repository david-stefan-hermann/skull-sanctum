package skullsanctum.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import skullsanctum.altar.AltarMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The altar's sacrifice screen: the nine offering slots in a niche of the wall, a bone with what they give, the
 * round seal that burns them, the player's inventory below. Embers rise off the offerings and burst when they burn.
 */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {
    public static final int WIDTH = 176;
    /** The seal's box, round inside it, and the middle of the column it stands in with the bone. */
    private static final int SEAL_X = 106, SEAL_Y = 52, SEAL_D = 32, COLUMN = 122;

    private final RandomSource random = RandomSource.create();
    private final List<float[]> sparks = new ArrayList<>(); // x, y, speed x, speed y, life
    private int ticks;

    public AltarScreen(AltarMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, WIDTH, AltarMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelX = AltarMenu.PLAYER_X;
        inventoryLabelY = AltarMenu.PLAYER_Y - 12;
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private boolean canSacrifice() {
        return menu.preview() > 0 || hasOffering();
    }

    private void sacrifice() {
        if (!canSacrifice()) return;
        for (Slot slot : menu.slots) {
            if (menu.isOffering(slot) && slot.hasItem()) burst(slot.x + 8, slot.y + 8, 7);
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AltarMenu.BUTTON_SACRIFICE);
    }

    /** For the dev screenshots. */
    public void devPressSacrifice() {
        sacrifice();
    }

    private boolean hasOffering() {
        for (Slot slot : menu.slots) {
            if (menu.isOffering(slot) && slot.hasItem()) return true;
        }
        return false;
    }

    private String amount() {
        int fraction = menu.previewFraction();
        return menu.preview() + (fraction > 0 ? String.format(".%02d", fraction) : "");
    }

    private boolean worthSomething() {
        return menu.preview() > 0 || menu.previewFraction() > 0;
    }

    private boolean overSeal(double mouseX, double mouseY) {
        double r = SEAL_D / 2.0, dx = mouseX - (leftPos + SEAL_X + r), dy = mouseY - (topPos + SEAL_Y + r);
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && overSeal(event.x(), event.y())) {
            sacrifice();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (hasOffering() && random.nextInt(3) == 0) { // embers rising off the offerings
            Slot slot = menu.slots.get(random.nextInt(AltarMenu.SLOTS));
            if (slot.hasItem()) sparks.add(new float[]{slot.x + 2 + random.nextInt(12), slot.y + 4 + random.nextInt(8), 0, -0.25f - random.nextFloat() * 0.2f, 30 + random.nextInt(20)});
        }
        sparks.removeIf(s -> {
            s[0] += s[2] + (float) Math.sin((ticks + s[1]) * 0.3) * 0.15f;
            s[1] += s[3];
            s[2] *= 0.9f;
            s[3] = s[3] * 0.95f - 0.02f;
            return --s[4] <= 0;
        });
    }

    private void burst(int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 0.4f + random.nextFloat() * 0.8f;
            sparks.add(new float[]{x, y, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 0.6f, 20 + random.nextInt(15)});
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        SanctumUi.stone(g, x, y, WIDTH, imageHeight);
        SanctumUi.header(g, font, x, y, title);
        int gx = x + AltarMenu.SLOT_X - 1, gy = y + AltarMenu.SLOT_Y - 1; // the grid's outer corner, 54 square
        shrine(g, gx - 6, gy - 17, 67, 76);
        bone(g, x + COLUMN - 38, y + 28, 76, 16);
        for (Slot slot : menu.slots) {
            SanctumUi.slot(g, x + slot.x - 1, y + slot.y - 1);
            if (menu.isOffering(slot) && slot.hasItem()) { // the offering lit from below
                g.fill(x + slot.x, y + slot.y + 10, x + slot.x + 16, y + slot.y + 16, 0x2862E077);
            }
        }
        boolean active = canSacrifice();
        seal(g, x + SEAL_X, y + SEAL_Y, SEAL_D, active, active && overSeal(mouseX, mouseY));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (float[] s : sparks) { // above the items, fading out
            int alpha = (int) (Math.min(1, s[4] / 15f) * 255);
            int size = s[4] > 25 ? 2 : 1;
            int sx = leftPos + Math.round(s[0]), sy = topPos + Math.round(s[1]);
            g.fill(sx, sy, sx + size, sy + size, alpha << 24 | (s[4] > 18 ? 0xC8FFB4 : 0x62E077));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SanctumUi.LABEL, false);
        Component souls = Component.translatable("gui.skullsanctum.souls", amount());
        g.text(font, souls, COLUMN - font.width(souls) / 2, 33, worthSomething() ? SanctumUi.INK_GREEN : SanctumUi.INK, false);
        // under the seal what it does, or a warning when the offerings are worth nothing
        Component under = hasOffering() && !worthSomething() ? Component.translatable("gui.skullsanctum.altar.worthless")
                : Component.translatable("gui.skullsanctum.altar.sacrifice");
        int colour = hasOffering() && !worthSomething() ? SanctumUi.WARN : canSacrifice() ? SanctumUi.SOUL : SanctumUi.SOFT;
        g.text(font, under, COLUMN - font.width(under) / 2, SEAL_Y + SEAL_D + 4, colour, false);
    }

    // ---- the pieces ----------------------------------------------------------------------------------------------

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** A round seal to press: a ring of runes round a skull, lit green while there is something to burn. */
    private void seal(GuiGraphicsExtractor g, int x, int y, int d, boolean active, boolean hover) {
        double r = d / 2.0, cx = x + r, cy = y + r;
        float pulse = active ? 0.6f + 0.4f * (float) Math.sin(ticks * 0.2) : 0;
        for (int py = y; py < y + d; py++) {
            for (int px = x; px < x + d; px++) {
                double dist = Math.hypot(px + 0.5 - cx, py + 0.5 - cy);
                int colour;
                if (dist > r) continue;
                else if (dist > r - 1) colour = SanctumUi.EDGE;
                else if (dist > r - 3) colour = px + py < cx + cy ? SanctumUi.PANEL_LIGHT : SanctumUi.PANEL_SHADOW;
                else if (dist > r - 4) colour = active ? mix(SanctumUi.SOUL_DARK, SanctumUi.SOUL, pulse) : SanctumUi.STONE_LINE;
                else colour = active ? (hover ? 0xFF1F7A36 : SanctumUi.SOUL_DEEP) : SanctumUi.PANEL;
                g.fill(px, py, px + 1, py + 1, colour);
            }
        }
        for (int i = 0; i < 12; i++) { // notches round the ring
            double angle = i * Math.PI / 6;
            int nx = (int) Math.floor(cx + Math.cos(angle) * (r - 2)), ny = (int) Math.floor(cy + Math.sin(angle) * (r - 2));
            g.fill(nx, ny, nx + 1, ny + 1, SanctumUi.EDGE);
        }
        SanctumUi.skull(g, (int) cx - 6, (int) cy - 6);
    }

    /** A pointed niche carved into the wall, a skull for its keystone and a soul lantern hanging on each side. */
    private void shrine(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int half = w / 2;
        int previous = half;
        for (int row = 0; row < h; row++) {
            int in = 0; // a pointed arch over the first rows: two arcs meeting at the top
            if (row < 16) {
                double t = (16 - row) / 16.0;
                in = Math.min(half, (int) Math.round(half * (1 - Math.sqrt(1 - t * t)) + t * t * 4));
            }
            g.fill(x + in, y + row, x + w - in, y + row + 1, SanctumUi.EDGE);
            if (w - 2 * in > 2) {
                g.fill(x + in + 1, y + row, x + w - in - 1, y + row + 1, 0xFF131117);
                // the lit side, unbroken where the arch steps out by more than a pixel
                g.fill(x + w - Math.max(in, previous - 1) - 2, y + row, x + w - in - 1, y + row + 1, SanctumUi.PANEL_LIGHT);
            }
            previous = in;
        }
        g.fill(x, y + h - 1, x + w, y + h, SanctumUi.PANEL_LIGHT);
        g.fill(x, y + h, x + w, y + h + 2, SanctumUi.PANEL); // the sill
        g.fill(x, y + h + 2, x + w, y + h + 3, SanctumUi.PANEL_SHADOW);
        miniSkull(g, x + half - 3, y + 6);
        for (int lx : new int[]{x + 8, x + w - 13}) {
            g.fill(lx + 2, y + 6, lx + 3, y + 9, SanctumUi.STONE_LINE); // the chain
            g.fill(lx, y + 9, lx + 5, y + 10, SanctumUi.PANEL_LIGHT);
            g.fill(lx, y + 10, lx + 5, y + 15, SanctumUi.PANEL);
            g.fill(lx + 1, y + 11, lx + 4, y + 14, mix(0xFF2FA84A, SanctumUi.SOUL_BRIGHT, (float) (0.5 + 0.5 * Math.sin(ticks * 0.25 + lx))));
            g.fill(lx, y + 15, lx + 5, y + 16, SanctumUi.PANEL_SHADOW);
        }
    }

    /** A skull seven pixels square. */
    private static void miniSkull(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y, x + 6, y + 1, SanctumUi.BONE);
        g.fill(x, y + 1, x + 7, y + 4, SanctumUi.BONE);
        g.fill(x + 1, y + 4, x + 6, y + 6, SanctumUi.BONE);
        g.fill(x + 2, y + 6, x + 3, y + 7, SanctumUi.BONE_DARK);
        g.fill(x + 4, y + 6, x + 5, y + 7, SanctumUi.BONE_DARK);
        g.fill(x + 1, y + 2, x + 3, y + 4, SanctumUi.SOUL);
        g.fill(x + 4, y + 2, x + 6, y + 4, SanctumUi.SOUL);
        g.fill(x + 3, y + 4, x + 4, y + 5, SanctumUi.INK);
    }

    /** Whether pixel x, y lies on a bone {@code w} by {@code h}: a shaft, thicker towards its ends, and two knuckles at each end. */
    private static boolean onBone(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        double r = 3.6;
        for (double cx : new double[]{3.5, w - 3.5}) {
            for (double cy : new double[]{3.5, h - 3.5}) {
                if (Math.pow(x + 0.5 - cx, 2) + Math.pow(y + 0.5 - cy, 2) <= r * r) return true;
            }
        }
        int flare = x < 9 || x >= w - 9 ? 1 : 0;
        return x >= 4 && x < w - 4 && y >= 3 - flare && y < h - 3 + flare;
    }

    /** A bone lying across, to write the offering's worth on, lit from above. */
    private static void bone(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                if (!onBone(px, py, w, h)) continue;
                int colour;
                if (!onBone(px + 1, py, w, h) || !onBone(px - 1, py, w, h) || !onBone(px, py + 1, w, h) || !onBone(px, py - 1, w, h)) {
                    colour = 0xFF8A8170;                                    // the outline
                } else if (!onBone(px, py - 2, w, h)) colour = SanctumUi.BONE_LIGHT; // the lit top
                else if (!onBone(px, py + 2, w, h)) colour = SanctumUi.BONE_EDGE;   // the shaded underside
                else if (!onBone(px, py + 3, w, h)) colour = SanctumUi.BONE_DARK;
                else colour = SanctumUi.BONE;
                g.fill(x + px, y + py, x + px + 1, y + py + 1, colour);
            }
        }
    }
}
