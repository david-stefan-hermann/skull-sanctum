package skullsanctum;

import bcf.guidebook.GuideBookItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skullsanctum.altar.AltarMenu;
import skullsanctum.altar.SacrificeValues;
import skullsanctum.altar.SacrificialAltarBlock;
import skullsanctum.altar.SacrificialAltarBlockEntity;
import skullsanctum.altar.Sanctum;
import skullsanctum.atlas.HeadAtlasBlock;
import skullsanctum.atlas.HeadAtlasBlockEntity;
import skullsanctum.portable.PortableAtlasBlock;
import skullsanctum.portable.PortableAtlasBlockEntity;
import skullsanctum.portable.PortableAtlasItem;
import skullsanctum.shop.HeadCatalog;
import skullsanctum.shop.ShopNetwork;
import skullsanctum.tank.SoulTankBlock;
import skullsanctum.tank.SoulTankBlockEntity;
import skullsanctum.tank.TankMenu;

import java.util.function.Function;

public final class SkullSanctum implements ModInitializer {
    public static final String MOD_ID = "skullsanctum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** The heart of the sanctum: sacrifices go in here, and a right click checks and forms the structure. */
    public static final Block SACRIFICIAL_ALTAR = block("sacrificial_altar", props -> new SacrificialAltarBlock(props
            .mapColor(MapColor.COLOR_BLACK).strength(3.5f, 9.0f).sound(SoundType.POLISHED_DEEPSLATE)
            .requiresCorrectToolForDrops().lightLevel(state -> state.getValue(SacrificialAltarBlock.FORMED) ? 7 : 0)));
    /** What the sanctum's cauldron turns into: holds the souls. Never crafted, breaking it gives the cauldron back. */
    public static final Block SOUL_TANK = block("soul_tank", props -> new SoulTankBlock(props
            .mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
            // no light of its own, burning or not: the souls and the flame glow in their models without lighting up the tank

    /** What the sanctum's enchanting table turns into: opens the head browser. Breaking it gives the table back. */
    public static final Block HEAD_ATLAS = block("head_atlas", props -> new HeadAtlasBlock(props
            .mapColor(MapColor.COLOR_BLACK).strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
            .lightLevel(state -> 7)));
    /** A skull bound to an awake altar by a right click on it; set down anywhere, it is that sanctum's Head Atlas. */
    public static final Block PORTABLE_ATLAS = block("portable_atlas", props -> new PortableAtlasBlock(props
            .mapColor(MapColor.COLOR_BLACK).strength(0.4f).sound(PortableAtlasBlock.SOUND).noOcclusion() // quick to pick up by hand
            .pushReaction(PushReaction.DESTROY).lightLevel(state -> 7)));

    public static final BlockEntityType<SacrificialAltarBlockEntity> ALTAR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("sacrificial_altar"),
            FabricBlockEntityTypeBuilder.create(SacrificialAltarBlockEntity::new, SACRIFICIAL_ALTAR).build());
    public static final BlockEntityType<HeadAtlasBlockEntity> ATLAS_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("head_atlas"),
            FabricBlockEntityTypeBuilder.create(HeadAtlasBlockEntity::new, HEAD_ATLAS).build());
    public static final BlockEntityType<SoulTankBlockEntity> TANK_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("soul_tank"),
            FabricBlockEntityTypeBuilder.create(SoulTankBlockEntity::new, SOUL_TANK).build());
    public static final BlockEntityType<PortableAtlasBlockEntity> PORTABLE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("portable_atlas"),
            FabricBlockEntityTypeBuilder.create(PortableAtlasBlockEntity::new, PORTABLE_ATLAS).build());

    /** Where the altar a portable Head Atlas is bound to stands: its dimension and position. */
    public static final DataComponentType<GlobalPos> BOUND_ALTAR = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            id("bound_altar"), DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC).build());

    /** Everything the mod adds in one book. */
    public static final Item GUIDE_BOOK = item("guide_book", props -> new GuideBookItem(props.stacksTo(1), GuideBook.BOOK));
    public static final Item SACRIFICIAL_ALTAR_ITEM = item("sacrificial_altar",
            props -> new BlockItem(SACRIFICIAL_ALTAR, props.useBlockDescriptionPrefix()));
    /** The currency: what the altar makes of sacrifices, what heads cost. */
    public static final Item SOUL = item("soul", props -> new Item(props.rarity(Rarity.UNCOMMON)));
    public static final Item PORTABLE_ATLAS_ITEM = item("portable_atlas",
            props -> new PortableAtlasItem(PORTABLE_ATLAS, props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));

    public static final ExtendedMenuType<AltarMenu, BlockPos> ALTAR_MENU = Registry.register(BuiltInRegistries.MENU,
            id("sacrificial_altar"), new ExtendedMenuType<>(AltarMenu::client, BlockPos.STREAM_CODEC));
    public static final ExtendedMenuType<TankMenu, BlockPos> TANK_MENU = Registry.register(BuiltInRegistries.MENU,
            id("soul_tank"), new ExtendedMenuType<>(TankMenu::client, BlockPos.STREAM_CODEC));

    /** The green flame of the soul tank after a sacrifice. */
    public static final SimpleParticleType SOUL_FLAME = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            id("soul_flame"), FabricParticleTypes.simple());
    /** A bubble popping on the souls in the tank. */
    public static final SimpleParticleType SOUL_BUBBLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            id("soul_bubble"), FabricParticleTypes.simple());

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Config.load();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), FabricCreativeModeTab.builder()
                .icon(() -> new ItemStack(SACRIFICIAL_ALTAR_ITEM))
                .title(Component.translatable("itemGroup." + MOD_ID))
                .displayItems((params, out) -> {
                    out.accept(GUIDE_BOOK);
                    out.accept(SACRIFICIAL_ALTAR_ITEM);
                    out.accept(SOUL);
                    out.accept(PORTABLE_ATLAS_ITEM);
                })
                .build());
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("sacrifice_values"), new SacrificeValues.Loader());
        Sanctum.init();
        ShopNetwork.register();
        ServerLifecycleEvents.SERVER_STARTED.register(HeadCatalog::start);
        DevHooks.initServer();
        DevSelfTest.register();
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = id(name);
        return Registry.register(BuiltInRegistries.BLOCK, id,
                factory.apply(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        Identifier id = id(name);
        return Registry.register(BuiltInRegistries.ITEM, id, factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
