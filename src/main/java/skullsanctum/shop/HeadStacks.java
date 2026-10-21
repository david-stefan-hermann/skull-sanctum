package skullsanctum.shop;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Player head items: a catalog head with its texture, or the head of a player by name. */
public final class HeadStacks {
    private HeadStacks() {
    }

    public static ItemStack of(String name, String uuid, String texture, int count) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD, count);
        GameProfile profile = new GameProfile(profileId(uuid, texture), "",
                new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture))));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        return stack;
    }

    public static ItemStack of(HeadCatalog.Head head, int count) {
        return of(head.name(), head.uuid(), head.texture(), count);
    }

    /** The head of the player with this name; the game looks the skin up itself. */
    public static ItemStack player(String name, int count) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD, count);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
        return stack;
    }

    private static UUID profileId(String uuid, String texture) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        }
    }
}
