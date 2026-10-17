use super::*;
use std::{collections::HashMap, io::Read};
struct Input {
    bytes: Vec<u8>,
    at: usize,
}
impl Input {
    fn byte(&mut self) -> u8 {
        let value = self.bytes[self.at];
        self.at += 1;
        value
    }
    fn int(&mut self) -> i32 {
        let value = i32::from_be_bytes(self.bytes[self.at..self.at + 4].try_into().unwrap());
        self.at += 4;
        value
    }
    fn double(&mut self) -> f64 {
        let value = f64::from_be_bytes(self.bytes[self.at..self.at + 8].try_into().unwrap());
        self.at += 8;
        value
    }
    fn string(&mut self) -> String {
        let n = u16::from_be_bytes(self.bytes[self.at..self.at + 2].try_into().unwrap()) as usize;
        self.at += 2;
        let value = String::from_utf8(self.bytes[self.at..self.at + n].to_vec()).unwrap();
        self.at += n;
        value
    }
}
#[test]
fn actual_frozen_face_decisions_and_all_geometry_pairs() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(&include_bytes!("frozen-face-policy.bin.gz")[..])
        .read_to_end(&mut bytes)
        .unwrap();
    let mut input = Input { bytes, at: 0 };
    assert_eq!(input.int(), 0x54434f32);
    for _ in 0..4 {
        input.string();
    }
    let count = input.int() as usize;
    let mut remap = Vec::new();
    let mut unique = HashMap::new();
    let mut shapes = Vec::new();
    for _ in 0..count {
        let full_identity = input.byte() != 0;
        let empty = input.byte() != 0;
        let boxes: Vec<[f64; 6]> = (0..input.int())
            .map(|_| std::array::from_fn(|_| input.double()))
            .collect();
        let mut key = vec![full_identity as u64, empty as u64];
        key.extend(boxes.iter().flatten().map(|v| v.to_bits()));
        let id = *unique.entry(key).or_insert_with(|| {
            let id = shapes.len();
            shapes.push(Shape {
                full_identity,
                empty,
                boxes,
            });
            id
        });
        remap.push(id as u16);
    }
    let mut states = Vec::new();
    let mut facts = Vec::new();
    for id in 0..input.int() {
        assert_eq!(input.int(), id);
        let block = input.int() as u16;
        let name = input.string();
        input.string();
        let implementation = input.byte();
        let family = input.byte();
        let can_occlude = input.byte() != 0;
        input.byte();
        input.int();
        input.int();
        let faces = std::array::from_fn(|_| remap[input.int() as usize]);
        states.push(State {
            implementation,
            canonical_shape: true,
            faces,
        });
        facts.push(Facts {
            block,
            can_occlude,
            fluid_family: Some(family),
            mangrove_roots: name == "minecraft:mangrove_roots",
        });
    }
    assert_eq!(states.len(), 31809);
    assert_eq!(shapes.len(), 146);
    let policy = Policy::new(states, shapes).unwrap();
    let mut admitted = 0;
    let mut callbacks = 0;
    for row in 0..input.int() {
        let a = input.int() as usize;
        let b = input.int() as usize;
        let d = input.int() as usize;
        input.byte();
        let expected = input.byte() != 0;
        match policy.draw(a, b, d, true, &|id| facts.get(id).copied()) {
            Some(value) => {
                admitted += 1;
                assert_eq!(value, expected, "Frozen production row {row}");
            }
            None => {
                callbacks += 1;
                assert!(matches!(policy.states[a].implementation, 3 | 6));
            }
        }
    }
    assert_eq!((admitted, callbacks), (756437, 31555));
    let pairs = input.int();
    assert_eq!(pairs, 21316);
    for row in 0..pairs {
        let a = remap[input.int() as usize] as usize;
        let b = remap[input.int() as usize] as usize;
        assert_eq!(
            policy.exposed[a * policy.shapes.len() + b],
            input.byte() != 0,
            "Frozen geometry pair {row}"
        );
    }
    assert_eq!(input.at, input.bytes.len());
}
fn simple(implementation: u8, full_identity: bool) -> Policy {
    Policy::new(
        vec![
            State {
                implementation: 0,
                canonical_shape: true,
                faces: [0; 6],
            },
            State {
                implementation,
                canonical_shape: true,
                faces: [1; 6],
            },
        ],
        vec![
            Shape {
                full_identity: false,
                empty: true,
                boxes: vec![],
            },
            Shape {
                full_identity,
                empty: false,
                boxes: vec![[0., 0., 0., 1., 1., 1.]],
            },
        ],
    )
    .unwrap()
}
fn facts(id: usize) -> Option<Facts> {
    Some(Facts {
        block: id as u16,
        can_occlude: true,
        fluid_family: Some(0),
        mangrove_roots: false,
    })
}
#[test]
fn callback_order_and_full_object_identity_are_preserved() {
    let policy = simple(6, true);
    assert_eq!(
        policy.draw(1, 1, 0, false, &|_| panic!(
            "full neighbor precedes facts/callbacks"
        )),
        Some(false)
    );
    assert_eq!(policy.draw(1, 0, 0, true, &facts), None); // leaf hook, even empty neighbor
    assert_eq!(simple(3, true).draw(1, 0, 0, true, &facts), None); // bars tags
    assert_eq!(simple(7, true).draw(1, 0, 0, true, &facts), None); // unknown source method
    assert_eq!(simple(6, false).draw(1, 1, 0, true, &facts), None); // equal cube, different identity
    assert_eq!(simple(0, false).draw(1, 0, 0, false, &facts), None); // platform callback
    assert_eq!(simple(1, false).draw(1, 1, 0, false, &facts), Some(false)); // skip before platform
}
#[test]
fn admission_metadata_preserves_callback_cases_and_validates_the_entire_grid() {
    for kind in [0, 1, 2, 4] {
        assert!(simple(kind, true).native_state(1, &facts));
    }
    for kind in [3, 5, 6, 7] {
        assert!(!simple(kind, true).native_state(1, &facts));
    }
    let mut policy = simple(0, true);
    let mut ids = vec![0; CELLS];
    assert_eq!(policy.admit_grid(&ids), Ok(true));
    policy.states[1].canonical_shape = false;
    ids[CELLS - 1] = 1;
    assert_eq!(policy.admit_grid(&ids), Ok(false));
    ids[CELLS - 1] = 2;
    assert_eq!(policy.admit_grid(&ids), Err(()));
    ids[CELLS - 1] = -1;
    assert_eq!(policy.admit_grid(&ids), Err(()));
    assert_eq!(policy.admit_grid(&ids[..CELLS - 1]), Err(()));
}
#[test]
fn malformed_geometry_and_tables_are_rejected_before_publication() {
    let state = State {
        implementation: 0,
        canonical_shape: true,
        faces: [0; 6],
    };
    for shape in [
        Shape {
            full_identity: true,
            empty: true,
            boxes: vec![],
        },
        Shape {
            full_identity: false,
            empty: true,
            boxes: vec![[0., 0., 0., 1., 1., 1.]],
        },
        Shape {
            full_identity: false,
            empty: false,
            boxes: vec![[f64::NAN, 0., 0., 1., 1., 1.]],
        },
        Shape {
            full_identity: false,
            empty: false,
            boxes: vec![[1., 0., 0., 0., 1., 1.]],
        },
    ] {
        assert!(Policy::new(vec![state], vec![shape]).is_err());
    }
    assert!(Policy::new(vec![state], vec![]).is_err());
    assert!(simple(0, true).draw(2, 0, 0, true, &facts).is_none());
    assert!(simple(0, true).draw(0, 0, 6, true, &facts).is_none());
}
