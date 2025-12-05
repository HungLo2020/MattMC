package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class InventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {
	private float xMouse;
	private float yMouse;
	private boolean buttonClicked;
	private final EffectsInInventory effects;

	public InventoryScreen(Player player) {
		super(player.inventoryMenu, new CraftingRecipeBookComponent(player.inventoryMenu), player.getInventory(), Component.translatable("container.crafting"));
		this.titleLabelX = 97;
		this.effects = new EffectsInInventory(this);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		if (this.minecraft.player.hasInfiniteMaterials()) {
			this.minecraft
				.setScreen(
					new CreativeModeInventoryScreen(this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get())
				);
		}
	}

	@Override
	protected void init() {
		if (this.minecraft.player.hasInfiniteMaterials()) {
			this.minecraft
				.setScreen(
					new CreativeModeInventoryScreen(this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), this.minecraft.options.operatorItemsTab().get())
				);
		} else {
			super.init();
		}
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(this.leftPos + 104, this.height / 2 - 22);
	}

	@Override
	protected void onRecipeBookButtonClick() {
		this.buttonClicked = true;
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		this.effects.renderEffects(guiGraphics, i, j);
		super.render(guiGraphics, i, j, f);
		this.effects.renderTooltip(guiGraphics, i, j);
		this.xMouse = i;
		this.yMouse = j;
	}

	@Override
	public boolean showsActiveEffects() {
		return this.effects.canSeeEffects();
	}

	@Override
	protected boolean isBiggerResultSlot() {
		return false;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		renderEntityInInventoryFollowsMouse(guiGraphics, k + 26, l + 8, k + 75, l + 78, 30, 0.0625F, this.xMouse, this.yMouse, this.minecraft.player);
	}

	public static void renderEntityInInventoryFollowsMouse(
		GuiGraphics guiGraphics, int i, int j, int k, int l, int m, float f, float g, float h, LivingEntity livingEntity
	) {
		float n = (i + k) / 2.0F;
		float o = (j + l) / 2.0F;
		guiGraphics.enableScissor(i, j, k, l);
		float p = (float)Math.atan((n - g) / 40.0F);
		float q = (float)Math.atan((o - h) / 40.0F);
		Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf quaternionf2 = new Quaternionf().rotateX(q * 20.0F * (float) (Math.PI / 180.0));
		quaternionf.mul(quaternionf2);
		float r = livingEntity.yBodyRot;
		float s = livingEntity.getYRot();
		float t = livingEntity.getXRot();
		float u = livingEntity.yHeadRotO;
		float v = livingEntity.yHeadRot;
		livingEntity.yBodyRot = 180.0F + p * 20.0F;
		livingEntity.setYRot(180.0F + p * 40.0F);
		livingEntity.setXRot(-q * 20.0F);
		livingEntity.yHeadRot = livingEntity.getYRot();
		livingEntity.yHeadRotO = livingEntity.getYRot();
		float w = livingEntity.getScale();
		Vector3f vector3f = new Vector3f(0.0F, livingEntity.getBbHeight() / 2.0F + f * w, 0.0F);
		float x = m / w;
		renderEntityInInventory(guiGraphics, i, j, k, l, x, vector3f, quaternionf, quaternionf2, livingEntity);
		livingEntity.yBodyRot = r;
		livingEntity.setYRot(s);
		livingEntity.setXRot(t);
		livingEntity.yHeadRotO = u;
		livingEntity.yHeadRot = v;
		guiGraphics.disableScissor();
	}

	public static void renderEntityInInventory(
		GuiGraphics guiGraphics,
		int i,
		int j,
		int k,
		int l,
		float f,
		Vector3f vector3f,
		Quaternionf quaternionf,
		@Nullable Quaternionf quaternionf2,
		LivingEntity livingEntity
	) {
		EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> entityRenderer = entityRenderDispatcher.getRenderer(livingEntity);
		EntityRenderState entityRenderState = entityRenderer.createRenderState(livingEntity, 1.0F);
		entityRenderState.lightCoords = 15728880;
		entityRenderState.hitboxesRenderState = null;
		entityRenderState.shadowPieces.clear();
		entityRenderState.outlineColor = 0;
		guiGraphics.submitEntityRenderState(entityRenderState, f, vector3f, quaternionf, quaternionf2, i, j, k, l);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		if (this.buttonClicked) {
			this.buttonClicked = false;
			return true;
		} else {
			return super.mouseReleased(mouseButtonEvent);
		}
	}
}
