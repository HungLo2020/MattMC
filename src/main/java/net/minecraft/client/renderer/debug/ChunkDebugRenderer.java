package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import net.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChunkDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	final Minecraft minecraft;
	private double lastUpdateTime = Double.MIN_VALUE;
	private final int radius = 12;
	@Nullable
	private ChunkDebugRenderer.ChunkData data;

	public ChunkDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		double g = Util.getNanos();
		if (g - this.lastUpdateTime > 3.0E9) {
			this.lastUpdateTime = g;
			IntegratedServer integratedServer = this.minecraft.getSingleplayerServer();
			if (integratedServer != null) {
				this.data = new ChunkDebugRenderer.ChunkData(integratedServer, d, f);
			} else {
				this.data = null;
			}
		}

		if (this.data != null) {
			Map<ChunkPos, String> map = (Map<ChunkPos, String>)this.data.serverData.getNow(null);
			double h = this.minecraft.gameRenderer.getMainCamera().getPosition().y * 0.85;

			for (Entry<ChunkPos, String> entry : this.data.clientData.entrySet()) {
				ChunkPos chunkPos = (ChunkPos)entry.getKey();
				String string = (String)entry.getValue();
				if (map != null) {
					string = string + (String)map.get(chunkPos);
				}

				String[] strings = string.split("\n");
				int i = 0;

				for (String string2 : strings) {
					DebugRenderer.renderFloatingText(
						poseStack,
						multiBufferSource,
						string2,
						SectionPos.sectionToBlockCoord(chunkPos.x, 8),
						h + i,
						SectionPos.sectionToBlockCoord(chunkPos.z, 8),
						-1,
						0.15F,
						true,
						0.0F,
						true
					);
					i -= 2;
				}
			}
		}
	}

	/** Copies bounded client/server chunk diagnostics into Rust-owned semantic text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) return;
		double now = Util.getNanos();
		if (now - this.lastUpdateTime > 3.0E9) {
			this.lastUpdateTime = now;
			IntegratedServer integratedServer = this.minecraft.getSingleplayerServer();
			this.data = integratedServer == null ? null : new ChunkDebugRenderer.ChunkData(integratedServer, camera.getPosition().x, camera.getPosition().z);
		}
		if (this.data == null) return;
		Map<ChunkPos, String> server = this.data.serverData.getNow(null);
		float baseY = (float)(camera.getPosition().y * 0.85);
		for (Entry<ChunkPos, String> entry : this.data.clientData.entrySet()) {
			String value = entry.getValue();
			if (server != null) value += server.getOrDefault(entry.getKey(), "");
			int line = 0;
			for (String part : value.split("\\n")) {
				submitLabel(text, camera, SectionPos.sectionToBlockCoord(entry.getKey().x, 8), baseY + line, SectionPos.sectionToBlockCoord(entry.getKey().z, 8), part);
				line -= 2;
			}
		}
	}

	private static void submitLabel(SubmitNodeStorage text, Camera camera, double x, double y, double z, String value) {
		PoseStack pose = new PoseStack();
		pose.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);
		pose.mulPose(camera.rotation());
		pose.scale(0.15F, -0.15F, 0.15F);
		text.submitTextSemantic(0, pose, 0.0F, 0.0F, Component.literal(value).getVisualOrderText(), true,
			Font.DisplayMode.SEE_THROUGH, -1, -1, 0, 0);
	}

	@Environment(EnvType.CLIENT)
	final class ChunkData {
		final Map<ChunkPos, String> clientData;
		final CompletableFuture<Map<ChunkPos, String>> serverData;

		ChunkData(final IntegratedServer integratedServer, final double d, final double e) {
			ClientLevel clientLevel = ChunkDebugRenderer.this.minecraft.level;
			ResourceKey<Level> resourceKey = clientLevel.dimension();
			int i = SectionPos.posToSectionCoord(d);
			int j = SectionPos.posToSectionCoord(e);
			Builder<ChunkPos, String> builder = ImmutableMap.builder();
			ClientChunkCache clientChunkCache = clientLevel.getChunkSource();

			for (int k = i - 12; k <= i + 12; k++) {
				for (int l = j - 12; l <= j + 12; l++) {
					ChunkPos chunkPos = new ChunkPos(k, l);
					String string = "";
					LevelChunk levelChunk = clientChunkCache.getChunk(k, l, false);
					string = string + "Client: ";
					if (levelChunk == null) {
						string = string + "0n/a\n";
					} else {
						string = string + (levelChunk.isEmpty() ? " E" : "");
						string = string + "\n";
					}

					builder.put(chunkPos, string);
				}
			}

			this.clientData = builder.build();
			this.serverData = integratedServer.submit(() -> {
				ServerLevel serverLevel = integratedServer.getLevel(resourceKey);
				if (serverLevel == null) {
					return ImmutableMap.of();
				} else {
					Builder<ChunkPos, String> builderx = ImmutableMap.builder();
					ServerChunkCache serverChunkCache = serverLevel.getChunkSource();

					for (int kx = i - 12; kx <= i + 12; kx++) {
						for (int lx = j - 12; lx <= j + 12; lx++) {
							ChunkPos chunkPosx = new ChunkPos(kx, lx);
							builderx.put(chunkPosx, "Server: " + serverChunkCache.getChunkDebugData(chunkPosx));
						}
					}

					return builderx.build();
				}
			});
		}
	}
}
