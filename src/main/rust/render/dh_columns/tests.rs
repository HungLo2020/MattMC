use super::reduction;
struct Reader {
    bytes: Vec<u8>,
    at: usize,
}
impl Reader {
    fn int(&mut self) -> i32 {
        let b = self.take(4);
        i32::from_be_bytes(b.try_into().unwrap())
    }
    fn long(&mut self) -> u64 {
        let b = self.take(8);
        u64::from_be_bytes(b.try_into().unwrap())
    }
    fn take(&mut self, n: usize) -> &[u8] {
        let at = self.at;
        self.at += n;
        &self.bytes[at..self.at]
    }
    fn text(&mut self) -> String {
        let n = u16::from_be_bytes(self.take(2).try_into().unwrap()) as usize;
        String::from_utf8(self.take(n).to_vec()).unwrap()
    }
}
#[test]
fn actual_frozen_reduction_all3616_cases_and_ffi_publication() {
    use std::io::Read;
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(
        include_bytes!("../../../../test/resources/world/chunk/frozen-dh-render-reduction.bin.gz")
            .as_slice(),
    )
    .read_to_end(&mut bytes)
    .unwrap();
    let mut r = Reader { bytes, at: 0 };
    assert_eq!(r.int(), 0x44525231);
    assert_eq!(r.int(), 6);
    for _ in 0..6 {
        assert!(!r.text().is_empty());
        assert_eq!(r.text().len(), 64);
    }
    assert!(r.text().contains("Actual Frozen render reduction"));
    assert_eq!(r.int(), 3616);
    for _ in 0..3616 {
        let name = r.text();
        let target = r.int();
        let size = r.int() as usize;
        let count = r.int() as usize;
        let input = (0..count).map(|_| r.long()).collect::<Vec<_>>();
        let error = r.text();
        let expected = (0..size + 3).map(|_| r.long()).collect::<Vec<_>>();
        let mut actual = vec![0x1122334455667788; size + 3];
        let status = unsafe {
            super::ffi::mattmc_dh_render_reduce(
                input.as_ptr(),
                count as i32,
                std::ptr::null(),
                target,
                actual.as_mut_ptr().add(1),
                size as i32,
                std::ptr::null_mut(),
            )
        };
        if error.is_empty() {
            assert_eq!(status, 1, "admission {name}");
            assert_eq!(actual, expected, "{name}");
        } else {
            assert_eq!(error, "java.lang.IllegalArgumentException");
            assert_eq!(status, 0, "decline {name}");
            assert_eq!(actual, vec![0x1122334455667788; size + 3]);
        }
    }
    assert_eq!(r.at, r.bytes.len());
}

#[test]
fn invalid_geometry_and_bounds_decline_without_output() {
    let point = |top: u64, bottom: u64| (15 << 56) | (top << 20) | (bottom << 8);
    assert!(reduction::reduce(&[point(4, 0), point(8, 2)], None, 2, 2).is_err());
    assert!(reduction::reduce(&vec![0; 4097], None, 2, 2).is_err());
    assert_eq!(
        reduction::reduce(&[point(8, 0)], None, 0, 3).unwrap(),
        vec![(0, 0); 3]
    );
    assert!(reduction::reduce(&[point(8, 0)], Some(&[]), 2, 2).is_err());
}

#[test]
fn semantic_material_survival_aliasing_and_empty_policy() {
    let a = (15 << 56) | (1 << 20) | (2 << 60);
    let b = (15 << 56) | (2 << 20) | (1 << 8) | (13 << 60);
    assert_eq!(
        reduction::reduce(&[a, b], Some(&[31, 47]), 1, 1).unwrap()[0].1,
        -1
    );
    assert_eq!(
        reduction::reduce(&[a, b], Some(&[47, 47]), 1, 1).unwrap()[0].1,
        47
    );
    assert_eq!(
        reduction::reduce(&[], Some(&[]), 2, 3).unwrap(),
        vec![(0, 0); 3]
    );
    let mut data = [a, b, 99];
    let mut materials = [31, 47, 88];
    let status = unsafe {
        super::ffi::mattmc_dh_render_reduce(
            data.as_ptr(),
            2,
            materials.as_ptr(),
            1,
            data.as_mut_ptr(),
            1,
            materials.as_mut_ptr(),
        )
    };
    assert_eq!(status, 1);
    assert_eq!(materials, [-1, 47, 88]);
    assert_eq!(data[1..], [b, 99]);
}

#[test]
fn per_thread_scratch_reuses_bounded_capacity_and_concurrent_workers_are_independent() {
    let threads = (0..4)
        .map(|worker| {
            std::thread::spawn(move || {
                for size in [7, 19, 130, 511, 4096, 3, 0, 4096] {
                    let points = (0..size)
                        .map(|i| {
                            (15 << 56)
                                | ((i as u64 + 1) << 20)
                                | ((i as u64) << 8)
                                | ((worker as u64 + 1) << 32)
                        })
                        .collect::<Vec<_>>();
                    reduction::reduce(&points, None, 8, 16).unwrap();
                    let capacities = reduction::scratch_capacities();
                    assert!(
                        capacities[0] <= 8192 && capacities[1] <= 8192 && capacities[2] <= 4096
                    );
                }
                let before = reduction::scratch_capacities();
                for _ in 0..100 {
                    let out = reduction::reduce(
                        &[(15 << 56) | (2 << 20) | ((worker as u64 + 1) << 32)],
                        None,
                        2,
                        16,
                    )
                    .unwrap();
                    assert_eq!(out[0].0 >> 32 & 255, worker + 1);
                }
                assert_eq!(before, reduction::scratch_capacities());
            })
        })
        .collect::<Vec<_>>();
    for thread in threads {
        thread.join().unwrap();
    }
}
