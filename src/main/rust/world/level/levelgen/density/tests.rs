use super::{
    math::{max, min},
    program::{Header, Node},
    validation::density_validate,
};
#[test]
fn layout() {
    assert_eq!(std::mem::size_of::<Header>(), 16);
    assert_eq!(std::mem::size_of::<Node>(), 64);
}
#[test]
fn java_min_max() {
    assert_eq!(min(0., -0.).to_bits(), (-0.0f64).to_bits());
    assert_eq!(max(-0., 0.).to_bits(), 0.0f64.to_bits());
    assert!(min(1., f64::NAN).is_nan());
    assert!(max(f64::NAN, 1.).is_nan());
}
#[test]
fn reject_exponential_graph() {
    let mut data = [0u64; 2 + 48 * 8];
    data[0] = 48;
    let base = data.as_mut_ptr().cast::<u8>();
    for i in 1..48 {
        unsafe {
            let n = &mut *base.add(16 + i * 64).cast::<Node>();
            n.op = 8;
            n.a = (i - 1) as u32;
            n.b = (i - 1) as u32;
        }
    }
    assert_eq!(
        unsafe { density_validate(base, std::mem::size_of_val(&data) as u64) },
        -8
    );
}
#[test]
fn reject_forward_child() {
    #[repr(C)]
    struct Program {
        h: Header,
        n: Node,
    }
    let p = Program {
        h: Header {
            count: 1,
            inputs: 0,
            cells: 0,
            reserved: 0,
        },
        n: Node {
            op: 8,
            a: 0,
            b: 0,
            c: 0,
            offset: 0,
            bytes: 0,
            p: 0.,
            q: 0.,
            r: 0.,
            reserved: 0.,
        },
    };
    assert_eq!(
        unsafe { density_validate((&p as *const Program).cast(), 80) },
        -4
    );
}
