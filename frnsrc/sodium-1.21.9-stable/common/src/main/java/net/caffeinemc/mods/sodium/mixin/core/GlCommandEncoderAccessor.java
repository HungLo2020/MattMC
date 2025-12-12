package frnsrc.sodium;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlCommandEncoder.class)
public interface GlCommandEncoderAccessor {
    @Invoker("applyPipelineState")
    void sodium$applyPipelineState(RenderPipeline pipeline);

    @Accessor("lastProgram")
    void sodium$setLastProgram(GlProgram program);
}
