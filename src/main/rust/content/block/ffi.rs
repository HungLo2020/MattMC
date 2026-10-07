//! Java bridge for the block registry; see `NativeBlockRegistry`.
use super::{export, installed, BlockRegistry, DIRECTIONS};

/// Installs the process-wide registry from Java's export (layout in
/// `export.rs`). Returns 1 when installed (or already installed with equal
/// content), 0 for an invalid export, -1 when a different registry is
/// installed.
/// # Safety
/// Each pointer addresses its stated count of values for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_block_registry_install(ints: *const i32, int_len: i32, chars: *const u16, char_len: i32,
    bytes: *const u8, byte_len: i32) -> i32 {
    if ints.is_null() || bytes.is_null() || int_len <= 0 || char_len < 0 || byte_len < 0 || (chars.is_null() && char_len > 0) {
        return 0;
    }
    let chars = if char_len == 0 { &[][..] } else { std::slice::from_raw_parts(chars, char_len as usize) };
    let registry = match export::decode(std::slice::from_raw_parts(ints, int_len as usize), chars,
        std::slice::from_raw_parts(bytes, byte_len as usize)) {
        Ok(registry) => registry,
        Err(_) => return 0,
    };
    match super::install(registry) {
        Ok(_) => 1,
        Err(super::Error::Conflict) => -1,
        Err(_) => 0,
    }
}

/// Columns of the installed registry, for verification: 0 block ID, 1 flags,
/// 2 light block, 3 emission, 4 six face IDs, 5 each property's value index
/// (block property order), 6 `with_value` for each property and value index,
/// 7 default state per block, 8 the face truth table. Writes when `out_len` is large enough and
/// returns the value count; -1 when nothing is installed or `kind` is unknown.
/// # Safety
/// `out` addresses `out_len` values.
#[no_mangle]
pub unsafe extern "C" fn mattmc_block_registry_column(kind: i32, out: *mut i32, out_len: i32) -> i32 {
    let Some(registry) = installed() else { return -1 };
    let Some(values) = column(registry, kind) else { return -1 };
    if !out.is_null() && out_len as usize >= values.len() {
        std::slice::from_raw_parts_mut(out, values.len()).copy_from_slice(&values);
    }
    values.len() as i32
}

fn column(registry: &BlockRegistry, kind: i32) -> Option<Vec<i32>> {
    let states = || (0..registry.state_count()).map(|s| super::StateId(s as u16));
    Some(match kind {
        0 => states().map(|s| registry.block_of(s).0 as i32).collect(),
        1 => states().map(|s| registry.flags(s).0 as i32).collect(),
        2 => states().map(|s| registry.light_block(s) as i32).collect(),
        3 => states().map(|s| registry.emission(s) as i32).collect(),
        4 => states().flat_map(|s| (0..DIRECTIONS).map(move |d| registry.light_face(s, d).0 as i32)).collect(),
        5 => states()
            .flat_map(|s| registry.block(registry.block_of(s)).properties().map(move |p| registry.value(s, p).map_or(-1, i32::from)))
            .collect(),
        6 => states()
            .flat_map(|s| {
                registry.block(registry.block_of(s)).properties().flat_map(move |p| {
                    let count = registry.property(p).values().len() as u16;
                    (0..count).map(move |v| registry.with_value(s, p, v).map_or(-1, |n| n.0 as i32))
                })
            })
            .collect(),
        7 => registry.blocks().iter().map(|b| b.default_state().0 as i32).collect(),
        8 => registry.face_matrix().iter().map(|&o| o as i32).collect(),
        _ => return None,
    })
}
