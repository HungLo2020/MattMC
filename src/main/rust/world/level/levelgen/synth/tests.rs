use super::*;
use super::{
    common::{floor, lfloor},
    dispatch::{eval_dispatch, evaluate_batch},
    state::Octave,
};
#[test]
fn abi_layout() {
    assert_eq!(std::mem::size_of::<State>(), 80);
    assert_eq!(std::mem::size_of::<Octave>(), 1320);
    assert_eq!(std::mem::offset_of!(Octave, p), 40);
    assert_eq!(std::mem::offset_of!(Octave, p32), 296);
}
#[test]
fn java_cast_edges() {
    assert_eq!(floor(f64::NAN), 0);
    assert_eq!(floor(f64::INFINITY), i32::MAX);
    assert_eq!(floor(f64::NEG_INFINITY), i32::MAX);
    assert_eq!(lfloor(f64::NEG_INFINITY), i64::MAX);
    assert_eq!(floor(-0.1), -1);
    assert_eq!(floor(-1.), -1);
}
#[test]
fn scalar_and_vector_paths_match() {
    let mut o = Octave {
        x: 17.25,
        y: 91.5,
        z: 231.75,
        amplitude: 1.,
        present: 1,
        p: [0; 256],
        p32: [0; 256],
    };
    for i in 0..256 {
        o.p[i] = ((i * 137 + 19) & 255) as u8;
        o.p32[i] = o.p[i] as i32 | (((o.p[i] as i32) % 12) << 8);
    }
    let mut octaves = vec![o; 8];
    octaves[2].present = 0;
    octaves[6].present = 0;
    for kind in [1, 2, 3, 5, 6] {
        let mut s = State {
            kind,
            n1: if kind == 1 || kind == 5 { 1 } else { 4 },
            n2: if kind == 3 { 4 } else { 0 },
            n3: 0,
            params: [0.; 8],
        };
        s.params[..5].copy_from_slice(&[0.125, 0.5, 0.125, 0.5, 1.25]);
        let used = &octaves[..(s.n1 + s.n2) as usize];
        let mut xyz = Vec::new();
        for i in -300..300 {
            xyz.extend_from_slice(&[i as f64 * 100003.25, i as f64 * 0.125, i as f64 * -7101.5]);
        }
        xyz.extend_from_slice(&[f64::NAN, f64::INFINITY, f64::NEG_INFINITY]);
        xyz.extend_from_slice(&[-0., 0., f64::MIN_POSITIVE]);
        let mut out = vec![0.; xyz.len() / 3];
        for flags in [0, 1] {
            evaluate_batch(&s, used, &xyz, &mut out, 0., 0., flags);
            for (i, p) in xyz.chunks_exact(3).enumerate() {
                let a = s.eval(used, p[0], p[1], p[2], 0., 0., flags);
                let b = eval_dispatch(&s, used, p[0], p[1], p[2], 0., 0., flags);
                assert!(
                    a.to_bits() == out[i].to_bits() || (a.is_nan() && out[i].is_nan()),
                    "batch kind={kind} flags={flags} point={i}"
                );
                assert!(
                    a.to_bits() == b.to_bits() || (a.is_nan() && b.is_nan()),
                    "scalar kind={kind} flags={flags} point={i}"
                );
            }
        }
    }
}

#[test]
fn rejects_stale_permutation_encoding() {
    #[repr(C)]
    struct Fixture {
        state: State,
        octave: Octave,
    }
    let mut f = Fixture {
        state: State {
            kind: 1,
            n1: 1,
            n2: 0,
            n3: 0,
            params: [0.; 8],
        },
        octave: Octave {
            x: 0.,
            y: 0.,
            z: 0.,
            amplitude: 1.,
            present: 1,
            p: [0; 256],
            p32: [0; 256],
        },
    };
    assert_eq!(unsafe { noise_validate(&f.state, 1400) }, 0);
    f.octave.p[0] = 1;
    assert_eq!(unsafe { noise_validate(&f.state, 1400) }, -4);
    f.octave.p32[0] = 1 | (1 << 8);
    f.octave.x = f64::NAN;
    assert_eq!(unsafe { noise_validate(&f.state, 1400) }, -5);
}

#[test]
fn rejects_bad_state() {
    let s = State {
        kind: 9,
        n1: 0,
        n2: 0,
        n3: 0,
        params: [0.; 8],
    };
    assert_eq!(unsafe { noise_validate(&s, 80) }, -3);
    assert_eq!(unsafe { noise_validate(std::ptr::null(), 80) }, -1);
    assert_eq!(unsafe { noise_validate(&s, 79) }, -1);
}
