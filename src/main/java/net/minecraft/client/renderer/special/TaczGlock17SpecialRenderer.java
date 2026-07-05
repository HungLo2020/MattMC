package net.minecraft.client.renderer.special;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.tacz.TaczGlock17AnimationController;
import net.minecraft.client.tacz.TaczRefitTransform;
import net.minecraft.client.tacz.TaczScopeData;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.TaczAttachmentType;
import net.minecraft.world.item.TaczAttachmentItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczRefitGun;
import net.logging.LogUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TaczGlock17SpecialRenderer implements NoDataSpecialModelRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<String> FUNCTIONAL_MARKER_NODES = Set.of("lefthand_pos", "righthand_pos", "muzzle_flash", "shell");
	private static final Map<String, AttachmentRenderData> ATTACHMENT_CACHE = new ConcurrentHashMap<>();
	private final String gunId;
	private final ResourceLocation texture;
	private final BedrockGunGeometry geometry;
	private final BedrockAnimationSet animations;

	public TaczGlock17SpecialRenderer() {
		this("glock_17");
	}

	public TaczGlock17SpecialRenderer(String gunId) {
		this.gunId = gunId;
		this.texture = ResourceLocation.withDefaultNamespace("textures/gun/uv/" + gunId + ".png");
		this.geometry = BedrockGunGeometry.load(ResourceLocation.withDefaultNamespace("geo_models/gun/" + gunId + "_geo.json"));
		this.animations = BedrockAnimationSet.load(ResourceLocation.withDefaultNamespace("animations/" + gunId + ".animation.json"));
	}

	@Override
	public void submit(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k) {
		this.submitAnimated(itemDisplayContext, poseStack, submitNodeCollector, i, j, ItemStack.EMPTY);
	}

	public void submitFirstPerson(ItemStack itemStack, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay) {
		this.submitAnimated(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, poseStack, submitNodeCollector, light, overlay, itemStack);
	}

	private void submitAnimated(ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, ItemStack itemStack) {
		poseStack.pushPose();
		AnimationPose animationPose = this.animations.sample(TaczGlock17AnimationController.snapshot(itemStack));
		this.applyTaczTransform(itemDisplayContext, poseStack, animationPose, itemStack);
		submitNodeCollector.submitCustomGeometry(poseStack, RenderType.entityCutoutNoCull(this.texture), (pose, vertexConsumer) -> {
			PoseStack modelPoseStack = new PoseStack();
			modelPoseStack.last().set(pose);
			for (BedrockNode root : this.geometry.roots()) {
				root.render(modelPoseStack, itemDisplayContext, vertexConsumer, i, j, animationPose);
			}
		});
		this.submitAttachments(itemStack, itemDisplayContext, poseStack, submitNodeCollector, i, j, animationPose);
		this.submitFirstPersonArms(itemDisplayContext, poseStack, submitNodeCollector, i, animationPose);
		poseStack.popPose();
	}

	private void submitAttachments(
		ItemStack gunStack,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		int overlay,
		AnimationPose animationPose
	) {
		if (gunStack.isEmpty()) {
			return;
		}

		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			if (type == TaczAttachmentType.NONE || type == TaczAttachmentType.AMMO_MOD) {
				continue;
			}

			ItemStack attachmentStack = TaczRefitGun.getStoredAttachment(gunStack, type);
			if (!(attachmentStack.getItem() instanceof TaczAttachmentItem attachment)) {
				continue;
			}

			AttachmentRenderData attachmentData = attachmentData(attachment.getAttachmentId());
			if (attachmentData == null) {
				continue;
			}

			String marker = attachmentMarker(type);
			if (!this.geometry.hasNode(marker)) {
				continue;
			}

			submitNodeCollector.submitCustomGeometry(poseStack, RenderType.entityCutoutNoCull(attachmentData.texture()), (pose, vertexConsumer) -> {
				PoseStack attachmentPoseStack = new PoseStack();
				attachmentPoseStack.last().set(pose);
				if (this.geometry.applyAnimatedNodePath(marker, attachmentPoseStack, animationPose)) {
					attachmentPoseStack.translate(0.0F, -1.5F, 0.0F);
					for (BedrockNode root : attachmentData.geometry().roots()) {
						root.render(attachmentPoseStack, itemDisplayContext, vertexConsumer, light, overlay, animationPose, attachmentData);
					}
				}
			});
		}
	}

	private static String attachmentMarker(TaczAttachmentType type) {
		return switch (type) {
			case SCOPE -> "scope_pos";
			case MUZZLE -> "muzzle_pos";
			case LASER -> "laser_pos";
			case GRIP -> "grip_pos";
			case STOCK -> "stock_pos";
			case EXTENDED_MAG -> "magazine";
			default -> type.getSerializedName() + "_pos";
		};
	}

	private static AttachmentRenderData attachmentData(String attachmentId) {
		if (attachmentId == null || attachmentId.isEmpty()) {
			return null;
		}
		return ATTACHMENT_CACHE.computeIfAbsent(attachmentId, TaczGlock17SpecialRenderer::loadAttachmentData);
	}

	private static AttachmentRenderData loadAttachmentData(String attachmentId) {
		TaczScopeData.AttachmentDisplay display = TaczScopeData.display(attachmentId);
		if (display == null) {
			return null;
		}

		try {
			return new AttachmentRenderData(
				BedrockGunGeometry.load(display.geometryLocation()),
				display.textureLocation(),
				display.scope(),
				display.sight()
			);
		} catch (Exception exception) {
			LOGGER.warn("Failed to load TACZ attachment display {}", attachmentId, exception);
			return null;
		}
	}

	private void submitFirstPersonArms(
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		AnimationPose animationPose
	) {
		Minecraft minecraft = Minecraft.getInstance();
		AbstractClientPlayer player = minecraft.player;
		if (!itemDisplayContext.firstPerson() || player == null || player.isInvisible()) {
			return;
		}

		AvatarRenderer<AbstractClientPlayer> renderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
		ResourceLocation skin = player.getSkin().body().texturePath();
		this.submitFirstPersonArm(
			"righthand_pos",
			HumanoidArm.RIGHT,
			player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
			renderer,
			skin,
			poseStack,
			submitNodeCollector,
			light,
			animationPose
		);
		this.submitFirstPersonArm(
			"lefthand_pos",
			HumanoidArm.LEFT,
			player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE),
			renderer,
			skin,
			poseStack,
			submitNodeCollector,
			light,
			animationPose
		);
	}

	private void submitFirstPersonArm(
		String handNode,
		HumanoidArm arm,
		boolean sleeveVisible,
		AvatarRenderer<AbstractClientPlayer> renderer,
		ResourceLocation skin,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int light,
		AnimationPose animationPose
	) {
		poseStack.pushPose();
		if (this.geometry.applyAnimatedNodePath(handNode, poseStack, animationPose)) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			if (arm == HumanoidArm.RIGHT) {
				renderer.renderRightHand(poseStack, submitNodeCollector, light, skin, sleeveVisible);
			} else {
				renderer.renderLeftHand(poseStack, submitNodeCollector, light, skin, sleeveVisible);
			}
		}
		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		set.add(new Vector3f(-1.0F, -1.0F, -1.0F));
		set.add(new Vector3f(1.0F, 1.0F, 1.0F));
	}

	private void applyTaczTransform(ItemDisplayContext itemDisplayContext, PoseStack poseStack, AnimationPose animationPose, ItemStack itemStack) {
		if (itemDisplayContext.firstPerson()) {
			this.applyCameraAnimation(poseStack, animationPose);
			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			this.geometry.applyFirstPersonPositioning(
				poseStack,
				animationPose.aimProgress,
				this.scopeViewMatrix(itemStack),
				TaczRefitTransform.openingProgress(),
				TaczRefitTransform.previousType(),
				TaczRefitTransform.currentType(),
				TaczRefitTransform.viewProgress()
			);
			return;
		}

		poseStack.translate(0.5F, 2.0F, 0.5F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		switch (itemDisplayContext) {
			case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
				this.geometry.applyPositioningNode("thirdperson_hand", poseStack, 0.6F, 0.6F, 0.6F);
				poseStack.scale(0.6F, 0.6F, 0.6F);
			}
			case GROUND -> {
				this.geometry.applyPositioningNode("ground", poseStack, 0.6F, 0.6F, 0.6F);
				poseStack.scale(0.6F, 0.6F, 0.6F);
			}
			case FIXED -> {
				this.geometry.applyPositioningNode("fixed", poseStack, 1.2F, 1.2F, 1.2F);
				poseStack.scale(1.2F, 1.2F, 1.2F);
			}
			case GUI -> {
				this.geometry.applyPositioningNode("fixed", poseStack, 1.2F, 1.2F, 1.2F);
				poseStack.scale(0.82F, 0.82F, 0.82F);
			}
			default -> poseStack.scale(0.6F, 0.6F, 0.6F);
		}
	}

	private Matrix4f scopeViewMatrix(ItemStack itemStack) {
		TaczScopeData.AttachmentDisplay scope = TaczScopeData.scope(itemStack);
		if (scope == null || !this.geometry.hasNode("scope_pos")) {
			return null;
		}

		AttachmentRenderData attachmentData = attachmentData(scope.id());
		if (attachmentData == null || !attachmentData.geometry().hasNode("scope_view")) {
			return null;
		}

		return new Matrix4f(this.geometry.positioningMatrix("scope_pos")).mul(attachmentData.geometry().positioningMatrix("scope_view"));
	}

	private void applyCameraAnimation(PoseStack poseStack, AnimationPose animationPose) {
		NodePose camera = animationPose.node("camera");
		if (camera.rotation.x() != 0.0F) {
			poseStack.mulPose(Axis.XP.rotation(camera.rotation.x()));
		}
		if (camera.rotation.y() != 0.0F) {
			poseStack.mulPose(Axis.YP.rotation(camera.rotation.y()));
		}
		if (camera.rotation.z() != 0.0F) {
			poseStack.mulPose(Axis.ZP.rotation(-camera.rotation.z()));
		}
	}

	private record BedrockGunGeometry(List<BedrockNode> roots, Map<String, BedrockNode> nodes) {
		static BedrockGunGeometry load(ResourceLocation location) {
			try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
				JsonObject root = GsonHelper.parse(reader);
				JsonObject geometry = GsonHelper.getAsJsonArray(root, "minecraft:geometry").get(0).getAsJsonObject();
				JsonObject description = GsonHelper.getAsJsonObject(geometry, "description");
				int textureWidth = GsonHelper.getAsInt(description, "texture_width");
				int textureHeight = GsonHelper.getAsInt(description, "texture_height");
				JsonArray bones = GsonHelper.getAsJsonArray(geometry, "bones");
				Map<String, BoneData> boneData = new HashMap<>();
				Map<String, BedrockNode> nodes = new HashMap<>();
				List<BedrockNode> roots = new ArrayList<>();

				for (JsonElement element : bones) {
					JsonObject bone = element.getAsJsonObject();
					String name = GsonHelper.getAsString(bone, "name");
					BoneData data = BoneData.read(bone);
					boneData.put(name, data);
					nodes.put(name, new BedrockNode(name));
				}

				for (JsonElement element : bones) {
					JsonObject bone = element.getAsJsonObject();
					String name = GsonHelper.getAsString(bone, "name");
					BoneData data = boneData.get(name);
					BedrockNode node = nodes.get(name);
					node.hiddenMarker = FUNCTIONAL_MARKER_NODES.contains(name);
					node.x = convertPivot(data, boneData, 0);
					node.y = convertPivot(data, boneData, 1);
					node.z = convertPivot(data, boneData, 2);
					if (data.rotation != null) {
						node.xRot = degreesToRadians(data.rotation[0]);
						node.yRot = degreesToRadians(data.rotation[1]);
						node.zRot = degreesToRadians(data.rotation[2]);
					}

					if (data.parent != null) {
						BedrockNode parent = nodes.get(data.parent);
						node.parent = parent;
						parent.children.add(node);
					} else {
						roots.add(node);
					}

					JsonArray cubes = GsonHelper.getAsJsonArray(bone, "cubes", null);
					if (cubes == null) {
						continue;
					}

					for (JsonElement cubeElement : cubes) {
						JsonObject cube = cubeElement.getAsJsonObject();
						float[] size = readFloatArray(cube, "size", 3);
						float inflate = GsonHelper.getAsFloat(cube, "inflate", 0.0F);
						boolean mirror = GsonHelper.getAsBoolean(cube, "mirror", false);
						float x;
						float y;
						float z;
						JsonArray cubeRotation = GsonHelper.getAsJsonArray(cube, "rotation", null);
						if (cubeRotation == null) {
							x = convertOrigin(data, cube, 0);
							y = convertOrigin(data, cube, 1);
							z = convertOrigin(data, cube, 2);
							node.cubes.add(BedrockCube.create(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight));
						} else {
							BedrockNode cubeNode = new BedrockNode(null);
							cubeNode.x = convertCubePivot(data, cube, 0);
							cubeNode.y = convertCubePivot(data, cube, 1);
							cubeNode.z = convertCubePivot(data, cube, 2);
							float[] rotation = readFloatArray(cube, "rotation", 3);
							cubeNode.xRot = degreesToRadians(rotation[0]);
							cubeNode.yRot = degreesToRadians(rotation[1]);
							cubeNode.zRot = degreesToRadians(rotation[2]);
							x = convertOrigin(cube, 0);
							y = convertOrigin(cube, 1);
							z = convertOrigin(cube, 2);
							cubeNode.cubes.add(BedrockCube.create(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight));
							cubeNode.parent = node;
							node.children.add(cubeNode);
						}
					}
				}

				return new BedrockGunGeometry(roots, nodes);
			} catch (Exception exception) {
				LOGGER.error("Failed to load TACZ Glock 17 Bedrock geometry {}", location, exception);
				return new BedrockGunGeometry(List.of(), Map.of());
			}
		}

		void applyFirstPersonPositioning(
			PoseStack poseStack,
			float aimProgress,
			Matrix4f scopedAim,
			float refitProgress,
			TaczAttachmentType previousRefitType,
			TaczAttachmentType currentRefitType,
			float refitViewProgress
		) {
			Matrix4f idle = this.positioningMatrix("idle_view");
			Matrix4f aim = scopedAim == null ? this.positioningMatrix("iron_view") : scopedAim;
			Matrix4f matrix = idle;
			if (aim != null && aimProgress > 0.0F) {
				matrix = interpolateMatrix(idle, aim, aimProgress * (1.0F - refitProgress));
			}
			if (refitProgress > 0.0F) {
				Matrix4f previousRefit = this.refitMatrix(previousRefitType);
				Matrix4f currentRefit = this.refitMatrix(currentRefitType);
				Matrix4f refit = interpolateMatrix(previousRefit, currentRefit, refitViewProgress);
				matrix = interpolateMatrix(matrix, refit, refitProgress);
			}

			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.mulPose(matrix);
			poseStack.translate(0.0F, -1.5F, 0.0F);
		}

		private Matrix4f refitMatrix(TaczAttachmentType type) {
			if (type != TaczAttachmentType.NONE) {
				String typedView = "refit_" + type.getSerializedName() + "_view";
				if (this.pathTo(typedView) != null) {
					return this.positioningMatrix(typedView);
				}
			}
			return this.positioningMatrix("refit_view");
		}

		private Matrix4f positioningMatrix(String name) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return new Matrix4f().identity();
			}

			Matrix4f matrix = new Matrix4f().identity();
			for (int i = nodePath.size() - 1; i >= 0; i--) {
				BedrockNode part = nodePath.get(i);
				matrix.rotate(Axis.XN.rotation(part.xRot));
				matrix.rotate(Axis.YN.rotation(part.yRot));
				matrix.rotate(Axis.ZN.rotation(part.zRot));
				if (part.parent != null) {
					matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
				} else {
					matrix.translate(-part.x / 16.0F, 1.5F - part.y / 16.0F, -part.z / 16.0F);
				}
			}
			return matrix;
		}

		void applyPositioningNode(String name, PoseStack poseStack, float xScale, float yScale, float zScale) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return;
			}

			poseStack.translate(0.0F, 1.5F, 0.0F);
			for (int i = nodePath.size() - 1; i >= 0; i--) {
				BedrockNode part = nodePath.get(i);
				poseStack.mulPose(Axis.XN.rotation(part.xRot));
				poseStack.mulPose(Axis.YN.rotation(part.yRot));
				poseStack.mulPose(Axis.ZN.rotation(part.zRot));
				if (part.parent != null) {
					poseStack.translate(-part.x * xScale / 16.0F, -part.y * yScale / 16.0F, -part.z * zScale / 16.0F);
				} else {
					poseStack.translate(-part.x * xScale / 16.0F, (1.5F - part.y / 16.0F) * yScale, -part.z * zScale / 16.0F);
				}
			}
			poseStack.translate(0.0F, -1.5F, 0.0F);
		}

		boolean applyAnimatedNodePath(String name, PoseStack poseStack, AnimationPose animationPose) {
			List<BedrockNode> nodePath = this.pathTo(name);
			if (nodePath == null) {
				return false;
			}

			for (BedrockNode node : nodePath) {
				node.translateAndRotate(poseStack, animationPose.node(node.name));
			}
			return true;
		}

		boolean hasNode(String name) {
			return this.nodes.containsKey(name);
		}

		private List<BedrockNode> pathTo(String name) {
			BedrockNode node = this.nodes.get(name);
			if (node == null) {
				return null;
			}

			List<BedrockNode> path = new ArrayList<>();
			while (node != null) {
				path.add(0, node);
				node = node.parent;
			}
			return path;
		}
	}

	private static final class BedrockNode {
		private final String name;
		private final List<BedrockCube> cubes = new ArrayList<>();
		private final List<BedrockNode> children = new ArrayList<>();
		private BedrockNode parent;
		private float x;
		private float y;
		private float z;
		private float xRot;
		private float yRot;
		private float zRot;
		private boolean hiddenMarker;

		private BedrockNode(String name) {
			this.name = name;
		}

		private void render(
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose
		) {
			this.render(poseStack, itemDisplayContext, consumer, light, overlay, animationPose, null);
		}

		private void render(
			PoseStack poseStack,
			ItemDisplayContext itemDisplayContext,
			VertexConsumer consumer,
			int light,
			int overlay,
			AnimationPose animationPose,
			AttachmentRenderData attachmentRenderData
		) {
			if (this.cubes.isEmpty() && this.children.isEmpty()) {
				return;
			}

			poseStack.pushPose();
			this.translateAndRotate(poseStack, animationPose.node(this.name));
			int cubeLight = this.name != null && this.name.endsWith("_illuminated") ? LightTexture.pack(15, 15) : light;
			NodePose nodePose = animationPose.node(this.name);
			if (!this.hiddenMarker && nodePose.visible() && !this.hiddenByScopedFirstPerson(itemDisplayContext, attachmentRenderData)) {
				for (BedrockCube cube : this.cubes) {
					cube.compile(poseStack.last(), consumer, cubeLight, overlay);
				}
			}

			for (BedrockNode child : this.children) {
				child.render(poseStack, itemDisplayContext, consumer, cubeLight, overlay, animationPose, attachmentRenderData);
			}
			poseStack.popPose();
		}

		private boolean hiddenByScopedFirstPerson(ItemDisplayContext itemDisplayContext, AttachmentRenderData attachmentRenderData) {
			return attachmentRenderData != null
				&& itemDisplayContext.firstPerson()
				&& attachmentRenderData.hidesFirstPersonNode(this.name);
		}

		private void translateAndRotate(PoseStack poseStack, NodePose nodePose) {
			poseStack.translate(nodePose.position.x(), -nodePose.position.y(), nodePose.position.z());
			poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
			float zRotation = this.zRot + nodePose.rotation.z();
			float yRotation = this.yRot + nodePose.rotation.y();
			float xRotation = this.xRot + nodePose.rotation.x();
			if (zRotation != 0.0F) {
				poseStack.mulPose(Axis.ZP.rotation(zRotation));
			}
			if (yRotation != 0.0F) {
				poseStack.mulPose(Axis.YP.rotation(yRotation));
			}
			if (xRotation != 0.0F) {
				poseStack.mulPose(Axis.XP.rotation(xRotation));
			}
			poseStack.scale(nodePose.scale.x(), nodePose.scale.y(), nodePose.scale.z());
		}
	}

	private static final class BedrockAnimationSet {
		private final Map<String, BedrockAnimation> animations;

		private BedrockAnimationSet(Map<String, BedrockAnimation> animations) {
			this.animations = animations;
		}

		static BedrockAnimationSet load(ResourceLocation location) {
			try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
				JsonObject root = GsonHelper.parse(reader);
				JsonObject animationsObject = GsonHelper.getAsJsonObject(root, "animations");
				Map<String, BedrockAnimation> animations = new HashMap<>();
				for (Map.Entry<String, JsonElement> entry : animationsObject.entrySet()) {
					animations.put(entry.getKey(), BedrockAnimation.read(entry.getValue().getAsJsonObject()));
				}
				return new BedrockAnimationSet(animations);
			} catch (Exception exception) {
				LOGGER.error("Failed to load TACZ Glock 17 animation {}", location, exception);
				return new BedrockAnimationSet(Map.of());
			}
		}

		AnimationPose sample(TaczGlock17AnimationController.Snapshot snapshot) {
			AnimationPose pose = new AnimationPose(snapshot.aimProgress());
			for (TaczGlock17AnimationController.ActiveAnimation layer : snapshot.animations()) {
				BedrockAnimation animation = this.animations.get(layer.name());
				if (animation == null) {
					continue;
				}

				float time = layer.startNanos() == 0L ? animation.length : layer.ageSeconds();
				if (layer.startNanos() != 0L && time > animation.length && !animation.holdOnLastFrame) {
					continue;
				}

				animation.apply(pose, Math.min(time, animation.length), layer.additive());
			}
			return pose;
		}
	}

	private static final class BedrockAnimation {
		private final float length;
		private final boolean holdOnLastFrame;
		private final Map<String, NodeAnimation> nodes;

		private BedrockAnimation(float length, boolean holdOnLastFrame, Map<String, NodeAnimation> nodes) {
			this.length = length;
			this.holdOnLastFrame = holdOnLastFrame;
			this.nodes = nodes;
		}

		static BedrockAnimation read(JsonObject object) {
			float length = GsonHelper.getAsFloat(object, "animation_length", 0.0F);
			boolean holdOnLastFrame = false;
			if (object.has("loop")) {
				JsonElement loop = object.get("loop");
				holdOnLastFrame = loop.isJsonPrimitive() && "hold_on_last_frame".equals(loop.getAsString());
			}

			Map<String, NodeAnimation> nodes = new HashMap<>();
			JsonObject bones = GsonHelper.getAsJsonObject(object, "bones", new JsonObject());
			for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
				nodes.put(entry.getKey(), NodeAnimation.read(entry.getValue().getAsJsonObject()));
			}
			return new BedrockAnimation(length, holdOnLastFrame, nodes);
		}

		void apply(AnimationPose pose, float time, boolean additive) {
			for (Map.Entry<String, NodeAnimation> entry : this.nodes.entrySet()) {
				entry.getValue().apply(pose.node(entry.getKey()), time, additive);
			}
		}
	}

	private static final class NodeAnimation {
		private final Channel position;
		private final Channel rotation;
		private final Channel scale;

		private NodeAnimation(Channel position, Channel rotation, Channel scale) {
			this.position = position;
			this.rotation = rotation;
			this.scale = scale;
		}

		static NodeAnimation read(JsonObject object) {
			return new NodeAnimation(
				Channel.read(object.get("position"), ChannelKind.POSITION),
				Channel.read(object.get("rotation"), ChannelKind.ROTATION),
				Channel.read(object.get("scale"), ChannelKind.SCALE)
			);
		}

		void apply(NodePose pose, float time, boolean additive) {
			if (this.position != null) {
				Vector3f value = this.position.sample(time);
				if (additive) {
					pose.position.add(value);
				} else {
					pose.position.set(value);
				}
			}
			if (this.rotation != null) {
				Vector3f value = this.rotation.sample(time);
				if (additive) {
					pose.rotation.add(value);
				} else {
					pose.rotation.set(value);
				}
			}
			if (this.scale != null) {
				Vector3f value = this.scale.sample(time);
				if (additive) {
					pose.scale.mul(value);
				} else {
					pose.scale.set(value);
				}
			}
		}
	}

	private static final class Channel {
		private final List<Keyframe> keyframes;
		private final ChannelKind kind;

		private Channel(List<Keyframe> keyframes, ChannelKind kind) {
			this.keyframes = keyframes;
			this.kind = kind;
		}

		static Channel read(JsonElement element, ChannelKind kind) {
			if (element == null || element.isJsonNull()) {
				return null;
			}

			if (element.isJsonArray() || element.isJsonPrimitive()) {
				return new Channel(List.of(Keyframe.single(0.0F, convertAnimationValue(readVector(element), kind))), kind);
			}

			JsonObject object = element.getAsJsonObject();
			List<Keyframe> keyframes = new ArrayList<>();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				keyframes.add(Keyframe.read(Float.parseFloat(entry.getKey()), entry.getValue(), kind));
			}
			keyframes.sort(Comparator.comparing(Keyframe::time));
			return new Channel(keyframes, kind);
		}

		Vector3f sample(float time) {
			if (this.keyframes.isEmpty()) {
				return new Vector3f();
			}
			if (this.keyframes.size() == 1 || time <= this.keyframes.get(0).time) {
				return this.keyframes.get(0).value(false);
			}

			Keyframe last = this.keyframes.get(this.keyframes.size() - 1);
			if (time >= last.time) {
				return last.value(true);
			}

			for (int i = 0; i < this.keyframes.size() - 1; i++) {
				Keyframe from = this.keyframes.get(i);
				Keyframe to = this.keyframes.get(i + 1);
				if (time >= from.time && time <= to.time) {
					float alpha = (time - from.time) / (to.time - from.time);
					if (from.lerpMode == LerpMode.CATMULLROM || to.lerpMode == LerpMode.CATMULLROM) {
						return this.catmullrom(i, i + 1, alpha);
					}
					return from.value(true).lerp(to.value(false), alpha);
				}
			}
			return last.value(true);
		}

		private Vector3f catmullrom(int indexFrom, int indexTo, float alpha) {
			int previous = indexFrom == 0 ? 0 : indexFrom - 1;
			int next = indexTo == this.keyframes.size() - 1 ? this.keyframes.size() - 1 : indexTo + 1;
			Vector3f valuePrevious = this.keyframes.get(previous).value(true);
			Vector3f valueFrom = this.keyframes.get(indexFrom).value(false);
			Vector3f valueTo = this.keyframes.get(indexTo).value(false);
			Vector3f valueNext = this.keyframes.get(next).value(false);
			return new Vector3f(
				splineCurve(valuePrevious.x(), valueFrom.x(), valueTo.x(), valueNext.x(), alpha),
				splineCurve(valuePrevious.y(), valueFrom.y(), valueTo.y(), valueNext.y(), alpha),
				splineCurve(valuePrevious.z(), valueFrom.z(), valueTo.z(), valueNext.z(), alpha)
			);
		}
	}

	private record Keyframe(float time, Vector3f pre, Vector3f post, LerpMode lerpMode) {
		private static Keyframe single(float time, Vector3f value) {
			return new Keyframe(time, value, value, LerpMode.LINEAR);
		}

		private static Keyframe read(float time, JsonElement element, ChannelKind kind) {
			if (!element.isJsonObject()) {
				return single(time, convertAnimationValue(readVector(element), kind));
			}

			JsonObject object = element.getAsJsonObject();
			Vector3f pre = object.has("pre") ? convertAnimationValue(readVector(object.get("pre")), kind) : null;
			Vector3f post = object.has("post") ? convertAnimationValue(readVector(object.get("post")), kind) : null;
			if (pre == null && post == null) {
				pre = convertAnimationValue(readVector(element), kind);
				post = pre;
			} else if (pre == null) {
				pre = new Vector3f(post);
			} else if (post == null) {
				post = new Vector3f(pre);
			}
			return new Keyframe(time, pre, post, readLerpMode(object));
		}

		private Vector3f value(boolean usePost) {
			return new Vector3f(usePost ? this.post : this.pre);
		}
	}

	private enum ChannelKind {
		POSITION,
		ROTATION,
		SCALE
	}

	private enum LerpMode {
		LINEAR,
		CATMULLROM
	}

	private static final class AnimationPose {
		private final Map<String, NodePose> nodes = new HashMap<>();
		private final float aimProgress;

		private AnimationPose(float aimProgress) {
			this.aimProgress = aimProgress;
		}

		private NodePose node(String name) {
			return this.nodes.computeIfAbsent(name == null ? "" : name, key -> new NodePose());
		}
	}

	private static final class NodePose {
		private final Vector3f position = new Vector3f();
		private final Vector3f rotation = new Vector3f();
		private final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

		private boolean visible() {
			return this.scale.x() != 0.0F && this.scale.y() != 0.0F && this.scale.z() != 0.0F;
		}
	}

	private record BedrockCube(BedrockPolygon[] polygons) {
		private static BedrockCube create(JsonObject cube, float x, float y, float z, float[] size, float inflate, boolean mirror, int textureWidth, int textureHeight) {
			JsonObject faceUv = GsonHelper.getAsJsonObject(cube, "uv", null);
			return faceUv == null || faceUv.entrySet().isEmpty()
				? box(cube, x, y, z, size, inflate, mirror, textureWidth, textureHeight)
				: perFace(faceUv, x, y, z, size, inflate, textureWidth, textureHeight);
		}

		private static BedrockCube box(JsonObject cube, float x, float y, float z, float[] size, float inflate, boolean mirror, int textureWidth, int textureHeight) {
			float[] uv = readFloatArray(cube, "uv", 2);
			float width = size[0];
			float height = size[1];
			float depth = size[2];
			float xEnd = x + width + inflate;
			float yEnd = y + height + inflate;
			float zEnd = z + depth + inflate;
			x -= inflate;
			y -= inflate;
			z -= inflate;
			if (mirror) {
				float tmp = xEnd;
				xEnd = x;
				x = tmp;
			}

			BedrockVertex v1 = new BedrockVertex(x, y, z);
			BedrockVertex v2 = new BedrockVertex(xEnd, y, z);
			BedrockVertex v3 = new BedrockVertex(xEnd, yEnd, z);
			BedrockVertex v4 = new BedrockVertex(x, yEnd, z);
			BedrockVertex v5 = new BedrockVertex(x, y, zEnd);
			BedrockVertex v6 = new BedrockVertex(xEnd, y, zEnd);
			BedrockVertex v7 = new BedrockVertex(xEnd, yEnd, zEnd);
			BedrockVertex v8 = new BedrockVertex(x, yEnd, zEnd);
			int dx = (int)width;
			int dy = (int)height;
			int dz = (int)depth;
			float p1 = uv[0] + dz;
			float p2 = uv[0] + dz + dx;
			float p3 = uv[0] + dz + dx + dx;
			float p4 = uv[0] + dz + dx + dz;
			float p5 = uv[0] + dz + dx + dz + dx;
			float p6 = uv[1] + dz;
			float p7 = uv[1] + dz + dy;
			float p8 = uv[1];
			float p9 = uv[0];
			BedrockPolygon[] polygons = new BedrockPolygon[6];
			polygons[2] = new BedrockPolygon(new BedrockVertex[]{v6, v5, v1, v2}, p1, p8, p2, p6, textureWidth, textureHeight, mirror, Direction.DOWN);
			polygons[3] = new BedrockPolygon(new BedrockVertex[]{v3, v4, v8, v7}, p2, p6, p3, p8, textureWidth, textureHeight, mirror, Direction.UP);
			polygons[1] = new BedrockPolygon(new BedrockVertex[]{v1, v5, v8, v4}, p9, p6, p1, p7, textureWidth, textureHeight, mirror, Direction.WEST);
			polygons[4] = new BedrockPolygon(new BedrockVertex[]{v2, v1, v4, v3}, p1, p6, p2, p7, textureWidth, textureHeight, mirror, Direction.NORTH);
			polygons[0] = new BedrockPolygon(new BedrockVertex[]{v6, v2, v3, v7}, p2, p6, p4, p7, textureWidth, textureHeight, mirror, Direction.EAST);
			polygons[5] = new BedrockPolygon(new BedrockVertex[]{v5, v6, v7, v8}, p4, p6, p5, p7, textureWidth, textureHeight, mirror, Direction.SOUTH);
			return new BedrockCube(polygons);
		}

		private static BedrockCube perFace(JsonObject faces, float x, float y, float z, float[] size, float inflate, int textureWidth, int textureHeight) {
			float width = size[0];
			float height = size[1];
			float depth = size[2];
			float xEnd = x + width + inflate;
			float yEnd = y + height + inflate;
			float zEnd = z + depth + inflate;
			x -= inflate;
			y -= inflate;
			z -= inflate;
			BedrockVertex v1 = new BedrockVertex(x, y, z);
			BedrockVertex v2 = new BedrockVertex(xEnd, y, z);
			BedrockVertex v3 = new BedrockVertex(xEnd, yEnd, z);
			BedrockVertex v4 = new BedrockVertex(x, yEnd, z);
			BedrockVertex v5 = new BedrockVertex(x, y, zEnd);
			BedrockVertex v6 = new BedrockVertex(xEnd, y, zEnd);
			BedrockVertex v7 = new BedrockVertex(xEnd, yEnd, zEnd);
			BedrockVertex v8 = new BedrockVertex(x, yEnd, zEnd);
			BedrockPolygon[] polygons = new BedrockPolygon[6];
			polygons[2] = perFacePolygon(faces, "up", new BedrockVertex[]{v6, v5, v1, v2}, textureWidth, textureHeight, Direction.DOWN);
			polygons[3] = perFacePolygon(faces, "down", new BedrockVertex[]{v3, v4, v8, v7}, textureWidth, textureHeight, Direction.UP);
			polygons[1] = perFacePolygon(faces, "east", new BedrockVertex[]{v1, v5, v8, v4}, textureWidth, textureHeight, Direction.WEST);
			polygons[4] = perFacePolygon(faces, "north", new BedrockVertex[]{v2, v1, v4, v3}, textureWidth, textureHeight, Direction.NORTH);
			polygons[0] = perFacePolygon(faces, "west", new BedrockVertex[]{v6, v2, v3, v7}, textureWidth, textureHeight, Direction.EAST);
			polygons[5] = perFacePolygon(faces, "south", new BedrockVertex[]{v5, v6, v7, v8}, textureWidth, textureHeight, Direction.SOUTH);
			return new BedrockCube(polygons);
		}

		private static BedrockPolygon perFacePolygon(
			JsonObject faces, String faceName, BedrockVertex[] vertices, int textureWidth, int textureHeight, Direction direction
		) {
			JsonObject face = GsonHelper.getAsJsonObject(faces, faceName, null);
			if (face == null) {
				return BedrockPolygon.empty(direction);
			}

			float[] uv = readFloatArray(face, "uv", 2);
			float[] uvSize = readFloatArray(face, "uv_size", 2);
			if (uvSize[0] == 0.0F && uvSize[1] == 0.0F) {
				return BedrockPolygon.empty(direction);
			}

			return new BedrockPolygon(vertices, uv[0], uv[1], uv[0] + uvSize[0], uv[1] + uvSize[1], textureWidth, textureHeight, false, direction);
		}

		private void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
			Matrix4f poseMatrix = pose.pose();
			Matrix3f normalMatrix = pose.normal();
			for (BedrockPolygon polygon : this.polygons) {
				if (polygon.empty) {
					continue;
				}

				Vector3f normal = new Vector3f(polygon.normal);
				normal.mul(normalMatrix);
				for (BedrockVertex vertex : polygon.vertices) {
					Vector4f position = new Vector4f(vertex.x / 16.0F, vertex.y / 16.0F, vertex.z / 16.0F, 1.0F);
					position.mul(poseMatrix);
					consumer.addVertex(position.x(), position.y(), position.z())
						.setColor(-1)
						.setUv(vertex.u, vertex.v)
						.setOverlay(overlay)
						.setLight(light)
						.setNormal(normal.x(), normal.y(), normal.z());
				}
			}
		}
	}

	private static final class BedrockPolygon {
		private final BedrockVertex[] vertices;
		private final Vector3f normal;
		private final boolean empty;

		private BedrockPolygon(BedrockVertex[] vertices, float u1, float v1, float u2, float v2, int textureWidth, int textureHeight, boolean mirror, Direction direction) {
			this.vertices = vertices;
			vertices[0] = vertices[0].remap(u2 / textureWidth, v1 / textureHeight);
			vertices[1] = vertices[1].remap(u1 / textureWidth, v1 / textureHeight);
			vertices[2] = vertices[2].remap(u1 / textureWidth, v2 / textureHeight);
			vertices[3] = vertices[3].remap(u2 / textureWidth, v2 / textureHeight);
			if (mirror) {
				for (int i = 0; i < vertices.length / 2; i++) {
					BedrockVertex vertex = vertices[i];
					vertices[i] = vertices[vertices.length - 1 - i];
					vertices[vertices.length - 1 - i] = vertex;
				}
			}

			this.normal = direction.step();
			if (mirror) {
				this.normal.mul(-1.0F, 1.0F, 1.0F);
			}
			this.empty = false;
		}

		private BedrockPolygon(Direction direction) {
			this.vertices = new BedrockVertex[0];
			this.normal = direction.step();
			this.empty = true;
		}

		private static BedrockPolygon empty(Direction direction) {
			return new BedrockPolygon(direction);
		}
	}

	private record BedrockVertex(float x, float y, float z, float u, float v) {
		private BedrockVertex(float x, float y, float z) {
			this(x, y, z, 0.0F, 0.0F);
		}

		private BedrockVertex remap(float u, float v) {
			return new BedrockVertex(this.x, this.y, this.z, u, v);
		}
	}

	private record BoneData(String name, String parent, float[] pivot, float[] rotation) {
		private static BoneData read(JsonObject bone) {
			String name = GsonHelper.getAsString(bone, "name");
			String parent = GsonHelper.getAsString(bone, "parent", null);
			float[] pivot = readFloatArray(bone, "pivot", 3);
			JsonArray rotationArray = GsonHelper.getAsJsonArray(bone, "rotation", null);
			float[] rotation = rotationArray == null ? null : readFloatArray(rotationArray, 3);
			return new BoneData(name, parent, pivot, rotation);
		}
	}

	private static float convertPivot(BoneData bone, Map<String, BoneData> bones, int index) {
		if (bone.parent != null) {
			BoneData parent = bones.get(bone.parent);
			return index == 1 ? parent.pivot[index] - bone.pivot[index] : bone.pivot[index] - parent.pivot[index];
		}

		return index == 1 ? 24.0F - bone.pivot[index] : bone.pivot[index];
	}

	private static float convertCubePivot(BoneData parent, JsonObject cube, int index) {
		float[] pivot = readFloatArray(cube, "pivot", 3);
		return index == 1 ? parent.pivot[index] - pivot[index] : pivot[index] - parent.pivot[index];
	}

	private static float convertOrigin(BoneData bone, JsonObject cube, int index) {
		float[] origin = readFloatArray(cube, "origin", 3);
		float[] size = readFloatArray(cube, "size", 3);
		return index == 1 ? bone.pivot[index] - origin[index] - size[index] : origin[index] - bone.pivot[index];
	}

	private static float convertOrigin(JsonObject cube, int index) {
		float[] pivot = readFloatArray(cube, "pivot", 3);
		float[] origin = readFloatArray(cube, "origin", 3);
		float[] size = readFloatArray(cube, "size", 3);
		return index == 1 ? pivot[index] - origin[index] - size[index] : origin[index] - pivot[index];
	}

	private static float degreesToRadians(float degree) {
		return degree * (float)Math.PI / 180.0F;
	}

	private static float[] readFloatArray(JsonObject object, String key, int length) {
		return readFloatArray(GsonHelper.getAsJsonArray(object, key), length);
	}

	private static float[] readFloatArray(JsonArray array, int length) {
		float[] values = new float[length];
		for (int i = 0; i < length; i++) {
			values[i] = GsonHelper.convertToFloat(array.get(i), "[" + i + "]");
		}
		return values;
	}

	private static Vector3f readVector(JsonElement element) {
		if (element.isJsonPrimitive()) {
			float value = element.getAsFloat();
			return new Vector3f(value, value, value);
		}

		JsonArray array = element.getAsJsonArray();
		float x = array.size() > 0 ? GsonHelper.convertToFloat(array.get(0), "[0]") : 0.0F;
		float y = array.size() > 1 ? GsonHelper.convertToFloat(array.get(1), "[1]") : x;
		float z = array.size() > 2 ? GsonHelper.convertToFloat(array.get(2), "[2]") : x;
		return new Vector3f(x, y, z);
	}

	private static Vector3f convertAnimationValue(Vector3f value, ChannelKind kind) {
		return switch (kind) {
			case POSITION -> value.mul(1.0F / 16.0F);
			case ROTATION -> value.mul((float)Math.PI / 180.0F);
			case SCALE -> value;
		};
	}

	private static LerpMode readLerpMode(JsonObject object) {
		String lerpMode = GsonHelper.getAsString(object, "lerp_mode", "linear");
		return "catmullrom".equals(lerpMode) ? LerpMode.CATMULLROM : LerpMode.LINEAR;
	}

	private static float splineCurve(float y0, float y1, float y2, float y3, float alpha) {
		float v0 = (y2 - y0) * 0.5F;
		float v1 = (y3 - y1) * 0.5F;
		float t2 = alpha * alpha;
		float t3 = alpha * t2;
		float h1 = 2.0F * t3 - 3.0F * t2 + 1.0F;
		float h2 = -2.0F * t3 + 3.0F * t2;
		float h3 = t3 - 2.0F * t2 + alpha;
		float h4 = t3 - t2;
		return h1 * y1 + h2 * y2 + h3 * v0 + h4 * v1;
	}

	private static Matrix4f interpolateMatrix(Matrix4f from, Matrix4f to, float alpha) {
		return new Matrix4f(
			Mth.lerp(alpha, from.m00(), to.m00()),
			Mth.lerp(alpha, from.m01(), to.m01()),
			Mth.lerp(alpha, from.m02(), to.m02()),
			Mth.lerp(alpha, from.m03(), to.m03()),
			Mth.lerp(alpha, from.m10(), to.m10()),
			Mth.lerp(alpha, from.m11(), to.m11()),
			Mth.lerp(alpha, from.m12(), to.m12()),
			Mth.lerp(alpha, from.m13(), to.m13()),
			Mth.lerp(alpha, from.m20(), to.m20()),
			Mth.lerp(alpha, from.m21(), to.m21()),
			Mth.lerp(alpha, from.m22(), to.m22()),
			Mth.lerp(alpha, from.m23(), to.m23()),
			Mth.lerp(alpha, from.m30(), to.m30()),
			Mth.lerp(alpha, from.m31(), to.m31()),
			Mth.lerp(alpha, from.m32(), to.m32()),
			Mth.lerp(alpha, from.m33(), to.m33())
		);
	}

	private record AttachmentRenderData(BedrockGunGeometry geometry, ResourceLocation texture, boolean scope, boolean sight) {
		private boolean hidesFirstPersonNode(String nodeName) {
			if ((!this.scope && !this.sight) || nodeName == null) {
				return false;
			}

			return nodeName.equals("ocular")
				|| nodeName.startsWith("ocular_scope")
				|| nodeName.startsWith("ocular_sight");
		}
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(String gun) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<TaczGlock17SpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codec.STRING.optionalFieldOf("gun", "glock_17").forGetter(TaczGlock17SpecialRenderer.Unbaked::gun))
				.apply(instance, TaczGlock17SpecialRenderer.Unbaked::new)
		);

		@Override
		public MapCodec<TaczGlock17SpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext bakingContext) {
			return new TaczGlock17SpecialRenderer(this.gun);
		}
	}
}
