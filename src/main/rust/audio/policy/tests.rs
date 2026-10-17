use std::collections::HashSet;

use super::assets::{AssetCache, AssetLookup};
use super::engine::{PlayRequest, PlayResult, Policy, VoiceOp, CATEGORY_COUNT, CATEGORY_MUSIC};
use super::events::{RegistryBuilder, Resolution, SoundRegistry};
use crate::audio::commands::{SOUND_FLAG_LINEAR_ATTENUATION, SOUND_FLAG_LOOPING, SOUND_UPDATE_GAIN};

fn files(paths: &[&str]) -> HashSet<String> {
    paths.iter().map(|path| path.to_string()).collect()
}

fn registry(resources: &[(&str, &str)], available: &[&str]) -> SoundRegistry {
    let mut builder = RegistryBuilder::new(1, files(available));
    for (namespace, json) in resources {
        builder.add_resource(namespace, "test-pack", json);
    }
    builder.build()
}

fn sound(resolution: Resolution) -> super::events::ResolvedSound {
    match resolution {
        Resolution::Sound(sound) => sound,
        other => panic!("expected a sound, got {other:?}"),
    }
}

#[test]
fn parses_entries_with_vanilla_defaults_and_fields() {
    let registry = registry(
        &[(
            "minecraft",
            r#"{"block.test": {"subtitle": "sub.test", "sounds": [
                "block/a",
                {"name": "block/b", "volume": 0.5, "pitch": 1.25, "weight": 3,
                 "stream": true, "preload": true, "attenuation_distance": 24}
            ]}}"#,
        )],
        &["minecraft:sounds/block/a.ogg", "minecraft:sounds/block/b.ogg"],
    );
    assert!(registry.warnings.is_empty(), "{:?}", registry.warnings);
    let event = registry.event_index("minecraft:block.test").unwrap();
    assert_eq!(Some("sub.test"), registry.events[event as usize].subtitle.as_deref());
    assert_eq!(4, registry.event_weight(event));
    // Draw 0 selects the first (weight 1) entry; draws 1..3 the second.
    let first = sound(registry.resolve("minecraft:block.test", &[0]));
    assert_eq!(("minecraft:block/a", 1.0, 1.0, 1, false, 16), (
        &*first.location, first.volume, first.pitch, first.weight, first.stream, first.attenuation_distance));
    let second = sound(registry.resolve("minecraft:block.test", &[3]));
    assert_eq!(("minecraft:block/b", 0.5, 1.25, 3, true, true, 24), (
        &*second.location, second.volume, second.pitch, second.weight, second.stream, second.preload,
        second.attenuation_distance));
    assert_eq!(vec!["minecraft:sounds/block/b.ogg".to_string()], registry.preload_paths());
}

#[test]
fn resolution_asks_for_each_bounded_draw_in_order() {
    let registry = registry(
        &[(
            "minecraft",
            r#"{"inner": {"sounds": ["x", {"name": "y", "weight": 2}]},
                "outer": {"sounds": [{"name": "minecraft:inner", "type": "event", "volume": 0.5, "weight": 7}]}}"#,
        )],
        &["minecraft:sounds/x.ogg", "minecraft:sounds/y.ogg"],
    );
    // The outer total is the inner event's weight (3), not the entry's weight.
    assert_eq!(Resolution::NeedDraw(3), registry.resolve("minecraft:outer", &[]));
    assert_eq!(Resolution::NeedDraw(3), registry.resolve("minecraft:outer", &[2]));
    let nested = sound(registry.resolve("minecraft:outer", &[2, 1]));
    // Inner y, combined: volume multiplied, the outer entry's weight kept.
    assert_eq!(("minecraft:y", 0.5, 7), (&*nested.location, nested.volume, nested.weight));
    assert_eq!(Resolution::UnknownEvent, registry.resolve("minecraft:missing", &[]));
}

#[test]
fn missing_and_cyclic_nested_events_resolve_without_recursing_forever() {
    let registry = registry(
        &[(
            "minecraft",
            r#"{"outer": {"sounds": [{"name": "gone", "type": "event"}]}, "nothing": {},
                "a": {"sounds": [{"name": "b", "type": "event"}]},
                "b": {"sounds": [{"name": "a", "type": "event"}, "x"]}}"#,
        )],
        &["minecraft:sounds/x.ogg"],
    );
    // A missing target weighs zero: the event resolves to EMPTY_SOUND.
    assert_eq!(Resolution::Empty, registry.resolve("minecraft:outer", &[]));
    assert_eq!(Resolution::Empty, registry.resolve("minecraft:nothing", &[]));
    // Java recurses without bound on a cycle; the depth guard ends it.
    let mut draws = Vec::new();
    loop {
        match registry.resolve("minecraft:a", &draws) {
            Resolution::NeedDraw(bound) => {
                assert!(draws.len() < 64);
                draws.push(bound - 1);
            }
            _ => break,
        }
    }
}

#[test]
fn replace_resets_and_append_extends_across_packs() {
    let base = r#"{"e": {"subtitle": "old", "sounds": ["a"]}}"#;
    let append = r#"{"e": {"sounds": ["b"]}}"#;
    let replace = r#"{"e": {"replace": true, "subtitle": "new", "sounds": ["c"]}}"#;
    let available = ["minecraft:sounds/a.ogg", "minecraft:sounds/b.ogg", "minecraft:sounds/c.ogg"];
    let appended = registry(&[("minecraft", base), ("minecraft", append)], &available);
    let event = appended.event_index("minecraft:e").unwrap();
    assert_eq!(2, appended.event_weight(event));
    assert_eq!(Some("old"), appended.events[event as usize].subtitle.as_deref());
    let replaced = registry(&[("minecraft", base), ("minecraft", append), ("minecraft", replace)], &available);
    let event = replaced.event_index("minecraft:e").unwrap();
    assert_eq!(1, replaced.event_weight(event));
    assert_eq!(Some("new"), replaced.events[event as usize].subtitle.as_deref());
    assert_eq!("minecraft:c", &*sound(replaced.resolve("minecraft:e", &[0])).location);
}

#[test]
fn missing_files_are_skipped_and_invalid_resources_rejected_whole() {
    let registry = registry(
        &[
            ("minecraft", r#"{"e": {"sounds": ["present", "absent"]}}"#),
            ("minecraft", r#"{"bad": {"sounds": [{"name": "x", "volume": 0}]}}"#),
            ("minecraft", r#"{"bad2": {"sounds": [{"name": "x", "type": "nope"}]}}"#),
            ("minecraft", "not json"),
        ],
        &["minecraft:sounds/present.ogg", "minecraft:sounds/x.ogg"],
    );
    let event = registry.event_index("minecraft:e").unwrap();
    assert_eq!(1, registry.event_weight(event));
    assert!(registry.event_index("minecraft:bad").is_none());
    assert!(registry.event_index("minecraft:bad2").is_none());
    assert!(registry.warnings.iter().any(|w| w.contains("minecraft:sounds/absent.ogg does not exist")));
    assert_eq!(3, registry.warnings.iter().filter(|w| w.starts_with("Invalid sounds.json")).count());
}

#[test]
fn gson_coercions_match_the_vanilla_deserializer() {
    let registry = registry(
        &[(
            "minecraft",
            r#"{"e": {"replace": "TRUE", "subtitle": 7, "sounds": [
                {"name": "a", "weight": 2.9, "stream": "true", "preload": 1}]}}"#,
        )],
        &["minecraft:sounds/a.ogg"],
    );
    let event = registry.event_index("minecraft:e").unwrap();
    assert_eq!(Some("7"), registry.events[event as usize].subtitle.as_deref());
    let resolved = sound(registry.resolve("minecraft:e", &[0]));
    // Number.intValue() truncates; JsonPrimitive.getAsBoolean parses text.
    assert_eq!((2, true, false), (resolved.weight, resolved.stream, resolved.preload));
}

/// The bundled vanilla + integrated `sounds.json`: every event resolves for
/// every first draw and no file entry is lost.
#[test]
fn bundled_sounds_json_resolves_every_event() {
    let json = std::fs::read_to_string(concat!(env!("CARGO_MANIFEST_DIR"), "/../resources/assets/minecraft/sounds.json"))
        .expect("bundled sounds.json");
    let value: serde_json::Value = serde_json::from_str(&json).unwrap();
    // Treat every referenced file as present so the check covers structure.
    let mut available = HashSet::new();
    for entry in value.as_object().unwrap().values() {
        for sound in entry.get("sounds").and_then(|s| s.as_array()).into_iter().flatten() {
            let (name, kind) = match sound {
                serde_json::Value::String(name) => (name.as_str(), "file"),
                other => (other["name"].as_str().unwrap(), other.get("type").and_then(|t| t.as_str()).unwrap_or("file")),
            };
            if kind == "file" {
                available.insert(super::events::SoundSpec::file_path(&name.replace("minecraft:", "")));
            }
        }
    }
    let mut builder = RegistryBuilder::new(1, available);
    builder.add_resource("minecraft", "vanilla", &json);
    let registry = builder.build();
    assert!(registry.warnings.is_empty(), "{:?}", &registry.warnings[..registry.warnings.len().min(5)]);
    assert!(registry.names.len() > 1500, "{} events", registry.names.len());
    for name in &registry.names {
        let event = registry.event_index(name).unwrap();
        let total = registry.event_weight(event);
        let mut draws = Vec::new();
        loop {
            match registry.resolve(name, &draws) {
                Resolution::NeedDraw(bound) => {
                    assert!(bound > 0 && draws.len() < 8, "{name}");
                    draws.push(bound - 1);
                }
                Resolution::Empty => {
                    assert_eq!(0, total, "{name} resolved empty with weight {total}");
                    break;
                }
                Resolution::Sound(_) => break,
                Resolution::UnknownEvent => panic!("{name} vanished"),
            }
        }
    }
}

#[test]
fn asset_cache_answers_waiters_once_and_rejects_stale_generations() {
    let mut cache = AssetCache::default();
    assert_eq!(AssetLookup::Pending, cache.acquire("a", 1));
    assert_eq!(AssetLookup::Pending, cache.acquire("a", 2));
    assert_eq!(vec!["a".to_string()], cache.take_requests());
    assert_eq!(Some(vec![1, 2]), cache.provide("a", cache.generation(), 77));
    assert_eq!(AssetLookup::Ready(77), cache.acquire("a", 3));
    cache.acquire("b", 4);
    let old = cache.clear();
    assert!(cache.provide("b", old, 5).is_none(), "a pre-clear read must not publish");
    cache.preload("c");
    assert_eq!(vec![4u64; 0], cache.fail("c", cache.generation()));
    assert_eq!(AssetLookup::Failed, cache.acquire("c", 6));
}

fn request(instance: u64, category: u8) -> PlayRequest {
    PlayRequest {
        instance,
        event: "minecraft:e".into(),
        path: "minecraft:sounds/a.ogg".into(),
        category,
        volume: 1.0,
        pitch: 1.0,
        position: [1.0, 2.0, 3.0],
        linear_attenuation: true,
        relative: false,
        looping: false,
        delay: 0,
        stream: false,
        attenuation_distance: 16,
        can_start_silent: false,
    }
}

fn loaded_policy() -> Policy {
    let mut policy = Policy::default();
    policy.loaded = true;
    let mut volumes = [1.0; CATEGORY_COUNT];
    volumes[4] = 0.5;
    policy.set_category_volumes(volumes);
    policy
}

fn started_voice(policy: &mut Policy) -> u64 {
    match policy.take_ops().first() {
        Some(VoiceOp::Start { voice, .. }) => *voice,
        other => panic!("expected a start, got {other:?}"),
    }
}

#[test]
fn play_applies_volume_pitch_attenuation_and_silent_rules() {
    let mut policy = loaded_policy();
    let mut loud = request(1, 4);
    loud.volume = 2.0;
    loud.pitch = 3.0;
    loud.looping = true;
    assert_eq!(PlayResult::Started, policy.play(loud));
    match &policy.take_ops()[0] {
        VoiceOp::Start { config, asset: None, .. } => {
            // clamp(2)*clamp(0.5), clamp pitch to 2, max(volume,1)*16 linear.
            assert_eq!((0.5, 2.0, 32.0), (config.gain, config.pitch, config.attenuation_distance));
            assert_eq!(SOUND_FLAG_LINEAR_ATTENUATION | SOUND_FLAG_LOOPING, config.flags);
        }
        other => panic!("{other:?}"),
    }
    let mut zero = request(2, 4);
    zero.volume = 0.0;
    assert_eq!(PlayResult::NotStarted, policy.play(zero.clone()));
    zero.can_start_silent = true;
    assert_eq!(PlayResult::StartedSilently, policy.play(zero));
    let mut music = request(3, CATEGORY_MUSIC);
    music.volume = 0.0;
    assert_eq!(PlayResult::StartedSilently, policy.play(music));
    let not_loaded = &mut Policy::default();
    assert_eq!(PlayResult::NotStarted, not_loaded.play(request(4, 4)));
}

#[test]
fn released_instances_respect_the_minimum_lifetime_and_requeue_manual_loops() {
    let mut policy = loaded_policy();
    let mut looping = request(1, 4);
    looping.looping = true;
    looping.delay = 5;
    policy.play(looping);
    let voice = started_voice(&mut policy);
    policy.voice_released(voice);
    for _ in 0..19 {
        policy.begin_tick(false);
        let (released, _) = policy.end_tick(false);
        assert!(released.is_empty(), "forgotten before the 20-tick minimum lifetime");
    }
    policy.begin_tick(false);
    let (released, due) = policy.end_tick(false);
    assert_eq!((1, true), (released[0].instance, released[0].requeued));
    assert!(due.is_empty());
    assert!(!policy.is_active(1) && policy.knows(1));
    for _ in 0..4 {
        policy.begin_tick(false);
        assert!(policy.end_tick(false).1.is_empty());
    }
    policy.begin_tick(false);
    assert_eq!(vec![1], policy.end_tick(false).1);
    assert!(!policy.knows(1));
    assert_eq!(25, policy.tick_count());
}

#[test]
fn paused_ticks_forget_released_music_at_once_and_others_after_lifetime() {
    let mut policy = loaded_policy();
    policy.play(request(1, CATEGORY_MUSIC));
    let music = started_voice(&mut policy);
    policy.play(request(2, 4));
    let block = started_voice(&mut policy);
    policy.voice_released(music);
    policy.voice_released(block);
    let (released, _) = policy.end_tick(true);
    assert_eq!(vec![1], released.iter().map(|r| r.instance).collect::<Vec<_>>());
    assert!(policy.is_active(2));
}

#[test]
fn replayed_instance_keeps_only_its_newest_voice() {
    let mut policy = loaded_policy();
    policy.play(request(1, 4));
    let first = started_voice(&mut policy);
    policy.play(request(1, 4));
    let second = started_voice(&mut policy);
    policy.voice_released(first);
    for _ in 0..20 {
        policy.begin_tick(false);
    }
    assert!(policy.end_tick(false).0.is_empty(), "the replaced voice must not release the instance");
    policy.voice_released(second);
    assert_eq!(1, policy.end_tick(false).0.len());
}

#[test]
fn stop_matching_pause_and_category_refresh_target_the_right_voices() {
    let mut policy = loaded_policy();
    policy.play(request(1, 4));
    let block = started_voice(&mut policy);
    let mut other = request(2, 8);
    other.event = "minecraft:other".into();
    policy.play(other);
    let ambient = started_voice(&mut policy);
    policy.stop_matching(Some("minecraft:other"), None);
    assert_eq!(vec![VoiceOp::Stop { voice: ambient }], policy.take_ops());
    policy.stop_matching(None, Some(4));
    assert_eq!(vec![VoiceOp::Stop { voice: block }], policy.take_ops());
    policy.pause_all_except(1 << 8);
    assert_eq!(vec![VoiceOp::Pause { voice: block }], policy.take_ops());
    let mut volumes = [1.0; CATEGORY_COUNT];
    volumes[4] = 0.25;
    policy.set_category_volumes(volumes);
    policy.refresh_volumes();
    let gains: Vec<(u64, f32)> = policy
        .take_ops()
        .into_iter()
        .map(|op| match op {
            VoiceOp::Update { voice, mask: SOUND_UPDATE_GAIN, config } => (voice, config.gain),
            other => panic!("{other:?}"),
        })
        .collect();
    assert!(gains.contains(&(block, 0.25)) && gains.contains(&(ambient, 1.0)));
}

#[test]
fn failed_assets_fail_new_voices_immediately() {
    let mut policy = loaded_policy();
    policy.play(request(1, 4));
    let voice = started_voice(&mut policy);
    let generation = policy.assets.generation();
    let waiting = policy.assets.fail("minecraft:sounds/a.ogg", generation);
    policy.asset_failed(waiting);
    assert_eq!(vec![VoiceOp::FailAttachment { voice }], policy.take_ops());
    policy.play(request(2, 4));
    let ops = policy.take_ops();
    assert!(matches!(ops.last(), Some(VoiceOp::FailAttachment { .. })), "{ops:?}");
}
