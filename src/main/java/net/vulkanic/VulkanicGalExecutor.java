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
        }
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
}
