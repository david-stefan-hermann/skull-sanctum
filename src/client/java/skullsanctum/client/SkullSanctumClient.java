package skullsanctum.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.particle.BubblePopParticle;
import net.minecraft.client.particle.FlameParticle;
import skullsanctum.Config;
import skullsanctum.SkullSanctum;
import skullsanctum.client.render.HeadAtlasRenderer;
import skullsanctum.client.screen.AltarScreen;
import skullsanctum.client.screen.HeadBrowserScreen;
import skullsanctum.client.screen.TankScreen;
import skullsanctum.shop.ShopNetwork;

public final class SkullSanctumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(SkullSanctum.ALTAR_MENU, AltarScreen::new);
        MenuScreens.register(SkullSanctum.TANK_MENU, TankScreen::new);
        BlockEntityRendererRegistry.register(SkullSanctum.ATLAS_BLOCK_ENTITY, HeadAtlasRenderer::new);
        ParticleProviderRegistry.getInstance().register(SkullSanctum.SOUL_FLAME, FlameParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(SkullSanctum.SOUL_BUBBLE, BubblePopParticle.Provider::new);
        ClientPlayNetworking.registerGlobalReceiver(ShopNetwork.Settings.TYPE,
                (payload, context) -> Config.setShown(payload.tankCapacity(), payload.playerHeadPrice()));
        ClientPlayNetworking.registerGlobalReceiver(ShopNetwork.Open.TYPE,
                (payload, context) -> context.client().gui.setScreen(new HeadBrowserScreen(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ShopNetwork.Page.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof HeadBrowserScreen browser) browser.accept(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(ShopNetwork.Result.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof HeadBrowserScreen browser) browser.result(payload);
        });
        DevClientHooks.init();
    }
}
