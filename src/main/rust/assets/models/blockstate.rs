//! Blockstate definitions (`blockstates/*.json`): the `BlockModelDefinition`
//! codec, variant selectors, multipart conditions, and instantiation
//! against Java's state definitions (`BlockStateModelLoader`).

use super::geometry::Quadrant;
use crate::content::tags::Location;
use crate::core::json::{self, Json};
use crate::core::{dfu, java_log};

/// A model reference with its rotation (`Variant`).
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub(crate) struct Variant {
    pub(crate) model: Location,
    pub(crate) x: Quadrant,
    pub(crate) y: Quadrant,
    pub(crate) uv_lock: bool,
}

/// `BlockStateModel.Unbaked`: one variant, or weighted variants.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Unbaked {
    Single(Variant),
    Weighted(Vec<(Variant, i32)>),
}

impl Unbaked {
    pub(crate) fn variants(&self) -> Vec<&Variant> {
        match self {
            Unbaked::Single(variant) => vec![variant],
            Unbaked::Weighted(entries) => entries.iter().map(|(variant, _)| variant).collect(),
        }
    }
}

/// `KeyValueCondition.Term`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Term {
    pub(crate) value: String,
    pub(crate) negated: bool,
}

/// `multipart.Condition`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Condition {
    And(Vec<Condition>),
    Or(Vec<Condition>),
    /// Property tests in file order, each a `|`-separated list of terms.
    KeyValue(Vec<(String, Vec<Term>)>),
}

/// `BlockModelDefinition`: variants in file order, and multipart selectors.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Definition {
    pub(crate) variants: Option<Vec<(String, Unbaked)>>,
    pub(crate) multipart: Option<Vec<(Option<Condition>, Unbaked)>>,
}

/// `Variant.MAP_CODEC` over an object.
fn variant(object: &crate::core::json::Object) -> Result<Variant, String> {
    let quadrant = |value: &Json| {
        let degrees = dfu::int(value)?;
        Quadrant::from_degrees(degrees).ok_or_else(|| format!("Invalid rotation {degrees} found, only 0/90/180/270 allowed"))
    };
    Ok(Variant {
        model: dfu::required(object, "model", dfu::location)?,
        x: dfu::optional(object, "x", quadrant)?.unwrap_or_default(),
        y: dfu::optional(object, "y", quadrant)?.unwrap_or_default(),
        uv_lock: dfu::optional(object, "uvlock", dfu::boolean)?.unwrap_or(false),
    })
}

/// `BlockStateModel.Unbaked.CODEC`: weighted list first, then one variant.
fn unbaked(value: &Json) -> Result<Unbaked, String> {
    let weighted = dfu::list(value, |entry| {
        let object = dfu::object(entry)?;
        Ok((variant(object)?, dfu::optional(object, "weight", dfu::positive)?.unwrap_or(1)))
    })
    .and_then(|entries| if entries.is_empty() { Err("List must have contents".to_owned()) } else { Ok(entries) });
    match weighted {
        Ok(entries) => Ok(Unbaked::Weighted(entries)),
        Err(first) => match dfu::object(value).and_then(variant) {
            Ok(single) => Ok(Unbaked::Single(single)),
            Err(second) => Err(format!("Failed to parse either. First: {first}; Second: {second}")),
        },
    }
}

/// `KeyValueCondition.Terms.CODEC`: a string, or (legacy) an int or boolean.
fn terms(value: &Json) -> Result<Vec<Term>, String> {
    let text = match value {
        Json::String(text) => text.clone(),
        Json::Number(text) => json::int_value(text).to_string(),
        Json::Bool(value) => value.to_string(),
        other => return Err(format!("Not a string: {other}")),
    };
    let terms: Vec<Term> = text
        .split('|')
        .map(|term| match term.strip_prefix('!') {
            Some(value) => Term { value: value.to_owned(), negated: true },
            None => Term { value: term.to_owned(), negated: false },
        })
        .collect();
    if terms.iter().any(|term| term.value.is_empty()) {
        return Err(format!("Empty term in value '{text}'"));
    }
    Ok(terms)
}

/// `Condition.CODEC`: an `AND`/`OR` combiner, else property tests.
fn condition(value: &Json) -> Result<Condition, String> {
    let object = dfu::object(value)?;
    let combined = (|| {
        if object.len() != 1 {
            // Keys are checked before the size.
            for (key, _) in object.iter() {
                if key != "AND" && key != "OR" {
                    return Err(format!("Unknown element name:{key}"));
                }
            }
            return Err("Invalid map size for combiner condition, expected exactly one element".to_owned());
        }
        let (key, terms) = object.iter().next().unwrap();
        let terms = dfu::list(terms, condition)?;
        match key {
            "AND" => Ok(Condition::And(terms)),
            "OR" => Ok(Condition::Or(terms)),
            other => Err(format!("Unknown element name:{other}")),
        }
    })();
    match combined {
        Ok(combined) => Ok(combined),
        Err(first) => {
            let tests = object.iter().map(|(key, value)| Ok((key.to_owned(), terms(value)?))).collect::<Result<Vec<_>, String>>();
            match tests {
                Ok(tests) if !tests.is_empty() => Ok(Condition::KeyValue(tests)),
                Ok(_) => Err(format!("Failed to parse either. First: {first}; Second: Map must have contents")),
                Err(second) => Err(format!("Failed to parse either. First: {first}; Second: {second}")),
            }
        }
    }
}

/// `BlockModelDefinition.CODEC` over a file read by `StrictJsonParser`.
pub(crate) fn decode(bytes: &[u8]) -> Result<Definition, String> {
    let document = json::parse_document(bytes).map_err(|error| error.to_string())?;
    let object = dfu::object(&document)?;
    let variants = dfu::optional(object, "variants", |variants| {
        let entries = dfu::object(variants)?.iter().map(|(key, value)| Ok((key.to_owned(), unbaked(value)?))).collect::<Result<Vec<_>, String>>()?;
        if entries.is_empty() {
            return Err("Map must have contents".to_owned());
        }
        Ok(entries)
    })?;
    let multipart = dfu::optional(object, "multipart", |multipart| {
        let selectors = dfu::list(multipart, |selector| {
            let selector = dfu::object(selector)?;
            Ok((dfu::optional(selector, "when", condition)?, dfu::required(selector, "apply", unbaked)?))
        })?;
        if selectors.is_empty() {
            return Err("List must have contents".to_owned());
        }
        Ok(selectors)
    })?;
    if variants.is_none() && multipart.is_none() {
        return Err("Neither 'variants' nor 'multipart' found".into());
    }
    Ok(Definition { variants, multipart })
}

// -- state definitions ---------------------------------------------------

/// `Property` kinds, which decide `Property.getValue(String)`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Kind {
    Boolean,
    Integer,
    Enum,
}

/// A state property as Java describes it.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Property {
    pub(crate) name: String,
    pub(crate) kind: Kind,
    /// Value names in `getPossibleValues()` order.
    pub(crate) values: Vec<String>,
    /// `getPossibleValues().toString()`, for messages.
    pub(crate) values_text: String,
    /// `Property.toString()`, for messages.
    pub(crate) text: String,
}

impl Property {
    /// `Property.getValue(name)`: the value's index.
    pub(crate) fn value(&self, name: &str) -> Option<usize> {
        match self.kind {
            Kind::Boolean | Kind::Enum => self.values.iter().position(|value| value == name),
            Kind::Integer => {
                let parsed = java_parse_int(name)?;
                self.values.iter().position(|value| value.parse::<i32>().ok() == Some(parsed))
            }
        }
    }
}

/// `Integer.parseInt`: an optional sign, then decimal digits.
fn java_parse_int(text: &str) -> Option<i32> {
    let digits = text.strip_prefix(['+', '-']).unwrap_or(text);
    if digits.is_empty() || !digits.bytes().all(|byte| byte.is_ascii_digit()) {
        return None;
    }
    text.parse::<i32>().ok()
}

/// A `StateDefinition`: its owner's `toString`, properties, and each
/// state's value indices in `getPossibleStates()` order.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct States {
    pub(crate) owner: String,
    pub(crate) properties: Vec<Property>,
    pub(crate) states: Vec<Vec<u16>>,
}

impl States {
    fn property(&self, name: &str) -> Option<usize> {
        self.properties.iter().position(|property| property.name == name)
    }

    /// `BlockState.toString()`.
    pub(crate) fn describe(&self, state: usize) -> String {
        if self.properties.is_empty() {
            return self.owner.clone();
        }
        let values: Vec<String> = self
            .properties
            .iter()
            .zip(&self.states[state])
            .map(|(property, &value)| format!("{}={}", property.name, property.values[value as usize]))
            .collect();
        format!("{}[{}]", self.owner, values.join(","))
    }
}

/// `VariantSelector.predicate`: the property values a variant key fixes.
fn selector(states: &States, key: &str) -> Result<Vec<(usize, usize)>, String> {
    let mut fixed: Vec<(usize, usize)> = Vec::new();
    for part in key.split(',') {
        let (name, value) = match part.split_once('=') {
            Some((name, value)) => (name, Some(value)),
            None => (part, None),
        };
        match (states.property(name), value) {
            (Some(property), Some(value)) => {
                let Some(index) = states.properties[property].value(value) else {
                    return Err(format!("Unknown value: '{value}' for blockstate property: '{name}' {}", states.properties[property].values_text));
                };
                match fixed.iter_mut().find(|(known, _)| *known == property) {
                    Some(entry) => entry.1 = index,
                    None => fixed.push((property, index)),
                }
            }
            _ if !name.is_empty() => return Err(format!("Unknown blockstate property: '{name}'")),
            _ => {}
        }
    }
    Ok(fixed)
}

/// A condition evaluated for every state (`Condition.instantiate`).
fn evaluate(states: &States, condition: &Condition) -> Result<Vec<bool>, String> {
    let count = states.states.len();
    Ok(match condition {
        Condition::And(terms) => {
            let mut all = vec![true; count];
            for term in terms {
                for (slot, value) in all.iter_mut().zip(evaluate(states, term)?) {
                    *slot &= value;
                }
            }
            all
        }
        Condition::Or(terms) => {
            let mut any = vec![false; count];
            for term in terms {
                for (slot, value) in any.iter_mut().zip(evaluate(states, term)?) {
                    *slot |= value;
                }
            }
            any
        }
        Condition::KeyValue(tests) => {
            let mut all = vec![true; count];
            for (name, terms) in tests {
                let Some(property) = states.property(name) else {
                    return Err(format!("Unknown property '{name}' on '{}'", states.owner));
                };
                let matching = terms_values(states, property, terms)?;
                for (slot, state) in all.iter_mut().zip(&states.states) {
                    *slot &= matching[state[property] as usize];
                }
            }
            all
        }
    })
}

/// `Terms.instantiate`: which of a property's values the terms accept,
/// warning when that is all or none of them.
fn terms_values(states: &States, property: usize, terms: &[Term]) -> Result<Vec<bool>, String> {
    let property = &states.properties[property];
    let joined: Vec<String> = terms.iter().map(|term| if term.negated { format!("!{}", term.value) } else { term.value.clone() }).collect();
    let joined = joined.join("|");
    let mut accepted = vec![false; property.values.len()];
    for term in terms {
        let Some(value) = property.value(&term.value) else {
            return Err(format!("Unknown value '{}' for property '{}' on '{}' in '{joined}'", term.value, property.text, states.owner));
        };
        for (index, slot) in accepted.iter_mut().enumerate() {
            *slot |= (index == value) != term.negated;
        }
    }
    let count = accepted.iter().filter(|&&accepted| accepted).count();
    if count == 0 {
        java_log::warn(&format!("Condition {joined} for property {} on {} is always false", property.name, states.owner));
    } else if count == accepted.len() {
        java_log::warn(&format!("Condition {joined} for property {} on {} is always true", property.name, states.owner));
    }
    Ok(accepted)
}

/// A root model for states (`BlockStateModel.UnbakedRoot`).
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Root {
    /// `SimpleCachedUnbakedRoot`: one per variant key.
    Simple(Unbaked),
    /// `MultiPartModel.Unbaked`: one per definition; each state's matching
    /// selectors are given by [`Instantiated::selectors`].
    Multipart(Vec<Unbaked>),
}

/// One definition stack instantiated for its states.
#[derive(Debug, Default)]
pub(crate) struct Instantiated {
    pub(crate) roots: Vec<Root>,
    /// Each state's root, if any.
    pub(crate) state_roots: Vec<Option<u32>>,
    /// Per multipart root, each state's matching selector indices.
    pub(crate) selectors: Vec<(u32, Vec<Vec<u32>>)>,
}

/// `BlockModelDefinition.instantiate` for each pack's definition, lowest
/// first (`putAll`): variants in file order (a key whose selector fails,
/// or that overlaps an earlier key, is warned about and stops; states it
/// already set keep its root), then multipart roots for states still unset.
/// A multipart condition that fails rejects the whole stack.
pub(crate) fn instantiate(states: &States, file: &Location, definitions: &[(String, Definition)]) -> Result<Instantiated, String> {
    let mut result = Instantiated { state_roots: vec![None; states.states.len()], ..Default::default() };
    for (pack, definition) in definitions {
        let mut map: Vec<Option<u32>> = vec![None; states.states.len()];
        if let Some(variants) = &definition.variants {
            for (key, unbaked) in variants {
                let root = result.roots.len() as u32;
                let outcome = selector(states, key).and_then(|fixed| {
                    result.roots.push(Root::Simple(unbaked.clone()));
                    for (index, values) in states.states.iter().enumerate() {
                        if fixed.iter().all(|&(property, value)| values[property] as usize == value) {
                            if map[index].replace(root).is_some() {
                                return Err(format!("Overlapping definition on state: {}", states.describe(index)));
                            }
                        }
                    }
                    Ok(())
                });
                if let Err(message) = outcome {
                    java_log::warn(&format!("Exception loading blockstate definition: '{file}/{pack}' for variant: '{key}': {message}"));
                }
            }
        }
        if let Some(selectors) = &definition.multipart {
            let mut matching: Vec<Vec<bool>> = Vec::with_capacity(selectors.len());
            for (condition, _) in selectors {
                matching.push(match condition {
                    Some(condition) => evaluate(states, condition)?,
                    None => vec![true; states.states.len()],
                });
            }
            let root = result.roots.len() as u32;
            result.roots.push(Root::Multipart(selectors.iter().map(|(_, unbaked)| unbaked.clone()).collect()));
            let per_state = (0..states.states.len())
                .map(|state| (0..selectors.len() as u32).filter(|&selector| matching[selector as usize][state]).collect())
                .collect();
            result.selectors.push((root, per_state));
            for slot in map.iter_mut() {
                if slot.is_none() {
                    *slot = Some(root);
                }
            }
        }
        for (target, root) in result.state_roots.iter_mut().zip(map) {
            if root.is_some() {
                *target = root;
            }
        }
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn states() -> States {
        let property = |name: &str, kind: Kind, values: &[&str]| Property {
            name: name.into(),
            kind,
            values: values.iter().map(|value| value.to_string()).collect(),
            values_text: format!("[{}]", values.join(", ")),
            text: name.into(),
        };
        let mut states = Vec::new();
        for facing in 0..2u16 {
            for power in 0..3u16 {
                states.push(vec![facing, power]);
            }
        }
        States {
            owner: "Block{minecraft:test}".into(),
            properties: vec![property("facing", Kind::Enum, &["north", "south"]), property("power", Kind::Integer, &["0", "1", "2"])],
            states,
        }
    }

    #[test]
    fn decodes_and_instantiates() {
        let definition = decode(
            br#"{"variants": {"facing=north": {"model": "block/a", "y": -90}, "facing=south,power=+1": [{"model": "block/b", "weight": 3}, {"model": "block/c", "uvlock": true}], "power=7": {"model": "x"}},
                "multipart": [{"when": {"OR": [{"power": "0|2"}, {"facing": "!north"}]}, "apply": {"model": "block/m"}}, {"apply": {"model": "block/n"}}]}"#,
        )
        .unwrap();
        let file = Location::new("minecraft", "test");
        let instantiated = instantiate(&states(), &file, &[("vanilla".into(), definition)]).unwrap();
        // facing=north (3 states) -> root 0, facing=south,power=1 -> root 1; power=7 fails before
        // making a root; the rest are multipart (root 2).
        assert_eq!(vec![Some(0), Some(0), Some(0), Some(2), Some(1), Some(2)], instantiated.state_roots);
        let Root::Simple(Unbaked::Single(north)) = &instantiated.roots[0] else { panic!() };
        assert_eq!(Quadrant(3), north.y);
        let (root, selectors) = &instantiated.selectors[0];
        assert_eq!(2, *root);
        assert_eq!(vec![0, 1], selectors[3], "facing=south,power=0 matches both");
        assert_eq!(vec![1], selectors[1], "facing=north,power=1 matches only the unconditional selector");
        assert!(decode(br#"{"variants": {}}"#).is_err());
        assert!(decode(br#"{}"#).is_err());
        assert!(decode(br#"{"variants": {"": {"model": "a"}}} x"#).is_err());
        assert!(decode(br#"{"multipart": [{"when": {"power": "1||2"}, "apply": {"model": "a"}}]}"#).is_err());
        let bad = decode(br#"{"multipart": [{"when": {"missing": "1"}, "apply": {"model": "a"}}]}"#).unwrap();
        assert!(instantiate(&states(), &file, &[("vanilla".into(), bad)]).is_err());
        assert_eq!("Block{minecraft:test}[facing=south,power=2]", states().describe(5));
    }
}
