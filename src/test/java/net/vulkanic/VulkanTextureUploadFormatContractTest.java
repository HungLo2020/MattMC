package net.vulkanic;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies backend-neutral upload format mapping and Vulkan legacy tuple resolver behavior.
 */
public class VulkanTextureUploadFormatContractTest {

    @Test
    public void testLegacyTupleMapperRecognizesCenterDepthTuple() {
        VulkanicTextureUploadFormat format = VulkanicTextureUploadFormat
            .fromLegacyGlTuple(VulkanicAPI.GL_R32F, VulkanicAPI.GL_RED, VulkanicAPI.GL_FLOAT)
            .orElseThrow(() -> new AssertionError("Expected R32F/RED/FLOAT tuple to be recognized"));

        assertEquals(VulkanicTextureUploadFormat.RED32_SFLOAT, format);
        assertEquals(VulkanicAPI.GL_R32F, format.legacyInternalFormat());
        assertEquals(VulkanicAPI.GL_RED, format.legacyFormat());
        assertEquals(VulkanicAPI.GL_FLOAT, format.legacyType());
    }

    @Test
    public void testVulkanLegacyTextureResolverAcceptsCenterDepthTuple() throws Exception {
        Object resolved = invokeLegacyResolver(VulkanicAPI.GL_R32F, VulkanicAPI.GL_RED, VulkanicAPI.GL_FLOAT);
        assertNotNull(resolved, "Resolver should return format metadata for R32F/RED/FLOAT");

        assertEquals(VK10.VK_FORMAT_R32_SFLOAT, readIntField(resolved, "vkFormat"));
        assertEquals(4, readIntField(resolved, "pixelBytes"));
        assertEquals(VK10.VK_IMAGE_ASPECT_COLOR_BIT, readIntField(resolved, "aspectMask"));
    }

    @Test
    public void testVulkanLegacyTextureResolverStillFailsFastForUnknownTuple() throws Exception {
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeLegacyResolver(0x7FFF_2010, VulkanicAPI.GL_RED, VulkanicAPI.GL_FLOAT)
        );

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(
            cause.getMessage().contains("Unsupported legacy texture upload format combination"),
            "Unsupported tuple should still fail fast with the resolver diagnostic"
        );
    }

    /**
     * Regression: fromLegacyGlTuple must NOT route GL_RGBA (unsized) to RGBA8_UNORM.
     *
     * <p>Iris render targets default to InternalTextureFormat.RGBA (GL_RGBA = 0x1908), not
     * GL_RGBA8 (0x8058). Routing GL_RGBA to RGBA8_UNORM silently changed the GL internalFormat
     * sent to glTexImage2D for those targets, which is incorrect. The caller's original
     * internalFormat must be preserved when it does not exactly match a known enum entry.</p>
     */
    @Test
    public void testTupleMapperDoesNotRouteUnsizedRgbaToRgba8Unorm() {
        // GL_RGBA (0x1908) is the unsized base format used by default Iris render targets.
        // It is NOT the same as GL_RGBA8 (0x8058).  The mapper must return empty so the
        // caller falls through to the raw GL path with the original internalFormat intact.
        int GL_RGBA = VulkanicAPI.GL_RGBA;          // 0x1908
        int GL_UNSIGNED_BYTE = VulkanicAPI.GL_UNSIGNED_BYTE;

        assertFalse(
            VulkanicTextureUploadFormat.fromLegacyGlTuple(GL_RGBA, GL_RGBA, GL_UNSIGNED_BYTE).isPresent(),
            "GL_RGBA (unsized) should NOT be routed to RGBA8_UNORM — preserves caller's internalFormat"
        );
    }

    /**
     * Regression: fromLegacyGlTuple must NOT downgrade GL_RGBA16 to GL_RGBA8.
     *
     * <p>Some shader packs use 16-bit RGBA render targets (GL_RGBA16). Routing those to
     * RGBA8_UNORM silently halved precision per channel, corrupting colortex data and
     * producing visual artefacts (e.g. transparent-white water surfaces).</p>
     */
    @Test
    public void testTupleMapperDoesNotDowngradeRgba16ToRgba8() {
        int GL_RGBA16 = VulkanicAPI.GL_RGBA16;      // 0x805B
        int GL_RGBA = VulkanicAPI.GL_RGBA;
        int GL_UNSIGNED_BYTE = VulkanicAPI.GL_UNSIGNED_BYTE;

        assertFalse(
            VulkanicTextureUploadFormat.fromLegacyGlTuple(GL_RGBA16, GL_RGBA, GL_UNSIGNED_BYTE).isPresent(),
            "GL_RGBA16 must NOT be routed to RGBA8_UNORM — would silently lose 8 bits per channel"
        );
    }

    /**
     * Regression: fromLegacyGlTuple must NOT downgrade GL_RGB16 to RGB8_UNORM.
     *
     * <p>A 16-bit-per-channel RGB buffer uses GL_RGB16 as internalFormat.  Earlier broad
     * matching converted it to RGB8_UNORM (GL_RGB as internalFormat), losing precision.</p>
     */
    @Test
    public void testTupleMapperDoesNotDowngradeRgb16ToRgb8() {
        int GL_RGB16 = VulkanicAPI.GL_RGB16;        // 0x8054
        int GL_RGB   = VulkanicAPI.GL_RGB;
        int GL_UNSIGNED_BYTE = VulkanicAPI.GL_UNSIGNED_BYTE;

        assertFalse(
            VulkanicTextureUploadFormat.fromLegacyGlTuple(GL_RGB16, GL_RGB, GL_UNSIGNED_BYTE).isPresent(),
            "GL_RGB16 must NOT be routed to RGB8_UNORM — would silently lose 8 bits per channel"
        );
    }

    /**
     * Positive regression anchor: exact-match tuples that ARE canonical to an enum entry
     * must still be recognized after the broad-match removal.
     */
    @Test
    public void testTupleMapperStillRecognizesCanonicalRgba8UnormTuple() {
        VulkanicTextureUploadFormat format = VulkanicTextureUploadFormat
            .fromLegacyGlTuple(VulkanicAPI.GL_RGBA8, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_BYTE)
            .orElseThrow(() -> new AssertionError("Exact RGBA8 tuple should still be recognized"));

        assertEquals(VulkanicTextureUploadFormat.RGBA8_UNORM, format);
    }

    private static Object invokeLegacyResolver(int internalFormat, int format, int type) throws Exception {
        Class<?> resolverClass = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$LegacyTextureFormatInfo");
        Method resolveMethod = resolverClass.getDeclaredMethod("resolve", int.class, int.class, int.class);
        resolveMethod.setAccessible(true);
        return resolveMethod.invoke(null, internalFormat, format, type);
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
