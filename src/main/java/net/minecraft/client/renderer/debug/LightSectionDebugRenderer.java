package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage.SectionType;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class LightSectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500L);
	private static final int RADIUS = 10;
	private static final Vector4f LIGHT_AND_BLOCKS_COLOR = new Vector4f(1.0F, 1.0F, 0.0F, 0.25F);
	private static final Vector4f LIGHT_ONLY_COLOR = new Vector4f(0.25F, 0.125F, 0.0F, 0.125F);
	private final Minecraft minecraft;
	private final LightLayer lightLayer;
	private Instant lastUpdateTime = Instant.now();
	@Nullable
	private LightSectionDebugRenderer.SectionData data;

	public LightSectionDebugRenderer(Minecraft minecraft, LightLayer lightLayer) {
		this.minecraft = minecraft;
		this.lightLayer = lightLayer;
	}

	/** Copies light-section edges and faces into Rust semantic primitives. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (camera == null || !camera.isInitialized() || minecraft.level == null) return;
		Instant now = Instant.now();
		if (this.data == null || Duration.between(this.lastUpdateTime, now).compareTo(REFRESH_INTERVAL) > 0) {
			this.lastUpdateTime = now;
			this.data = new SectionData(this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), RADIUS, this.lightLayer);
		}
		SectionData snapshot = this.data;
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		collectEdges(snapshot.lightAndBlocksShape, snapshot.minPos, transform, geometry, 0x66FFFF00);
		collectEdges(snapshot.lightShape, snapshot.minPos, transform, geometry, 0x33204000);
		collectFaces(snapshot.lightAndBlocksShape, snapshot.minPos, transform, geometry, 0x40FFFF00);
		collectFaces(snapshot.lightShape, snapshot.minPos, transform, geometry, 0x20204000);
	}

	private static void collectEdges(DiscreteVoxelShape shape, SectionPos min, org.joml.Matrix4f transform, SubmitNodeStorage geometry, int color) {
		shape.forAllEdges((x0,y0,z0,x1,y1,z1) -> {
			float ax=SectionPos.sectionToBlockCoord(min.x())+x0*16.0F, ay=SectionPos.sectionToBlockCoord(min.y())+y0*16.0F, az=SectionPos.sectionToBlockCoord(min.z())+z0*16.0F;
			float bx=SectionPos.sectionToBlockCoord(min.x())+x1*16.0F, by=SectionPos.sectionToBlockCoord(min.y())+y1*16.0F, bz=SectionPos.sectionToBlockCoord(min.z())+z1*16.0F;
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,new float[]{ax,ay,az,bx,by,bz},color,1.0F)) throw new IllegalStateException("Rust whole-frame light-section debug route rejected edge");
		}, true);
	}

	private static void collectFaces(DiscreteVoxelShape shape, SectionPos min, org.joml.Matrix4f transform, SubmitNodeStorage geometry, int color) {
		shape.forAllFaces((direction,x,y,z) -> {
			float x0=SectionPos.sectionToBlockCoord(min.x())+x*16.0F, y0=SectionPos.sectionToBlockCoord(min.y())+y*16.0F, z0=SectionPos.sectionToBlockCoord(min.z())+z*16.0F;
			float x1=x0+16.0F,y1=y0+16.0F,z1=z0+16.0F; float[] v=switch(direction){
				case WEST->new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0}; case EAST->new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1};
				case DOWN->new float[]{x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1}; case UP->new float[]{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0};
				case NORTH->new float[]{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0}; default->new float[]{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1};};
			PoseStack pose=new PoseStack();pose.last().pose().set(transform); if(!geometry.submitColoredQuadsSemantic(pose,RenderType.debugFilledBox(),v,new float[]{0,0,1,0,1,1,0,1},new int[]{color},15728880))throw new IllegalStateException("Rust whole-frame light-section debug route rejected face");
		});
	}

	@Environment(EnvType.CLIENT)
	static final class SectionData {
		final DiscreteVoxelShape lightAndBlocksShape;
		final DiscreteVoxelShape lightShape;
		final SectionPos minPos;

		SectionData(LevelLightEngine levelLightEngine, SectionPos sectionPos, int i, LightLayer lightLayer) {
			int j = i * 2 + 1;
			this.lightAndBlocksShape = new BitSetDiscreteVoxelShape(j, j, j);
			this.lightShape = new BitSetDiscreteVoxelShape(j, j, j);

			for (int k = 0; k < j; k++) {
				for (int l = 0; l < j; l++) {
					for (int m = 0; m < j; m++) {
						SectionPos sectionPos2 = SectionPos.of(sectionPos.x() + m - i, sectionPos.y() + l - i, sectionPos.z() + k - i);
						SectionType sectionType = levelLightEngine.getDebugSectionType(lightLayer, sectionPos2);
						if (sectionType == SectionType.LIGHT_AND_DATA) {
							this.lightAndBlocksShape.fill(m, l, k);
							this.lightShape.fill(m, l, k);
						} else if (sectionType == SectionType.LIGHT_ONLY) {
							this.lightShape.fill(m, l, k);
						}
					}
				}
			}

			this.minPos = SectionPos.of(sectionPos.x() - i, sectionPos.y() - i, sectionPos.z() - i);
		}
	}
}
