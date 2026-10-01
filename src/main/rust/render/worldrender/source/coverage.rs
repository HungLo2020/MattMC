//! Writer-coverage checks that every admitted draw has a source writer.

use super::*;

#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct SourceMaterialWriterCoverage {
    pub(in crate::render::worldrender) batches: u64,
    pub(in crate::render::worldrender) quads: u64,
    pub(in crate::render::worldrender) draws: u64,
    pub(in crate::render::worldrender) vertices: u64,
}

pub(in crate::render::worldrender) fn source_material_writer_coverage(
    batches: &[SourceTexturedMaterialBatch],
    draws: Option<&[TexturedMaterialSourceDraw]>,
) -> GalResult<SourceMaterialWriterCoverage> {
    let quads = batches.iter().try_fold(0_u64, |total, batch| {
        total
            .checked_add(u64::try_from(batch.count).map_err(|_| {
                GalError::invalid_argument("source material batch quad count exceeds u64")
            })?)
            .ok_or_else(|| GalError::invalid_argument("source material quad count overflows"))
    })?;
    let draws = draws.unwrap_or_default();
    let vertices = draws.iter().try_fold(0_u64, |total, draw| {
        total
            .checked_add(u64::from(draw.vertices))
            .ok_or_else(|| GalError::invalid_argument("source material vertex count overflows"))
    })?;
    Ok(SourceMaterialWriterCoverage {
        batches: u64::try_from(batches.len())
            .map_err(|_| GalError::invalid_argument("source material batch count exceeds u64"))?,
        quads,
        draws: u64::try_from(draws.len())
            .map_err(|_| GalError::invalid_argument("source material draw count exceeds u64"))?,
        vertices,
    })
}

/// A compact semantic material quad lowers to exactly one six-vertex direct
/// draw. Keep this receipt invariant at the combined source transaction: a
/// missing draw or a truncated expansion must invalidate the frame before the
/// single presenter can publish an incomplete material family.
pub(crate) fn require_source_material_writer_coverage(
    writer: &str,
    coverage: SourceMaterialWriterCoverage,
) -> GalResult<()> {
    if coverage.quads == 0 {
        return Ok(());
    }
    if coverage.draws == 0 {
        return Err(GalError::backend(format!(
            "{writer} source writer produced no draws for {} semantic quads",
            coverage.quads
        )));
    }
    let expected_vertices = coverage.quads.checked_mul(6).ok_or_else(|| {
        GalError::invalid_argument(format!("{writer} source vertex coverage overflows"))
    })?;
    if coverage.vertices != expected_vertices {
        return Err(GalError::backend(format!(
            "{writer} source writer expanded {} semantic quads to {} vertices; expected {}",
            coverage.quads, coverage.vertices, expected_vertices
        )));
    }
    Ok(())
}

/// Particles deliberately reuse the compact `gbuffers_textured` ABI and
/// writer, but remain a distinct semantic source family. Prove that their
/// admitted quads are included in that shared writer's expanded vertex
/// stream instead of relying on the generic textured receipt alone.
pub(crate) fn require_source_material_family_coverage(
    family: &str,
    expected: SourceMaterialWriterCoverage,
    shared_writer: SourceMaterialWriterCoverage,
) -> GalResult<()> {
    if expected.quads == 0 {
        return Ok(());
    }
    if shared_writer.draws == 0 {
        return Err(GalError::backend(format!(
            "{family} source writer produced no draws for {} semantic quads",
            expected.quads
        )));
    }
    if shared_writer.quads < expected.quads {
        return Err(GalError::backend(format!(
            "{family} source writer covered {} of {} semantic quads",
            shared_writer.quads, expected.quads
        )));
    }
    let required_vertices = expected.quads.checked_mul(6).ok_or_else(|| {
        GalError::invalid_argument(format!("{family} source vertex coverage overflows"))
    })?;
    if shared_writer.vertices < required_vertices {
        return Err(GalError::backend(format!(
            "{family} source writer covered {} of {} required vertices",
            shared_writer.vertices, required_vertices
        )));
    }
    Ok(())
}

/// Particle-group coverage is valid only when the copied frame contains at
/// least one Rust semantic particle batch. This check is shared by ordinary,
/// Fabulous, and selected-source submissions so no route can silently drop a
/// reported particle group before the explicit writer receipt runs.
pub(crate) fn require_particle_group_semantics(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    if frame.feature_coverage.particle_group_submits == 0 {
        return Ok(());
    }
    let batches = source_material_batches_for_program(
        frame,
        WORLD_MATERIAL_SOURCE_PARTICLES,
        &[
            WORLD_MATERIAL_MODE_OPAQUE,
            WORLD_MATERIAL_MODE_CUTOUT,
            WORLD_MATERIAL_MODE_TRANSLUCENT,
        ],
    )?;
    if batches.is_empty() {
        return Err(GalError::unsupported_feature(
            "frame reports particle groups without Rust-owned semantic particle quads",
        ));
    }
    Ok(())
}

/// Every admitted fullscreen source consumer is a real Rust draw, including
/// shader-pack post-processing and cloud stages. A consumer that only records
/// copies or barriers must not silently satisfy the semantic source contract.
pub(crate) fn require_source_fullscreen_writer_coverage(
    writer: &str,
    operations: &[CommandOp],
) -> GalResult<()> {
    let draws = operations
        .iter()
        .filter(|operation| {
            matches!(
                operation,
                CommandOp::Draw { .. } | CommandOp::DrawIndexed { .. }
            )
        })
        .count();
    if draws == 0 {
        return Err(GalError::backend(format!(
            "{writer} fullscreen source writer produced no draw operations"
        )));
    }
    Ok(())
}

/// Outline, crack, and border streams are separate semantic overlay writers.
/// A non-empty stream must emit its own Rust draw before the source presenter
/// can publish the frame; unrelated terrain or fullscreen work cannot satisfy
/// this receipt because the operation slice is isolated per writer.
pub(crate) fn require_source_overlay_writer_coverage(
    writer: &str,
    expected: bool,
    operations: &[CommandOp],
) -> GalResult<()> {
    if !expected {
        return Ok(());
    }
    let draws = operations
        .iter()
        .filter(|operation| {
            matches!(
                operation,
                CommandOp::Draw { .. } | CommandOp::DrawIndexed { .. }
            )
        })
        .count();
    if draws == 0 {
        return Err(GalError::backend(format!(
            "{writer} source overlay writer produced no draw operations"
        )));
    }
    Ok(())
}

/// World text is a semantic quad stream with a dedicated Rust atlas/pass.
/// Every admitted quad must be represented by at least one text draw before
/// the source overlay is allowed to continue toward presentation.
pub(crate) fn require_source_text_writer_coverage(expected_quads: u64, actual_draws: u64) -> GalResult<()> {
    if expected_quads != 0 && actual_draws == 0 {
        return Err(GalError::backend(format!(
            "world text source writer produced no draws for {expected_quads} semantic quads"
        )));
    }
    Ok(())
}

/// Entity mesh instances are semantic submissions, not optional diagnostics.
/// Every admitted ordinary entity instance must reach at least one Rust draw
/// with a non-empty indexed range before the combined source presenter runs.
pub(crate) fn require_source_entity_writer_coverage(
    expected_instances: u64,
    actual_instances: u64,
    draws: u64,
    indices: u64,
) -> GalResult<()> {
    if expected_instances == 0 {
        return Ok(());
    }
    // A single semantic instance may own several immutable mesh sections;
    // each section is emitted as its own indexed draw. Therefore the draw
    // stream's summed instance counts can exceed the semantic-instance count
    // while still providing complete coverage.
    if actual_instances < expected_instances {
        return Err(GalError::backend(format!(
            "entity source writer covered {} of {} semantic instances",
            actual_instances, expected_instances
        )));
    }
    if draws == 0 || indices == 0 {
        return Err(GalError::backend(format!(
            "entity source writer produced incomplete coverage for {} semantic instances (draws={}, indices={})",
            expected_instances, draws, indices
        )));
    }
    Ok(())
}

/// The hand stream has its own projection and depth domain, but it is still
/// part of the same source transaction. Do not let a prepared hand frame
/// disappear while ordinary entity coverage remains valid.
pub(crate) fn require_source_hand_writer_coverage(
    expected_instances: u64,
    actual_instances: u64,
    draws: u64,
    indices: u64,
) -> GalResult<()> {
    if expected_instances == 0 {
        return Ok(());
    }
    // Hand models also expand one semantic instance into multiple section
    // draws; require coverage, not one-to-one draw accounting.
    if actual_instances < expected_instances {
        return Err(GalError::backend(format!(
            "hand source writer covered {} of {} semantic instances",
            actual_instances, expected_instances
        )));
    }
    if draws == 0 || indices == 0 {
        return Err(GalError::backend(format!(
            "hand source writer produced incomplete coverage for {} semantic instances (draws={}, indices={})",
            expected_instances, draws, indices
        )));
    }
    Ok(())
}

/// The near-terrain source plan owns only the indexed baked-block strata;
/// entity batches share the transport list but have a separate writer. Keep
/// the receipt partition explicit so an entity draw cannot falsely satisfy
/// terrain coverage, and reject any admitted terrain range omitted by the
/// source terrain pass.
pub(in crate::render::worldrender) fn require_source_terrain_writer_coverage(
    batches: &[MeshBatch],
    coverage: SourceTerrainDrawCoverage,
) -> GalResult<()> {
    let mut expected_opaque_indices = 0_u64;
    let mut expected_cutout_indices = 0_u64;
    let mut expected_translucent_indices = 0_u64;
    let mut expected_opaque_batches = 0_u64;
    let mut expected_cutout_batches = 0_u64;
    let mut expected_translucent_batches = 0_u64;
    for batch in batches
        .iter()
        .filter(|batch| is_source_terrain_mesh_stratum(batch.key.stratum))
    {
        match batch.key.material_mode {
            WORLD_MATERIAL_MODE_OPAQUE => {
                expected_opaque_batches = expected_opaque_batches.saturating_add(1);
                expected_opaque_indices =
                    expected_opaque_indices.saturating_add(u64::from(batch.index_count));
            }
            WORLD_MATERIAL_MODE_CUTOUT => {
                expected_cutout_batches = expected_cutout_batches.saturating_add(1);
                expected_cutout_indices =
                    expected_cutout_indices.saturating_add(u64::from(batch.index_count));
            }
            WORLD_MATERIAL_MODE_TRANSLUCENT => {
                expected_translucent_batches = expected_translucent_batches.saturating_add(1);
                expected_translucent_indices =
                    expected_translucent_indices.saturating_add(u64::from(batch.index_count));
            }
            _ => {}
        }
    }
    if expected_opaque_batches != 0
        && (coverage.opaque_draws == 0 || coverage.opaque_draw_indices != expected_opaque_indices)
    {
        return Err(GalError::backend(format!(
            "opaque source terrain coverage omitted or truncated indexed work (batches={}, draws={}, indices={}/{})",
            expected_opaque_batches,
            coverage.opaque_draws,
            coverage.opaque_draw_indices,
            expected_opaque_indices
        )));
    }
    if expected_cutout_batches != 0
        && (coverage.cutout_draws == 0 || coverage.cutout_draw_indices != expected_cutout_indices)
    {
        return Err(GalError::backend(format!(
            "cutout source terrain coverage omitted or truncated indexed work (batches={}, draws={}, indices={}/{})",
            expected_cutout_batches,
            coverage.cutout_draws,
            coverage.cutout_draw_indices,
            expected_cutout_indices
        )));
    }
    if expected_translucent_batches != 0
        && (coverage.translucent_draws == 0
            || coverage.translucent_draw_indices != expected_translucent_indices)
    {
        return Err(GalError::backend(format!(
            "translucent source terrain coverage omitted or truncated indexed work (batches={}, draws={}, indices={}/{})",
            expected_translucent_batches,
            coverage.translucent_draws,
            coverage.translucent_draw_indices,
            expected_translucent_indices
        )));
    }
    Ok(())
}

/// Block-model and moving-block producers use the shared indexed terrain
/// writer, but retain distinct semantic strata. Keep a family-specific receipt
/// so ordinary terrain draws cannot mask a dropped block-model submission.
pub(in crate::render::worldrender) fn require_source_mesh_family_writer_coverage(
    family: &str,
    stratum: u32,
    batches: &[MeshBatch],
    draws: &[TerrainMeshDraw],
) -> GalResult<()> {
    let expected = batches
        .iter()
        .filter(|batch| {
            is_source_terrain_mesh_stratum(batch.key.stratum) && batch.key.stratum == stratum
        })
        .fold((0_u64, 0_u64), |(batch_count, indices), batch| {
            (
                batch_count.saturating_add(1),
                indices.saturating_add(u64::from(batch.index_count)),
            )
        });
    if expected.0 == 0 {
        return Ok(());
    }
    let actual = draws.iter().filter(|draw| draw.stratum == stratum).fold(
        (0_u64, 0_u64),
        |(draw_count, indices), draw| {
            (
                draw_count.saturating_add(1),
                indices.saturating_add(u64::from(draw.index_count)),
            )
        },
    );
    if actual.0 == 0 || actual.1 != expected.1 {
        return Err(GalError::backend(format!(
            "{family} source writer omitted or truncated indexed work (batches={}, draws={}, indices={}/{})",
            expected.0, actual.0, actual.1, expected.1
        )));
    }
    Ok(())
}

/// Distant Horizons has several material streams (reduced-color, exact-atlas,
/// and translucent water). They share one source transaction, so prove that
/// any admitted LOD workload reached at least one Rust-owned indexed draw.
pub(crate) fn require_source_lod_writer_coverage(expected_instances: u64, actual_draws: u64) -> GalResult<()> {
    if expected_instances != 0 && actual_draws < expected_instances {
        return Err(GalError::backend(format!(
            "Distant Horizons source writer covered {actual_draws} of {expected_instances} admitted LOD instances"
        )));
    }
    Ok(())
}
