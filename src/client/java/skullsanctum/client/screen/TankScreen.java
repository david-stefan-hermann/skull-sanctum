package skullsanctum.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import skullsanctum.tank.TankMenu;

import java.util.List;

/**
 * The soul tank's screen, one window of stone: the title, the tank in cross-section (a cauldron of blackstone
 * holding the souls up to its level) with its one slot in the middle, and the player's inventory below.
 */
public class TankScreen extends AbstractContainerScreen<TankMenu> {
    public static final int WIDTH = 176, MAIN_Y = SanctumUi.HEADER_H, MAIN_H = 80, INVENTORY_Y = MAIN_Y + MAIN_H, HEIGHT = TankMenu.PLAYER_Y + 82;
    private static final int POT_W = 76, POT_H = 62, POT_X = (WIDTH - POT_W) / 2, POT_Y = MAIN_Y + 5;

    /** Bubbles in the cauldron; it is wider than the Head Atlas's vial, so they come more often. */
    private final SoulBubbles bubbles = new SoulBubbles(14);

    public TankScreen(TankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelX = TankMenu.PLAYER_X;
        inventoryLabelY = TankMenu.PLAYER_Y - 12;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        bubbles.tick(fill());
    }

    private int fill() {
        return SanctumUi.potFill(POT_H, menu.souls(), menu.capacity());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        SanctumUi.stone(g, leftPos, topPos, WIDTH, HEIGHT);
        SanctumUi.header(g, font, leftPos, topPos, title);
        int fill = fill();
        SanctumUi.cauldron(g, leftPos + POT_X, topPos + POT_Y, POT_W, POT_H, fill, minecraft.getAtlasManager().get(SanctumUi.SOUL_VIAL));
        int floor = topPos + POT_Y + POT_H - 2; // the cauldron's bottom, under the souls
        for (SoulBubbles.Bubble bubble : bubbles.all()) {
            if (bubble.height() >= fill - 1) continue; // the level dropped under it
            int by = floor - Math.round(bubble.height()), inside = SanctumUi.potInside(by - 1 - topPos - POT_Y, POT_W, POT_H);
            int bx = leftPos + POT_X + inside + 1 + Math.round(bubble.across() * (POT_W - 2 * inside - 2 - bubble.size()));
            g.fill(bx, by - bubble.size(), bx + bubble.size(), by, SoulBubbles.COLOUR);
        }
        for (Slot slot : menu.slots) SanctumUi.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        Slot slot = menu.slots.get(TankMenu.SLOT);
        if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
            if (!slot.hasItem() && menu.getCarried().isEmpty()) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.skullsanctum.tank.slot")), mouseX, mouseY);
            }
        } else if (isHovering(POT_X, POT_Y, POT_W, POT_H + 4, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.skullsanctum.tank.amount", menu.souls(), menu.capacity())), mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SanctumUi.LABEL, false);
    }
}
