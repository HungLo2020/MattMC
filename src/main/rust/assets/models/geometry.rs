//! Geometry primitives for model baking: directions, quadrants, the
//! vertex order of each face (`FaceInfo`), and the JOML float operations
//! `FaceBakery` uses, in JOML 1.10.5's operation order (its `Math.fma` is
//! a multiply then an add unless `joml.useMathFma` is set, which the game
//! does not set).

/// `Direction`, in ordinal (and `get3DDataValue`) order.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub(crate) enum Direction {
    Down = 0,
    Up = 1,
    North = 2,
    South = 3,
    West = 4,
    East = 5,
}

impl Direction {
    pub(crate) const ALL: [Direction; 6] = [Direction::Down, Direction::Up, Direction::North, Direction::South, Direction::West, Direction::East];

    /// `Direction.byName`: the serialized name, case sensitive.
    pub(crate) fn by_name(name: &str) -> Option<Direction> {
        Some(match name {
            "down" => Direction::Down,
            "up" => Direction::Up,
            "north" => Direction::North,
            "south" => Direction::South,
            "west" => Direction::West,
            "east" => Direction::East,
            _ => return None,
        })
    }

    pub(crate) fn index(self) -> usize {
        self as usize
    }

    /// `getUnitVec3f`.
    pub(crate) fn unit(self) -> [f32; 3] {
        match self {
            Direction::Down => [0.0, -1.0, 0.0],
            Direction::Up => [0.0, 1.0, 0.0],
            Direction::North => [0.0, 0.0, -1.0],
            Direction::South => [0.0, 0.0, 1.0],
            Direction::West => [-1.0, 0.0, 0.0],
            Direction::East => [1.0, 0.0, 0.0],
        }
    }

    /// Sodium's `getApproximateNearest` (vanilla's ties: Y, then Z, then X;
    /// negative before positive; all-zero is north).
    pub(crate) fn approximate_nearest(x: f32, y: f32, z: f32) -> Direction {
        if x == 0.0 && y == 0.0 && z == 0.0 {
            return Direction::North;
        }
        let (ym, zm, xm) = (y.abs(), z.abs(), x.abs());
        if ym >= zm {
            if ym >= xm {
                return if y <= 0.0 { Direction::Down } else { Direction::Up };
            }
        } else if zm >= xm {
            return if z <= 0.0 { Direction::North } else { Direction::South };
        }
        if x <= 0.0 {
            Direction::West
        } else {
            Direction::East
        }
    }
}

/// `Direction.Axis`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub(crate) enum Axis {
    X,
    Y,
    Z,
}

impl Axis {
    /// `Axis.byName` (lowercased by the caller).
    pub(crate) fn by_name(name: &str) -> Option<Axis> {
        Some(match name {
            "x" => Axis::X,
            "y" => Axis::Y,
            "z" => Axis::Z,
            _ => return None,
        })
    }

    pub(crate) fn index(self) -> u32 {
        self as u32
    }
}

/// `Quadrant`: a quarter-turn count.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub(crate) struct Quadrant(pub(crate) u8);

impl Quadrant {
    /// `Quadrant.parseJson` / `CODEC`: degrees, modulo 360, a multiple of 90.
    pub(crate) fn from_degrees(degrees: i32) -> Option<Quadrant> {
        match degrees.rem_euclid(360) {
            0 => Some(Quadrant(0)),
            90 => Some(Quadrant(1)),
            180 => Some(Quadrant(2)),
            270 => Some(Quadrant(3)),
            _ => None,
        }
    }

    pub(crate) fn rotate_vertex_index(self, index: usize) -> usize {
        (index + self.0 as usize) % 4
    }
}

/// `FaceInfo.Constants`: indices into a shape array of six bounds.
const MIN_Y: usize = 0;
const MAX_Y: usize = 1;
const MIN_Z: usize = 2;
const MAX_Z: usize = 3;
const MIN_X: usize = 4;
const MAX_X: usize = 5;

/// `FaceInfo`: each face's corners as (x, y, z) shape indices.
pub(crate) fn face_info(direction: Direction) -> [[usize; 3]; 4] {
    match direction {
        Direction::Down => [[MIN_X, MIN_Y, MAX_Z], [MIN_X, MIN_Y, MIN_Z], [MAX_X, MIN_Y, MIN_Z], [MAX_X, MIN_Y, MAX_Z]],
        Direction::Up => [[MIN_X, MAX_Y, MIN_Z], [MIN_X, MAX_Y, MAX_Z], [MAX_X, MAX_Y, MAX_Z], [MAX_X, MAX_Y, MIN_Z]],
        Direction::North => [[MAX_X, MAX_Y, MIN_Z], [MAX_X, MIN_Y, MIN_Z], [MIN_X, MIN_Y, MIN_Z], [MIN_X, MAX_Y, MIN_Z]],
        Direction::South => [[MIN_X, MAX_Y, MAX_Z], [MIN_X, MIN_Y, MAX_Z], [MAX_X, MIN_Y, MAX_Z], [MAX_X, MAX_Y, MAX_Z]],
        Direction::West => [[MIN_X, MAX_Y, MIN_Z], [MIN_X, MIN_Y, MIN_Z], [MIN_X, MIN_Y, MAX_Z], [MIN_X, MAX_Y, MAX_Z]],
        Direction::East => [[MAX_X, MAX_Y, MAX_Z], [MAX_X, MIN_Y, MAX_Z], [MAX_X, MIN_Y, MIN_Z], [MAX_X, MAX_Y, MIN_Z]],
    }
}

/// A shape array (`FaceBakery.setupShape`): bounds in block units.
pub(crate) fn shape(from: [f32; 3], to: [f32; 3]) -> [f32; 6] {
    let mut shape = [0.0f32; 6];
    shape[MIN_X] = from[0] / 16.0;
    shape[MIN_Y] = from[1] / 16.0;
    shape[MIN_Z] = from[2] / 16.0;
    shape[MAX_X] = to[0] / 16.0;
    shape[MAX_Y] = to[1] / 16.0;
    shape[MAX_Z] = to[2] / 16.0;
    shape
}

/// The six shape indices for `recalculateWinding`'s bounds.
pub(crate) const SHAPE_MIN_X: usize = MIN_X;
pub(crate) const SHAPE_MIN_Y: usize = MIN_Y;
pub(crate) const SHAPE_MIN_Z: usize = MIN_Z;
pub(crate) const SHAPE_MAX_X: usize = MAX_X;
pub(crate) const SHAPE_MAX_Y: usize = MAX_Y;
pub(crate) const SHAPE_MAX_Z: usize = MAX_Z;

/// A JOML `Matrix4f` in column-major fields (`m00, m01, ... m33`, as
/// `Matrix4f.get(float[])` writes them).
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct Matrix(pub(crate) [f32; 16]);

impl Matrix {
    #[inline]
    fn m(&self, column: usize, row: usize) -> f32 {
        self.0[column * 4 + row]
    }

    /// `Vector3f.mulPosition(matrix)`.
    pub(crate) fn transform_position(&self, v: [f32; 3]) -> [f32; 3] {
        let [x, y, z] = v;
        [
            self.m(0, 0) * x + (self.m(1, 0) * y + (self.m(2, 0) * z + self.m(3, 0))),
            self.m(0, 1) * x + (self.m(1, 1) * y + (self.m(2, 1) * z + self.m(3, 1))),
            self.m(0, 2) * x + (self.m(1, 2) * y + (self.m(2, 2) * z + self.m(3, 2))),
        ]
    }

    /// `Vector3f.mulDirection(matrix)`.
    pub(crate) fn transform_direction(&self, v: [f32; 3]) -> [f32; 3] {
        let [x, y, z] = v;
        [
            self.m(0, 0) * x + (self.m(1, 0) * y + self.m(2, 0) * z),
            self.m(0, 1) * x + (self.m(1, 1) * y + self.m(2, 1) * z),
            self.m(0, 2) * x + (self.m(1, 2) * y + self.m(2, 2) * z),
        ]
    }

    /// `Direction.rotate(matrix, direction)`.
    pub(crate) fn rotate(&self, direction: Direction) -> Direction {
        let [x, y, z] = self.transform_direction(direction.unit());
        Direction::approximate_nearest(x, y, z)
    }
}

/// `Vector3f.sub`.
#[inline]
pub(crate) fn sub(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
}

/// `Vector3f.add`.
#[inline]
pub(crate) fn add(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[0] + b[0], a[1] + b[1], a[2] + b[2]]
}

/// `Vector3f.mul(Vector3fc)`.
#[inline]
pub(crate) fn mul(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[0] * b[0], a[1] * b[1], a[2] * b[2]]
}

/// `Vector3f.cross`: `fma(y, v.z, -z * v.y)` and so on.
#[inline]
pub(crate) fn cross(a: [f32; 3], b: [f32; 3]) -> [f32; 3] {
    [a[1] * b[2] + (-a[2] * b[1]), a[2] * b[0] + (-a[0] * b[2]), a[0] * b[1] + (-a[1] * b[0])]
}

/// `Vector3f.normalize`: scaled by `1 / (float) Math.sqrt(length²)`.
#[inline]
pub(crate) fn normalize(v: [f32; 3]) -> [f32; 3] {
    let length = v[0] * v[0] + (v[1] * v[1] + v[2] * v[2]);
    let scalar = 1.0f32 / (f64::from(length).sqrt() as f32);
    [v[0] * scalar, v[1] * scalar, v[2] * scalar]
}

/// `Vector3f.dot`.
#[inline]
pub(crate) fn dot(a: [f32; 3], b: [f32; 3]) -> f32 {
    a[0] * b[0] + (a[1] * b[1] + a[2] * b[2])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn directions_and_quadrants() {
        assert_eq!(Direction::Up, Direction::approximate_nearest(0.0, 1.0, 1.0));
        assert_eq!(Direction::North, Direction::approximate_nearest(0.0, 0.0, 0.0));
        assert_eq!(Direction::South, Direction::approximate_nearest(0.5, 0.0, 0.5));
        assert_eq!(Direction::West, Direction::approximate_nearest(-1.0, 0.5, 0.2));
        assert_eq!(Some(Quadrant(3)), Quadrant::from_degrees(-90));
        assert_eq!(None, Quadrant::from_degrees(45));
        assert_eq!(1, Quadrant(1).rotate_vertex_index(0));
        let identity = Matrix([1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]);
        assert_eq!(Direction::East, identity.rotate(Direction::East));
        assert_eq!([0.0, 0.0, 1.0], normalize(cross([1.0, 0.0, 0.0], [0.0, 1.0, 0.0])));
    }
}
