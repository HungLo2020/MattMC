use super::section::SectionLifecycle;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SectionLifecycleEvent {
    pub section_pos: i64,
    pub lifecycle: SectionLifecycle,
}
