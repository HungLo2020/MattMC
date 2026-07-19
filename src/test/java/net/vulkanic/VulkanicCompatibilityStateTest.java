package net.vulkanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicCompatibilityStateTest {
    @Test
    void graphicsSnapshotCapturesProgramVaoFramebufferAndResourceBindingsImmutably() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(7);
        state.setUniformInt(3, 11);
        state.setUniformFloat(4, 1.0F, 2.0F, 3.0F, 4.0F);

        state.bindVertexArray(5);
        state.bindBuffer(VulkanicBufferTarget.VERTEX, 101);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 24, 8L);
        state.enableVertexAttribArray(0);
        state.bindBuffer(VulkanicBufferTarget.INDEX, 202);

        state.bindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, 9);
        state.framebufferTexture(VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, 303, 0);
        state.setDrawBuffer(VulkanicAPI.GL_COLOR_ATTACHMENT0);

        state.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0 + 2);
        state.bindTexture2D(404);
        state.bindSampler(2, 505);
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 1, 606, 32L, 64L);
        state.setViewport(1, 2, 1280, 720);
        state.setScissor(3, 4, 640, 360);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyIndexed(
                "shared-state-test",
                VulkanicPrimitiveMode.TRIANGLES,
                6,
                VulkanicIndexType.SHORT,
                0L,
                1,
                0
            );
        VulkanicCompatibilityState.GraphicsSnapshot snapshot = state.captureGraphics(request);

        state.bindProgram(8);
        state.setUniformInt(3, 99);
        state.bindVertexArray(6);
        state.bindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, 10);
        state.bindTextureUnit(2, 707);
        state.bindSampler(2, 808);

        assertEquals(7, snapshot.programId());
        assertArrayEquals(new int[] {11}, snapshot.program().uniformsByLocation().get(3).ints());
        assertArrayEquals(new float[] {1.0F, 2.0F, 3.0F, 4.0F}, snapshot.program().uniformsByLocation().get(4).floats());
        assertEquals(5, snapshot.vaoId());
        assertEquals(202, snapshot.vao().elementBuffer());
        assertEquals(101, snapshot.vao().vertexBindings().get(0).buffer());
        assertTrue(snapshot.vao().enabledAttributes().contains(0));
        assertEquals(9, snapshot.drawFramebuffer());
        assertEquals(303, snapshot.framebuffer().attachments().get(VulkanicAPI.GL_COLOR_ATTACHMENT0).texture());
        assertEquals(404, snapshot.texture2DByUnit().get(2));
        assertEquals(505, snapshot.samplerBindings().get(2));
        assertEquals(606, snapshot.indexedBufferBindings()
            .get(new VulkanicCompatibilityState.IndexedBufferKey(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 1))
            .buffer());
        assertTrue(snapshot.fixedFunction().viewport().isPresent());
        assertTrue(snapshot.fixedFunction().scissor().isPresent());
    }

    @Test
    void sharedCompatibilitySnapshotProvidesImmutableVertexAndDescriptorSemantics() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(1);
        state.bindVertexArray(2);
        state.bindBuffer(VulkanicBufferTarget.VERTEX, 10);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 12, 0L);
        state.bindBuffer(VulkanicBufferTarget.INDEX, 20);
        state.bindTextureUnit(3, 30);
        state.bindSampler(3, 40);
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 2, 50, 16L, 32L);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyIndexed(
                "shared-compatibility-snapshot",
                VulkanicPrimitiveMode.TRIANGLES,
                3,
                VulkanicIndexType.SHORT,
                0L,
                1,
                0
            );
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        state.bindTextureUnit(3, 300);
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 2, 500, 0L, 8L);

        assertEquals("frontend-shared-compatibility-draw", snapshot.source());
        assertTrue(snapshot.sharedCompatibilityState().isPresent());
        assertEquals(1, snapshot.sharedCompatibilityState().get().programId());
        assertEquals(2, snapshot.sharedCompatibilityState().get().vaoId());
        assertEquals(1, snapshot.vertexInput().vertexBuffers().size());
        assertEquals("legacy-buffer:10", snapshot.vertexInput().vertexBuffers().get(0).stableKey());
        assertTrue(snapshot.vertexInput().indexBuffer().isPresent());
        assertEquals("legacy-buffer:20", snapshot.vertexInput().indexBuffer().get().stableKey());
        assertFalse(snapshot.descriptorBindings().isEmpty());
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Sampler3")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:30")
                && binding.set().orElse(-1) == 3
                && binding.binding().orElse(-1) == 40));
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Buffer2")
                && binding.resourceUse().resource().stableKey().equals("legacy-buffer:50")));
    }

    @Test
    void disablingSparseVertexAttributeRemovesAttributeIdentityNotListSlot() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindVertexArray(1);
        state.enableVertexAttribArray(0);
        state.enableVertexAttribArray(1);
        state.enableVertexAttribArray(4);

        state.disableVertexAttribArray(4);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "disable-sparse-attribute",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicCompatibilityState.GraphicsSnapshot snapshot = state.captureGraphics(request);
        assertTrue(snapshot.vao().enabledAttributes().contains(0));
        assertTrue(snapshot.vao().enabledAttributes().contains(1));
        assertFalse(snapshot.vao().enabledAttributes().contains(4));
    }

    @Test
    void legacyVertexAttribPointerKeepsPointerAsAttributeRelativeOffsetOnly() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindVertexArray(1);
        state.bindBuffer(VulkanicBufferTarget.VERTEX, 77);
        state.setVertexAttribPointer(0, 4, VulkanicAPI.GL_UNSIGNED_SHORT, false, true, 16, 0L);
        state.setVertexAttribPointer(1, 4, VulkanicAPI.GL_UNSIGNED_BYTE, true, false, 16, 8L);
        state.setVertexAttribPointer(2, 4, VulkanicAPI.GL_UNSIGNED_BYTE, true, true, 16, 12L);
        state.enableVertexAttribArray(0);
        state.enableVertexAttribArray(1);
        state.enableVertexAttribArray(2);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "legacy-packed-vertex-offsets",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicCompatibilityState.VaoSnapshot vao = state.captureGraphics(request).vao();

        assertEquals(0L, vao.vertexBindings().get(0).offset());
        assertEquals(0L, vao.vertexBindings().get(1).offset());
        assertEquals(0L, vao.vertexBindings().get(2).offset());
        assertEquals(0, vao.attributes().get(0).relativeOffset());
        assertEquals(8, vao.attributes().get(1).relativeOffset());
        assertEquals(12, vao.attributes().get(2).relativeOffset());
    }

    @Test
    void imageUnitBindingsAreCapturedAsImmutableStorageImageSemantics() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(9);
        state.bindImageTexture(7, 700, 2, false, 3, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "storage-image-capture",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        state.bindImageTexture(7, 701, 0, true, 0, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_R32F);
        state.deleteTexture(700);

        VulkanicCompatibilityState.ImageUnitBindingState captured =
            snapshot.sharedCompatibilityState().orElseThrow().imageUnitBindings().get(7);
        assertEquals(700, captured.texture());
        assertEquals(2, captured.level());
        assertFalse(captured.layered());
        assertEquals(3, captured.layer());
        assertEquals(VulkanicAPI.GL_READ_WRITE, captured.access());
        assertEquals(VulkanicAPI.GL_RGBA8, captured.format());
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Image7")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:700")
                && binding.resourceUse().subresource().baseMipLevel() == 2
                && binding.resourceUse().subresource().baseLayer() == 3));
    }

    @Test
    void computeCompatibilitySnapshotCapturesProgramResourcesAndIndirectBufferImmutably() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(13);
        state.bindTextureUnit(1, 110);
        state.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0 + 1);
        state.bindTexture(VulkanicAPI.GL_TEXTURE_3D, 112);
        state.bindSampler(1, 111);
        state.bindImageTexture(2, 220, 0, true, 0, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);
        state.bindBuffer(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER, 330);
        state.bindBufferRange(VulkanicBufferTarget.SHADER_STORAGE.toLegacyGlTarget(), 4, 440, 64L, 128L);

        VulkanicGalExecutionRequest.ComputeDispatchRequest request =
            VulkanicGalExecutionRequest.ComputeDispatchRequest.legacyIndirect("compute-capture", 16L);
        VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        state.bindProgram(14);
        state.bindTextureUnit(1, 210);
        state.bindSampler(1, 211);
        state.bindImageTexture(2, 221, 1, false, 2, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_R32F);
        state.bindBuffer(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER, 331);
        state.bindBufferRange(VulkanicBufferTarget.SHADER_STORAGE.toLegacyGlTarget(), 4, 441, 0L, 8L);

        VulkanicCompatibilityState.ComputeSnapshot captured = snapshot.sharedCompatibilityState().orElseThrow();
        assertEquals(13, captured.programId());
        assertEquals(110, captured.texture2DByUnit().get(1));
        assertEquals(112, captured.textureBindingsByKey()
            .get(new VulkanicCompatibilityState.TextureBindingKey(1, VulkanicAPI.GL_TEXTURE_3D)));
        assertEquals(111, captured.samplerBindings().get(1));
        assertEquals(220, captured.imageUnitBindings().get(2).texture());
        assertEquals(330, captured.bufferBindings().get(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER));
        assertEquals(440, captured.indexedBufferBindings()
            .get(new VulkanicCompatibilityState.IndexedBufferKey(VulkanicBufferTarget.SHADER_STORAGE.toLegacyGlTarget(), 4))
            .buffer());
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Sampler1")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:110")));
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Image2")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:220")));
    }

    @Test
    void fixedFunctionClosureCapturesLogicOpPolygonModeAndPolygonOffset() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.setLogicOpEnabled(true);
        state.setLogicOp(VulkanicAPI.GL_OR_REVERSE);
        state.setPolygonMode(VulkanicAPI.GL_FRONT_AND_BACK, VulkanicAPI.GL_LINE);
        state.setPolygonOffsetEnabled(true);
        state.setPolygonOffset(1.25F, 2.5F);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "fixed-function-capture",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicCompatibilityState.FixedFunctionSnapshot snapshot = state.captureGraphics(request).fixedFunction();

        state.setLogicOpEnabled(false);
        state.setLogicOp(0x1503);
        state.setPolygonMode(VulkanicAPI.GL_FRONT_AND_BACK, VulkanicAPI.GL_FILL);
        state.setPolygonOffsetEnabled(false);
        state.setPolygonOffset(0.0F, 0.0F);

        assertTrue(snapshot.logicOpEnabled());
        assertEquals(VulkanicAPI.GL_OR_REVERSE, snapshot.logicOp());
        assertEquals(VulkanicAPI.GL_FRONT_AND_BACK, snapshot.polygonFace());
        assertEquals(VulkanicAPI.GL_LINE, snapshot.polygonMode());
        assertTrue(snapshot.polygonOffsetEnabled());
        assertEquals(1.25F, snapshot.polygonOffsetFactor());
        assertEquals(2.5F, snapshot.polygonOffsetUnits());
    }

    @Test
    void fixedFunctionSnapshotUsesOpenGlDefaultBackFaceCullingMode() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "default-cull-face-mode",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicCompatibilityState.FixedFunctionSnapshot snapshot = state.captureGraphics(request).fixedFunction();

        assertFalse(snapshot.cullEnabled());
        assertEquals(VulkanicAPI.GL_BACK, snapshot.cullFaceMode());
    }

    @Test
    void resourceDeletionAndReplacementInvalidateSharedSemanticReferencesWithoutChangingCapturedRequests() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindVertexArray(1);
        state.bindBuffer(VulkanicBufferTarget.VERTEX, 100);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 12, 0L);
        state.enableVertexAttribArray(0);
        state.bindTextureUnit(0, 200);
        state.bindSampler(0, 300);
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 4, 400, 0L, 16L);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "delete-replacement-snapshot",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot beforeDeletion = state.compatibilitySnapshotFor(request);

        state.deleteTexture(200);
        state.deleteBuffer(100);
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 4, 401, 0L, 16L);
        state.bindTextureUnit(0, 201);

        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot afterReplacement = state.compatibilitySnapshotFor(request);

        assertTrue(beforeDeletion.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-texture:200")));
        assertTrue(beforeDeletion.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-buffer:400")));
        assertEquals("legacy-buffer:100", beforeDeletion.vertexInput().vertexBuffers().get(0).stableKey());
        assertTrue(afterReplacement.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-texture:201")));
        assertTrue(afterReplacement.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-buffer:401")));
        assertTrue(afterReplacement.vertexInput().vertexBuffers().isEmpty());
    }
}
