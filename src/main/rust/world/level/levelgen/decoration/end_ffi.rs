//! Synchronous End-only effect bridge. All state belongs to this invocation;
//! no native owner, lease, lock or RefCell borrow survives a Java callback.
use super::end::{self, Effects, Material, Spike};

type Callback = unsafe extern "C" fn(i32, *mut i32, i32) -> i32;
const END_EFFECT: i32 = 23;
trait Descriptors: Effects<Error = ()> {
    fn next(&mut self, args: &mut [i32; 7]) -> Result<i32, ()>;
}
struct JavaEffects {
    callback: Callback, origin: [i32; 3], mutable_origin: bool,
    writes: [i32; 128], pending: usize,
}
impl JavaEffects {
    fn raw_call(&mut self, op: i32, args: &mut [i32]) -> Result<i32, ()> {
        let status = unsafe { (self.callback)(op, args.as_mut_ptr(), args.len() as i32) };
        if status < 0 { Err(()) } else { Ok(status) }
    }
    fn flush(&mut self) -> Result<(), ()> {
        if self.pending == 0 { return Ok(()); }
        let count = std::mem::take(&mut self.pending);
        let mut words = std::mem::replace(&mut self.writes, [0; 128]);
        self.raw_call(9, &mut words[..count]).map(|_| ())
    }
    fn call(&mut self, op: i32, args: &mut [i32]) -> Result<i32, ()> {
        self.flush()?;
        self.raw_call(op, args)
    }
}
impl Descriptors for JavaEffects {
    fn next(&mut self, args: &mut [i32; 7]) -> Result<i32, ()> { self.call(2, args) }
}
fn spikes<S: Descriptors>(sink: &mut S) -> Result<(), ()> {
    loop {
        let mut args = [0; 7];
        match sink.next(&mut args)? {
            0 => return Ok(()),
            2 => continue,
            _ => if args[0] >> 4 == args[5] >> 4 && args[1] >> 4 == args[6] >> 4 {
                end::place_spike(sink, Spike { x: args[0], z: args[1], radius: args[2], height: args[3], guarded: args[4] != 0 })?;
            },
        }
    }
}
fn material(material: Material) -> i32 {
    match material { Material::Obsidian => 1, Material::Air => 2, Material::Bedrock => 3,
        Material::Gateway => 4, Material::Bars(bits) => 16 + i32::from(bits) }
}
impl Effects for JavaEffects {
    type Error = ();
    fn min_y(&mut self) -> Result<i32, ()> { let mut args = [0]; self.call(1, &mut args)?; Ok(args[0]) }
    fn origin(&mut self) -> Result<[i32; 3], ()> {
        if !self.mutable_origin { return Ok(self.origin); }
        let mut args = [0; 3]; self.call(8, &mut args)?; Ok(args)
    }
    fn set(&mut self, pos: [i32; 3], state: Material) -> Result<(), ()> {
        if self.mutable_origin {
            return self.raw_call(3, &mut [pos[0], pos[1], pos[2], material(state)]).map(|_| ());
        }
        self.writes[self.pending..self.pending + 4].copy_from_slice(&[pos[0], pos[1], pos[2], material(state)]);
        self.pending += 4;
        if self.pending == self.writes.len() { self.flush()?; }
        Ok(())
    }
    fn matches(&mut self, pos: [i32; 3], state: Material) -> Result<bool, ()> {
        self.call(4, &mut [pos[0], pos[1], pos[2], material(state)]).map(|v| v != 0)
    }
    fn destroy(&mut self, pos: [i32; 3]) -> Result<(), ()> { self.call(5, &mut pos.clone()).map(|_| ()) }
    fn crystal(&mut self, pos: [i32; 3]) -> Result<(), ()> { self.call(6, &mut pos.clone()).map(|_| ()) }
    fn exit(&mut self, pos: [i32; 3]) -> Result<(), ()> { self.call(7, &mut pos.clone()).map(|_| ()) }
    fn platform_cell(&mut self, pos: [i32; 3], state: Material, destroy: bool) -> Result<(), ()> {
        self.call(10, &mut [pos[0], pos[1], pos[2], material(state), i32::from(destroy)]).map(|_| ())
    }
}

/// Returns 1 on completion, -1 for invalid arguments before effects, -2 after
/// a Java exception (retained/rethrown by Java), -3 for a caught native panic.
/// kind: 0 spikes, 1 gateway, 2 platform; flags: mutable origin=1, destroy=2.
/// # Safety
/// `callback` must stay callable synchronously until this invocation returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_end_features_place(kind: i32, x: i32, y: i32, z: i32, flags: i32, callback: Option<Callback>) -> i32 {
    let Some(callback) = callback else { return -1 };
    if !(0..=2).contains(&kind) || flags & !3 != 0 { return -1; }
    // These two boxes must have representable initial bounds. The Java adapter
    // declines extreme origins before any effect and uses the original method.
    if kind == 1 && (x.checked_sub(1).is_none() || x.checked_add(1).is_none()
        || z.checked_sub(1).is_none() || z.checked_add(1).is_none()
        || y.checked_sub(2).is_none() || y.checked_add(2).is_none()) { return -1; }
    let result = std::panic::catch_unwind(|| {
        let mut sink = JavaEffects { callback, origin: [x, y, z], mutable_origin: flags & 1 != 0, writes: [0; 128], pending: 0 };
        let placed = match kind {
            0 => spikes(&mut sink),
            1 => end::place_gateway(&mut sink),
            2 => end::place_platform(&mut sink, flags & 2 != 0),
            _ => unreachable!(),
        };
        placed?;
        sink.flush()
    });
    match result { Ok(Ok(())) => 1, Ok(Err(())) => -2, Err(_) => -3 }
}

/// No allocation, callbacks, owners or locks: safe for a critical Java call
/// between its real per-cell read and actual destroy/write effects.
#[no_mangle]
pub extern "C" fn mattmc_end_platform_action(matches: i32, destroy: i32) -> i32 {
    end::platform_action(matches != 0, destroy != 0)
}

/// Uses an already admitted generation binding while a configured End spike
/// leaf is running. Descriptor iteration and crystal effects synchronize through
/// the existing decoration callback; geometry and native writes are shared.
struct GenerationEffects<'a> {
    region: super::region::Region<'a>,
    origin: [i32; 3],
    states: [u32; 32],
}
impl GenerationEffects<'_> {
    fn call(&mut self, op: i32, args: &mut [i32]) -> Result<i32, ()> {
        let mut words = [0; 8];
        words[0] = i64::from(op);
        for (dst, &src) in words[1..].iter_mut().zip(args.iter()) { *dst = i64::from(src); }
        let status = self.region.sync_call(END_EFFECT, &mut words[..args.len() + 1]).map_err(|_| ())?;
        for (dst, &src) in args.iter_mut().zip(&words[1..]) { *dst = src as i32; }
        Ok(status)
    }
}
impl Descriptors for GenerationEffects<'_> {
    fn next(&mut self, args: &mut [i32; 7]) -> Result<i32, ()> { self.call(2, args) }
}
impl Effects for GenerationEffects<'_> {
    type Error = ();
    fn min_y(&mut self) -> Result<i32, ()> { Ok(self.region.min_y) }
    fn origin(&mut self) -> Result<[i32; 3], ()> { Ok(self.origin) }
    fn set(&mut self, pos: [i32; 3], state: Material) -> Result<(), ()> {
        self.region.set_block(pos[0], pos[1], pos[2], self.states[material(state) as usize], 3).map(|_| ()).map_err(|_| ())
    }
    fn matches(&mut self, _: [i32; 3], _: Material) -> Result<bool, ()> { Err(()) }
    fn destroy(&mut self, _: [i32; 3]) -> Result<(), ()> { Err(()) }
    fn crystal(&mut self, mut pos: [i32; 3]) -> Result<(), ()> { self.call(6, &mut pos).map(|_| ()) }
    fn exit(&mut self, mut pos: [i32; 3]) -> Result<(), ()> { self.call(7, &mut pos).map(|_| ()) }
}

/// Returns 0 only when admission declined before callbacks/effects. Other
/// statuses match `mattmc_end_features_place`.
/// # Safety
/// All binding/state pointers and the synchronous callback remain valid until
/// return, exactly as for the existing decoration placement exports.
#[no_mangle]
pub unsafe extern "C" fn mattmc_end_spikes_generation(x: i32, y: i32, z: i32,
    words: *mut i64, word_count: i32, out: *mut i64, out_count: i32, states: *const u32,
    callback: Option<super::region::Callback>) -> i32 {
    mattmc_end_features_generation(0, x, y, z, words, word_count, out, out_count, states, callback)
}

/// # Safety
/// Same generation binding contract as `mattmc_end_spikes_generation`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_end_features_generation(kind: i32, x: i32, y: i32, z: i32,
    words: *mut i64, word_count: i32, out: *mut i64, out_count: i32, states: *const u32,
    callback: Option<super::region::Callback>) -> i32 {
    let Some(callback) = callback else { return 0 };
    if !(0..=1).contains(&kind) { return 0; }
    if kind == 1 && (x.checked_sub(1).is_none() || x.checked_add(1).is_none()
        || z.checked_sub(1).is_none() || z.checked_add(1).is_none()
        || y.checked_sub(2).is_none() || y.checked_add(2).is_none()) { return 0; }
    if words.is_null() || out.is_null() || states.is_null() || word_count < 0 || out_count < 0 { return 0; }
    let states: [u32; 32] = std::slice::from_raw_parts(states, 32).try_into().unwrap();
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = super::region::Region::bind(
            super::region::Shared::new(words, word_count as usize), super::region::Shared::new(out, out_count as usize)) else { return Ok(0) };
        if !region.write_path_ready() || !super::facts::installed().is_some_and(|f| !f.any_unknown()) { return Ok(0); }
        let Some(policies) = crate::world::level::chunk::counters::installed_policies() else { return Ok(0) };
        let indices: &[usize] = if kind == 0 { &[1, 2, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31] } else { &[2, 3, 4] };
        if indices.iter().any(|&index| !policies.get(states[index] as usize).is_some_and(Option::is_some)) {
            return Ok(0);
        }
        region.callback = Some(callback);
        let mut sink = GenerationEffects { region, origin: [x, y, z], states };
        let placed = if kind == 0 { spikes(&mut sink) } else { end::place_gateway(&mut sink) };
        // Publish any prefix even when a native write failed, as the existing
        // placement exports do. The surrounding FEATURES transaction decides
        // whether that failed step is rolled back.
        let flushed = sink.call(0, &mut []);
        placed?;
        flushed?;
        Ok::<i32, ()>(1)
    }));
    match result { Ok(Ok(value)) => value, Ok(Err(())) => -2, Err(_) => -3 }
}
