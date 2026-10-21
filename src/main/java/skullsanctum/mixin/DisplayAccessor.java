package skullsanctum.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The display setters vanilla keeps private (only its NBT sets them); the sanctum's ghost blocks need them. */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void skullsanctum$setTransformation(Transformation transformation);

    @Invoker("setGlowColorOverride")
    void skullsanctum$setGlowColorOverride(int color);

    @Invoker("setBrightnessOverride")
    void skullsanctum$setBrightnessOverride(Brightness brightness);
}
