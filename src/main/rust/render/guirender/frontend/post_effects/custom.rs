//! Recording of custom shader-pack post-effect chains into the GUI frame.

use super::*;

impl GuiFrontend {
    /// A cancelled transaction cannot confirm the uploads/layouts recorded
    /// while building post-effect commands. Retire their private resources
    /// through GAL so the next frame starts from new, explicitly undefined
    /// images rather than guessing which part of a failed frame executed.
    pub(crate) fn discard_prepared_post_effects(&mut self, gal: &mut VulkanicGal) {
        self.clear_frame_pass(gal);
        if let Some(resources) = self.blur_resources.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        for resources in std::mem::take(&mut self.custom_post_effect_resources) {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        for intermediate in std::mem::take(&mut self.custom_post_effect_intermediates).into_values()
        {
            for handle in intermediate.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(resources) = self.custom_post_effect_depth_target.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.blur_snapshot_initialized = false;
        self.custom_post_effect_snapshot_initialized.clear();
        self.custom_post_effect_image_initialized.clear();
        self.creeper_intermediate_initialized = false;
        self.spider_initialized = [false; 4];
    }

    /// Executes a bounded resource-pack post-effect chain. Main-target reads
    /// are snapshotted; each declared private target is a Rust-owned
    /// generation-bound intermediate. Each pass may carry bounded static
    /// uniform blocks; dynamic uniform writers and feedback graphs remain
    /// unavailable until their explicit contracts exist.
    pub(crate) fn append_custom_post_effect(
        &mut self,
        gal: &mut VulkanicGal,
        render_target: Handle,
        color_attachment: Handle,
        identity: &str,
        shader_sources: &[CustomPostEffectSource],
    ) -> GalResult<Vec<CommandOp>> {
        self.append_custom_post_effect_with_owned_targets(
            gal,
            render_target,
            color_attachment,
            identity,
            shader_sources,
            None,
        )
        .map(|(ops, _)| ops)
    }

    /// Executes a custom graph with a validated Rust-owned external target
    /// inventory. The legacy wrapper above intentionally supplies no
    /// inventory, keeping external roles unavailable until the frame
    /// coordinator has installed the complete attachment set.
    #[cfg(test)]
    pub(crate) fn append_custom_post_effect_with_external_targets(
        &mut self,
        gal: &mut VulkanicGal,
        render_target: Handle,
        color_attachment: Handle,
        identity: &str,
        shader_sources: &[CustomPostEffectSource],
        external_targets: Option<&VanillaPostEffectExternalTargetBindings>,
    ) -> GalResult<Vec<CommandOp>> {
        self.append_custom_post_effect_with_owned_targets(
            gal,
            render_target,
            color_attachment,
            identity,
            shader_sources,
            external_targets,
        )
        .map(|(ops, _)| ops)
    }

    /// Same graph as above, additionally returning the private GUI-owned
    /// render targets its passes write besides `render_target`: declared
    /// intermediates and the color-only depth execution target. External
    /// bindings are never reported; they are not GUI-owned intermediates.
    pub(crate) fn append_custom_post_effect_with_owned_targets(
        &mut self,
        gal: &mut VulkanicGal,
        render_target: Handle,
        color_attachment: Handle,
        identity: &str,
        shader_sources: &[CustomPostEffectSource],
        external_targets: Option<&VanillaPostEffectExternalTargetBindings>,
    ) -> GalResult<(Vec<CommandOp>, Vec<Handle>)> {
        if shader_sources.is_empty() || shader_sources.len() > MAX_CUSTOM_POST_EFFECT_PASSES {
            return Err(GalError::unsupported_feature(
                format!(
                    "custom post-effect pass count must be in the bounded range 1..={MAX_CUSTOM_POST_EFFECT_PASSES}"
                ),
            ));
        }
        let graph_uniform_bytes = shader_sources
            .iter()
            .flat_map(|source| source.uniform_blocks.iter())
            .try_fold(0usize, |total, block| total.checked_add(block.len()))
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "custom post-effect graph uniform byte count overflowed the bounded contract",
                )
            })?;
        if graph_uniform_bytes > MAX_CUSTOM_POST_EFFECT_UNIFORM_GRAPH_BYTES {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect graph uniform bytes exceed bounded limit {MAX_CUSTOM_POST_EFFECT_UNIFORM_GRAPH_BYTES}"
            )));
        }
        let extent = gal.pass_target_extent(render_target)?;
        let color_format = gal.pass_target_color_format(render_target)?;
        let mut intermediate_names = BTreeMap::<String, ()>::new();
        for source in shader_sources {
            if source.input_bilinear.len() != source.input_count {
                return Err(GalError::invalid_argument(
                    "custom post-effect filter inventory does not match input count",
                ));
            }
            if source
                .sampler_info_uniform
                .is_some_and(|index| index >= source.uniform_blocks.len())
            {
                return Err(GalError::invalid_argument(
                    "post-effect SamplerInfo binding is outside its uniform inventory",
                ));
            }
            let uniform_bytes = source
                .uniform_blocks
                .iter()
                .try_fold(0usize, |total, block| total.checked_add(block.len()))
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "custom post-effect uniform byte count overflowed the bounded contract",
                    )
                })?;
            if uniform_bytes > MAX_CUSTOM_POST_EFFECT_UNIFORM_BYTES {
                return Err(GalError::unsupported_feature(format!(
                    "custom post-effect uniform bytes exceed bounded limit {MAX_CUSTOM_POST_EFFECT_UNIFORM_BYTES}"
                )));
            }
            if source.input_images.len() != source.input_count {
                return Err(GalError::unsupported_feature(
                    "custom post-effect image-input inventory does not match its semantic input count",
                ));
            }
            if source.input_use_depth.len() != source.input_count {
                return Err(GalError::unsupported_feature(
                    "custom post-effect depth-input inventory does not match its semantic input count",
                ));
            }
            for target in source
                .input_targets
                .iter()
                .chain(std::iter::once(&source.output_target))
            {
                if !target.is_empty()
                    && target != "minecraft:main"
                    && !external_targets.is_some_and(|targets| targets.get(target).is_some())
                {
                    intermediate_names.insert(target.clone(), ());
                }
            }
        }
        let mut produced_targets = BTreeSet::new();
        for source in shader_sources {
            let invalid_shape = source.input_targets.len() != source.input_count
                || source.input_count == 0
                || source.input_count > 4
                || source.input_targets.iter().enumerate().any(|(input_index, target)| {
                    source.input_images.get(input_index).is_none_or(Option::is_none)
                        && target != "minecraft:main"
                        && !external_targets.is_some_and(|targets| targets.get(target).is_some())
                        && !produced_targets.contains(target)
                })
                // Each pass reads the last completed version of its inputs.
                // Reusing a private output in a later pass is legal; sampling
                // that same private output during its own write is not.
                || (source.output_target != "minecraft:main"
                    && !external_targets.is_some_and(|targets| targets.get(&source.output_target).is_some())
                    && (source.input_targets.iter().enumerate().any(|(input_index, target)| {
                            source.input_images.get(input_index).is_none_or(Option::is_none)
                                && target == &source.output_target
                        })
                        || (!intermediate_names.contains_key(&source.output_target)
                            && !external_targets.is_some_and(|targets| targets.get(&source.output_target).is_some()))
                        ));
            if invalid_shape {
                return Err(GalError::unsupported_feature(
                    "custom post-effect graph has an invalid bounded target contract",
                ));
            }
            for (index, target) in source.input_targets.iter().enumerate() {
                if source.input_use_depth[index]
                    && (source.input_images[index].is_some()
                        || (target != "minecraft:main"
                            && external_targets
                                .and_then(|targets| targets.get(target))
                                .and_then(|binding| binding.depth_attachment)
                                .is_none()))
                {
                    return Err(GalError::unsupported_feature(
                        "custom post-effect named depth input has no matching owned depth attachment",
                    ));
                }
            }
            if source.output_target != "minecraft:main" {
                produced_targets.insert(source.output_target.clone());
            }
        }
        if intermediate_names.len() > MAX_CUSTOM_POST_EFFECT_INTERMEDIATES {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect intermediate target count exceeds bounded limit {MAX_CUSTOM_POST_EFFECT_INTERMEDIATES}"
            )));
        }
        let depth_requested = shader_sources
            .iter()
            .any(|source| source.input_use_depth.iter().any(|uses_depth| *uses_depth));
        let (execution_target, depth_view, depth_texture) = if depth_requested {
            let Some((depth_texture, depth_view)) =
                gal.pass_target_depth_attachment(render_target)?
            else {
                return Err(GalError::unsupported_feature(
                    "custom post-effect depth input requires a Rust-owned render-target depth attachment",
                ));
            };
            let color_view = gal.pass_target_color_attachment(render_target)?;
            let execution_target = if self
                .custom_post_effect_depth_target
                .as_ref()
                .is_some_and(|cached| cached.source == render_target)
            {
                self.custom_post_effect_depth_target
                    .as_ref()
                    .unwrap()
                    .target
            } else {
                if let Some(previous) = self.custom_post_effect_depth_target.take() {
                    for handle in previous.handles_in_destroy_order() {
                        let _ = gal.destroy(handle);
                    }
                }
                let target = gal.create_render_target(RenderTargetDesc {
                    label: format!("minecraft.post-effect.{identity}.color-only-target"),
                    color_views: vec![color_view],
                    depth_stencil_view: None,
                    extent,
                })?;
                self.custom_post_effect_depth_target = Some(CustomPostEffectDepthTarget {
                    source: render_target,
                    target,
                    color_texture: None,
                    color_view,
                    owns_color_view: false,
                    depth_texture: None,
                    depth_view,
                    owns_depth_view: false,
                });
                target
            };
            (execution_target, Some(depth_view), Some(depth_texture))
        } else {
            (render_target, None, None)
        };
        let stale_intermediate_names = self
            .custom_post_effect_intermediates
            .keys()
            .filter(|name| !intermediate_names.contains_key(*name))
            .cloned()
            .collect::<Vec<_>>();
        for target_name in stale_intermediate_names {
            if let Some(previous) = self.custom_post_effect_intermediates.remove(&target_name) {
                for handle in previous.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
            }
        }
        let mut intermediates = BTreeMap::new();
        for target_name in intermediate_names.keys() {
            let handles = self
                .ensure_custom_post_effect_intermediate(
                    gal,
                    identity,
                    target_name,
                    extent.width,
                    extent.height,
                    color_format,
                )?
                .expect("named private post-effect target must be allocated");
            intermediates.insert(target_name.clone(), handles);
        }
        let mut ops = Vec::new();
        let mut owned_targets = Vec::<Handle>::new();
        for (pass_index, source) in shader_sources.iter().enumerate() {
            let input_depth_views = (0..source.input_count)
                .map(|input_index| {
                    if !source.input_use_depth[input_index] {
                        return None;
                    }
                    if source.input_targets[input_index] == "minecraft:main" {
                        depth_view
                    } else {
                        external_targets
                            .and_then(|targets| targets.get(&source.input_targets[input_index]))
                            .and_then(|binding| binding.depth_attachment)
                    }
                })
                .collect::<Vec<_>>();
            let (
                snapshots,
                frame_copy_scratch,
                image_textures,
                image_upload_buffers,
                pipeline,
                pipeline_layout,
                resource_set,
            ) = {
                let resources = self.ensure_custom_post_effect_resources(
                    gal,
                    identity,
                    pass_index,
                    extent.width,
                    extent.height,
                    color_format,
                    &source.vertex_shader,
                    &source.fragment_shader,
                    source.input_count,
                    source.input_row_order,
                    source.input_row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse
                        && render_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget)
                        && source.input_targets.iter().enumerate().any(|(i, name)| {
                            name == "minecraft:main"
                                && !source.input_use_depth[i]
                                && source.input_images[i].is_none()
                        }),
                    &source.input_bilinear,
                    &source.input_targets,
                    &source.input_images,
                    &input_depth_views,
                    &source.output_target,
                    &source.uniform_blocks,
                )?;
                (
                    resources.snapshots.clone(),
                    resources.frame_copy_scratch,
                    resources.image_textures.clone(),
                    resources.image_upload_buffers.clone(),
                    resources.pipeline,
                    resources.pipeline_layout,
                    resources.resource_set,
                )
            };
            let uniform_buffers = self.custom_post_effect_resources[pass_index]
                .uniform_buffers
                .clone();
            let snapshot_initialized =
                self.custom_post_effect_snapshot_initialized[pass_index].clone();
            let image_initialized = self.custom_post_effect_image_initialized[pass_index].clone();
            let (pass_target, pass_color_attachment, pass_handle) =
                if source.output_target == "minecraft:main" {
                    if execution_target != render_target
                        && !owned_targets.contains(&execution_target)
                    {
                        owned_targets.push(execution_target);
                    }
                    (
                        execution_target,
                        color_attachment,
                        self.frame_pass(gal, execution_target, None)?,
                    )
                } else if let Some(binding) =
                    external_targets.and_then(|targets| targets.get(&source.output_target))
                {
                    (
                        binding.render_target,
                        binding.color_attachment,
                        binding.render_pass,
                    )
                } else {
                    let (_, view, target, pass) = intermediates
                        .get(&source.output_target)
                        .copied()
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "custom post-effect intermediate target was not allocated",
                            )
                        })?;
                    if !owned_targets.contains(&target) {
                        owned_targets.push(target);
                    }
                    (target, view, pass)
                };
            let mut source_states = BTreeMap::<String, TextureUsageState>::new();
            let reverse_rows = source.input_row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse;
            let mut scratch_initialized = snapshot_initialized.iter().any(|value| *value);
            for (input_index, source_target_name) in source.input_targets.iter().enumerate() {
                if source
                    .input_images
                    .get(input_index)
                    .is_some_and(Option::is_some)
                    || (source.input_use_depth[input_index] && !reverse_rows)
                {
                    continue;
                }
                let snapshot = snapshots.get(input_index).copied().ok_or_else(|| {
                    GalError::unsupported_feature(
                        "custom post-effect input snapshot was not allocated",
                    )
                })?;
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: snapshot,
                    subresources: None,
                    before: if snapshot_initialized
                        .get(input_index)
                        .copied()
                        .unwrap_or(false)
                    {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    },
                    after: TextureUsageState::TransferDst,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Transfer,
                }));
                if source.input_use_depth[input_index] {
                    let info = gal.texture_view_info(
                        input_depth_views[input_index].ok_or_else(|| {
                            GalError::unsupported_feature(
                                "row-converted depth input has no owned view",
                            )
                        })?,
                    )?;
                    if info.range.base_mip != 0
                        || info.range.mip_count != 1
                        || info.range.base_layer != 0
                        || info.range.layer_count != 1
                        || info.extent != extent
                        || !info.usages.contains(&TextureUsage::TransferSrc)
                    {
                        return Err(GalError::unsupported_feature(
                            "row-converted depth input requires a full-size transferable single-subresource attachment"));
                    }
                    let restore = external_targets
                        .and_then(|targets| targets.get(source_target_name))
                        .and_then(|binding| binding.depth_usage)
                        .unwrap_or(TextureUsageState::DepthStencilAttachment);
                    ops.extend([
                        CommandOp::Barrier(texture_barrier(
                            info.texture,
                            restore,
                            TextureUsageState::TransferSrc,
                        )),
                        CommandOp::CopyTexture(TextureImageCopyRegion {
                            row_order: source.input_row_order,
                            src_texture: info.texture,
                            src_mip: 0,
                            src_layer: 0,
                            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                            dst_texture: snapshot,
                            dst_mip: 0,
                            dst_layer: 0,
                            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                            extent,
                        }),
                        CommandOp::Barrier(texture_barrier(
                            info.texture,
                            TextureUsageState::TransferSrc,
                            restore,
                        )),
                    ]);
                } else if source_target_name == "minecraft:main"
                    && render_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget)
                {
                    let copy_destination = frame_copy_scratch.unwrap_or(snapshot);
                    if let Some(scratch) = frame_copy_scratch {
                        ops.push(CommandOp::Barrier(texture_barrier(
                            scratch,
                            if scratch_initialized {
                                TextureUsageState::TransferSrc
                            } else {
                                TextureUsageState::Undefined
                            },
                            TextureUsageState::TransferDst,
                        )));
                    }
                    ops.push(CommandOp::CopyFrameTargetToTexture {
                        src: render_target,
                        dst: copy_destination,
                        extent,
                    });
                    if let Some(scratch) = frame_copy_scratch {
                        ops.extend([
                            CommandOp::Barrier(texture_barrier(
                                scratch,
                                TextureUsageState::TransferDst,
                                TextureUsageState::TransferSrc,
                            )),
                            CommandOp::CopyTexture(TextureImageCopyRegion {
                                row_order: source.input_row_order,
                                src_texture: scratch,
                                src_mip: 0,
                                src_layer: 0,
                                src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                                dst_texture: snapshot,
                                dst_mip: 0,
                                dst_layer: 0,
                                dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                                extent,
                            }),
                        ]);
                        scratch_initialized = true;
                    }
                } else {
                    let (source_texture, restore_state) = if source_target_name == "minecraft:main"
                    {
                        (
                            gal.pass_target_color_texture(render_target)?,
                            TextureUsageState::ColorAttachment,
                        )
                    } else if let Some(binding) =
                        external_targets.and_then(|targets| targets.get(source_target_name))
                    {
                        (
                            gal.pass_target_color_texture(binding.render_target)?,
                            binding.color_usage,
                        )
                    } else {
                        let (source_texture, _, _, _) = intermediates
                            .get(source_target_name)
                            .copied()
                            .ok_or_else(|| {
                                GalError::unsupported_feature(
                                    "custom post-effect intermediate input was not allocated",
                                )
                            })?;
                        (source_texture, TextureUsageState::ShaderRead)
                    };
                    let before_state = source_states
                        .insert(source_target_name.clone(), TextureUsageState::TransferSrc)
                        .unwrap_or_else(|| {
                            external_targets
                                .and_then(|targets| targets.get(source_target_name))
                                .map(|binding| binding.color_usage)
                                .unwrap_or_else(|| {
                                    self.custom_post_effect_intermediates
                                        .get(source_target_name)
                                        .map(|target| target.usage)
                                        .unwrap_or(TextureUsageState::ColorAttachment)
                                })
                        });
                    ops.extend([
                        CommandOp::Barrier(ResourceBarrier {
                            resource: source_texture,
                            subresources: None,
                            before: before_state,
                            after: TextureUsageState::TransferSrc,
                            src_queue: QueueClass::Graphics,
                            dst_queue: QueueClass::Transfer,
                        }),
                        CommandOp::CopyTexture(TextureImageCopyRegion {
                            row_order: source.input_row_order,
                            src_texture: source_texture,
                            src_mip: 0,
                            src_layer: 0,
                            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                            dst_texture: snapshot,
                            dst_mip: 0,
                            dst_layer: 0,
                            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                            extent,
                        }),
                        CommandOp::Barrier(ResourceBarrier {
                            resource: source_texture,
                            subresources: None,
                            before: TextureUsageState::TransferSrc,
                            after: restore_state,
                            src_queue: QueueClass::Transfer,
                            dst_queue: QueueClass::Graphics,
                        }),
                    ]);
                    source_states.insert(source_target_name.clone(), restore_state);
                    if let Some(target) = self
                        .custom_post_effect_intermediates
                        .get_mut(source_target_name)
                    {
                        target.usage = restore_state;
                    }
                }
            }
            for (input_index, image) in source.input_images.iter().enumerate() {
                let Some(image) = image else { continue };
                if image_initialized.get(input_index).copied().unwrap_or(false) {
                    continue;
                }
                let upload = image_upload_buffers
                    .get(input_index)
                    .and_then(|buffer| *buffer)
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "custom post-effect texture upload buffer was not allocated",
                        )
                    })?;
                let texture = image_textures
                    .get(input_index)
                    .and_then(|texture| *texture)
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "custom post-effect texture resource was not allocated",
                        )
                    })?;
                ops.extend([
                    CommandOp::HostWriteBuffer {
                        buffer: upload,
                        offset: 0,
                        data: image.pixels_rgba8.clone(),
                    },
                    CommandOp::Barrier(buffer_barrier(
                        upload,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    )),
                    CommandOp::Barrier(texture_barrier(
                        texture,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    )),
                    CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                        buffer: upload,
                        buffer_offset: 0,
                        bytes_per_row: image.width * 4,
                        rows_per_image: image.height,
                        texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: image.width,
                            height: image.height,
                            depth: 1,
                        },
                    }),
                    CommandOp::Barrier(texture_barrier(
                        texture,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )),
                ]);
                self.custom_post_effect_image_initialized[pass_index][input_index] = true;
            }
            for (index, (buffer, bytes)) in uniform_buffers
                .iter()
                .zip(source.uniform_blocks.iter())
                .enumerate()
            {
                push_uniform_write(
                    &mut ops,
                    *buffer,
                    if source.sampler_info_uniform == Some(index) {
                        custom_sampler_info_bytes(extent, &source.input_images)
                    } else if bytes.is_empty() {
                        vec![0; 16]
                    } else {
                        bytes.clone()
                    },
                );
            }
            for (input_index, snapshot) in snapshots.iter().enumerate() {
                if source
                    .input_images
                    .get(input_index)
                    .is_some_and(Option::is_some)
                    || (source.input_use_depth[input_index] && !reverse_rows)
                {
                    continue;
                }
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: *snapshot,
                    subresources: None,
                    before: TextureUsageState::TransferDst,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Transfer,
                    dst_queue: QueueClass::Graphics,
                }));
            }
            if let Some(depth_texture) = depth_texture.filter(|_| {
                !reverse_rows && source.input_use_depth.iter().any(|uses_depth| *uses_depth)
            }) {
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: depth_texture,
                    subresources: None,
                    before: TextureUsageState::DepthStencilAttachment,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }));
            }
            for (input_index, uses_depth) in source.input_use_depth.iter().copied().enumerate() {
                if !uses_depth || reverse_rows {
                    continue;
                }
                let target_name = &source.input_targets[input_index];
                let Some(binding) = external_targets.and_then(|targets| targets.get(target_name))
                else {
                    continue;
                };
                let (external_depth_texture, _) = gal
                    .pass_target_depth_attachment(binding.render_target)?
                    .ok_or_else(|| GalError::unsupported_feature(
                        "custom post-effect external depth input has no Rust-owned depth attachment",
                    ))?;
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: external_depth_texture,
                    subresources: None,
                    before: binding
                        .depth_usage
                        .unwrap_or(TextureUsageState::DepthStencilAttachment),
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }));
            }
            if let Some(binding) =
                external_targets.and_then(|targets| targets.get(&source.output_target))
            {
                if binding.color_usage != TextureUsageState::ColorAttachment {
                    let output_texture = gal.pass_target_color_texture(binding.render_target)?;
                    ops.push(CommandOp::Barrier(ResourceBarrier {
                        resource: output_texture,
                        subresources: None,
                        before: binding.color_usage,
                        after: TextureUsageState::ColorAttachment,
                        src_queue: QueueClass::Graphics,
                        dst_queue: QueueClass::Graphics,
                    }));
                }
            }
            if let Some(target) = self
                .custom_post_effect_intermediates
                .get_mut(&source.output_target)
            {
                ops.push(CommandOp::Barrier(texture_barrier(
                    target.texture,
                    target.usage,
                    TextureUsageState::ColorAttachment,
                )));
                target.usage = TextureUsageState::ColorAttachment;
            }
            ops.extend([
                CommandOp::BeginPass {
                    pass: pass_handle,
                    target: pass_target,
                    colors: vec![loaded_frame_color_attachment(pass_color_attachment)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: resource_set,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1,
                },
                CommandOp::EndPass,
            ]);
            for (input_index, uses_depth) in source.input_use_depth.iter().copied().enumerate() {
                if !uses_depth || reverse_rows {
                    continue;
                }
                let target_name = &source.input_targets[input_index];
                let Some(binding) = external_targets.and_then(|targets| targets.get(target_name))
                else {
                    continue;
                };
                let (external_depth_texture, _) = gal
                    .pass_target_depth_attachment(binding.render_target)?
                    .ok_or_else(|| GalError::unsupported_feature(
                        "custom post-effect external depth input has no Rust-owned depth attachment",
                    ))?;
                let restore = binding
                    .depth_usage
                    .unwrap_or(TextureUsageState::DepthStencilAttachment);
                if restore != TextureUsageState::ShaderRead {
                    ops.push(CommandOp::Barrier(ResourceBarrier {
                        resource: external_depth_texture,
                        subresources: None,
                        before: TextureUsageState::ShaderRead,
                        after: restore,
                        src_queue: QueueClass::Graphics,
                        dst_queue: QueueClass::Graphics,
                    }));
                }
            }
            if let Some(depth_texture) = depth_texture.filter(|_| {
                !reverse_rows && source.input_use_depth.iter().any(|uses_depth| *uses_depth)
            }) {
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: depth_texture,
                    subresources: None,
                    before: TextureUsageState::ShaderRead,
                    after: TextureUsageState::DepthStencilAttachment,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }));
            }
            self.custom_post_effect_snapshot_initialized[pass_index].fill(true);
        }
        Ok((ops, owned_targets))
    }
}
