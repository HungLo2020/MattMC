use super::registries::REGISTRIES;
use super::schema::{D, O};
use super::value::{Mode, Node, Num, Value};
use super::*;
use crate::content::json::{self, Json};

fn files(registry: &str) -> Vec<(String, Vec<u8>)> {
    let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../resources/data/minecraft").join(registry);
    let mut files = Vec::new();
    let mut stack = vec![dir.clone()];
    while let Some(d) = stack.pop() {
        for entry in std::fs::read_dir(d).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                stack.push(path);
            } else if path.extension().is_some_and(|e| e == "json") {
                let name = path.strip_prefix(&dir).unwrap().with_extension("").to_string_lossy().into_owned();
                files.push((name, std::fs::read(&path).unwrap()));
            }
        }
    }
    files.sort();
    files
}

/// An encoded value back as an input node, slots as null.
fn node(o: &O) -> Node {
    Node::new(match o {
        O::Bool(v) => Value::Bool(*v),
        O::Int(v) => Value::Number(Num::Int(*v)),
        O::Float(v) => Value::Number(Num::Float(*v)),
        O::Double(v) => Value::Number(Num::Double(*v)),
        O::Str(s) | O::SoundRef(s) => Value::String(s.clone()),
        O::List(items) => Value::List(items.iter().map(node).collect::<Vec<_>>().into()),
        O::Map(entries) => Value::Map(entries.iter().map(|(k, v)| (k.clone(), node(v))).collect::<Vec<_>>().into()),
        O::Slot(_) | O::SlotUnlessDefault(_) => Value::String("<slot>".into()),
    })
}

#[test]
fn every_shipped_definition_decodes_and_round_trips() {
    let mut total = 0;
    for (index, registry) in REGISTRIES.iter().enumerate() {
        let files = files(registry.name);
        assert!(!files.is_empty(), "{}", registry.name);
        for (name, bytes) in files {
            let def = decode_file(index, false, &bytes).unwrap_or_else(|e| panic!("{}/{name}: {e}", registry.name));
            assert!(def.logs.is_empty(), "{}/{name}", registry.name);
            for network in [false, true] {
                let encoded = encode(&def, network).unwrap();
                let again = decode(index, network, &node(&encoded), Mode::Json, true).unwrap();
                if !network {
                    let slots = def.slots.len();
                    assert!(define(index, def.value.clone(), slots).is_ok(), "{}/{name} define", registry.name);
                }
                if !network {
                    assert_eq!(again.value, def.value, "{}/{name}", registry.name);
                }
            }
            total += 1;
        }
    }
    assert!(total > 300, "{total}");
}

fn plains() -> Json {
    let bytes = files("worldgen/biome").into_iter().find(|(n, _)| n == "plains").unwrap().1;
    json::parse(&bytes).unwrap()
}

fn decode_edited(edit: impl FnOnce(&mut Vec<(String, Json)>)) -> R<Definition> {
    let Json::Object(mut members) = plains() else { panic!() };
    edit(&mut members);
    decode(0, false, &Node::from_json(Json::Object(members)), Mode::Json, true)
}

fn set(members: &mut Vec<(String, Json)>, key: &str, value: Json) {
    match members.iter_mut().find(|(k, _)| k == key) {
        Some(m) => m.1 = value,
        None => members.push((key.into(), value)),
    }
}

fn effects(members: &mut [(String, Json)]) -> &mut Vec<(String, Json)> {
    match &mut members.iter_mut().find(|(k, _)| k == "effects").unwrap().1 {
        Json::Object(e) => e,
        _ => panic!(),
    }
}

fn effects_field(def: &Definition, index: usize) -> D {
    let D::Record(fields) = &def.value else { panic!() };
    let D::Record(effects) = &fields[1] else { panic!() };
    effects[index].clone()
}

#[test]
fn biome_fields_follow_their_codecs() {
    assert!(decode_edited(|_| {}).is_ok());
    assert!(decode_edited(|m| m.retain(|(k, _)| k != "temperature")).is_err());
    assert!(decode_edited(|m| set(m, "temperature", Json::Null)).is_err());
    assert!(decode_edited(|m| m.retain(|(k, _)| k != "carvers")).is_err());
    assert!(decode_edited(|m| set(m, "temperature_modifier", Json::String("hot".into()))).is_err());
    assert!(decode_edited(|m| set(m, "temperature_modifier", Json::Null)).is_ok());
    assert!(decode_edited(|m| set(effects(m), "grass_color", Json::String("1".into()))).is_err());
    let volume = decode_edited(|m| set(effects(m), "music_volume", Json::String("x".into()))).unwrap();
    assert_eq!(effects_field(&volume, 13), D::Float(1.0));
    assert!(decode_edited(|m| set(m, "has_precipitation", Json::Number("1".into()))).is_err());
    assert!(decode_edited(|m| set(m, "temperature", Json::Bool(true))).is_err());
    let fog = decode_edited(|m| set(effects(m), "fog_color", Json::Number("1e10".into()))).unwrap();
    assert_eq!(effects_field(&fog, 0), D::Int(1410065408));
    assert!(decode_edited(|m| set(m, "creature_spawn_probability", Json::Number("-0.0".into()))).is_err());
    assert!(decode_edited(|m| set(m, "creature_spawn_probability", Json::Number("0.9999999".into()))).is_ok());
    assert!(decode_edited(|m| set(m, "creature_spawn_probability", Json::Number("1".into()))).is_err());
}

#[test]
fn bad_spawners_are_dropped_but_bad_categories_fail() {
    let bad = |entry: &str| json::parse(entry.as_bytes()).unwrap();
    let def = decode_edited(|m| {
        let Json::Object(spawners) = &mut m.iter_mut().find(|(k, _)| k == "spawners").unwrap().1 else { panic!() };
        let Json::Array(monsters) = &mut spawners.iter_mut().find(|(k, _)| k == "monster").unwrap().1 else { panic!() };
        monsters.push(bad(r#"{"type":"minecraft:nope","weight":1,"minCount":1,"maxCount":1}"#));
        monsters.push(bad(r#"{"type":"minecraft:zombie","weight":1,"minCount":3,"maxCount":2}"#));
        monsters.push(bad(r#"{"type":"minecraft:zombie","weight":-1,"minCount":1,"maxCount":1}"#));
        monsters.push(bad("null"));
    }).unwrap();
    assert_eq!(def.logs.len(), 4);
    assert!(def.logs.iter().all(|l| l.starts_with("Spawn data: ")));
    assert!(decode_edited(|m| {
        let Json::Object(spawners) = &mut m.iter_mut().find(|(k, _)| k == "spawners").unwrap().1 else { panic!() };
        spawners.push(("boss".into(), Json::Array(vec![])));
    }).is_err());
    assert!(decode_edited(|m| set(m, "spawn_costs", bad(r#"{"minecraft:nope":{"energy_budget":1,"charge":1}}"#))).is_err());
}

#[test]
fn defaults_are_omitted_and_dispatch_writes_type_last() {
    let def = decode(2, false, &Node::from_json(json::parse(br#"{"message_id":"x","scaling":"never","exhaustion":0.1,"effects":"hurt"}"#).unwrap()), Mode::Json, true).unwrap();
    let O::Map(entries) = encode(&def, false).unwrap() else { panic!() };
    // Five members write in DFU's regrouped order, defaults omitted.
    assert_eq!(entries.iter().map(|(k, _)| k.as_str()).collect::<Vec<_>>(), ["exhaustion", "message_id", "scaling"]);
    let provider = decode(9, false, &Node::from_json(json::parse(br##"{"type":"minecraft:by_cost_with_difficulty","enchantments":"#x","min_cost":1,"max_cost_span":2}"##).unwrap()), Mode::Json, true).unwrap();
    let O::Map(entries) = encode(&provider, false).unwrap() else { panic!() };
    assert_eq!(entries.last().unwrap().0, "type");
    // Bounds encode as a number when both ends match.
    let cat = br#"{"asset_id":"a","spawn_conditions":[{"priority":1,"condition":{"type":"minecraft:moon_brightness","range":{"min":0.5,"max":0.5}}}]}"#;
    let def = decode(10, false, &Node::from_json(json::parse(cat).unwrap()), Mode::Json, true).unwrap();
    let encoded = format!("{:?}", encode(&def, false).unwrap());
    assert!(encoded.contains("(\"range\", Double(0.5))"), "{encoded}");
}

#[test]
fn nbt_trees_read_numbers_as_booleans() {
    let one = |v: Value| Node::new(v);
    let mut cx = schema::Cx::new(Mode::Nbt, true);
    assert_eq!(schema::decode(&schema::S::Bool, &one(Value::Number(Num::Byte(1))), &mut cx).unwrap(), D::Bool(true));
    let mut cx = schema::Cx::new(Mode::Json, true);
    assert!(schema::decode(&schema::S::Bool, &one(Value::Number(Num::Byte(1))), &mut cx).is_err());
    assert_eq!(Num::Float(3e9).int_value(), i32::MAX);
    assert_eq!(Num::Text("3e9".into()).int_value(), -1294967296);
    assert_eq!(Num::Int(256).byte_value(), 0);
}

/// Timing probe (release): `cargo test --release --lib content::data::tests::decode_cost -- --ignored --nocapture`.
#[test]
#[ignore]
fn decode_cost() {
    let files = files("worldgen/biome");
    let rounds = 300;
    let start = std::time::Instant::now();
    for _ in 0..rounds {
        for (_, bytes) in &files {
            std::hint::black_box(decode_file(0, false, bytes).unwrap());
        }
    }
    let total = start.elapsed();
    let parse = std::time::Instant::now();
    for _ in 0..rounds {
        for (_, bytes) in &files {
            std::hint::black_box(crate::content::json::parse(bytes).unwrap());
        }
    }
    let parse = parse.elapsed();
    let parsed: Vec<_> = files.iter().map(|(_, b)| crate::content::json::parse(b).unwrap()).collect();
    let convert = std::time::Instant::now();
    for _ in 0..rounds {
        for json in &parsed {
            std::hint::black_box(Node::from_json(json.clone()));
        }
    }
    let convert = convert.elapsed();
    let nodes: Vec<_> = parsed.iter().map(|j| Node::from_json(j.clone())).collect();
    let schema = std::time::Instant::now();
    for _ in 0..rounds {
        for node in &nodes {
            std::hint::black_box(decode(0, false, node, Mode::Json, true).unwrap());
        }
    }
    let schema = schema.elapsed();
    let n = (rounds * files.len()) as f64;
    let us = |d: std::time::Duration| d.as_secs_f64() * 1e6 / n;
    println!("decode_file {:.1}us/file, json parse {:.1}, convert(+clone) {:.1}, schema decode {:.1}", us(total), us(parse), us(convert), us(schema));
}
