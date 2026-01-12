package com.github.alexthe666.citadel.client.render;

import com.github.alexthe666.citadel.client.render.state.CitadelLecternRenderState;
import com.github.alexthe666.citadel.server.block.CitadelLecternBlockEntity;
import com.github.alexthe666.citadel.server.block.LecternBooks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Citadel: Updated to 1.21 BlockEntityRenderer API with render states
public class CitadelLecternRenderer implements BlockEntityRenderer<CitadelLecternBlockEntity, CitadelLecternRenderState> {
    private final BookModel bookModel;
    public static final ResourceLocation BOOK_PAGE_TEXTURE =  ResourceLocation.parse("citadel:textures/entity/lectern_book_pages.png");
    public static final ResourceLocation BOOK_BINDING_TEXTURE = ResourceLocation.parse("citadel:textures/entity/lectern_book_binding.png");
    private static final LecternBooks.BookData EMPTY_BOOK_DATA = new LecternBooks.BookData(0XC58439, 0XF4E9BF);
    
    public CitadelLecternRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public CitadelLecternRenderState createRenderState() {
        return new CitadelLecternRenderState();
    }

    @Override
    public void extractRenderState(CitadelLecternBlockEntity blockEntity, CitadelLecternRenderState renderState, float partialTick, Vec3 vec3, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, vec3, crumblingOverlay);
        renderState.hasBook = blockEntity.getBlockState().getValue(LecternBlock.HAS_BOOK);
        if (renderState.hasBook) {
            renderState.yRot = blockEntity.getBlockState().getValue(LecternBlock.FACING).getClockWise().toYRot();
            renderState.book = blockEntity.getBook();
        }
    }

    @Override
    public void submit(CitadelLecternRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        if (renderState.hasBook) {
            LecternBooks.BookData bookData = LecternBooks.BOOKS.getOrDefault(
                BuiltInRegistries.ITEM.getKey(renderState.book.getItem()), 
                EMPTY_BOOK_DATA
            );
            poseStack.pushPose();
            poseStack.translate(0.5D, 1.0625D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(-renderState.yRot));
            poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
            poseStack.translate(0.0D, -0.125D, 0.0D);
            // Citadel: 1.21 - BookModel.setupAnim now requires BookModel.State instead of floats
            BookModel.State bookState = new BookModel.State(0.0F, 0.1F, 0.9F, 1.2F);
            this.bookModel.setupAnim(bookState);
            
            // Citadel: 1.21 - submitTriangles doesn't exist, use submitModel instead
            submitNodeCollector.submitModel(
                this.bookModel,
                bookState,
                poseStack,
                RenderType.entityCutoutNoCull(BOOK_PAGE_TEXTURE),
                renderState.lightCoords,
                0,
                bookData.getPageColor(),
                null
            );
            submitNodeCollector.submitModel(
                this.bookModel,
                bookState,
                poseStack,
                RenderType.entityCutoutNoCull(BOOK_BINDING_TEXTURE),
                renderState.lightCoords,
                0,
                bookData.getBindingColor(),
                null
            );
            poseStack.popPose();
        }
    }
}