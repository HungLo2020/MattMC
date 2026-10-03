use super::{coordinates::Coordinates, intersection::Intersection};
use std::cell::RefCell;

thread_local! {
    // Private endpoint scratch, not a geometry/query cache. Only this call's
    // freshly extracted prefix is read; at most 65536 boxes / 1.5 MiB.
    static BOXES: RefCell<Vec<i32>> = const { RefCell::new(Vec::new()) };
}

#[inline(never)]
fn extract(words: &mut [u64], dims: [usize; 3], output: &mut [i32]) -> usize {
    let mut at = 0;
    super::super::box_extract::extract::visit(words, dims, |box_| {
        output[at..at + 6].copy_from_slice(&box_);
        at += 6;
    })
}

#[cfg(test)]
pub(super) fn evaluate(words: &mut [u64], dims: [usize; 3], coordinates: &[f64],
    cubes: u32, ray: &[f64; 9], delta: [f64; 3]) -> Intersection {
    let ordered = super::coordinates::ordered(coordinates, dims, cubes, ray).unwrap();
    evaluate_validated(words, dims, coordinates, cubes, ray, delta, ordered)
}

pub(super) fn evaluate_validated(words: &mut [u64], dims: [usize; 3], coordinates: &[f64],
    cubes: u32, ray: &[f64; 9], delta: [f64; 3], ordered: bool) -> Intersection {
    match cubes {
        0 => evaluate_mode::<0>(words, dims, coordinates, ray, delta, ordered),
        1 => evaluate_mode::<1>(words, dims, coordinates, ray, delta, ordered),
        2 => evaluate_mode::<2>(words, dims, coordinates, ray, delta, ordered),
        3 => evaluate_mode::<3>(words, dims, coordinates, ray, delta, ordered),
        4 => evaluate_mode::<4>(words, dims, coordinates, ray, delta, ordered),
        5 => evaluate_mode::<5>(words, dims, coordinates, ray, delta, ordered),
        6 => evaluate_mode::<6>(words, dims, coordinates, ray, delta, ordered),
        7 => evaluate_mode::<7>(words, dims, coordinates, ray, delta, ordered),
        _ => unreachable!("FFI validates coordinate layout"),
    }
}

fn evaluate_mode<const CUBES: u32>(words: &mut [u64], dims: [usize; 3], values: &[f64],
    ray: &[f64; 9], delta: [f64; 3], ordered: bool) -> Intersection {
    let coordinates = Coordinates::new(dims, values);
    let mut hit = Intersection::new();
    if ordered && super::miss::proven::<CUBES>(&coordinates, dims, ray, delta) { return hit; }
    if dims.iter().product::<usize>() >= 4096 {
        return BOXES.with(|storage| {
            let mut boxes = storage.borrow_mut();
            let capacity = dims.iter().product::<usize>() * 6;
            if boxes.len() < capacity {
                // Avoid Vec's geometric growth retaining more than the cap.
                let additional = capacity - boxes.len();
                boxes.reserve_exact(additional);
                boxes.resize(capacity, 0);
            }
            let count = extract(words, dims, &mut boxes);
            if ordered && count >= 64 {
                let planes = super::prepared::Planes::new::<CUBES>(&coordinates, dims, ray, delta);
                for box_ in boxes[..count * 6].chunks_exact(6) {
                    planes.consider(&mut hit, box_.try_into().unwrap(), ray, delta);
                }
            } else {
                for box_ in boxes[..count * 6].chunks_exact(6) {
                    let box_ = box_.try_into().unwrap();
                    if ordered { hit.consider_coordinates::<CUBES>(&coordinates, box_, ray, delta); }
                    else { hit.consider(std::array::from_fn(|i| coordinates.get::<CUBES>(i % 3, box_[i])), ray, delta); }
                }
            }
            hit
        });
    }
    super::super::box_extract::extract::visit(words, dims, |box_| {
        if ordered { hit.consider_coordinates::<CUBES>(&coordinates, box_, ray, delta); }
        else { hit.consider(std::array::from_fn(|i| coordinates.get::<CUBES>(i % 3, box_[i])), ray, delta); }
    });
    hit
}
