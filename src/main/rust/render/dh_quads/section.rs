//! One complete DH render-section build: column traversal, neighbor selection,
//! debug coloring, semantic provenance and box production into the quad owner.
//! Java supplies per-build facts once; no callback runs during the traversal.
use super::{boxes, faces, Builder, Policy, Quad};
use crate::render::dh_columns::source::Source;

const WIDTH: usize = 64;
const MATERIAL_UNAVAILABLE: i32 = 0;
const VARIANT_UNAVAILABLE: u8 = 0;
const VARIANT_EXACT: u8 = 1;
const WATER: u8 = 12;

// Parameter block indices; mirrored by NativeDhQuadBuilder.buildSection.
pub(super) const P_HEIGHT: usize = 0;
pub(super) const P_DETAIL: usize = 1;
pub(super) const P_NEIGHBOR_HEIGHT: usize = 2; // 4 slots, -1 when absent
pub(super) const P_NEIGHBOR_DETAIL: usize = 6; // 4 slots
pub(super) const P_SAME_DETAIL: usize = 10;
pub(super) const P_COLOR_MODE: usize = 11;
pub(super) const P_SATURATION: usize = 12;
pub(super) const P_BRIGHTNESS: usize = 13;
pub(super) const P_DETAIL_COLOR: usize = 14;
pub(super) const P_CAVE_Y: usize = 15;
pub(super) const P_FLAGS: usize = 16;
pub(super) const P_SHADE_FACTS: usize = 17;
pub(super) const P_POLICY: usize = 18;
pub(super) const P_WANT_X: usize = 19;
pub(super) const P_WANT_Z: usize = 20;
pub(super) const P_WANT_INDEX: usize = 21;
pub(super) const P_BLOCK_X: usize = 22;
pub(super) const P_BLOCK_Z: usize = 23;
pub(super) const P_Y_OFFSET: usize = 24;
pub(super) const P_MATERIALS: usize = 25;
pub(super) const P_INTERN_BASE: usize = 26;
pub(super) const PARAMS: usize = 27;

pub(super) const F_TRANSPARENCY: i64 = 1;
pub(super) const F_FAKE_OCEAN: i64 = 2;
pub(super) const F_WHOLE_FRAME: i64 = 4;
pub(super) const F_EXACT_TOPOLOGY: i64 = 8;
pub(super) const F_CONTRIBUTOR_BOXES: i64 = 16;
pub(super) const F_DEBUG_FILTER: i64 = 32;

/// Color modes follow EDhApiDebugRendering: normal, detail, material, overlap, source flag.
const MODE_NORMAL: i64 = 0;
const MODE_DETAIL: i64 = 1;
const MODE_MATERIAL: i64 = 2;
const MODE_OVERLAP: i64 = 3;

/// Errors match Java's exceptions: -2 zero size, -3 negative size, -4 negative point height.
pub(super) const NEGATIVE_POINT_HEIGHT: i32 = -4;

fn exists(p: u64) -> bool {
    p != 0
}
fn zero_height(p: u64) -> bool {
    (p >> 8) & 0xff_ffff == 0
}
fn y_min(p: u64) -> i16 {
    ((p >> 8) & 4095) as i16
}
fn y_max(p: u64) -> i16 {
    ((p >> 20) & 4095) as i16
}
fn material(p: u64) -> u8 {
    (p >> 60) as u8
}
fn color(p: u64) -> u32 {
    let alpha = (((p >> 56) & 15) << 4) | 15;
    (((p >> 32) & 0xff_ffff) | (alpha << 24)) as u32
}
fn transparent(p: u64, enabled: bool) -> bool {
    exists(p) && enabled && ((p >> 56) & 15 != 15 || material(p) == WATER)
}
fn water_surface_occluding(material: u8) -> bool {
    matches!(material, 2 | 3 | 4 | 5 | 6 | 7 | 9)
}

/// Java's ColorUtil.argbToInt(float...) with saturating float-to-int casts.
fn argb(a: f32, r: f32, g: f32, b: f32) -> u32 {
    let c = |v: f32| (v * 255.0) as i32;
    (c(a).wrapping_shl(24) | c(r).wrapping_shl(16) | c(g).wrapping_shl(8) | c(b)) as u32
}

/// ColorUtil.argbToAhsv then ahsvToArgb with scaled saturation and value.
fn adjust(color: u32, saturation: f32, brightness: f32) -> u32 {
    let a = ((color >> 24) & 255) as f32 / 255.0;
    let r = ((color >> 16) & 255) as f32 / 255.0;
    let g = ((color >> 8) & 255) as f32 / 255.0;
    let b = (color & 255) as f32 / 255.0;
    let min = r.min(g).min(b);
    let max = r.max(g).max(b);
    let (h, s, v) = if max != 0.0 {
        let delta = max - min;
        let s = delta / max;
        let h = if delta == 0.0 {
            0.0
        } else {
            let mut h = if r == max {
                (g - b) / delta
            } else if g == max {
                2.0 + (b - r) / delta
            } else {
                4.0 + (r - g) / delta
            };
            h *= 60.0;
            if h < 0.0 {
                h += 360.0;
            }
            h
        };
        (h, s, max)
    } else {
        (0.0, 0.0, 0.0)
    };
    let (mut a, mut h, mut s, mut v) = (a, h, s * saturation, v * brightness);
    if a > 1.0 {
        a = 1.0;
    }
    if h > 360.0 {
        h -= 350.0;
    }
    if s > 1.0 {
        s = 1.0;
    }
    if v > 1.0 {
        v = 1.0;
    }
    if s == 0.0 {
        return argb(a, v, v, v);
    }
    h /= 60.0;
    let i = (f64::from(h)).floor() as i32;
    let f = h - i as f32;
    let p = v * (1.0 - s);
    let q = v * (1.0 - s * f);
    let t = v * (1.0 - s * (1.0 - f));
    match i {
        0 => argb(a, v, t, p),
        1 => argb(a, q, v, p),
        2 => argb(a, p, v, t),
        3 => argb(a, p, q, v),
        4 => argb(a, t, p, v),
        _ => argb(a, v, p, q),
    }
}

fn material_color(material: u8) -> u32 {
    const fn rgb(r: u32, g: u32, b: u32) -> u32 {
        0xff00_0000 | r << 16 | g << 8 | b
    }
    match material {
        0 | 14 => rgb(255, 105, 180), // unknown, air: hot pink
        1 => rgb(80, 140, 80),        // leaves
        2 => rgb(128, 128, 128),      // stone
        3 => rgb(68, 46, 24),         // wood
        4 => rgb(64, 64, 64),         // metal
        5 => rgb(130, 112, 67),       // dirt
        6 => rgb(255, 128, 0),        // lava
        7 => rgb(0, 0, 0),            // deepslate
        8 => rgb(255, 255, 255),      // snow
        9 => rgb(183, 165, 119),      // sand
        10 => rgb(125, 62, 0),        // terracotta
        11 => rgb(100, 0, 0),         // nether stone
        WATER => rgb(0, 0, 255),
        13 => rgb(0, 255, 0),   // grass
        15 => rgb(255, 255, 0), // illuminated
        _ => rgb(0, 255, 255),
    }
}

/// One semantic span: [start, end), builder or source identity, variant and position.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
struct Span {
    min: i32,
    max: i32,
    identity: i32,
    variant: u8,
    position: i64,
}
impl Span {
    fn words(self) -> [u64; 3] {
        [
            (self.min as u32 as u64) | (self.max as u32 as u64) << 32,
            (self.identity as u32 as u64) | u64::from(self.variant) << 32,
            self.position as u64,
        ]
    }
}

/// Sparse semantic sidecars of the center source, keyed by data-point index.
pub(super) struct Sidecars<'a> {
    /// [index, count, count * 3 span words]... sorted by index.
    pub spans: &'a [i64],
    /// [index, then four contributors: count (-1 null), count * 3 words]... sorted.
    pub contributors: &'a [i64],
}
fn read_span(words: &[i64]) -> Span {
    Span {
        min: words[0] as i32,
        max: (words[0] >> 32) as i32,
        identity: words[1] as i32,
        variant: (words[1] >> 32) as u8,
        position: words[2],
    }
}
struct Cursor<'a> {
    table: &'a [i64],
    at: usize,
}
impl<'a> Cursor<'a> {
    /// Advances monotonically: data points are visited in increasing index order.
    fn find(&mut self, index: i64, record_len: impl Fn(&[i64]) -> Option<usize>) -> Option<&'a [i64]> {
        while self.at < self.table.len() {
            let key = self.table[self.at];
            let len = record_len(&self.table[self.at..])?;
            if key < index {
                self.at += len;
                continue;
            }
            return (key == index).then(|| &self.table[self.at..self.at + len]);
        }
        None
    }
}
fn span_record_len(r: &[i64]) -> Option<usize> {
    let count = usize::try_from(*r.get(1)?).ok()?;
    let len = 2 + count.checked_mul(3)?;
    (len <= r.len()).then_some(len)
}
fn contributor_record_len(r: &[i64]) -> Option<usize> {
    let mut at = 1;
    for _ in 0..4 {
        let count = *r.get(at)?;
        at += 1;
        if count > 0 {
            at = at.checked_add(usize::try_from(count).ok()?.checked_mul(3)?)?;
        }
    }
    (at <= r.len()).then_some(at)
}
/// Four optional contributors, each an ordered span list.
fn contributors(record: &[i64]) -> [Option<Vec<Span>>; 4] {
    let mut at = 1;
    std::array::from_fn(|_| {
        let count = record[at];
        at += 1;
        if count < 0 {
            return None;
        }
        let spans = (0..count as usize).map(|i| read_span(&record[at + i * 3..])).collect();
        at += count as usize * 3;
        Some(spans)
    })
}

/// ColumnRenderBufferBuilder.clippedContributorSpans.
fn clipped(spans: &[Span], min: i32, max: i32) -> Vec<Span> {
    let mut out: Vec<Span> = Vec::new();
    for span in spans {
        let (lo, hi) = (min.max(span.min), max.min(span.max));
        if hi > lo {
            if out.last().is_some_and(|last| last.max != lo) {
                return Vec::new();
            }
            out.push(Span { min: lo, max: hi, ..*span });
        }
    }
    out
}

/// recoverCommonHorizontalSpans: one shared clipped layer sequence, else empty.
fn common_spans(data: u64, contributors: Option<&[Option<Vec<Span>>; 4]>) -> Vec<Span> {
    let Some(contributors) = contributors.filter(|_| exists(data)) else {
        return Vec::new();
    };
    let (min, max) = (i32::from(y_min(data)), i32::from(y_max(data)));
    let mut first: Option<Vec<Span>> = None;
    for contributor in contributors {
        // A missing contributor cannot agree with the others.
        let Some(spans) = contributor else {
            return Vec::new();
        };
        let clipped = clipped(spans, min, max);
        if clipped.is_empty() || clipped[0].min != min || clipped[clipped.len() - 1].max != max {
            return Vec::new();
        }
        match &first {
            None => first = Some(clipped),
            Some(first) if *first != clipped => return Vec::new(),
            _ => {}
        }
    }
    first.unwrap_or_default()
}

/// recoverCommonHorizontalProvenance: one exact identity across the whole footprint.
fn common_provenance(
    data: u64,
    contributors: Option<&[Option<Vec<Span>>; 4]>,
    fallback: (i32, u8, i64),
) -> (i32, u8, i64) {
    let Some(contributors) = contributors.filter(|_| exists(data)) else {
        return fallback;
    };
    let (min, max) = (i32::from(y_min(data)), i32::from(y_max(data)));
    let mut first: Option<Span> = None;
    for contributor in contributors {
        let Some(spans) = contributor else {
            return fallback;
        };
        let spans = clipped(spans, min, max);
        if spans.len() != 1 {
            return fallback;
        }
        let span = spans[0];
        if span.min != min || span.max != max || span.identity <= MATERIAL_UNAVAILABLE || span.variant != VARIANT_EXACT {
            return fallback;
        }
        match first {
            None => first = Some(span),
            Some(f) if span.identity != f.identity || span.position != f.position => return fallback,
            _ => {}
        }
    }
    match first {
        Some(f) => (f.identity, VARIANT_EXACT, f.position),
        None => fallback,
    }
}

/// Builder-local identities in first-use order, as LodQuadBuilder.internSemanticMaterial.
struct Interner<'a> {
    remap: Vec<i32>,
    order: &'a mut [i32],
    used: usize,
    base: i32,
}
impl Interner<'_> {
    fn intern(&mut self, source: i32) -> i32 {
        if source <= MATERIAL_UNAVAILABLE || source as usize >= self.remap.len() {
            return MATERIAL_UNAVAILABLE;
        }
        let slot = &mut self.remap[source as usize];
        if *slot != 0 {
            return *slot;
        }
        if self.used >= self.order.len() {
            return MATERIAL_UNAVAILABLE;
        }
        self.order[self.used] = source;
        self.used += 1;
        *slot = self.base + self.used as i32;
        *slot
    }
}

pub(super) struct Section<'a> {
    pub params: &'a [i64; PARAMS],
    pub center: &'a Source,
    pub neighbors: [Option<&'a Source>; 4],
    pub min_x: &'a [i16; WIDTH],
    pub min_z: &'a [i16; WIDTH],
    /// Per-column debug source color, only for the source-flag mode.
    pub flag_colors: Option<&'a [u32]>,
    pub sidecars: Sidecars<'a>,
}

/// Returns 1 + the interned identity count, 0 on failure, or a negative size status.
pub(super) fn build(owner: &mut Builder, s: &Section, order: &mut [i32]) -> i32 {
    let p = s.params;
    let height = p[P_HEIGHT] as usize;
    let detail = p[P_DETAIL] as u32;
    let flags = p[P_FLAGS];
    let transparency = flags & F_TRANSPARENCY != 0;
    let fake_ocean = flags & F_FAKE_OCEAN != 0;
    let whole_frame = flags & F_WHOLE_FRAME != 0;
    let contributor_boxes = detail > 0 && flags & F_EXACT_TOPOLOGY != 0 && flags & F_CONTRIBUTOR_BOXES != 0;
    let debug_filter = flags & F_DEBUG_FILTER != 0;
    let mode = p[P_COLOR_MODE];
    let saturation = f32::from_bits(p[P_SATURATION] as u32);
    let brightness = f32::from_bits(p[P_BRIGHTNESS] as u32);
    let cave_y = p[P_CAVE_Y] as i32;
    let policy = Policy {
        exact: p[P_POLICY] & (1 << 49) != 0,
        overlap_errors: p[P_POLICY] & (1 << 50) != 0,
        curvature: p[P_POLICY] & (1 << 51) != 0,
    };
    let width = 1i16 << detail;
    let points = s.center.points();
    let materials = s.center.materials();
    let variants = s.center.variants();
    let uniform = s.center.uniform();
    // A source with no planes reads zero: unavailable material, unavailable variant.
    let material_at = |i: usize| materials.map_or(0, |m| i32::from(m[i]));
    let variant_at = |i: usize| variants.map_or(0, |v| v[i]);
    let uniform_at = |column: usize| uniform.is_some_and(|u| u[column] != 0);
    let mut interner = Interner {
        remap: vec![0; p[P_MATERIALS].max(0) as usize + 1],
        order,
        used: 0,
        base: p[P_INTERN_BASE] as i32,
    };
    let mut spans_cursor = Cursor { table: s.sidecars.spans, at: 0 };
    let mut contributors_cursor = Cursor { table: s.sidecars.contributors, at: 0 };
    let neighbor_column = |direction: usize, x: usize, z: usize| -> Option<&[u64]> {
        let source = s.neighbors[direction]?;
        // Cross-detail neighbors fall outside Java's view bounds and read as absent.
        if p[P_NEIGHBOR_DETAIL + direction] != p[P_DETAIL] {
            return None;
        }
        let h = p[P_NEIGHBOR_HEIGHT + direction] as usize;
        let start = (x * WIDTH + z) * h;
        source.points().get(start..start + h)
    };
    for x in 0..WIDTH {
        for z in 0..WIDTH {
            let column = x * WIDTH + z;
            let first = column * height;
            let Some(data_column) = points.get(first..first + height) else {
                return 0;
            };
            if height == 0 || !exists(data_column[0]) || zero_height(data_column[0]) {
                continue;
            }
            if debug_filter
                && (p[P_WANT_X] >= 0 && x as i64 != p[P_WANT_X] || p[P_WANT_Z] >= 0 && z as i64 != p[P_WANT_Z])
            {
                continue;
            }
            // N, S, W, E.
            let neighbors: [Option<&[u64]>; 4] = std::array::from_fn(|direction| {
                let (dx, dz) = [(0, -1), (0, 1), (-1, 0), (1, 0)][direction];
                let (ax, az) = (x as i32 + dx, z as i32 + dz);
                if (0..WIDTH as i32).contains(&ax) && (0..WIDTH as i32).contains(&az) {
                    let start = (ax as usize * WIDTH + az as usize) * height;
                    return points.get(start..start + height);
                }
                neighbor_column(direction, ax.rem_euclid(WIDTH as i32) as usize, az.rem_euclid(WIDTH as i32) as usize)
            });
            let horizontal_uniform = uniform_at(column);
            for i in 0..height {
                if debug_filter && p[P_WANT_INDEX] >= 0 && i as i64 != p[P_WANT_INDEX] {
                    continue;
                }
                let data = data_column[i];
                if zero_height(data) || !exists(data) {
                    break;
                }
                let index = first + i;
                let top = if i > 0 { data_column[i - 1] } else { 0 };
                let bottom = data_column.get(i + 1).copied().unwrap_or(0);
                // Semantic provenance of this entry, in source identities.
                let reduced_coarse = detail > 0 && !horizontal_uniform;
                let variant = variant_at(index);
                let mut provenance = if whole_frame && reduced_coarse {
                    (MATERIAL_UNAVAILABLE, VARIANT_UNAVAILABLE, 0i64)
                } else {
                    let position = if variant == VARIANT_EXACT {
                        let bx = p[P_BLOCK_X] as i32 + ((x as i32) << detail);
                        let bz = p[P_BLOCK_Z] as i32 + ((z as i32) << detail);
                        let by = i32::from(y_min(data)) + p[P_Y_OFFSET] as i32;
                        ((i64::from(bx) & 0x3ff_ffff) << 38) | ((i64::from(bz) & 0x3ff_ffff) << 12) | (i64::from(by) & 0xfff)
                    } else {
                        0
                    };
                    (material_at(index), variant, position)
                };
                let contributor_record = contributors_cursor.find(index as i64, contributor_record_len).map(contributors);
                let mut spans: Vec<Span> = Vec::new();
                if whole_frame && !reduced_coarse {
                    if let Some(record) = spans_cursor.find(index as i64, span_record_len) {
                        for k in 0..record[1] as usize {
                            let mut span = read_span(&record[2 + k * 3..]);
                            if span.identity > MATERIAL_UNAVAILABLE {
                                span.identity = interner.intern(span.identity);
                            }
                            spans.push(span);
                        }
                    }
                }
                if reduced_coarse {
                    let common = common_spans(data, contributor_record.as_ref());
                    if !common.is_empty() {
                        spans = common
                            .into_iter()
                            .map(|span| Span { identity: interner.intern(span.identity), ..span })
                            .collect();
                    }
                    provenance = common_provenance(data, contributor_record.as_ref(), provenance);
                }
                let mut identity = provenance.0;
                if identity > MATERIAL_UNAVAILABLE {
                    identity = interner.intern(identity);
                }
                // addRenderDataPointToBuilder.
                let y = y_min(data);
                let box_height = y_max(data) - y;
                if box_height == 0 {
                    continue;
                }
                if box_height < 0 {
                    return NEGATIVE_POINT_HEIGHT;
                }
                let block_material = material(data);
                let (color, full_bright) = match mode {
                    MODE_NORMAL if saturation == 1.0 && brightness == 1.0 => (color(data), false),
                    MODE_NORMAL => (adjust(color(data), saturation, brightness), false),
                    MODE_DETAIL => (p[P_DETAIL_COLOR] as u32, true),
                    MODE_MATERIAL => (material_color(block_material), true),
                    MODE_OVERLAP => (0xffff_ffff, true),
                    _ => (s.flag_colors.map_or(0xffff_0000, |c| c[column]), true),
                };
                let sky = (data & 15) as u8;
                let block = if full_bright { 15 } else { ((data >> 4) & 15) as u8 };
                let q = Quad {
                    x: s.min_x[x],
                    y,
                    z: s.min_z[z],
                    width,
                    height: box_height,
                    direction: 0,
                    material: block_material,
                    sky,
                    block,
                    error: 0,
                    variant: provenance.1,
                    color,
                    identity,
                    position: provenance.2,
                };
                if contributor_boxes {
                    match contributor_box(owner, &q, contributor_record.as_ref(), top, bottom, &neighbors, s.params, &mut interner, policy, transparency, fake_ocean, cave_y) {
                        Ok(true) => continue,
                        Ok(false) => {}
                        Err(status) => return status,
                    }
                }
                let span_words: Vec<u64> = spans.iter().flat_map(|s| s.words()).collect();
                let mut box_flags = u32::from(transparency) | u32::from(fake_ocean) << 1 | (p[P_SHADE_FACTS] as u32) << 8;
                for d in 0..4 {
                    if p[P_SAME_DETAIL] & (1 << d) != 0 {
                        box_flags |= 1 << (d + 2);
                    }
                }
                let status = boxes::produce(owner, q, top, bottom, neighbors, cave_y, box_flags, &span_words, [0; 3], policy);
                if status != 1 {
                    return status;
                }
            }
        }
    }
    1 + interner.used as i32
}

/// emitHorizontalContributorBoxes: Ok(true) when the four-cell footprint replaced
/// the box. As in Java, a later contributor can reject after earlier faces emitted.
#[allow(clippy::too_many_arguments)]
fn contributor_box(
    owner: &mut Builder,
    q: &Quad,
    contributors: Option<&[Option<Vec<Span>>; 4]>,
    top: u64,
    bottom: u64,
    neighbors: &[Option<&[u64]>; 4],
    params: &[i64; PARAMS],
    interner: &mut Interner,
    policy: Policy,
    transparency: bool,
    fake_ocean: bool,
    cave_y: i32,
) -> Result<bool, i32> {
    let Some(contributors) = contributors else {
        return Ok(false);
    };
    if q.width < 2 || q.height <= 0 || fake_ocean {
        return Ok(false);
    }
    if contributors.iter().any(|c| c.as_ref().is_none_or(|spans| spans.is_empty())) {
        return Ok(false);
    }
    let (min_y, max_y) = (q.y, q.y + q.height);
    let child = q.width / 2;
    let horizontal = Policy { exact: false, overlap_errors: false, curvature: false };
    let add = |owner: &mut Builder, quad: Quad, premerge: bool, policy: Policy| -> Result<(), i32> {
        if quad.width == 0 || quad.height == 0 {
            return Err(-2);
        }
        if quad.width < 0 || quad.height < 0 {
            return Err(-3);
        }
        if owner.add(quad, premerge, policy) { Ok(()) } else { Err(0) }
    };
    for dx in 0..2i16 {
        for dz in 0..2i16 {
            let spans = clipped(contributors[(dx * 2 + dz) as usize].as_ref().unwrap(), i32::from(min_y), i32::from(max_y));
            if spans.is_empty() || spans[0].min > i32::from(min_y) || spans[spans.len() - 1].max < i32::from(max_y) {
                return Ok(false);
            }
            let x = q.x + dx * child;
            let z = q.z + dz * child;
            for span in spans {
                let identity = interner.intern(span.identity);
                let face = Quad {
                    x,
                    z,
                    width: child,
                    height: child,
                    identity,
                    variant: span.variant,
                    position: span.position,
                    ..*q
                };
                if span.max == i32::from(max_y) {
                    let skip = exists(top)
                        && y_min(top) == max_y
                        && !transparent(top, transparency)
                        && (q.material != WATER || water_surface_occluding(material(top)));
                    if !skip {
                        add(owner, Quad { y: max_y, direction: 1, ..face }, false, horizontal)?;
                    }
                }
                if span.min == i32::from(min_y) {
                    let skip = exists(bottom) && y_max(bottom) == min_y && !transparent(bottom, transparency);
                    if !skip {
                        add(owner, Quad { y: min_y, direction: 0, ..face }, false, horizontal)?;
                    }
                }
                let side = Quad { y: span.min as i16, height: (span.max - span.min) as i16, ..face };
                let words = Span { identity, ..span }.words();
                // Java passes the cell's minimum corner for every side.
                for (direction, emit) in [(2u8, dz == 0), (3, dz == 1), (4, dx == 0), (5, dx == 1)] {
                    if !emit {
                        continue;
                    }
                    let index = direction as usize - 2;
                    let face = Quad { direction, width: child, ..side };
                    let status = match neighbors[index] {
                        None => {
                            add(owner, face, true, policy)?;
                            1
                        }
                        // ColumnBox.makeAdjVerticalQuad shades the face before splitting it.
                        Some(points) => faces::adjacent(
                            owner,
                            Quad {
                                color: super::packets::shade(face.color, boxes::shade(params[P_SHADE_FACTS] as u32, direction)),
                                ..face
                            },
                            points,
                            params[P_SAME_DETAIL] & (1 << index) != 0,
                            cave_y,
                            transparency,
                            &words,
                            policy,
                        ),
                    };
                    if status != 1 {
                        return Err(status);
                    }
                }
            }
        }
    }
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn color_adjustment_matches_java_hsv_round_trip_identities() {
        // Unit multipliers keep exact channel values for representative colors.
        for color in [0xff336699u32, 0x80ffffff, 0xff000000, 0x10ff8000, 0xff7f7f7f] {
            let adjusted = adjust(color, 1.0, 1.0);
            let channel = |c: u32, s: u32| ((c >> s) & 255) as i32;
            for shift in [0, 8, 16, 24] {
                assert!((channel(adjusted, shift) - channel(color, shift)).abs() <= 1, "{color:x} -> {adjusted:x}");
            }
        }
        // Zero saturation is gray at the original value.
        assert_eq!(adjust(0xffff0000, 0.0, 1.0), 0xffffffff);
        assert_eq!(adjust(0xff000000, 2.0, 2.0), 0xff000000);
    }
    #[test]
    fn clipping_and_common_recovery_follow_java_rules() {
        let span = |min, max, identity| Span { min, max, identity, variant: VARIANT_EXACT, position: 7 };
        assert_eq!(clipped(&[span(0, 4, 1), span(4, 8, 2)], 2, 6), vec![span(2, 4, 1), span(4, 6, 2)]);
        assert!(clipped(&[span(0, 2, 1), span(3, 8, 2)], 0, 8).is_empty());
        let point = (8u64 << 20) | (2u64 << 8);
        let all = [Some(vec![span(0, 10, 3)]), Some(vec![span(0, 10, 3)]), Some(vec![span(0, 10, 3)]), Some(vec![span(0, 10, 3)])];
        assert_eq!(common_spans(point, Some(&all)), vec![span(2, 8, 3)]);
        assert_eq!(common_provenance(point, Some(&all), (0, 0, 0)), (3, VARIANT_EXACT, 7));
        let mut partial = all.clone();
        partial[2] = None;
        assert!(common_spans(point, Some(&partial)).is_empty());
        assert_eq!(common_provenance(point, Some(&partial), (5, 2, 0)), (5, 2, 0));
    }
}
