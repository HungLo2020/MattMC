package net.vulkanic;

/**
 * Command buffer for recording rendering commands.
 */
public interface VulkanicCommandBuffer {
    void beginRenderPass(VulkanicFramebuffer framebuffer);
    void endRenderPass();
    void bindShader(VulkanicShader shader);
    void bindVertexBuffer(VulkanicBuffer buffer);
    void bindIndexBuffer(VulkanicBuffer buffer);
    void bindTexture(int unit, VulkanicTexture texture);
    void draw(int vertexCount);
    void drawIndexed(int indexCount);
    void clear(float r, float g, float b, float a);
    void clearDepth(float depth);
    void setViewport(int x, int y, int width, int height);
    void submit();
}
