use super::{
    packets::{self, Entry, Plan},
    Builder, Policy, Quad,
};
use std::io::Read;
struct Reader {
    data: Vec<u8>,
    at: usize,
}
impl Reader {
    fn take(&mut self, n: usize) -> &[u8] {
        let start = self.at;
        self.at += n;
        &self.data[start..self.at]
    }
    fn int(&mut self) -> i32 {
        i32::from_be_bytes(self.take(4).try_into().unwrap())
    }
    fn short(&mut self) -> i16 {
        i16::from_be_bytes(self.take(2).try_into().unwrap())
    }
    fn byte(&mut self) -> u8 {
        self.take(1)[0]
    }
    fn text(&mut self) -> String {
        let n = self.short() as u16 as usize;
        String::from_utf8(self.take(n).to_vec()).unwrap()
    }
}
#[test]
fn actual_frozen512_packet_layers_and_bytes() {
    let mut data = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../test/resources/rendering/frozen-dh-quads.bin.gz").as_slice(),
    )
    .read_to_end(&mut data)
    .unwrap();
    let mut r = Reader { data, at: 0 };
    assert_eq!(r.int(), 0x44485131);
    r.text();
    let pins = r.int();
    for _ in 0..pins {
        r.text();
        r.text();
    }
    assert_eq!(r.int(), 512);
    for case in 0..512 {
        let trans = r.byte() != 0;
        let curvature = r.byte() != 0;
        let overlap_errors = r.byte() != 0;
        let merge = r.byte() != 0;
        let policy = Policy {
            exact: false,
            curvature,
            overlap_errors,
        };
        let mut builder = Builder::new(trans);
        let count = r.int();
        for _ in 0..count {
            let mut input = [0i16; 9];
            for v in &mut input {
                *v = r.short();
            }
            let q = Quad {
                x: input[1],
                y: input[2],
                z: input[3],
                width: input[4],
                height: input[5],
                direction: input[0] as u8,
                material: input[6] as u8,
                color: ((input[7] as u32) << 24) | 0x336699,
                sky: 15,
                block: input[8] as u8,
                error: 0,
                variant: 0,
                identity: 0,
                position: 0,
            };
            builder.add(q, q.direction >= 2, policy);
        }
        if merge {
            builder.merge(policy);
            builder.merge(policy);
        }
        let mut expected: [[Vec<(Quad, Vec<u8>)>; 6]; 2] =
            std::array::from_fn(|_| std::array::from_fn(|_| Vec::new()));
        for layer in &mut expected {
            for rows in layer {
                let count = r.int();
                for _ in 0..count {
                    let q = Quad {
                        x: r.short(),
                        y: r.short(),
                        z: r.short(),
                        width: r.short(),
                        height: r.short(),
                        color: r.int() as u32,
                        material: r.byte(),
                        sky: r.byte(),
                        block: r.byte(),
                        direction: r.byte(),
                        error: r.byte(),
                        variant: 0,
                        identity: 0,
                        position: 0,
                    };
                    let mut bytes = r.take(64).to_vec();
                    if cfg!(target_endian = "big") {
                        for vertex in bytes.chunks_exact_mut(16) {
                            for field in vertex[..8].chunks_exact_mut(2) {
                                field.reverse();
                            }
                        }
                    }
                    rows.push((q, bytes));
                }
            }
        }
        for route in 0..4 {
            let plan = Plan::new(&builder, route, 0, false).unwrap();
            let layer = usize::from(route != 0);
            let wanted: Vec<_> = expected[layer]
                .iter()
                .enumerate()
                .filter(|(d, _)| match route {
                    0 => true,
                    1 => *d != 1,
                    _ => *d == 1,
                })
                .flat_map(|(_, rows)| rows)
                .filter(|(q, _)| match route {
                    2 => q.material != 12,
                    3 => q.material == 12,
                    _ => true,
                })
                .collect();
            assert_eq!(
                plan.entries.len(),
                wanted.len(),
                "case {case} route {route}"
            );
            for (entry, (q, bytes)) in plan.entries.iter().zip(wanted) {
                assert_eq!(&entry.quad, q);
                assert_eq!(
                    packets::encode(entry, trans, &[], &[]).as_slice(),
                    bytes.as_slice(),
                    "case {case} route {route}"
                );
            }
        }
    }
    assert_eq!(r.at, r.data.len());
}
fn grass(direction: u8) -> Quad {
    Quad {
        x: 32767,
        y: -32768,
        z: 3,
        width: 4,
        height: 5,
        direction,
        material: 13,
        sky: 255,
        block: 129,
        error: 0,
        variant: 1,
        color: 0x80123456,
        identity: 17,
        position: 19,
    }
}
#[test]
fn event_order_errors_and_snapshot_independence() {
    let mut builder = Builder::new(true);
    let policy = Policy {
        exact: false,
        overlap_errors: false,
        curvature: false,
    };
    let mut down = grass(0); down.color |= 0xff000000;
    let mut up = grass(1); up.color |= 0xff000000;
    builder.add(down, false, policy);
    builder.add(up, false, policy);
    let mut water = grass(2);
    water.material = 12;
    builder.add(water, false, policy);
    let plan = Plan::new(&builder, 0, 2, true).unwrap();
    assert_eq!(plan.callbacks, 4);
    assert_eq!(
        plan.events
            .iter()
            .map(|e| (e.vertex, e.kind))
            .collect::<Vec<_>>(),
        [(0, 1), (1, 1), (2, 1), (3, 1)]
    );
    let water = Plan::new(&builder, 1, 2, true).unwrap();
    assert_eq!(water.events.len(), 4);
    drop(builder);
    assert_eq!(plan.entries.len(), 2);
    assert_eq!(std::mem::size_of::<packets::Event>(), 16);
}
#[test]
fn signed_light_and_wrapping_geometry_are_preserved() {
    let q = grass(2);
    let entry = Entry {
        quad: q,
        callbacks: [-1; 4],
    };
    let bytes = packets::encode(&entry, true, &[], &[]);
    assert_eq!(i16::from_ne_bytes(bytes[6..8].try_into().unwrap()), -1);
    assert_eq!(
        i16::from_ne_bytes(bytes[32..34].try_into().unwrap()),
        -32765
    );
    assert_eq!(&bytes[8..14], &[0x12, 0x34, 0x56, 0x80, 13, 2]);
    assert_eq!(packets::shade(0x80123456, f32::NAN), 0x80000000);
    assert_eq!(packets::shade(0x80123456, f32::INFINITY), 0x80ffffff);
}
