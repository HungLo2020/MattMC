package net.vulkanic.world;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnergySwirlUvSemanticsTest {
	@Test
	void materialIdentityIsStableAndDistinctFromOrdinaryTranslucency() {
		assertEquals(0x4553574C, RustGalWorldPrimitiveRenderer.MATERIAL_ID_ENERGY_SWIRL);
		assertNotEquals(
			RustGalWorldPrimitiveRenderer.MATERIAL_ID_TRANSLUCENT_TEXTURED,
			RustGalWorldPrimitiveRenderer.MATERIAL_ID_ENERGY_SWIRL
		);
	}

	@Test
	void vanillaEnergySwirlDoesNotContributeToTheEntityOutlineBuffer() {
		ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
		RenderType renderType = RenderType.energySwirl(texture, 0.25F, 0.75F);
		assertTrue(renderType.outline().isEmpty());
		assertFalse(renderType.isOutline());
	}

    @Test @SuppressWarnings("unchecked")
    void copiedCubePreservesNormalizedUvsAndAddsAnimationOffset() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var cube = new ModelPart.Cube(8, 4, 0, 0, 0, 4, 8, 4, 0, 0, 0,
            false, 64, 32, EnumSet.allOf(Direction.class));
        var root = new ModelPart(List.of(cube), Map.of());
        Model<Object> model = mock(Model.class);
        when(model.root()).thenReturn(root);
        Object state = new Object();
        var pose = new PoseStack().last();
        var texture = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
        var copied = new ArrayList<float[]>();
        String key = "mattmc.dev.rustEnergySwirl", previous = System.getProperty(key);
        try (var routes = mockStatic(WorldRenderRoutePolicy.class);
             var renderer = mockStatic(RustGalWorldPrimitiveRenderer.class)) {
            routes.when(() -> WorldRenderRoutePolicy.currentModelMeshRoute(true))
                .thenReturn(WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME);
			renderer.when(() -> RustGalWorldPrimitiveRenderer.enqueueEnergySwirlTexturedQuad(
				any(Matrix4f.class), eq(texture), any(float[].class), any(float[].class), eq(-1), eq(15728880)))
                .thenAnswer(call -> { copied.add(((float[]) call.getArgument(3)).clone()); return true; });
            renderer.when(() -> RustGalWorldPrimitiveRenderer.enqueueEnergySwirlModel(
                model, state, pose, texture, .25F, .75F, 64, 32, 15728880, -1)).thenCallRealMethod();
            System.clearProperty(key);
            assertFalse(RustGalWorldPrimitiveRenderer.enqueueEnergySwirlModel(
                model, state, pose, texture, .25F, .75F, 64, 32, 15728880, -1));
            assertTrue(copied.isEmpty());
            System.setProperty(key, "true");
            assertTrue(RustGalWorldPrimitiveRenderer.enqueueEnergySwirlModel(
                model, state, pose, texture, .25F, .75F, 64, 32, 15728880, -1));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
        assertEquals(cube.polygons.length, copied.size());
        for (int face = 0; face < cube.polygons.length; face++) {
            float[] expected = new float[8];
            for (int vertex = 0; vertex < 4; vertex++) {
                expected[vertex * 2] = cube.polygons[face].vertices()[vertex].u() + .25F;
                expected[vertex * 2 + 1] = cube.polygons[face].vertices()[vertex].v() + .75F;
            }
            assertArrayEquals(expected, copied.get(face), 0.000001F);
        }
    }
}
