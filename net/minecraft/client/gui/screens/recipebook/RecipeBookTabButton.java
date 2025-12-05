package net.minecraft.client.gui.screens.recipebook;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

@Environment(EnvType.CLIENT)
public class RecipeBookTabButton extends StateSwitchingButton {
	private static final WidgetSprites SPRITES = new WidgetSprites(
		ResourceLocation.withDefaultNamespace("recipe_book/tab"), ResourceLocation.withDefaultNamespace("recipe_book/tab_selected")
	);
	private final RecipeBookComponent.TabInfo tabInfo;
	private static final float ANIMATION_TIME = 15.0F;
	private float animationTime;

	public RecipeBookTabButton(RecipeBookComponent.TabInfo tabInfo) {
		super(0, 0, 35, 27, false);
		this.tabInfo = tabInfo;
		this.initTextureValues(SPRITES);
	}

	public void startAnimation(ClientRecipeBook clientRecipeBook, boolean bl) {
		RecipeCollection.CraftableStatus craftableStatus = bl ? RecipeCollection.CraftableStatus.CRAFTABLE : RecipeCollection.CraftableStatus.ANY;

		for (RecipeCollection recipeCollection : clientRecipeBook.getCollection(this.tabInfo.category())) {
			for (RecipeDisplayEntry recipeDisplayEntry : recipeCollection.getSelectedRecipes(craftableStatus)) {
				if (clientRecipeBook.willHighlight(recipeDisplayEntry.id())) {
					this.animationTime = 15.0F;
					return;
				}
			}
		}
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.sprites != null) {
			if (this.animationTime > 0.0F) {
				float g = 1.0F + 0.1F * (float)Math.sin(this.animationTime / 15.0F * (float) Math.PI);
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate(this.getX() + 8, this.getY() + 12);
				guiGraphics.pose().scale(1.0F, g);
				guiGraphics.pose().translate(-(this.getX() + 8), -(this.getY() + 12));
			}

			ResourceLocation resourceLocation = this.sprites.get(true, this.isStateTriggered);
			int k = this.getX();
			if (this.isStateTriggered) {
				k -= 2;
			}

			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocation, k, this.getY(), this.width, this.height);
			this.renderIcon(guiGraphics);
			if (this.animationTime > 0.0F) {
				guiGraphics.pose().popMatrix();
				this.animationTime -= f;
			}
		}
	}

	private void renderIcon(GuiGraphics guiGraphics) {
		int i = this.isStateTriggered ? -2 : 0;
		if (this.tabInfo.secondaryIcon().isPresent()) {
			guiGraphics.renderFakeItem(this.tabInfo.primaryIcon(), this.getX() + 3 + i, this.getY() + 5);
			guiGraphics.renderFakeItem((ItemStack)this.tabInfo.secondaryIcon().get(), this.getX() + 14 + i, this.getY() + 5);
		} else {
			guiGraphics.renderFakeItem(this.tabInfo.primaryIcon(), this.getX() + 9 + i, this.getY() + 5);
		}
	}

	public ExtendedRecipeBookCategory getCategory() {
		return this.tabInfo.category();
	}

	public boolean updateVisibility(ClientRecipeBook clientRecipeBook) {
		List<RecipeCollection> list = clientRecipeBook.getCollection(this.tabInfo.category());
		this.visible = false;

		for (RecipeCollection recipeCollection : list) {
			if (recipeCollection.hasAnySelected()) {
				this.visible = true;
				break;
			}
		}

		return this.visible;
	}
}
