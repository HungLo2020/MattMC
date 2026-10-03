//! Frozen shadow-frustum distance rules over immutable source/frame semantics.

use super::*;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) struct ShadowCasterDistancePolicy {
    pub terrain_multiplier: f32,
    pub entity_multiplier: f32,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct ShadowCasterFrameDistances {
    /// The copied `far` scalar: effective normal render distance * 16.
    pub render_distance_blocks: f32,
    pub configured_shadow_distance_chunks: i32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ShadowCasterKind {
    Terrain,
    Entity,
}

pub(super) struct ShadowCasterLimits {
    pub use_planes: bool,
    pub distance_limit: Option<f64>,
    pub safe_zone: Option<(f64, f64)>,
}

impl ShaderPackShadowPolicy {
    pub(crate) fn requires_copied_entity_culling(self) -> bool {
        self.caster_distances.is_some()
    }
    pub(crate) fn entity_uses_distinct_shadow_frustum(self) -> bool {
        self.caster_distances.is_some_and(|policy| {
            policy.entity_multiplier >= 0.0 && policy.entity_multiplier != 1.0
        })
    }

    pub(super) fn caster_limits(
        self,
        frame: Option<ShadowCasterFrameDistances>,
        kind: ShadowCasterKind,
    ) -> GalResult<ShadowCasterLimits> {
        let Some(policy) = self.caster_distances else {
            let mode = self.require_supported_caster_selection()?;
            return Ok(ShadowCasterLimits {
                use_planes: true,
                distance_limit: None,
                safe_zone: (mode == ShadowCasterSelection::SafeZone)
                    .then_some((f64::from(self.voxel_distance), f64::from(self.distance))),
            });
        };
        let frame = frame.ok_or_else(|| {
            GalError::unsupported_feature(
                "scoped source shadow selection requires copied render and user shadow distances",
            )
        })?;
        if !frame.render_distance_blocks.is_finite() || frame.render_distance_blocks <= 0.0 {
            return Err(GalError::invalid_argument(
                "shadow selection requires a finite positive normal render distance",
            ));
        }
        let mode = self.caster_selection.ok_or_else(|| {
            GalError::unsupported_feature(
                "shadow selection requires resolved source culling options",
            )
        })?;
        let mut multiplier = policy.terrain_multiplier;
        if kind == ShadowCasterKind::Entity && self.entity_uses_distinct_shadow_frustum() {
            // Java float multiplication occurs before promotion to double.
            multiplier *= policy.entity_multiplier;
        }
        let normal_distance = f64::from(frame.render_distance_blocks);
        if mode == ShadowCasterSelection::Distance {
            let distance = finite_product(self.distance, multiplier)?;
            return Ok(ShadowCasterLimits {
                use_planes: false,
                distance_limit: (distance > 0.0 && distance <= normal_distance).then_some(distance),
                safe_zone: None,
            });
        }
        let safe = mode == ShadowCasterSelection::SafeZone;
        if safe && multiplier < 0.0 {
            multiplier = 1.0;
        }
        let distance = if multiplier < 0.0 {
            // Preserve Frozen's int multiplication before promotion. A saved
            // configuration value never allocates or expands resource counts.
            f64::from(frame.configured_shadow_distance_chunks.wrapping_mul(16))
        } else {
            finite_product(
                if safe {
                    self.voxel_distance
                } else {
                    self.distance
                },
                multiplier,
            )?
        };
        Ok(ShadowCasterLimits {
            use_planes: true,
            distance_limit: (!safe && distance < normal_distance).then_some(distance),
            safe_zone: if safe {
                Some((distance, finite_product(self.distance, multiplier)?))
            } else {
                None
            },
        })
    }
}

fn finite_product(left: f32, right: f32) -> GalResult<f64> {
    let value = left * right;
    if !value.is_finite() {
        return Err(GalError::invalid_argument(
            "shadow distance multiplier product must be finite",
        ));
    }
    Ok(f64::from(value))
}

#[cfg(test)]
mod tests;
