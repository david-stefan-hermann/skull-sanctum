package skullsanctum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import skullsanctum.SkullSanctum;
import skullsanctum.portable.PortableAtlasBlockEntity;

/**
 * The portable Head Atlas: the skeleton skull model in the mod's skull texture (the block atlas's animated sprite, so
 * its eyes sparkle), floating a little up and down over the block's floor and turning slowly.
 */
public class PortableAtlasRenderer implements BlockEntityRenderer<PortableAtlasBlockEntity, PortableAtlasRenderer.State> {
    public static final SpriteId SKULL = Sheets.BLOCKS_MAPPER.apply(SkullSanctum.id("portable_atlas"));
    /** Degrees per tick: a full turn every nine seconds. */
    private static final float TURN = 2.0f;
    /** Where the skull's bottom floats (its 8 pixels sit in the middle of the block), and how far it rises and sinks. */
    private static final float HEIGHT = 0.25f, BOB = 0.08f, BOB_SPEED = 0.06f;
    private final SpriteGetter sprites;
    private final SkullModel skull;

    public static class State extends BlockEntityRenderState {
        float time;
    }

    public PortableAtlasRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
        skull = new SkullModel(context.bakeLayer(ModelLayers.SKELETON_SKULL));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PortableAtlasBlockEntity skull, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(skull, state, partialTick, camera, breakProgress);
        state.time = skull.time + partialTick;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5f, HEIGHT + Mth.sin(state.time * BOB_SPEED) * BOB, 0.5f);
        pose.rotateDegrees(Axis.YP, state.time * TURN);
        pose.scale(-1.0f, -1.0f, 1.0f); // as the skull block renderer does: the model hangs below its origin
        SkullModelBase.State model = new SkullModelBase.State();
        collector.submitModel(skull, model, pose, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, SKULL, sprites, 0);
        // the block being broken: its own pass, like vanilla's enchanting table
        if (state.breakProgress != null) {
            collector.order(1).submitCrumblingOverlay(skull, model, pose, SKULL.renderType(skull.renderType()),
                    state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
        }
        pose.popPose();
    }
}
