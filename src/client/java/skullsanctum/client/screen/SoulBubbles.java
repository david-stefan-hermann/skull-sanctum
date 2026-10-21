package skullsanctum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Bubbles rising through souls in a vessel on a screen: now and then one starts at the floor and pops at the
 * surface, sometimes audibly. The screen ticks them and draws them where its vessel is.
 */
public final class SoulBubbles {
    /** One bubble: where across the vessel's inside (0 to 1), how far above its floor in pixels, how fast it rises. */
    public record Bubble(float across, float height, float speed) {
        public int size() {
            return speed > 0.45f ? 2 : 1;
        }
    }

    private final List<Bubble> bubbles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final int rarity;

    /** {@code rarity}: one bubble starts on average every that many ticks. */
    public SoulBubbles(int rarity) {
        this.rarity = rarity;
    }

    /** One tick for souls {@code fill} pixels deep. */
    public void tick(int fill) {
        if (fill > 4 && random.nextInt(rarity) == 0) {
            bubbles.add(new Bubble(0.15f + random.nextFloat() * 0.7f, 1, 0.3f + random.nextFloat() * 0.3f));
        }
        for (var it = bubbles.listIterator(); it.hasNext(); ) {
            Bubble bubble = it.next();
            if (bubble.height() + bubble.speed() >= fill - 1) {
                it.remove();
                if (random.nextInt(4) == 0) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LAVA_POP, 1.3f + random.nextFloat() * 0.5f, 0.12f));
                }
            } else {
                it.set(new Bubble(bubble.across(), bubble.height() + bubble.speed(), bubble.speed()));
            }
        }
    }

    public List<Bubble> all() {
        return bubbles;
    }

    /** The colour of a bubble: the same pale green as the souls' surface. */
    public static final int COLOUR = 0xFFE6FFE9;
}
