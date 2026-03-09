package net.sodium.client.gl.device;

import net.sodium.client.compatibility.environment.OsUtils;
import net.sodium.client.gl.array.GlVertexArray;
import net.sodium.client.gl.buffer.*;
import net.sodium.client.gl.buffer.*;
import net.sodium.client.gl.functions.DeviceFunctions;
import net.sodium.client.gl.state.GlStateTracker;
import net.sodium.client.gl.sync.GlFence;
import net.sodium.client.gl.tessellation.*;
import net.sodium.client.gl.tessellation.*;
import net.sodium.client.gl.util.EnumBitField;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import java.nio.ByteBuffer;

public class GLRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();

    private final DeviceFunctions functions = new DeviceFunctions(this);

    private boolean isActive;
    private GlTessellation activeTessellation;

    @Override
    public CommandList createCommandList() {
        GLRenderDevice.this.checkDeviceActive();

        return this.commandList;
    }

    @Override
    public void makeActive() {
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
    public net.vulkanic.GraphicsCapabilities getCapabilities() {
        return VulkanicAPI.obtainGraphicsCapabilities();
    }

    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    @Override
    public int getSubTexelPrecisionBits() {
        // OpenGL only specifies "at least" 4 bits of sub-texel precision for texture fetches. Thankfully, nearly every
        // graphics card is Direct3D-compatible and capable of providing 8 bits of precision. The only exception to this
        // rule seems to be when using OpenGL on macOS, where it appears to arbitrarily limit the precision to 4 bits
        // *even if* the hardware is capable of better.
        if (OsUtils.getOs() == OsUtils.OperatingSystem.MAC) {
            return 4;
        }

        return 8;
    }

    @Override
    public int getMaxTextureLodBias() {
        return VulkanicAPI.getInteger(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_TEXTURE_MAX_LEVEL);
    }

    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
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
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, glBuffer);

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
                VulkanicAPI.bindBuffer(ctx, target.getTargetParameter(), buffer.handle());
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
            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

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
            // NO-OP
        }

        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            GLRenderDevice.this.activeTessellation = tessellation;
            GLRenderDevice.this.activeTessellation.bind(GLRenderDevice.this.commandList);

            return GLRenderDevice.this.drawCommandList;
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

            // TODO: speed this up?
            if (buffer instanceof GlImmutableBuffer) {
                EnumBitField<GlBufferStorageFlags> bufferFlags = ((GlImmutableBuffer) buffer).getFlags();

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

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);

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

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
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

        @Override
        public GlMutableBuffer createMutableBuffer() {
            return new GlMutableBuffer();
        }

        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);

            this.bindBuffer(GlBufferTarget.ARRAY_BUFFER, buffer);
            GLRenderDevice.this.functions.getBufferStorageFunctions()
                    .createBufferStorage(GlBufferTarget.ARRAY_BUFFER, bufferSize, flags);

            return buffer;
        }

        @Override
        public GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
            GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), primitiveType, bindings);
            tessellation.init(this);

            return tessellation;
        }
    }

    private class ImmediateDrawCommandList implements DrawCommandList {
        public ImmediateDrawCommandList() {

        }

        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType) {
            GlPrimitiveType primitiveType = GLRenderDevice.this.activeTessellation.getPrimitiveType();
            
            // Iris: Use GL_PATCHES when tessellation is active
            int primitiveId = net.irisshaders.iris.vertices.ImmediateState.usingTessellation 
                ? VulkanicAPI.GL_PATCHES
                : primitiveType.getId();

            VulkanicAPI.multiDrawElementsBaseVertex(VulkanicAPI.getCommandContext(), primitiveId,
                    batch.pElementCount,
                    indexType.getFormatId(),
                    batch.pElementPointer,
                    batch.size,
                    batch.pBaseVertex);
        }

        @Override
        public void endTessellating() {
            GLRenderDevice.this.activeTessellation.unbind(GLRenderDevice.this.commandList);
            GLRenderDevice.this.activeTessellation = null;
        }

        @Override
        public void flush() {
            if (GLRenderDevice.this.activeTessellation != null) {
                this.endTessellating();
            }
        }
    }
}
