package net.vulkanic;

/**
 * Internal explicit Vulkanic GAL execution boundary.
 *
 * <p>Legacy GL-style APIs build immutable request objects before reaching this
 * interface. Backends lower those requests into their native implementation:
 * Vulkan maps them into existing command coordinators, while OpenGL translates
 * them back into the existing immediate-mode implementation during the
 * migration period.</p>
 */
public interface VulkanicGalExecutor {
    default VulkanicGalExecutionRequest.GraphicsDrawRequest captureGraphicsRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.ComputeDispatchRequest captureComputeDispatchRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.ClearRequest captureClearRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ClearRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.TransferRequest captureTransferRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.TransferRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.RenderPassBeginRequest captureRenderPassBeginRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassBeginRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.RenderPassEndRequest captureRenderPassEndRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassEndRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.ComputePassBeginRequest captureComputePassBeginRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputePassBeginRequest request
    ) {
        return request;
    }

    default VulkanicGalExecutionRequest.ComputePassEndRequest captureComputePassEndRequest(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputePassEndRequest request
    ) {
        return request;
    }

    default void executeGraphicsDraw(CommandContext ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest request) {
        VulkanicGalExecutionRequest.GraphicsDrawCommand command = request.command();
        switch (command.kind()) {
            case ARRAYS -> drawArrays(
                ctx,
                command.mode().toGlModeConstant(),
                command.firstVertex(),
                command.vertexCount()
            );
            case INDEXED -> drawIndexedInstancedBaseVertex(
                ctx,
                command.mode().toGlModeConstant(),
                command.indexCount(),
                command.indexType().toGlTypeConstant(),
                command.indexByteOffset(),
                command.instanceCount(),
                command.baseVertex()
            );
            case MULTI_INDEXED_BASE_VERTEX -> {
                for (VulkanicGalExecutionRequest.IndexedDraw draw : command.indexedDraws()) {
                    drawIndexedBaseVertex(
                        ctx,
                        command.mode().toGlModeConstant(),
                        draw.indexCount(),
                        command.indexType().toGlTypeConstant(),
                        (long) draw.firstIndex() * command.indexType().bytesPerIndex(),
                        draw.baseVertex()
                    );
                }
            }
        }
    }

    default void executeComputeDispatch(CommandContext ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest request) {
        VulkanicGalExecutionRequest.ComputeDispatchCommand command = request.command();
        if (command.indirect()) {
            dispatchComputeIndirect(ctx, command.indirectOffset());
        } else {
            dispatchCompute(ctx, command.workX(), command.workY(), command.workZ());
        }
    }

    default void executeClear(CommandContext ctx, VulkanicGalExecutionRequest.ClearRequest request) {
        clearBuffers(ctx, VulkanicClearBuffer.toLegacyGlMask(request.buffers().toArray(VulkanicClearBuffer[]::new)));
    }

    default void executeTransfer(CommandContext ctx, VulkanicGalExecutionRequest.TransferRequest request) {
        int[] i = request.intArgs();
        long[] l = request.longArgs();
        switch (request.kind()) {
            case COPY_BUFFER_SUB_DATA -> copyBufferSubData(ctx, i[0], i[1], l[0], l[1], l[2]);
            case COPY_NAMED_BUFFER_SUB_DATA -> copyNamedBufferSubDataDSA(ctx, i[0], i[1], l[0], l[1], l[2]);
            case COPY_IMAGE_SUB_DATA -> copyImageSubData(
                ctx,
                i[0], i[1], i[2], i[3], i[4], i[5],
                i[6], i[7], i[8], i[9], i[10], i[11],
                i[12], i[13], i[14]
            );
            case COPY_TEXTURE_SUB_IMAGE_2D -> copyTextureSubImage2D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7]);
            case COPY_TEX_IMAGE_2D -> copyTexImage2D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7]);
            case COPY_TEX_SUB_IMAGE_2D -> copyTexSubImage2D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7]);
            case BLIT_FRAMEBUFFER -> blitFramebuffer(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], i[8], i[9]);
            case BLIT_NAMED_FRAMEBUFFER -> blitNamedFramebuffer(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], i[8], i[9], i[10], i[11]);
            case READ_PIXELS -> readPixels(ctx, i[0], i[1], i[2], i[3], i[4], i[5], l[0]);
            case READ_PIXELS_FLOAT_ARRAY -> readPixels(ctx, i[0], i[1], i[2], i[3], i[4], i[5], request.floatArrayOutput());
            case BUFFER_SUB_DATA -> bufferSubData(ctx, i[0], l[0], request.bytePayload());
            case NAMED_BUFFER_SUB_DATA -> namedBufferSubDataDSA(ctx, i[0], l[0], request.bytePayload());
            case UPLOAD_TEXTURE_1D -> uploadTexture1D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], request.bytePayload());
            case UPLOAD_TEXTURE_2D -> uploadTexture2D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], request.bytePayload());
            case UPLOAD_TEXTURE_2D_SUB_IMAGE_POINTER -> uploadTexture2DSubImage(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], l[0]);
            case UPLOAD_TEXTURE_2D_SUB_IMAGE_BUFFER -> uploadTexture2DSubImage(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], request.bytePayload());
            case UPLOAD_TEXTURE_3D -> uploadTexture3D(ctx, i[0], i[1], i[2], i[3], i[4], i[5], i[6], i[7], i[8], request.bytePayload());
            case CLEAR_TEX_IMAGE_INT -> clearTexImage(ctx, i[0], i[1], i[2], i[3], request.intPayload());
            case CLEAR_BUFFER_SUB_DATA_INT -> clearBufferSubData(ctx, i[0], i[1], l[0], l[1], i[2], i[3], request.intPayload());
            case CLEAR_BUFFER_FLOAT -> clearBufferfv(ctx, i[0], i[1], request.floatPayload());
            case CLEAR_BUFFER_INT -> clearBufferiv(ctx, i[0], i[1], request.intPayload());
            case CLEAR_BUFFER_UINT -> clearBufferuiv(ctx, i[0], i[1], request.intPayload());
            case CLEAR_NAMED_FRAMEBUFFER_FLOAT -> clearNamedFramebufferfv(ctx, i[0], i[1], i[2], request.floatPayload());
            case CLEAR_NAMED_FRAMEBUFFER_INT -> clearNamedFramebufferiv(ctx, i[0], i[1], i[2], request.intPayload());
            case CLEAR_NAMED_FRAMEBUFFER_UINT -> clearNamedFramebufferuiv(ctx, i[0], i[1], i[2], request.intPayload());
            case GENERATE_MIPMAP -> generateMipmap(ctx, i[0]);
            case GENERATE_TEXTURE_MIPMAP -> generateTextureMipmapDSA(ctx, i[0]);
        }
    }

    VulkanicRenderPass executeRenderPassBegin(CommandContext ctx, VulkanicGalExecutionRequest.RenderPassBeginRequest request);

    default void executeRenderPassEnd(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassEndRequest request,
        VulkanicRenderPass pass
    ) {
        pass.close();
    }

    default void executeComputePassBegin(CommandContext ctx, VulkanicGalExecutionRequest.ComputePassBeginRequest request) {
    }

    default void executeComputePassEnd(CommandContext ctx, VulkanicGalExecutionRequest.ComputePassEndRequest request) {
    }

    void drawArrays(CommandContext ctx, int mode, int first, int count);

    void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex);

    void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex);

    void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ);

    void dispatchComputeIndirect(CommandContext ctx, long offset);

    void clearBuffers(CommandContext ctx, int mask);

    void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size);

    void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size);

    void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                          int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                          int width, int height, int depth);

    void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height);

    void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border);

    void copyTexSubImage2D(CommandContext ctx, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height);

    void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                         int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);

    void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer,
                              int srcX0, int srcY0, int srcX1, int srcY1,
                              int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);

    void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels);

    void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels);

    void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data);

    void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data);

    void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels);

    void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels);

    void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, long pixels);

    void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, java.nio.ByteBuffer pixels);

    void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels);

    void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data);

    void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data);

    void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values);

    void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values);

    void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values);

    void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value);

    void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value);

    void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value);

    void generateMipmap(CommandContext ctx, int target);

    void generateTextureMipmapDSA(CommandContext ctx, int texture);
}
