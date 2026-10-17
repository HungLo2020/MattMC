use super::*;

fn resource<'a>(id: &str, pack: &'a str, json: &'a str) -> TagResource<'a> {
    let (namespace, path) = id.split_once(':').unwrap();
    TagResource { file: "file", id: Location::new(namespace, path), pack, bytes: json.as_bytes() }
}

fn tags(entries: &[(&str, &str)]) -> Loaded {
    load(entries.iter().map(|(id, json)| resource(id, "pack", json)))
}

#[test]
fn files_parse_replace_and_create_tags_on_json() {
    let loaded = load([
        resource("minecraft:a", "low", r##"{"values": ["minecraft:x", "#minecraft:b", {"id": "y", "required": false}]}"##),
        resource("minecraft:a", "high", r##"{"replace": true, "values": ["z"]}"##),
        resource("minecraft:broken", "p", r##"{"values": "x"}"##),
        resource("minecraft:notjson", "p", "{"),
        resource("minecraft:empty", "p", ""),
        resource("minecraft:trailing", "p", r#"{"values": []} x"#),
    ]);
    let a: Vec<String> = loaded.tags[&Location::new("minecraft", "a")].iter().map(ToString::to_string).collect();
    assert_eq!(vec!["minecraft:z (from high)"], a);
    // Decode failures still create the tag; JSON failures do not.
    assert!(loaded.tags[&Location::new("minecraft", "broken")].is_empty());
    assert!(loaded.tags[&Location::new("minecraft", "empty")].is_empty());
    assert!(!loaded.tags.contains_key(&Location::new("minecraft", "notjson")));
    assert!(!loaded.tags.contains_key(&Location::new("minecraft", "trailing")));
}

#[test]
fn resolution_orders_dependencies_and_reports_missing_references() {
    let loaded = tags(&[
        ("minecraft:top", r##"{"values": ["#minecraft:mid", "minecraft:t", {"id": "#minecraft:absent", "required": false}]}"##),
        ("minecraft:mid", r##"{"values": ["minecraft:m", {"id": "minecraft:gone", "required": false}, "minecraft:m"]}"##),
        ("minecraft:bad", r##"{"values": ["minecraft:missing", "#minecraft:nowhere"]}"##),
    ]);
    let planned = plan(&loaded).unwrap();
    let position = |id: &str| planned.order.iter().position(|location| location.path.as_ref() == id).unwrap();
    assert!(position("mid") < position("top"));
    let found: Vec<bool> = planned.lookups.iter().map(|(location, _)| !matches!(location.path.as_ref(), "gone" | "missing")).collect();
    let results: HashMap<String, Built> = resolve(&loaded, &planned, &found)
        .into_iter()
        .map(|(location, built)| (location.path.to_string(), built))
        .collect();
    let names = |built: &Built| built.as_ref().unwrap().iter().map(|&index| planned.lookups[index].0.path.to_string()).collect::<Vec<_>>();
    assert_eq!(vec!["m"], names(&results["mid"]));
    assert_eq!(vec!["m", "t"], names(&results["top"]));
    assert_eq!(
        &vec!["minecraft:missing (from pack)".to_string(), "#minecraft:nowhere (from pack)".to_string()],
        results["bad"].as_ref().unwrap_err()
    );
}

#[test]
fn self_reference_followed_by_a_dependent_overflows_like_java() {
    let alone = tags(&[("minecraft:selfish", r##"{"values": ["#minecraft:selfish"]}"##)]);
    let planned = plan(&alone).unwrap();
    let results = resolve(&alone, &planned, &[]);
    assert!(results[0].1.is_err(), "it references itself before it is built");
    let dependent = tags(&[
        ("minecraft:selfish", r##"{"values": ["#minecraft:selfish"]}"##),
        ("minecraft:other", r##"{"values": ["#minecraft:selfish"]}"##),
    ]);
    // Java recurses forever when, in its map order, `selfish` gains its
    // self-loop before `other`'s edge to it is checked.
    let selfish_first = dependent.order[0].path.as_ref() == "selfish";
    assert_eq!(selfish_first, plan(&dependent).is_err());
}
