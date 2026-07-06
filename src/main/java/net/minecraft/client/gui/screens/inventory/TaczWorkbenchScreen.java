package net.minecraft.client.gui.screens.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.TaczWorkbenchMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.TaczWorkbenchRecipe;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TaczWorkbenchScreen extends AbstractContainerScreen<TaczWorkbenchMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/gun_smith_table.png");
	private static final ResourceLocation SIDE = ResourceLocation.withDefaultNamespace("textures/gui/gun_smith_table_side.png");
	private static final int TYPE_PAGE_SIZE = 7;
	private static final int RESULT_PAGE_SIZE = 6;
	private final Map<TaczWorkbenchMenu.Tab, List<Integer>> recipesByTab = new LinkedHashMap<>();
	private int typePage;
	private int indexPage;
	private @Nullable TaczWorkbenchMenu.Tab selectedTab;
	private int selectedRecipeIndex = -1;
	private final List<ResultButton> resultButtons = new ArrayList<>();

	public TaczWorkbenchScreen(TaczWorkbenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 344;
		this.imageHeight = 186;
		this.inventoryLabelY = 150;
		this.classifyRecipes();
	}

	@Override
	protected void init() {
		super.init();
		this.classifyRecipes();
		this.clearWidgets();
		this.resultButtons.clear();
		this.addTypePageButtons();
		this.addTypeButtons();
		this.addIndexPageButtons();
		this.addIndexButtons();
		this.addCraftButton();
	}

	private void classifyRecipes() {
		this.recipesByTab.clear();
		List<TaczWorkbenchRecipe> recipes = this.menu.recipes();
		for (TaczWorkbenchMenu.Tab tab : this.menu.tabs()) {
			List<Integer> indexes = new ArrayList<>();
			for (int recipeIndex = 0; recipeIndex < recipes.size(); recipeIndex++) {
				if (recipes.get(recipeIndex).group().equals(tab.id())) {
					indexes.add(recipeIndex);
				}
			}
			if (!indexes.isEmpty()) {
				this.recipesByTab.put(tab, indexes);
			}
		}

		if (this.selectedTab == null || !this.recipesByTab.containsKey(this.selectedTab)) {
			this.selectedTab = this.recipesByTab.keySet().stream().findFirst().orElse(null);
			this.indexPage = 0;
		}

		List<Integer> selectedRecipes = this.selectedRecipes();
		if (selectedRecipes.isEmpty()) {
			this.selectedRecipeIndex = -1;
		} else if (!selectedRecipes.contains(this.selectedRecipeIndex)) {
			this.selectedRecipeIndex = selectedRecipes.get(0);
		}
	}

	private List<Integer> selectedRecipes() {
		return this.selectedTab == null ? List.of() : this.recipesByTab.getOrDefault(this.selectedTab, List.of());
	}

	private @Nullable TaczWorkbenchRecipe selectedRecipe() {
		return this.selectedRecipeIndex >= 0 && this.selectedRecipeIndex < this.menu.recipes().size() ? this.menu.recipes().get(this.selectedRecipeIndex) : null;
	}

	private void addCraftButton() {
		this.addRenderableWidget(new TextureButton(this.leftPos + 289, this.topPos + 162, 48, 18, 138, 164, 18, button -> {
			TaczWorkbenchRecipe recipe = this.selectedRecipe();
			if (recipe != null && recipe.canCraft(this.minecraft.player.getInventory())) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, this.selectedRecipeIndex);
			}
		}));
	}

	private void addIndexButtons() {
		List<Integer> recipeIndexes = this.selectedRecipes();
		for (int i = 0; i < RESULT_PAGE_SIZE; i++) {
			int visibleIndex = i + this.indexPage * RESULT_PAGE_SIZE;
			if (visibleIndex >= recipeIndexes.size()) {
				break;
			}

			int recipeIndex = recipeIndexes.get(visibleIndex);
			TaczWorkbenchRecipe recipe = this.menu.recipes().get(recipeIndex);
			ResultButton button = this.addRenderableWidget(new ResultButton(this.leftPos + 144, this.topPos + 66 + 17 * i, recipe.result(), b -> {
				this.selectedRecipeIndex = recipeIndex;
				this.updateIngredientCount();
			}));
			button.setSelected(recipeIndex == this.selectedRecipeIndex);
			this.resultButtons.add(button);
		}
	}

	private void addTypeButtons() {
		List<TaczWorkbenchMenu.Tab> tabs = List.copyOf(this.recipesByTab.keySet());
		for (int i = 0; i < TYPE_PAGE_SIZE; i++) {
			int typeIndex = this.typePage * TYPE_PAGE_SIZE + i;
			if (typeIndex >= tabs.size()) {
				return;
			}

			TaczWorkbenchMenu.Tab tab = tabs.get(typeIndex);
			ItemStack icon = this.iconFor(tab);
			TypeButton typeButton = new TypeButton(this.leftPos + 157 + 24 * i, this.topPos + 2, icon, b -> {
				this.selectedTab = tab;
				this.indexPage = 0;
				List<Integer> selected = this.selectedRecipes();
				this.selectedRecipeIndex = selected.isEmpty() ? -1 : selected.get(0);
				this.updateIngredientCount();
			});
			typeButton.setTooltip(Tooltip.create(tab.name(), tab.name()));
			typeButton.setSelected(tab.equals(this.selectedTab));
			this.addRenderableWidget(typeButton);
		}
	}

	private ItemStack iconFor(TaczWorkbenchMenu.Tab tab) {
		List<Integer> recipeIndexes = this.recipesByTab.getOrDefault(tab, List.of());
		return recipeIndexes.isEmpty() ? ItemStack.EMPTY : this.menu.recipes().get(recipeIndexes.get(0)).result();
	}

	private void addIndexPageButtons() {
		this.addRenderableWidget(new TextureButton(this.leftPos + 143, this.topPos + 56, 96, 6, 40, 166, 6, b -> {
			if (this.indexPage > 0) {
				this.indexPage--;
				this.updateIngredientCount();
			}
		}));
		this.addRenderableWidget(new TextureButton(this.leftPos + 143, this.topPos + 171, 96, 6, 40, 186, 6, b -> {
			int maxPage = Math.max(0, (this.selectedRecipes().size() - 1) / RESULT_PAGE_SIZE);
			if (this.indexPage < maxPage) {
				this.indexPage++;
				this.updateIngredientCount();
			}
		}));
	}

	private void addTypePageButtons() {
		this.addRenderableWidget(new TextureButton(this.leftPos + 136, this.topPos + 4, 18, 20, 0, 162, 20, b -> {
			if (this.typePage > 0) {
				this.typePage--;
				this.updateIngredientCount();
			}
		}));
		this.addRenderableWidget(new TextureButton(this.leftPos + 327, this.topPos + 4, 18, 20, 20, 162, 20, b -> {
			int maxPage = Math.max(0, (this.recipesByTab.size() - 1) / TYPE_PAGE_SIZE);
			if (this.typePage < maxPage) {
				this.typePage++;
				this.updateIngredientCount();
			}
		}));
	}

	private void updateIngredientCount() {
		this.rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX > this.leftPos + 143 && mouseX < this.leftPos + 237 && mouseY > this.topPos + 66 && mouseY < this.topPos + 151) {
			int maxPage = Math.max(0, (this.selectedRecipes().size() - 1) / RESULT_PAGE_SIZE);
			this.indexPage = Mth.clamp(this.indexPage - (int)Math.signum(scrollY), 0, maxPage);
			this.updateIngredientCount();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		drawCenteredString(guiGraphics, this.font, Component.translatable("gui.tacz.gun_smith_table.preview"), this.leftPos + 108, this.topPos + 5, 0x555555);
		if (this.selectedTab != null) {
			guiGraphics.drawString(this.font, this.selectedTab.name(), this.leftPos + 150, this.topPos + 32, 0x555555, false);
		}
		guiGraphics.drawString(this.font, Component.translatable("gui.tacz.gun_smith_table.ingredient"), this.leftPos + 254, this.topPos + 50, 0x555555, false);
		drawCenteredString(guiGraphics, this.font, Component.translatable("gui.tacz.gun_smith_table.craft"), this.leftPos + 312, this.topPos + 167, 0xFFFFFF);

		TaczWorkbenchRecipe recipe = this.selectedRecipe();
		if (recipe != null) {
			this.renderLeftModel(guiGraphics, recipe.result());
			guiGraphics.drawString(this.font, Component.translatable("gui.tacz.gun_smith_table.count", recipe.result().getCount()), this.leftPos + 254, this.topPos + 140, 0x555555, false);
			this.renderIngredients(guiGraphics, recipe);
		}

		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderTooltip(guiGraphics, mouseX, mouseY);
		for (ResultButton resultButton : this.resultButtons) {
			resultButton.renderTooltips(stack -> guiGraphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY));
		}
	}

	private static void drawCenteredString(GuiGraphics guiGraphics, Font font, Component component, int x, int y, int color) {
		FormattedCharSequence text = component.getVisualOrderText();
		guiGraphics.drawString(font, text, x - font.width(text) / 2, y, color, false);
	}

	private void renderIngredients(GuiGraphics guiGraphics, TaczWorkbenchRecipe recipe) {
		for (int row = 0; row < 6; row++) {
			for (int column = 0; column < 2; column++) {
				int index = row * 2 + column;
				if (index >= recipe.ingredients().size()) {
					return;
				}

				TaczWorkbenchRecipe.Ingredient ingredient = recipe.ingredients().get(index);
				int x = this.leftPos + 254 + 45 * column;
				int y = this.topPos + 62 + 17 * row;
				guiGraphics.renderFakeItem(ingredient.displayStack(), x, y);

				int required = ingredient.count();
				int available = this.countIngredient(ingredient);
				boolean creative = this.minecraft.player != null && this.minecraft.player.isCreative();
				String count = creative ? required + "/∞" : required + "/" + available;
				int color = creative || available >= required ? 0xFFFFFF : 0xFF0000;
				guiGraphics.pose().pushMatrix();
				guiGraphics.pose().translate((x + 17) * 2.0F, (y + 10) * 2.0F);
				guiGraphics.pose().scale(0.5F, 0.5F);
				guiGraphics.drawString(this.font, count, 0, 0, color, false);
				guiGraphics.pose().popMatrix();
			}
		}
	}

	private int countIngredient(TaczWorkbenchRecipe.Ingredient ingredient) {
		int count = 0;
		if (this.minecraft.player == null) {
			return 0;
		}

		Inventory inventory = this.minecraft.player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ingredient.matches(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private void renderLeftModel(GuiGraphics guiGraphics, ItemStack stack) {
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(this.leftPos + 60.0F, this.topPos + 40.0F);
		guiGraphics.pose().scale(3.0F, 3.0F);
		guiGraphics.renderItem(stack, -8, -8);
		guiGraphics.pose().popMatrix();
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SIDE, this.leftPos, this.topPos, 0.0F, 0.0F, 134, 187, 256, 256);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + 136, this.topPos + 27, 0.0F, 0.0F, 208, 160, 256, 256);
	}

	private static final class TextureButton extends Button {
		private final int u;
		private final int v;
		private final int hoverOffset;

		TextureButton(int x, int y, int width, int height, int u, int v, int hoverOffset, OnPress onPress) {
			super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
			this.u = u;
			this.v = v;
			this.hoverOffset = hoverOffset;
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			int textureV = this.v + (this.isHoveredOrFocused() ? this.hoverOffset : 0);
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), this.u, textureV, this.width, this.height, 256, 256);
		}
	}

	private static final class TypeButton extends Button {
		private final ItemStack stack;
		private boolean selected;

		TypeButton(int x, int y, ItemStack stack, OnPress onPress) {
			super(x, y, 24, 25, Component.empty(), onPress, DEFAULT_NARRATION);
			this.stack = stack;
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			int u = this.selected ? 0 : 26;
			int v = this.isHoveredOrFocused() ? 229 : 204;
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), u, v, this.width, this.height, 256, 256);
			guiGraphics.renderItem(this.stack, this.getX() + 4, this.getY() + 5);
		}

		void setSelected(boolean selected) {
			this.selected = selected;
		}
	}

	private static final class ResultButton extends Button {
		private final ItemStack stack;
		private boolean selected;

		ResultButton(int x, int y, ItemStack stack, OnPress onPress) {
			super(x, y, 94, 16, Component.empty(), onPress, DEFAULT_NARRATION);
			this.stack = stack;
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			if (this.selected) {
				if (this.isHoveredOrFocused()) {
					guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() - 1, this.getY() - 1, 52, 229, this.width + 2, this.height + 2, 256, 256);
				} else {
					guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 53, 230, this.width, this.height, 256, 256);
				}
			} else if (this.isHoveredOrFocused()) {
				guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX() - 1, this.getY() - 1, 52, 211, this.width + 2, this.height + 2, 256, 256);
			} else {
				guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 53, 212, this.width, this.height, 256, 256);
			}

			guiGraphics.renderItem(this.stack, this.getX() + 1, this.getY());
			renderScrollingString(guiGraphics, Minecraft.getInstance().font, this.stack.getHoverName(), this.getX() + 20, this.getY() + 4, this.getX() + 92, this.getY() + 13, 0xFFFFFF);
		}

		void setSelected(boolean selected) {
			this.selected = selected;
		}

		void renderTooltips(java.util.function.Consumer<ItemStack> consumer) {
			if (this.isHoveredOrFocused() && !this.stack.isEmpty()) {
				consumer.accept(this.stack);
			}
		}
	}
}
