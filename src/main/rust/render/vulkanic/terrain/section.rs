#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SectionLifecycle {
    Absent,
    Queued,
    Meshing,
    Ready,
    Visible,
    Stale,
    Retired,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LayerKind {
    Solid,
    Cutout,
    CutoutMipped,
}
