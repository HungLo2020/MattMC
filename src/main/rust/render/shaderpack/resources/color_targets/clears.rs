//! Cached generation-owned passes for frame-start source color clears.
use super::*;
use std::sync::Arc;

/// Copied environmental clear values; explicit pack colors take precedence.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct ShaderPackColorClearValues {
    pub fog_color: ClearColor,
}

#[derive(Clone, Debug)]
struct ColorClearPass {
    name: String,
    previous: bool,
    texture: Handle,
    view: Handle,
    target: Handle,
    pass: Handle,
}

/// Clones are borrowed handle snapshots, like the enclosing target set. Only
/// the target cache retires these passes, before their attachment views/images.
#[derive(Clone, Debug)]
pub(super) struct ShaderPackColorClearPasses {
    passes: Arc<[ColorClearPass]>,
}

impl ShaderPackColorClearPasses {
    pub(super) fn create(
        gal: &mut VulkanicGal,
        identity: &ShaderPackColorTargetIdentity,
        targets: &BTreeMap<String, ShaderPackColorTarget>,
        created: &mut Vec<Handle>,
    ) -> GalResult<Self> {
        let mut passes = Vec::new();
        for (name, color) in targets {
            let mut add = |previous, texture, view| -> GalResult<()> {
                let side = if previous { "previous" } else { "current" };
                let label = format!(
                    "shader-pack-color-clear.world{}-pack{}.{}.{side}",
                    identity.world_generation, identity.shader_pack_generation, name
                );
                let target = gal.create_render_target(RenderTargetDesc {
                    label: format!("{label}.target"),
                    color_views: vec![view],
                    depth_stencil_view: None,
                    extent: identity.extent,
                })?;
                created.push(target);
                let pass = gal.create_render_pass(RenderPassDesc {
                    label: format!("{label}.pass"),
                    target,
                    color_formats: vec![color.format],
                    depth_format: None,
                })?;
                created.push(pass);
                passes.push(ColorClearPass {
                    name: name.clone(),
                    previous,
                    texture,
                    view,
                    target,
                    pass,
                });
                Ok(())
            };
            add(false, color.current_texture, color.current_attachment_view)?;
            if let (Some(texture), Some(view)) = (color.previous_texture, color.previous_view) {
                add(
                    true,
                    texture,
                    color.previous_attachment_view.unwrap_or(view),
                )?;
            }
        }
        Ok(Self { passes: passes.into() })
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for clear in self.passes.iter().rev() {
            for handle in [clear.pass, clear.target] {
                let _ = gal.destroy(handle);
            }
        }
    }
}

impl ShaderPackColorFramePlan {
    /// Frozen clears both main/alt sides before begin, even when no subsequent
    /// writer touches a target. Only clear=false history survives a warm frame.
    /// Allocation/semantic confirmation still belongs to the combined submit.
    pub(crate) fn append_frame_start_clears(
        &mut self,
        targets: &ShaderPackColorTargets,
        values: ShaderPackColorClearValues,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.require_targets(targets)?;
        if self.frame_start_clears_recorded {
            return Err(GalError::invalid_argument(
                "source frame-start clears were already recorded",
            ));
        }
        let full_clear = self.requires_initial_clear()?;
        for clear in targets.clear_passes.passes.iter() {
            let declaration = targets.target(&clear.name).expect("cached clear target");
            if !full_clear && !declaration.clear_each_frame {
                continue;
            }
            let state = self
                .targets
                .get_mut(&clear.name)
                .expect("validated frame target");
            let initialized = if clear.previous {
                state.previous_initialized
            } else {
                state.current_initialized
            };
            let barrier = |before, after| ResourceBarrier {
                resource: clear.texture,
                subresources: Some(TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                }),
                before,
                after,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            };
            operations.push(CommandOp::Barrier(barrier(
                if initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::ColorAttachment,
            )));
            operations.push(CommandOp::BeginPass {
                pass: clear.pass,
                target: clear.target,
                colors: vec![PassAttachment {
                    view: clear.view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(source_color_clear_color(
                        declaration.source_slot,
                        declaration.clear_color_bits,
                        values.fog_color,
                    )),
                }],
                depth_stencil: None,
            });
            operations.push(CommandOp::EndPass);
            operations.push(CommandOp::Barrier(barrier(
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
            if clear.previous {
                state.previous_initialized = true;
                state.previous_mipmaps_initialized = false;
            } else {
                state.current_initialized = true;
                state.mipmaps_initialized = false;
                state.current_written_this_frame = true;
            }
        }
        self.frame_start_clears_recorded = true;
        Ok(())
    }
}

pub(crate) fn source_color_clear_color(
    source_slot: u32,
    clear_color_bits: Option<[u32; 4]>,
    fog_color: ClearColor,
) -> ClearColor {
    if source_slot == 0
        && matches!(
            crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_CLEAR_PROBE")
                .ok()
                .as_deref()
                .map(str::trim),
            Some("primary-red")
        )
    {
        return ClearColor {
            r: 1.0,
            g: 0.0,
            b: 0.0,
            a: 1.0,
        };
    }
    if let Some([r, g, b, a]) = clear_color_bits {
        return ClearColor {
            r: f32::from_bits(r),
            g: f32::from_bits(g),
            b: f32::from_bits(b),
            a: f32::from_bits(a),
        };
    }
    match source_slot {
        0 => ClearColor {
            a: 1.0,
            ..fog_color
        },
        1 => ClearColor {
            r: 1.0,
            g: 1.0,
            b: 1.0,
            a: 1.0,
        },
        _ => ClearColor {
            r: 0.0,
            g: 0.0,
            b: 0.0,
            a: 0.0,
        },
    }
}
