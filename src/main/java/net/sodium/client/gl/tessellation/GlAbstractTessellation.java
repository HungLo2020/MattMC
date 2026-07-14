package net.sodium.client.gl.tessellation;

import net.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.buffer.GlBufferTarget;
import net.sodium.client.gl.device.CommandList;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public abstract class GlAbstractTessellation implements GlTessellation {
    protected final GlPrimitiveType primitiveType;
    protected final TessellationBinding[] bindings;

    protected GlAbstractTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        this.primitiveType = primitiveType;
        this.bindings = bindings;
    }

    @Override
    public GlPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    public GlBuffer getDiagnosticVertexBuffer() {
        return this.getDiagnosticBuffer(GlBufferTarget.ARRAY_BUFFER);
    }

    public GlBuffer getDiagnosticIndexBuffer() {
        return this.getDiagnosticBuffer(GlBufferTarget.ELEMENT_BUFFER);
    }

    private GlBuffer getDiagnosticBuffer(GlBufferTarget target) {
        for (TessellationBinding binding : this.bindings) {
            if (binding.target() == target) {
                return binding.buffer();
            }
        }
        return null;
    }

    protected void bindAttributes(CommandList commandList) {
        CommandContext ctx = VulkanicAPI.getCommandContext();
        for (TessellationBinding binding : this.bindings) {
            commandList.bindBuffer(binding.target(), binding.buffer());

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
}
