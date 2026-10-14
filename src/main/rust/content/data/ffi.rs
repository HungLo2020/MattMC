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
//! count, fields), 11 slot (u32), 12 sound reference (string), 13 inline
//! sound (string, u8 has range, f32 range), 14 holder set slot (u32, u8
//! parsed, then u32 sets, each 0 tag string or 1 u32 IDs and strings).
//!
//! Encoded values (`O`): 1 false, 2 true, 3 int, 4 float, 5 double,
//! 6 string, 7 list, 8 map, 11 slot (u32), 14 slot omitted when Java's
//! value equals its default (u32), 15 registered sound (string).

use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use super::schema::{D, O, R};
use super::value::{Mode, Node, Num, Value};
use super::Definition;

type Entry = Result<Arc<Definition>, String>;

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
    store().lock().unwrap().get(&handle).and_then(|e| e.as_ref().ok().cloned())
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
            14 => D::Holders(self.u32()?, None),
            12 => D::SoundRef(self.string()?),
            13 => {
                let id = self.string()?;
                let has = self.u8()? != 0;
                let range = f32::from_bits(self.u32()?);
                D::SoundDirect(id, has.then_some(range))
            }
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

impl Out {
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
            D::SoundRef(s) => {
                self.u8(12);
                self.string(s);
            }
            D::SoundDirect(s, range) => {
                self.u8(13);
                self.string(s);
                self.u8(u8::from(range.is_some()));
                self.u32(range.unwrap_or(0.0).to_bits());
            }
            D::Holders(slot, refs) => {
                self.u8(14);
                self.u32(*slot);
                match refs {
                    None => self.u8(0),
                    Some(sets) => {
                        self.u8(1);
                        self.u32(sets.len() as u32);
                        for set in sets {
                            match set {
                                super::schema::Refs::Tag(tag) => {
                                    self.u8(0);
                                    self.string(tag);
                                }
                                super::schema::Refs::Ids(ids) => {
                                    self.u8(1);
                                    self.u32(ids.len() as u32);
                                    ids.iter().for_each(|i| self.string(i));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    fn encoded(&mut self, o: &O) {
        match o {
            O::Bool(v) => self.u8(if *v { 2 } else { 1 }),
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
            O::SoundRef(s) => {
                self.u8(15);
                self.string(s);
            }
        }
    }
}

/// Status 1, the decoded value, its slots (present flag, Java node index or
/// u32::MAX, input tree) and logs; or status 0 and the error.
fn payload(entry: &Entry) -> Vec<u8> {
    let mut o = Out::default();
    match entry {
        Err(error) => {
            o.u8(0);
            o.string(error);
        }
        Ok(def) => {
            o.u8(1);
            o.decoded(&def.value);
            o.u32(def.slots.len() as u32);
            for slot in &def.slots {
                match slot {
                    None => o.u8(0),
                    Some(node) => {
                        o.u8(1);
                        o.u32(node.index.unwrap_or(u32::MAX));
                        o.node(node);
                    }
                }
            }
            o.u32(def.logs.len() as u32);
            def.logs.iter().for_each(|l| o.string(l));
        }
    }
    o.0
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

fn guarded(f: impl FnOnce() -> R<Definition>) -> i64 {
    let entry = catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|_| Err("data decode panicked".into()));
    keep(entry.map(Arc::new))
}

/// Decodes a data pack file of `registry` into the store; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_decode_file(registry: i32, network: i32, bytes: *const u8, len: i64) -> i64 {
    let input = slice(bytes, len);
    guarded(|| super::decode_file(registry as usize, network != 0, input))
}

/// Decodes a value tree from Java (`mode` 0 JsonOps, 1 NbtOps) into the store; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_decode_tree(registry: i32, network: i32, mode: i32, registry_ops: i32, bytes: *const u8, len: i64) -> i64 {
    let input = slice(bytes, len);
    guarded(|| {
        let node = In { bytes: input, pos: 0, next_index: 0 }.node()?;
        super::decode(registry as usize, network != 0, &node, if mode == 1 { Mode::Nbt } else { Mode::Json }, registry_ops != 0)
    })
}

/// Stores a definition Java built (`Unprojector`), as `slots` delegated
/// slots, after checking it encodes and decodes under its schema; returns its handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_data_define(registry: i32, slots: i32, bytes: *const u8, len: i64) -> i64 {
    let input = slice(bytes, len);
    guarded(|| {
        let value = In { bytes: input, pos: 0, next_index: 0 }.decoded()?;
        super::define(registry as usize, value, slots.max(0) as usize)
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
    match catch_unwind(AssertUnwindSafe(|| super::encode(&def, network != 0))).unwrap_or_else(|_| Err("data encode panicked".into())) {
        Ok(value) => {
            o.u8(1);
            o.encoded(&value);
        }
        Err(error) => {
            o.u8(0);
            o.string(&error);
        }
    }
    copy_out(&o.0, out, capacity)
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
