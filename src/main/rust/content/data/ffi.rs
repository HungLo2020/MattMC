//! Java's view of native data definitions: decode into the store, read a
//! definition's projection table, encode it, release it with its Java object.
//!
//! Input trees from Java are preorder, little endian: a tag byte, then
//! 0 null, 1 false, 2 true, 3 number text, 4 byte, 5 short, 6 int, 7 long,
//! 8 float, 9 double, 10 string, 11 list (u32 count, items), 12 map (u32
//! count, key string and value each). Strings are u32 length + UTF-8. Nodes
//! are numbered in that preorder.
//!
//! Decoded values (`D`): 0 absent, 1 false, 2 true, 3 int, 4 float,
//! 5 double, 6 string, 7 list, 8 map, 9 record, 10 variant (u16 case, u32
//! count, fields), 11 slot (u32), 15 a number as its source held it (a
//! tree number node).
//!
//! Encoded values (`O`): 1 false, 2 true, 3 int, 4 float, 5 double,
//! 6 string, 7 list, 8 map, 11 slot (u32), 14 slot omitted when Java's
//! value equals its default (u32), 16 int
//! list (u32 count, ints), 17 a number of its Java type (tree number
//! node), 18 a delegated map codec's entries merged into the map (u32).

use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use super::schema::{D, O, R};
use super::value::{Mode, Node, Num, Value};
use super::Definition;

/// A stored decode: its definition, or the error and any partial definition.
type Entry = Result<Arc<Definition>, (String, Option<Arc<Definition>>)>;

fn store() -> &'static Mutex<HashMap<u64, Entry>> {
    static STORE: OnceLock<Mutex<HashMap<u64, Entry>>> = OnceLock::new();
    STORE.get_or_init(|| Mutex::new(HashMap::new()))
}
static NEXT: AtomicU64 = AtomicU64::new(1);

fn keep(entry: Entry) -> i64 {
    let handle = NEXT.fetch_add(1, Ordering::Relaxed);
    store().lock().unwrap().insert(handle, entry);
    handle as i64
}

/// The stored definition behind a live Java object, for native readers.
pub fn get(handle: u64) -> Option<Arc<Definition>> {
    store().lock().unwrap().get(&handle).and_then(|e| match e {
        Ok(def) | Err((_, Some(def))) => Some(def.clone()),
        Err((_, None)) => None,
    })
}

struct In<'a> {
    bytes: &'a [u8],
    pos: usize,
    next_index: u32,
}

impl In<'_> {
    fn take(&mut self, n: usize) -> R<&[u8]> {
        let end = self.pos.checked_add(n).filter(|&e| e <= self.bytes.len()).ok_or("truncated value tree")?;
        let slice = &self.bytes[self.pos..end];
        self.pos = end;
        Ok(slice)
    }
    fn u8(&mut self) -> R<u8> {
        Ok(self.take(1)?[0])
    }
    fn u32(&mut self) -> R<u32> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn u64(&mut self) -> R<u64> {
        Ok(u64::from_le_bytes(self.take(8)?.try_into().unwrap()))
    }
    fn string(&mut self) -> R<String> {
        let len = self.u32()? as usize;
        Ok(String::from_utf8_lossy(self.take(len)?).into_owned())
    }
    /// A decoded value from Java (`D` tags).
    fn decoded(&mut self) -> R<D> {
        Ok(match self.u8()? {
            0 => D::Absent,
            1 => D::Bool(false),
            2 => D::Bool(true),
            3 => D::Int(self.u32()? as i32),
            4 => D::Float(f32::from_bits(self.u32()?)),
            5 => D::Double(f64::from_bits(self.u64()?)),
            6 => D::Str(self.string()?),
            7 => {
                let n = self.u32()?;
                D::List((0..n).map(|_| self.decoded()).collect::<R<_>>()?)
            }
            8 => {
                let n = self.u32()?;
                let mut entries = Vec::new();
                for _ in 0..n {
                    let key = self.string()?;
                    entries.push((key, self.decoded()?));
                }
                D::Map(entries)
            }
            9 => {
                let n = self.u32()?;
                D::Record((0..n).map(|_| self.decoded()).collect::<R<_>>()?)
            }
            10 => {
                let case = u16::from_le_bytes(self.take(2)?.try_into().unwrap());
                let n = self.u32()?;
                D::Variant(case, (0..n).map(|_| self.decoded()).collect::<R<_>>()?)
            }
            11 => D::Slot(self.u32()?),
            15 => match self.node()?.value {
                Value::Number(n) => D::Num(n),
                _ => return Err("decoded number is not a number".into()),
            },
            tag => return Err(format!("unknown decoded tag {tag}")),
        })
    }

    fn node(&mut self) -> R<Node> {
        let index = self.next_index;
        self.next_index += 1;
        let value = match self.u8()? {
            0 => Value::Null,
            1 => Value::Bool(false),
            2 => Value::Bool(true),
            3 => Value::Number(Num::Text(self.string()?)),
            4 => Value::Number(Num::Byte(self.u8()? as i8)),
            5 => Value::Number(Num::Short(i16::from_le_bytes(self.take(2)?.try_into().unwrap()))),
            6 => Value::Number(Num::Int(self.u32()? as i32)),
            7 => Value::Number(Num::Long(self.u64()? as i64)),
            8 => Value::Number(Num::Float(f32::from_bits(self.u32()?))),
            9 => Value::Number(Num::Double(f64::from_bits(self.u64()?))),
            10 => Value::String(self.string()?),
            11 => {
                let n = self.u32()?;
                Value::List((0..n).map(|_| self.node()).collect::<R<Vec<_>>>()?.into())
            }
            12 => {
                let n = self.u32()?;
                let mut members = Vec::new();
                for _ in 0..n {
                    let key = self.string()?;
                    members.push((key, self.node()?));
                }
                Value::Map(members.into())
            }
            tag => return Err(format!("unknown value tree tag {tag}")),
        };
        Ok(Node { index: Some(index), value })
    }
}

#[derive(Default)]
struct Out(Vec<u8>);

/// Appends `node` in the input tree format to `bytes`, for a Java codec Rust calls back.
pub(crate) fn write_tree(node: &Node, bytes: Vec<u8>) -> Vec<u8> {
    let mut o = Out(bytes);
    o.node(node);
    o.0
}

thread_local! {
    /// A reusable output buffer; taken while in use, so a nested call (Java
    /// calling back into Rust) gets a fresh one.
    static SPARE: std::cell::Cell<Vec<u8>> = const { std::cell::Cell::new(Vec::new()) };
    /// A value call's result that did not fit Java's buffer.
    static PENDING: std::cell::Cell<Vec<u8>> = const { std::cell::Cell::new(Vec::new()) };
}

/// Copies a value call's result into `out` when it fits, else keeps it for
/// `mattmc_value_take_pending`; returns its full length.
unsafe fn finish(o: Out, out: *mut u8, capacity: i64) -> i64 {
    let n = o.0.len() as i64;
    if !out.is_null() && capacity >= n {
        std::ptr::copy_nonoverlapping(o.0.as_ptr(), out, o.0.len());
        o.recycle();
    } else {
        PENDING.with(|p| p.set(o.0));
    }
    n
}

impl Out {
    fn with_capacity(n: usize) -> Out {
        let mut v = SPARE.with(|s| s.take());
        v.clear();
        v.reserve(n);
        Out(v)
    }

    /// Returns the buffer for the next call on this thread.
    fn recycle(self) {
        SPARE.with(|s| s.set(self.0));
    }
    fn u8(&mut self, v: u8) {
        self.0.push(v);
    }
    fn u16(&mut self, v: u16) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn u32(&mut self, v: u32) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn u64(&mut self, v: u64) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn string(&mut self, v: &str) {
        self.u32(v.len() as u32);
        self.0.extend_from_slice(v.as_bytes());
    }
    /// An input node, in the input tree format.
    fn node(&mut self, node: &Node) {
        match &node.value {
            Value::Null => self.u8(0),
            Value::Bool(v) => self.u8(if *v { 2 } else { 1 }),
            Value::Number(n) => match n {
                Num::Text(t) => {
                    self.u8(3);
                    self.string(t);
                }
                Num::Byte(v) => {
                    self.u8(4);
                    self.u8(*v as u8);
                }
                Num::Short(v) => {
                    self.u8(5);
                    self.0.extend_from_slice(&v.to_le_bytes());
                }
                Num::Int(v) => {
                    self.u8(6);
                    self.u32(*v as u32);
                }
                Num::Long(v) => {
                    self.u8(7);
                    self.u64(*v as u64);
                }
                Num::Float(v) => {
                    self.u8(8);
                    self.u32(v.to_bits());
                }
                Num::Double(v) => {
                    self.u8(9);
                    self.u64(v.to_bits());
                }
            },
            Value::String(s) => {
                self.u8(10);
                self.string(s);
            }
            Value::List(items) => {
                self.u8(11);
                self.u32(items.len() as u32);
                items.iter().for_each(|i| self.node(i));
            }
            Value::Map(members) => {
                self.u8(12);
                self.u32(members.len() as u32);
                for (k, v) in members.iter() {
                    self.string(k);
                    self.node(v);
                }
            }
        }
    }
    fn decoded(&mut self, d: &D) {
        match d {
            D::Absent => self.u8(0),
            D::Bool(v) => self.u8(if *v { 2 } else { 1 }),
            D::Int(v) => {
                self.u8(3);
                self.u32(*v as u32);
            }
            D::Float(v) => {
                self.u8(4);
                self.u32(v.to_bits());
            }
            D::Double(v) => {
                self.u8(5);
                self.u64(v.to_bits());
            }
            D::Str(s) => {
                self.u8(6);
                self.string(s);
            }
            D::List(items) | D::Record(items) => {
                self.u8(if matches!(d, D::List(_)) { 7 } else { 9 });
                self.u32(items.len() as u32);
                items.iter().for_each(|i| self.decoded(i));
            }
            D::Map(entries) => {
                self.u8(8);
                self.u32(entries.len() as u32);
                for (k, v) in entries {
                    self.string(k);
                    self.decoded(v);
                }
            }
            D::Variant(case, fields) => {
                self.u8(10);
                self.u16(*case);
                self.u32(fields.len() as u32);
                fields.iter().for_each(|i| self.decoded(i));
            }
            D::Slot(i) => {
                self.u8(11);
                self.u32(*i);
            }
            D::Num(n) => {
                self.u8(15);
                self.node(&Node::new(Value::Number(n.clone())));
            }
        }
    }
    fn encoded(&mut self, o: &O) {
        match o {
            O::Bool(v) => self.u8(if *v { 2 } else { 1 }),
            O::IntList(items) => {
                self.u8(16);
                self.u32(items.len() as u32);
                items.iter().for_each(|i| self.u32(*i as u32));
            }
            O::Int(v) => {
                self.u8(3);
                self.u32(*v as u32);
            }
            O::Float(v) => {
                self.u8(4);
                self.u32(v.to_bits());
            }
            O::Double(v) => {
                self.u8(5);
                self.u64(v.to_bits());
            }
            O::Str(s) => {
                self.u8(6);
                self.string(s);
            }
            O::List(items) => {
                self.u8(7);
                self.u32(items.len() as u32);
                items.iter().for_each(|i| self.encoded(i));
            }
            O::Map(entries) => {
                self.u8(8);
                self.u32(entries.len() as u32);
                for (k, v) in entries {
                    self.string(k);
                    self.encoded(v);
                }
            }
            O::Slot(i) => {
                self.u8(11);
                self.u32(*i);
            }
            O::SlotUnlessDefault(i) => {
                self.u8(14);
                self.u32(*i);
            }
            O::Num(n) => {
                self.u8(17);
                self.node(&Node::new(Value::Number(n.clone())));
            }
            O::SlotMerge(i) => {
                self.u8(18);
                self.u32(*i);
            }
        }
    }
}

/// Status 1, the decoded value, its slots (present flag, Java node index or
/// u32::MAX, input tree) and logs; or status 0, the error and u8 partial,
/// then a partial definition as for status 1.
fn payload(entry: &Entry) -> Vec<u8> {
    let mut o = Out::default();
    let def = match entry {
        Err((error, partial)) => {
            o.u8(0);
            o.string(error);
            o.u8(u8::from(partial.is_some()));
            partial.as_ref()
        }
        Ok(def) => {
            o.u8(1);
            Some(def)
        }
    };
    if let Some(def) = def {
        o.decoded(&def.value);
        o.slots(&def.slots, &def.logs);
    }
    o.0
}

impl Out {
    fn slots(&mut self, slots: &[Option<Node>], logs: &[String]) {
        self.u32(slots.len() as u32);
        for slot in slots {
            match slot {
                None => self.u8(0),
                Some(node) => {
                    self.u8(1);
                    self.u32(node.index.unwrap_or(u32::MAX));
                    self.node(node);
                }
            }
        }
        self.u32(logs.len() as u32);
        logs.iter().for_each(|l| self.string(l));
    }
}

unsafe fn slice<'a>(ptr: *const u8, len: i64) -> &'a [u8] {
    if ptr.is_null() || len <= 0 { &[] } else { std::slice::from_raw_parts(ptr, len as usize) }
}

unsafe fn copy_out(table: &[u8], out: *mut u8, capacity: i64) -> i64 {
    if !out.is_null() && capacity >= table.len() as i64 {
        std::ptr::copy_nonoverlapping(table.as_ptr(), out, table.len());
    }
    table.len() as i64
}

/// A data call's handle when it met a block state the block registry could
/// not be installed for (`schema::BLOCKS_MISSING`); nothing is stored.
const BLOCKS_MISSING_HANDLE: i64 = -2;
/// A file call's handle when its bytes are not JSON Rust parses; nothing is stored.
const JSON_INVALID_HANDLE: i64 = -1;
/// A data call's handle when a Java codec it called back threw: `-3 - id`,
/// Java's id for that `Throwable`, which Java rethrows.
const JAVA_THROWN_HANDLE: i64 = -3;

fn guarded(f: impl FnOnce() -> Result<Definition, super::Failed>) -> i64 {
    let (entry, thrown) = super::schema::thrown_scope(|| catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|_| Err("data decode panicked".to_owned().into())));
    let entry = match thrown {
        Some((super::schema::BLOCKS_MISSING, _, _)) => return BLOCKS_MISSING_HANDLE,
        Some((super::schema::JAVA_THROWN, _, id)) => return JAVA_THROWN_HANDLE - i64::from(id),
        Some((_, message, _)) => Err((message, None)),
        None => entry.map_err(|f| (f.msg, f.partial.map(Arc::new))),
    };
    keep(entry.map(Arc::new))
}

/// Decodes a data pack file of `registry` into the store; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_decode_file(registry: i32, network: i32, bytes: *const u8, len: i64) -> i64 {
    decode_file_with_component_ops(registry, network, slice(bytes, len), false)
}

/// File decoding with the original complete component codecs for custom ops.
/// The original file export and its ABI retain their existing behavior.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_decode_file_generic_components(registry: i32, network: i32, bytes: *const u8, len: i64) -> i64 {
    decode_file_with_component_ops(registry, network, slice(bytes, len), true)
}

fn decode_file_with_component_ops(registry: i32, network: i32, input: &[u8], generic_component_ops: bool) -> i64 {
    // Java parses text that isn't JSON itself, for the loader's own exception.
    let Ok(node) = crate::content::json::parse_into::<Node>(input) else { return JSON_INVALID_HANDLE };
    guarded(|| {
        let mut cx = super::schema::Cx::new(Mode::Json, true);
        cx.generic_component_ops = generic_component_ops;
        super::decode_partial_with_context(registry as usize, network != 0, &node, cx)
    })
}

/// Decodes a value tree from Java (`mode` 0 JsonOps, 1 NbtOps) into the store; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_decode_tree(registry: i32, network: i32, mode: i32, registry_ops: i32, bytes: *const u8, len: i64) -> i64 {
    let input = slice(bytes, len);
    guarded(|| {
        let node = In { bytes: input, pos: 0, next_index: 0 }.node()?;
        let mut cx = super::schema::Cx::new(if mode & 1 == 1 { Mode::Nbt } else { Mode::Json }, registry_ops != 0);
        cx.generic_component_ops = mode & 4 != 0;
        super::decode_partial_with_context(registry as usize, network != 0, &node, cx)
    })
}

/// Stores a definition Java built (`Unprojector`), as `slots` delegated
/// slots, after checking it encodes and decodes under its schema; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_define(registry: i32, slots: i32, bytes: *const u8, len: i64) -> i64 {
    let input = slice(bytes, len);
    guarded(|| {
        let value = In { bytes: input, pos: 0, next_index: 0 }.decoded()?;
        Ok(super::define(registry as usize, value, slots.max(0) as usize)?)
    })
}

/// Copies a handle's projection table into `out` when it fits; returns its
/// full length, or -1 for an unknown handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_payload(handle: i64, out: *mut u8, capacity: i64) -> i64 {
    let table = match store().lock().unwrap().get(&(handle as u64)) {
        Some(entry) => payload(entry),
        None => return -1,
    };
    copy_out(&table, out, capacity)
}

/// Copies a stored definition's encoding (status 1 and the value, or 0 and
/// the error) into `out` when it fits; returns its full length, or -1.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_encode(handle: i64, network: i32, out: *mut u8, capacity: i64) -> i64 {
    let Some(def) = get(handle as u64) else { return -1 };
    let mut o = Out::default();
    let (result, thrown) = super::schema::thrown_scope(|| catch_unwind(AssertUnwindSafe(|| super::encode(&def, network != 0))).unwrap_or_else(|_| Err("data encode panicked".into())));
    match (thrown, result) {
        // Status 2: the block registry could not be installed.
        (Some((super::schema::BLOCKS_MISSING, _, _)), _) => o.u8(2),
        (_, Ok(value)) => {
            o.u8(1);
            o.encoded(&value);
        }
        (_, Err(error)) => {
            o.u8(0);
            o.string(&error);
        }
    }
    copy_out(&o.0, out, capacity)
}

/// Decodes a value tree as a value kind (`content/data/values.rs`), checking
/// `[min, max]` when `bounded`. Copies into `out` when it fits: status 1 and
/// the value, or 0, the error, u8 partial present and the partial; then the
/// slots and logs as in a definition's payload. Status 2 and a message is an
/// exception Java's codec throws. Returns the full length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_value_decode(kind: i32, mode: i32, bounded: i32, min: f64, max: f64, bytes: *const u8, len: i64, out: *mut u8, capacity: i64) -> i64 {
    let input = slice(bytes, len);
    let bounds = (bounded != 0).then_some(super::values::Bounds { min, max });
    // Bit 0: NbtOps; bit 1: RegistryOps (registry file codecs take references).
    let registry_ops = mode & 2 != 0;
    let generic_component_ops = mode & 4 != 0;
    let mode = if mode & 1 == 1 { Mode::Nbt } else { Mode::Json };
    value_result(input, out, capacity, |node| {
        let mut cx = super::schema::Cx::new(mode, registry_ops);
        cx.generic_component_ops = generic_component_ops;
        super::values::decode_with_context(kind as usize, node, bounds, cx)
    })
}

/// Decodes one dispatch case of a value kind from a map without `type`
/// (named by `case`, UTF-8); the result as `mattmc_value_decode`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_value_decode_case(kind: i32, mode: i32, case: *const u8, case_len: i64, bytes: *const u8, len: i64, out: *mut u8, capacity: i64) -> i64 {
    let input = slice(bytes, len);
    let case = String::from_utf8_lossy(slice(case, case_len)).into_owned();
    let registry_ops = mode & 2 != 0;
    let generic_component_ops = mode & 4 != 0;
    let mode = if mode & 1 == 1 { Mode::Nbt } else { Mode::Json };
    value_result(input, out, capacity, |node| {
        let mut cx = super::schema::Cx::new(mode, registry_ops);
        cx.generic_component_ops = generic_component_ops;
        super::values::decode_case_with_context(kind as usize, &case, node, cx)
    })
}

/// Data component types whose value codecs are native (u32 count, paths).
#[no_mangle]
pub unsafe extern "C" fn mattmc_item_native_components(out: *mut u8, capacity: i64) -> i64 {
    let mut o = Out::default();
    let names = super::items::native_components();
    o.u32(names.len() as u32);
    names.iter().for_each(|n| o.string(n));
    copy_out(&o.0, out, capacity)
}

/// A value kind's dispatch case names (u32 count, strings), or count 0.
#[no_mangle]
pub unsafe extern "C" fn mattmc_value_case_names(kind: i32, out: *mut u8, capacity: i64) -> i64 {
    let mut o = Out::default();
    let names = super::values::case_names(kind as usize).unwrap_or_default();
    o.u32(names.len() as u32);
    names.iter().for_each(|n| o.string(n));
    finish(o, out, capacity)
}

/// Copies the result a value call kept because it did not fit, clearing it
/// once copied; returns its full length, or -1 when none is kept. Java takes
/// it right after growing its buffer, so the call (and any Java callbacks
/// it made) never runs twice.
#[no_mangle]
pub unsafe extern "C" fn mattmc_value_take_pending(out: *mut u8, capacity: i64) -> i64 {
    let kept = PENDING.with(|p| p.take());
    if kept.is_empty() {
        return -1;
    }
    let n = kept.len() as i64;
    if !out.is_null() && capacity >= n {
        std::ptr::copy_nonoverlapping(kept.as_ptr(), out, kept.len());
        Out(kept).recycle();
    } else {
        PENDING.with(|p| p.set(kept));
    }
    n
}

unsafe fn value_result(input: &[u8], out: *mut u8, capacity: i64, decode: impl FnOnce(&Node) -> (super::schema::DR, super::schema::Cx)) -> i64 {
    let (decoded, thrown) = super::schema::thrown_scope(|| {
        catch_unwind(AssertUnwindSafe(|| match (In { bytes: input, pos: 0, next_index: 0 }).node() {
            Ok(node) => decode(&node),
            Err(error) => (Err(error.into()), super::schema::Cx::new(Mode::Json, false)),
        }))
    });
    let mut o = Out::with_capacity(256);
    match (thrown, decoded) {
        (Some((class, message, id)), _) => {
            // Status 2: Java throws the exception class (0 illegal argument,
            // 1 null pointer) with the message, or rethrows its own
            // exception `id` (`JAVA_THROWN`).
            o.u8(2);
            o.u8(class);
            o.u32(id);
            o.string(&message);
        }
        (None, Err(_)) => {
            o.u8(0);
            o.string("value decode panicked");
            o.u8(0);
            o.slots(&[], &[]);
        }
        (None, Ok((Ok(d), cx))) => {
            o.u8(1);
            o.decoded(&d);
            o.slots(&cx.slots, &cx.logs);
        }
        (None, Ok((Err(fail), cx))) => {
            o.u8(0);
            o.string(&fail.msg);
            match &fail.partial {
                Some(partial) => {
                    o.u8(1);
                    o.decoded(partial);
                }
                None => o.u8(0),
            }
            o.slots(&cx.slots, &cx.logs);
        }
    }
    finish(o, out, capacity)
}

/// Encodes a value Java built (decoded form), with its codec's checks;
/// copies status 1 and the encoded value, or 0 and the error, into `out`
/// when it fits; returns the full length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_value_encode(kind: i32, bounded: i32, min: f64, max: f64, bytes: *const u8, len: i64, out: *mut u8, capacity: i64) -> i64 {
    let input = slice(bytes, len);
    let bounds = (bounded != 0).then_some(super::values::Bounds { min, max });
    let mut o = Out::with_capacity(256);
    let (result, thrown) = super::schema::thrown_scope(|| {
        catch_unwind(AssertUnwindSafe(|| {
            let d = In { bytes: input, pos: 0, next_index: 0 }.decoded()?;
            let value = super::values::encode(kind as usize, &d, bounds)?;
            // Serialized while `value` borrows `d`.
            o.u8(1);
            o.encoded(&value);
            Ok::<_, String>(())
        }))
        .unwrap_or_else(|_| Err("value encode panicked".into()))
    });
    if let Some((class, message, id)) = thrown {
        // Status 2 as in `value_result`: Java throws for `class`.
        o.0.clear();
        o.u8(2);
        o.u8(class);
        o.u32(id);
        o.string(&message);
    } else if let Err(error) = result {
        o.0.clear();
        o.u8(0);
        o.string(&error);
    }
    finish(o, out, capacity)
}

/// Installs Java's callback for codecs Java still owns (`schema::JavaDecode`).
#[no_mangle]
pub extern "C" fn mattmc_value_set_java_decode(decode: super::schema::JavaDecode) {
    super::schema::set_java_decode(decode);
}

/// Installs Java's callback that installs the block registry on first need.
#[no_mangle]
pub extern "C" fn mattmc_value_set_install_blocks(install: super::schema::InstallBlocks) {
    super::schema::set_install_blocks(install);
}

/// Installs Java's callback for string parsers Java owns (`schema::Leaf`).
#[no_mangle]
pub extern "C" fn mattmc_value_set_leaf_check(check: super::schema::LeafCheck) {
    super::schema::set_leaf_check(check);
}

#[no_mangle]
pub extern "C" fn mattmc_data_release(handle: i64) {
    store().lock().unwrap().remove(&(handle as u64));
}

/// Live stored definitions, for leak checks.
#[no_mangle]
pub extern "C" fn mattmc_data_live_count() -> i64 {
    store().lock().unwrap().len() as i64
}
