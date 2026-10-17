use crate::content::block::{collision::Catalog, StateFlags};
use std::io::Read;
pub(crate) struct Chunk {
    pub(crate) min_y: i32,
    pub(crate) min: i32,
    pub(crate) max: i32,
    pub(crate) states: Vec<Vec<u32>>,
    pub(crate) solid: [i32; 256],
    pub(crate) blocking: [i32; 256],
}
pub(crate) struct Fixture {
    pub(crate) catalog: Catalog,
    pub(crate) flags: Vec<StateFlags>,
    pub(crate) expected: Vec<(bool, u8)>,
    pub(crate) chunks: Vec<Chunk>,
}
pub(crate) fn load() -> Fixture {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../../../test/resources/world/chunk/frozen-dh-heightmaps.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut input = bytes.as_slice();
    fn i(input: &mut &[u8]) -> i32 {
        let (a, b) = input.split_at(4);
        *input = b;
        i32::from_be_bytes(a.try_into().unwrap())
    }
    fn byte(input: &mut &[u8]) -> u8 {
        let v = input[0];
        *input = &input[1..];
        v
    }
    fn text(input: &mut &[u8]) -> String {
        let n = u16::from_be_bytes(input[..2].try_into().unwrap()) as usize;
        let t = String::from_utf8(input[2..n + 2].to_vec()).unwrap();
        *input = &input[n + 2..];
        t
    }
    assert_eq!(i(&mut input), 0x44484831);
    for expected in [
        "68c7e9206b17b042ecc9307d02a9fd6dcd3fcf0fbfc5ba02b75951c11f4400cd",
        "71dada60ecbd485877e412bf0a2e7b8f437aa09e7f5579817e97f21c55dd69e6",
        "3430938c5556f13efcd286e6312b50312ec7e994673553022d3a70ff4881dbdc",
    ] {
        assert_eq!(text(&mut input), expected);
    }
    assert_eq!(text(&mut input).len(), 64);
    text(&mut input);
    let mut shapes = Vec::new();
    for _ in 0..i(&mut input) {
        let mut boxes = Vec::new();
        for _ in 0..i(&mut input) {
            let mut b = [0.; 6];
            for v in &mut b {
                *v = f64::from_be_bytes(input[..8].try_into().unwrap());
                input = &input[8..];
            }
            boxes.push(b);
        }
        shapes.push(boxes);
    }
    assert_eq!(shapes.len(), 323);
    let count = i(&mut input) as usize;
    assert_eq!(count, 31809);
    let mut states = Vec::new();
    let mut flags = Vec::new();
    let mut lights = Vec::new();
    let mut expected = Vec::new();
    for id in 0..count {
        assert_eq!(i(&mut input), id as i32);
        states.push(i(&mut input));
        let air = byte(&mut input) != 0;
        let occludes = byte(&mut input) != 0;
        let fluid = byte(&mut input) != 0;
        flags.push(StateFlags(
            (if air { StateFlags::AIR.0 } else { 0 })
                | (if occludes {
                    StateFlags::CAN_OCCLUDE.0
                } else {
                    0
                })
                | (if fluid { StateFlags::HAS_FLUID.0 } else { 0 }),
        ));
        lights.push(byte(&mut input));
        expected.push((byte(&mut input) != 0, byte(&mut input)));
    }
    let catalog = Catalog::new(states, shapes, |id| (flags[id], lights[id])).unwrap();
    let mut chunks = Vec::new();
    for _ in 0..i(&mut input) {
        text(&mut input);
        input = &input[8..];
        let min_y = i(&mut input);
        let height = i(&mut input);
        let min = i(&mut input);
        let max = i(&mut input);
        let states = (0..height / 16)
            .map(|_| (0..4096).map(|_| i(&mut input) as u32).collect())
            .collect();
        let mut solid = [0; 256];
        let mut blocking = [0; 256];
        for col in 0..256 {
            solid[col] = i(&mut input);
            blocking[col] = i(&mut input);
        }
        chunks.push(Chunk {
            min_y,
            min,
            max,
            states,
            solid,
            blocking,
        });
    }
    assert_eq!(chunks.len(), 16);
    assert!(input.is_empty());
    Fixture {
        catalog,
        flags,
        expected,
        chunks,
    }
}
