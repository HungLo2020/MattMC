package net.sodium.client.gl.device;

import net.blaze3d.buffers.GpuBuffer;
import net.sodium.client.gl.array.GlVertexArray;
import net.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.buffer.GlBufferMapFlags;
import net.sodium.client.gl.buffer.GlBufferMapping;
import net.sodium.client.gl.buffer.GlBufferStorageFlags;
import net.sodium.client.gl.buffer.GlBufferTarget;
import net.sodium.client.gl.buffer.GlBufferUsage;
import net.sodium.client.gl.buffer.GlImmutableBuffer;
import net.sodium.client.gl.buffer.GlMutableBuffer;
import net.sodium.client.gl.functions.DeviceFunctions;
import net.sodium.client.gl.state.GlStateTracker;
import net.sodium.client.gl.sync.GlFence;
import net.sodium.client.gl.tessellation.GlIndexType;
import net.sodium.client.gl.tessellation.GlPrimitiveType;
import net.sodium.client.gl.tessellation.GlTessellation;
import net.sodium.client.gl.tessellation.GlVertexArrayTessellation;
import net.sodium.client.gl.tessellation.TessellationBinding;
import net.sodium.client.gl.util.EnumBitField;
import net.sodium.client.render.device.RenderBufferTarget;
import net.sodium.client.render.device.RenderTessellation;
import net.sodium.client.render.device.RenderTessellationBinding;
import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsCapabilities;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCoreAPI;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicIntegerQuery;
import net.vulkanic.VulkanicPrimitiveMode;

import java.nio.ByteBuffer;

/**
 * Vulkan-selected Sodium render device.
 *
 * <p>This intentionally does not inherit {@link GLRenderDevice}. Sodium's
 * terrain call sites can now submit backend-neutral tessellation and draw
 * commands directly to the Vulkan-selected device, while legacy GL-shaped
 * buffer objects remain supported during the staged arena/buffer migration.</p>
 */
public class VulkanicRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();
    private final DeviceFunctions functions = new DeviceFunctions(this);

    private boolean isActive;
    private RenderTessellation activeTessellation;

    @Override
    public CommandList createCommandList() {
        this.checkDeviceActive();
        return this.commandList;
    }

    @Override
    public void makeActive() {
        if (VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException(
                    "Java Sodium Vulkan render-device activation is unavailable; Rust owns the selected Vulkan route");
        }
        if (this.isActive) {
            return;
        }

        this.stateTracker.clear();
        this.isActive = true;
    }

    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        this.stateTracker.clear();
        this.isActive = false;
    }

    @Override
    public GraphicsCapabilities getCapabilities() {
        return VulkanicAPI.getGraphicsCapabilities();
    }

    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    @Override
    public int getSubTexelPrecisionBits() {
        return 8;
    }

    @Override
    public int getMaxTextureLodBias() {
        return VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicIntegerQuery.MAX_TEXTURE_LOD_BIAS);
    }

    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private final class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);

            this.bindBuffer(RenderBufferTarget.VERTEX, buffer);
            VulkanicRenderDevice.this.functions.getBufferStorageFunctions()
                    .createBufferStorage(GlBufferTarget.ARRAY_BUFFER, bufferSize, flags);

            return buffer;
        }

        @Override
        public GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
            GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), primitiveType, bindings);
            tessellation.init(this);

            return tessellation;
        }

        @Override
        public RenderTessellation createTessellation(VulkanicPrimitiveMode primitiveMode, RenderTessellationBinding[] bindings) {
            return new VulkanicTessellation(primitiveMode, bindings);
        }

        @Override
        public void bindVertexArray(GlVertexArray array) {
            if (this.stateTracker.makeVertexArrayActive(array)) {
                CommandContext ctx = VulkanicAPI.getCommandContext();
                VulkanicAPI.bindVertexArray(ctx, array.handle());
            }
        }

        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            this.bindBuffer(RenderBufferTarget.VERTEX, glBuffer);

            CommandContext ctx = VulkanicAPI.getCommandContext();
            VulkanicAPI.bufferData(ctx, GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), byteBuffer, usage.getId());
            glBuffer.setSize(byteBuffer.remaining());
        }

        @Override
        public void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            this.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
            this.bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);

            VulkanicAPI.copyBufferSubDataBetweenCopyTargets(VulkanicAPI.getCommandContext(), readOffset, writeOffset, bytes);
        }

        @Override
        public void bindBuffer(GlBufferTarget target, GlBuffer buffer) {
            if (this.stateTracker.makeBufferActive(target, buffer)) {
                CommandContext ctx = VulkanicAPI.getCommandContext();
                VulkanicAPI.bindBuffer(ctx, target.toVulkanicBufferTarget(), buffer.handle());
            }
        }

        @Override
        public void bindBuffer(RenderBufferTarget target, GlBuffer buffer) {
            if (this.stateTracker.makeBufferActive(target.toGlBufferTarget(), buffer)) {
                CommandContext ctx = VulkanicAPI.getCommandContext();
                VulkanicAPI.bindBuffer(ctx, target.toVulkanicBufferTarget(), buffer.handle());
            }
        }

        @Override
        public void unbindVertexArray() {
            if (this.stateTracker.makeVertexArrayActive(null)) {
                CommandContext ctx = VulkanicAPI.getCommandContext();
                VulkanicAPI.bindVertexArray(ctx, GlVertexArray.NULL_ARRAY_ID);
            }
        }

        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            this.bindBuffer(RenderBufferTarget.VERTEX, buffer);

            VulkanicAPI.bufferData(VulkanicAPI.getCommandContext(), GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), bufferSize, usage.getId());
            buffer.setSize(bufferSize);
        }

        @Override
        public void deleteBuffer(GlBuffer buffer) {
            if (buffer.getActiveMapping() != null) {
                this.unmap(buffer.getActiveMapping());
            }

            this.stateTracker.notifyBufferDeleted(buffer);

            int handle = buffer.handle();
            buffer.invalidateHandle();

            CommandContext ctx = VulkanicAPI.getCommandContext();
            VulkanicAPI.deleteBuffer(ctx, handle);
        }

        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            this.stateTracker.notifyVertexArrayDeleted(vertexArray);

            int handle = vertexArray.handle();
            vertexArray.invalidateHandle();

            VulkanicAPI.deleteVertexArrays(VulkanicAPI.getCommandContext(), handle);
        }

        @Override
        public void flush() {
            // Immediate command list.
        }

        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            VulkanicRenderDevice.this.activeTessellation = tessellation;
            VulkanicRenderDevice.this.activeTessellation.bind(this);

            return VulkanicRenderDevice.this.drawCommandList;
        }

        @Override
        public DrawCommandList beginTessellating(RenderTessellation tessellation) {
            VulkanicRenderDevice.this.activeTessellation = tessellation;
            VulkanicRenderDevice.this.activeTessellation.bind(this);

            return VulkanicRenderDevice.this.drawCommandList;
        }

        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            tessellation.delete(this);
        }

        @Override
        public GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            if (buffer.getActiveMapping() != null) {
                throw new IllegalStateException("Buffer is already mapped");
            }

            if (flags.contains(GlBufferMapFlags.PERSISTENT) && !(buffer instanceof GlImmutableBuffer)) {
                throw new IllegalStateException("Tried to map mutable buffer as persistent");
            }

            if (buffer instanceof GlImmutableBuffer immutableBuffer) {
                EnumBitField<GlBufferStorageFlags> bufferFlags = immutableBuffer.getFlags();

                if (flags.contains(GlBufferMapFlags.PERSISTENT) && !bufferFlags.contains(GlBufferStorageFlags.PERSISTENT)) {
                    throw new IllegalArgumentException("Tried to map non-persistent buffer as persistent");
                }

                if (flags.contains(GlBufferMapFlags.WRITE) && !bufferFlags.contains(GlBufferStorageFlags.MAP_WRITE)) {
                    throw new IllegalStateException("Tried to map non-writable buffer as writable");
                }

                if (flags.contains(GlBufferMapFlags.READ) && !bufferFlags.contains(GlBufferStorageFlags.MAP_READ)) {
                    throw new IllegalStateException("Tried to map non-readable buffer as readable");
                }
            }

            this.bindBuffer(RenderBufferTarget.VERTEX, buffer);

            ByteBuffer buf = VulkanicAPI.mapBuffer(VulkanicAPI.getCommandContext(), GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), offset, length, flags.getBitField());

            if (buf == null) {
                throw new RuntimeException("Failed to map buffer");
            }

            GlBufferMapping mapping = new GlBufferMapping(buffer, buf);

            buffer.setActiveMapping(mapping);

            return mapping;
        }

        @Override
        public void unmap(GlBufferMapping map) {
            checkMapDisposed(map);

            GlBuffer buffer = map.getBufferObject();

            this.bindBuffer(RenderBufferTarget.VERTEX, buffer);
            VulkanicAPI.unmapBuffer(VulkanicAPI.getCommandContext(), GlBufferTarget.ARRAY_BUFFER.getTargetParameter());

            buffer.setActiveMapping(null);
            map.dispose();
        }

        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            checkMapDisposed(map);

            GlBuffer buffer = map.getBufferObject();

            this.bindBuffer(GlBufferTarget.COPY_READ_BUFFER, buffer);
            VulkanicAPI.flushMappedBufferRange(VulkanicAPI.getCommandContext(), GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), offset, length);
        }

        @Override
        public GlFence createFence() {
            return new GlFence(VulkanicAPI.createGpuCompletionFence(VulkanicAPI.getCommandContext()));
        }

        private void checkMapDisposed(GlBufferMapping map) {
            if (map.isDisposed()) {
                throw new IllegalStateException("Buffer mapping is already disposed");
            }
        }
    }

    private final class ImmediateDrawCommandList implements DrawCommandList {
        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType) {
            this.multiDrawElementsBaseVertex(batch, indexType.toVulkanicIndexType());
        }

        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, VulkanicIndexType indexType) {
            RenderTessellation tessellation = VulkanicRenderDevice.this.activeTessellation;
            if (tessellation == null) {
                throw new IllegalStateException("Cannot draw without an active Sodium terrain tessellation");
            }

            VulkanicPrimitiveMode primitiveMode = tessellation.getPrimitiveMode();

            VulkanicAPI.multiDrawElementsBaseVertex(
                VulkanicAPI.getCommandContext(),
                primitiveMode,
                batch.pElementCount,
                indexType,
                batch.pElementPointer,
                batch.size,
                batch.pBaseVertex
            );
        }

        @Override
        public void endTessellating() {
            RenderTessellation tessellation = VulkanicRenderDevice.this.activeTessellation;
            if (tessellation != null) {
                tessellation.unbind(VulkanicRenderDevice.this.commandList);
                VulkanicRenderDevice.this.activeTessellation = null;
            }
        }

        @Override
        public void flush() {
            this.endTessellating();
        }
    }

    private static final class VulkanicTessellation implements RenderTessellation {
        private final VulkanicPrimitiveMode primitiveMode;
        private final RenderTessellationBinding[] bindings;

        private VulkanicTessellation(VulkanicPrimitiveMode primitiveMode, RenderTessellationBinding[] bindings) {
            if (primitiveMode == null) {
                throw new IllegalArgumentException("primitiveMode must not be null");
            }
            if (bindings == null) {
                throw new IllegalArgumentException("bindings must not be null");
            }

            this.primitiveMode = primitiveMode;
            this.bindings = bindings.clone();
        }

        @Override
        public void delete(CommandList commandList) {
            // The tessellation does not own the buffers it binds.
        }

        @Override
        public void bind(CommandList commandList) {
            CommandContext ctx = VulkanicAPI.getCommandContext();
            for (RenderTessellationBinding binding : this.bindings) {
                GpuBuffer buffer = binding.requireGpuBuffer();
                VulkanicAPI.bindBuffer(ctx, binding.target().toVulkanicBufferTarget(), VulkanicCoreAPI.bufferId(buffer));

                for (GlVertexAttributeBinding attrib : binding.attributeBindings()) {
                    if (attrib.isIntType()) {
                        VulkanicAPI.setVertexAttribIPointer(ctx, attrib.getIndex(), attrib.getCount(), attrib.getFormat(),
                                attrib.getStride(), attrib.getPointer());
                    } else {
                        VulkanicAPI.setVertexAttribPointer(ctx, attrib.getIndex(), attrib.getCount(), attrib.getFormat(), attrib.isNormalized(),
                                attrib.getStride(), attrib.getPointer());
                    }
                    VulkanicAPI.enableVertexAttribArray(ctx, attrib.getIndex());
                }
            }
        }

        @Override
        public void unbind(CommandList commandList) {
            // Vulkan does not need a VAO unbind. The next tessellation bind replaces buffer state.
        }

        @Override
        public VulkanicPrimitiveMode getPrimitiveMode() {
            return this.primitiveMode;
        }
    }
}
