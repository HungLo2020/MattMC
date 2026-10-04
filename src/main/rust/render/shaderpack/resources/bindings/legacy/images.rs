//! Selected image declarations that name fields already owned by the runtime.
//! Unknown images remain unresolved; sampler spelling alone grants no resource.

use super::*;
use crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeRequirements;
use crate::render::shaderpack::voxels::occupancy::PuddleOccupancyDescriptor;

pub(super) fn bind_owned_images(
    bindings: &mut TerrainSourceResourceBindings,
    properties: &str,
) -> GalResult<()> {
    // Java properties replace a duplicate key with its final selected value.
    // Only four supported identities enter this bounded table.
    let mut declarations = BTreeMap::new();
    for line in properties.lines() {
        let Some((key, value)) = line.trim().split_once('=') else {
            continue;
        };
        let Some(image) = key.trim().strip_prefix("image.") else {
            continue;
        };
        if matches!(
            image,
            "voxel_img" | "floodfill_img" | "floodfill_img_copy" | "puddle_img"
        ) {
            declarations.insert(image, value);
        }
    }
    let mut volume_extent = None;
    for (image, value) in declarations {
        let (role, format, dimensions) = match image {
            "voxel_img" => (
                TerrainSourceResourceRole::ColoredVoxelOccupancy,
                ["red_integer", "r8ui", "unsigned_int", "true", "false"],
                3,
            ),
            "floodfill_img" => (
                TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                ["rgba", "rgba16f", "half_float", "false", "false"],
                3,
            ),
            "floodfill_img_copy" => (
                TerrainSourceResourceRole::ColoredVoxelLightPrevious,
                ["rgba", "rgba16f", "half_float", "false", "false"],
                3,
            ),
            "puddle_img" => (
                TerrainSourceResourceRole::PuddleOccupancy,
                ["red_integer", "r8ui", "unsigned_int", "true", "false"],
                2,
            ),
            _ => continue,
        };
        let fields = value.split_ascii_whitespace().take(10).collect::<Vec<_>>();
        if fields.len() != 6 + dimensions || !valid_identifier(fields[0]) || fields[1..6] != format
        {
            return Err(GalError::unsupported_feature(format!(
                "source image '{image}' is incompatible with its owned format/update contract",
            )));
        }
        let extent = fields[6..]
            .iter()
            .map(|field| field.parse::<u32>())
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| {
                GalError::invalid_argument(format!("source image '{image}' has invalid dimensions"))
            })?;
        if dimensions == 3 {
            let expected = VoxelLightVolumeRequirements::complementary(extent[0])?.extent;
            if extent != [expected.width, expected.height, expected.depth]
                || volume_extent.is_some_and(|previous| previous != expected)
            {
                return Err(GalError::unsupported_feature(format!(
                    "source image '{image}' has incompatible owned voxel-volume dimensions",
                )));
            }
            volume_extent = Some(expected);
        } else if extent != [PuddleOccupancyDescriptor::EXTENT; 2] {
            return Err(GalError::unsupported_feature(format!(
                "source image '{image}' has incompatible owned puddle-field dimensions",
            )));
        }
        for alias in [fields[0], image] {
            if bindings
                .bindings
                .get(alias)
                .is_some_and(|previous| *previous != role)
            {
                return Err(GalError::invalid_argument(format!(
                    "source image '{image}' conflicts with semantic resource alias '{alias}'",
                )));
            }
            bindings.bindings.insert(alias.to_owned(), role.clone());
        }
    }
    Ok(())
}
