package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Rust model rigs compose the part hierarchy that Java's
 * {@code ModelPart.visitRenderable} walks: for real vanilla models under
 * random poses, visibility and {@code skipDraw}, Rust's expanded part
 * transforms match Java's {@code entity * part} matrices in draw order.
 */
class ModelRigTransformParityTest {
	private static final MethodHandle EXPAND = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_vulkanic_world_model_rig_expand_transforms",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

	@Test
	void rustRigTransformsMatchJavaModelPartPoses() throws Throwable {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		List<ModelPart> roots = List.of(
			CowModel.createBodyLayer().bakeRoot(),
			LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE), 64, 32).bakeRoot(),
			LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64).bakeRoot());
		Random random = new Random(42);
		long rigId = 0x7E57_0000L;
		for (ModelPart root : roots) {
			List<ModelPart> nodes = new ArrayList<>();
			List<Integer> parents = new ArrayList<>();
			List<Boolean> hasCubes = new ArrayList<>();
			root.visitStructure((index, parent, path, part, cubes) -> {
				nodes.add(part);
				parents.add(parent);
				hasCubes.add(!cubes.isEmpty());
			});
			int count = nodes.size();
			int[] parentArray = parents.stream().mapToInt(Integer::intValue).toArray();
			long[] keys = new long[count];
			long[] generations = new long[count];
			for (int node = 0; node < count; node++) {
				if (hasCubes.get(node)) {
					keys[node] = node + 1L;
					generations[node] = 1L;
				}
			}
			rigId++;
			VulkanicGalBridge.registerModelRig(rigId, parentArray, keys, generations);
			try {
				for (int trial = 0; trial < 20; trial++) {
					for (ModelPart part : nodes) {
						part.x = random.nextFloat() * 8.0F - 4.0F;
						part.y = random.nextFloat() * 24.0F;
						part.z = random.nextFloat() * 8.0F - 4.0F;
						part.xRot = (random.nextFloat() * 2.0F - 1.0F) * (float) Math.PI * 1.5F;
						part.yRot = (random.nextFloat() * 2.0F - 1.0F) * (float) Math.PI * 1.5F;
						part.zRot = trial % 3 == 0 ? 0.0F : (random.nextFloat() * 2.0F - 1.0F) * (float) Math.PI;
						float scale = trial % 2 == 0 ? 1.0F : 0.5F + random.nextFloat();
						part.xScale = scale;
						part.yScale = trial % 4 == 1 ? 1.0F : scale;
						part.zScale = scale;
						part.visible = random.nextFloat() < 0.85F;
						part.skipDraw = random.nextFloat() < 0.1F;
					}
					nodes.get(0).visible = true;
					Matrix4f entity = new Matrix4f().translate(3.5F, -1.25F, 7.0F).rotateY(0.7F).scale(-1.0F, -1.0F, 1.0F);
					assertPartsMatch(rigId, root, nodes, entity);
				}
			} finally {
				VulkanicGalBridge.releaseModelRig(rigId);
			}
		}
	}

	private static void assertPartsMatch(long rigId, ModelPart root, List<ModelPart> nodes, Matrix4f entity) throws Throwable {
		List<Matrix4f> expected = new ArrayList<>();
		String[] activePath = {null};
		root.visitRenderable(new PoseStack(), (pose, path, cubeIndex, cube) -> {
			if (path.equals(activePath[0])) return;
			activePath[0] = path;
			expected.add(new Matrix4f(entity).mul(pose.pose()));
		});
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment poses = arena.allocate(40L * nodes.size(), 8);
			for (int node = 0; node < nodes.size(); node++) {
				ModelPart part = nodes.get(node);
				long base = node * 40L;
				float[] values = {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale};
				MemorySegment.copy(values, 0, poses, ValueLayout.JAVA_FLOAT, base, 9);
				poses.set(ValueLayout.JAVA_INT, base + 36, (part.visible ? 1 : 0) | (part.skipDraw ? 2 : 0));
			}
			MemorySegment entityMatrix = arena.allocate(64, 4);
			MemorySegment.copy(entity.get(new float[16]), 0, entityMatrix, ValueLayout.JAVA_FLOAT, 0, 16);
			int capacity = nodes.size();
			MemorySegment out = arena.allocate(64L * capacity, 4);
			int parts = (int) EXPAND.invokeExact(rigId, poses, nodes.size(), entityMatrix, out, capacity);
			assertEquals(expected.size(), parts, "drawn part count");
			for (int part = 0; part < parts; part++) {
				float[] actual = new float[16];
				MemorySegment.copy(out, ValueLayout.JAVA_FLOAT, part * 64L, actual, 0, 16);
				float[] wanted = expected.get(part).get(new float[16]);
				for (int value = 0; value < 16; value++) {
					float tolerance = 1e-5F * Math.max(1.0F, Math.abs(wanted[value]));
					assertTrue(Math.abs(actual[value] - wanted[value]) <= tolerance,
						"part " + part + " element " + value + ": rust " + actual[value] + " java " + wanted[value]);
				}
			}
		}
	}
}
