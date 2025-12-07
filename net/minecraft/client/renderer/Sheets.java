package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Sheets {
	public static final ResourceLocation SHULKER_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/shulker_boxes.png");
	public static final ResourceLocation BED_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/beds.png");
	public static final ResourceLocation BANNER_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/banner_patterns.png");
	public static final ResourceLocation SHIELD_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/shield_patterns.png");
	public static final ResourceLocation SIGN_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/signs.png");
	public static final ResourceLocation CHEST_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/chest.png");
	public static final ResourceLocation ARMOR_TRIMS_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/armor_trims.png");
	public static final ResourceLocation DECORATED_POT_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/decorated_pot.png");
	public static final ResourceLocation GUI_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/gui.png");
	public static final ResourceLocation MAP_DECORATIONS_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/map_decorations.png");
	public static final ResourceLocation PAINTINGS_SHEET = ResourceLocation.withDefaultNamespace("textures/atlas/paintings.png");
	private static final RenderType SHULKER_BOX_SHEET_TYPE = RenderType.entityCutoutNoCull(SHULKER_SHEET);
	private static final RenderType BED_SHEET_TYPE = RenderType.entitySolid(BED_SHEET);
	private static final RenderType BANNER_SHEET_TYPE = RenderType.entityNoOutline(BANNER_SHEET);
	private static final RenderType SHIELD_SHEET_TYPE = RenderType.entityNoOutline(SHIELD_SHEET);
	private static final RenderType SIGN_SHEET_TYPE = RenderType.entityCutoutNoCull(SIGN_SHEET);
	private static final RenderType CHEST_SHEET_TYPE = RenderType.entityCutout(CHEST_SHEET);
	private static final RenderType ARMOR_TRIMS_SHEET_TYPE = RenderType.armorCutoutNoCull(ARMOR_TRIMS_SHEET);
	private static final RenderType ARMOR_TRIMS_DECAL_SHEET_TYPE = RenderType.createArmorDecalCutoutNoCull(ARMOR_TRIMS_SHEET);
	private static final RenderType SOLID_BLOCK_SHEET = RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
	private static final RenderType CUTOUT_BLOCK_SHEET = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
	private static final RenderType TRANSLUCENT_ITEM_CULL_BLOCK_SHEET = RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
	public static final MaterialMapper ITEMS_MAPPER = new MaterialMapper(TextureAtlas.LOCATION_BLOCKS, "item");
	public static final MaterialMapper BLOCKS_MAPPER = new MaterialMapper(TextureAtlas.LOCATION_BLOCKS, "block");
	public static final MaterialMapper BLOCK_ENTITIES_MAPPER = new MaterialMapper(TextureAtlas.LOCATION_BLOCKS, "entity");
	public static final MaterialMapper BANNER_MAPPER = new MaterialMapper(BANNER_SHEET, "entity/banner");
	public static final MaterialMapper SHIELD_MAPPER = new MaterialMapper(SHIELD_SHEET, "entity/shield");
	public static final MaterialMapper CHEST_MAPPER = new MaterialMapper(CHEST_SHEET, "entity/chest");
	public static final MaterialMapper DECORATED_POT_MAPPER = new MaterialMapper(DECORATED_POT_SHEET, "entity/decorated_pot");
	public static final MaterialMapper BED_MAPPER = new MaterialMapper(BED_SHEET, "entity/bed");
	public static final MaterialMapper SHULKER_MAPPER = new MaterialMapper(SHULKER_SHEET, "entity/shulker");
	public static final MaterialMapper SIGN_MAPPER = new MaterialMapper(SIGN_SHEET, "entity/signs");
	public static final MaterialMapper HANGING_SIGN_MAPPER = new MaterialMapper(SIGN_SHEET, "entity/signs/hanging");
	public static final Material DEFAULT_SHULKER_TEXTURE_LOCATION = SHULKER_MAPPER.defaultNamespaceApply("shulker");
	public static final List<Material> SHULKER_TEXTURE_LOCATION = (List<Material>)Arrays.stream(DyeColor.values())
		.sorted(Comparator.comparingInt(DyeColor::getId))
		.map(Sheets::createShulkerMaterial)
		.collect(ImmutableList.toImmutableList());
	public static final Map<WoodType, Material> SIGN_MATERIALS = (Map<WoodType, Material>)WoodType.values()
		.collect(Collectors.toMap(Function.identity(), Sheets::createSignMaterial));
	public static final Map<WoodType, Material> HANGING_SIGN_MATERIALS = (Map<WoodType, Material>)WoodType.values()
		.collect(Collectors.toMap(Function.identity(), Sheets::createHangingSignMaterial));
	public static final Material BANNER_BASE = BANNER_MAPPER.defaultNamespaceApply("base");
	public static final Material SHIELD_BASE = SHIELD_MAPPER.defaultNamespaceApply("base");
	private static final Map<ResourceLocation, Material> BANNER_MATERIALS = new HashMap();
	private static final Map<ResourceLocation, Material> SHIELD_MATERIALS = new HashMap();
	public static final Map<ResourceKey<DecoratedPotPattern>, Material> DECORATED_POT_MATERIALS = (Map<ResourceKey<DecoratedPotPattern>, Material>)BuiltInRegistries.DECORATED_POT_PATTERN
		.listElements()
		.collect(Collectors.toMap(Reference::key, reference -> DECORATED_POT_MAPPER.apply(((DecoratedPotPattern)reference.value()).assetId())));
	public static final Material DECORATED_POT_BASE = DECORATED_POT_MAPPER.defaultNamespaceApply("decorated_pot_base");
	public static final Material DECORATED_POT_SIDE = DECORATED_POT_MAPPER.defaultNamespaceApply("decorated_pot_side");
	private static final Material[] BED_TEXTURES = (Material[])Arrays.stream(DyeColor.values())
		.sorted(Comparator.comparingInt(DyeColor::getId))
		.map(Sheets::createBedMaterial)
		.toArray(Material[]::new);
	public static final Material CHEST_TRAP_LOCATION = CHEST_MAPPER.defaultNamespaceApply("trapped");
	public static final Material CHEST_TRAP_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("trapped_left");
	public static final Material CHEST_TRAP_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("trapped_right");
	public static final Material CHEST_XMAS_LOCATION = CHEST_MAPPER.defaultNamespaceApply("christmas");
	public static final Material CHEST_XMAS_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("christmas_left");
	public static final Material CHEST_XMAS_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("christmas_right");
	public static final Material CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("normal");
	public static final Material CHEST_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("normal_left");
	public static final Material CHEST_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("normal_right");
	public static final Material ENDER_CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("ender");
	public static final Material COPPER_CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("copper");
	public static final Material COPPER_CHEST_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("copper_left");
	public static final Material COPPER_CHEST_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("copper_right");
	public static final Material EXPOSED_COPPER_CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("copper_exposed");
	public static final Material EXPOSED_COPPER_CHEST_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("copper_exposed_left");
	public static final Material EXPOSED_COPPER_CHEST_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("copper_exposed_right");
	public static final Material WEATHERED_COPPER_CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("copper_weathered");
	public static final Material WEATHERED_COPPER_CHEST_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("copper_weathered_left");
	public static final Material WEATHERED_COPPER_CHEST_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("copper_weathered_right");
	public static final Material OXIDIZED_COPPER_CHEST_LOCATION = CHEST_MAPPER.defaultNamespaceApply("copper_oxidized");
	public static final Material OXIDIZED_COPPER_CHEST_LOCATION_LEFT = CHEST_MAPPER.defaultNamespaceApply("copper_oxidized_left");
	public static final Material OXIDIZED_COPPER_CHEST_LOCATION_RIGHT = CHEST_MAPPER.defaultNamespaceApply("copper_oxidized_right");

	public static RenderType bannerSheet() {
		return BANNER_SHEET_TYPE;
	}

	public static RenderType shieldSheet() {
		return SHIELD_SHEET_TYPE;
	}

	public static RenderType bedSheet() {
		return BED_SHEET_TYPE;
	}

	public static RenderType shulkerBoxSheet() {
		return SHULKER_BOX_SHEET_TYPE;
	}

	public static RenderType signSheet() {
		return SIGN_SHEET_TYPE;
	}

	public static RenderType hangingSignSheet() {
		return SIGN_SHEET_TYPE;
	}

	public static RenderType chestSheet() {
		return CHEST_SHEET_TYPE;
	}

	public static RenderType armorTrimsSheet(boolean bl) {
		return bl ? ARMOR_TRIMS_DECAL_SHEET_TYPE : ARMOR_TRIMS_SHEET_TYPE;
	}

	public static RenderType solidBlockSheet() {
		return SOLID_BLOCK_SHEET;
	}

	public static RenderType cutoutBlockSheet() {
		return CUTOUT_BLOCK_SHEET;
	}

	public static RenderType translucentItemSheet() {
		return TRANSLUCENT_ITEM_CULL_BLOCK_SHEET;
	}

	public static Material getBedMaterial(DyeColor dyeColor) {
		return BED_TEXTURES[dyeColor.getId()];
	}

	public static ResourceLocation colorToResourceMaterial(DyeColor dyeColor) {
		return ResourceLocation.withDefaultNamespace(dyeColor.getName());
	}

	public static Material createBedMaterial(DyeColor dyeColor) {
		return BED_MAPPER.apply(colorToResourceMaterial(dyeColor));
	}

	public static Material getShulkerBoxMaterial(DyeColor dyeColor) {
		return (Material)SHULKER_TEXTURE_LOCATION.get(dyeColor.getId());
	}

	public static ResourceLocation colorToShulkerMaterial(DyeColor dyeColor) {
		return ResourceLocation.withDefaultNamespace("shulker_" + dyeColor.getName());
	}

	public static Material createShulkerMaterial(DyeColor dyeColor) {
		return SHULKER_MAPPER.apply(colorToShulkerMaterial(dyeColor));
	}

	private static Material createSignMaterial(WoodType woodType) {
		return SIGN_MAPPER.defaultNamespaceApply(woodType.name());
	}

	private static Material createHangingSignMaterial(WoodType woodType) {
		return HANGING_SIGN_MAPPER.defaultNamespaceApply(woodType.name());
	}

	public static Material getSignMaterial(WoodType woodType) {
		return (Material)SIGN_MATERIALS.get(woodType);
	}

	public static Material getHangingSignMaterial(WoodType woodType) {
		return (Material)HANGING_SIGN_MATERIALS.get(woodType);
	}

	public static Material getBannerMaterial(Holder<BannerPattern> holder) {
		return (Material)BANNER_MATERIALS.computeIfAbsent(((BannerPattern)holder.value()).assetId(), BANNER_MAPPER::apply);
	}

	public static Material getShieldMaterial(Holder<BannerPattern> holder) {
		return (Material)SHIELD_MATERIALS.computeIfAbsent(((BannerPattern)holder.value()).assetId(), SHIELD_MAPPER::apply);
	}

	@Nullable
	public static Material getDecoratedPotMaterial(@Nullable ResourceKey<DecoratedPotPattern> resourceKey) {
		return resourceKey == null ? null : (Material)DECORATED_POT_MATERIALS.get(resourceKey);
	}

	public static Material chooseMaterial(ChestRenderState.ChestMaterialType chestMaterialType, ChestType chestType) {
		return switch (chestMaterialType) {
			case ENDER_CHEST -> ENDER_CHEST_LOCATION;
			case CHRISTMAS -> chooseMaterial(chestType, CHEST_XMAS_LOCATION, CHEST_XMAS_LOCATION_LEFT, CHEST_XMAS_LOCATION_RIGHT);
			case TRAPPED -> chooseMaterial(chestType, CHEST_TRAP_LOCATION, CHEST_TRAP_LOCATION_LEFT, CHEST_TRAP_LOCATION_RIGHT);
			case COPPER_UNAFFECTED -> chooseMaterial(chestType, COPPER_CHEST_LOCATION, COPPER_CHEST_LOCATION_LEFT, COPPER_CHEST_LOCATION_RIGHT);
			case COPPER_EXPOSED -> chooseMaterial(chestType, EXPOSED_COPPER_CHEST_LOCATION, EXPOSED_COPPER_CHEST_LOCATION_LEFT, EXPOSED_COPPER_CHEST_LOCATION_RIGHT);
			case COPPER_WEATHERED -> chooseMaterial(
				chestType, WEATHERED_COPPER_CHEST_LOCATION, WEATHERED_COPPER_CHEST_LOCATION_LEFT, WEATHERED_COPPER_CHEST_LOCATION_RIGHT
			);
			case COPPER_OXIDIZED -> chooseMaterial(chestType, OXIDIZED_COPPER_CHEST_LOCATION, OXIDIZED_COPPER_CHEST_LOCATION_LEFT, OXIDIZED_COPPER_CHEST_LOCATION_RIGHT);
			case REGULAR -> chooseMaterial(chestType, CHEST_LOCATION, CHEST_LOCATION_LEFT, CHEST_LOCATION_RIGHT);
		};
	}

	private static Material chooseMaterial(ChestType chestType, Material material, Material material2, Material material3) {
		switch (chestType) {
			case LEFT:
				return material2;
			case RIGHT:
				return material3;
			case SINGLE:
			default:
				return material;
		}
	}
}
