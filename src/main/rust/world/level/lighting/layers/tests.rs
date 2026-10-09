use super::*;

fn i32_at(bytes: &[u8], at: &mut usize) -> i32 {
    let value = i32::from_be_bytes(bytes[*at..*at + 4].try_into().unwrap());
    *at += 4;
    value
}

#[test]
fn frozen_allocated_and_lazy_transitions_match_all_cells() {
    let bytes: &[u8] = include_bytes!("frozen-data-layer.rle");
    let mut at = 0;
    assert_eq!(i32_at(bytes, &mut at), 0x4c415952);
    let cases = i32_at(bytes, &mut at);
    let mut layer = Layer::new(0);
    for case in 0..cases {
        let default = i32_at(bytes, &mut at);
        let op = i32_at(bytes, &mut at);
        match op {
            0 => layer = Layer::new(default),
            1 | 3 | 10 => layer = layer.copy(),
            2 => {
                layer.set(0, 17).unwrap();
            }
            4 => {
                layer.set(4095, -2).unwrap();
            }
            5 => {
                let previous = layer;
                layer = previous.copy();
                previous.fill(7);
            }
            6 => layer.fill(31),
            7 | 11 => {
                layer.materialize();
            }
            8 => {
                layer.set((9 << 8) | (13 << 4) | 7, -81).unwrap();
            }
            9 => layer.fill(0),
            _ => panic!("unexpected operation {op}"),
        }
        let view = layer.view();
        assert_eq!(
            view.default_value(),
            i32_at(bytes, &mut at),
            "raw default case {case}"
        );
        assert_eq!(view.allocated(), bytes[at] != 0, "allocation case {case}");
        at += 1;
        let runs = u16::from_be_bytes(bytes[at..at + 2].try_into().unwrap());
        at += 2;
        let mut index = 0;
        for _ in 0..runs {
            let count = u16::from_be_bytes(bytes[at..at + 2].try_into().unwrap());
            at += 2;
            let expected = i32_at(bytes, &mut at);
            for _ in 0..count {
                assert_eq!(view.get(index).unwrap(), expected, "cell {index} case {case}");
                index += 1;
            }
        }
        assert_eq!(index, 4096);
    }
    assert_eq!(at, bytes.len());
    println!("All {cases} Frozen light representations match across every cell.");
}

#[test]
fn views_remain_valid_across_mutation_fill_copy_and_owner_release() {
    let layer = Layer::new(-1);
    let lazy = layer.view();
    assert_eq!(lazy.get(-5000), Ok(-1));
    assert!(layer.set(2, 4).unwrap());
    let allocated = layer.view();
    let address = allocated.bytes_address().unwrap();
    assert!(!layer.set(2, 5).unwrap());
    assert_eq!(allocated.get(2), Ok(5));
    assert_eq!(allocated.bytes_address(), Some(address));
    assert_eq!(lazy.get(2), Ok(-1));
    let copy = layer.copy();
    assert_eq!(copy.view().default_value(), 0);
    copy.set(2, 6).unwrap();
    assert_eq!(allocated.get(2), Ok(5));
    layer.fill(31);
    drop(layer);
    assert_eq!(allocated.get(2), Ok(5));
    assert_eq!(lazy.get(2), Ok(-1));
}

#[test]
fn invalid_set_materializes_before_error_while_lazy_get_ignores_bounds() {
    let layer = Layer::new(16);
    assert_eq!(layer.view().get(i32::MIN), Ok(16));
    assert_eq!(layer.set(-1, 4), Err(InvalidByteIndex(-1)));
    let view = layer.view();
    assert!(view.allocated());
    assert_eq!(view.default_value(), 16);
    assert_eq!(view.get(-1), Err(InvalidByteIndex(-1)));
    assert_eq!(view.get(0), Ok(0));
    assert_eq!(view.get(1), Ok(1));
}
