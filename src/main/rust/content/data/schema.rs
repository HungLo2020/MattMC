//! Declarative record schemas with DFU's decode and encode rules.
//!
//! A schema decodes an input [`Node`] into a canonical [`D`] value, and
//! encodes a [`D`] back into an output tree in the field order and with the
//! default omission Java's `RecordCodecBuilder` codecs use. Values that
//! belong to registries Java still owns are [`S::Java`] leaves: Java's codec
//! decodes them while Rust decodes, encoding leaves a slot for Java to fill.

use std::borrow::Cow;
use std::cmp::Ordering;

use super::value::{java_double, java_float, Mode, Node, Num, Value};
use crate::content::tags::Location;

pub type R<T> = Result<T, String>;

pub enum S {
    Bool,
    Int,
    /// `ExtraCodecs.intRange` and `NON_NEGATIVE_INT` / `POSITIVE_INT`, inclusive.
    IntRange(i32, i32),
    /// DFU's `Codec.intRange`, inclusive (its own wording).
    DfuIntRange(i32, i32),
    /// `Codec.LONG`: a number's `longValue`.
    Long,
    /// `ExtraCodecs.UNSIGNED_BYTE`: a number's `byteValue` as 0..255; encodes
    /// a byte, refusing values above 255.
    UnsignedByte,
    /// `Codec.string(min, max)`: length in UTF-16 units.
    SizedStr(usize, usize),
    /// `ExtraCodecs.NON_NEGATIVE_FLOAT`: by `Float.compareTo`, so -0.0 fails.
    NonNegativeFloat,
    Float,
    /// `Codec.floatRange`: inclusive, by `Float.compareTo`.
    FloatRange(f32, f32),
    /// `ExtraCodecs.POSITIVE_FLOAT`: above 0, at most `Float.MAX_VALUE`.
    PositiveFloat,
    Double,
    Str,
    /// `ResourceLocation.CODEC` (and `ResourceKey.codec`); canonical "namespace:path".
    Location,
    /// `ExtraCodecs.RESOURCE_PATH_CODEC`.
    ResourcePath,
    /// `StringRepresentable.fromEnum`: serialized names.
    Enum(&'static [&'static str]),
    Record(&'static [F]),
    List(&'static S),
    /// `Codec.list(item, min, max)` (`sizeLimitedListOf`): entries past `max`
    /// are not decoded; too few decoded or too many given is an error.
    SizedList(&'static S, usize, usize),
    /// A list whose failing entries are dropped and logged with this prefix (`promotePartial`).
    PartialList(&'static S, &'static str),
    /// `Codec.simpleMap` / `unboundedMap` from a string-keyed map.
    Map(&'static S, &'static S),
    /// A `"type"`-keyed dispatch over a built-in type registry (named by its
    /// path, for errors), by case path in `minecraft`.
    Dispatch(&'static str, &'static [Case]),
    /// `BuiltInRegistries.ENTITY_TYPE.byNameCodec()`, against the Rust entity catalog.
    EntityType,
    /// `MinMaxBounds.Doubles.CODEC`: a number, or `{min, max}` with no swapped bounds.
    DoubleBounds,
    /// Decoded, then checked.
    Validated(&'static S, fn(&D) -> R<()>),
    /// `Codec.either`: the first schema, else the second (a two-case variant).
    Either(&'static S, &'static S),
    /// Decoded, then rewritten to its canonical form (an `xmap` that
    /// normalizes, such as a typed constant becoming the plain number).
    Normalize(&'static S, fn(D) -> D),
    /// Decoded, then mapped by a function that may fail (`comapFlatMap`),
    /// with `DataResult.flatMap`'s partial rules.
    Then(&'static S, fn(D) -> DR),
    /// `Codec.INT_STREAM`: a list of numbers read as ints (`getIntStream`);
    /// encodes as an int list (an `IntArrayTag` in NBT).
    IntStream,
    /// `BlockState.CODEC` against the installed block registry: a state ID.
    BlockState,
    /// `BuiltInRegistries.BLOCK.byNameCodec()` taken to the block's default
    /// state ID (`Block::defaultBlockState`).
    BlockDefaultState,
    /// `ComponentSerialization.createLegacyComponentMatcher`: a `MapCodec`
    /// dispatched on `key` (a string ID) when present, else the first case
    /// whose fields decode; encodes the case's fields without the key.
    Matcher(&'static str, &'static [Case]),
    /// A dispatch on `key` whose value `key_schema` decodes to a case name
    /// (an enum codec, possibly validated); the key is written last.
    KeyDispatch(&'static str, &'static S, &'static [Case]),
    /// A hand-written codec.
    Custom(&'static Custom),
    /// A string checked by a parser Java owns (a selector pattern, an
    /// untrusted URI), called back during decoding (`Leaf`).
    LeafStr(Leaf),
    /// A delegated `MapCodec` reading the containing map (`ItemStack.MAP_CODEC`
    /// in a hover event); Java merges its encoding into the parent map.
    DelegateMap,
    /// A codec Java still owns (`NativeValues.JavaCodec`, by ordinal), run
    /// by Java while Rust decodes so its errors and partials compose.
    Java(u16),
}

/// One dispatch case: its type name (path in `minecraft`), fields and,
/// for cases whose codec validates, the check.
pub struct Case {
    pub name: &'static str,
    pub fields: &'static [F],
    pub check: Option<fn(&D) -> R<()>>,
}

pub const fn case(name: &'static str, fields: &'static [F]) -> Case {
    Case { name, fields, check: None }
}

pub const fn checked(name: &'static str, fields: &'static [F], check: fn(&D) -> R<()>) -> Case {
    Case { name, fields, check: Some(check) }
}

/// String parsers Java owns: the command system's selector parser and the
/// JDK's URI parser. Rust calls back into Java to run them.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Leaf {
    /// `SelectorPattern.CODEC`.
    Selector = 0,
    /// `ExtraCodecs.UNTRUSTED_URI`.
    UntrustedUri = 1,
}

/// The Java callback: `(leaf, utf8, len, out, capacity)` returns -1 when the
/// string parses, else the error message's length (copied when it fits).
pub type LeafCheck = unsafe extern "C" fn(i32, *const u8, i64, *mut u8, i64) -> i64;
static LEAF_CHECK: std::sync::OnceLock<LeafCheck> = std::sync::OnceLock::new();

/// Installs Java's leaf parser callback (once).
pub fn set_leaf_check(check: LeafCheck) {
    let _ = LEAF_CHECK.set(check);
}

fn leaf_check(leaf: Leaf, text: &str) -> R<()> {
    if let Some(msg) = pending_thrown() {
        return Err(msg);
    }
    let check = LEAF_CHECK.get().ok_or("The Java leaf parsers are not installed")?;
    let mut buffer = vec![0u8; 256];
    loop {
        // SAFETY: the callback reads `text` and writes at most `capacity` bytes.
        let n = unsafe { check(leaf as i32, text.as_ptr(), text.len() as i64, buffer.as_mut_ptr(), buffer.len() as i64) };
        if n == -1 {
            return Ok(());
        }
        // -2 - n bytes: Java's exception id and message.
        let (thrown, n) = if n < -1 { (true, (-2 - n) as usize) } else { (false, n as usize) };
        if n > buffer.len() {
            // Java keeps the answer for this retry rather than parsing again.
            buffer.resize(n, 0);
            continue;
        }
        if thrown {
            let id = u32::from_le_bytes(buffer[..4].try_into().unwrap());
            return Err(throw_java(id, &String::from_utf8_lossy(&buffer[4..n])));
        }
        return Err(String::from_utf8_lossy(&buffer[..n]).into_owned());
    }
}

/// What Java's delegated decode answered.
pub enum JavaAnswer {
    /// No Java frame (a data pack file): resolve the slot lazily.
    Lazy,
    Ok,
    Err { msg: String, partial: bool },
}

/// The Java callback: `(kind, id, name, len, node, slot, out, capacity)`
/// decodes Java's preorder `node` of the current decode with codec `id`
/// (kind 0; with `node` -1, the JSON text in `name` instead) or the
/// persistent data component named `name` (kind 1), keeps
/// the value (or partial) at `slot`, and returns the answer's length:
/// u8 status (0 error, 1 ok, 2 lazy, 3 exception), then for an error the
/// message and u8 partial, for an exception u32 Java's id for the original
/// `Throwable` and the message. An answer longer than `capacity` is kept by
/// Java and returned by the immediate retry, without decoding again.
pub type JavaDecode = unsafe extern "C" fn(i32, i32, *const u8, i64, i32, i32, *mut u8, i64) -> i64;
static JAVA_DECODE: std::sync::OnceLock<JavaDecode> = std::sync::OnceLock::new();

pub fn set_java_decode(decode: JavaDecode) {
    let _ = JAVA_DECODE.set(decode);
}

/// Java's callback installing the block registry (`NativeBlockRegistry.ready()`).
pub type InstallBlocks = unsafe extern "C" fn() -> i32;
static INSTALL_BLOCKS: std::sync::OnceLock<InstallBlocks> = std::sync::OnceLock::new();

pub fn set_install_blocks(install: InstallBlocks) {
    let _ = INSTALL_BLOCKS.set(install);
}

/// The block registry, which Java installs on the first block state a
/// decode meets, so values without block states never wait for it.
///
/// If it can't be installed, the call ends with [`BLOCKS_MISSING`] pending,
/// so no further Java callback runs and Java's entry point throws its guard's
/// exception. An exception already pending stands and no callback runs.
fn blocks() -> R<&'static crate::content::block::BlockRegistry> {
    blocks_from(crate::content::block::installed, || {
        if let Some(install) = INSTALL_BLOCKS.get() {
            // SAFETY: the callback takes no arguments and only installs the registry.
            unsafe { install() };
        }
    })
}

/// [`blocks`] over an installed-registry lookup and Java's install callback.
pub(crate) fn blocks_from<T>(installed: impl Fn() -> Option<T>, install: impl FnOnce()) -> R<T> {
    if let Some(registry) = installed() {
        return Ok(registry);
    }
    if let Some(msg) = pending_thrown() {
        return Err(msg);
    }
    install();
    installed().ok_or_else(|| throw_as(BLOCKS_MISSING, "The block registry is not installed"))
}

/// `NativeValues.JavaCodec` ordinals.
pub const JAVA_DIALOG: u16 = 0;
pub const JAVA_NBT: u16 = 1;
pub const JAVA_PROFILE: u16 = 2;

/// Codec kinds the Java callback runs.
pub const JAVA_CODEC: i32 = 0;
pub const JAVA_COMPONENT: i32 = 1;

fn java_answer(kind: i32, id: i32, name: &str, node: &Node, slot: u32) -> JavaAnswer {
    if let Some(msg) = pending_thrown() {
        return JavaAnswer::Err { msg, partial: false };
    }
    let Some(call) = JAVA_DECODE.get() else { return JavaAnswer::Lazy };
    // A node Java holds is named by its index. One Rust parsed (a data pack
    // file) is sent as an input tree (numbers as their text), after the
    // component type's name (u32 length and UTF-8) for a component codec.
    let tree;
    let (index, name): (i32, &[u8]) = match node.index {
        Some(index) => (index as i32, name.as_bytes()),
        None => {
            let mut bytes = Vec::with_capacity(64);
            if kind != JAVA_CODEC {
                bytes.extend_from_slice(&(name.len() as u32).to_le_bytes());
                bytes.extend_from_slice(name.as_bytes());
            }
            tree = super::ffi::write_tree(node, bytes);
            (-1, &tree)
        }
    };
    // Most answers fit on the stack; a larger one is retried into the heap.
    let mut small = [0u8; 256];
    let mut large = Vec::new();
    let mut buffer: &mut [u8] = &mut small;
    loop {
        // SAFETY: the callback reads `name` and writes at most `capacity` bytes.
        let n = unsafe { call(kind, id, name.as_ptr(), name.len() as i64, index, slot as i32, buffer.as_mut_ptr(), buffer.len() as i64) } as usize;
        if n > buffer.len() {
            large.resize(n, 0);
            buffer = &mut large;
            continue;
        }
        let text = |from: usize| -> (String, usize) {
            let len = u32::from_le_bytes(buffer[from..from + 4].try_into().unwrap()) as usize;
            (String::from_utf8_lossy(&buffer[from + 4..from + 4 + len]).into_owned(), from + 4 + len)
        };
        return match buffer[0] {
            1 => JavaAnswer::Ok,
            0 => {
                let (msg, at) = text(1);
                JavaAnswer::Err { msg, partial: buffer[at] != 0 }
            }
            3 => {
                let thrown = u32::from_le_bytes(buffer[1..5].try_into().unwrap());
                let (msg, _) = text(5);
                JavaAnswer::Err { msg: throw_java(thrown, &msg), partial: false }
            }
            _ => JavaAnswer::Lazy,
        };
    }
}

/// A value Java's codec decodes now (when the input came from Java) or,
/// for a data pack file, when the projection resolves it.
pub fn java_delegate(kind: i32, id: i32, name: &str, node: &Node, cx: &mut Cx) -> DR {
    let slot = cx.slots.len() as u32;
    match java_answer(kind, id, name, node, slot) {
        JavaAnswer::Lazy | JavaAnswer::Ok => Ok(cx.slot(Some(node.clone()))),
        JavaAnswer::Err { msg, partial: true } => {
            let slot = cx.slot(Some(node.clone()));
            Err(Fail { msg, partial: Some(slot) })
        }
        JavaAnswer::Err { msg, partial: false } => Err(msg.into()),
    }
}

/// A hand-written codec: decode, encode and the checks it runs on encode.
pub struct Custom {
    pub decode: fn(&Node, &mut Cx) -> DR,
    pub encode: for<'a> fn(&'a D) -> R<O<'a>>,
    pub check: fn(&D) -> R<()>,
}

pub struct F {
    pub name: &'static str,
    pub schema: &'static S,
    pub presence: P,
}

pub const fn f(name: &'static str, schema: &'static S, presence: P) -> F {
    F { name, schema, presence }
}

#[derive(Clone, Copy)]
pub enum P {
    /// `fieldOf`.
    Required,
    /// `optionalFieldOf`: absent, or present and valid.
    Optional,
    /// `optionalFieldOf(name, default)`: omitted on encode when equal to the default.
    Default(Dv),
    /// `optionalFieldOf(name, default)` whose Java default never equals a
    /// value (no `equals`): always encoded.
    DefaultAlways(Dv),
    /// `fieldOf(...).orElse(default)`: missing or invalid is the default; always encoded.
    OrElse(Dv),
    /// `lenientOptionalFieldOf`: invalid is absent.
    Lenient,
    /// `lenientOptionalFieldOf(name, default)`: missing or invalid is the
    /// default, omitted on encode when equal.
    LenientDefault(Dv),
    /// A `MapCodec` record merged into its parent; the name only labels it.
    Inline,
    /// A delegated `optionalFieldOf(name, default)` whose default Java holds.
    JavaDefault,
}

/// Default values, in canonical form.
#[derive(Clone, Copy)]
pub enum Dv {
    Bool(bool),
    Int(i32),
    Long(i64),
    /// A normalized `Either` value's plain form (`Variant(0, [int])`).
    Variant0Int(i32),
    Float(f32),
    /// An enum's serialized name.
    Name(&'static str),
    EmptyMap,
    EmptyList,
    /// A normalized float provider's plain constant (`Variant(0, [float])`).
    Variant0Float(f32),
    /// `Vec3i.ZERO` / `BlockPos.ZERO`.
    ZeroInts3,
    /// `Vec3.ZERO`.
    ZeroDoubles3,
}

impl Dv {
    fn to_d(self) -> D {
        match self {
            Dv::Bool(v) => D::Bool(v),
            Dv::Int(v) => D::Int(v),
            Dv::Long(v) => D::Num(Num::Long(v)),
            Dv::Variant0Int(v) => D::Variant(0, vec![D::Int(v)]),
            Dv::Float(v) => D::Float(v),
            Dv::Name(v) => D::Str(v.into()),
            Dv::EmptyMap => D::Map(Vec::new()),
            Dv::EmptyList => D::List(Vec::new()),
            Dv::Variant0Float(v) => D::Variant(0, vec![D::Float(v)]),
            Dv::ZeroInts3 => D::List(vec![D::Int(0); 3]),
            Dv::ZeroDoubles3 => D::List(vec![D::Double(0.0); 3]),
        }
    }
}

/// A decoded value.
#[derive(Clone, Debug)]
pub enum D {
    Absent,
    Bool(bool),
    Int(i32),
    Float(f32),
    Double(f64),
    Str(String),
    List(Vec<D>),
    Map(Vec<(String, D)>),
    /// Fields in schema order.
    Record(Vec<D>),
    /// A dispatch case and its fields.
    Variant(u16, Vec<D>),
    /// A delegated value: index into the decoded element's slots.
    Slot(u32),
    /// A number kept as its source held it (`ExtraCodecs.JAVA`'s values).
    Num(Num),
}

impl PartialEq for D {
    /// `Objects.equals` on the Java values: floats compare by bits.
    fn eq(&self, other: &D) -> bool {
        match (self, other) {
            (D::Float(a), D::Float(b)) => a.to_bits() == b.to_bits(),
            (D::Double(a), D::Double(b)) => a.to_bits() == b.to_bits(),
            (D::Absent, D::Absent) => true,
            (D::Bool(a), D::Bool(b)) => a == b,
            (D::Int(a), D::Int(b)) => a == b,
            (D::Str(a), D::Str(b)) => a == b,
            (D::List(a), D::List(b)) | (D::Record(a), D::Record(b)) => a == b,
            (D::Map(a), D::Map(b)) => a == b,
            (D::Variant(t, a), D::Variant(u, b)) => t == u && a == b,
            (D::Slot(a), D::Slot(b)) => a == b,
            // Parsed references derive from the slot's own input.
            (D::Num(a), D::Num(b)) => a == b,
            _ => false,
        }
    }
}

/// Decoding state: the source's rules, delegated input nodes and logged partial errors.
pub struct Cx {
    pub mode: Mode,
    /// Whether the source is a `RegistryOps` (registry references resolve; otherwise sounds are inline).
    pub registry_ops: bool,
    pub slots: Vec<Option<Node>>,
    pub logs: Vec<String>,
}

impl Cx {
    pub fn new(mode: Mode, registry_ops: bool) -> Cx {
        Cx { mode, registry_ops, slots: Vec::new(), logs: Vec::new() }
    }
    fn slot(&mut self, node: Option<Node>) -> D {
        self.slots.push(node);
        D::Slot(self.slots.len() as u32 - 1)
    }
}

/// A decode failure as DFU reports it: the message and, where DFU keeps
/// one, the partial value (`DataResult.Error`'s `partialValue`).
#[derive(Clone, Debug)]
pub struct Fail {
    pub msg: String,
    pub partial: Option<D>,
}

impl From<&str> for Fail {
    fn from(msg: &str) -> Fail {
        Fail { msg: msg.to_owned(), partial: None }
    }
}

impl From<String> for Fail {
    fn from(msg: String) -> Fail {
        Fail { msg, partial: None }
    }
}

impl std::fmt::Display for Fail {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.msg)
    }
}

pub type DR = Result<D, Fail>;

thread_local! {
    static THROWN: std::cell::RefCell<Option<Thrown>> = const { std::cell::RefCell::new(None) };
}

/// The first exception of the current native call: its class, message and,
/// for [`JAVA_THROWN`], Java's id for the original `Throwable`.
pub type Thrown = (u8, String, u32);

/// An exception class Java throws for a recorded [`throw`].
pub const ILLEGAL_ARGUMENT: u8 = 0;
pub const NULL_POINTER: u8 = 1;
/// A `Throwable` a Java codec or parser threw during a callback: Java keeps
/// it and rethrows that same object.
pub const JAVA_THROWN: u8 = 3;
/// A block state was met but the block registry could not be installed:
/// Java throws its guard's `IllegalStateException`, caused by the failure.
pub const BLOCKS_MISSING: u8 = 4;

/// An exception a Java codec throws out of decoding (a constructor's
/// argument check): recorded for the caller, which reports it so Java
/// throws `IllegalArgumentException`; the decode's own result is moot.
/// Returns the message as an error, ending the failing branch.
pub fn throw(message: &str) -> String {
    throw_as(ILLEGAL_ARGUMENT, message)
}

/// [`throw`] with the Java exception class.
pub fn throw_as(class: u8, message: &str) -> String {
    keep_first((class, message.to_owned(), 0))
}

/// Records the original exception Java threw during a callback (`id`).
pub fn throw_java(id: u32, message: &str) -> String {
    keep_first((JAVA_THROWN, message.to_owned(), id))
}

/// Keeps the first exception: the one the original codec would have thrown.
fn keep_first(thrown: Thrown) -> String {
    THROWN.with(|t| t.borrow_mut().get_or_insert(thrown).1.clone())
}

/// The current call's pending exception message: once the original codec
/// would have thrown, no further Java callbacks run.
pub fn pending_thrown() -> Option<String> {
    THROWN.with(|t| t.borrow().as_ref().map(|t| t.1.clone()))
}

/// Runs one native call with its own exception record, restoring an outer
/// call's (Java re-entering native decoding from a callback); returns the
/// first exception `f` recorded.
pub fn thrown_scope<T>(f: impl FnOnce() -> T) -> (T, Option<Thrown>) {
    let outer = THROWN.with(|t| t.borrow_mut().take());
    let value = f();
    let inner = THROWN.with(|t| std::mem::replace(&mut *t.borrow_mut(), outer));
    (value, inner)
}

/// `DataResult.appendMessages`.
fn join(first: &str, second: &str) -> String {
    format!("{first}; {second}")
}

/// `DataResult.flatMap` with a check: a failure's partial is checked too,
/// and a failing check replaces it.
pub fn check_result(result: DR, check: impl FnOnce(&D) -> R<()>) -> DR {
    match result {
        Ok(d) => {
            check(&d)?;
            Ok(d)
        }
        Err(Fail { msg, partial: Some(p) }) => match check(&p) {
            Ok(()) => Err(Fail { msg, partial: Some(p) }),
            Err(second) => Err(Fail { msg: join(&msg, &second), partial: None }),
        },
        Err(fail) => Err(fail),
    }
}

/// `DataResult.map`: the partial maps too.
pub fn map_result(result: DR, f: impl FnOnce(D) -> D) -> DR {
    match result {
        Ok(d) => Ok(f(d)),
        Err(Fail { msg, partial }) => Err(Fail { msg, partial: partial.map(f) }),
    }
}

/// Parts combined with `apply2stable`, as records, lists and map entries
/// decode: messages joined last part first, and each part's value or partial.
/// Values stay plain until the first failure.
#[derive(Default)]
struct Parts {
    ok: Vec<D>,
    /// After a failure: every part's value or partial.
    slow: Option<Vec<Option<D>>>,
    errors: Vec<String>,
}

impl Parts {
    fn with_capacity(n: usize) -> Parts {
        Parts { ok: Vec::with_capacity(n), slow: None, errors: Vec::new() }
    }
    fn push(&mut self, result: DR) {
        if self.slow.is_none() {
            match result {
                Ok(d) => {
                    self.ok.push(d);
                    return;
                }
                Err(fail) => {
                    self.slow = Some(std::mem::take(&mut self.ok).into_iter().map(Some).collect());
                    self.push(Err(fail));
                    return;
                }
            }
        }
        let slow = self.slow.as_mut().unwrap();
        match result {
            Ok(d) => slow.push(Some(d)),
            Err(Fail { msg, partial }) => {
                self.errors.push(msg);
                slow.push(partial);
            }
        }
    }
    fn message(&self) -> Option<String> {
        (!self.errors.is_empty()).then(|| self.errors.iter().rev().map(String::as_str).collect::<Vec<_>>().join("; "))
    }
    /// Each part's value or partial, in order.
    fn values(&self) -> Vec<Option<&D>> {
        match &self.slow {
            None => self.ok.iter().map(Some).collect(),
            Some(slow) => slow.iter().map(Option::as_ref).collect(),
        }
    }
    /// The values and partials present (a list keeps these).
    fn present(self) -> Vec<D> {
        match self.slow {
            None => self.ok,
            Some(slow) => slow.into_iter().flatten().collect(),
        }
    }
    /// A record: partial only when every part has a value.
    fn all(self, wrap: impl FnOnce(Vec<D>) -> D) -> DR {
        let message = self.message();
        match (message, self.slow) {
            (None, _) => Ok(wrap(self.ok)),
            (Some(msg), Some(slow)) => Err(Fail { msg, partial: slow.into_iter().collect::<Option<Vec<D>>>().map(wrap) }),
            (Some(msg), None) => Err(msg.into()),
        }
    }
}

/// A primitive accessor's error in the source's `DynamicOps`.
pub fn not_msg(kind: &str, node: &Node, mode: Mode) -> String {
    match (mode, kind) {
        (Mode::Json, "map") => format!("Not a JSON object: {}", node.show(mode)),
        (Mode::Json, "list") => format!("Not a json array: {}", node.show(mode)),
        (Mode::Json, _) => format!("Not a {kind}: {}", node.show(mode)),
        // NbtOps reads booleans through `getNumberValue`.
        (Mode::Nbt, "number" | "boolean") => "Not a number".into(),
        (Mode::Nbt, "string") => "Not a string".into(),
        (Mode::Nbt, _) => format!("Not a {kind}: {}", node.show(mode)),
    }
}

fn not<T>(kind: &str, node: &Node, mode: Mode) -> R<T> {
    Err(not_msg(kind, node, mode))
}

fn number(node: &Node, mode: Mode) -> R<&Num> {
    match &node.value {
        Value::Number(n) => Ok(n),
        _ => not("number", node, mode),
    }
}

fn string(node: &Node, mode: Mode) -> R<&str> {
    match &node.value {
        Value::String(s) => Ok(s),
        _ => not("string", node, mode),
    }
}

fn map_members(node: &Node, mode: Mode) -> R<&[(String, Node)]> {
    match &node.value {
        Value::Map(m) => Ok(m),
        _ => not("map", node, mode),
    }
}

fn list_items(node: &Node, mode: Mode) -> R<&[Node]> {
    match &node.value {
        Value::List(items) => Ok(items),
        _ => not("list", node, mode),
    }
}

/// `MapLike.toString`.
fn map_like(node: &Node, mode: Mode) -> String {
    format!("MapLike[{}]", node.show(mode))
}

/// `Float.compareTo` / `Double.compareTo` ordering.
fn in_range<T: Copy>(value: T, min: T, max: T, cmp: fn(&T, &T) -> Ordering) -> bool {
    cmp(&value, &min) != Ordering::Less && cmp(&value, &max) != Ordering::Greater
}

fn location(node: &Node, mode: Mode) -> R<String> {
    Ok(Location::parse(string(node, mode)?)?.to_string())
}

pub fn decode(s: &S, node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    Ok(match s {
        S::Bool => match (&node.value, mode) {
            (Value::Bool(v), _) => D::Bool(*v),
            (Value::Number(n), Mode::Nbt) => D::Bool(n.byte_value() != 0),
            _ => return Err(not_msg("boolean", node, mode).into()),
        },
        S::Int => D::Int(number(node, mode)?.int_value()),
        S::IntRange(min, max) => {
            let v = number(node, mode)?.int_value();
            if v < *min || v > *max {
                // `ExtraCodecs.NON_NEGATIVE_INT` / `POSITIVE_INT` word their errors.
                return Err(match (*min, *max) {
                    (0, i32::MAX) => format!("Value must be non-negative: {v}"),
                    (1, i32::MAX) => format!("Value must be positive: {v}"),
                    _ => format!("Value must be within range [{min};{max}]: {v}"),
                }
                .into());
            }
            D::Int(v)
        }
        S::Float => D::Float(number(node, mode)?.float_value()),
        S::DfuIntRange(min, max) => {
            let v = number(node, mode)?.int_value();
            if v < *min || v > *max {
                return Err(format!("Value {v} outside of range [{min}:{max}]").into());
            }
            D::Int(v)
        }
        S::Long => D::Num(Num::Long(number(node, mode)?.long_value())),
        S::UnsignedByte => D::Int(i32::from(number(node, mode)?.byte_value() as u8)),
        S::SizedStr(min, max) => {
            let text = string(node, mode)?;
            let length = text.encode_utf16().count();
            if length > *max {
                return Err(format!("String \"{text}\" is too long: {length}, expected range [{min}-{max}]").into());
            }
            if length < *min {
                return Err(format!("String \"{text}\" is too short: {length}, expected range [{min}-{max}]").into());
            }
            D::Str(text.to_owned())
        }
        S::NonNegativeFloat => {
            let v = number(node, mode)?.float_value();
            if v.total_cmp(&0.0) == Ordering::Less || v.total_cmp(&f32::MAX) == Ordering::Greater {
                return Err(format!("Value must be non-negative: {}", java_float(v)).into());
            }
            D::Float(v)
        }
        S::FloatRange(min, max) => {
            let v = number(node, mode)?.float_value();
            if !in_range(v, *min, *max, f32::total_cmp) {
                return Err(format!("Value {} outside of range [{}:{}]", java_float(v), java_float(*min), java_float(*max)).into());
            }
            D::Float(v)
        }
        S::PositiveFloat => {
            let v = number(node, mode)?.float_value();
            if v.total_cmp(&0.0) != Ordering::Greater || v.total_cmp(&f32::MAX) == Ordering::Greater {
                return Err(format!("Value must be positive: {}", java_float(v)).into());
            }
            D::Float(v)
        }
        S::Double => D::Double(number(node, mode)?.double_value()),
        S::Str => D::Str(string(node, mode)?.to_owned()),
        S::Location => D::Str(location(node, mode)?),
        S::ResourcePath => {
            let path = string(node, mode)?;
            if !crate::core::id::is_valid_path(path) {
                return Err(format!("Invalid string to use as a resource path element: {path}").into());
            }
            D::Str(path.to_owned())
        }
        S::Enum(names) => {
            let name = string(node, mode)?;
            if !names.contains(&name) {
                return Err(format!("Unknown element name:{name}").into());
            }
            D::Str(name.to_owned())
        }
        S::Record(fields) => {
            map_members(node, mode)?;
            return record(fields, node, cx);
        }
        S::List(item) => {
            let nodes = list_items(node, mode)?;
            let mut parts = Parts::with_capacity(nodes.len());
            for n in nodes {
                parts.push(decode(item, n, cx));
            }
            let message = parts.message();
            let items = D::List(parts.present());
            // `ListCodec` keeps the decoded elements as the partial.
            return match message {
                None => Ok(items),
                Some(msg) => Err(Fail { msg, partial: Some(items) }),
            };
        }
        S::SizedList(item, min, max) => {
            let items = list_items(node, mode)?;
            let mut parts = Parts::with_capacity(items.len().min(*max));
            for n in items.iter().take(*max) {
                parts.push(decode(item, n, cx));
            }
            let message = parts.message();
            let decoded: Vec<D> = parts.present();
            if decoded.len() < *min {
                return Err(format!("List is too short: {}, expected range [{min}-{max}]", decoded.len()).into());
            }
            if items.len() > *max {
                return Err(format!("List is too long: {}, expected range [{min}-{max}]", items.len()).into());
            }
            return match message {
                None => Ok(D::List(decoded)),
                Some(msg) => Err(Fail { msg, partial: Some(D::List(decoded)) }),
            };
        }
        S::PartialList(item, prefix) => {
            let mut parts = Parts::default();
            for n in list_items(node, mode)? {
                parts.push(decode(item, n, cx));
            }
            // `promotePartial`: one log of the combined errors.
            if let Some(msg) = parts.message() {
                cx.logs.push(format!("{prefix}{msg}"));
            }
            D::List(parts.present())
        }
        S::Map(key, value) => return map(key, value, node, cx),
        S::Dispatch(registry, cases) => {
            map_members(node, mode)?;
            let kind = node.get("type").ok_or_else(|| format!("Input does not contain a key [type]: {}", map_like(node, mode)))?;
            let location = Location::parse(string(kind, mode)?)?;
            let index = cases.iter().position(|c| &*location.namespace == "minecraft" && c.name == &*location.path)
                .ok_or_else(|| format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:{registry}]: {location}"))?;
            return case_result(index, &cases[index], node, cx);
        }
        S::EntityType => {
            let location = Location::parse(string(node, mode)?)?;
            if &*location.namespace != "minecraft" || !entity_names().contains(&*location.path) {
                return Err(format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_type]: {location}").into());
            }
            D::Str(location.to_string())
        }
        S::DoubleBounds => {
            let (min, max) = match &node.value {
                Value::Map(_) => {
                    // The record side: invalid optional fields leave their
                    // partial absent, so `Codec.either` returns that side's
                    // error with the partial (errors joined last field first).
                    let opt = |key| node.get(key).map(|n| number(n, mode).map(Num::double_value)).transpose();
                    match (opt("min"), opt("max")) {
                        (Ok(min), Ok(max)) => (min, max),
                        (min, max) => {
                            let msg = [&max, &min].iter().filter_map(|r| r.as_ref().err()).cloned().collect::<Vec<_>>().join("; ");
                            let field = |r: &R<Option<f64>>| r.as_ref().ok().copied().flatten().map_or(D::Absent, D::Double);
                            return Err(Fail { msg, partial: Some(D::Record(vec![field(&min), field(&max)])) });
                        }
                    }
                }
                _ => {
                    let v = number(node, mode)
                        .map_err(|second| format!("Failed to parse either. First: {}; Second: {second}", not_msg("map", node, mode)))?
                        .double_value();
                    (Some(v), Some(v))
                }
            };
            if let (Some(a), Some(b)) = (min, max) {
                if a.total_cmp(&b) == Ordering::Greater {
                    return Err(format!("Swapped bounds in range: {} is higher than {}", java_double(a), java_double(b)).into());
                }
            }
            D::Record(vec![min.map_or(D::Absent, D::Double), max.map_or(D::Absent, D::Double)])
        }
        S::Validated(inner, check) => return check_result(decode(inner, node, cx), check),
        S::Either(first, second) => {
            // A first side whose input shape cannot match fails without a
            // partial: try the second, and spell out the first's error only
            // if both fail (DFU builds messages lazily).
            if rejects_shape(first, node, mode) {
                return match decode(second, node, cx) {
                    Ok(d) => Ok(D::Variant(1, vec![d])),
                    Err(Fail { msg, partial: Some(p) }) => Err(Fail { msg, partial: Some(D::Variant(1, vec![p])) }),
                    Err(two) => {
                        let one = decode(first, node, cx).err().map(|f| f.msg).unwrap_or_default();
                        Err(format!("Failed to parse either. First: {one}; Second: {}", two.msg).into())
                    }
                };
            }
            // Partial slots from a failed first try stay unused.
            let one = match decode(first, node, cx) {
                Ok(d) => return Ok(D::Variant(0, vec![d])),
                Err(fail) => fail,
            };
            let two = match decode(second, node, cx) {
                Ok(d) => return Ok(D::Variant(1, vec![d])),
                Err(fail) => fail,
            };
            return Err(match (one, two) {
                (Fail { msg, partial: Some(p) }, _) => Fail { msg, partial: Some(D::Variant(0, vec![p])) },
                (_, Fail { msg, partial: Some(p) }) => Fail { msg, partial: Some(D::Variant(1, vec![p])) },
                (a, b) => format!("Failed to parse either. First: {}; Second: {}", a.msg, b.msg).into(),
            });
        }
        S::Normalize(inner, normalize) => return map_result(decode(inner, node, cx), normalize),
        S::Then(inner, f) => return then(decode(inner, node, cx), *f),
        S::IntStream => {
            let items = list_items(node, mode).map_err(|_| match mode {
                Mode::Json => not_msg("list", node, mode),
                Mode::Nbt => "Not a list".to_owned(),
            })?;
            let ints = items.iter().map(|n| match &n.value {
                Value::Number(num) => Some(D::Int(num.int_value())),
                _ => None,
            });
            match ints.collect::<Option<Vec<_>>>() {
                Some(ints) => D::List(ints),
                None => return Err(format!("Some elements are not ints: {}", node.show(mode)).into()),
            }
        }
        S::BlockState => return block_state(node, cx),
        S::Matcher(key, cases) => return matcher(key, cases, node, cx),
        S::KeyDispatch(key, key_schema, cases) => {
            map_members(node, mode)?;
            let kind = node.get(key).ok_or_else(|| format!("Input does not contain a key [{key}]: {}", map_like(node, mode)))?;
            let D::Str(name) = decode(key_schema, kind, cx)? else { return Err("dispatch key is not a name".into()) };
            let index = cases.iter().position(|c| c.name == name).ok_or_else(|| format!("No case {name}"))?;
            return case_result(index, &cases[index], node, cx);
        }
        S::Custom(custom) => return (custom.decode)(node, cx),
        S::LeafStr(leaf) => {
            let text = string(node, mode)?;
            leaf_check(*leaf, text)?;
            D::Str(text.to_owned())
        }
        S::DelegateMap => cx.slot(Some(node.clone())),
        S::Java(id) => return java_delegate(JAVA_CODEC, i32::from(*id), "", node, cx),
        S::BlockDefaultState => {
            let registry = blocks()?;
            let location = Location::parse(string(node, mode)?)?;
            let block = registry.by_name(&location.to_string())
                .ok_or_else(|| format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: {location}"))?;
            D::Int(i32::from(registry.block(block).default_state().0))
        }
    })
}

/// A dispatch case's fields from `node`, as `Variant(index, fields)`, checked.
fn case_result(index: usize, case: &Case, node: &Node, cx: &mut Cx) -> DR {
    let result = map_result(record(case.fields, node, cx), move |r| match r {
        D::Record(fields) => D::Variant(index as u16, fields),
        other => other,
    });
    match case.check {
        Some(check) => check_result(result, check),
        None => result,
    }
}

/// Whether `node`'s shape alone makes `s` fail (without a partial value).
fn rejects_shape(s: &S, node: &Node, mode: Mode) -> bool {
    match (s, &node.value) {
        (S::Str | S::Location | S::Enum(_) | S::ResourcePath | S::LeafStr(_), v) => !matches!(v, Value::String(_)),
        (S::Int | S::IntRange(..) | S::DfuIntRange(..) | S::Long | S::UnsignedByte | S::NonNegativeFloat | S::Float | S::FloatRange(..) | S::Double | S::PositiveFloat, v) => !matches!(v, Value::Number(_)),
        (S::SizedStr(..), v) => !matches!(v, Value::String(_)),
        (S::Bool, Value::Bool(_)) => false,
        (S::Bool, Value::Number(_)) => mode == Mode::Json,
        (S::Bool, _) => true,
        (S::List(_) | S::SizedList(..) | S::PartialList(..) | S::IntStream, v) => !matches!(v, Value::List(_)),
        (S::Record(_) | S::Dispatch(..) | S::KeyDispatch(..) | S::Matcher(..), v) => !matches!(v, Value::Map(_)),
        (S::Validated(inner, _) | S::Normalize(inner, _) | S::Then(inner, _), _) => rejects_shape(inner, node, mode),
        (S::Either(a, b), _) => rejects_shape(a, node, mode) && rejects_shape(b, node, mode),
        _ => false,
    }
}

/// `createLegacyComponentMatcher`'s decode: typed by `key` (`StrictEither`),
/// else `FuzzyCodec`, the first case that decodes successfully.
fn matcher(key: &str, cases: &[Case], node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    map_members(node, mode)?;
    if let Some(kind) = node.get(key) {
        let name = string(kind, mode)?;
        let index = cases.iter().position(|c| c.name == name).ok_or_else(|| format!("Unknown element id: {name}"))?;
        return case_result(index, &cases[index], node, cx);
    }
    for (index, case) in cases.iter().enumerate() {
        // A case missing a required key fails; skip it unbuilt.
        if case.fields.iter().any(|f| matches!(f.presence, P::Required) && node.get(f.name).is_none()) {
            continue;
        }
        let slots = cx.slots.len();
        let logs = cx.logs.len();
        match case_result(index, case, node, cx) {
            Ok(d) => return Ok(d),
            Err(_) => {
                // A failed attempt leaves nothing behind.
                cx.slots.truncate(slots);
                cx.logs.truncate(logs);
            }
        }
    }
    Err("No matching codec found".into())
}

/// `DataResult.flatMap(f)`: a failure's partial is mapped too, and a
/// failing map's message is appended.
pub fn then(result: DR, f: fn(D) -> DR) -> DR {
    match result {
        Ok(d) => f(d),
        Err(Fail { msg, partial: Some(p) }) => match f(p) {
            Ok(v) => Err(Fail { msg, partial: Some(v) }),
            Err(second) => Err(Fail { msg: join(&msg, &second.msg), partial: second.partial }),
        },
        Err(fail) => Err(fail),
    }
}

/// `StateHolder.codec`: dispatched on `Name`; a block with properties reads
/// a lenient `Properties` map whose missing or invalid values stay default.
fn block_state(node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    let registry = blocks()?;
    map_members(node, mode)?;
    let name = node.get("Name").ok_or_else(|| format!("Input does not contain a key [Name]: {}", map_like(node, mode)))?;
    let location = Location::parse(string(name, mode)?)?;
    let id = registry.by_name(&location.to_string())
        .ok_or_else(|| format!("Unknown registry key in ResourceKey[minecraft:root / minecraft:block]: {location}"))?;
    let block = registry.block(id);
    let mut state = block.default_state();
    if let Some(Node { value: Value::Map(members), .. }) = node.get("Properties") {
        for property in block.properties() {
            let schema = registry.property(property);
            let found = members.iter().find(|(k, v)| k == schema.name() && v.value != Value::Null);
            if let Some((_, Node { value: Value::String(text), .. })) = found {
                if let Some(index) = schema.values().iter().position(|v| v == text) {
                    state = registry.with_value(state, property, index as u16).unwrap_or(state);
                }
            }
        }
    }
    Ok(D::Int(i32::from(state.0)))
}

fn encode_block_state(state: i32) -> R<O<'static>> {
    let registry = blocks()?;
    let state = crate::content::block::StateId(u16::try_from(state).map_err(|_| format!("Invalid block state {state}"))?);
    if usize::from(state.0) >= registry.state_count() {
        return Err(format!("Invalid block state {}", state.0));
    }
    let block = registry.block(registry.block_of(state));
    let mut entries = Vec::new();
    if state != block.default_state() {
        // `StateDefinition` nests a codec per property in name order; the
        // outermost (last name) writes first.
        let properties = block.properties().rev().map(|p| {
            let schema = registry.property(p);
            let value = registry.value(state, p).unwrap_or(0);
            (Cow::Borrowed(schema.name()), O::Str(Cow::Borrowed(schema.values()[usize::from(value)].as_str())))
        });
        entries.push((Cow::Borrowed("Properties"), O::Map(properties.collect())));
    }
    entries.push((Cow::Borrowed("Name"), O::Str(Cow::Borrowed(block.name()))));
    Ok(O::Map(entries))
}

/// `Codec.simpleMap` / `unboundedMap` (`BaseMapCodec`): failed entries are
/// dropped and named in the error, whose partial is the decoded entries.
fn map(key: &S, value: &S, node: &Node, cx: &mut Cx) -> DR {
    let mode = cx.mode;
    let mut out: Vec<(String, D)> = Vec::new();
    let mut errors: Vec<String> = Vec::new();
    let mut failed: Vec<(String, Node)> = Vec::new();
    for (k, v) in map_members(node, mode)? {
        let mut entry = Parts::default();
        entry.push(decode(key, &Node::new(Value::String(k.clone())), cx));
        entry.push(decode(value, v, cx));
        let message = entry.message();
        if let [Some(D::Str(k2)), Some(d)] = &entry.values()[..] {
            if out.iter().any(|(existing, _)| existing == k2) {
                failed.push((k.clone(), v.clone()));
                errors.push(format!("Duplicate entry for key: '{k2}'"));
                continue;
            }
            out.push((k2.clone(), (*d).clone()));
        }
        if let Some(msg) = message {
            failed.push((k.clone(), v.clone()));
            errors.push(msg);
        }
    }
    if errors.is_empty() {
        return Ok(D::Map(out));
    }
    let msg = errors.iter().rev().map(String::as_str).collect::<Vec<_>>().join("; ");
    let missed = Node::new(Value::Map(failed.into()));
    Err(Fail { msg: format!("{msg} missed input: {}", missed.show(mode)), partial: Some(D::Map(out)) })
}

/// A record's fields read from a map (a `MapCodec`'s decode).
pub fn decode_fields(fields: &[F], map: &Node, cx: &mut Cx) -> DR {
    record(fields, map, cx)
}

fn record(fields: &[F], map: &Node, cx: &mut Cx) -> DR {
    let mut parts = Parts::with_capacity(fields.len());
    for field in fields {
        if let P::Inline = field.presence {
            parts.push(match field.schema {
                S::Record(inner) => record(inner, map, cx),
                S::Matcher(key, cases) => matcher(key, cases, map, cx),
                S::DelegateMap => Ok(cx.slot(Some(map.clone()))),
                S::Custom(custom) => (custom.decode)(map, cx),
                _ => panic!("inline field {} is not a map codec", field.name),
            });
            continue;
        }
        let value = map.get(field.name);
        parts.push(match (field.presence, value) {
            (P::Required, None) => Err(format!("No key {} in {}", field.name, map_like(map, cx.mode)).into()),
            (P::Optional | P::Lenient, None) => Ok(D::Absent),
            (P::LenientDefault(d), None) => Ok(d.to_d()),
            (P::LenientDefault(d), Some(n)) => Ok(decode(field.schema, n, cx).unwrap_or(d.to_d())),
            (P::Default(d) | P::DefaultAlways(d) | P::OrElse(d), None) => Ok(d.to_d()),
            (P::JavaDefault, None) => Ok(cx.slot(None)),
            // As `Optional` above: an invalid value leaves Java's default as the partial.
            (P::JavaDefault, Some(n)) => match decode(field.schema, n, cx) {
                Err(Fail { msg, partial: None }) => Err(Fail { msg, partial: Some(cx.slot(None)) }),
                result => result,
            },
            (P::Lenient, Some(n)) => Ok(decode(field.schema, n, cx).unwrap_or(D::Absent)),
            // `OptionalFieldCodec` sets the partial to the parsed partial or
            // `Optional.empty()`: an invalid value leaves absent (or the default).
            (P::Optional | P::Default(_) | P::DefaultAlways(_), Some(n)) => decode(field.schema, n, cx).map_err(|fail| {
                let absent = match field.presence {
                    P::Default(d) | P::DefaultAlways(d) => d.to_d(),
                    _ => D::Absent,
                };
                Fail { msg: fail.msg, partial: Some(fail.partial.unwrap_or(absent)) }
            }),
            (P::OrElse(d), Some(n)) => Ok(decode(field.schema, n, cx).unwrap_or(d.to_d())),
            (_, Some(n)) => decode(field.schema, n, cx),
            (P::Inline, _) => unreachable!(),
        });
    }
    parts.all(D::Record)
}

fn entity_names() -> &'static std::collections::HashSet<&'static str> {
    static NAMES: std::sync::OnceLock<std::collections::HashSet<&'static str>> = std::sync::OnceLock::new();
    NAMES.get_or_init(|| crate::content::entity::definitions().iter().map(|d| d.name).collect())
}

/// An encoded value: Java turns it into its `DynamicOps` output.
#[derive(Clone, Debug, PartialEq)]
pub enum O<'a> {
    Bool(bool),
    /// `createIntList`: an int array in NBT, a list in JSON.
    IntList(Vec<i32>),
    Int(i32),
    Float(f32),
    Double(f64),
    Str(Cow<'a, str>),
    List(Vec<O<'a>>),
    Map(Vec<(Cow<'a, str>, O<'a>)>),
    /// A delegated value Java encodes.
    Slot(u32),
    /// A delegated `JavaDefault` field: Java omits the entry when it equals the default.
    SlotUnlessDefault(u32),
    /// A number created as its Java type (`JavaOps.convertTo`).
    Num(Num),
    /// A delegated `MapCodec`'s entries, merged into the enclosing map.
    SlotMerge(u32),
}

/// Encodes `d`, decoded with `stored`, through `target`. Records match
/// fields by name, so a direct definition encodes in its network form.
pub fn encode<'a>(target: &S, stored: &S, d: &'a D) -> R<O<'a>> {
    Ok(match (target, d) {
        (S::Record(t), D::Record(values)) => {
            let S::Record(s) = stored else { return Err("record schema mismatch".into()) };
            let mut entries = Vec::new();
            record_entries(t, s, values, &mut entries)?;
            O::Map(entries)
        }
        (S::Validated(inner, _), _) => encode(inner, unvalidated(stored), d)?,
        (S::Normalize(inner, _), _) => encode(inner, unvalidated(stored), d)?,
        (S::Then(inner, _), _) => encode(inner, unvalidated(stored), d)?,
        (S::IntStream, D::List(items)) => O::IntList(items.iter().map(|i| if let D::Int(v) = i { *v } else { 0 }).collect()),
        (S::BlockState | S::BlockDefaultState, D::Int(state)) => encode_block_state(*state)?,
        (S::Custom(custom), _) => (custom.encode)(d)?,
        (S::Matcher(_, cases), D::Variant(tag, values)) => {
            let fields = cases[*tag as usize].fields;
            let mut entries = Vec::new();
            record_entries(fields, fields, values, &mut entries)?;
            O::Map(entries)
        }
        (S::KeyDispatch(key, _, cases), D::Variant(tag, values)) => {
            let case = &cases[*tag as usize];
            let mut entries = Vec::new();
            record_entries(case.fields, case.fields, values, &mut entries)?;
            entries.push((Cow::Borrowed(*key), O::Str(Cow::Borrowed(case.name))));
            O::Map(entries)
        }
        (S::DelegateMap, D::Slot(i)) => O::SlotMerge(*i),
        (_, D::Num(n)) => O::Num(n.clone()),
        (S::Either(first, second), D::Variant(side, values)) => {
            let (target, stored_side) = match (side, stored) {
                (0, S::Either(f, _)) => (*first, *f),
                (_, S::Either(_, s)) => (*second, *s),
                (0, _) => (*first, *first),
                _ => (*second, *second),
            };
            encode(target, stored_side, &values[0])?
        }
        (S::UnsignedByte, D::Int(v)) => O::Num(Num::Byte(*v as i8)),
        (_, D::Bool(v)) => O::Bool(*v),
        (_, D::Int(v)) => O::Int(*v),
        (_, D::Float(v)) => O::Float(*v),
        (S::DoubleBounds, D::Record(bounds)) => match (&bounds[0], &bounds[1]) {
            (D::Double(a), D::Double(b)) if a.to_bits() == b.to_bits() => O::Double(*a),
            (min, max) => {
                let mut entries = Vec::new();
                if let D::Double(v) = min {
                    entries.push((Cow::Borrowed("min"), O::Double(*v)));
                }
                if let D::Double(v) = max {
                    entries.push((Cow::Borrowed("max"), O::Double(*v)));
                }
                O::Map(entries)
            }
        },
        (_, D::Double(v)) => O::Double(*v),
        (_, D::Str(v)) => O::Str(Cow::Borrowed(v)),
        (S::List(item) | S::PartialList(item, _) | S::SizedList(item, _, _), D::List(items)) => {
            let stored_item = match stored { S::List(i) | S::PartialList(i, _) | S::SizedList(i, _, _) => *i, _ => *item };
            O::List(items.iter().map(|d| encode(item, stored_item, d)).collect::<R<_>>()?)
        }
        (S::Map(_, value), D::Map(entries)) => {
            let stored_value = match stored { S::Map(_, v) => *v, _ => *value };
            O::Map(entries.iter().map(|(k, d)| Ok((Cow::Borrowed(k.as_str()), encode(value, stored_value, d)?))).collect::<R<_>>()?)
        }
        (S::Dispatch(_, cases), D::Variant(tag, values)) => {
            let (name, fields) = (cases[*tag as usize].name, cases[*tag as usize].fields);
            let mut entries = Vec::new();
            record_entries(fields, fields, values, &mut entries)?;
            // DFU's dispatch writes the case's fields, then the type.
            entries.push((Cow::Borrowed("type"), O::Str(Cow::Owned(format!("minecraft:{name}")))));
            O::Map(entries)
        }
        (_, D::Slot(i)) => O::Slot(*i),
        (_, other) => return Err(format!("cannot encode {other:?}")),
    })
}

/// The checks a value's codecs run when encoding it (`validate` applies
/// both ways), outermost first.
pub fn check(s: &S, d: &D) -> R<()> {
    match (s, d) {
        (S::Validated(inner, c), _) => {
            c(d)?;
            check(inner, d)
        }
        (S::Normalize(inner, _) | S::Then(inner, _), _) => check(inner, d),
        (S::Either(first, second), D::Variant(side, values)) => check(if *side == 0 { first } else { second }, &values[0]),
        (S::Dispatch(_, cases), D::Variant(tag, values)) => {
            let case = &cases[*tag as usize];
            if let Some(c) = case.check {
                c(d)?;
            }
            check_fields(case.fields, values)
        }
        (S::Record(fields), D::Record(values)) => check_fields(fields, values),
        (S::Matcher(_, cases) | S::KeyDispatch(_, _, cases), D::Variant(tag, values)) => {
            let case = &cases[*tag as usize];
            // The key's codec checks the case it encodes (an enum's `validate`).
            if let S::KeyDispatch(_, key_schema, _) = s {
                check(key_schema, &D::Str(case.name.to_owned()))?;
            }
            if let Some(c) = case.check {
                c(d)?;
            }
            check_fields(case.fields, values)
        }
        (S::Custom(custom), _) => (custom.check)(d),
        (S::List(item) | S::PartialList(item, _), D::List(items)) => items.iter().try_for_each(|i| check(item, i)),
        (S::SizedList(item, min, max), D::List(items)) => {
            if items.len() > *max {
                return Err(format!("List is too long: {}, expected range [{min}-{max}]", items.len()));
            }
            if items.len() < *min {
                return Err(format!("List is too short: {}, expected range [{min}-{max}]", items.len()));
            }
            items.iter().try_for_each(|i| check(item, i))
        }
        (S::Map(_, value), D::Map(entries)) => entries.iter().try_for_each(|(_, v)| check(value, v)),
        (S::DfuIntRange(min, max), D::Int(v)) if v < min || v > max => Err(format!("Value {v} outside of range [{min}:{max}]")),
        (S::UnsignedByte, D::Int(v)) if *v > 255 => Err(format!("Unsigned byte was too large: {v} > 255")),
        (S::SizedStr(min, max), D::Str(text)) => {
            let length = text.encode_utf16().count();
            if length > *max {
                Err(format!("String \"{text}\" is too long: {length}, expected range [{min}-{max}]"))
            } else if length < *min {
                Err(format!("String \"{text}\" is too short: {length}, expected range [{min}-{max}]"))
            } else {
                Ok(())
            }
        }
        (S::NonNegativeFloat, D::Float(v)) if v.total_cmp(&0.0) == Ordering::Less || v.total_cmp(&f32::MAX) == Ordering::Greater => {
            Err(format!("Value must be non-negative: {}", java_float(*v)))
        }
        (S::PositiveFloat, D::Float(v)) if v.total_cmp(&0.0) != Ordering::Greater || v.total_cmp(&f32::MAX) == Ordering::Greater => {
            Err(format!("Value must be positive: {}", java_float(*v)))
        }
        (S::IntRange(min, max), D::Int(v)) if v < min || v > max => Err(match (*min, *max) {
            (0, i32::MAX) => format!("Value must be non-negative: {v}"),
            (1, i32::MAX) => format!("Value must be positive: {v}"),
            _ => format!("Value must be within range [{min};{max}]: {v}"),
        }),
        _ => Ok(()),
    }
}

/// A record's checks: every field's errors, joined last field first as
/// `RecordCodecBuilder` combines them.
fn check_fields(fields: &[F], values: &[D]) -> R<()> {
    let mut errors = Vec::new();
    for (f, v) in fields.iter().zip(values) {
        let result = match (f.presence, v) {
            (_, D::Absent) => Ok(()),
            // An omitted default is never encoded, so never checked.
            (P::Default(d) | P::LenientDefault(d), v) if *v == d.to_d() => Ok(()),
            (P::Inline, v) => match (f.schema, v) {
                (S::Record(inner), D::Record(values)) => check_fields(inner, values),
                _ => check(f.schema, v),
            },
            (_, v) => check(f.schema, v),
        };
        if let Err(e) = result {
            errors.push(e);
        }
    }
    if errors.is_empty() {
        return Ok(());
    }
    Err(errors.iter().rev().map(String::as_str).collect::<Vec<_>>().join("; "))
}

fn unvalidated(s: &S) -> &S {
    match s {
        S::Validated(inner, _) | S::Normalize(inner, _) | S::Then(inner, _) => inner,
        other => other,
    }
}

/// The order `RecordCodecBuilder` writes a group of N members: its
/// applicative nesting regroups larger records (measured from DFU 8.0.16).
const GROUP_ORDER: [&[usize]; 17] = [
    &[],
    &[0],
    &[0, 1],
    &[0, 1, 2],
    &[0, 1, 2, 3],
    &[2, 3, 4, 0, 1],
    &[3, 4, 5, 0, 1, 2],
    &[3, 4, 5, 6, 0, 1, 2],
    &[4, 5, 6, 7, 0, 1, 2, 3],
    &[6, 7, 8, 4, 5, 0, 1, 2, 3],
    &[7, 8, 9, 5, 6, 2, 3, 4, 0, 1],
    &[8, 9, 10, 5, 6, 7, 2, 3, 4, 0, 1],
    &[9, 10, 11, 6, 7, 8, 3, 4, 5, 0, 1, 2],
    &[9, 10, 11, 12, 6, 7, 8, 3, 4, 5, 0, 1, 2],
    &[10, 11, 12, 13, 7, 8, 9, 3, 4, 5, 6, 0, 1, 2],
    &[11, 12, 13, 14, 7, 8, 9, 10, 3, 4, 5, 6, 0, 1, 2],
    &[12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3],
];

fn record_entries<'a>(target: &[F], stored: &[F], values: &'a [D], out: &mut Vec<(Cow<'a, str>, O<'a>)>) -> R<()> {
    let order = GROUP_ORDER.get(target.len()).ok_or_else(|| format!("record of {} members", target.len()))?;
    let same = std::ptr::eq(target, stored);
    for &i in order.iter() {
        let field = &target[i];
        // Usually the stored schema is the target; otherwise match by name.
        let index = if same { i } else { stored.iter().position(|s| s.name == field.name).ok_or_else(|| format!("field {} is not stored", field.name))? };
        let (stored_field, value) = (&stored[index], &values[index]);
        match field.presence {
            P::Inline => match (field.schema, stored_field.schema, value) {
                (S::Record(t), S::Record(s), D::Record(v)) => record_entries(t, s, v, out)?,
                (S::Matcher(_, cases), _, D::Variant(tag, v)) => {
                    let fields = cases[*tag as usize].fields;
                    record_entries(fields, fields, v, out)?;
                }
                (S::DelegateMap, _, D::Slot(i)) => out.push((Cow::Borrowed(field.name), O::SlotMerge(*i))),
                (S::Custom(custom), _, v) => match (custom.encode)(v)? {
                    O::Map(entries) => out.extend(entries),
                    _ => return Err(format!("inline field {} is not a map", field.name)),
                },
                _ => return Err(format!("inline field {} mismatch", field.name)),
            },
            P::LenientDefault(d) if *value == d.to_d() => {}
            P::Optional | P::Lenient if *value == D::Absent => {}
            P::Default(d) if *value == d.to_d() => {}
            P::JavaDefault => {
                let D::Slot(i) = value else { return Err(format!("{} is not a slot", field.name)) };
                out.push((Cow::Borrowed(field.name), O::SlotUnlessDefault(*i)));
            }
            _ => out.push((Cow::Borrowed(field.name), encode(field.schema, stored_field.schema, value)?)),
        }
    }
    Ok(())
}

/// An encoded value as an input node; delegated slots become placeholder
/// strings, which delegates accept unseen.
pub fn to_node(o: &O) -> Node {
    Node::new(match o {
        O::Bool(v) => Value::Bool(*v),
        O::IntList(v) => Value::List(v.iter().map(|i| Node::new(Value::Number(Num::Int(*i)))).collect::<Vec<_>>().into()),
        O::Int(v) => Value::Number(Num::Int(*v)),
        O::Float(v) => Value::Number(Num::Float(*v)),
        O::Double(v) => Value::Number(Num::Double(*v)),
        O::Str(s) => Value::String(s.to_string()),
        O::List(items) => Value::List(items.iter().map(to_node).collect::<Vec<_>>().into()),
        O::Map(entries) => Value::Map(entries.iter().filter(|(_, v)| !matches!(v, O::SlotUnlessDefault(_) | O::SlotMerge(_))).map(|(k, v)| (k.to_string(), to_node(v))).collect::<Vec<_>>().into()),
        O::Slot(_) | O::SlotUnlessDefault(_) | O::SlotMerge(_) => Value::String("<delegated>".into()),
        O::Num(n) => Value::Number(n.clone()),
    })
}
