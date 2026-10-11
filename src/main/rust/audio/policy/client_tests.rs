//! Tests for the Rust-owned client sounds: rules, subtitles, native plays,
//! music, ambience and the preview. Expected values follow the vanilla Java
//! bodies line by line; random draws are replayed on an independent source.

use std::collections::HashSet;
use std::sync::Arc;

use crate::world::level::levelgen::random::Legacy;

use super::ambient::{AdditionsSettings, AmbientInput, BiomeChange, BubbleColumn, MoodSettings, UNDERWATER_LOOP};
use super::client::{Client, JavaRule, NativeSound, TickUpdate, NATIVE_INSTANCE};
use super::engine::{PlayRequest, PlayResult, VoiceOp, CATEGORY_AMBIENT, CATEGORY_MUSIC};
use super::events::RegistryBuilder;
use super::music::{Frequency, Music, Situation, Track, ACTION_HIDE_TOAST, ACTION_SHOW_TOAST};
use super::preview::PREVIEW_EVENTS;
use super::rules::*;
use super::subtitles::Subtitles;

const SOUNDS: &str = r#"{
  "music.game": {"sounds": [{"name": "music/a", "stream": true}]},
  "music.menu": {"sounds": [{"name": "music/b", "stream": true}]},
  "block.test": {"subtitle": "subtitles.block.test", "sounds": ["test/a", "test/b"]},
  "ambient.cave": {"sounds": ["ambient/cave"]},
  "ambient.loop": {"sounds": ["ambient/loop"]},
  "ambient.additions": {"sounds": ["ambient/additions"]},
  "ambient.underwater.loop": {"sounds": ["ambient/underwater"]},
  "ambient.underwater.loop.additions": {"sounds": ["ambient/underwater"]},
  "ambient.underwater.loop.additions.rare": {"sounds": ["ambient/underwater"]},
  "ambient.underwater.loop.additions.ultra_rare": {"sounds": ["ambient/underwater"]},
  "block.bubble_column.upwards_inside": {"sounds": ["bubble/up"]},
  "block.bubble_column.whirlpool_inside": {"sounds": ["bubble/down"]},
  "ui.button.click": {"sounds": ["ui/click"]}
}"#;

fn client() -> Client {
    let files: HashSet<String> = [
        "music/a", "music/b", "test/a", "test/b", "ambient/cave", "ambient/loop", "ambient/additions",
        "ambient/underwater", "bubble/up", "bubble/down", "ui/click",
    ]
    .iter()
    .map(|path| format!("minecraft:sounds/{path}.ogg"))
    .collect();
    let mut builder = RegistryBuilder::new(1, files);
    builder.add_resource("minecraft", "test", SOUNDS);
    let mut client = Client::default();
    client.registry = Arc::new(builder.build());
    assert!(client.registry.warnings.is_empty(), "{:?}", client.registry.warnings);
    client.policy.loaded = true;
    client.fixed_time = Some(1_000);
    client
}

/// The voices of the starts queued since the last call.
fn started(client: &mut Client) -> Vec<u64> {
    client
        .policy
        .take_ops()
        .into_iter()
        .filter_map(|op| match op {
            VoiceOp::Start { voice, .. } => Some(voice),
            _ => None,
        })
        .collect()
}

fn stops(ops: &[VoiceOp]) -> usize {
    ops.iter().filter(|op| matches!(op, VoiceOp::Stop { .. })).count()
}

/// Ends a voice the way the audio thread reports it, then ticks past the
/// minimum lifetime so the policy forgets it.
fn release(client: &mut Client, voice: u64) {
    client.policy.voice_released(voice);
    for _ in 0..21 {
        client.begin_tick(false);
        client.end_tick(false);
    }
}

#[test]
fn legacy_random_matches_java_fixtures() {
    // From `new LegacyRandomSource(42)` / `(-7)` in the game's own classes.
    let mut random = Legacy::new(42);
    assert_eq!(0, random.next_int_bound(10));
    assert_eq!(5, random.next_int_bound(7));
    assert_eq!(1_060_038_587, random.next_float().to_bits());
    assert_eq!(4_587_069_602_885_795_840, random.next_double().to_bits());
    assert_eq!(-1_068_554_554_583_376_509, random.next_long());
    assert_eq!(918, random.next_int_bound(1000));
    let mut random = Legacy::new(-7);
    assert_eq!(4_598_519_687_196_704_768, random.next_double().to_bits());
    assert_eq!(4_604_238_696_533_721_088, random.next_double().to_bits());
    assert_eq!(8, random.next_int_bound(16));
    let copy = Legacy::from_raw(random.raw());
    assert_eq!(copy.raw(), random.raw());
}

fn sample(flags: u32, value: f32) -> Sample {
    Sample { flags, position: [1.25, 64.0, -3.5], value }
}

fn rule(kind: RuleKind, parameter: i32, volume: f32, pitch: f32) -> RuleState {
    RuleState::new(kind, parameter, volume, pitch, [0.0; 3], 1.0, 1.0)
}

#[test]
fn entity_and_minecart_rules_follow_the_entity_and_its_speed() {
    let mut bound = rule(RuleKind::EntityBound, 0, 0.8, 1.3);
    assert!(!bound.tick(&sample(0, 0.0)).stopped);
    assert_eq!(([1.25, 64.0, -3.5], 0.8, 1.3), (bound.position, bound.volume, bound.pitch));
    assert!(bound.tick(&sample(SAMPLE_REMOVED, 0.0)).stopped);

    let mut minecart = rule(RuleKind::Minecart, 0, 0.0, 1.0);
    minecart.tick(&sample(SAMPLE_RUNS_NORMALLY, 0.5));
    assert_eq!(0.0 + 0.5f32 * (0.7 - 0.0), minecart.volume);
    // The class's private `pitch` shadows the instance field: pitch stays 1.
    assert_eq!(1.0, minecart.pitch);
    minecart.tick(&sample(SAMPLE_RUNS_NORMALLY | SAMPLE_OFF_RAILS, 0.5));
    assert_eq!(0.0, minecart.volume);
    minecart.tick(&sample(0, 0.5));
    assert_eq!(0.0, minecart.volume, "frozen tick rate silences it");
    minecart.tick(&sample(SAMPLE_RUNS_NORMALLY, 0.009));
    assert_eq!(0.0, minecart.volume);

    let mut riding = rule(RuleKind::RidingMinecart, 1, 0.0, 1.0);
    riding.tick(&sample(SAMPLE_RIDING, 0.4));
    assert_eq!(0.0, riding.volume, "the underwater variant is silent above water");
    riding.tick(&sample(SAMPLE_RIDING | SAMPLE_UNDERWATER, 0.4));
    assert_eq!(0.0 + 0.4f32 * 0.75, riding.volume);
    riding.tick(&sample(SAMPLE_RIDING | SAMPLE_UNDERWATER, 3.0));
    assert_eq!(0.75, riding.volume);
    assert!(riding.tick(&sample(SAMPLE_UNDERWATER, 0.4)).stopped, "dismounting stops it");
}

#[test]
fn elytra_rule_fades_in_and_raises_pitch() {
    let mut elytra = rule(RuleKind::Elytra, 0, 0.1, 1.0);
    for _ in 0..19 {
        elytra.tick(&sample(0, 2.0));
        assert_eq!(0.0, elytra.volume);
    }
    // time 20 starts the fade at (20 - 20) / 20.
    elytra.tick(&sample(0, 2.0));
    assert_eq!(0.0, elytra.volume);
    for _ in 21..30 {
        elytra.tick(&sample(SAMPLE_FALL_FLYING, 2.0));
    }
    // time 29.
    assert_eq!(0.5 * (9.0f32 / 20.0), elytra.volume);
    for _ in 30..45 {
        elytra.tick(&sample(SAMPLE_FALL_FLYING, 4.0));
    }
    assert_eq!(1.0, elytra.volume);
    assert_eq!(1.0 + (1.0 - 0.8f32), elytra.pitch);
    assert!(elytra.tick(&sample(0, 4.0)).stopped, "past 20 ticks it needs fall flying");
    let mut slow = rule(RuleKind::Elytra, 0, 0.1, 1.0);
    for _ in 0..40 {
        slow.tick(&sample(SAMPLE_FALL_FLYING, 9.0e-8));
    }
    assert_eq!(0.0, slow.volume, "below 1e-7 (compared as double) is silent");
}

#[test]
fn bee_rule_switches_once_and_stops_the_same_tick() {
    let mut bee = rule(RuleKind::Bee, 0, 0.0, 1.0);
    bee.tick(&sample(0, 0.3));
    assert_eq!((0.7 + 0.7 * (1.1 - 0.7f32), 0.0 + 0.3f32 * 1.2), (bee.pitch, bee.volume));
    bee.tick(&sample(SAMPLE_BABY, 1.3));
    assert_eq!(1.1 + 1.3 * (1.5 - 1.1f32), bee.pitch);
    let step = bee.tick(&sample(SAMPLE_SWITCH, 0.3));
    assert_eq!(Step { stopped: true, switched: true }, step);
    assert_eq!(Step { stopped: true, switched: false }, bee.tick(&sample(SAMPLE_SWITCH, 0.3)));
}

#[test]
fn guardian_ghast_and_sniffer_rules() {
    let mut guardian = rule(RuleKind::GuardianAttack, 0, 1.0, 1.0);
    guardian.tick(&sample(0, 0.5));
    assert_eq!((0.0 + 1.0 * 0.5f32 * 0.5, 0.7 + 0.5 * 0.5f32), (guardian.volume, guardian.pitch));
    assert!(guardian.tick(&sample(SAMPLE_HAS_TARGET, 0.5)).stopped);

    let mut ghast = rule(RuleKind::RidingHappyGhast, 0, 0.0, 1.0);
    ghast.tick(&sample(SAMPLE_RIDING, 0.2));
    assert_eq!(5.0 * (0.0 + 0.2f32 * 1.0), ghast.volume);
    ghast.tick(&sample(SAMPLE_RIDING, 0.001));
    assert_eq!(0.0, ghast.volume);
    assert!(ghast.tick(&sample(0, 0.2)).stopped);

    let mut sniffer = rule(RuleKind::Sniffer, 0, 0.3, 0.4);
    sniffer.tick(&sample(SAMPLE_CAN_DIG, 0.0));
    assert_eq!((1.0, 1.0), (sniffer.volume, sniffer.pitch));
    assert!(sniffer.tick(&sample(0, 0.0)).stopped);
}

#[test]
fn ambient_rules_fade_and_stop() {
    let mut biome = rule(RuleKind::BiomeLoop, 0, 1.0, 1.0);
    biome.fade_in();
    for tick in 1..=45 {
        biome.tick(&Sample::default());
        assert_eq!((tick.min(45) as f32 / 40.0).min(1.0), biome.volume, "tick {tick}");
    }
    biome.fade_out();
    let mut ticks = 0;
    while !biome.tick(&Sample::default()).stopped {
        ticks += 1;
        assert!(ticks < 100);
    }
    // 40 down to -1 (41 ticks), then the tick that sees -1 stops it.
    assert_eq!(41, ticks);

    let mut underwater = rule(RuleKind::UnderwaterLoop, 0, 1.0, 1.0);
    for _ in 0..50 {
        underwater.tick(&sample(SAMPLE_UNDERWATER, 0.0));
    }
    assert_eq!(1.0, underwater.volume);
    underwater.tick(&sample(0, 0.0));
    assert_eq!(38.0f32 / 40.0, underwater.volume);
    let mut sub = rule(RuleKind::UnderwaterSub, 0, 1.0, 1.0);
    assert!(!sub.tick(&sample(SAMPLE_UNDERWATER, 0.0)).stopped);
    assert!(sub.tick(&sample(SAMPLE_UNDERWATER | SAMPLE_REMOVED, 0.0)).stopped);
}

#[test]
fn subtitles_refresh_by_text_and_expire_by_age() {
    let mut subtitles = Subtitles::default();
    subtitles.listening = true;
    let text: Arc<str> = Arc::from("subtitles.block.test");
    subtitles.on_play(text.clone(), [0.0, 0.0, 0.0], 16.0, 100);
    subtitles.on_play(text.clone(), [10.0, 0.0, 0.0], 64.0, 200);
    subtitles.on_play(text.clone(), [0.0, 0.0, 0.0], 64.0, 300);
    subtitles.on_play(Arc::from("subtitles.far"), [100.0, 0.0, 0.0], f32::INFINITY, 300);
    let frame = subtitles.frame([9.0, 0.0, 0.0], 3000.0, 400);
    assert_eq!(2, frame.len());
    assert_eq!(([10.0, 0.0, 0.0], 200), (frame[0].position, frame[0].age_ms));
    assert_eq!(&*frame[1].text, "subtitles.far", "an infinite range is always audible");
    // The first play's range (16) sticks: from 30 blocks away it is inaudible.
    assert_eq!(1, subtitles.frame([40.0, 0.0, 0.0], 3000.0, 400).len());
    // After the display time only the refreshed (time 300) instances remain.
    let frame = subtitles.frame([9.0, 0.0, 0.0], 3000.0, 3_250);
    assert_eq!(([0.0, 0.0, 0.0], 2_950), (frame[0].position, frame[0].age_ms));
    assert!(subtitles.frame([9.0, 0.0, 0.0], 3000.0, 10_000).is_empty());
    let mut idle = Subtitles::default();
    idle.on_play(text, [0.0; 3], 16.0, 0);
    assert!(idle.frame([0.0; 3], 3000.0, 1).is_empty(), "nothing is recorded while not listening");
}

#[test]
fn native_plays_resolve_with_their_own_random_and_warn_once() {
    let mut client = client();
    client.subtitles.listening = true;
    let mut random = Legacy::new(77);
    let mut expected = Legacy::new(77);
    let sound = NativeSound::at(Arc::from("minecraft:block.test"), 4, 1.0, 1.0, [3.0, 4.0, 5.0]);
    let (result, id) = client.play_native(sound.clone(), &mut random, true);
    assert_eq!(PlayResult::Started, result);
    assert_ne!(0, id & NATIVE_INSTANCE);
    let choice = expected.next_int_bound(2);
    let location = client.native_location(id).unwrap();
    assert_eq!(if choice == 0 { "minecraft:test/a" } else { "minecraft:test/b" }, &*location);
    assert_eq!(expected.raw(), random.raw());
    let frame = client.subtitles.frame([3.0, 4.0, 5.0], 3000.0, 1_000);
    assert_eq!(&*frame[0].text, "subtitles.block.test");

    let unknown = NativeSound { event: Arc::from("minecraft:nope"), ..sound.clone() };
    assert_eq!((PlayResult::NotStarted, 0), client.play_native(unknown.clone(), &mut random, false));
    client.play_native(unknown, &mut random, false);
    assert_eq!(vec!["W Unable to play unknown soundEvent: minecraft:nope".to_string()], client.take_logs());
    let empty = NativeSound { event: Arc::from("minecraft:intentionally_empty"), ..sound.clone() };
    assert_eq!(PlayResult::NotStarted, client.play_native(empty, &mut random, false).0);
    assert!(client.take_logs().is_empty());

    client.policy.loaded = false;
    let before = random.raw();
    assert_eq!(PlayResult::NotStarted, client.play_native(sound, &mut random, false).0);
    assert_eq!(before, random.raw(), "an unloaded engine resolves nothing");
}

#[test]
fn native_delays_resolve_when_due_and_released_natives_are_forgotten() {
    let mut client = client();
    let sound = NativeSound::at(Arc::from("minecraft:block.test"), 4, 1.0, 1.0, [0.0; 3]);
    client.play_native_delayed(sound, Legacy::new(1), 3);
    for _ in 0..2 {
        client.begin_tick(false);
        client.end_tick(false);
    }
    assert!(started(&mut client).is_empty());
    client.begin_tick(false);
    let (released, due) = client.end_tick(false);
    assert!(released.is_empty() && due.is_empty(), "native work never reaches Java");
    let voices = started(&mut client);
    assert_eq!(1, voices.len());
    assert_eq!(1, client.native_count());
    release(&mut client, voices[0]);
    assert_eq!(0, client.native_count());
}

#[test]
fn held_natives_survive_stop_all_inactive() {
    let mut client = client();
    let mut random = Legacy::new(3);
    let held = NativeSound::listener_relative(Arc::from("minecraft:ambient.cave"), CATEGORY_AMBIENT, 1.0, 1.0);
    let (_, id) = client.play_native(held.clone(), &mut random, true);
    client.play_native(held, &mut random, false);
    client.policy.clear();
    client.clear_playback();
    assert_eq!(1, client.native_count());
    assert!(!client.policy.is_active(id));
    client.release_hold(id);
    assert_eq!(0, client.native_count());
}

fn java_request(instance: u64) -> PlayRequest {
    PlayRequest {
        instance,
        event: Arc::from("minecraft:block.test"),
        path: "minecraft:sounds/test/a.ogg".into(),
        category: 6,
        volume: 0.0,
        pitch: 1.0,
        position: [0.0; 3],
        linear_attenuation: true,
        relative: false,
        looping: true,
        delay: 0,
        stream: false,
        attenuation_distance: 16,
        can_start_silent: true,
    }
}

#[test]
fn java_ruled_instances_tick_in_rust_and_report_stops_and_switches() {
    let mut client = client();
    let rule = JavaRule {
        kind: RuleKind::Bee,
        parameter: 0,
        base_volume: 0.0,
        base_pitch: 1.0,
        sound_volume: 0.5,
        sound_pitch: 1.0,
    };
    assert_eq!(PlayResult::StartedSilently, client.play_java(java_request(5), [0.0; 3], Some(rule)));
    client.policy.take_ops();
    let outcomes = client.update_ticking(&[TickUpdate::Sampled { instance: 5, sample: sample(0, 0.3) }]);
    assert!(outcomes.is_empty());
    match client.policy.take_ops().as_slice() {
        [VoiceOp::Update { config, .. }] => assert_eq!(0.3f32 * 1.2 * 0.5, config.gain),
        other => panic!("{other:?}"),
    }
    let outcomes = client.update_ticking(&[TickUpdate::Sampled { instance: 5, sample: sample(SAMPLE_SWITCH, 0.3) }]);
    assert_eq!(1, outcomes.len());
    assert!(outcomes[0].stopped && outcomes[0].switched);
    assert_eq!(1, stops(&client.policy.take_ops()));
    let plain = client.update_ticking(&[TickUpdate::Plain { instance: 5, volume: 0.25, pitch: 1.0, position: [0.0; 3] }]);
    assert!(plain.is_empty());
}

fn situation(event: &str, min: i32, max: i32, replace: bool, volume: f32) -> Situation {
    Situation {
        track: Some(Track { event: Arc::from(event), min_delay: min, max_delay: max, replace_current: replace }),
        volume,
    }
}

#[test]
fn music_waits_starts_toasts_and_replaces() {
    let mut client = client();
    client.music = Music::new(Frequency::Constant, Legacy::new(9));
    let game = situation("minecraft:music.game", 1200, 2400, false, 1.0);
    // The starting delay is 100; CONSTANT keeps it at 100.
    for _ in 0..100 {
        assert_eq!(0, client.music_tick(&game));
    }
    assert_eq!(ACTION_SHOW_TOAST, client.music_tick(&game));
    assert!(client.music_is_playing("minecraft:music.game"));
    assert_eq!(Some("minecraft:music/a"), client.music_current_sound().as_deref());
    assert_eq!(0, client.music_toast_if_needed(), "the toast already showed");
    let voices = started(&mut client);
    assert_eq!(1, voices.len());

    // A replacing track stops the current one and draws a short delay.
    let menu = situation("minecraft:music.menu", 20, 600, true, 1.0);
    client.music_tick(&menu);
    assert_eq!(1, stops(&client.policy.take_ops()));
    release(&mut client, voices[0]);
    client.music_tick(&menu);
    assert!(!client.music_is_playing("minecraft:music.game"));

    // `stopPlaying` hides the toast and adds 100 to the delay.
    let mut client = self::client();
    client.music = Music::new(Frequency::Constant, Legacy::new(9));
    client.music_start(&Arc::from("minecraft:music.game"), 1.0);
    assert_eq!(ACTION_HIDE_TOAST, client.music_stop());
    // From Integer.MAX_VALUE, + 100 wraps negative, as in Java.
    assert_eq!(i32::MAX.wrapping_add(100), client.music.next_song_delay());
}

#[test]
fn music_delays_draw_from_the_music_random() {
    let mut client = client();
    client.music = Music::new(Frequency::Default, Legacy::new(21));
    let mut expected = Legacy::new(21);
    let game = situation("minecraft:music.game", 12000, 24000, false, 1.0);
    client.music_tick(&game);
    // min(100, nextInt(12000, 24000) clamped to 24000) = 100, then decremented.
    let _ = expected.next_int_bound(24000 - 12000 + 1);
    assert_eq!(99, client.music.next_song_delay());
    client.music_set_frequency(Frequency::Frequent, Some(&game.track.clone().unwrap()));
    // FREQUENT caps both delays at 12000: Mth.nextInt(12000, 12000) draws nothing.
    assert_eq!(12000, client.music.next_song_delay());
    client.music_tick(&Situation { track: None, volume: 1.0 });
    assert_eq!(12000, client.music.next_song_delay());
}

#[test]
fn music_fades_to_a_lower_volume_and_stops_at_silence() {
    let mut client = client();
    client.music = Music::new(Frequency::Constant, Legacy::new(4));
    client.music_start(&Arc::from("minecraft:music.game"), 1.0);
    started(&mut client);
    let quiet = situation("minecraft:music.game", 1200, 2400, false, 0.0);
    let mut ticks = 0;
    let mut actions = 0;
    while client.music_is_playing("minecraft:music.game") {
        actions |= client.music_tick(&quiet);
        ticks += 1;
        assert!(ticks < 1000);
    }
    assert_eq!(ACTION_HIDE_TOAST, actions);
    // 0.97^n drops below 1e-4 after 303 ticks.
    let mut gain = 1.0f32;
    let mut expected = 0;
    while gain > 1.0e-4 {
        gain = 0.03 * 0.0 + 0.97 * gain;
        expected += 1;
    }
    assert_eq!(expected, ticks);
    assert_eq!(CATEGORY_MUSIC, 1);
}

fn ambient_input() -> AmbientInput {
    AmbientInput {
        x: 10.5,
        y: 64.0,
        eye_y: 65.62,
        z: -4.25,
        underwater: false,
        spectator: false,
        bubble_column: BubbleColumn::None,
        biome_change: None,
        sky_light: 15,
        block_light: 0,
    }
}

#[test]
fn underwater_and_bubble_handlers_draw_in_vanilla_order() {
    let mut client = client();
    let mut expected = Legacy::new(500);
    let mut input = ambient_input();
    input.underwater = true;
    input.bubble_column = BubbleColumn::Upwards;
    // First tick: the underwater handler draws a float; the bubble handler
    // only records the column (first tick).
    let output = client.ambient_tick(1, &input, Legacy::new(500).raw());
    expected.next_float();
    assert_eq!(expected.raw(), output.random);
    // Leave the column, then enter it again: a seeded local sound.
    input.bubble_column = BubbleColumn::None;
    let output = client.ambient_tick(1, &input, output.random);
    expected.next_float();
    assert_eq!(expected.raw(), output.random);
    input.bubble_column = BubbleColumn::Downwards;
    started(&mut client);
    let output = client.ambient_tick(1, &input, output.random);
    expected.next_float();
    expected.next_long();
    assert_eq!(expected.raw(), output.random);
    assert!(!started(&mut client).is_empty());
    // Out of water and no column: no draws.
    input.underwater = false;
    input.bubble_column = BubbleColumn::None;
    assert_eq!(expected.raw(), client.ambient_tick(1, &input, expected.raw()).random);
}

#[test]
fn biome_loops_cross_fade_and_mood_samples_light() {
    let mut client = client();
    let mut input = ambient_input();
    let mood = MoodSettings {
        event: Arc::from("minecraft:ambient.cave"),
        tick_delay: 6000,
        block_search_extent: 8,
        sound_position_offset: 2.0,
    };
    input.biome_change = Some(BiomeChange {
        biome: 1,
        ambient_loop: Some(Arc::from("minecraft:ambient.loop")),
        mood: Some(mood.clone()),
        additions: Some(AdditionsSettings { event: Arc::from("minecraft:ambient.additions"), tick_chance: 0.0111 }),
    });
    let mut expected = Legacy::new(-12);
    let output = client.ambient_tick(1, &input, Legacy::new(-12).raw());
    // Additions draw a double, then the mood three bounded ints.
    let roll = expected.next_double();
    let (dx, dy, dz) = (expected.next_int_bound(17), expected.next_int_bound(17), expected.next_int_bound(17));
    let sample = output.mood_sample.expect("mood needs a light sample");
    assert_eq!(
        [
            (10.5 + f64::from(dx) - 8.0).floor() as i32,
            (65.62 + f64::from(dy) - 8.0).floor() as i32,
            (-4.25 + f64::from(dz) - 8.0).floor() as i32,
        ],
        sample
    );
    let voices = started(&mut client);
    assert_eq!(if roll < 0.0111 { 2 } else { 1 }, voices.len(), "the biome loop (and maybe an addition)");
    // Sky light lowers the moodiness from zero, which clamps to zero.
    let finished = client.ambient_finish(15, 0);
    assert_eq!(expected.raw(), finished.random);
    assert_eq!(0.0, finished.moodiness);
    assert_eq!(0.0, finished.constant_moodiness);

    // Darkness raises the mood until a cave sound plays from the level random.
    input.biome_change = None;
    input.sky_light = 0;
    let mut level = expected.raw();
    let mut moods = 0;
    for _ in 0..7000 {
        let output = client.ambient_tick(1, &input, level);
        let finished = client.ambient_finish(0, 0);
        level = finished.random;
        if finished.moodiness == 0.0 && output.moodiness > 0.99 {
            moods += 1;
            break;
        }
    }
    assert_eq!(1, moods, "a full mood plays one cave sound");
    assert!(client.ambient_tick(1, &input, level).constant_moodiness > 0.0);

    // A biome without a loop fades the old loop out until it stops.
    input.biome_change = Some(BiomeChange { biome: 2, ambient_loop: None, mood: None, additions: None });
    input.sky_light = 15;
    client.ambient_tick(1, &input, level);
    input.biome_change = None;
    for _ in 0..60 {
        client.begin_tick(false);
        client.end_tick(false);
        client.ambient_tick(1, &input, level);
    }
    assert!(client.ambient.into_held().is_empty(), "the stopped loop left the handler");
}

#[test]
fn underwater_loop_follows_the_player_generation() {
    let mut client = client();
    client.play_underwater_loop(1);
    assert_eq!(1, started(&mut client).len());
    let mut input = ambient_input();
    input.underwater = true;
    client.ambient_tick(1, &input, 0);
    // The underwater handler may also have played an addition.
    let additions = started(&mut client).len();
    client.begin_tick(false);
    client.end_tick(false);
    assert_eq!(0, stops(&client.policy.take_ops()));
    client.use_player(2);
    client.begin_tick(false);
    client.end_tick(false);
    assert_eq!(1 + additions, stops(&client.policy.take_ops()), "a new player removes the old one's sounds");
}

#[test]
fn preview_stops_on_category_change_and_waits_while_active() {
    let mut client = client();
    client.preview(10, 0.5);
    assert_eq!(1, started(&mut client).len());
    client.preview(10, 0.7);
    assert!(started(&mut client).is_empty(), "the active preview keeps playing");
    client.preview(4, 0.7);
    assert_eq!(1, stops(&client.policy.take_ops()));
}

#[test]
fn hardcoded_events_exist_in_the_bundled_sounds() {
    let json = std::fs::read_to_string(concat!(env!("CARGO_MANIFEST_DIR"), "/../resources/assets/minecraft/sounds.json"))
        .expect("bundled sounds.json");
    let value: serde_json::Value = serde_json::from_str(&json).unwrap();
    let names = [
        UNDERWATER_LOOP,
        "minecraft:ambient.underwater.loop.additions",
        "minecraft:ambient.underwater.loop.additions.rare",
        "minecraft:ambient.underwater.loop.additions.ultra_rare",
        "minecraft:block.bubble_column.whirlpool_inside",
        "minecraft:block.bubble_column.upwards_inside",
    ];
    for name in names.iter().copied().chain(PREVIEW_EVENTS.iter().flatten().copied()) {
        let key = name.strip_prefix("minecraft:").unwrap();
        assert!(value.get(key).is_some(), "{name} is not in sounds.json");
    }
}
