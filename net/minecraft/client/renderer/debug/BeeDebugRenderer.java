package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.util.debug.DebugBeeInfo;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugHiveInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugGoalInfo.DebugGoal;
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
