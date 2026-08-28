package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Path.DebugData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float MAX_RENDER_DIST = 80.0F;
	private static final int MAX_TARGETING_DIST = 8;
	private static final boolean SHOW_ONLY_SELECTED = false;
	private static final boolean SHOW_OPEN_CLOSED = true;
	private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
	private static final boolean SHOW_GROUND_LABELS = true;
	private static final float TEXT_SCALE = 0.02F;

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		debugValueAccess.forEachEntity(
			DebugSubscriptions.ENTITY_PATHS,
			(entity, debugPathInfo) -> renderPath(poseStack, multiBufferSource, d, e, f, debugPathInfo.path(), debugPathInfo.maxNodeDistance())
		);
	}

	/** Copies path lines, node markers, and labels into Rust semantic streams. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame pathfinding-debug route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized()) return;
		DebugValueAccess access = Minecraft.getInstance().getConnection().createDebugValueAccess();
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		access.forEachEntity(DebugSubscriptions.ENTITY_PATHS, (entity, info) -> collectPath(transform, camera, geometry, text, info.path(), info.maxNodeDistance()));
	}

	private static void collectPath(org.joml.Matrix4f transform, Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text, Path path, float radius) {
		Node previous = null;
		for (int i=0;i<path.getNodeCount();i++) { Node node=path.getNode(i); if (previous!=null && distanceToCamera(node.asBlockPos(),camera.getPosition().x,camera.getPosition().y,camera.getPosition().z)<=80.0F) {
			int color=i==1?0xFF000000:ARGB.opaque(Mth.hsvToRgb((float)i/path.getNodeCount()*0.33F,0.9F,0.9F));
			line(transform, previous.x+0.5F,previous.y+0.5F,previous.z+0.5F,node.x+0.5F,node.y+0.5F,node.z+0.5F,color,6.0F); } previous=node; }
		BlockPos target=path.getTarget(); if(distanceToCamera(target,camera.getPosition().x,camera.getPosition().y,camera.getPosition().z)<=80.0F) box(geometry,transform,new AABB(target.getX()+.25,target.getY()+.25,target.getZ()+.25,target.getX()+.75,target.getY()+.75,target.getZ()+.75),0x8000FF00);
		for(int i=0;i<path.getNodeCount();i++){Node n=path.getNode(i); if(distanceToCamera(n.asBlockPos(),camera.getPosition().x,camera.getPosition().y,camera.getPosition().z)>80.0F)continue; float x=n.x+.5F,y=n.y+.01F*i,z=n.z+.5F; float half=radius; box(geometry,transform,new AABB(x-half,y,z-half,x+half,y+.24F,z+half),i==path.getNextNodeIndex()?0x8000FF00:0x80FFFF00); label(text,camera,new Vec3(x,n.y+.75,z),String.valueOf(n.type),-1,.02F); label(text,camera,new Vec3(x,n.y+.25,z),String.format(Locale.ROOT,"%.2f",n.costMalus),-1,.02F); }
		DebugData data=path.debugData(); if(data!=null){for(Node n:data.closedSet()) if(distanceToCamera(n.asBlockPos(),camera.getPosition().x,camera.getPosition().y,camera.getPosition().z)<=80)box(geometry,transform,new AABB(n.x+.5-radius/2,n.y+.01,n.z+.5-radius/2,n.x+.5+radius/2,n.y+.1,n.z+.5+radius/2),0x80FFCCCC); for(Node n:data.openSet()) if(distanceToCamera(n.asBlockPos(),camera.getPosition().x,camera.getPosition().y,camera.getPosition().z)<=80)box(geometry,transform,new AABB(n.x+.5-radius/2,n.y+.01,n.z+.5-radius/2,n.x+.5+radius/2,n.y+.1,n.z+.5+radius/2),0x80CCFFFF);}
	}

	private static void line(org.joml.Matrix4f t,float x0,float y0,float z0,float x1,float y1,float z1,int c,float w){if(!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(t,new float[]{x0,y0,z0,x1,y1,z1},c,w))throw new IllegalStateException("Rust whole-frame pathfinding-debug route rejected node line");}
	private static void box(SubmitNodeStorage g,org.joml.Matrix4f t,AABB b,int c){PoseStack p=new PoseStack();p.last().pose().set(t);float x0=(float)b.minX,y0=(float)b.minY,z0=(float)b.minZ,x1=(float)b.maxX,y1=(float)b.maxY,z1=(float)b.maxZ;float[]u={0,0,1,0,1,1,0,1};int[]col={c};float[][]f={{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0},{x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1},{x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0},{x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}};for(float[]q:f)if(!g.submitColoredQuadsSemantic(p,RenderType.debugFilledBox(),q,u,col,15728880))throw new IllegalStateException("Rust whole-frame pathfinding-debug route rejected node box");}
	private static void label(SubmitNodeStorage text,Camera camera,Vec3 pos,String s,int c,float scale){PoseStack p=new PoseStack();p.translate(pos.x,pos.y,pos.z);p.mulPose(camera.rotation());p.scale(scale,-scale,scale);var v=Component.literal(s).getVisualOrderText();text.submitTextSemantic(0,p,-Minecraft.getInstance().font.width(v)/2F,0,v,false,net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,c,0,15728880,0);}

	private static void renderPath(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, Path path, float g) {
		renderPath(poseStack, multiBufferSource, path, g, true, true, d, e, f);
	}

	public static void renderPath(
		PoseStack poseStack, MultiBufferSource multiBufferSource, Path path, float f, boolean bl, boolean bl2, double d, double e, double g
	) {
		renderPathLine(poseStack, multiBufferSource.getBuffer(RenderType.debugLineStrip(6.0)), path, d, e, g);
		BlockPos blockPos = path.getTarget();
		if (distanceToCamera(blockPos, d, e, g) <= 80.0F) {
			DebugRenderer.renderFilledBox(
				poseStack,
				multiBufferSource,
				new AABB(
						blockPos.getX() + 0.25F, blockPos.getY() + 0.25F, blockPos.getZ() + 0.25, blockPos.getX() + 0.75F, blockPos.getY() + 0.75F, blockPos.getZ() + 0.75F
					)
					.move(-d, -e, -g),
				0.0F,
				1.0F,
				0.0F,
				0.5F
			);

			for (int i = 0; i < path.getNodeCount(); i++) {
				Node node = path.getNode(i);
				if (distanceToCamera(node.asBlockPos(), d, e, g) <= 80.0F) {
					float h = i == path.getNextNodeIndex() ? 1.0F : 0.0F;
					float j = i == path.getNextNodeIndex() ? 0.0F : 1.0F;
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node.x + 0.5F - f, node.y + 0.01F * i, node.z + 0.5F - f, node.x + 0.5F + f, node.y + 0.25F + 0.01F * i, node.z + 0.5F + f).move(-d, -e, -g),
						h,
						0.0F,
						j,
						0.5F
					);
				}
			}
		}

		DebugData debugData = path.debugData();
		if (bl && debugData != null) {
			for (Node node2 : debugData.closedSet()) {
				if (distanceToCamera(node2.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node2.x + 0.5F - f / 2.0F, node2.y + 0.01F, node2.z + 0.5F - f / 2.0F, node2.x + 0.5F + f / 2.0F, node2.y + 0.1, node2.z + 0.5F + f / 2.0F)
							.move(-d, -e, -g),
						1.0F,
						0.8F,
						0.8F,
						0.5F
					);
				}
			}

			for (Node node2x : debugData.openSet()) {
				if (distanceToCamera(node2x.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node2x.x + 0.5F - f / 2.0F, node2x.y + 0.01F, node2x.z + 0.5F - f / 2.0F, node2x.x + 0.5F + f / 2.0F, node2x.y + 0.1, node2x.z + 0.5F + f / 2.0F)
							.move(-d, -e, -g),
						0.8F,
						1.0F,
						1.0F,
						0.5F
					);
				}
			}
		}

		if (bl2) {
			for (int k = 0; k < path.getNodeCount(); k++) {
				Node node3 = path.getNode(k);
				if (distanceToCamera(node3.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFloatingText(
						poseStack, multiBufferSource, String.valueOf(node3.type), node3.x + 0.5, node3.y + 0.75, node3.z + 0.5, -1, 0.02F, true, 0.0F, true
					);
					DebugRenderer.renderFloatingText(
						poseStack,
						multiBufferSource,
						String.format(Locale.ROOT, "%.2f", node3.costMalus),
						node3.x + 0.5,
						node3.y + 0.25,
						node3.z + 0.5,
						-1,
						0.02F,
						true,
						0.0F,
						true
					);
				}
			}
		}
	}

	public static void renderPathLine(PoseStack poseStack, VertexConsumer vertexConsumer, Path path, double d, double e, double f) {
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			if (!(distanceToCamera(node.asBlockPos(), d, e, f) > 80.0F)) {
				float g = (float)i / path.getNodeCount() * 0.33F;
				int j = i == 0 ? -16777216 : ARGB.opaque(Mth.hsvToRgb(g, 0.9F, 0.9F));
				vertexConsumer.addVertex(poseStack.last(), (float)(node.x - d + 0.5), (float)(node.y - e + 0.5), (float)(node.z - f + 0.5)).setColor(j);
			}
		}
	}

	private static float distanceToCamera(BlockPos blockPos, double d, double e, double f) {
		return (float)(Math.abs(blockPos.getX() - d) + Math.abs(blockPos.getY() - e) + Math.abs(blockPos.getZ() - f));
	}
}
