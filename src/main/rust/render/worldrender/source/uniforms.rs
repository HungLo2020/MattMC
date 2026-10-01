//! Per-frame source uniform frames, render stages and current item ids.

use super::*;

/// `vulkanic_source_celestial_is_moon` selector values for the owned
/// `gbuffers_skytextured` geometry.
pub(crate) const SOURCE_CELESTIAL_SUN: i32 = 0;

pub(crate) const SOURCE_CELESTIAL_MOON: i32 = 1;

pub(crate) const SOURCE_CELESTIAL_END_SKY: i32 = 2;

impl WorldPrimitiveFrontend {
    /// Returns the fixed semantic texture-coordinate transforms used by the
    /// lowered source terrain ABI. The frontend requires its own atlas asset
    /// before exposing them, but does not create any source resource set or
    /// infer that all source sampler requirements are satisfied.
    pub(crate) fn source_texture_transforms_for_owned_resources(
        &self,
    ) -> GalResult<TerrainSourceTextureTransforms> {
        let atlas = self
            .mesh_texture_assets
            .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected terrain source requires a Rust-owned terrain material atlas",
                )
            })?;
        if atlas.width == 0 || atlas.height == 0 {
            return Err(GalError::invalid_argument(
                "Rust-owned terrain material atlas has zero extent",
            ));
        }
        Ok(TerrainSourceTextureTransforms::canonical_minecraft_terrain())
    }

    /// Combines copied gameplay semantics with resource metadata owned by the
    /// frontend. The source contract sees only the atlas extent; it never sees
    /// the Java asset record or a backend texture identity.
    pub(crate) fn source_uniform_frame_for_owned_resources(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<TerrainSourceUniformFrame> {
        let key = SourceUniformFrameMemoKey {
            frame_id: frame.frame_id,
            world_generation: frame.shader_environment.world_generation,
            frame_time_bits: frame.shader_environment.frame_time_seconds.to_bits(),
            view_bits: frame.view_matrix.map(f32::to_bits),
        };
        if let Some((memo_key, uniforms)) = self.source_uniform_frame_memo.as_ref() {
            if *memo_key == key {
                return Ok(uniforms.clone());
            }
        }
        let uniforms = self.compute_source_uniform_frame_for_owned_resources(frame)?;
        self.source_uniform_frame_memo = Some((key, uniforms.clone()));
        Ok(uniforms)
    }

    pub(crate) fn compute_source_uniform_frame_for_owned_resources(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<TerrainSourceUniformFrame> {
        let atlas = self
            .mesh_texture_assets
            .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected terrain source requires a Rust-owned terrain material atlas",
                )
            })?;
        if atlas.width == 0 || atlas.height == 0 {
            return Err(GalError::invalid_argument(
                "Rust-owned terrain material atlas has zero extent",
            ));
        }
        let mut uniforms = frame.source_uniform_frame()?;
        let width = i32::try_from(atlas.width).map_err(|_| {
            GalError::invalid_argument(
                "Rust-owned terrain material atlas width exceeds source ivec2",
            )
        })?;
        let height = i32::try_from(atlas.height).map_err(|_| {
            GalError::invalid_argument(
                "Rust-owned terrain material atlas height exceeds source ivec2",
            )
        })?;
        uniforms.material_atlas_size = Some([width, height]);
        if frame.shader_environment.enabled {
            let shader_pack_generation = self
                .shader_pack_sources
                .active()
                .map_or(0, |source| source.generation());
            uniforms.rain_factor = Some(self.source_temporal_uniforms.rain_factor(
                TerrainSourceTemporalKey {
                    world_generation: frame.shader_environment.world_generation,
                    shader_pack_generation,
                },
                frame.frame_id,
                frame.shader_environment.frame_time_seconds,
                frame.shader_environment.rain_strength,
            )?);
            let (biome_dry, biome_rainy, biome_snowy) =
                self.source_temporal_uniforms.biome_climate(
                    TerrainSourceTemporalKey {
                        world_generation: frame.shader_environment.world_generation,
                        shader_pack_generation,
                    },
                    frame.frame_id,
                    frame.shader_environment.frame_time_seconds,
                    frame.shader_environment.biome_precipitation,
                )?;
            uniforms.biome_dry = Some(biome_dry);
            uniforms.biome_rainy = Some(biome_rainy);
            uniforms.biome_snowy = Some(biome_snowy);
            let custom_uniform_policy = self
                .shader_pack_sources
                .active_custom_uniform_policy()
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "enabled shader environment requires a generation-matched custom-uniform policy",
                    )
                })?;
            if custom_uniform_policy.generation() != shader_pack_generation {
                return Err(GalError::invalid_argument(
                    "shader-pack source and custom-uniform policy generations differ",
                ));
            }
            // `inPaleGarden` is a copied gameplay predicate, not an Iris
            // uniform object. Evaluate it from the canonical biome identity
            // whether or not the active pack happens to declare the role.
            // Keeping the value populated also preserves the source ABI for
            // packs that use the custom uniform directly.
            uniforms.biome_pale_garden = Some(TerrainSourceTemporalUniforms::pale_garden_biome(
                &frame.shader_environment.biome_resource_location,
            ));
            let wetness_policy = self
                .shader_pack_sources
                .active_wetness_policy()
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "enabled shader environment requires a generation-matched wetness policy",
                    )
                })?;
            if wetness_policy.generation() != shader_pack_generation {
                return Err(GalError::invalid_argument(
                    "shader-pack source and wetness policy generations differ",
                ));
            }
            uniforms.wetness = Some(self.source_temporal_uniforms.wetness(
                TerrainSourceTemporalKey {
                    world_generation: frame.shader_environment.world_generation,
                    shader_pack_generation,
                },
                frame.frame_id,
                frame.shader_environment.frame_time_seconds,
                frame.shader_environment.rain_strength,
                wetness_policy.wetness_half_life_seconds(),
                wetness_policy.dryness_half_life_seconds(),
            )?);
            let nether_biomes = self.source_temporal_uniforms.nether_biomes(
                TerrainSourceTemporalKey {
                    world_generation: frame.shader_environment.world_generation,
                    shader_pack_generation,
                },
                frame.frame_id,
                frame.shader_environment.frame_time_seconds,
                &frame.shader_environment.biome_resource_location,
            )?;
            uniforms.biome_nether_wastes = Some(nether_biomes[0]);
            uniforms.biome_crimson_forest = Some(nether_biomes[1]);
            uniforms.biome_warped_forest = Some(nether_biomes[2]);
            uniforms.biome_basalt_deltas = Some(nether_biomes[3]);
            uniforms.biome_soul_valley = Some(nether_biomes[4]);
            let item_id_map = self
                .shader_pack_sources
                .active_item_id_map()
                .ok_or_else(|| {
                    GalError::invalid_argument(
                    "enabled shader environment requires a generation-matched item identity map",
                )
                })?;
            if item_id_map.generation() != shader_pack_generation {
                return Err(GalError::invalid_argument(
                    "shader-pack source and item identity map generations differ",
                ));
            }
            let held_light_policy = self
                .shader_pack_sources
                .active_held_light_policy()
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "enabled shader environment requires a generation-matched held-light policy",
                    )
                })?;
            if held_light_policy.generation() != shader_pack_generation {
                return Err(GalError::invalid_argument(
                    "shader-pack source and held-light policy generations differ",
                ));
            }
            uniforms.held_item_id_main = Some(
                item_id_map.resolve_optional(
                    &frame
                        .shader_environment
                        .main_hand_item_model_resource_location,
                )?,
            );
            uniforms.held_item_id_off_hand = Some(
                item_id_map.resolve_optional(
                    &frame
                        .shader_environment
                        .off_hand_item_model_resource_location,
                )?,
            );
            let [main_hand_light, off_hand_light] = held_light_policy.compose(
                frame.shader_environment.main_hand_item_light_emission,
                frame.shader_environment.off_hand_item_light_emission,
            )?;
            uniforms.held_block_light_main = Some(main_hand_light);
            uniforms.held_block_light_off_hand = Some(off_hand_light);

            let temporal_key = TerrainSourceTemporalKey {
                world_generation: frame.shader_environment.world_generation,
                shader_pack_generation,
            };
            uniforms.frame_time_smooth = Some(self.source_temporal_uniforms.frame_time_smooth(
                temporal_key,
                frame.frame_id,
                frame.shader_environment.frame_time_seconds,
            )?);
            // Some bounded source candidates only use non-temporal uniforms.
            // Keep their existing preparation available; a source that names
            // previous-frame values still fails explicitly when packing unless
            // the frame supplied a semantic camera mapping.
            if let Some(camera_world_position) = uniforms.camera_world_position {
                let history = self.source_temporal_uniforms.camera_history(
                    temporal_key,
                    frame.frame_id,
                    camera_world_position,
                    frame.view_matrix,
                    frame.projection_matrix,
                )?;
                uniforms.previous_camera_world_position =
                    Some(history.previous_camera_world_position);
                uniforms.previous_view_matrix = Some(history.previous_view_matrix);
                uniforms.previous_projection_matrix = Some(history.previous_projection_matrix);
                uniforms.camera_velocity = Some(
                    camera_world_position
                        .iter()
                        .zip(history.previous_camera_world_position)
                        .map(|(current, previous)| (current - previous).powi(2))
                        .sum::<f32>()
                        .sqrt(),
                );
            }
            let (eye_brightness_m, eye_brightness_m2) =
                self.source_temporal_uniforms.eye_brightness(
                    temporal_key,
                    frame.frame_id,
                    frame.shader_environment.frame_time_seconds,
                    frame.shader_environment.eye_brightness,
                )?;
            uniforms.eye_brightness_m = Some(eye_brightness_m);
            uniforms.eye_brightness_m2 = Some(eye_brightness_m2);

            if let Some(shadow_policy) = self.shader_pack_sources.active_shadow_policy() {
                if shadow_policy.generation() != shader_pack_generation {
                    return Err(GalError::invalid_argument(
                        "shader-pack source and shadow policy generations differ",
                    ));
                }
                uniforms.celestial_sun_path_rotation =
                    Some(shadow_policy.sun_path_rotation_degrees());
                let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "source shadow matrices require an explicit terrain program scope",
                        )
                    })?;
                let camera_world_position = uniforms.camera_world_position.ok_or_else(|| {
                    GalError::invalid_argument(
                        "source shadow matrices require an enabled semantic camera mapping",
                    )
                })?;
                let shadow = shadow_policy.uniforms_with_end_flash(
                    scope,
                    frame.shader_environment.time_of_day,
                    camera_world_position,
                    Some([
                        frame.background.sky.end_flash_x_angle,
                        frame.background.sky.end_flash_y_angle,
                    ]),
                )?;
                uniforms.shadow_model_view = Some(shadow.model_view);
                uniforms.shadow_model_view_inverse = Some(shadow.model_view_inverse);
                uniforms.shadow_projection = Some(shadow.projection);
                uniforms.shadow_projection_inverse = Some(shadow.projection_inverse);
            }
        }
        Ok(uniforms)
    }

    /// Derives the source-declared shadow-scene transform for the private
    /// puddle field from copied camera semantics and the parsed pack policy.
    /// The result matches `shadowModelViewInverse * gl_ModelViewMatrix` in
    /// Complementary's shadow voxelization source, without borrowing an Iris
    /// matrix, program, framebuffer, or renderer object.
    pub(crate) fn candidate_puddle_descriptor_for_frame(
        &self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Option<PuddleOccupancyDescriptor>> {
        let Some(runtime) = self.shader_runtime.as_ref() else {
            return Ok(None);
        };
        if !runtime.candidate_source_requires_resource(TerrainSourceResourceRole::PuddleOccupancy) {
            return Ok(None);
        }
        if !frame.voxel_volume.enabled {
            return Err(GalError::invalid_argument(
                "puddle occupancy requires an enabled semantic world-volume frame",
            ));
        }
        let scope =
            terrain_program_scope_for_sky_type(frame.background.sky_type)?.ok_or_else(|| {
                GalError::unsupported_feature(
                    "puddle occupancy requires an explicit terrain program scope",
                )
            })?;
        let shadow_policy = self
            .shader_pack_sources
            .active_shadow_policy()
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "puddle occupancy requires a parsed source shadow policy",
                )
            })?;
        let shader_pack_generation = runtime.expected_shader_pack_generation_for_resources();
        if shadow_policy.generation() != shader_pack_generation {
            return Err(GalError::invalid_argument(
                "puddle occupancy shadow policy does not match the source generation",
            ));
        }
        if frame.voxel_volume.world_generation == 0 || frame.voxel_volume.resource_generation == 0 {
            return Err(GalError::invalid_argument(
                "puddle occupancy requires non-zero world and resource generations",
            ));
        }
        let camera_world_position = frame.voxel_volume.camera_world_position;
        if camera_world_position.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "puddle occupancy camera world position must be finite",
            ));
        }
        let shadow = shadow_policy.uniforms_with_end_flash(
            scope,
            frame.shader_environment.time_of_day,
            camera_world_position,
            Some([
                frame.background.sky.end_flash_x_angle,
                frame.background.sky.end_flash_y_angle,
            ]),
        )?;
        let camera_fraction =
            camera_world_position.map(|coordinate| coordinate - coordinate.floor());
        Ok(Some(PuddleOccupancyDescriptor {
            shader_pack_generation,
            world_generation: frame.voxel_volume.world_generation,
            resource_generation: frame.voxel_volume.resource_generation,
            camera_fraction,
            shadow_scene_from_world: multiply_column_major_mat4(
                shadow.model_view_inverse,
                frame.view_matrix,
            ),
        }))
    }

    pub(crate) fn source_uniform_frame_for_material_mode(
        &mut self,
        frame: &WorldPrimitiveFrame,
        material_mode: u32,
    ) -> GalResult<TerrainSourceUniformFrame> {
        let mut uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        uniforms.render_stage = Some(self.source_render_stage_for_material_mode(material_mode)?);
        Ok(uniforms)
    }

    /// Adds the copied DH-specific transform semantics required by a lowered
    /// source program. These remain distinct from the near-terrain camera
    /// matrices because source packs declare `dh*` uniforms independently.
    pub(crate) fn source_uniform_frame_for_distant_horizons(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<TerrainSourceUniformFrame> {
        if !frame.lod_render_frame.rust_route_selected() {
            return Err(GalError::invalid_argument(
                "Distant Horizons source uniforms require an explicitly selected Rust DH route",
            ));
        }
        let mut uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        apply_distant_horizons_source_matrices(&mut uniforms, &frame.lod_render_frame)?;
        // Iris gives DH programs the same `far` as every other program (the
        // vanilla render distance in blocks); packs fade DH in against it
        // (Complementary: smoothstep(far * 0.5, far * 0.7) dithered discard).
        Ok(uniforms)
    }

    /// Resolves the pack-generation's declared render category for one
    /// Rust-owned terrain pass. The source collector copies only scalar macro
    /// definitions, so no active Iris phase or renderer state crosses this
    /// boundary.
    pub(crate) fn source_render_stage_for_material_mode(&self, material_mode: u32) -> GalResult<i32> {
        let define = match material_mode {
            WORLD_MATERIAL_MODE_OPAQUE => "MC_RENDER_STAGE_TERRAIN_SOLID",
            WORLD_MATERIAL_MODE_CUTOUT => "MC_RENDER_STAGE_TERRAIN_CUTOUT",
            WORLD_MATERIAL_MODE_TRANSLUCENT => "MC_RENDER_STAGE_TERRAIN_TRANSLUCENT",
            value => {
                return Err(GalError::unsupported_feature(format!(
                    "source terrain render stage is unsupported for material mode {value}"
                )));
            }
        };
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source terrain render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32(define)
    }

    pub(crate) fn source_weather_render_stage(&self) -> GalResult<i32> {
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source weather render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32("MC_RENDER_STAGE_RAIN_SNOW")
    }

    pub(crate) fn source_cloud_render_stage(&self) -> GalResult<i32> {
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source cloud render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32("MC_RENDER_STAGE_CLOUDS")
    }

    /// Resolves the selected pack's explicit celestial render category. This
    /// comes from copied shader-pack defines, never an Iris render phase.
    pub(crate) fn source_celestial_render_stage(&self, celestial: i32) -> GalResult<i32> {
        // Iris renders the End sky box in its CUSTOM_SKY phase.
        let define = match celestial {
            SOURCE_CELESTIAL_MOON => "MC_RENDER_STAGE_MOON",
            SOURCE_CELESTIAL_END_SKY => "MC_RENDER_STAGE_CUSTOM_SKY",
            _ => "MC_RENDER_STAGE_SUN",
        };
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source celestial render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32(define)
    }

    pub(crate) fn source_celestial_assets_available(&self, end_sky: bool) -> bool {
        let required: &[u32] = if end_sky {
            &[WORLD_MATERIAL_TEXTURE_END_SKY]
        } else {
            &[WORLD_MATERIAL_TEXTURE_SKY_SUN, WORLD_MATERIAL_TEXTURE_SKY_MOON_PHASES]
        };
        required.iter().copied().all(|texture_id| {
            self.mesh_texture_assets
                .get(&texture_id)
                .is_some_and(|asset| asset.animation_generation != 0)
        })
    }

    /// Uses the selected source generation's declared entity-stage value only
    /// when an entity program actually requests `renderStage`. This stays a
    /// pack semantic derived by Rust, not a borrowed Iris phase.
    pub(crate) fn source_entity_render_stage(&self) -> GalResult<i32> {
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "entity source render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32("MC_RENDER_STAGE_ENTITIES")
    }

    /// Resolves the selected pack's declared first-person render category.
    /// This is copied source configuration, not a borrowed Iris render phase.
    pub(crate) fn source_hand_render_stage(&self, translucent: bool) -> GalResult<i32> {
        // Iris draws hands in HAND_SOLID, or HAND_TRANSLUCENT for a hand it
        // classifies as translucent (`isHandTranslucent`).
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "hand source render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32(if translucent {
                "MC_RENDER_STAGE_HAND_TRANSLUCENT"
            } else {
                "MC_RENDER_STAGE_HAND_SOLID"
            })
    }

    /// Iris `currentRenderedItemId` for a world entity draw: an item layer
    /// (`minecraft:item_entity/ground/<ns>/<path>`) resolves its drawn item;
    /// every other draw sees the value Iris restores after each item, 0.
    pub(crate) fn source_entity_current_item_id(&self, mesh_identity: &str) -> GalResult<i32> {
        let Some(item) = mesh_identity
            .strip_prefix("minecraft:item_entity/ground/")
            .and_then(|item| item.split_once('/'))
        else {
            return Ok(0);
        };
        let item_id_map = self
            .shader_pack_sources
            .active_item_id_map()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "rendered item identity requires a generation-matched item identity map",
                )
            })?;
        item_id_map.resolve_rendered(&format!("{}:{}", item.0, item.1))
    }

    /// Resolves the selected pack's current-item ID for one first-person
    /// draw from the copied identity of the item actually drawn, as Iris does.
    pub(in crate::render::worldrender) fn source_hand_current_item_id(
        &self,
        frame: &WorldPrimitiveFrame,
        hand: FirstPersonHand,
        mesh_identity: &str,
    ) -> GalResult<i32> {
        if !frame.shader_environment.enabled {
            return Err(GalError::invalid_argument(
                "current hand item identity requires an enabled shader environment",
            ));
        }
        let (expected_identity, hand_label) = match hand {
            FirstPersonHand::Main => (
                &frame
                    .shader_environment
                    .main_hand_item_model_resource_location,
                "main",
            ),
            FirstPersonHand::Off => (
                &frame
                    .shader_environment
                    .off_hand_item_model_resource_location,
                "off",
            ),
        };
        let canonical_mesh_identity = canonical_resource_location(mesh_identity).map_err(|_| {
            GalError::invalid_argument(format!(
                "{hand_label}-hand source mesh identity is not a canonical resource location"
            ))
        })?;
        // Iris sets `currentRenderedItemId` from the stack being drawn, which
        // is `ItemInHandRenderer`'s equip-animated item, not the player's
        // current held stack: for a few frames after a hotbar change the old
        // item (or bare arm) is drawn while the held-item uniforms already
        // name the new one. Item-model meshes carry the drawn stack's model
        // identity, so resolve from the mesh. Special item renderers
        // (tridents, shields, banners, ...) draw copied ModelPart geometry
        // whose identity names the model part, so those use the copied
        // held-item identity. A bare arm's skin identity resolves unmapped.
        let model_part_geometry = canonical_mesh_identity
            .split_once(':')
            .is_some_and(|(_, path)| path.starts_with("model-part/"));
        let rendered_identity = if model_part_geometry {
            if expected_identity.is_empty() {
                // The bare arm is not an item layer: Iris leaves the value it
                // restores after every item draw.
                return Ok(0);
            } else {
                canonical_resource_location(expected_identity).map_err(|_| {
                    GalError::invalid_argument(format!(
                        "{hand_label}-hand copied item identity is not a canonical resource location"
                    ))
                })?
            }
        } else {
            canonical_mesh_identity
        };
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument(
                "current hand item identity requires an active shader-pack generation",
            )
        })?;
        let item_id_map = self
            .shader_pack_sources
            .active_item_id_map()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "current hand item identity requires a generation-matched item identity map",
                )
            })?;
        if item_id_map.generation() != source.generation() {
            return Err(GalError::invalid_argument(
                "current hand item identity map does not match the active shader-pack generation",
            ));
        }
        item_id_map.resolve_rendered(&rendered_identity)
    }

    /// Iris exposes the outline phase through `renderStage`; a pack that does
    /// not define the stage constant cannot observe it.
    pub(crate) fn source_line_render_stage(&self) -> GalResult<Option<i32>> {
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument("source line render stage requires an active shader pack")
        })?;
        if source
            .runtime_semantic_defines()?
            .contains_key("MC_RENDER_STAGE_OUTLINE")
        {
            source.runtime_semantic_i32("MC_RENDER_STAGE_OUTLINE").map(Some)
        } else {
            Ok(None)
        }
    }

    pub(crate) fn source_shadow_render_stage(&self) -> GalResult<i32> {
        self.shader_pack_sources
            .active()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source shadow render stage requires an active shader-pack generation",
                )
            })?
            .runtime_semantic_i32("MC_RENDER_STAGE_TERRAIN_SOLID")
    }
}
