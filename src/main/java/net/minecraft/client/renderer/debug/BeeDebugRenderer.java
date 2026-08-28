package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import net.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.util.debug.DebugBeeInfo;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugGoalInfo.DebugGoal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BeeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final boolean SHOW_GOAL_FOR_ALL_BEES = true;
	private static final boolean SHOW_NAME_FOR_ALL_BEES = true;
	private static final boolean SHOW_HIVE_FOR_ALL_BEES = true;
	private static final boolean SHOW_FLOWER_POS_FOR_ALL_BEES = true;
	private static final boolean SHOW_TRAVEL_TICKS_FOR_ALL_BEES = true;
	private static final boolean SHOW_GOAL_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_NAME_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_HIVE_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_FLOWER_POS_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_TRAVEL_TICKS_FOR_SELECTED_BEE = true;
	private static final boolean SHOW_HIVE_MEMBERS = true;
	private static final boolean SHOW_BLACKLISTS = true;
	private static final int MAX_RENDER_DIST_FOR_HIVE_OVERLAY = 30;
	private static final int MAX_RENDER_DIST_FOR_BEE_OVERLAY = 30;
	private static final int MAX_TARGETING_DIST = 8;
	private static final float TEXT_SCALE = 0.02F;
	private static final int ORANGE = -23296;
	private static final int GRAY = -3355444;
	private static final int PINK = -98404;
	private final Minecraft minecraft;
	@Nullable
	private UUID lastLookedAtUuid;

	public BeeDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		this.doRender(poseStack, multiBufferSource, debugValueAccess);
		if (!this.minecraft.player.isSpectator()) {
			this.updateLastLookedAtUuid();
		}
	}

	private void doRender(PoseStack poseStack, MultiBufferSource multiBufferSource, DebugValueAccess debugValueAccess) {
		BlockPos blockPos = this.getCamera().getBlockPosition();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (this.minecraft.player.closerThan(entity, 30.0)) {
				DebugGoalInfo debugGoalInfo = (DebugGoalInfo)debugValueAccess.getEntityValue(DebugSubscriptions.GOAL_SELECTORS, entity);
				this.renderBeeInfo(poseStack, multiBufferSource, entity, debugBeeInfo, debugGoalInfo);
			}
		});
		this.renderFlowerInfos(poseStack, multiBufferSource, debugValueAccess);
		Map<BlockPos, Set<UUID>> map = this.createHiveBlacklistMap(debugValueAccess);
		debugValueAccess.forEachBlock(DebugSubscriptions.BEE_HIVES, (blockPos2, debugHiveInfo) -> {
			if (blockPos.closerThan(blockPos2, 30.0)) {
				highlightHive(poseStack, multiBufferSource, blockPos2);
				Set<UUID> set = (Set<UUID>)map.getOrDefault(blockPos2, Set.of());
				this.renderHiveInfo(poseStack, multiBufferSource, blockPos2, debugHiveInfo, set, debugValueAccess);
			}
		});
		this.getGhostHives(debugValueAccess).forEach((blockPos2, list) -> {
			if (blockPos.closerThan(blockPos2, 30.0)) {
				this.renderGhostHive(poseStack, multiBufferSource, blockPos2, list);
			}
		});
	}

	/** Copies bee, flower, hive, and ghost-hive diagnostics into Rust semantic streams. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) return;
		DebugValueAccess access = this.minecraft.getConnection().createDebugValueAccess();
		if (this.minecraft.player != null && !this.minecraft.player.isSpectator()) this.updateLastLookedAtUuid();
		BlockPos center = camera.getBlockPosition();
		PoseStack transform = new PoseStack(); transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		access.forEachEntity(DebugSubscriptions.BEES, (entity, bee) -> {
			if (this.minecraft.player == null || !this.minecraft.player.closerThan(entity, MAX_RENDER_DIST_FOR_BEE_OVERLAY)) return;
			DebugGoalInfo goals = (DebugGoalInfo)access.getEntityValue(DebugSubscriptions.GOAL_SELECTORS, entity);
			int line = 0;
			line = beeLabel(text, camera, entity, line, bee.toString(), -1, 0.03F);
			line = beeLabel(text, camera, entity, line, bee.hivePos().isEmpty() ? "No hive" : "Hive: " + getPosDescription(entity, bee.hivePos().get()), bee.hivePos().isEmpty() ? PINK : -256, TEXT_SCALE);
			line = beeLabel(text, camera, entity, line, bee.flowerPos().isEmpty() ? "No flower" : "Flower: " + getPosDescription(entity, bee.flowerPos().get()), bee.flowerPos().isEmpty() ? PINK : -256, TEXT_SCALE);
			if (goals != null) for (DebugGoal goal : goals.goals()) if (goal.isRunning()) line = beeLabel(text, camera, entity, line, goal.name(), -16711936, TEXT_SCALE);
			if (bee.travelTicks() > 0) beeLabel(text, camera, entity, line, "Travelling: " + bee.travelTicks() + " ticks", bee.travelTicks() < 2400 ? GRAY : ORANGE, TEXT_SCALE);
		});
		Map<BlockPos, Set<UUID>> flowers = new HashMap<>();
		access.forEachEntity(DebugSubscriptions.BEES, (entity, bee) -> bee.flowerPos().ifPresent(pos -> flowers.computeIfAbsent(pos, ignored -> new HashSet<>()).add(entity.getUUID())));
		flowers.forEach((pos, ids) -> { if (center.closerThan(pos, MAX_RENDER_DIST_FOR_HIVE_OVERLAY)) { beeBox(geometry, transform, uvs, pos, 0x4DCCCC00); beeLabel(text, camera, pos, 1, ids.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet()).toString(), -256); beeLabel(text, camera, pos, 2, "Flower", -1); } });
		Map<BlockPos, Set<UUID>> blacklist = createHiveBlacklistMap(access);
		access.forEachBlock(DebugSubscriptions.BEE_HIVES, (pos, hive) -> {
			if (!center.closerThan(pos, MAX_RENDER_DIST_FOR_HIVE_OVERLAY)) return;
			beeBox(geometry, transform, uvs, pos, 0x4D3333FF);
			int line = 0;
			Set<UUID> blocked = blacklist.getOrDefault(pos, Set.of());
			if (!blocked.isEmpty()) beeLabel(text, camera, pos, line++, "Blacklisted by " + getBeeUuidsAsString(blocked), -65536);
			beeLabel(text, camera, pos, line++, "Out: " + getBeeUuidsAsString(getHiveMembers(pos, access)), GRAY);
			beeLabel(text, camera, pos, line++, hive.occupantCount() == 0 ? "In: -" : hive.occupantCount() == 1 ? "In: 1 bee" : "In: " + hive.occupantCount() + " bees", -256);
			beeLabel(text, camera, pos, line++, "Honey: " + hive.honeyLevel(), ORANGE);
			beeLabel(text, camera, pos, line, hive.type().getName().getString() + (hive.sedated() ? " (sedated)" : ""), -1);
		});
		getGhostHives(access).forEach((pos, names) -> { if (center.closerThan(pos, MAX_RENDER_DIST_FOR_HIVE_OVERLAY)) { beeBox(geometry, transform, uvs, pos, 0x4D3333FF); beeLabel(text, camera, pos, 0, names.toString(), -256); beeLabel(text, camera, pos, 1, "Ghost Hive", -65536); } });
	}

	private static int beeLabel(SubmitNodeStorage text, Camera camera, Entity entity, int line, String value, int color, float scale) {
		PoseStack pose = new PoseStack(); pose.translate(entity.getBlockX() + 0.5 - camera.getPosition().x, entity.getY() + 2.4 + line * 0.25 - camera.getPosition().y, entity.getBlockZ() + 0.5 - camera.getPosition().z); pose.mulPose(camera.rotation()); pose.scale(scale, -scale, scale); text.submitTextSemantic(0, pose, -0.5F, 0, Component.literal(value).getVisualOrderText(), true, Font.DisplayMode.SEE_THROUGH, color, -1, 0, 0); return line + 1;
	}
	private static void beeLabel(SubmitNodeStorage text, Camera camera, BlockPos pos, int line, String value, int color) { PoseStack pose = new PoseStack(); pose.translate(pos.getX() + 0.5 - camera.getPosition().x, pos.getY() + 1.3 + line * 0.2 - camera.getPosition().y, pos.getZ() + 0.5 - camera.getPosition().z); pose.mulPose(camera.rotation()); pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE); text.submitTextSemantic(0, pose, 0, 0, Component.literal(value).getVisualOrderText(), true, Font.DisplayMode.SEE_THROUGH, color, -1, 0, 0); }
	private static void beeBox(SubmitNodeStorage geometry, PoseStack transform, float[] uvs, BlockPos pos, int color) { float p=0.05F,x0=pos.getX()-p,y0=pos.getY()-p,z0=pos.getZ()-p,x1=pos.getX()+1+p,y1=pos.getY()+1+p,z1=pos.getZ()+1+p; float[][] faces={{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}}; for(float[] face:faces) if(!geometry.submitColoredQuadsSemantic(transform,RenderType.debugFilledBox(),face,uvs,new int[]{color},15728880)) throw new IllegalStateException("Rust whole-frame bee route rejected semantic marker"); }

	private Map<BlockPos, Set<UUID>> createHiveBlacklistMap(DebugValueAccess debugValueAccess) {
		Map<BlockPos, Set<UUID>> map = new HashMap();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			for (BlockPos blockPos : debugBeeInfo.blacklistedHives()) {
				((Set)map.computeIfAbsent(blockPos, blockPosx -> new HashSet())).add(entity.getUUID());
			}
		});
		return map;
	}

	private void renderFlowerInfos(PoseStack poseStack, MultiBufferSource multiBufferSource, DebugValueAccess debugValueAccess) {
		Map<BlockPos, Set<UUID>> map = new HashMap();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.flowerPos().isPresent()) {
				((Set)map.computeIfAbsent((BlockPos)debugBeeInfo.flowerPos().get(), blockPos -> new HashSet())).add(entity.getUUID());
			}
		});
		map.forEach((blockPos, set) -> {
			Set<String> set2 = (Set<String>)set.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
			int i = 1;
			DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, set2.toString(), blockPos, i++, -256, 0.02F);
			DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, "Flower", blockPos, i++, -1, 0.02F);
			float f = 0.05F;
			DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.05F, 0.8F, 0.8F, 0.0F, 0.3F);
		});
	}

	private static String getBeeUuidsAsString(Collection<UUID> collection) {
		if (collection.isEmpty()) {
			return "-";
		} else {
			return collection.size() > 3
				? collection.size() + " bees"
				: ((Set)collection.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet())).toString();
		}
	}

	private static void highlightHive(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos) {
		float f = 0.05F;
		DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
	}

	private void renderGhostHive(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, List<String> list) {
		float f = 0.05F;
		DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, list.toString(), blockPos, 0, -256, 0.02F);
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, "Ghost Hive", blockPos, 1, -65536, 0.02F);
	}

	private void renderHiveInfo(
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		BlockPos blockPos,
		DebugHiveInfo debugHiveInfo,
		Collection<UUID> collection,
		DebugValueAccess debugValueAccess
	) {
		int i = 0;
		if (!collection.isEmpty()) {
			renderTextOverHive(poseStack, multiBufferSource, "Blacklisted by " + getBeeUuidsAsString(collection), blockPos, debugHiveInfo, i++, -65536);
		}

		renderTextOverHive(
			poseStack, multiBufferSource, "Out: " + getBeeUuidsAsString(this.getHiveMembers(blockPos, debugValueAccess)), blockPos, debugHiveInfo, i++, -3355444
		);
		if (debugHiveInfo.occupantCount() == 0) {
			renderTextOverHive(poseStack, multiBufferSource, "In: -", blockPos, debugHiveInfo, i++, -256);
		} else if (debugHiveInfo.occupantCount() == 1) {
			renderTextOverHive(poseStack, multiBufferSource, "In: 1 bee", blockPos, debugHiveInfo, i++, -256);
		} else {
			renderTextOverHive(poseStack, multiBufferSource, "In: " + debugHiveInfo.occupantCount() + " bees", blockPos, debugHiveInfo, i++, -256);
		}

		renderTextOverHive(poseStack, multiBufferSource, "Honey: " + debugHiveInfo.honeyLevel(), blockPos, debugHiveInfo, i++, -23296);
		renderTextOverHive(
			poseStack, multiBufferSource, debugHiveInfo.type().getName().getString() + (debugHiveInfo.sedated() ? " (sedated)" : ""), blockPos, debugHiveInfo, i++, -1
		);
	}

	private void renderBeeInfo(
		PoseStack poseStack, MultiBufferSource multiBufferSource, Entity entity, DebugBeeInfo debugBeeInfo, @Nullable DebugGoalInfo debugGoalInfo
	) {
		boolean bl = this.isBeeSelected(entity);
		int i = 0;
		DebugRenderer.renderTextOverMob(poseStack, multiBufferSource, entity, i++, debugBeeInfo.toString(), -1, 0.03F);
		if (debugBeeInfo.hivePos().isEmpty()) {
			DebugRenderer.renderTextOverMob(poseStack, multiBufferSource, entity, i++, "No hive", -98404, 0.02F);
		} else {
			DebugRenderer.renderTextOverMob(
				poseStack, multiBufferSource, entity, i++, "Hive: " + this.getPosDescription(entity, (BlockPos)debugBeeInfo.hivePos().get()), -256, 0.02F
			);
		}

		if (debugBeeInfo.flowerPos().isEmpty()) {
			DebugRenderer.renderTextOverMob(poseStack, multiBufferSource, entity, i++, "No flower", -98404, 0.02F);
		} else {
			DebugRenderer.renderTextOverMob(
				poseStack, multiBufferSource, entity, i++, "Flower: " + this.getPosDescription(entity, (BlockPos)debugBeeInfo.flowerPos().get()), -256, 0.02F
			);
		}

		if (debugGoalInfo != null) {
			for (DebugGoal debugGoal : debugGoalInfo.goals()) {
				if (debugGoal.isRunning()) {
					DebugRenderer.renderTextOverMob(poseStack, multiBufferSource, entity, i++, debugGoal.name(), -16711936, 0.02F);
				}
			}
		}

		if (debugBeeInfo.travelTicks() > 0) {
			int j = debugBeeInfo.travelTicks() < 2400 ? -3355444 : -23296;
			DebugRenderer.renderTextOverMob(poseStack, multiBufferSource, entity, i++, "Travelling: " + debugBeeInfo.travelTicks() + " ticks", j, 0.02F);
		}
	}

	private static void renderTextOverHive(
		PoseStack poseStack, MultiBufferSource multiBufferSource, String string, BlockPos blockPos, DebugHiveInfo debugHiveInfo, int i, int j
	) {
		DebugRenderer.renderTextOverBlock(poseStack, multiBufferSource, string, blockPos, i, j, 0.02F);
	}

	private Camera getCamera() {
		return this.minecraft.gameRenderer.getMainCamera();
	}

	private String getPosDescription(Entity entity, BlockPos blockPos) {
		double d = blockPos.distToCenterSqr(entity.position());
		double e = Math.round(d * 10.0) / 10.0;
		return blockPos.toShortString() + " (dist " + e + ")";
	}

	private boolean isBeeSelected(Entity entity) {
		return Objects.equals(this.lastLookedAtUuid, entity.getUUID());
	}

	private Collection<UUID> getHiveMembers(BlockPos blockPos, DebugValueAccess debugValueAccess) {
		Set<UUID> set = new HashSet();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.hasHive(blockPos)) {
				set.add(entity.getUUID());
			}
		});
		return set;
	}

	private Map<BlockPos, List<String>> getGhostHives(DebugValueAccess debugValueAccess) {
		Map<BlockPos, List<String>> map = new HashMap();
		debugValueAccess.forEachEntity(DebugSubscriptions.BEES, (entity, debugBeeInfo) -> {
			if (debugBeeInfo.hivePos().isPresent() && debugValueAccess.getBlockValue(DebugSubscriptions.BEE_HIVES, (BlockPos)debugBeeInfo.hivePos().get()) == null) {
				((List)map.computeIfAbsent((BlockPos)debugBeeInfo.hivePos().get(), blockPos -> Lists.newArrayList())).add(DebugEntityNameGenerator.getEntityName(entity));
			}
		});
		return map;
	}

	private void updateLastLookedAtUuid() {
		DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(entity -> this.lastLookedAtUuid = entity.getUUID());
	}
}
