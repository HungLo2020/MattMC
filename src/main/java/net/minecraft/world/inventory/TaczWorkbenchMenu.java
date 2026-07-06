package net.minecraft.world.inventory;

import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.TaczWorkbenchRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class TaczWorkbenchMenu extends AbstractContainerMenu {
	private static final int INVENTORY_START = 0;
	private static final int INVENTORY_END = 27;
	private static final int HOTBAR_START = 27;
	private static final int HOTBAR_END = 36;
	private final ContainerLevelAccess access;
	private final WorkbenchType workbenchType;
	private final Block block;
	private final List<TaczWorkbenchRecipe> recipes;
	private long lastSoundTime;

	public static TaczWorkbenchMenu gunSmithTable(int containerId, Inventory inventory) {
		return new TaczWorkbenchMenu(MenuType.TACZ_GUN_SMITH_TABLE, WorkbenchType.GUN_SMITH_TABLE, Blocks.GUN_SMITH_TABLE, containerId, inventory, ContainerLevelAccess.NULL);
	}

	public static TaczWorkbenchMenu ammoWorkbench(int containerId, Inventory inventory) {
		return new TaczWorkbenchMenu(MenuType.TACZ_AMMO_WORKBENCH, WorkbenchType.AMMO_WORKBENCH, Blocks.AMMO_WORKBENCH, containerId, inventory, ContainerLevelAccess.NULL);
	}

	public static TaczWorkbenchMenu attachmentWorkbench(int containerId, Inventory inventory) {
		return new TaczWorkbenchMenu(MenuType.TACZ_ATTACHMENT_WORKBENCH, WorkbenchType.ATTACHMENT_WORKBENCH, Blocks.ATTACHMENT_WORKBENCH, containerId, inventory, ContainerLevelAccess.NULL);
	}

	public static TaczWorkbenchMenu gunSmithTable(int containerId, Inventory inventory, ContainerLevelAccess access) {
		return new TaczWorkbenchMenu(MenuType.TACZ_GUN_SMITH_TABLE, WorkbenchType.GUN_SMITH_TABLE, Blocks.GUN_SMITH_TABLE, containerId, inventory, access);
	}

	public static TaczWorkbenchMenu ammoWorkbench(int containerId, Inventory inventory, ContainerLevelAccess access) {
		return new TaczWorkbenchMenu(MenuType.TACZ_AMMO_WORKBENCH, WorkbenchType.AMMO_WORKBENCH, Blocks.AMMO_WORKBENCH, containerId, inventory, access);
	}

	public static TaczWorkbenchMenu attachmentWorkbench(int containerId, Inventory inventory, ContainerLevelAccess access) {
		return new TaczWorkbenchMenu(MenuType.TACZ_ATTACHMENT_WORKBENCH, WorkbenchType.ATTACHMENT_WORKBENCH, Blocks.ATTACHMENT_WORKBENCH, containerId, inventory, access);
	}

	private TaczWorkbenchMenu(MenuType<?> menuType, WorkbenchType workbenchType, Block block, int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(menuType, containerId);
		this.workbenchType = workbenchType;
		this.block = block;
		this.access = access;
		this.recipes = TaczWorkbenchRecipe.allRecipes().stream().filter(workbenchType::accepts).toList();
		this.addStandardInventorySlots(inventory, 8, 150);
	}

	public WorkbenchType workbenchType() {
		return this.workbenchType;
	}

	public List<TaczWorkbenchRecipe> recipes() {
		return this.recipes;
	}

	public List<Tab> tabs() {
		return this.workbenchType.tabs;
	}

	@Override
	public boolean clickMenuButton(Player player, int index) {
		if (index < 0 || index >= this.recipes.size()) {
			return false;
		}

		TaczWorkbenchRecipe recipe = this.recipes.get(index);
		if (!recipe.canCraft(player.getInventory())) {
			return false;
		}

		recipe.craft(player);
		this.access.execute((level, blockPos) -> {
			long gameTime = level.getGameTime();
			if (this.lastSoundTime != gameTime) {
				level.playSound(null, blockPos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
				this.lastSoundTime = gameTime;
			}
		});
		this.broadcastChanges();
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, this.block);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (slotIndex >= INVENTORY_START && slotIndex < INVENTORY_END) {
				if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotIndex >= HOTBAR_START && slotIndex < HOTBAR_END && !this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			}

			slot.setChanged();
		}

		return copy;
	}

	public enum WorkbenchType {
		GUN_SMITH_TABLE(
			ResourceLocation.withDefaultNamespace("gun_smith_table"),
			List.of(
				new Tab("pistol", "minecraft.type.pistol.name"),
				new Tab("sniper", "minecraft.type.sniper.name"),
				new Tab("rifle", "minecraft.type.rifle.name"),
				new Tab("shotgun", "minecraft.type.shotgun.name"),
				new Tab("smg", "minecraft.type.smg.name"),
				new Tab("rpg", "minecraft.type.rpg.name"),
				new Tab("mg", "minecraft.type.mg.name"),
				new Tab("misc", "minecraft.type.misc.name")
			)
		),
		AMMO_WORKBENCH(
			ResourceLocation.withDefaultNamespace("ammo_workbench"),
			List.of(
				new Tab("ammo", "minecraft.type.ammo.name"),
				new Tab("pd_cartridges", "minecraft.type.pd_cartridges.name"),
				new Tab("ifp_rifle_cartridges", "minecraft.type.ifp_rifle_cartridges.name"),
				new Tab("lc_specialized", "minecraft.type.lc_specialized.name"),
				new Tab("explosives", "minecraft.type.explosives.name"),
				new Tab("shotgun_shells", "minecraft.type.shotgun_shells.name"),
				new Tab("alternative_proj", "minecraft.type.alternative_proj.name")
			)
		),
		ATTACHMENT_WORKBENCH(
			ResourceLocation.withDefaultNamespace("attachment_workbench"),
			List.of(
				new Tab("scope", "minecraft.type.scope.name"),
				new Tab("muzzle", "minecraft.type.muzzle.name"),
				new Tab("stock", "minecraft.type.stock.name"),
				new Tab("grip", "minecraft.type.grip.name"),
				new Tab("extended_mag", "minecraft.type.extended_mag.name"),
				new Tab("laser", "minecraft.type.laser.name")
			)
		);

		private final ResourceLocation id;
		private final List<Tab> tabs;
		private final Set<ResourceLocation> groups;

		WorkbenchType(ResourceLocation id, List<Tab> tabs) {
			this.id = id;
			this.tabs = tabs;
			this.groups = tabs.stream().map(Tab::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
		}

		public ResourceLocation id() {
			return this.id;
		}

		private boolean accepts(TaczWorkbenchRecipe recipe) {
			return this.groups.contains(recipe.group());
		}
	}

	public record Tab(ResourceLocation id, Component name) {
		private Tab(String id, String translationKey) {
			this(ResourceLocation.withDefaultNamespace(id), Component.translatable(translationKey));
		}
	}
}
