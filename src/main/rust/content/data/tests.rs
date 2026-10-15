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
        O::Str(s) | O::SoundRef(s) => Value::String(s.to_string()),
        O::List(items) => Value::List(items.iter().map(node).collect::<Vec<_>>().into()),
        O::Map(entries) => Value::Map(entries.iter().map(|(k, v)| (k.to_string(), node(v))).collect::<Vec<_>>().into()),
        O::IntList(v) => Value::List(v.iter().map(|i| Node::new(Value::Number(Num::Int(*i)))).collect::<Vec<_>>().into()),
        O::Num(n) => Value::Number(n.clone()),
        O::Slot(_) | O::SlotUnlessDefault(_) | O::SlotMerge(_) => Value::String("<slot>".into()),
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
    // `promotePartial` logs once: the entries' errors, last first.
    assert_eq!(def.logs.len(), 1, "{:?}", def.logs);
    assert!(def.logs[0].starts_with("Spawn data: Not a JSON object: null; "), "{}", def.logs[0]);
    assert!(def.logs[0].ends_with("Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_type]: minecraft:nope"), "{}", def.logs[0]);
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
    assert_eq!(entries.iter().map(|(k, _)| k.as_ref()).collect::<Vec<&str>>(), ["exhaustion", "message_id", "scaling"]);
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

fn value(kind: usize, text: &str, mode: Mode, bounds: Option<values::Bounds>) -> schema::DR {
    let node: Node = json::parse_into(text.as_bytes()).unwrap();
    values::decode(kind, &node, mode, bounds).0
}

#[test]
fn java_number_strings() {
    use super::value::{java_double, java_float};
    for (v, s) in [(0.0, "0.0"), (-0.0, "-0.0"), (1.0, "1.0"), (0.5, "0.5"), (100.0, "100.0"), (1e7, "1.0E7"), (1.5e10, "1.5E10"), (0.001, "0.001"),
        (0.0001, "1.0E-4"), (1234567.0, "1234567.0"), (f32::NAN, "NaN"), (f32::NEG_INFINITY, "-Infinity")] {
        assert_eq!(java_float(v), s);
    }
    assert_eq!(java_float(f32::from_bits(1)), "1.4E-45");
    assert_eq!(java_float(0.1), "0.1");
    assert_eq!(java_double(1e-9), "1.0E-9");
    assert_eq!(java_double(0.1 + 0.2), "0.30000000000000004");
}

#[test]
fn int_providers_decode_as_java() {
    // A typed constant is the plain number; validation messages are Java's.
    assert_eq!(value(0, r#"{"type":"constant","value":4}"#, Mode::Json, None).unwrap(), D::Variant(0, vec![D::Int(4)]));
    assert_eq!(value(0, "1.7", Mode::Json, None).unwrap(), D::Variant(0, vec![D::Int(1)]));
    let err = value(0, r#"{"type":"uniform","min_inclusive":4,"max_inclusive":1}"#, Mode::Json, None).unwrap_err();
    assert_eq!(err.msg, "Failed to parse either. First: Not a number: {\"type\":\"uniform\",\"min_inclusive\":4,\"max_inclusive\":1}; Second: Max must be at least min, min_inclusive: 4, max_inclusive: 1");
    let err = value(0, r#"{"type":"uniform"}"#, Mode::Json, None).unwrap_err();
    assert!(err.msg.ends_with("Second: No key max_inclusive in MapLike[{\"type\":\"uniform\"}]; No key min_inclusive in MapLike[{\"type\":\"uniform\"}]"), "{}", err.msg);
    // A list keeps its decoded entries as the partial; Either returns that side.
    let err = value(0, r#"{"type":"weighted_list","distribution":[{"data":"q","weight":1},{"data":2,"weight":-4},{"data":3,"weight":2}]}"#, Mode::Json, None).unwrap_err();
    assert_eq!(err.msg, "Value must be non-negative: -4; Failed to parse either. First: Not a number: \"q\"; Second: Not a JSON object: \"q\"");
    assert!(err.partial.is_some());
    // Bounds: `codec(min, max)` checks the provider's range.
    let b = |min, max| Some(values::Bounds { min, max });
    assert_eq!(value(0, r#"{"type":"clamped","source":{"type":"uniform","min_inclusive":-4,"max_inclusive":9},"min_inclusive":0,"max_inclusive":5}"#, Mode::Json, b(0.0, 5.0)).is_ok(), true);
    assert_eq!(value(0, "7", Mode::Json, b(0.0, 4.0)).unwrap_err().msg, "Value provider too high: 4 [7-7]");
    assert_eq!(value(1, "-0.5", Mode::Json, b(0.0, 1.0)).unwrap_err().msg, "Value provider too low: 0.0 [-0.5--0.5]");
}

#[test]
fn nbt_messages_follow_nbt_ops() {
    let tree = |members: Vec<(&str, Value)>| Node::new(Value::Map(members.into_iter().map(|(k, v)| (k.to_owned(), Node::new(v))).collect::<Vec<_>>().into()));
    let input = tree(vec![("type", Value::String("uniform".into())), ("max_inclusive", Value::Number(Num::Byte(1)))]);
    let err = values::decode(0, &input, Mode::Nbt, None).0.unwrap_err();
    assert_eq!(err.msg, "Failed to parse either. First: Not a number; Second: No key min_inclusive in MapLike[{max_inclusive:1b,type:\"uniform\"}]");
}

#[test]
fn weight_overflow_throws_as_java() {
    let (_, thrown) = schema::thrown_scope(|| value(0, r#"{"type":"weighted_list","distribution":[{"data":1,"weight":2147483647},{"data":2,"weight":1}]}"#, Mode::Json, None));
    assert_eq!(thrown.map(|t| t.1).as_deref(), Some("Sum of weights must be <= 2147483647"));
}

#[test]
fn nested_calls_keep_their_own_first_exception() {
    let (inner, outer) = schema::thrown_scope(|| {
        schema::throw("outer first");
        // A callback re-entering native decoding gets a fresh record...
        let (_, inner) = schema::thrown_scope(|| {
            assert_eq!(schema::pending_thrown(), None);
            schema::throw_java(7, "inner");
            schema::throw("inner second");
        });
        // ...and the outer call's pending exception survives it.
        assert_eq!(schema::pending_thrown().as_deref(), Some("outer first"));
        schema::throw("outer second");
        inner
    });
    assert_eq!(inner, Some((schema::JAVA_THROWN, "inner".to_owned(), 7)));
    assert_eq!(outer, Some((schema::ILLEGAL_ARGUMENT, "outer first".to_owned(), 0)));
    assert_eq!(schema::pending_thrown(), None);
}

#[test]
fn a_pending_exception_skips_later_java_callbacks() {
    // No callback is installed in unit tests, so a node with a Java index would
    // be lazy; with an exception pending it fails without calling Java.
    let (result, thrown) = schema::thrown_scope(|| {
        schema::throw_as(schema::NULL_POINTER, "null element");
        let mut node = Node::new(Value::String("x".into()));
        node.index = Some(0);
        let mut cx = schema::Cx::new(Mode::Json, false);
        schema::java_delegate(schema::JAVA_CODEC, 0, "", &node, &mut cx).map(|_| ())
    });
    assert!(result.is_err());
    assert_eq!(thrown.map(|t| (t.0, t.1)), Some((schema::NULL_POINTER, "null element".to_owned())));
}

#[test]
fn level_based_values_round_trip() {
    for text in [r#"2.5"#, r#"{"type":"linear","base":2.0,"per_level_above_first":0.5}"#, r#"{"type":"lookup","values":[1.0,2.5],"fallback":{"type":"levels_squared","added":1.0}}"#] {
        let d = value(2, text, Mode::Json, None).unwrap();
        let o = values::encode(2, &d, None).unwrap();
        assert_eq!(values::decode(2, &schema::to_node(&o), Mode::Json, None).0.unwrap(), d);
    }
    assert_eq!(value(2, r#"{"type":"clamped","value":3,"min":4,"max":4}"#, Mode::Json, None).unwrap_err().msg,
        "Failed to parse either. First: Not a number: {\"type\":\"clamped\",\"value\":3,\"min\":4,\"max\":4}; Second: Max must be larger than min, min: 4.0, max: 4.0");
}

/// Timing probe (release): `cargo test --release --lib content::data::tests::value_cost -- --ignored --nocapture`.
#[test]
#[ignore]
fn value_cost() {
    let stack = r#"{"id":"minecraft:diamond_sword","count":1,"components":{"minecraft:custom_name":{"text":"Item 4","color":"gold"},"minecraft:lore":["line",{"translate":"item.minecraft.stick"}],"minecraft:damage":3}}"#;
    let node: Node = json::parse_into(stack.as_bytes()).unwrap();
    let kind = values::VALUES.iter().position(|k| k.name == "item_stack").unwrap();
    let d = values::decode(kind, &node, Mode::Json, None).0.unwrap();
    let rounds = 200_000;
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        std::hint::black_box(values::decode(kind, &node, Mode::Json, None).0.unwrap());
    }
    let decode = t.elapsed();
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        std::hint::black_box(schema::check(values::VALUES[kind].schema, &d).unwrap());
    }
    let check = t.elapsed();
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        std::hint::black_box(values::encode(kind, &d, None).unwrap());
    }
    let encode = t.elapsed();
    let ns = |x: std::time::Duration| x.as_nanos() as f64 / rounds as f64;
    println!("stack decode {:.0}ns check {:.0}ns encode(+check) {:.0}ns", ns(decode), ns(check), ns(encode));
}

#[test]
#[ignore]
fn value_cost_parts() {
    let kind = |name: &str| values::VALUES.iter().position(|k| k.name == name).unwrap();
    for (name, text) in [
        ("item_stack", r#"{"id":"minecraft:stick","count":1}"#),
        ("component", r#""Item 4""#),
        ("component", r#"{"text":"Item 4","color":"gold"}"#),
        ("component", r#"["line",{"translate":"item.minecraft.stick"}]"#),
        ("component_patch", r#"{"minecraft:damage":3}"#),
    ] {
        let k = kind(name);
        let node: Node = json::parse_into(text.as_bytes()).unwrap();
        let d = values::decode(k, &node, Mode::Json, None).0.unwrap();
        let rounds = 500_000;
        let t = std::time::Instant::now();
        for _ in 0..rounds {
            std::hint::black_box(values::decode(k, &node, Mode::Json, None).0.unwrap());
        }
        let decode = t.elapsed();
        let t = std::time::Instant::now();
        for _ in 0..rounds {
            std::hint::black_box(values::encode(k, &d, None).unwrap());
        }
        let encode = t.elapsed();
        let ns = |x: std::time::Duration| x.as_nanos() as f64 / rounds as f64;
        println!("{name} {text}: decode {:.0}ns encode {:.0}ns", ns(decode), ns(encode));
    }
}

#[test]
#[ignore]
fn ffi_floor_cost() {
    // An int provider decode of the int 5 through the FFI entry point.
    let input = [6u8, 5, 0, 0, 0];
    let mut out = vec![0u8; 1024];
    let rounds = 2_000_000;
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        unsafe { std::hint::black_box(super::ffi::mattmc_value_decode(0, 1, 0, 0.0, 0.0, input.as_ptr(), input.len() as i64, out.as_mut_ptr(), out.len() as i64)) };
    }
    println!("ffi floor {:.0}ns", t.elapsed().as_nanos() as f64 / rounds as f64);
}

#[test]
#[ignore]
fn ffi_encode_cost() {
    // `Record[id 1, count 3, components {}]` through the FFI encode entry point.
    let mut input = vec![9u8, 3, 0, 0, 0];
    input.extend([3, 1, 0, 0, 0, 3, 3, 0, 0, 0, 8, 0, 0, 0, 0]);
    let kind = values::VALUES.iter().position(|k| k.name == "item_stack").unwrap() as i32;
    let mut out = vec![0u8; 1024];
    let rounds = 2_000_000;
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        unsafe { std::hint::black_box(super::ffi::mattmc_value_encode(kind, 0, 0.0, 0.0, input.as_ptr(), input.len() as i64, out.as_mut_ptr(), out.len() as i64)) };
    }
    println!("ffi encode {:.0}ns", t.elapsed().as_nanos() as f64 / rounds as f64);
}

#[test]
#[ignore]
fn encode_cost_split() {
    let kind = values::VALUES.iter().position(|k| k.name == "item_stack").unwrap();
    let d = D::Record(vec![D::Int(1), D::Int(3), D::Map(Vec::new())]);
    let rounds = 2_000_000;
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        std::hint::black_box(schema::check(values::VALUES[kind].schema, std::hint::black_box(&d)).unwrap());
    }
    let check = t.elapsed();
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        let s = values::VALUES[kind].schema;
        std::hint::black_box(schema::encode(s, s, std::hint::black_box(&d)).unwrap());
    }
    let encode = t.elapsed();
    let t = std::time::Instant::now();
    for _ in 0..rounds {
        std::hint::black_box(D::Record(vec![D::Int(1), D::Int(3), D::Map(Vec::new())]));
    }
    let build = t.elapsed();
    let ns = |x: std::time::Duration| x.as_nanos() as f64 / rounds as f64;
    println!("split check {:.0}ns encode {:.0}ns build-d {:.0}ns size_of D {} O {}", ns(check), ns(encode), ns(build), std::mem::size_of::<D>(), std::mem::size_of::<schema::O>());
}

#[test]
fn a_missing_block_registry_ends_the_call_without_further_callbacks() {
    let installs = std::cell::Cell::new(0);
    let missing = || None::<u8>;
    let (calls, thrown) = schema::thrown_scope(|| {
        let first = schema::blocks_from(missing, || installs.set(installs.get() + 1));
        assert!(first.is_err());
        // Pending now: neither another install nor a Java callback runs.
        let second = schema::blocks_from(missing, || installs.set(installs.get() + 1));
        assert!(second.is_err());
        let mut node = Node::new(Value::String("x".into()));
        node.index = Some(0);
        let mut cx = schema::Cx::new(Mode::Json, false);
        assert!(schema::java_delegate(schema::JAVA_CODEC, 0, "", &node, &mut cx).is_err());
        // A nested call keeps its own record, and the outer one survives it.
        let (_, inner) = schema::thrown_scope(|| assert_eq!(schema::pending_thrown(), None));
        assert_eq!(inner, None);
        installs.get()
    });
    assert_eq!(calls, 1);
    assert_eq!(thrown.map(|t| t.0), Some(schema::BLOCKS_MISSING));
    // Installed: the registry is returned without calling Java.
    assert_eq!(schema::blocks_from(|| Some(7u8), || panic!("installed already")), Ok(7));
}
