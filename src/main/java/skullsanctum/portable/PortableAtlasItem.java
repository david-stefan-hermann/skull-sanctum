package skullsanctum.portable;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import skullsanctum.SkullSanctum;

import java.util.function.Consumer;

/** The portable Head Atlas in the hand: its tooltip names the altar it is bound to, or says that it is bound to none. */
public class PortableAtlasItem extends BlockItem {
    public PortableAtlasItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        GlobalPos altar = stack.get(SkullSanctum.BOUND_ALTAR);
        if (altar == null) {
            lines.accept(Component.translatable("item.skullsanctum.portable_atlas.unbound").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("item.skullsanctum.portable_atlas.bound", altar.pos().toShortString(),
                    altar.dimension().identifier().getPath()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
