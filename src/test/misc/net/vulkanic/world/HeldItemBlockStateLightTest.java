package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.util.FogParameters;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real item/state components and frame construction; client world/render services are mocked. */
@Isolated("Temporarily sets the renderer's pending semantic frame")
class HeldItemBlockStateLightTest {
    private static final IrisItemLightProvider PROVIDER = new IrisItemLightProvider() {};
    private static String previousByteBuddy;
    private static Method emissionMethod;
    private static Method seedMethod;
    private static Field frameField;

    @BeforeAll
    static void bootstrap() throws Exception {
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        emissionMethod = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("shaderPackHeldItemLightEmission", ItemStack.class);
        emissionMethod.setAccessible(true);
        seedMethod = RustGalWorldPrimitiveRenderer.class.getDeclaredMethod("seedShaderEnvironmentFrameLocked", ClientLevel.class, Camera.class);
        seedMethod.setAccessible(true);
        frameField = RustGalWorldPrimitiveRenderer.class.getDeclaredField("pendingShaderEnvironmentFrame");
        frameField.setAccessible(true);
    }

    @AfterAll
    static void restoreProperty() {
        if (previousByteBuddy == null) System.clearProperty("net.bytebuddy.experimental");
        else System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
    }

    private static int emission(ItemStack stack) throws Exception {
        return (int) emissionMethod.invoke(null, stack);
    }

    private static ItemStack light(int level) {
        ItemStack stack = new ItemStack(Items.LIGHT);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LightBlock.LEVEL, level));
        return stack;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 12, 15})
    void appliesActualLightBlockLevelWithoutMutatingStackOrDefaultState(int expected) throws Exception {
        Minecraft client = mock(Minecraft.class);
        client.player = mock(LocalPlayer.class);
        ItemStack stack = light(expected);
        ItemStack original = stack.copy();
        try (var game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getInstance).thenReturn(client);
            assertEquals(expected, emission(stack));
            assertEquals(expected, PROVIDER.getLightEmission(client.player, stack));
            assertTrue(ItemStack.matches(original, stack));
            assertEquals(15, ((BlockItem) stack.getItem()).getBlock().defaultBlockState().getLightEmission());
        }
    }

    @Test
    void supportsBooleanLightStatesAndIgnoresInvalidOrUnrelatedProperties() throws Exception {
        Minecraft client = mock(Minecraft.class);
        client.player = mock(LocalPlayer.class);
        ItemStack lamp = new ItemStack(Items.REDSTONE_LAMP);
        lamp.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("lit", "true", "unknown", "value")));
        ItemStack invalid = light(7);
        invalid.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("level", "99", "waterlogged", "not-a-boolean")));
        try (var game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getInstance).thenReturn(client);
            assertEquals(15, emission(lamp));
            assertEquals(15, PROVIDER.getLightEmission(client.player, lamp));
            assertEquals(15, emission(invalid));
            assertEquals(15, PROVIDER.getLightEmission(client.player, invalid));
        }
    }

    @Test
    void retainsDefaultAbsentComponentEmptyNonBlockAndMissingPlayerBehavior() throws Exception {
        Minecraft client = mock(Minecraft.class);
        client.player = mock(LocalPlayer.class);
        ItemStack absent = new ItemStack(Items.LIGHT);
        absent.remove(DataComponents.BLOCK_STATE);
        ItemStack nonBlock = new ItemStack(Items.STICK);
        nonBlock.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("lit", "true")));
        try (var game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getInstance).thenReturn(client);
            for (ItemStack stack : new ItemStack[] {new ItemStack(Items.LIGHT), absent, new ItemStack(Items.TORCH)}) {
                int expected = ((BlockItem) stack.getItem()).getBlock().defaultBlockState().getLightEmission();
                assertEquals(expected, emission(stack));
                assertEquals(expected, PROVIDER.getLightEmission(client.player, stack));
            }
            for (ItemStack stack : new ItemStack[] {ItemStack.EMPTY, nonBlock, new ItemStack(Items.REDSTONE_LAMP)}) {
                assertEquals(0, emission(stack));
                assertEquals(0, PROVIDER.getLightEmission(client.player, stack));
            }
            assertEquals(0, emission(null));
            client.player = null;
            assertEquals(0, emission(light(12)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, 30})
    void keepsNativeEmissionClamped(int rawEmission) throws Exception {
        Minecraft client = mock(Minecraft.class);
        client.player = mock(LocalPlayer.class);
        ItemStack stack = mock(ItemStack.class);
        BlockItem item = mock(BlockItem.class);
        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)).thenReturn(BlockItemStateProperties.EMPTY);
        when(item.getBlock()).thenReturn(block);
        when(block.defaultBlockState()).thenReturn(state);
        when(state.getBlock()).thenReturn(block);
        when(state.getLightEmission()).thenReturn(rawEmission);
        try (var game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getInstance).thenReturn(client);
            assertEquals(Math.clamp(rawEmission, 0, 15), emission(stack));
            // The retained provider has no clamp; this change preserves that contract.
            assertEquals(rawEmission, PROVIDER.getLightEmission(client.player, stack));
        }
    }

    @Test
    void nativeFrameProducerCopiesBothHandsAndRefreshesAfterSwitchingOrComponentChange() throws Exception {
        Minecraft client = mock(Minecraft.class);
        client.player = mock(LocalPlayer.class);
        Options options = mock(Options.class, RETURNS_DEEP_STUBS);
        when(options.gamma().get()).thenReturn(0.5);
        when(options.darknessEffectScale().get()).thenReturn(1.0);
        when(options.getEffectiveRenderDistance()).thenReturn(8);
        setField(client, "options", options);
        setField(client, "gui", mock(Gui.class, RETURNS_DEEP_STUBS));
        GameRenderer renderer = mock(GameRenderer.class);
        FogRenderer fog = mock(FogRenderer.class);
        when(fog.computeFogColorSemantic(any(), anyFloat(), any(), anyInt(), anyFloat(), anyBoolean())).thenReturn(new Vector4f());
        when(fog.collectFogParametersForRust(any(), anyInt(), anyBoolean(), any(), anyFloat(), any()))
            .thenReturn(new FogRenderer.RustFogParameters(new FogParameters(0, 0, 0, 1, 0, 128, 0, 128), 128, 128));
        setField(renderer, "fogRenderer", fog);
        when(renderer.lightTexture()).thenReturn(mock(LightTexture.class));
        setField(client, "gameRenderer", renderer);
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.getBiome(any()).value().getPrecipitationAt(any(), anyInt())).thenReturn(Biome.Precipitation.NONE);
        Camera camera = mock(Camera.class);
        when(camera.getPosition()).thenReturn(Vec3.ZERO);
        when(camera.getBlockPosition()).thenReturn(BlockPos.ZERO);
        when(camera.getFluidInCamera()).thenReturn(FogType.NONE);
        ItemStack dim = light(3), bright = light(12);
        Object oldFrame = frameField.get(null);
        try (var game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getInstance).thenReturn(client);
            when(client.player.getMainHandItem()).thenReturn(dim);
            when(client.player.getOffhandItem()).thenReturn(bright);
            assertFrame(level, camera, 3, 12);
            when(client.player.getMainHandItem()).thenReturn(bright);
            when(client.player.getOffhandItem()).thenReturn(dim);
            assertFrame(level, camera, 12, 3);
            bright.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LightBlock.LEVEL, 1));
            assertFrame(level, camera, 1, 3);
            client.player = null;
            assertFrame(level, camera, 0, 0);
        } finally {
            frameField.set(null, oldFrame);
        }
    }

    private static void assertFrame(ClientLevel level, Camera camera, int main, int off) throws Exception {
        seedMethod.invoke(null, level, camera);
        var frame = (VulkanicGalBridge.WorldShaderEnvironmentFrameRecord) frameField.get(null);
        assertTrue(frame.enabled());
        assertEquals(main, frame.mainHandItemLightEmission());
        assertEquals(off, frame.offHandItemLightEmission());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
