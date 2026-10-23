package skullsanctum.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skullsanctum.DevHooks;
import skullsanctum.SkullSanctum;
import skullsanctum.client.screen.AltarScreen;
import skullsanctum.client.screen.HeadBrowserScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * With -Dskullsanctum.dev.screenshot=NAME the client hides the HUD after joining. Without actions it waits until the
 * server has put it on each view point of -Dskullsanctum.dev.views and saves screenshots/NAME-1.png, NAME-2.png, ...;
 * with -Dskullsanctum.dev.actions="tick:command;..." it runs those commands at those client ticks instead and quits
 * after the last one; a command and its argument are split by '=' or a space, '+' stands for a space. Commands:
 * {@code use x,y,z[,face]} (right-click a block), {@code quick N} (shift-click slot N of
 * the open screen), {@code sacrifice}, {@code tab N}, {@code search text}, {@code buy N [stack]}, {@code pick N}, {@code fav N},
 * {@code close}, {@code inventory} (opens the player's inventory), {@code key letter} (a key press to the open screen), {@code mouse x,y} (GUI pixels) and
 * {@code shot name} (screenshots/name.png).
 */
public final class DevClientHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkullSanctum.MOD_ID);
    private static final int SETTLE_TICKS = 60;
    private static int ticks, view, still, after;
    private static boolean done;

    private record Action(int tick, String command, String argument) {
    }

    private DevClientHooks() {
    }

    public static void init() {
        String name = System.getProperty("skullsanctum.dev.screenshot");
        double[][] views = DevHooks.parseViews();
        if (name == null || views == null) return;
        List<Action> actions = parseActions(System.getProperty("skullsanctum.dev.actions", ""));
        LOGGER.info("Dev screenshot active: {} ({} view points, {} actions)", name, views.length, actions.size());
        int end = actions.isEmpty() ? DevHooks.FIRST_TICK + views.length * DevHooks.TICKS_PER_VIEW + 400
                : actions.get(actions.size() - 1).tick() + 30;
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft, name, views, actions, end));
    }

    private static List<Action> parseActions(String raw) {
        List<Action> actions = new ArrayList<>();
        for (String part : raw.split(";")) {
            if (part.isBlank()) continue;
            int colon = part.indexOf(':');
            String body = part.substring(colon + 1).trim();
            int space = body.indexOf('=') >= 0 ? body.indexOf('=') : body.indexOf(' ');
            actions.add(new Action(Integer.parseInt(part.substring(0, colon).trim()),
                    space < 0 ? body : body.substring(0, space), space < 0 ? "" : body.substring(space + 1).trim()));
        }
        actions.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        return actions;
    }

    private static void tick(Minecraft minecraft, String name, double[][] views, List<Action> actions, int end) {
        if (done || minecraft.level == null || minecraft.player == null) return;
        ticks++;
        if (ticks == 1) { // the sounds a run plays, for the log (ALSOFT_DRIVERS=null lets them play without a device)
            minecraft.getSoundManager().addListener((sound, events, range) -> LOGGER.info("Dev: sound {} pitch {}",
                    sound.getIdentifier(), sound.getPitch()));
        }
        if (!minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
        minecraft.gui.toastManager().clear();
        if (!actions.isEmpty()) {
            for (Action action : actions) {
                if (action.tick() == ticks) run(minecraft, action);
            }
            if (ticks >= end) quit(minecraft);
            return;
        }
        if (view < views.length) {
            double[] v = views[view];
            boolean there = minecraft.player.position().distanceToSqr(v[0], v[1], v[2]) < 0.01;
            still = there ? still + 1 : 0;
            if (still == SETTLE_TICKS) {
                grab(minecraft, name + "-" + (view + 1) + ".png");
                view++;
                still = 0;
            }
        }
        if ((view >= views.length && ++after > 20) || ticks >= end) {
            if (view < views.length) LOGGER.warn("Dev screenshot: view {} never reached", view + 1);
            quit(minecraft);
        }
    }

    private static void quit(Minecraft minecraft) {
        done = true;
        minecraft.stop();
    }

    private static void run(Minecraft minecraft, Action action) {
        LOGGER.info("Dev action at {}: {} {} (screen {})", ticks, action.command(), action.argument(),
                minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getSimpleName());
        String[] args = action.argument().isEmpty() ? new String[0] : action.argument().split("[ ,+]+");
        switch (action.command()) {
            case "use" -> {
                if (minecraft.gui.screen() != null) minecraft.gui.setScreen(null);
                BlockPos pos = new BlockPos(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                Direction face = args.length > 3 ? Direction.byName(args[3]) : Direction.SOUTH;
                var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
                var result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
                LOGGER.info("Dev: right-click on {} face {} -> {}", pos.toShortString(), face, result);
            }
            case "quick" -> {
                if (minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) {
                    minecraft.gameMode.handleContainerInput(screen.getMenu().containerId, Integer.parseInt(args[0]), 0,
                            ContainerInput.QUICK_MOVE, minecraft.player);
                }
            }
            case "sacrifice" -> {
                if (minecraft.gui.screen() instanceof AltarScreen altar) altar.devPressSacrifice();
            }
            case "tab" -> browser(minecraft, b -> b.devSelectTab(Integer.parseInt(args[0])));
            case "search" -> browser(minecraft, b -> b.devSearch(action.argument().replace('+', ' ')));
            case "buy" -> browser(minecraft, b -> b.devBuy(Integer.parseInt(args[0]), args.length > 1));
            case "pick" -> browser(minecraft, b -> b.devPick(Integer.parseInt(args[0])));
            case "fav" -> browser(minecraft, b -> b.devFavourite(Integer.parseInt(args[0])));
            case "close" -> minecraft.player.closeContainer();
            case "inventory" -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
            case "cmd" -> minecraft.player.connection.sendCommand(action.argument().replace('+', ' ')); // cmd data+merge+...
            case "key" -> { // a key press as the keyboard sends it: key=e
                // 26.3 key codes are scancodes, so look them up by their key.keyboard.* name like options.txt does
                int key = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard." + Character.toLowerCase(args[0].charAt(0))).getValue();
                if (minecraft.gui.screen() != null) minecraft.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(key, key, 0));
            }
            case "click" -> click(minecraft, args); // click seal|cell+N left|right: a real click through mouseClicked
            case "mouse" -> moveMouse(minecraft, Double.parseDouble(args[0]), Double.parseDouble(args[1]));
            case "shot" -> grab(minecraft, action.argument() + ".png");
            default -> LOGGER.warn("Dev: unknown action {}", action.command());
        }
    }

    private static void browser(Minecraft minecraft, java.util.function.Consumer<HeadBrowserScreen> action) {
        if (minecraft.gui.screen() instanceof HeadBrowserScreen browser) action.accept(browser);
        else LOGGER.warn("Dev: no head browser open");
    }

    private static void click(Minecraft minecraft, String[] args) {
        var screen = minecraft.gui.screen();
        double[] at;
        if (args[0].equals("seal") && screen instanceof AltarScreen altar) at = altar.devSealCentre();
        else if (args[0].equals("cell") && screen instanceof HeadBrowserScreen browser) at = browser.devCellCentre(Integer.parseInt(args[1]));
        else {
            LOGGER.warn("Dev: nothing to click for {}", String.join(" ", args));
            return;
        }
        int button = args[args.length - 1].equals("right") ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT;
        var event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(button, 0));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        LOGGER.info("Dev: {} click on {} at {}, {} -> {}{}", args[args.length - 1], args[0], at[0], at[1], handled,
                screen instanceof HeadBrowserScreen browser ? " (" + browser.devCellState(Integer.parseInt(args[1])) + ")" : "");
    }

    /** Puts the mouse on GUI pixel x, y (the window's pixels are the GUI's times its scale). */
    private static void moveMouse(Minecraft minecraft, double x, double y) {
        double scale = minecraft.getWindow().getGuiScale();
        try {
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(minecraft.mouseHandler, x * scale);
            fy.setDouble(minecraft.mouseHandler, y * scale);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Dev: cannot move the mouse: {}", e.toString());
        }
    }

    private static void grab(Minecraft minecraft, String file) {
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message ->
                LOGGER.info("Screenshot: {}", message.getString()));
    }
}
