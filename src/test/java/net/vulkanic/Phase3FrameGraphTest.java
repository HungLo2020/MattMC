package net.vulkanic;

import net.blaze3d.framegraph.FrameGraphBuilder;
import net.blaze3d.framegraph.FramePass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase 3e: FrameGraph migration to Vulkanic.
 *
 * <p>Validates:
 * <ul>
 *   <li>{@link VulkanicFrameGraph} and {@link VulkanicFramePass} interfaces exist in
 *       {@code net.vulkanic}.</li>
 *   <li>{@link FrameGraphBuilder} implements {@link VulkanicFrameGraph} — a {@code FrameGraphBuilder}
 *       can be used wherever a {@code VulkanicFrameGraph} is expected.</li>
 *   <li>{@link FramePass} extends {@link VulkanicFramePass} — a {@code FramePass} can be used
 *       wherever a {@code VulkanicFramePass} is expected.</li>
 *   <li>{@link ResourceHandle} / {@link ResourceDescriptor} / {@link GraphicsResourceAllocator}
 *       now live in {@code net.vulkanic}.</li>
 *   <li>{@link VulkanicAPI#beginFrame()} returns a non-null {@code VulkanicFrameGraph}.</li>
 *   <li>{@link VulkanicAPI#executeFrame} correctly executes passes in dependency order.</li>
 *   <li>{@link GraphicsResourceAllocator#UNPOOLED} allocates and frees resources correctly.</li>
 * </ul>
 *
 * All tests run without an OpenGL context (no GL calls made).
 */
public class Phase3FrameGraphTest {

    // ── Interface hierarchy ─────────────────────────────────────────────────

    @Test
    public void testFrameGraphBuilderImplementsVulkanicFrameGraph() {
        FrameGraphBuilder fgb = new FrameGraphBuilder();
        assertInstanceOf(VulkanicFrameGraph.class, fgb,
            "FrameGraphBuilder must implement VulkanicFrameGraph");
    }

    @Test
    public void testFramePassExtendsVulkanicFramePass() {
        FrameGraphBuilder fgb = new FrameGraphBuilder();
        FramePass pass = fgb.addPass("test");
        assertInstanceOf(VulkanicFramePass.class, pass,
            "FramePass must extend VulkanicFramePass");
    }

    @Test
    public void testVulkanicAPIBeginFrameReturnsNonNull() {
        VulkanicAPI.initialize();
        VulkanicFrameGraph fg = VulkanicAPI.beginFrame();
        assertNotNull(fg, "VulkanicAPI.beginFrame() must return a non-null VulkanicFrameGraph");
    }

    @Test
    public void testVulkanicAPIBeginFrameReturnsFrameGraphBuilder() {
        VulkanicAPI.initialize();
        VulkanicFrameGraph fg = VulkanicAPI.beginFrame();
        assertInstanceOf(FrameGraphBuilder.class, fg,
            "OpenGL backend must return a FrameGraphBuilder from beginFrame()");
    }

    // ── Resource types ──────────────────────────────────────────────────────

    @Test
    public void testResourceHandleInvalidThrows() {
        ResourceHandle<Object> invalid = ResourceHandle.invalid();
        assertThrows(IllegalStateException.class, invalid::get,
            "Invalid ResourceHandle.get() must throw IllegalStateException");
    }

    @Test
    public void testResourceDescriptorDefaultCanUsePhysicalResource() {
        ResourceDescriptor<String> desc = new ResourceDescriptor<>() {
            @Override public String allocate() { return "test"; }
            @Override public void free(String s) {}
        };
        assertTrue(desc.canUsePhysicalResource(desc),
            "Default canUsePhysicalResource must return true for same descriptor");
    }

    @Test
    public void testGraphicsResourceAllocatorUnpooled() {
        List<String> freed = new ArrayList<>();
        ResourceDescriptor<String> desc = new ResourceDescriptor<>() {
            @Override public String allocate() { return "resource"; }
            @Override public void free(String s) { freed.add(s); }
        };

        GraphicsResourceAllocator allocator = GraphicsResourceAllocator.UNPOOLED;
        String acquired = allocator.acquire(desc);
        assertEquals("resource", acquired, "UNPOOLED acquire must return allocate()");
        assertTrue(freed.isEmpty(), "Nothing should be freed before release");

        allocator.release(desc, acquired);
        assertEquals(List.of("resource"), freed, "UNPOOLED release must call free()");
    }

    // ── FrameGraph execution ─────────────────────────────────────────────────

    @Test
    public void testFrameGraphExecution() {
        List<String> executed = new ArrayList<>();

        FrameGraphBuilder fg = new FrameGraphBuilder();
        FramePass pass = fg.addPass("test-pass");
        pass.disableCulling();
        pass.executes(() -> executed.add("test-pass"));

        fg.execute(GraphicsResourceAllocator.UNPOOLED);

        assertEquals(List.of("test-pass"), executed,
            "Pass task must be executed when frame graph runs");
    }

    @Test
    public void testFrameGraphPassOrder() {
        List<String> executed = new ArrayList<>();

        FrameGraphBuilder fg = new FrameGraphBuilder();

        FramePass first = fg.addPass("first");
        first.disableCulling();
        first.executes(() -> executed.add("first"));

        FramePass second = fg.addPass("second");
        second.disableCulling();
        second.requires(first);
        second.executes(() -> executed.add("second"));

        fg.execute(GraphicsResourceAllocator.UNPOOLED);

        assertEquals(List.of("first", "second"), executed,
            "Passes must execute in dependency order");
    }

    @Test
    public void testFrameGraphWithResourceLifecycle() {
        List<String> events = new ArrayList<>();

        ResourceDescriptor<String> desc = new ResourceDescriptor<>() {
            @Override public String allocate() { events.add("allocate"); return "res"; }
            @Override public void prepare(String s) { events.add("prepare"); }
            @Override public void free(String s) { events.add("free"); }
        };

        FrameGraphBuilder fg = new FrameGraphBuilder();
        FramePass pass = fg.addPass("resource-pass");
        ResourceHandle<String> handle = pass.createsInternal("test-resource", desc);
        pass.executes(() -> {
            events.add("execute:" + handle.get());
        });

        // Export so the pass is not culled
        fg.importExternal("keep-alive", new Object());
        fg.createInternal("internal", new ResourceDescriptor<Object>() {
            @Override public Object allocate() { return new Object(); }
            @Override public void free(Object o) {}
        });

        // Force the resource pass to be kept alive
        FramePass sinkPass = fg.addPass("sink");
        sinkPass.reads(handle);
        sinkPass.disableCulling();
        sinkPass.executes(() -> events.add("sink"));

        fg.execute(GraphicsResourceAllocator.UNPOOLED);

        assertTrue(events.contains("allocate"), "Resource must be allocated");
        assertTrue(events.contains("prepare"), "Resource must be prepared");
        assertTrue(events.contains("execute:res"), "Pass must execute with resource");
        assertTrue(events.contains("free"), "Resource must be freed after all readers");
    }

    @Test
    public void testVulkanicAPIExecuteFrame() {
        VulkanicAPI.initialize();
        VulkanicFrameGraph fg = VulkanicAPI.beginFrame();

        List<String> executed = new ArrayList<>();
        VulkanicFramePass pass = fg.addPass("api-test-pass");
        pass.disableCulling();
        pass.executes(() -> executed.add("ran"));

        assertDoesNotThrow(() -> VulkanicAPI.executeFrame(fg, GraphicsResourceAllocator.UNPOOLED));
        assertEquals(List.of("ran"), executed,
            "VulkanicAPI.executeFrame must run frame passes");
    }

    // ── Vulkanic-native usage (using net.vulkanic.* types only) ─────────────

    @Test
    public void testVulkanicFrameGraphViaVulkanicAPIOnly() {
        // This test uses only net.vulkanic.* types — no blaze3d imports needed.
        VulkanicAPI.initialize();

        List<String> log = new ArrayList<>();

        VulkanicFrameGraph fg = VulkanicAPI.beginFrame();

        VulkanicFramePass p1 = fg.addPass("p1");
        p1.disableCulling();
        p1.executes(() -> log.add("p1"));

        VulkanicFramePass p2 = fg.addPass("p2");
        p2.disableCulling();
        p2.requires(p1);
        p2.executes(() -> log.add("p2"));

        VulkanicAPI.executeFrame(fg, GraphicsResourceAllocator.UNPOOLED);

        assertEquals(List.of("p1", "p2"), log,
            "Frame graph must run passes via VulkanicAPI in dependency order");
    }
}
