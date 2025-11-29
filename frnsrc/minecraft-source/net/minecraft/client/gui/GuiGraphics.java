package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Divisor;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

@OnlyIn(Dist.CLIENT)
public class GuiGraphics implements net.minecraftforge.client.extensions.IForgeGuiGraphics {
   public static final float MAX_GUI_Z = 10000.0F;
   public static final float MIN_GUI_Z = -10000.0F;
   private static final int EXTRA_SPACE_AFTER_FIRST_TOOLTIP_LINE = 2;
   private final Minecraft minecraft;
   private final PoseStack pose;
   private final MultiBufferSource.BufferSource bufferSource;
   private final GuiGraphics.ScissorStack scissorStack = new GuiGraphics.ScissorStack();
   private boolean managed;

   private GuiGraphics(Minecraft pMinecraft, PoseStack pPose, MultiBufferSource.BufferSource pBufferSource) {
      this.minecraft = pMinecraft;
      this.pose = pPose;
      this.bufferSource = pBufferSource;
   }

   public GuiGraphics(Minecraft pMinecraft, MultiBufferSource.BufferSource pBufferSource) {
      this(pMinecraft, new PoseStack(), pBufferSource);
   }

   /** @deprecated */
   @Deprecated
   public void drawManaged(Runnable pRunnable) {
      this.flush();
      this.managed = true;
      pRunnable.run();
      this.managed = false;
      this.flush();
   }

   /** @deprecated */
   @Deprecated
   private void flushIfUnmanaged() {
      if (!this.managed) {
         this.flush();
      }

   }

   /** @deprecated */
   @Deprecated
   private void flushIfManaged() {
      if (this.managed) {
         this.flush();
      }

   }

   public int guiWidth() {
      return this.minecraft.getWindow().getGuiScaledWidth();
   }

   public int guiHeight() {
      return this.minecraft.getWindow().getGuiScaledHeight();
   }

   public PoseStack pose() {
      return this.pose;
   }

   public MultiBufferSource.BufferSource bufferSource() {
      return this.bufferSource;
   }

   public void flush() {
      RenderSystem.disableDepthTest();
      this.bufferSource.endBatch();
      RenderSystem.enableDepthTest();
   }

   public void hLine(int pMinX, int pMaxX, int pY, int pColor) {
      this.hLine(RenderType.gui(), pMinX, pMaxX, pY, pColor);
   }

   public void hLine(RenderType pRenderType, int pMinX, int pMaxX, int pY, int pColor) {
      if (pMaxX < pMinX) {
         int i = pMinX;
         pMinX = pMaxX;
         pMaxX = i;
      }

      this.fill(pRenderType, pMinX, pY, pMaxX + 1, pY + 1, pColor);
   }

   public void vLine(int pX, int pMinY, int pMaxY, int pColor) {
      this.vLine(RenderType.gui(), pX, pMinY, pMaxY, pColor);
   }

   public void vLine(RenderType pRenderType, int pX, int pMinY, int pMaxY, int pColor) {
      if (pMaxY < pMinY) {
         int i = pMinY;
         pMinY = pMaxY;
         pMaxY = i;
      }

      this.fill(pRenderType, pX, pMinY + 1, pX + 1, pMaxY, pColor);
   }

   public void enableScissor(int pMinX, int pMinY, int pMaxX, int pMaxY) {
      this.applyScissor(this.scissorStack.push(new ScreenRectangle(pMinX, pMinY, pMaxX - pMinX, pMaxY - pMinY)));
   }

   public void disableScissor() {
      this.applyScissor(this.scissorStack.pop());
   }

   private void applyScissor(@Nullable ScreenRectangle pRectangle) {
      this.flushIfManaged();
      if (pRectangle != null) {
         Window window = Minecraft.getInstance().getWindow();
         int i = window.getHeight();
         double d0 = window.getGuiScale();
         double d1 = (double)pRectangle.left() * d0;
         double d2 = (double)i - (double)pRectangle.bottom() * d0;
         double d3 = (double)pRectangle.width() * d0;
         double d4 = (double)pRectangle.height() * d0;
         RenderSystem.enableScissor((int)d1, (int)d2, Math.max(0, (int)d3), Math.max(0, (int)d4));
      } else {
         RenderSystem.disableScissor();
      }

   }

   public void setColor(float pRed, float pGreen, float pBlue, float pAlpha) {
      this.flushIfManaged();
      RenderSystem.setShaderColor(pRed, pGreen, pBlue, pAlpha);
   }

   public void fill(int pMinX, int pMinY, int pMaxX, int pMaxY, int pColor) {
      this.fill(pMinX, pMinY, pMaxX, pMaxY, 0, pColor);
   }

   public void fill(int pMinX, int pMinY, int pMaxX, int pMaxY, int pZ, int pColor) {
      this.fill(RenderType.gui(), pMinX, pMinY, pMaxX, pMaxY, pZ, pColor);
   }

   public void fill(RenderType pRenderType, int pMinX, int pMinY, int pMaxX, int pMaxY, int pColor) {
      this.fill(pRenderType, pMinX, pMinY, pMaxX, pMaxY, 0, pColor);
   }

   public void fill(RenderType pRenderType, int pMinX, int pMinY, int pMaxX, int pMaxY, int pZ, int pColor) {
      Matrix4f matrix4f = this.pose.last().pose();
      if (pMinX < pMaxX) {
         int i = pMinX;
         pMinX = pMaxX;
         pMaxX = i;
      }

      if (pMinY < pMaxY) {
         int j = pMinY;
         pMinY = pMaxY;
         pMaxY = j;
      }

      float f3 = (float)FastColor.ARGB32.alpha(pColor) / 255.0F;
      float f = (float)FastColor.ARGB32.red(pColor) / 255.0F;
      float f1 = (float)FastColor.ARGB32.green(pColor) / 255.0F;
      float f2 = (float)FastColor.ARGB32.blue(pColor) / 255.0F;
      VertexConsumer vertexconsumer = this.bufferSource.getBuffer(pRenderType);
      vertexconsumer.vertex(matrix4f, (float)pMinX, (float)pMinY, (float)pZ).color(f, f1, f2, f3).endVertex();
      vertexconsumer.vertex(matrix4f, (float)pMinX, (float)pMaxY, (float)pZ).color(f, f1, f2, f3).endVertex();
      vertexconsumer.vertex(matrix4f, (float)pMaxX, (float)pMaxY, (float)pZ).color(f, f1, f2, f3).endVertex();
      vertexconsumer.vertex(matrix4f, (float)pMaxX, (float)pMinY, (float)pZ).color(f, f1, f2, f3).endVertex();
      this.flushIfUnmanaged();
   }

   public void fillGradient(int pX1, int pY1, int pX2, int pY2, int pColorFrom, int pColorTo) {
      this.fillGradient(pX1, pY1, pX2, pY2, 0, pColorFrom, pColorTo);
   }

   public void fillGradient(int pX1, int pY1, int pX2, int pY2, int pZ, int pColorFrom, int pColorTo) {
      this.fillGradient(RenderType.gui(), pX1, pY1, pX2, pY2, pColorFrom, pColorTo, pZ);
   }

   public void fillGradient(RenderType pRenderType, int pX1, int pY1, int pX2, int pY2, int pColorFrom, int pColorTo, int pZ) {
      VertexConsumer vertexconsumer = this.bufferSource.getBuffer(pRenderType);
      this.fillGradient(vertexconsumer, pX1, pY1, pX2, pY2, pZ, pColorFrom, pColorTo);
      this.flushIfUnmanaged();
   }

   private void fillGradient(VertexConsumer pConsumer, int pX1, int pY1, int pX2, int pY2, int pZ, int pColorFrom, int pColorTo) {
      float f = (float)FastColor.ARGB32.alpha(pColorFrom) / 255.0F;
      float f1 = (float)FastColor.ARGB32.red(pColorFrom) / 255.0F;
      float f2 = (float)FastColor.ARGB32.green(pColorFrom) / 255.0F;
      float f3 = (float)FastColor.ARGB32.blue(pColorFrom) / 255.0F;
      float f4 = (float)FastColor.ARGB32.alpha(pColorTo) / 255.0F;
      float f5 = (float)FastColor.ARGB32.red(pColorTo) / 255.0F;
      float f6 = (float)FastColor.ARGB32.green(pColorTo) / 255.0F;
      float f7 = (float)FastColor.ARGB32.blue(pColorTo) / 255.0F;
      Matrix4f matrix4f = this.pose.last().pose();
      pConsumer.vertex(matrix4f, (float)pX1, (float)pY1, (float)pZ).color(f1, f2, f3, f).endVertex();
      pConsumer.vertex(matrix4f, (float)pX1, (float)pY2, (float)pZ).color(f5, f6, f7, f4).endVertex();
      pConsumer.vertex(matrix4f, (float)pX2, (float)pY2, (float)pZ).color(f5, f6, f7, f4).endVertex();
      pConsumer.vertex(matrix4f, (float)pX2, (float)pY1, (float)pZ).color(f1, f2, f3, f).endVertex();
   }

   public void drawCenteredString(Font pFont, String pText, int pX, int pY, int pColor) {
      this.drawString(pFont, pText, pX - pFont.width(pText) / 2, pY, pColor);
   }

   public void drawCenteredString(Font pFont, Component pText, int pX, int pY, int pColor) {
      FormattedCharSequence formattedcharsequence = pText.getVisualOrderText();
      this.drawString(pFont, formattedcharsequence, pX - pFont.width(formattedcharsequence) / 2, pY, pColor);
   }

   public void drawCenteredString(Font pFont, FormattedCharSequence pText, int pX, int pY, int pColor) {
      this.drawString(pFont, pText, pX - pFont.width(pText) / 2, pY, pColor);
   }

   public int drawString(Font pFont, @Nullable String pText, int pX, int pY, int pColor) {
      return this.drawString(pFont, pText, pX, pY, pColor, true);
   }

   public int drawString(Font pFont, @Nullable String pText, int pX, int pY, int pColor, boolean pDropShadow) {
      return this.drawString(pFont, pText, (float)pX, (float)pY, pColor, pDropShadow);
   }

   // Forge: Add float variant for x,y coordinates
   public int drawString(Font pFont, @Nullable String pText, float pX, float pY, int pColor, boolean pDropShadow) {
      if (pText == null) {
         return 0;
      } else {
         int i = pFont.drawInBatch(pText, (float)pX, (float)pY, pColor, pDropShadow, this.pose.last().pose(), this.bufferSource, Font.DisplayMode.NORMAL, 0, 15728880, pFont.isBidirectional());
         this.flushIfUnmanaged();
         return i;
      }
   }

   public int drawString(Font pFont, FormattedCharSequence pText, int pX, int pY, int pColor) {
      return this.drawString(pFont, pText, pX, pY, pColor, true);
   }

   public int drawString(Font pFont, FormattedCharSequence pText, int pX, int pY, int pColor, boolean pDropShadow) {
      return this.drawString(pFont, pText, (float)pX, (float)pY, pColor, pDropShadow);
   }

   // Forge: Add float variant for x,y coordinates
   public int drawString(Font pFont, FormattedCharSequence pText, float pX, float pY, int pColor, boolean pDropShadow) {
      int i = pFont.drawInBatch(pText, (float)pX, (float)pY, pColor, pDropShadow, this.pose.last().pose(), this.bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
      this.flushIfUnmanaged();
      return i;
   }

   public int drawString(Font pFont, Component pText, int pX, int pY, int pColor) {
      return this.drawString(pFont, pText, pX, pY, pColor, true);
   }

   public int drawString(Font pFont, Component pText, int pX, int pY, int pColor, boolean pDropShadow) {
      return this.drawString(pFont, pText.getVisualOrderText(), pX, pY, pColor, pDropShadow);
   }

   public void drawWordWrap(Font pFont, FormattedText pText, int pX, int pY, int pLineWidth, int pColor) {
      for(FormattedCharSequence formattedcharsequence : pFont.split(pText, pLineWidth)) {
         this.drawString(pFont, formattedcharsequence, pX, pY, pColor, false);
         pY += 9;
      }

   }

   public void blit(int pX, int pY, int pBlitOffset, int pWidth, int pHeight, TextureAtlasSprite pSprite) {
      this.innerBlit(pSprite.atlasLocation(), pX, pX + pWidth, pY, pY + pHeight, pBlitOffset, pSprite.getU0(), pSprite.getU1(), pSprite.getV0(), pSprite.getV1());
   }

   public void blit(int pX, int pY, int pBlitOffset, int pWidth, int pHeight, TextureAtlasSprite pSprite, float pRed, float pGreen, float pBlue, float pAlpha) {
      this.innerBlit(pSprite.atlasLocation(), pX, pX + pWidth, pY, pY + pHeight, pBlitOffset, pSprite.getU0(), pSprite.getU1(), pSprite.getV0(), pSprite.getV1(), pRed, pGreen, pBlue, pAlpha);
   }

   public void renderOutline(int pX, int pY, int pWidth, int pHeight, int pColor) {
      this.fill(pX, pY, pX + pWidth, pY + 1, pColor);
      this.fill(pX, pY + pHeight - 1, pX + pWidth, pY + pHeight, pColor);
      this.fill(pX, pY + 1, pX + 1, pY + pHeight - 1, pColor);
      this.fill(pX + pWidth - 1, pY + 1, pX + pWidth, pY + pHeight - 1, pColor);
   }

   public void blit(ResourceLocation pAtlasLocation, int pX, int pY, int pUOffset, int pVOffset, int pUWidth, int pVHeight) {
      this.blit(pAtlasLocation, pX, pY, 0, (float)pUOffset, (float)pVOffset, pUWidth, pVHeight, 256, 256);
   }

   public void blit(ResourceLocation pAtlasLocation, int pX, int pY, int pBlitOffset, float pUOffset, float pVOffset, int pUWidth, int pVHeight, int pTextureWidth, int pTextureHeight) {
      this.blit(pAtlasLocation, pX, pX + pUWidth, pY, pY + pVHeight, pBlitOffset, pUWidth, pVHeight, pUOffset, pVOffset, pTextureWidth, pTextureHeight);
   }

   public void blit(ResourceLocation pAtlasLocation, int pX, int pY, int pWidth, int pHeight, float pUWidth, float pVHeight, int pUOffset, int pVOffset, int pTextureWidth, int pTextureHeight) {
      this.blit(pAtlasLocation, pX, pX + pWidth, pY, pY + pHeight, 0, pUOffset, pVOffset, pUWidth, pVHeight, pTextureWidth, pTextureHeight);
   }

   public void blit(ResourceLocation pAtlasLocation, int pX, int pY, float pUOffset, float pVOffset, int pWidth, int pHeight, int pTextureWidth, int pTextureHeight) {
      this.blit(pAtlasLocation, pX, pY, pWidth, pHeight, pUOffset, pVOffset, pWidth, pHeight, pTextureWidth, pTextureHeight);
   }

   void blit(ResourceLocation pAtlasLocation, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, int pUWidth, int pVHeight, float pUOffset, float pVOffset, int pTextureWidth, int pTextureHeight) {
      this.innerBlit(pAtlasLocation, pX1, pX2, pY1, pY2, pBlitOffset, (pUOffset + 0.0F) / (float)pTextureWidth, (pUOffset + (float)pUWidth) / (float)pTextureWidth, (pVOffset + 0.0F) / (float)pTextureHeight, (pVOffset + (float)pVHeight) / (float)pTextureHeight);
   }

   void innerBlit(ResourceLocation pAtlasLocation, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, float pMinU, float pMaxU, float pMinV, float pMaxV) {
      RenderSystem.setShaderTexture(0, pAtlasLocation);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      Matrix4f matrix4f = this.pose.last().pose();
      BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
      bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferbuilder.vertex(matrix4f, (float)pX1, (float)pY1, (float)pBlitOffset).uv(pMinU, pMinV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX1, (float)pY2, (float)pBlitOffset).uv(pMinU, pMaxV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX2, (float)pY2, (float)pBlitOffset).uv(pMaxU, pMaxV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX2, (float)pY1, (float)pBlitOffset).uv(pMaxU, pMinV).endVertex();
      BufferUploader.drawWithShader(bufferbuilder.end());
   }

   void innerBlit(ResourceLocation pAtlasLocation, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, float pMinU, float pMaxU, float pMinV, float pMaxV, float pRed, float pGreen, float pBlue, float pAlpha) {
      RenderSystem.setShaderTexture(0, pAtlasLocation);
      RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
      RenderSystem.enableBlend();
      Matrix4f matrix4f = this.pose.last().pose();
      BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
      bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
      bufferbuilder.vertex(matrix4f, (float)pX1, (float)pY1, (float)pBlitOffset).color(pRed, pGreen, pBlue, pAlpha).uv(pMinU, pMinV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX1, (float)pY2, (float)pBlitOffset).color(pRed, pGreen, pBlue, pAlpha).uv(pMinU, pMaxV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX2, (float)pY2, (float)pBlitOffset).color(pRed, pGreen, pBlue, pAlpha).uv(pMaxU, pMaxV).endVertex();
      bufferbuilder.vertex(matrix4f, (float)pX2, (float)pY1, (float)pBlitOffset).color(pRed, pGreen, pBlue, pAlpha).uv(pMaxU, pMinV).endVertex();
      BufferUploader.drawWithShader(bufferbuilder.end());
      RenderSystem.disableBlend();
   }

   public void blitNineSliced(ResourceLocation pAtlasLocation, int pTargetX, int pTargetY, int pTargetWidth, int pTargetHeight, int pSliceSize, int pSourceWidth, int pSourceHeight, int pSourceX, int pSourceY) {
      this.blitNineSliced(pAtlasLocation, pTargetX, pTargetY, pTargetWidth, pTargetHeight, pSliceSize, pSliceSize, pSliceSize, pSliceSize, pSourceWidth, pSourceHeight, pSourceX, pSourceY);
   }

   public void blitNineSliced(ResourceLocation pAtlasLocation, int pTargetX, int pTargetY, int pTargetWidth, int pTargetHeight, int pSliceWidth, int pSliceHeight, int pSourceWidth, int pSourceHeight, int pSourceX, int pSourceY) {
      this.blitNineSliced(pAtlasLocation, pTargetX, pTargetY, pTargetWidth, pTargetHeight, pSliceWidth, pSliceHeight, pSliceWidth, pSliceHeight, pSourceWidth, pSourceHeight, pSourceX, pSourceY);
   }

   public void blitNineSliced(ResourceLocation pAtlasLocation, int pTargetX, int pTargetY, int pTargetWidth, int pTargetHeight, int pCornerWidth, int pCornerHeight, int pEdgeWidth, int pEdgeHeight, int pSourceWidth, int pSourceHeight, int pSourceX, int pSourceY) {
      pCornerWidth = Math.min(pCornerWidth, pTargetWidth / 2);
      pEdgeWidth = Math.min(pEdgeWidth, pTargetWidth / 2);
      pCornerHeight = Math.min(pCornerHeight, pTargetHeight / 2);
      pEdgeHeight = Math.min(pEdgeHeight, pTargetHeight / 2);
      if (pTargetWidth == pSourceWidth && pTargetHeight == pSourceHeight) {
         this.blit(pAtlasLocation, pTargetX, pTargetY, pSourceX, pSourceY, pTargetWidth, pTargetHeight);
      } else if (pTargetHeight == pSourceHeight) {
         this.blit(pAtlasLocation, pTargetX, pTargetY, pSourceX, pSourceY, pCornerWidth, pTargetHeight);
         this.blitRepeating(pAtlasLocation, pTargetX + pCornerWidth, pTargetY, pTargetWidth - pEdgeWidth - pCornerWidth, pTargetHeight, pSourceX + pCornerWidth, pSourceY, pSourceWidth - pEdgeWidth - pCornerWidth, pSourceHeight);
         this.blit(pAtlasLocation, pTargetX + pTargetWidth - pEdgeWidth, pTargetY, pSourceX + pSourceWidth - pEdgeWidth, pSourceY, pEdgeWidth, pTargetHeight);
      } else if (pTargetWidth == pSourceWidth) {
         this.blit(pAtlasLocation, pTargetX, pTargetY, pSourceX, pSourceY, pTargetWidth, pCornerHeight);
         this.blitRepeating(pAtlasLocation, pTargetX, pTargetY + pCornerHeight, pTargetWidth, pTargetHeight - pEdgeHeight - pCornerHeight, pSourceX, pSourceY + pCornerHeight, pSourceWidth, pSourceHeight - pEdgeHeight - pCornerHeight);
         this.blit(pAtlasLocation, pTargetX, pTargetY + pTargetHeight - pEdgeHeight, pSourceX, pSourceY + pSourceHeight - pEdgeHeight, pTargetWidth, pEdgeHeight);
      } else {
         this.blit(pAtlasLocation, pTargetX, pTargetY, pSourceX, pSourceY, pCornerWidth, pCornerHeight);
         this.blitRepeating(pAtlasLocation, pTargetX + pCornerWidth, pTargetY, pTargetWidth - pEdgeWidth - pCornerWidth, pCornerHeight, pSourceX + pCornerWidth, pSourceY, pSourceWidth - pEdgeWidth - pCornerWidth, pCornerHeight);
         this.blit(pAtlasLocation, pTargetX + pTargetWidth - pEdgeWidth, pTargetY, pSourceX + pSourceWidth - pEdgeWidth, pSourceY, pEdgeWidth, pCornerHeight);
         this.blit(pAtlasLocation, pTargetX, pTargetY + pTargetHeight - pEdgeHeight, pSourceX, pSourceY + pSourceHeight - pEdgeHeight, pCornerWidth, pEdgeHeight);
         this.blitRepeating(pAtlasLocation, pTargetX + pCornerWidth, pTargetY + pTargetHeight - pEdgeHeight, pTargetWidth - pEdgeWidth - pCornerWidth, pEdgeHeight, pSourceX + pCornerWidth, pSourceY + pSourceHeight - pEdgeHeight, pSourceWidth - pEdgeWidth - pCornerWidth, pEdgeHeight);
         this.blit(pAtlasLocation, pTargetX + pTargetWidth - pEdgeWidth, pTargetY + pTargetHeight - pEdgeHeight, pSourceX + pSourceWidth - pEdgeWidth, pSourceY + pSourceHeight - pEdgeHeight, pEdgeWidth, pEdgeHeight);
         this.blitRepeating(pAtlasLocation, pTargetX, pTargetY + pCornerHeight, pCornerWidth, pTargetHeight - pEdgeHeight - pCornerHeight, pSourceX, pSourceY + pCornerHeight, pCornerWidth, pSourceHeight - pEdgeHeight - pCornerHeight);
         this.blitRepeating(pAtlasLocation, pTargetX + pCornerWidth, pTargetY + pCornerHeight, pTargetWidth - pEdgeWidth - pCornerWidth, pTargetHeight - pEdgeHeight - pCornerHeight, pSourceX + pCornerWidth, pSourceY + pCornerHeight, pSourceWidth - pEdgeWidth - pCornerWidth, pSourceHeight - pEdgeHeight - pCornerHeight);
         this.blitRepeating(pAtlasLocation, pTargetX + pTargetWidth - pEdgeWidth, pTargetY + pCornerHeight, pCornerWidth, pTargetHeight - pEdgeHeight - pCornerHeight, pSourceX + pSourceWidth - pEdgeWidth, pSourceY + pCornerHeight, pEdgeWidth, pSourceHeight - pEdgeHeight - pCornerHeight);
      }
   }

   public void blitRepeating(ResourceLocation pAtlasLocation, int pTargetX, int pTargetY, int pTargetWidth, int pTargetHeight, int pSourceX, int pSourceY, int pSourceWidth, int pSourceHeight) {
      blitRepeating(pAtlasLocation, pTargetX, pTargetY, pTargetWidth, pTargetHeight, pSourceX, pSourceY, pSourceWidth, pSourceHeight, 256, 256);
   }

   public void blitRepeating(ResourceLocation pAtlasLocation, int pTargetX, int pTargetY, int pTargetWidth, int pTargetHeight, int pSourceX, int pSourceY, int pSourceWidth, int pSourceHeight, int textureWidth, int textureHeight) {
      int i = pTargetX;

      int j;
      for(IntIterator intiterator = slices(pTargetWidth, pSourceWidth); intiterator.hasNext(); i += j) {
         j = intiterator.nextInt();
         int k = (pSourceWidth - j) / 2;
         int l = pTargetY;

         int i1;
         for(IntIterator intiterator1 = slices(pTargetHeight, pSourceHeight); intiterator1.hasNext(); l += i1) {
            i1 = intiterator1.nextInt();
            int j1 = (pSourceHeight - i1) / 2;
            this.blit(pAtlasLocation, i, l, pSourceX + k, pSourceY + j1, j, i1, textureWidth, textureHeight);
         }
      }

   }

   private static IntIterator slices(int p_282197_, int p_282161_) {
      int i = Mth.positiveCeilDiv(p_282197_, p_282161_);
      return new Divisor(p_282197_, i);
   }

   public void renderItem(ItemStack pStack, int pX, int pY) {
      this.renderItem(this.minecraft.player, this.minecraft.level, pStack, pX, pY, 0);
   }

   public void renderItem(ItemStack pStack, int pX, int pY, int pSeed) {
      this.renderItem(this.minecraft.player, this.minecraft.level, pStack, pX, pY, pSeed);
   }

   public void renderItem(ItemStack pStack, int pX, int pY, int pSeed, int p_282314_) {
      this.renderItem(this.minecraft.player, this.minecraft.level, pStack, pX, pY, pSeed, p_282314_);
   }

   public void renderFakeItem(ItemStack pStack, int pX, int pY) {
      this.renderItem((LivingEntity)null, this.minecraft.level, pStack, pX, pY, 0);
   }

   public void renderItem(LivingEntity pEntity, ItemStack pStack, int pX, int pY, int pSeed) {
      this.renderItem(pEntity, pEntity.level(), pStack, pX, pY, pSeed);
   }

   private void renderItem(@Nullable LivingEntity pEntity, @Nullable Level pLevel, ItemStack pStack, int pX, int pY, int pSeed) {
      this.renderItem(pEntity, pLevel, pStack, pX, pY, pSeed, 0);
   }

   private void renderItem(@Nullable LivingEntity pEntity, @Nullable Level pLevel, ItemStack pStack, int pX, int pY, int pSeed, int p_281995_) {
      if (!pStack.isEmpty()) {
         BakedModel bakedmodel = this.minecraft.getItemRenderer().getModel(pStack, pLevel, pEntity, pSeed);
         this.pose.pushPose();
         this.pose.translate((float)(pX + 8), (float)(pY + 8), (float)(150 + (bakedmodel.isGui3d() ? p_281995_ : 0)));

         try {
            this.pose.mulPoseMatrix((new Matrix4f()).scaling(1.0F, -1.0F, 1.0F));
            this.pose.scale(16.0F, 16.0F, 16.0F);
            boolean flag = !bakedmodel.usesBlockLight();
            if (flag) {
               Lighting.setupForFlatItems();
            }

            this.minecraft.getItemRenderer().render(pStack, ItemDisplayContext.GUI, false, this.pose, this.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, bakedmodel);
            this.flush();
            if (flag) {
               Lighting.setupFor3DItems();
            }
         } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering item");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
            crashreportcategory.setDetail("Item Type", () -> {
               return String.valueOf((Object)pStack.getItem());
            });
            crashreportcategory.setDetail("Registry Name", () -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pStack.getItem())));
            crashreportcategory.setDetail("Item Damage", () -> {
               return String.valueOf(pStack.getDamageValue());
            });
            crashreportcategory.setDetail("Item NBT", () -> {
               return String.valueOf((Object)pStack.getTag());
            });
            crashreportcategory.setDetail("Item Foil", () -> {
               return String.valueOf(pStack.hasFoil());
            });
            throw new ReportedException(crashreport);
         }

         this.pose.popPose();
      }
   }

   public void renderItemDecorations(Font pFont, ItemStack pStack, int pX, int pY) {
      this.renderItemDecorations(pFont, pStack, pX, pY, (String)null);
   }

   public void renderItemDecorations(Font pFont, ItemStack pStack, int pX, int pY, @Nullable String pText) {
      if (!pStack.isEmpty()) {
         this.pose.pushPose();
         if (pStack.getCount() != 1 || pText != null) {
            String s = pText == null ? String.valueOf(pStack.getCount()) : pText;
            this.pose.translate(0.0F, 0.0F, 200.0F);
            this.drawString(pFont, s, pX + 19 - 2 - pFont.width(s), pY + 6 + 3, 16777215, true);
         }

         if (pStack.isBarVisible()) {
            int l = pStack.getBarWidth();
            int i = pStack.getBarColor();
            int j = pX + 2;
            int k = pY + 13;
            this.fill(RenderType.guiOverlay(), j, k, j + 13, k + 2, -16777216);
            this.fill(RenderType.guiOverlay(), j, k, j + l, k + 1, i | -16777216);
         }

         LocalPlayer localplayer = this.minecraft.player;
         float f = localplayer == null ? 0.0F : localplayer.getCooldowns().getCooldownPercent(pStack.getItem(), this.minecraft.getFrameTime());
         if (f > 0.0F) {
            int i1 = pY + Mth.floor(16.0F * (1.0F - f));
            int j1 = i1 + Mth.ceil(16.0F * f);
            this.fill(RenderType.guiOverlay(), pX, i1, pX + 16, j1, Integer.MAX_VALUE);
         }

         this.pose.popPose();
         net.minecraftforge.client.ItemDecoratorHandler.of(pStack).render(this, pFont, pStack, pX, pY);
      }
   }

   private ItemStack tooltipStack = ItemStack.EMPTY;

   public void renderTooltip(Font pFont, ItemStack pStack, int pMouseX, int pMouseY) {
      this.tooltipStack = pStack;
      this.renderTooltip(pFont, Screen.getTooltipFromItem(this.minecraft, pStack), pStack.getTooltipImage(), pMouseX, pMouseY);
      this.tooltipStack = ItemStack.EMPTY;
   }

   public void renderTooltip(Font font, List<Component> textComponents, Optional<TooltipComponent> tooltipComponent, ItemStack stack, int mouseX, int mouseY) {
      this.tooltipStack = stack;
      this.renderTooltip(font, textComponents, tooltipComponent, mouseX, mouseY);
      this.tooltipStack = ItemStack.EMPTY;
   }

   public void renderTooltip(Font pFont, List<Component> pTooltipLines, Optional<TooltipComponent> pVisualTooltipComponent, int pMouseX, int pMouseY) {
      List<ClientTooltipComponent> list = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponents(this.tooltipStack, pTooltipLines, pVisualTooltipComponent, pMouseX, guiWidth(), guiHeight(), pFont);
      this.renderTooltipInternal(pFont, list, pMouseX, pMouseY, DefaultTooltipPositioner.INSTANCE);
   }

   public void renderTooltip(Font pFont, Component pText, int pMouseX, int pMouseY) {
      this.renderTooltip(pFont, List.of(pText.getVisualOrderText()), pMouseX, pMouseY);
   }

   public void renderComponentTooltip(Font pFont, List<Component> pTooltipLines, int pMouseX, int pMouseY) {
      List<ClientTooltipComponent> components = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponents(this.tooltipStack, pTooltipLines, pMouseX, guiWidth(), guiHeight(), pFont);
      this.renderTooltipInternal(pFont, components, pMouseX, pMouseY, DefaultTooltipPositioner.INSTANCE);
   }

   public void renderComponentTooltip(Font font, List<? extends net.minecraft.network.chat.FormattedText> tooltips, int mouseX, int mouseY, ItemStack stack) {
      this.tooltipStack = stack;
      List<ClientTooltipComponent> components = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponents(stack, tooltips, mouseX, guiWidth(), guiHeight(), font);
      this.renderTooltipInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
      this.tooltipStack = ItemStack.EMPTY;
   }

   public void renderComponentTooltipFromElements(Font font, List<com.mojang.datafixers.util.Either<FormattedText, TooltipComponent>> elements, int mouseX, int mouseY, ItemStack stack) {
      this.tooltipStack = stack;
      List<ClientTooltipComponent> components = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponentsFromElements(stack, elements, mouseX, guiWidth(), guiHeight(), font);
      this.renderTooltipInternal(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
      this.tooltipStack = ItemStack.EMPTY;
   }

   public void renderTooltip(Font pFont, List<? extends FormattedCharSequence> pTooltipLines, int pMouseX, int pMouseY) {
      this.renderTooltipInternal(pFont, pTooltipLines.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), pMouseX, pMouseY, DefaultTooltipPositioner.INSTANCE);
   }

   public void renderTooltip(Font pFont, List<FormattedCharSequence> pTooltipLines, ClientTooltipPositioner pTooltipPositioner, int pMouseX, int pMouseY) {
      this.renderTooltipInternal(pFont, pTooltipLines.stream().map(ClientTooltipComponent::create).collect(Collectors.toList()), pMouseX, pMouseY, pTooltipPositioner);
   }

   private void renderTooltipInternal(Font pFont, List<ClientTooltipComponent> pComponents, int pMouseX, int pMouseY, ClientTooltipPositioner pTooltipPositioner) {
      if (!pComponents.isEmpty()) {
         net.minecraftforge.client.event.RenderTooltipEvent.Pre preEvent = net.minecraftforge.client.ForgeHooksClient.onRenderTooltipPre(this.tooltipStack, this, pMouseX, pMouseY, guiWidth(), guiHeight(), pComponents, pFont, pTooltipPositioner);
         if (preEvent.isCanceled()) return;
         int i = 0;
         int j = pComponents.size() == 1 ? -2 : 0;

         for(ClientTooltipComponent clienttooltipcomponent : pComponents) {
            int k = clienttooltipcomponent.getWidth(preEvent.getFont());
            if (k > i) {
               i = k;
            }

            j += clienttooltipcomponent.getHeight();
         }

         int i2 = i;
         int j2 = j;
         Vector2ic vector2ic = pTooltipPositioner.positionTooltip(this.guiWidth(), this.guiHeight(), preEvent.getX(), preEvent.getY(), i2, j2);
         int l = vector2ic.x();
         int i1 = vector2ic.y();
         this.pose.pushPose();
         int j1 = 400;
         this.drawManaged(() -> {
            net.minecraftforge.client.event.RenderTooltipEvent.Color colorEvent = net.minecraftforge.client.ForgeHooksClient.onRenderTooltipColor(this.tooltipStack, this, l, i1, preEvent.getFont(), pComponents);
            TooltipRenderUtil.renderTooltipBackground(this, l, i1, i2, j2, 400, colorEvent.getBackgroundStart(), colorEvent.getBackgroundEnd(), colorEvent.getBorderStart(), colorEvent.getBorderEnd());
         });
         this.pose.translate(0.0F, 0.0F, 400.0F);
         int k1 = i1;

         for(int l1 = 0; l1 < pComponents.size(); ++l1) {
            ClientTooltipComponent clienttooltipcomponent1 = pComponents.get(l1);
            clienttooltipcomponent1.renderText(preEvent.getFont(), l, k1, this.pose.last().pose(), this.bufferSource);
            k1 += clienttooltipcomponent1.getHeight() + (l1 == 0 ? 2 : 0);
         }

         k1 = i1;

         for(int k2 = 0; k2 < pComponents.size(); ++k2) {
            ClientTooltipComponent clienttooltipcomponent2 = pComponents.get(k2);
            clienttooltipcomponent2.renderImage(preEvent.getFont(), l, k1, this);
            k1 += clienttooltipcomponent2.getHeight() + (k2 == 0 ? 2 : 0);
         }

         this.pose.popPose();
      }
   }

   public void renderComponentHoverEffect(Font pFont, @Nullable Style pStyle, int pMouseX, int pMouseY) {
      if (pStyle != null && pStyle.getHoverEvent() != null) {
         HoverEvent hoverevent = pStyle.getHoverEvent();
         HoverEvent.ItemStackInfo hoverevent$itemstackinfo = hoverevent.getValue(HoverEvent.Action.SHOW_ITEM);
         if (hoverevent$itemstackinfo != null) {
            this.renderTooltip(pFont, hoverevent$itemstackinfo.getItemStack(), pMouseX, pMouseY);
         } else {
            HoverEvent.EntityTooltipInfo hoverevent$entitytooltipinfo = hoverevent.getValue(HoverEvent.Action.SHOW_ENTITY);
            if (hoverevent$entitytooltipinfo != null) {
               if (this.minecraft.options.advancedItemTooltips) {
                  this.renderComponentTooltip(pFont, hoverevent$entitytooltipinfo.getTooltipLines(), pMouseX, pMouseY);
               }
            } else {
               Component component = hoverevent.getValue(HoverEvent.Action.SHOW_TEXT);
               if (component != null) {
                  this.renderTooltip(pFont, pFont.split(component, Math.max(this.guiWidth() / 2, 200)), pMouseX, pMouseY);
               }
            }
         }

      }
   }

   @OnlyIn(Dist.CLIENT)
   static class ScissorStack {
      private final Deque<ScreenRectangle> stack = new ArrayDeque<>();

      public ScreenRectangle push(ScreenRectangle pSciccor) {
         ScreenRectangle screenrectangle = this.stack.peekLast();
         if (screenrectangle != null) {
            ScreenRectangle screenrectangle1 = Objects.requireNonNullElse(pSciccor.intersection(screenrectangle), ScreenRectangle.empty());
            this.stack.addLast(screenrectangle1);
            return screenrectangle1;
         } else {
            this.stack.addLast(pSciccor);
            return pSciccor;
         }
      }

      public @Nullable ScreenRectangle pop() {
         if (this.stack.isEmpty()) {
            throw new IllegalStateException("Scissor stack underflow");
         } else {
            this.stack.removeLast();
            return this.stack.peekLast();
         }
      }
   }
}
