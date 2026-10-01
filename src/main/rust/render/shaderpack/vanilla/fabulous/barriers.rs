//! Barrier sequences around the transparency pass and external shader reads.

use super::*;

impl FabulousAttachmentSet {
    /// Emits the explicit state transitions required before the first
    /// transparency fullscreen pass. The caller supplies no backend state:
    /// every resource identity comes from this Rust-owned attachment set.
    pub(crate) fn pre_transparency_barriers(&self) -> Vec<CommandOp> {
        let mut operations = Vec::with_capacity(13);
        for attachment in [
            &self.main,
            &self.translucent,
            &self.item_entity,
            &self.particles,
            &self.clouds,
            &self.weather,
        ] {
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }));
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.depth_texture,
                subresources: None,
                before: TextureUsageState::DepthStencilAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            }));
        }
        operations.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::Undefined,
            after: TextureUsageState::ColorAttachment,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        }));
        operations
    }

    pub(crate) fn between_transparency_pass_barriers(&self) -> Vec<CommandOp> {
        vec![CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::ColorAttachment,
            after: TextureUsageState::ShaderRead,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        })]
    }

    /// Transitions the six producer attachments after their semantic draw
    /// passes have completed.  Keeping this separate from the intermediate
    /// target transition lets the frame coordinator initialize empty families
    /// with a clear pass and still present every declared sampler in a valid
    /// shader-readable state.
    pub(crate) fn external_shader_read_barriers(&self) -> Vec<CommandOp> {
        self.external_shader_read_barriers_with_translucent_state(
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
        )
    }

    /// Variant used when an external producer already left the translucent
    /// color/depth resources in explicit states (for example a transfer copy
    /// from the normal deferred graph). Other Fabulous roles retain the
    /// ordinary raster-to-sample transition contract.
    pub(crate) fn external_shader_read_barriers_with_translucent_state(
        &self,
        translucent_color_before: TextureUsageState,
        translucent_depth_before: TextureUsageState,
    ) -> Vec<CommandOp> {
        self.external_shader_read_barriers_with_input_states(
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
            translucent_color_before,
            translucent_depth_before,
        )
    }

    pub(crate) fn external_shader_read_barriers_with_input_states(
        &self,
        main_color_before: TextureUsageState,
        main_depth_before: TextureUsageState,
        translucent_color_before: TextureUsageState,
        translucent_depth_before: TextureUsageState,
    ) -> Vec<CommandOp> {
        self.external_shader_read_barriers_with_role_states(
            main_color_before,
            main_depth_before,
            translucent_color_before,
            translucent_depth_before,
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ColorAttachment,
            TextureUsageState::DepthStencilAttachment,
        )
    }

    pub(crate) fn external_shader_read_barriers_with_role_states(
        &self,
        main_color_before: TextureUsageState,
        main_depth_before: TextureUsageState,
        translucent_color_before: TextureUsageState,
        translucent_depth_before: TextureUsageState,
        item_entity_color_before: TextureUsageState,
        item_entity_depth_before: TextureUsageState,
        particles_color_before: TextureUsageState,
        particles_depth_before: TextureUsageState,
        clouds_color_before: TextureUsageState,
        clouds_depth_before: TextureUsageState,
        weather_color_before: TextureUsageState,
        weather_depth_before: TextureUsageState,
    ) -> Vec<CommandOp> {
        let mut operations = Vec::with_capacity(12);
        for (attachment, color_before, depth_before) in [
            (&self.main, main_color_before, main_depth_before),
            (
                &self.translucent,
                translucent_color_before,
                translucent_depth_before,
            ),
            (
                &self.item_entity,
                item_entity_color_before,
                item_entity_depth_before,
            ),
            (
                &self.particles,
                particles_color_before,
                particles_depth_before,
            ),
            (&self.clouds, clouds_color_before, clouds_depth_before),
            (&self.weather, weather_color_before, weather_depth_before),
        ] {
            if color_before != TextureUsageState::ShaderRead {
                operations.push(CommandOp::Barrier(ResourceBarrier {
                    resource: attachment.color_texture,
                    subresources: None,
                    before: color_before,
                    after: TextureUsageState::ShaderRead,
                    src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                    dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                }));
            }
            if depth_before != TextureUsageState::ShaderRead {
                operations.push(CommandOp::Barrier(ResourceBarrier {
                    resource: attachment.depth_texture,
                    subresources: None,
                    before: depth_before,
                    after: TextureUsageState::ShaderRead,
                    src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                    dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
                }));
            }
        }
        operations
    }

    pub(crate) fn final_target_color_attachment_barrier(&self) -> CommandOp {
        CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::Undefined,
            after: TextureUsageState::ColorAttachment,
            src_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
            dst_queue: crate::render::vulkanic::resources::QueueClass::Graphics,
        })
    }
}
