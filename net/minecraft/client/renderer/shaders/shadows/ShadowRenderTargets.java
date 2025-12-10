package net.minecraft.client.renderer.shaders.shadows;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.targets.DepthTexture;
import net.minecraft.client.renderer.shaders.targets.RenderTarget;
import net.minecraft.client.renderer.shaders.texture.DepthBufferFormat;
import net.minecraft.client.renderer.shaders.texture.DepthCopyStrategy;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import net.minecraft.client.renderer.shaders.texture.PixelType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages shadow map render targets (depth and color buffers).
 * Based on IRIS ShadowRenderTargets.java
 */
public class ShadowRenderTargets {
    private final RenderTarget[] targets;
    private final PackShadowDirectives shadowDirectives;
    private final DepthTexture mainDepth; // shadowtex0
    private final DepthTexture noTranslucents; // shadowtex1
    private final GlFramebuffer depthSourceFb;
    private final GlFramebuffer noTranslucentsDestFb;
    private final boolean[] flipped;
    private final List<GlFramebuffer> ownedFramebuffers;
    private final int resolution;
    private final boolean[] hardwareFiltered;
    private final boolean[] mipped;
    private final boolean[] linearFiltered;
    private final InternalTextureFormat[] formats;
    private final IntList buffersToBeCleared;
    private final int size;
    private boolean fullClearRequired;
    private boolean translucentDepthDirty;

    public ShadowRenderTargets(int resolution, PackShadowDirectives shadowDirectives, boolean higherShadowcolor) {
        this.shadowDirectives = shadowDirectives;
        this.size = higherShadowcolor ? 
            PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS_IRIS : 
            PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS_OF;
        
        this.targets = new RenderTarget[size];
        this.formats = new InternalTextureFormat[size];
        this.flipped = new boolean[size];
        this.hardwareFiltered = new boolean[size];
        this.mipped = new boolean[size];
        this.linearFiltered = new boolean[size];
        this.buffersToBeCleared = new IntArrayList();
        this.ownedFramebuffers = new ArrayList<>();
        this.resolution = resolution;

        // Configure sampling settings from directives
        PackShadowDirectives.DepthSamplingSettings[] depthSettings = shadowDirectives.getDepthSamplingSettings();
        for (int i = 0; i < depthSettings.length; i++) {
            this.hardwareFiltered[i] = depthSettings[i].getHardwareFiltering();
            this.mipped[i] = depthSettings[i].getMipmap();
            this.linearFiltered[i] = !depthSettings[i].getNearest();
        }

        // Create shadow depth textures
        this.mainDepth = new DepthTexture("shadowtex0", resolution, resolution, DepthBufferFormat.DEPTH32F);
        this.noTranslucents = new DepthTexture("shadowtex1", resolution, resolution, DepthBufferFormat.DEPTH32F);

        // Configure filtering and mipmaps
        configureDepthTexture(mainDepth, linearFiltered[0], mipped[0]);
        configureDepthTexture(noTranslucents, linearFiltered[1], mipped[1]);

        // Mark for initial clear
        this.fullClearRequired = true;

        // Create framebuffers for depth operations
        this.depthSourceFb = createFramebufferWritingToMain(new int[]{0});
        this.noTranslucentsDestFb = createFramebufferWritingToMain(new int[]{0});
        this.noTranslucentsDestFb.addDepthAttachment(noTranslucents.getTextureId());

        this.translucentDepthDirty = true;
    }

    private void configureDepthTexture(DepthTexture texture, boolean linear, boolean mipmap) {
        // Configure texture filtering
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, 
            linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, 
            linear ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        
        if (mipmap) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, 
                linear ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static int log2(int val) {
        return (int) Math.floor(Math.log(val) / Math.log(2.0));
    }

    public void flip(int target) {
        flipped[target] = !flipped[target];
    }

    public boolean isFlipped(int target) {
        return flipped[target];
    }

    public void destroy() {
        for (GlFramebuffer owned : ownedFramebuffers) {
            owned.destroy();
        }

        for (RenderTarget target : targets) {
            if (target != null) {
                target.destroy();
            }
        }

        mainDepth.destroy();
        noTranslucents.destroy();
    }

    public int getRenderTargetCount() {
        return targets.length;
    }

    public RenderTarget get(int index) {
        return targets[index];
    }

    public RenderTarget getOrCreate(int index) {
        if (targets[index] != null) {
            return targets[index];
        }

        create(index);
        return targets[index];
    }

    private void create(int index) {
        if (index >= size) {
            throw new IllegalStateException("Tried to access buffer higher than allowed limit of " + size);
        }

        PackShadowDirectives.SamplingSettings settings = 
            shadowDirectives.getColorSamplingSettings().computeIfAbsent(
                index, i -> new PackShadowDirectives.SamplingSettings()
            );
        
        targets[index] = new RenderTarget.Builder()
            .setDimensions(resolution, resolution)
            .setInternalFormat(settings.getFormat())
            .setPixelFormat(settings.getFormat().getPixelFormat())
            .setPixelType(PixelType.UNSIGNED_BYTE)
            .setName("shadowcolor" + index)
            .build();
        
        formats[index] = settings.getFormat();
        
        if (settings.getClear()) {
            buffersToBeCleared.add(index);
        }

        fullClearRequired = true;
    }

    public void createIfEmpty(int index) {
        if (targets[index] == null) {
            create(index);
        }
    }

    public int getResolution() {
        return resolution;
    }

    public DepthTexture getDepthTexture() {
        return mainDepth;
    }

    public DepthTexture getDepthTextureNoTranslucents() {
        return noTranslucents;
    }

    public GlFramebuffer getDepthSourceFb() {
        return depthSourceFb;
    }

    public void copyPreTranslucentDepth() {
        if (translucentDepthDirty) {
            translucentDepthDirty = false;
            // Use blit framebuffer for initial copy
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthSourceFb.getId());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, noTranslucentsDestFb.getId());
            GL30.glBlitFramebuffer(
                0, 0, resolution, resolution,
                0, 0, resolution, resolution,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
            );
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } else {
            // Use fastest copy strategy for subsequent copies
            DepthCopyStrategy.fastest(false).copy(
                depthSourceFb, mainDepth.getTextureId(),
                noTranslucentsDestFb, noTranslucents.getTextureId(),
                resolution, resolution
            );
        }
    }

    public boolean isFullClearRequired() {
        return fullClearRequired;
    }

    public void onFullClear() {
        fullClearRequired = false;
    }

    public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
        return createFullFramebuffer(false, drawBuffers);
    }

    public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
        return createFullFramebuffer(true, drawBuffers);
    }

    private GlFramebuffer createEmptyFramebuffer() {
        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);

        framebuffer.addDepthAttachment(mainDepth.getTextureId());

        // OpenGL 3.0+ requires at least one color attachment
        framebuffer.addColorAttachment(0, get(0).getMainTexture());
        framebuffer.noDrawBuffers();

        return framebuffer;
    }

    private ImmutableSet<Integer> invert(ImmutableSet<Integer> base, int[] relevant) {
        ImmutableSet.Builder<Integer> inverted = ImmutableSet.builder();

        for (int i : relevant) {
            if (!base.contains(i)) {
                inverted.add(i);
            }
        }

        return inverted.build();
    }

    public GlFramebuffer createShadowFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);

        GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);
        framebuffer.addDepthAttachment(mainDepth.getTextureId());

        return framebuffer;
    }

    private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

        if (!clearsAlt) {
            stageWritesToMain = invert(ImmutableSet.of(), drawBuffers);
        }

        return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
    }

    public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);
        framebuffer.addDepthAttachment(mainDepth.getTextureId());
        return framebuffer;
    }

    public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
        }

        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);

        int[] actualDrawBuffers = new int[drawBuffers.length];

        for (int i = 0; i < drawBuffers.length; i++) {
            actualDrawBuffers[i] = i;

            int bufferIndex = drawBuffers[i];
            createIfEmpty(bufferIndex);

            if (stageWritesToMain.contains(bufferIndex)) {
                framebuffer.addColorAttachment(i, targets[bufferIndex].getMainTexture());
            } else {
                framebuffer.addColorAttachment(i, targets[bufferIndex].getAltTexture());
            }
        }

        framebuffer.drawBuffers(actualDrawBuffers);

        return framebuffer;
    }

    public IntList getBuffersToBeCleared() {
        return buffersToBeCleared;
    }
    
    // Additional helper methods for shadow rendering
    
    /**
     * Gets the main shadow framebuffer for rendering.
     * @return shadow framebuffer
     */
    public GlFramebuffer getShadowFramebuffer() {
        return depthSourceFb;
    }
    
    /**
     * Gets the shadow composite framebuffer.
     * @return composite framebuffer, or null if no composites
     */
    public GlFramebuffer getShadowCompositeFramebuffer() {
        // Return the main framebuffer for composite passes
        return depthSourceFb;
    }
    
    /**
     * Gets depth texture 0 (shadowtex0) ID.
     * @return OpenGL texture ID
     */
    public int getDepthTexture0() {
        return mainDepth != null ? mainDepth.getTextureId() : 0;
    }
    
    /**
     * Gets depth texture 1 (shadowtex1) ID.
     * @return OpenGL texture ID
     */
    public int getDepthTexture1() {
        return noTranslucents != null ? noTranslucents.getTextureId() : 0;
    }
    
    /**
     * Gets a shadow color texture ID.
     * @param index color buffer index (0-7)
     * @return OpenGL texture ID
     */
    public int getColorTexture(int index) {
        if (index >= 0 && index < targets.length && targets[index] != null) {
            return targets[index].getMainTexture();
        }
        return 0;
    }
    
    /**
     * Generates mipmaps for all shadow textures.
     */
    public void generateMipmaps() {
        // Generate mipmaps for depth textures if needed
        if (mainDepth != null && mipped[0]) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainDepth.getTextureId());
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        
        if (noTranslucents != null && mipped[1]) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, noTranslucents.getTextureId());
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        
        // Generate mipmaps for color textures
        for (int i = 0; i < size; i++) {
            if (targets[i] != null && mipped[i]) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, targets[i].getMainTexture());
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
        }
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
