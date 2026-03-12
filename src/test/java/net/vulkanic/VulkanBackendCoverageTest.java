package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tracks what percentage of the {@link GraphicsBackend} interface is actually
 * implemented in {@link VulkanBackend} (the real Vulkan path).
 *
 * <h2>Why this test exists</h2>
 * The fail-hard proxy used by {@link VulkanicAPI} will throw for any
 * {@code GraphicsBackend} method that {@link VulkanBackend} does not
 * declare.  This test measures the gap and asserts it does not regress.
 *
 * <h2>Thresholds</h2>
 * Update {@code MIN_VULKAN_COVERAGE_PCT} when a real implementation batch
 * has been merged.  Never lower it — regressions are not acceptable.
 *
 * <h2>Running</h2>
 * <pre>
 *   ./gradlew test --tests net.vulkanic.VulkanBackendCoverageTest
 * </pre>
 */
class VulkanBackendCoverageTest {

    /**
     * Floor for Vulkan method coverage: the percentage of {@link GraphicsBackend}
     * interface methods that {@link VulkanBackend} explicitly declares.
     *
     * Because {@code VulkanicAPI} uses a fail-hard proxy, any undeclared method
     * throws at runtime.  This floor tracks real progress.
     *
     * Raise this number every time new Vulkan implementations are added.
     * NEVER lower it.
     */
    private static final double MIN_VULKAN_COVERAGE_PCT = 13.0;

    // ── helpers ────────────────────────────────────────────────────────────

    /** All non-static method names declared on {@link GraphicsBackend}. */
    private static Set<String> interfaceMethodNames() {
        return Arrays.stream(GraphicsBackend.class.getDeclaredMethods())
            .filter(m -> !Modifier.isStatic(m.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** All public method names declared directly on {@code clazz} (no inheritance). */
    private static Set<String> declaredPublicMethodNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static double coverage(Set<String> interfaceMethods, Set<String> backendMethods) {
        long covered = interfaceMethods.stream()
            .filter(backendMethods::contains)
            .count();
        return 100.0 * covered / interfaceMethods.size();
    }

    // ── tests ──────────────────────────────────────────────────────────────

    @Test
    void testVulkanBackendCoverageDoesNotRegress() {
        Set<String> ifaceMethods   = interfaceMethodNames();
        Set<String> vulkanMethods  = declaredPublicMethodNames(VulkanBackend.class);

        Set<String> covered = ifaceMethods.stream()
            .filter(vulkanMethods::contains)
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> missing = ifaceMethods.stream()
            .filter(m -> !vulkanMethods.contains(m))
            .collect(Collectors.toCollection(TreeSet::new));

        double pct = 100.0 * covered.size() / ifaceMethods.size();

        // ── diagnostic output (always printed) ──────────────────────────────
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  VULKAN BACKEND COVERAGE: %d / %d  (%.1f%%)%n",
            covered.size(), ifaceMethods.size(), pct);
        System.out.printf("  Floor threshold: %.1f%%%n", MIN_VULKAN_COVERAGE_PCT);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  ✅ covered  (%d): %s%n", covered.size(), covered);
        System.out.printf("  ❌ missing  (%d): %s%n", missing.size(), missing);
        System.out.println();

        assertTrue(pct >= MIN_VULKAN_COVERAGE_PCT,
            String.format(
                "Vulkan backend coverage regressed to %.1f%% (floor is %.1f%%). " +
                "Missing methods: %s",
                pct, MIN_VULKAN_COVERAGE_PCT, missing
            )
        );
    }

    /**
     * OpenGL coverage is 100% by compiler contract: {@link OpenGLBackend} implements
     * {@link GraphicsBackend}, so every abstract interface method must be satisfied
     * or the project would not compile.  Methods that rely on interface {@code default}
     * implementations are also correct — those defaults behave correctly for OpenGL.
     *
     * <p>This test therefore checks two things:</p>
     * <ol>
     *   <li>That {@code OpenGLBackend} actually implements {@code GraphicsBackend}
     *       (i.e. the compile contract is in effect).</li>
     *   <li>That every {@code GraphicsBackend} abstract method has at least one
     *       concrete implementation accessible through an {@code OpenGLBackend}
     *       instance (via {@code getMethods()}, which includes inherited and
     *       default implementations).</li>
     * </ol>
     */
    @Test
    void testOpenGLBackendCoverageIsComplete() {
        // Compiler guarantee: OpenGLBackend implements GraphicsBackend
        assertTrue(
            GraphicsBackend.class.isAssignableFrom(OpenGLBackend.class),
            "OpenGLBackend must implement GraphicsBackend"
        );

        // Every interface method must be callable (non-abstract) through OpenGLBackend
        Set<String> ifaceMethods = interfaceMethodNames();
        // getMethods() includes inherited, default, and declared — all methods
        // callable on an OpenGLBackend instance
        Set<String> callableMethods = Arrays.stream(OpenGLBackend.class.getMethods())
            .map(java.lang.reflect.Method::getName)
            .collect(Collectors.toCollection(TreeSet::new));

        Set<String> uncallable = ifaceMethods.stream()
            .filter(m -> !callableMethods.contains(m))
            .collect(Collectors.toCollection(TreeSet::new));

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  OPENGL BACKEND: compiler-verified 100%%%n");
        System.out.printf("  Interface methods: %d  |  all callable via OpenGLBackend: %s%n",
            ifaceMethods.size(), uncallable.isEmpty() ? "YES" : "NO — " + uncallable);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        assertTrue(uncallable.isEmpty(),
            "These GraphicsBackend methods are not callable through OpenGLBackend: " + uncallable
        );
    }

    /**
     * Prints a side-by-side comparison of which interface methods each backend
     * covers.  Informational only — never fails.
     */
    @Test
    void testPrintCoverageMatrix() {
        Set<String> ifaceMethods  = interfaceMethodNames();
        Set<String> vulkanMethods = declaredPublicMethodNames(VulkanBackend.class);
        Set<String> glMethods     = declaredPublicMethodNames(OpenGLBackend.class);

        // Category → list of method names
        Map<String, Set<String>> categories = new LinkedHashMap<>();
        categories.put("BOTH_IMPLEMENTED",  new TreeSet<>());
        categories.put("GL_ONLY",            new TreeSet<>());
        categories.put("VULKAN_ONLY",        new TreeSet<>());
        categories.put("NEITHER_OVERRIDE",   new TreeSet<>());

        for (String m : ifaceMethods) {
            boolean gl  = glMethods.contains(m);
            boolean vk  = vulkanMethods.contains(m);
            if (gl && vk)       categories.get("BOTH_IMPLEMENTED").add(m);
            else if (gl)        categories.get("GL_ONLY").add(m);
            else if (vk)        categories.get("VULKAN_ONLY").add(m);
            else                categories.get("NEITHER_OVERRIDE").add(m);
        }

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  COVERAGE MATRIX  (total interface methods: %d)%n", ifaceMethods.size());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        for (Map.Entry<String, Set<String>> e : categories.entrySet()) {
            System.out.printf("  %-22s (%3d): %s%n",
                e.getKey(), e.getValue().size(), e.getValue());
        }
        System.out.println();
    }
}
