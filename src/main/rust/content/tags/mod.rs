//! Tag files and their resolution (`TagLoader`, `TagFile`, `TagEntry`,
//! `DependencySorter`).
//!
//! Loading parses each tag file stack, lowest pack first: strict JSON, then
//! the `TagFile` codec (`values`, optional `replace`). A file whose JSON
//! parses creates its tag even if its contents then fail to decode, as Java's
//! `computeIfAbsent` does.
//!
//! Resolution follows `DependencySorter.orderByDependencies` exactly,
//! including its iteration over Java hash maps and sets: that order decides
//! which edge of a dependency cycle is dropped and so which tags fail.
//! Element lookups belong to the registry (Java holders), and the registry's
//! lookups can create holders, so [`plan`] lists every lookup in Java's call
//! order and [`resolve`] takes their results.

pub mod ffi;

use std::collections::{HashMap, HashSet};
use std::fmt;
use std::sync::Arc;

use serde_json::Value;

use crate::core::id;
use crate::core::java_hash::{iteration_order, location_hash, table_for_capacity, Insertion};
use crate::core::java_log;

/// A resource location (`namespace:path`).
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub(crate) struct Location {
    pub(crate) namespace: Arc<str>,
    pub(crate) path: Arc<str>,
}

impl Location {
    pub(crate) fn new(namespace: &str, path: &str) -> Location {
        Location { namespace: Arc::from(namespace), path: Arc::from(path) }
    }

    fn java_hash(&self) -> i32 {
        location_hash(&self.namespace, &self.path)
    }

    /// `ResourceLocation.parse` (via `bySeparator(':')`), as `ResourceLocation.read`.
    pub(crate) fn parse(text: &str) -> Result<Location, String> {
        let (namespace, path) = match text.find(':') {
            Some(0) => ("minecraft", &text[1..]),
            Some(at) => (&text[..at], &text[at + 1..]),
            None => ("minecraft", text),
        };
        if !id::is_valid_namespace(namespace) {
            return Err(format!(
                "Not a valid resource location: {text} Non [a-z0-9_.-] character in namespace of location: {namespace}:{path}"
            ));
        }
        if !id::is_valid_path(path) {
            return Err(format!(
                "Not a valid resource location: {text} Non [a-z0-9/._-] character in path of location: {namespace}:{path}"
            ));
        }
        Ok(Location::new(namespace, path))
    }
}

impl fmt::Display for Location {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}:{}", self.namespace, self.path)
    }
}

/// `TagEntry`: an element or `#tag`, required unless marked optional.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Entry {
    pub(crate) id: Location,
    pub(crate) tag: bool,
    pub(crate) required: bool,
}

impl fmt::Display for Entry {
    /// `TagEntry.toString`.
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        if self.tag {
            formatter.write_str("#")?;
        }
        write!(formatter, "{}", self.id)?;
        if !self.required {
            formatter.write_str("?")?;
        }
        Ok(())
    }
}

/// `TagLoader.EntryWithSource`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Sourced {
    pub(crate) entry: Entry,
    pub(crate) source: Arc<str>,
}

impl fmt::Display for Sourced {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{} (from {})", self.entry, self.source)
    }
}

/// One file of a tag's stack.
pub(crate) struct TagResource<'a> {
    /// The file's location (`ns:tags/.../x.json`), for messages.
    pub(crate) file: &'a str,
    pub(crate) id: Location,
    pub(crate) pack: &'a str,
    pub(crate) bytes: &'a [u8],
}

/// `TagLoader.load`'s map: tags in Java `HashMap` iteration order.
#[derive(Debug, Default)]
pub(crate) struct Loaded {
    pub(crate) order: Vec<Location>,
    pub(crate) tags: HashMap<Location, Vec<Sourced>>,
}

/// `StrictJsonParser.parse` of an `InputStreamReader`'s text: malformed
/// UTF-8 decodes to replacement characters, an empty document is `null`,
/// and trailing content is an error.
fn parse_strict(bytes: &[u8]) -> Result<Value, String> {
    let text = String::from_utf8_lossy(bytes);
    if text.trim_matches([' ', '\t', '\n', '\r']).is_empty() {
        return Ok(Value::Null);
    }
    serde_json::from_str(&text).map_err(|error| {
        if error.is_syntax() && error.to_string().starts_with("trailing characters") {
            "Did not consume the entire document.".to_string()
        } else {
            error.to_string()
        }
    })
}

/// `ExtraCodecs.TAG_OR_ELEMENT_ID`.
fn tag_or_element(value: &Value) -> Result<(Location, bool), String> {
    let Value::String(text) = value else {
        return Err(format!("Not a string: {value}"));
    };
    match text.strip_prefix('#') {
        Some(tag) => Ok((Location::parse(tag)?, true)),
        None => Ok((Location::parse(text)?, false)),
    }
}

/// `TagEntry.CODEC`: an id string, or `{id, required = true}`.
fn decode_entry(value: &Value) -> Result<Entry, String> {
    let first = match tag_or_element(value) {
        Ok((id, tag)) => return Ok(Entry { id, tag, required: true }),
        Err(error) => error,
    };
    let second = (|| {
        let Value::Object(object) = value else {
            return Err(format!("Not a JSON object: {value}"));
        };
        let (id, tag) = tag_or_element(object.get("id").ok_or_else(|| "No key id in MapLike".to_string())?)?;
        let required = match object.get("required") {
            None => true,
            Some(Value::Bool(required)) => *required,
            Some(other) => return Err(format!("Not a boolean: {other}")),
        };
        Ok(Entry { id, tag, required })
    })();
    second.map_err(|second| format!("Failed to parse either. First: {first}; Second: {second}"))
}

/// `TagFile.CODEC`: `{values: [entry...], replace = false}`.
fn decode_file(value: &Value) -> Result<(Vec<Entry>, bool), String> {
    let Value::Object(object) = value else {
        return Err(format!("Not a JSON object: {value}"));
    };
    let values = match object.get("values") {
        Some(Value::Array(values)) => values,
        Some(other) => return Err(format!("Not a json array: {other}")),
        None => return Err("No key values in MapLike".into()),
    };
    let replace = match object.get("replace") {
        None => false,
        Some(Value::Bool(replace)) => *replace,
        Some(other) => return Err(format!("Not a boolean: {other}")),
    };
    let mut entries = Vec::with_capacity(values.len());
    let mut errors = Vec::new();
    for value in values {
        match decode_entry(value) {
            Ok(entry) => entries.push(entry),
            Err(error) => errors.push(error),
        }
    }
    if errors.is_empty() {
        Ok((entries, replace))
    } else {
        Err(errors.join("; "))
    }
}

/// `TagLoader.load` over the stacks in file location order.
pub(crate) fn load<'a>(resources: impl IntoIterator<Item = TagResource<'a>>) -> Loaded {
    let mut inserted: Vec<Location> = Vec::new();
    let mut tags: HashMap<Location, Vec<Sourced>> = HashMap::new();
    for resource in resources {
        let report = |error: &str| {
            java_log::error(&format!(
                "Couldn't read tag list {} from {} in data pack {}\n{error}",
                resource.id, resource.file, resource.pack
            ));
        };
        let value = match parse_strict(resource.bytes) {
            Ok(value) => value,
            Err(error) => {
                report(&format!("com.google.gson.JsonSyntaxException: {error}"));
                continue;
            }
        };
        let list = tags.entry(resource.id.clone()).or_insert_with(|| {
            inserted.push(resource.id.clone());
            Vec::new()
        });
        match decode_file(&value) {
            Ok((entries, replace)) => {
                if replace {
                    list.clear();
                }
                let source: Arc<str> = Arc::from(resource.pack);
                list.extend(entries.into_iter().map(|entry| Sourced { entry, source: source.clone() }));
            }
            Err(error) => report(&format!("java.lang.IllegalStateException: {error}")),
        }
    }
    // Java's map gains each tag through `computeIfAbsent`.
    let hashes: Vec<i32> = inserted.iter().map(Location::java_hash).collect();
    let order = iteration_order(&hashes, 16, Insertion::ComputeIfAbsent).into_iter().map(|index| inserted[index].clone()).collect();
    Loaded { order, tags }
}

/// A Guava `HashMultimap` value set: insertion order plus membership (its
/// iteration order is computed when read).
#[derive(Default)]
struct ValueSet {
    inserted: Vec<Location>,
    members: HashSet<Location>,
}

impl ValueSet {
    fn insert(&mut self, value: Location) {
        if self.members.insert(value.clone()) {
            self.inserted.push(value);
        }
    }

    /// `HashSet` iteration from `Sets.newHashSetWithExpectedSize(2)` (a
    /// first table of four buckets).
    fn iterate(&self) -> Vec<Location> {
        let hashes: Vec<i32> = self.inserted.iter().map(Location::java_hash).collect();
        iteration_order(&hashes, table_for_capacity(3), Insertion::Put).into_iter().map(|index| self.inserted[index].clone()).collect()
    }
}

type Multimap = HashMap<Location, ValueSet>;

/// Java's `isCyclic` recursion never ends on a self-referencing tag (a
/// `StackOverflowError`); this reports it instead of overflowing.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Overflow(pub(crate) Location);

/// `DependencySorter.isCyclic(multimap, object, object2)`. `path` holds the
/// recursion's keys: re-entering one is Java's endless recursion.
fn is_cyclic(multimap: &Multimap, object: &Location, object2: &Location, path: &mut Vec<Location>) -> Result<bool, Overflow> {
    let Some(values) = multimap.get(object2) else { return Ok(false) };
    if values.members.contains(object) {
        return Ok(true);
    }
    if path.contains(object2) {
        return Err(Overflow(object2.clone()));
    }
    path.push(object2.clone());
    for value in &values.inserted {
        if is_cyclic(multimap, object, value, path)? {
            path.pop();
            return Ok(true);
        }
    }
    path.pop();
    Ok(false)
}

fn add_dependency_if_not_cyclic(multimap: &mut Multimap, object: &Location, object2: &Location) -> Result<(), Overflow> {
    if !is_cyclic(multimap, object, object2, &mut Vec::new())? {
        multimap.entry(object.clone()).or_default().insert(object2.clone());
    }
    Ok(())
}

/// The order tags are built in and every element lookup Java makes.
pub(crate) struct Plan {
    /// Processed tags, in `orderByDependencies` order.
    pub(crate) order: Vec<Location>,
    /// Element lookups (`ElementLookup.get(id, required)`) in call order.
    pub(crate) lookups: Vec<(Location, bool)>,
}

/// `DependencySorter.orderByDependencies` and `tryBuildTag`'s lookups.
pub(crate) fn plan(loaded: &Loaded) -> Result<Plan, Overflow> {
    let mut multimap: Multimap = HashMap::new();
    for required in [true, false] {
        for key in &loaded.order {
            for sourced in &loaded.tags[key] {
                let entry = &sourced.entry;
                if entry.tag && entry.required == required {
                    add_dependency_if_not_cyclic(&mut multimap, key, &entry.id)?;
                }
            }
        }
    }
    let mut visited: HashSet<Location> = HashSet::new();
    let mut order = Vec::new();
    fn visit(multimap: &Multimap, loaded: &Loaded, visited: &mut HashSet<Location>, order: &mut Vec<Location>, key: &Location) {
        if !visited.insert(key.clone()) {
            return;
        }
        if let Some(values) = multimap.get(key) {
            for dependency in values.iterate() {
                visit(multimap, loaded, visited, order, &dependency);
            }
        }
        if loaded.tags.contains_key(key) {
            order.push(key.clone());
        }
    }
    for key in &loaded.order {
        visit(&multimap, loaded, &mut visited, &mut order, key);
    }
    let lookups = order
        .iter()
        .flat_map(|key| loaded.tags[key].iter())
        .filter(|sourced| !sourced.entry.tag)
        .map(|sourced| (sourced.entry.id.clone(), sourced.entry.required))
        .collect();
    Ok(Plan { order, lookups })
}

/// One built tag: the lookups whose values it holds (first occurrence of
/// each element; Java's set keeps the first of equal holders), or the
/// entries that could not resolve.
pub(crate) type Built = Result<Vec<usize>, Vec<String>>;

/// `TagLoader.build` with the plan's lookup results (`found[i]`: lookup `i`
/// returned a value).
pub(crate) fn resolve(loaded: &Loaded, plan: &Plan, found: &[bool]) -> Vec<(Location, Built)> {
    let mut built: HashMap<Location, Vec<usize>> = HashMap::new();
    let mut results = Vec::with_capacity(plan.order.len());
    let mut lookup = 0usize;
    for key in &plan.order {
        let mut values = Vec::new();
        let mut seen: HashSet<&Location> = HashSet::new();
        let mut missing = Vec::new();
        for sourced in &loaded.tags[key] {
            let entry = &sourced.entry;
            if entry.tag {
                match built.get(&entry.id) {
                    Some(members) => {
                        for &member in members {
                            if seen.insert(&plan.lookups[member].0) {
                                values.push(member);
                            }
                        }
                    }
                    None if entry.required => missing.push(sourced.to_string()),
                    None => {}
                }
            } else {
                let index = lookup;
                lookup += 1;
                if found.get(index).copied().unwrap_or(false) {
                    if seen.insert(&plan.lookups[index].0) {
                        values.push(index);
                    }
                } else if entry.required {
                    missing.push(sourced.to_string());
                }
            }
        }
        if missing.is_empty() {
            built.insert(key.clone(), values.clone());
            results.push((key.clone(), Ok(values)));
        } else {
            results.push((key.clone(), Err(missing)));
        }
    }
    results
}

#[cfg(test)]
mod tests;
