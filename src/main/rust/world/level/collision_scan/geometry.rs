//! Static world collision policy. Both grids retain empty intervals because
//! the ordered epsilon merge is not equivalent to intersecting merged boxes.
use crate::content::block::collision::WorldGeometry;
const EPS: f64 = 1.0e-7;

#[derive(Clone, Copy)]
pub(super) struct Axis {
    coords: [f64; 9],
    length: usize,
    filled: [usize; 2],
}
#[derive(Clone, Copy)]
pub(super) struct Query { axes: [Axis; 3], bounds: [f64; 6] }

pub(super) fn query_bounds(bounds: [f64; 6]) -> Option<Query> {
    if (0..3).any(|a| bounds[a+3]-bounds[a] < EPS) { return None; }
    let bits: [i32; 3] = std::array::from_fn(|a| {
        if bounds[a] < -EPS || bounds[a+3] > 1.0000001 { return -1; }
        for bits in 0..=3 {
            let scale = (1 << bits) as f64;
            let min = bounds[a]*scale; let max = bounds[a+3]*scale;
            if (min-(min+0.5).floor()).abs() < EPS*scale && (max-(max+0.5).floor()).abs() < EPS*scale { return bits; }
        }
        -1
    });
    let array = bits.iter().any(|&v| v < 0);
    let axes = std::array::from_fn(|a| {
        let mut coords = [0.; 9];
        if array {
            coords[0] = bounds[a]; coords[1] = bounds[a+3];
            Axis { coords, length: 2, filled: [0, 1] }
        } else {
            let size = 1 << bits[a];
            for (i, v) in coords[..=size].iter_mut().enumerate() { *v = i as f64 / size as f64; }
            // Shapes.create's zero-bit shortcut returns BLOCK even when both
            // input endpoints round to zero.
            let filled = if bits == [0; 3] { [0, 1] } else {
                [(bounds[a]*size as f64+0.5).floor() as usize, (bounds[a+3]*size as f64+0.5).floor() as usize]
            };
            Axis { coords, length: size+1, filled }
        }
    });
    if axes.iter().any(|a| a.filled[0] >= a.filled[1]) { return None; }
    Some(Query { bounds: std::array::from_fn(|i| axes[i%3].coords[axes[i%3].filled[i/3]]), axes })
}

fn axis_mask(a: &[f64], offset: f64, b: Axis) -> u64 {
    let mut first=0; let mut second=0; let mut previous=f64::NAN;
    let mut previous_indices=(usize::MAX,usize::MAX); let mut emitted=false;
    let mut mask = 0;
    while first<a.len() || second<b.length {
        let first_done=first==a.len(); let second_done=second==b.length;
        let take_first=!first_done && (second_done || a[first]+offset < b.coords[second]+EPS);
        if take_first {
            first+=1;
            if second==0 || second_done { continue; }
        } else {
            second+=1;
            if first==0 || first_done { continue; }
        }
        let indices=(first-1,second-1);
        let coordinate=if take_first { a[indices.0]+offset } else { b.coords[indices.1] };
        if !(previous >= coordinate-EPS) {
            if emitted && previous_indices.0+1<a.len()
                && (b.filled[0]..b.filled[1]).contains(&previous_indices.1) {
                mask |= 1 << previous_indices.0;
            }
            previous=coordinate; emitted=true;
        }
        previous_indices=indices;
    }
    mask
}

pub(super) fn intersects(shape: WorldGeometry<'_>, point: [i32;3], edge: usize,
    raw: [f64;6], query: Option<&Query>) -> bool {
    if (edge==1 && !shape.large) || edge==2 { return false; }
    if shape.full_block_identity {
        return (0..3).all(|a| raw[a] < point[a] as f64+1.0 && raw[a+3] > point[a] as f64);
    }
    let Some(query)=query else { return false; };
    let grid = shape.grid;
    if grid.boxes.is_empty() || (0..3).any(|a| grid.bounds[a+3]+(point[a] as f64) < query.bounds[a]-EPS
        || query.bounds[a+3] < grid.bounds[a]+point[a] as f64-EPS) { return false; }
    let masks: [u64; 3] = std::array::from_fn(|a| axis_mask(&grid.axes[a], point[a] as f64, query.axes[a]));
    grid.boxes.iter().any(|b| (0..3).all(|a| masks[a] & b[a] != 0))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn snapping_retains_zero_bit_shortcut_and_thin_query_rejection() {
        assert_eq!(query_bounds([-4e-8,-4e-8,-4e-8,6e-8,6e-8,6e-8]).unwrap().bounds,[0.,0.,0.,1.,1.,1.]);
        assert!(query_bounds([0.,0.,0.,1e-8,1.,1.]).is_none());
        assert_eq!(query_bounds([0.,0.,0.,0.5,1.,1.]).unwrap().bounds,[0.,0.,0.,0.5,1.,1.]);
    }
    #[test]
    fn empty_grid_intervals_preserve_epsilon_merge_history() {
        let query = query_bounds([0.1,0.4999999,0.1,0.9,0.5000001,0.9]).unwrap();
        // Frozen top-slab regression: dropping the leading empty interval
        // changes which coordinate anchors the epsilon collapse.
        assert_eq!(axis_mask(&[0.,0.5,1.],0.,query.axes[1]) & 2,2);
        assert_eq!(axis_mask(&[0.5,1.],0.,query.axes[1]),0);
    }
    #[test]
    fn static_join_and_full_block_identity_keep_distinct_contact_rules() {
        use crate::content::block::collision::WorldGrid;
        let grid = WorldGrid { axes: std::array::from_fn(|_|vec![0.,1.]),boxes:vec![[1;3]],bounds:[0.,0.,0.,1.,1.,1.] };
        let raw=[1.-1e-8,0.,0.,2.,1.,1.];
        assert!(intersects(WorldGeometry {grid:&grid,large:false,full_block_identity:true},[0;3],0,raw,query_bounds(raw).as_ref()));
        assert!(!intersects(WorldGeometry {grid:&grid,large:false,full_block_identity:false},[0;3],0,raw,query_bounds(raw).as_ref()));
    }
}
