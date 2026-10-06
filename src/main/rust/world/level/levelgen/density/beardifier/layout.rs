pub(crate) const KERNEL_SIZE: usize = 24 * 24 * 24;
pub(super) const MAX_ENTRIES: usize = 65536;
pub(super) struct Geometry<'a> {
    pub bounds: &'a [i32],
    pub pieces: &'a [i32],
    pub junctions: &'a [i32],
}
impl<'a> Geometry<'a> {
    pub fn read(data: &'a [i32]) -> Option<Self> {
        if data.len() < 8 {
            return None;
        }
        let pieces = usize::try_from(data[0]).ok()?;
        let junctions = usize::try_from(data[1]).ok()?;
        if pieces > MAX_ENTRIES || junctions > MAX_ENTRIES {
            return None;
        }
        let split = 8 + pieces * 8;
        if data.len() != split + junctions * 3 {
            return None;
        }
        if data[8..split]
            .chunks_exact(8)
            .any(|p| !(0..=4).contains(&p[7]))
        {
            return None;
        }
        Some(Self {
            bounds: &data[2..8],
            pieces: &data[8..split],
            junctions: &data[split..],
        })
    }
}
