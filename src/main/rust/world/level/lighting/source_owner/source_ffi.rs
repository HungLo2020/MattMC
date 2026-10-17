//! Prototype exports in the EXISTING runtime image, no loader/registry/service.
use super::{
    source_update::{Effect, Pos, Reply, Request, Update},
    Error, Source,
};
use std::ptr;
enum Cursor {
    Update(Update),
    Fill(super::source_fill::Fill),
}
fn error(e: Error) -> (i32, i32) {
    match e {
        Error::Index(v) => (1, v),
        Error::Value(v) => (2, v),
        Error::Bits(v) => (3, v),
        Error::WordCount => (4, 0),
        Error::Allocation => (5, 0),
        _ => (6, 0),
    }
}
fn result(r: Result<i32, Error>) -> u64 {
    match r {
        Ok(v) => v as u32 as u64,
        Err(e) => {
            let (k, v) = error(e);
            ((k as u64) << 32) | v as u32 as u64
        }
    }
}
unsafe fn source<'a>(id: u64) -> &'a Source {
    &*(id as usize as *const Source)
}
fn write(frame: &mut [i64; 16], r: Request) {
    match r {
        Request::Done(value) => {
            frame[0] = 0;
            frame[1] = i64::from(value);
        }
        Request::Effect(Effect::Get {
            position,
            set,
            destination,
            copy_lower_to_upper,
        }) => {
            frame[0] = 1;
            frame[1] = position as i64;
            frame[2] = destination as i64;
            frame[3] = i64::from(copy_lower_to_upper);
            frame[4] = i64::from(set.is_some());
            if let Some(p) = set {
                frame[5] = p.x as i64;
                frame[6] = p.y as i64;
                frame[7] = p.z as i64;
            }
        }
        Request::Effect(Effect::Positions {
            first,
            second,
            copy_lower_to_upper,
            set_upper_air,
        }) => {
            frame[0] = 2;
            frame[2] = i64::from(set_upper_air);
            frame[1] = i64::from(copy_lower_to_upper);
            frame[5] = first.x as i64;
            frame[6] = first.y as i64;
            frame[7] = first.z as i64;
            frame[8] = second.x as i64;
            frame[9] = second.y as i64;
            frame[10] = second.z as i64;
        }
        Request::Effect(Effect::Highest) => frame[0] = 3,
        Request::Effect(Effect::SectionY { index, set_air }) => {
            frame[0] = 4;
            frame[1] = index as i64;
            frame[2] = i64::from(set_air);
        }
        Request::Effect(Effect::Section { index }) => {
            frame[0] = 5;
            frame[1] = index as i64;
        }
        Request::Effect(Effect::SectionAir) => frame[0] = 6,
        Request::Effect(Effect::SectionState { x, y, z }) => {
            frame[0] = 7;
            frame[1] = x as i64;
            frame[2] = y as i64;
            frame[3] = z as i64;
        }
        Request::Effect(Effect::Edge(effect)) => frame[0] = 16 + effect as i64,
    }
}
#[no_mangle]
pub extern "C" fn mattmc_sky_source_owner_new(min: i32, bits: i32) -> u64 {
    Source::new(min, bits).map_or(0, |source| Box::into_raw(Box::new(source)) as usize as u64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(id as usize as *mut Source));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_metadata(id: u64) -> u64 {
    let store = source(id).0.lock().unwrap();
    ((store.words.len() as u64) << 32) | store.bits as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_get(id: u64, x: i32, z: i32) -> u64 {
    result(source(id).get(x.wrapping_add(z.wrapping_mul(16))))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_highest(id: u64) -> u64 {
    result(source(id).highest())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_set(id: u64, index: i32, y: i32) -> u64 {
    result(source(id).set(index, y).map(|_| 0))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_fill(id: u64, y: i32) -> u64 {
    let mut store = match source(id).0.lock() {
        Ok(s) => s,
        Err(_) => return result(Err(Error::Poisoned)),
    };
    let value = y.wrapping_sub(store.min);
    let bits = store.bits;
    if value < 0 || value as u64 > ((1u64 << bits) - 1) {
        return result(Err(Error::Value(value)));
    }
    for index in 0..256 {
        super::scan::set_height(&mut store.words, index, bits, value as u64);
    }
    0
}
/// mode0 export,1 fullimport,2 prefixrestore. Pointer borrowed only for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_words(
    id: u64,
    buffer: *mut u64,
    len: i32,
    mode: i32,
) -> i32 {
    let mut store = match source(id).0.lock() {
        Ok(s) => s,
        Err(_) => return -6,
    };
    if len < 0
        || len as usize > store.words.len()
        || mode != 2 && len as usize != store.words.len()
        || buffer.is_null() && len != 0
    {
        return -4;
    }
    if len == 0 {
        return 0;
    }
    match mode {
        0 => ptr::copy_nonoverlapping(store.words.as_ptr(), buffer, len as usize),
        1 | 2 => ptr::copy_nonoverlapping(buffer, store.words.as_mut_ptr(), len as usize),
        _ => return -6,
    }
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_cursor_new(
    id: u64,
    x: i32,
    y: i32,
    z: i32,
    frame: *mut [i64; 16],
) -> u64 {
    match Update::new(source(id).clone(), x, y, z) {
        Ok((update, request)) => {
            write(&mut *frame, request);
            Box::into_raw(Box::new(Cursor::Update(update))) as usize as u64
        }
        Err(e) => {
            let (k, v) = error(e);
            (*frame)[0] = -(k as i64);
            (*frame)[1] = v as i64;
            0
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_cursor_step(id: u64, frame: *mut [i64; 16]) -> i32 {
    let frame = &mut *frame;
    let reply = Reply {
        value: frame[11] as i32,
        first: Pos {
            x: frame[5] as i32,
            y: frame[6] as i32,
            z: frame[7] as i32,
        },
        second: Pos {
            x: frame[8] as i32,
            y: frame[9] as i32,
            z: frame[10] as i32,
        },
    };
    match match &mut *(id as usize as *mut Cursor) {
        Cursor::Update(update) => update.resume(reply),
        Cursor::Fill(fill) => fill.resume(reply),
    } {
        Ok(next) => {
            write(frame, next);
            0
        }
        Err(e) => {
            let (k, v) = error(e);
            frame[0] = -(k as i64);
            frame[1] = v as i64;
            -k
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_cursor_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(id as usize as *mut Cursor));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_cursor_fill_new(id: u64, frame: *mut [i64; 16]) -> u64 {
    match super::source_fill::Fill::new(source(id).clone()) {
        Ok((fill, request)) => {
            write(&mut *frame, request);
            Box::into_raw(Box::new(Cursor::Fill(fill))) as usize as u64
        }
        Err(e) => {
            let (k, v) = error(e);
            (*frame)[0] = -(k as i64);
            (*frame)[1] = v as i64;
            0
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_owner_from_bounds(
    low: i32,
    high: i32,
    error_out: *mut [i64; 2],
) -> u64 {
    match Source::from_bounds(low, high) {
        Ok(source) => Box::into_raw(Box::new(source)) as usize as u64,
        Err(e) => {
            let (k, v) = error(e);
            (*error_out)[0] = k as i64;
            (*error_out)[1] = v as i64;
            0
        }
    }
}
