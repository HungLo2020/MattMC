use super::{Config, OffsetType};
macro_rules! offset_profiles {
    ($($name:ident => ($kind:ident,$h:expr,$v:expr)),+ $(,)?) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u8)]
        pub enum Profile { $($name),+ }
        impl Profile { pub fn config(self) -> Config { PROFILES[self as usize] } }
        pub(crate) static PROFILES: &[Config] = &[$(Config {kind:OffsetType::$kind,horizontal:$h,vertical:$v}),+];
    };
}
offset_profiles! {
    Air => (None,0.25,0.200000003),
    MangrovePropagule => (Xz,0.25,0.200000003),
    ShortGrass => (Xyz,0.25,0.200000003),
    Cycad => (Xz,0.200000003,0.200000003),
    PointedDripstone => (Xz,0.125,0.200000003),
    SmallDripleaf => (Xyz,0.25,0.100000001),
    PewenBranch => (None,0.0,0.75),
    PewenPines => (Xz,0.100000001,0.200000003),
}
