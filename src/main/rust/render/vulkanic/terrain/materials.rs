use super::section::LayerKind;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StaticTerrainMaterialMode {
    Opaque,
    Cutout,
}

pub fn material_mode_for_layer(layer: LayerKind) -> StaticTerrainMaterialMode {
    match layer {
        LayerKind::Solid => StaticTerrainMaterialMode::Opaque,
        LayerKind::Cutout | LayerKind::CutoutMipped => StaticTerrainMaterialMode::Cutout,
    }
}
