package com.mojang.realmsclient.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.realms.RealmsObjectSelectionList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RowButton {
   public final int width;
   public final int height;
   public final int xOffset;
   public final int yOffset;

   public RowButton(int pWidth, int pHeight, int pXOffset, int pYOffset) {
      this.width = pWidth;
      this.height = pHeight;
      this.xOffset = pXOffset;
      this.yOffset = pYOffset;
   }

   public void drawForRowAt(GuiGraphics pGuiGraphics, int pX, int pY, int pMouseX, int pMouseY) {
      int i = pX + this.xOffset;
      int j = pY + this.yOffset;
      boolean flag = pMouseX >= i && pMouseX <= i + this.width && pMouseY >= j && pMouseY <= j + this.height;
      this.draw(pGuiGraphics, i, j, flag);
   }

   protected abstract void draw(GuiGraphics pGuiGraphics, int pX, int pY, boolean pShowTooltip);

   public int getRight() {
      return this.xOffset + this.width;
   }

   public int getBottom() {
      return this.yOffset + this.height;
   }

   public abstract void onClick(int p_88017_);

   public static void drawButtonsInRow(GuiGraphics pGuiGraphics, List<RowButton> pButtons, RealmsObjectSelectionList<?> pPendingInvitations, int pX, int pY, int pMouseX, int pMouseY) {
      for(RowButton rowbutton : pButtons) {
         if (pPendingInvitations.getRowWidth() > rowbutton.getRight()) {
            rowbutton.drawForRowAt(pGuiGraphics, pX, pY, pMouseX, pMouseY);
         }
      }

   }

   public static void rowButtonMouseClicked(RealmsObjectSelectionList<?> p_88037_, ObjectSelectionList.Entry<?> p_88038_, List<RowButton> p_88039_, int p_88040_, double p_88041_, double p_88042_) {
      if (p_88040_ == 0) {
         int i = p_88037_.children().indexOf(p_88038_);
         if (i > -1) {
            p_88037_.selectItem(i);
            int j = p_88037_.getRowLeft();
            int k = p_88037_.getRowTop(i);
            int l = (int)(p_88041_ - (double)j);
            int i1 = (int)(p_88042_ - (double)k);

            for(RowButton rowbutton : p_88039_) {
               if (l >= rowbutton.xOffset && l <= rowbutton.getRight() && i1 >= rowbutton.yOffset && i1 <= rowbutton.getBottom()) {
                  rowbutton.onClick(i);
               }
            }
         }
      }

   }
}