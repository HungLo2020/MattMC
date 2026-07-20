package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
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
        assertFalse(snapshot.textureUnitBindings().containsKey(2));
        assertEquals(505, snapshot.samplerBindings().get(2));
        assertEquals(606, snapshot.indexedBufferBindings()
            .get(new VulkanicCompatibilityState.IndexedBufferKey(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 1))
            .buffer());
        assertTrue(snapshot.fixedFunction().viewport().isPresent());
        assertTrue(snapshot.fixedFunction().scissor().isPresent());
    }

    @Test
    void defaultFramebufferUsesBackBufferRouting() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();

        VulkanicCompatibilityState.GraphicsSnapshot snapshot = state.captureGraphics(
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "default-fbo",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            )
        );

        assertEquals(0, snapshot.drawFramebuffer());
        assertEquals(List.of(VulkanicAPI.GL_BACK), snapshot.framebuffer().drawBuffers());
        assertEquals(VulkanicAPI.GL_BACK, snapshot.framebuffer().readBuffer());
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
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
        assertEquals(30, snapshot.sharedCompatibilityState().get().textureUnitBindings().get(3));
        assertFalse(snapshot.sharedCompatibilityState().get().texture2DByUnit().containsKey(3));
        assertEquals(1, snapshot.vertexInput().vertexBuffers().size());
        assertEquals("legacy-buffer:10", snapshot.vertexInput().vertexBuffers().get(0).stableKey());
        assertTrue(snapshot.vertexInput().indexBuffer().isPresent());
        assertEquals("legacy-buffer:20", snapshot.vertexInput().indexBuffer().get().stableKey());
        assertFalse(snapshot.descriptorBindings().isEmpty());
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Sampler3")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:30")
                && binding.resourceReference().orElseThrow().legacyId().orElseThrow() == 30
                && binding.resourceReference().orElseThrow().bindingUnit().orElseThrow() == 3
                && binding.resourceReference().orElseThrow().legacyTarget().isEmpty()
                && binding.resourceReference().orElseThrow().targetClass() == VulkanicPassResourceModel.TargetClass.UNKNOWN
                && binding.set().orElse(-1) == 3
                && binding.binding().orElse(-1) == 40));
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Buffer2")
                && binding.resourceUse().resource().stableKey().equals("legacy-buffer:50")
                && binding.resourceReference().orElseThrow().subresource().baseMipLevel() == 16
                && binding.resourceReference().orElseThrow().subresource().levelCount() == 32));
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
        int glReadOnly = 0x88B8;
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(9);
        state.bindImageTexture(7, 700, 2, false, 3, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);
        state.bindImageTexture(8, 800, 1, false, 0, glReadOnly, VulkanicAPI.GL_RGBA8);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
                && binding.resourceReference().orElseThrow().bindingKind() == VulkanicPassResourceModel.BindingKind.STORAGE_IMAGE
                && binding.resourceReference().orElseThrow().imageAccess().orElseThrow() == VulkanicAPI.GL_READ_WRITE
                && binding.resourceReference().orElseThrow().imageFormat().orElseThrow() == VulkanicAPI.GL_RGBA8
                && binding.resourceUse().access() == VulkanicPassResourceModel.Access.READ_WRITE
                && binding.resourceUse().subresource().baseMipLevel() == 2
                && binding.resourceUse().subresource().baseLayer() == 3));
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Image8")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:800")
                && binding.resourceUse().access() == VulkanicPassResourceModel.Access.READ
                && binding.resourceUse().usage() == VulkanicResourceUsage.SAMPLED_READ));
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
            VulkanicGalExecutionRequest.ComputeDispatchRequest.indirect("compute-capture", 16L);
        VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        state.bindProgram(14);
        state.bindTextureUnit(1, 210);
        state.bindSampler(1, 211);
        state.bindImageTexture(2, 221, 1, false, 2, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_R32F);
        state.bindBuffer(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER, 331);
        state.bindBufferRange(VulkanicBufferTarget.SHADER_STORAGE.toLegacyGlTarget(), 4, 441, 0L, 8L);

        VulkanicCompatibilityState.ComputeSnapshot captured = snapshot.sharedCompatibilityState().orElseThrow();
        assertEquals(13, captured.programId());
        assertFalse(captured.textureUnitBindings().containsKey(1));
        assertFalse(captured.texture2DByUnit().containsKey(1));
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
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:112")
                && binding.resourceReference().orElseThrow().legacyTarget().orElseThrow() == VulkanicAPI.GL_TEXTURE_3D
                && binding.resourceReference().orElseThrow().targetClass() == VulkanicPassResourceModel.TargetClass.TEXTURE_3D));
        assertTrue(snapshot.descriptorBindings().stream()
            .anyMatch(binding -> binding.name().equals("Image2")
                && binding.resourceUse().resource().stableKey().equals("legacy-texture:220")
                && binding.resourceReference().orElseThrow().layered()));
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
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
        VulkanicPassResourceModel.CanonicalResourceReference capturedTexture =
            beforeDeletion.descriptorBindings().stream()
                .map(VulkanicPassResourceModel.BindingSnapshot::resourceReference)
                .filter(reference -> reference.isPresent()
                    && reference.get().resource().stableKey().equals("legacy-texture:200"))
                .map(reference -> reference.get())
                .findFirst()
                .orElseThrow();
        VulkanicPassResourceModel.CanonicalResourceReference capturedBuffer =
            beforeDeletion.descriptorBindings().stream()
                .map(VulkanicPassResourceModel.BindingSnapshot::resourceReference)
                .filter(reference -> reference.isPresent()
                    && reference.get().resource().stableKey().equals("legacy-buffer:400"))
                .map(reference -> reference.get())
                .findFirst()
                .orElseThrow();
        assertEquals(0L, capturedTexture.generation());
        assertEquals(0L, capturedBuffer.generation());
        assertEquals(200, capturedTexture.legacyId().orElseThrow());
        assertEquals(400, capturedBuffer.legacyId().orElseThrow());
        assertEquals("legacy-buffer:100", beforeDeletion.vertexInput().vertexBuffers().get(0).stableKey());
        assertTrue(afterReplacement.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-texture:201")));
        assertTrue(afterReplacement.descriptorBindings().stream()
            .anyMatch(binding -> binding.resourceUse().resource().stableKey().equals("legacy-buffer:401")));
        assertTrue(afterReplacement.vertexInput().vertexBuffers().isEmpty());
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(beforeDeletion));
        state.validateResourceGenerations(afterReplacement);
    }

    @Test
    void storageReplacementAdvancesBufferGenerationForCapturedRanges() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindBuffer(VulkanicBufferTarget.UNIFORM, 910);
        state.markBoundBufferStorageReplaced(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget());
        state.bindBufferRange(VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), 0, 910, 16L, 32L);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "buffer-generation",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        VulkanicPassResourceModel.CanonicalResourceReference capturedBuffer =
            snapshot.descriptorBindings().stream()
                .map(VulkanicPassResourceModel.BindingSnapshot::resourceReference)
                .filter(reference -> reference.isPresent()
                    && reference.get().resource().stableKey().equals("legacy-buffer:910"))
                .map(reference -> reference.get())
                .findFirst()
                .orElseThrow();

        assertEquals(1L, capturedBuffer.generation());
        state.validateResourceGenerations(snapshot);
        state.markBufferStorageReplaced(910);
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(snapshot));
    }

    @Test
    void textureStorageReplacementAdvancesSampledTextureGeneration() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindTextureUnit(2, 920);
        state.markTextureStorageReplaced(920);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "texture-generation",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        VulkanicPassResourceModel.CanonicalResourceReference capturedTexture =
            snapshot.descriptorBindings().stream()
                .map(VulkanicPassResourceModel.BindingSnapshot::resourceReference)
                .filter(reference -> reference.isPresent()
                    && reference.get().resource().stableKey().equals("legacy-texture:920"))
                .map(reference -> reference.get())
                .findFirst()
                .orElseThrow();

        assertEquals(1L, capturedTexture.generation());
        state.validateResourceGenerations(snapshot);
        state.markTextureStorageReplaced(920);
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(snapshot));
    }

    @Test
    void transferSnapshotCapturesBoundTextureGenerationAndPixelStoreImmutably() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.setActiveTextureUnit(VulkanicAPI.GL_TEXTURE0);
        state.bindTexture(VulkanicAPI.GL_TEXTURE_2D, 77);
        state.setPixelStore(VulkanicAPI.GL_UNPACK_ROW_LENGTH, 16);
        state.setPixelStore(VulkanicAPI.GL_UNPACK_SKIP_ROWS, 2);
        state.setPixelStore(VulkanicAPI.GL_UNPACK_SKIP_PIXELS, 3);
        state.setPixelStore(VulkanicAPI.GL_UNPACK_ALIGNMENT, 8);

        VulkanicGalExecutionRequest.TransferRequest request =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "uploadTexture2D",
                new VulkanicGalExecutionRequest.UploadTexture2D(
                    VulkanicAPI.GL_TEXTURE_2D,
                    4,
                    VulkanicAPI.GL_RGBA8,
                    8,
                    8,
                    0,
                    VulkanicAPI.GL_RGBA,
                    VulkanicAPI.GL_UNSIGNED_BYTE,
                    java.nio.ByteBuffer.allocateDirect(16)
                ),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "legacy-bound-texture-target:" + VulkanicAPI.GL_TEXTURE_2D + ":level:4",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicResourceUsage.TRANSFER_DST
            );

        VulkanicGalExecutionRequest.TransferCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);
        state.markTextureStorageReplaced(77);

        VulkanicPassResourceModel.CanonicalResourceReference destination = snapshot.destination(0);
        assertEquals(77, destination.legacyId().orElseThrow());
        assertEquals(VulkanicAPI.GL_TEXTURE_2D, destination.legacyTarget().orElseThrow());
        assertEquals(4, destination.subresource().baseMipLevel());
        assertEquals(0L, destination.generation());
        assertEquals(16, snapshot.pixelStore().unpackRowLength());
        assertEquals(2, snapshot.pixelStore().unpackSkipRows());
        assertEquals(3, snapshot.pixelStore().unpackSkipPixels());
        assertEquals(8, snapshot.pixelStore().unpackAlignment());
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(snapshot));
    }

    @Test
    void transferSnapshotCapturesBufferCopyGenerationsAndRanges() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindBuffer(VulkanicAPI.GL_COPY_READ_BUFFER, 10);
        state.bindBuffer(VulkanicAPI.GL_COPY_WRITE_BUFFER, 20);

        VulkanicGalExecutionRequest.TransferRequest request =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "copyBufferSubData",
                new VulkanicGalExecutionRequest.CopyBufferSubData(
                    VulkanicAPI.GL_COPY_READ_BUFFER,
                    VulkanicAPI.GL_COPY_WRITE_BUFFER,
                    32L,
                    96L,
                    128L
                ),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_SOURCE,
                "legacy-copy-buffer-targets",
                VulkanicPassResourceModel.Access.READ_WRITE,
                VulkanicResourceUsage.TRANSFER_SRC
            );

        VulkanicGalExecutionRequest.TransferCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        assertEquals(10, snapshot.source(0).legacyId().orElseThrow());
        assertEquals(VulkanicAPI.GL_COPY_READ_BUFFER, snapshot.source(0).bindingUnit().orElseThrow());
        assertEquals(32, snapshot.source(0).subresource().baseMipLevel());
        assertEquals(128, snapshot.source(0).subresource().levelCount());
        assertEquals(20, snapshot.destination(0).legacyId().orElseThrow());
        assertEquals(VulkanicAPI.GL_COPY_WRITE_BUFFER, snapshot.destination(0).bindingUnit().orElseThrow());
        assertEquals(96, snapshot.destination(0).subresource().baseMipLevel());
        assertEquals(128, snapshot.destination(0).subresource().levelCount());

        state.markBufferStorageReplaced(20);
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(snapshot));
    }

    @Test
    void transferCaptureFreezesUnpackBufferSourceAndRejectsStaleGeneration() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindBuffer(VulkanicAPI.GL_PIXEL_UNPACK_BUFFER, 50);
        state.bindTexture(VulkanicAPI.GL_TEXTURE_2D, 60);

        VulkanicGalExecutionRequest.TransferRequest request =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "uploadTexture2DSubImage-address",
                new VulkanicGalExecutionRequest.UploadTexture2DSubImagePointer(
                    VulkanicAPI.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    4,
                    4,
                    VulkanicAPI.GL_RGBA,
                    VulkanicAPI.GL_UNSIGNED_BYTE,
                    64L
                ),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "legacy-bound-texture-target:" + VulkanicAPI.GL_TEXTURE_2D + ":level:0",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicResourceUsage.TRANSFER_DST
            );

        VulkanicGalExecutionRequest.TransferCompatibilitySnapshot snapshot = state.compatibilitySnapshotFor(request);

        assertEquals(50, snapshot.source(0).legacyId().orElseThrow());
        assertEquals(VulkanicAPI.GL_PIXEL_UNPACK_BUFFER, snapshot.source(0).bindingUnit().orElseThrow());
        assertEquals(64, snapshot.source(0).subresource().baseMipLevel());
        assertEquals(60, snapshot.destination(0).legacyId().orElseThrow());

        state.markBufferStorageReplaced(50);
        assertThrows(IllegalStateException.class, () -> state.validateResourceGenerations(snapshot));
    }
}
