package skullsanctum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
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
import skullsanctum.atlas.HeadAtlasBlockEntity;

/**
 * The book above the Head Atlas: the enchanting table's book in the atlas's own cover (a wither skeleton skull on it),
 * turning to the nearest player and opening for them.
 */
public class HeadAtlasRenderer implements BlockEntityRenderer<HeadAtlasBlockEntity, HeadAtlasRenderer.State> {
    public static final SpriteId BOOK = Sheets.BLOCKS_MAPPER.apply(SkullSanctum.id("head_atlas_book"));
    private final SpriteGetter sprites;
    private final BookModel book;

    public static class State extends BlockEntityRenderState {
        float time, yRot, flip, open;
    }

    public HeadAtlasRenderer(BlockEntityRendererProvider.Context context) {
        sprites = context.sprites();
        book = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HeadAtlasBlockEntity atlas, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(atlas, state, partialTick, camera, breakProgress);
        state.flip = Mth.lerp(partialTick, atlas.oFlip, atlas.flip);
        state.open = Mth.lerp(partialTick, atlas.oOpen, atlas.open);
        state.time = atlas.time + partialTick;
        float turn = Mth.wrapDegrees((atlas.rot - atlas.oRot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        state.yRot = atlas.oRot + turn * partialTick;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5f, 0.75f, 0.5f);
        pose.translate(0.0f, 0.1f + Mth.sin(state.time * 0.1f) * 0.01f, 0.0f);
        pose.mulPose(Axis.YP.rotation(-state.yRot));
        pose.mulPose(Axis.ZP.rotationDegrees(80.0f));
        float left = Mth.frac(state.flip + 0.25f) * 1.6f - 0.3f;
        float right = Mth.frac(state.flip + 0.75f) * 1.6f - 0.3f;
        BookModel.State model = BookModel.State.forAnimation(state.time, Mth.clamp(left, 0.0f, 1.0f),
                Mth.clamp(right, 0.0f, 1.0f), state.open);
        collector.submitModel(book, model, pose, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, BOOK, sprites, 0,
                state.breakProgress);
        pose.popPose();
    }
}
