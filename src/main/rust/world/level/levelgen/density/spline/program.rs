#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(crate) struct Node {
    pub axis: i32, // -1: constant
    pub start: u32,
    pub count: u32,
    pub value: f32,
}
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(crate) struct Knot {
    pub location: f32,
    pub derivative: f32,
    pub child: u32,
}
pub(crate) const MAX_NODES: usize = 4096;
pub(crate) const MAX_KNOTS: usize = 16384;
pub(crate) fn valid(nodes: &[Node], knots: &[Knot]) -> bool {
    if nodes.is_empty() || nodes.len() > MAX_NODES || knots.len() > MAX_KNOTS {
        return false;
    }
    let mut depths = vec![0u32; nodes.len()];
    let mut visits = vec![0u32; nodes.len()];
    for (i, n) in nodes.iter().enumerate() {
        if n.axis == -1 {
            if n.count != 0 {
                return false;
            }
            visits[i] = 1;
        } else {
            if !(0..4).contains(&n.axis) || n.count == 0 {
                return false;
            }
            let Some(end) = (n.start as usize).checked_add(n.count as usize) else {
                return false;
            };
            let Some(ks) = knots.get(n.start as usize..end) else {
                return false;
            };
            let mut largest = 0;
            let mut second = 0;
            for k in ks {
                if k.child as usize >= i {
                    return false;
                }
                depths[i] = depths[i].max(depths[k.child as usize] + 1);
                let v = visits[k.child as usize];
                if v >= largest {
                    second = largest;
                    largest = v;
                } else {
                    second = second.max(v);
                }
            }
            visits[i] = 1 + largest + second;
            if depths[i] > 64 || visits[i] > 4096 {
                return false;
            }
        }
    }
    true
}
