use std::sync::{Mutex, OnceLock};

use super::backend::{self, test_support};
use super::commands::{
    SoundConfigRecord, StaticDecodeParityRecord, StreamChunkRecord, SOUND_FLAG_DISABLE_ATTENUATION,
    SOUND_FLAG_LINEAR_ATTENUATION, SOUND_FLAG_LOOPING, SOUND_FLAG_RELATIVE, SOUND_UPDATE_GAIN,
    SOUND_UPDATE_POSITION,
};
use super::device::{split_channel_counts, ChannelPool, NativeDevice};
use super::errors::{
    AudioError, ERR_DECODE_FAILED, ERR_INVALID_ARGUMENT, ERR_INVALID_HANDLE, ERR_POOL_EXHAUSTED,
    ERR_UNSUPPORTED_FORMAT, ERR_WRONG_THREAD, OK,
};
use super::ffi::{
    mattmc_audio_asset_compare_static_pcm, mattmc_audio_asset_create, mattmc_audio_asset_destroy,
    mattmc_audio_asset_destroy_generation, mattmc_audio_device_create, mattmc_audio_device_destroy,
    mattmc_audio_format_to_openal, mattmc_audio_sound_create_static_from_asset,
    mattmc_audio_sound_create_streaming, mattmc_audio_sound_state,
    mattmc_audio_sound_stop_and_destroy, mattmc_audio_sound_submit_stream_chunks,
};
use super::format::audio_format_to_openal;
use super::handles::{HandleTable, NativeHandle, ResourceKind};
use super::listener::ListenerTransform;
use super::source::AL_STOPPED;
use super::{context::load_openal, errors::AudioResult};

const PARITY_MONO_OGG: &[u8] =
    include_bytes!("../../../resources/assets/minecraft/sounds/random/pop.ogg");
const OVERSIZED_STATIC_PCM_LEN: u64 = (super::decoder::MAX_DECODED_PCM_BYTES as u64) + 1;

fn audio_test_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

fn with_audio_backend<T>(f: impl FnOnce() -> T) -> T {
    let _guard = audio_test_lock().lock().expect("audio test lock poisoned");
    test_support::reset_backend_for_tests();
    let result = f();
    test_support::reset_backend_for_tests();
    result
}

fn create_test_device() -> AudioResult<u64> {
    backend::create_device(None, false)
}

#[test]
fn encoded_audio_asset_creation_copies_bytes_and_metadata() {
    with_audio_backend(|| {
        let mut encoded = b"OggSasset-bytes".to_vec();
        let asset = backend::create_asset(&encoded, Some("minecraft:test".to_string()), 1)
            .expect("asset should be created");
        encoded[0] = b'X';

        let snapshot = backend::asset_snapshot_for_tests(asset).expect("asset should remain live");
        assert_eq!(b"OggSasset-bytes".to_vec(), snapshot.encoded);
        assert_eq!(Some("minecraft:test".to_string()), snapshot.debug_name);
        assert_eq!(1, snapshot.reload_generation);
        assert_eq!(snapshot.encoded.len() as u64, snapshot.metadata.byte_len);
        assert!(snapshot.metadata.ogg_container);
        assert_eq!(1, test_support::asset_count_for_tests());
    });
}

#[test]
fn encoded_audio_asset_handles_are_typed_and_generation_safe() {
    with_audio_backend(|| {
        let first = backend::create_asset(b"OggSfirst", None, 1).expect("asset should create");
        let decoded = NativeHandle::decode(first).expect("asset handle should decode");
        assert_eq!(ResourceKind::Asset, decoded.kind);
        assert_eq!(1, decoded.generation);

        backend::destroy_asset(first).expect("asset should destroy");
        let second = backend::create_asset(b"OggSsecond", None, 1).expect("asset should create");
        let second_decoded = NativeHandle::decode(second).expect("asset handle should decode");
        assert_eq!(ResourceKind::Asset, second_decoded.kind);
        assert_ne!(first, second);
        assert_ne!(decoded.generation, second_decoded.generation);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::validate_asset(first)
        );
        assert!(backend::validate_asset(second).is_ok());
    });
}

#[test]
fn encoded_audio_asset_wrong_kind_and_double_destroy_fail_safely() {
    with_audio_backend(|| {
        let asset = backend::create_asset(b"OggSasset", None, 1).expect("asset should create");
        assert_eq!(Err(AudioError::InvalidHandle), backend::source_state(asset));
        backend::destroy_asset(asset).expect("first destroy should succeed");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::destroy_asset(asset)
        );
    });
}

#[test]
fn reload_generation_invalidation_rejects_late_old_assets() {
    with_audio_backend(|| {
        let old = backend::create_asset(b"OggSold", Some("old".to_string()), 1)
            .expect("old asset should create");
        let current = backend::create_asset(b"OggScurrent", Some("current".to_string()), 2)
            .expect("current asset should create");

        backend::destroy_asset_generation(1).expect("generation 1 should invalidate");
        assert_eq!(Err(AudioError::InvalidHandle), backend::validate_asset(old));
        assert!(backend::validate_asset(current).is_ok());
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::create_asset(b"OggSlate", Some("late".to_string()), 1)
        );
        assert!(backend::create_asset(b"OggSnew", Some("new".to_string()), 2).is_ok());
    });
}

#[test]
fn encoded_audio_asset_shutdown_clear_removes_all_assets() {
    with_audio_backend(|| {
        let first = backend::create_asset(b"OggSfirst", None, 1).expect("asset should create");
        let second = backend::create_asset(b"OggSsecond", None, 2).expect("asset should create");
        assert_eq!(2, test_support::asset_count_for_tests());
        backend::clear_assets_for_shutdown().expect("shutdown asset clear should succeed");
        assert_eq!(0, test_support::asset_count_for_tests());
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::validate_asset(first)
        );
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::validate_asset(second)
        );
    });
}

#[test]
fn handle_table_rejects_removed_handles() {
    let mut table = HandleTable::new(ResourceKind::Device);
    let handle = table.insert("device");
    assert_eq!(Some(&"device"), table.get(handle));
    assert_eq!(Some("device"), table.remove(handle));
    assert_eq!(None, table.get(handle));
    assert_eq!(0, table.len());
}

#[test]
fn handle_table_encodes_kind_generation_and_slot() {
    let mut table = HandleTable::new(ResourceKind::Source);
    let first = table.insert("source-a");
    let decoded = NativeHandle::decode(first).expect("handle should decode");
    assert_eq!(ResourceKind::Source, decoded.kind);
    assert_eq!(1, decoded.generation);
    assert_eq!(1, decoded.slot);

    assert_eq!(Some("source-a"), table.remove(first));
    let second = table.insert("source-b");
    let second_decoded = NativeHandle::decode(second).expect("handle should decode");
    assert_eq!(ResourceKind::Source, second_decoded.kind);
    assert_ne!(decoded.generation, second_decoded.generation);
    assert_eq!(None, table.get(first));
    assert_eq!(Some(&"source-b"), table.get(second));
}

#[test]
fn owner_thread_can_use_context_sensitive_handles() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("owner-thread".to_string()), 1)
            .expect("asset should be created");
        let source = backend::create_static_sound(device, SoundConfigRecord::default(), asset)
            .expect("static sound should be created on owner thread");
        assert!(backend::source_state(source).is_ok());
    });
}

#[test]
fn wrong_thread_rejects_context_sensitive_operations() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created on owner thread");
        let result = std::thread::spawn(move || backend::source_state(source))
            .join()
            .expect("wrong-thread check thread should not panic");
        assert_eq!(Err(AudioError::WrongThread), result);
    });
}

#[test]
fn stale_handles_after_destroy_and_reload_fail_safely() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");
        backend::destroy_device(device).expect("device destroy should invalidate children");
        let reloaded = create_test_device().expect("new device should open after reload");

        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(source)
        );
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::device_pool_counts(device)
        );
        assert!(backend::device_pool_counts(reloaded).is_ok());
    });
}

#[test]
fn handle_generation_reuse_protection_survives_reload() {
    with_audio_backend(|| {
        let old_device = create_test_device().expect("default audio device should open");
        let old = NativeHandle::decode(old_device).expect("device handle should decode");
        backend::destroy_device(old_device).expect("old device should destroy");
        let new_device = create_test_device().expect("new device should open");
        let new = NativeHandle::decode(new_device).expect("device handle should decode");

        assert_eq!(ResourceKind::Device, old.kind);
        assert_eq!(ResourceKind::Device, new.kind);
        assert_ne!(old_device, new_device);
        assert_ne!(old.generation, new.generation);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::device_pool_counts(old_device)
        );
    });
}

#[test]
fn wrong_resource_type_fails_safely() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");

        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(device)
        );
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::destroy_asset(source)
        );
    });
}

#[test]
fn static_sound_rejects_wrong_asset_type() {
    with_audio_backend(|| {
        let device = create_test_device().expect("device should open");
        let source =
            backend::create_source(device, ChannelPool::Static).expect("source should be created");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::create_static_sound(device, SoundConfigRecord::default(), source)
        );
    });
}

#[test]
fn channel_pool_split_matches_java_limits() {
    assert_eq!((25, 5), split_channel_counts(30));
    assert_eq!((8, 2), split_channel_counts(1));
    assert_eq!((255, 8), split_channel_counts(400));
}

#[test]
fn pool_accounting_tracks_source_lifetime() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let initial = backend::device_pool_counts(device).expect("pool counts should be readable");
        assert_eq!(0, initial.static_used);
        assert_eq!(0, initial.streaming_used);

        let static_source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");
        let streaming_source = backend::create_source(device, ChannelPool::Streaming)
            .expect("streaming source should be created");
        let after_create =
            backend::device_pool_counts(device).expect("pool counts should be readable");
        assert_eq!(1, after_create.static_used);
        assert_eq!(1, after_create.streaming_used);

        backend::destroy_source(static_source).expect("static source should be destroyed");
        backend::destroy_source(streaming_source).expect("streaming source should be destroyed");
        let after_destroy =
            backend::device_pool_counts(device).expect("pool counts should be readable");
        assert_eq!(0, after_destroy.static_used);
        assert_eq!(0, after_destroy.streaming_used);
    });
}

#[test]
fn double_destroy_and_stale_handles_fail_without_leaking_pool_slots() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");
        backend::destroy_source(source).expect("first source destroy should succeed");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::destroy_source(source)
        );

        let counts = backend::device_pool_counts(device).expect("pool counts should be readable");
        assert_eq!(0, counts.static_used);

        backend::destroy_device(device).expect("first device destroy should succeed");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::destroy_device(device)
        );
    });
}

#[test]
fn device_cleanup_removes_owned_sources_and_static_cache() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("cleanup".to_string()), 1)
            .expect("asset should be created");
        let source = backend::create_static_sound(device, SoundConfigRecord::default(), asset)
            .expect("static sound should be created");

        let before = test_support::counts_for_tests();
        assert_eq!(1, before.devices);
        assert_eq!(1, before.sources);
        assert_eq!(0, before.buffers);
        assert_eq!(0, before.queued_stream_buffers);
        assert_eq!(1, before.assets);
        assert_eq!(1, before.static_cache_entries);

        backend::destroy_device(device).expect("device destroy should clean owned handles");
        let after = test_support::counts_for_tests();
        assert_eq!(0, after.devices);
        assert_eq!(0, after.sources);
        assert_eq!(0, after.buffers);
        assert_eq!(0, after.queued_stream_buffers);
        assert_eq!(1, after.assets);
        assert_eq!(0, after.static_cache_entries);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(source)
        );
        backend::destroy_asset(asset).expect("asset cleanup should succeed");
    });
}

#[test]
fn streaming_queue_ownership_is_released_with_source() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Streaming)
            .expect("streaming source should be created");
        let pcm = [0_u8, 0, 0, 0, 0, 0, 0, 0];
        let chunks: [&[u8]; 1] = [&pcm];
        backend::submit_stream_chunks(source, &chunks, 1, 16, true, 44_100)
            .expect("streaming chunk should queue");
        assert_eq!(1, backend::live_counts().unwrap().queued_stream_buffers);
        let processed = backend::remove_processed_stream_buffers(source)
            .expect("processed buffers should read");
        assert!(processed >= 0);
        backend::destroy_source(source).expect("streaming source should drop queued buffers");
        assert_eq!(0, backend::live_counts().unwrap().queued_stream_buffers);
    });
}

#[test]
fn coarse_static_sound_create_configures_and_tracks_pool() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("coarse-static".to_string()), 1)
            .expect("asset should be created");
        let config = SoundConfigRecord {
            x: 1.0,
            y: 2.0,
            z: 3.0,
            pitch: 0.75,
            gain: 0.5,
            attenuation_distance: 16.0,
            flags: SOUND_FLAG_LOOPING | SOUND_FLAG_RELATIVE | SOUND_FLAG_LINEAR_ATTENUATION,
        };

        let sound = backend::create_static_sound(device, config, asset)
            .expect("static sound should be created with its initial config");
        let counts = backend::device_pool_counts(device).expect("pool counts should read");
        assert_eq!(1, counts.static_used);

        backend::update_sound(
            sound,
            SOUND_UPDATE_POSITION | SOUND_UPDATE_GAIN,
            SoundConfigRecord {
                x: 4.0,
                y: 5.0,
                z: 6.0,
                gain: 0.25,
                ..config
            },
        )
        .expect("coarse update should apply selected fields");
        backend::destroy_source(sound).expect("sound destroy should release pool");
        backend::destroy_asset(asset).expect("asset should destroy");
        let counts = backend::device_pool_counts(device).expect("pool counts should read");
        assert_eq!(0, counts.static_used);
    });
}

#[test]
fn static_asset_playback_reuses_device_scoped_cache_for_repeated_sounds() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("cache-hit".to_string()), 1)
            .expect("asset should be created");

        let first = backend::create_static_sound(device, SoundConfigRecord::default(), asset)
            .expect("first static sound should decode and create");
        let after_first = test_support::static_cache_stats_for_tests();
        assert_eq!(1, after_first.entries);
        assert_eq!(1, after_first.ready_entries);
        assert_eq!(0, after_first.failed_entries);
        assert_eq!(1, after_first.first_channels);
        assert!(after_first.first_sample_rate > 0);
        assert!(after_first.total_frames > 0);
        assert_eq!(0, after_first.hits);
        assert_eq!(1, after_first.misses);

        let second = backend::create_static_sound(device, SoundConfigRecord::default(), asset)
            .expect("second static sound should reuse cached buffer");
        let after_second = test_support::static_cache_stats_for_tests();
        assert_eq!(1, after_second.entries);
        assert_eq!(1, after_second.hits);
        assert_eq!(1, after_second.misses);
        assert_ne!(first, second);

        let counts = backend::device_pool_counts(device).expect("pool counts should read");
        assert_eq!(2, counts.static_used);
        backend::destroy_source(first).expect("first source should destroy");
        backend::destroy_source(second).expect("second source should destroy");
        backend::destroy_asset(asset).expect("asset destroy should clear cache");
        assert_eq!(0, test_support::static_cache_stats_for_tests().entries);
    });
}

#[test]
fn static_asset_cache_is_invalidated_by_device_and_asset_generation() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("cache-invalidate".to_string()), 1)
            .expect("asset should be created");
        let sound = backend::create_static_sound(device, SoundConfigRecord::default(), asset)
            .expect("static sound should create");
        assert_eq!(1, test_support::static_cache_stats_for_tests().entries);

        backend::destroy_device(device).expect("device destroy should clear static cache");
        assert_eq!(0, test_support::static_cache_stats_for_tests().entries);
        assert_eq!(Err(AudioError::InvalidHandle), backend::source_state(sound));

        let reloaded = create_test_device().expect("device should reopen");
        let reloaded_sound =
            backend::create_static_sound(reloaded, SoundConfigRecord::default(), asset)
                .expect("asset should decode again for the reloaded device");
        assert_eq!(1, test_support::static_cache_stats_for_tests().entries);
        backend::destroy_source(reloaded_sound).expect("reloaded sound should destroy");

        backend::destroy_asset_generation(1).expect("asset generation should invalidate");
        assert_eq!(0, test_support::static_cache_stats_for_tests().entries);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::create_static_sound(reloaded, SoundConfigRecord::default(), asset)
        );
    });
}

#[test]
fn failed_static_asset_decode_is_cached_until_asset_destroy() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let asset = backend::create_asset(b"not ogg", Some("bad".to_string()), 1)
            .expect("bad encoded asset still owns bytes");

        assert!(matches!(
            backend::create_static_sound(device, SoundConfigRecord::default(), asset),
            Err(AudioError::DecodeFailed(_))
        ));
        let after_first = test_support::static_cache_stats_for_tests();
        assert_eq!(1, after_first.entries);
        assert_eq!(0, after_first.ready_entries);
        assert_eq!(1, after_first.failed_entries);
        assert_eq!(0, after_first.hits);
        assert_eq!(1, after_first.misses);

        assert!(matches!(
            backend::create_static_sound(device, SoundConfigRecord::default(), asset),
            Err(AudioError::DecodeFailed(_))
        ));
        let after_second = test_support::static_cache_stats_for_tests();
        assert_eq!(1, after_second.entries);
        assert_eq!(1, after_second.hits);
        assert_eq!(1, after_second.misses);

        let counts = backend::device_pool_counts(device).expect("pool counts should read");
        assert_eq!(0, counts.static_used);
        backend::destroy_asset(asset)
            .expect("destroying failed asset should clear failed cache entry");
        assert_eq!(0, test_support::static_cache_stats_for_tests().entries);
    });
}

#[test]
fn coarse_streaming_sound_owns_queued_buffers() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let config = SoundConfigRecord {
            flags: SOUND_FLAG_DISABLE_ATTENUATION,
            ..SoundConfigRecord::default()
        };
        let sound = backend::create_streaming_sound(device, config)
            .expect("streaming sound should be created");
        let first = [0_u8, 0, 0, 0];
        let second = [0_u8, 0, 0, 0];
        let chunks: [&[u8]; 2] = [&first, &second];
        assert_eq!(
            2,
            backend::submit_stream_chunks(sound, &chunks, 1, 16, true, 44_100)
                .expect("stream chunks should submit as a batch")
        );
        assert_eq!(2, backend::live_counts().unwrap().queued_stream_buffers);
        backend::destroy_source(sound).expect("sound destroy should release stream queue");
        assert_eq!(0, backend::live_counts().unwrap().queued_stream_buffers);
    });
}

#[test]
fn counter_reset_after_shutdown_clears_live_resources_and_queued_stream_buffers() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Streaming)
            .expect("streaming source should be created");
        let asset = backend::create_asset(PARITY_MONO_OGG, Some("shutdown".to_string()), 1)
            .expect("asset should be created");
        let static_sound =
            backend::create_static_sound(device, SoundConfigRecord::default(), asset)
                .expect("static sound should be created");
        let pcm = [0_u8, 0, 0, 0];
        let chunks: [&[u8]; 1] = [&pcm];
        backend::submit_stream_chunks(source, &chunks, 1, 16, true, 44_100)
            .expect("streaming chunk should queue");

        let before = backend::live_counts().expect("live counts should read");
        assert_eq!(1, before.devices);
        assert_eq!(2, before.sources);
        assert_eq!(0, before.buffers);
        assert_eq!(1, before.queued_stream_buffers);
        assert_eq!(1, before.assets);
        assert_eq!(1, before.static_cache_entries);

        backend::destroy_device(device).expect("device shutdown should clear children");
        let after = backend::live_counts().expect("live counts should read");
        assert_eq!(0, after.devices);
        assert_eq!(0, after.sources);
        assert_eq!(0, after.buffers);
        assert_eq!(0, after.queued_stream_buffers);
        assert_eq!(1, after.assets);
        assert_eq!(0, after.static_cache_entries);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(static_sound)
        );
        backend::clear_assets_for_shutdown().expect("asset shutdown should clear assets");
    });
}

#[test]
fn listener_state_updates_are_bound_to_live_device_handles() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        backend::listener_set_transform(
            device,
            ListenerTransform {
                position: [1.0, 2.0, 3.0],
                forward: [0.0, 0.0, -1.0],
                up: [0.0, 1.0, 0.0],
            },
        )
        .expect("listener transform should update");
        backend::listener_set_gain(device, 0.5).expect("listener gain should update");
        backend::destroy_device(device).expect("device should destroy");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::listener_set_gain(device, 1.0)
        );
    });
}

#[test]
fn audio_format_mapping_matches_openal_constants() {
    assert_eq!(0x1100, audio_format_to_openal(1, 8, true).unwrap());
    assert_eq!(0x1101, audio_format_to_openal(1, 16, true).unwrap());
    assert_eq!(0x1102, audio_format_to_openal(2, 8, true).unwrap());
    assert_eq!(0x1103, audio_format_to_openal(2, 16, true).unwrap());
    assert_eq!(
        Err(AudioError::UnsupportedFormat),
        audio_format_to_openal(6, 16, true)
    );
    assert_eq!(
        Err(AudioError::UnsupportedFormat),
        audio_format_to_openal(2, 24, true)
    );
    assert_eq!(
        Err(AudioError::UnsupportedFormat),
        audio_format_to_openal(2, 16, false)
    );
}

#[test]
fn public_status_codes_are_stable() {
    assert_eq!(-1, AudioError::InvalidHandle.status());
    assert_eq!(-2, AudioError::InvalidArgument.status());
    assert_eq!(-6, AudioError::PoolExhausted.status());
    assert_eq!(-7, AudioError::UnsupportedFormat.status());
    assert_eq!(-8, AudioError::WrongThread.status());
    assert_eq!(ERR_INVALID_HANDLE, AudioError::InvalidHandle.status());
    assert_eq!(ERR_INVALID_ARGUMENT, AudioError::InvalidArgument.status());
    assert_eq!(ERR_POOL_EXHAUSTED, AudioError::PoolExhausted.status());
    assert_eq!(
        ERR_UNSUPPORTED_FORMAT,
        AudioError::UnsupportedFormat.status()
    );
    assert_eq!(
        ERR_DECODE_FAILED,
        AudioError::DecodeFailed("bad".to_string()).status()
    );
}

#[test]
fn ffi_wrong_thread_returns_distinct_status_and_safe_fallback_state() {
    with_audio_backend(|| {
        let mut device = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 0, 0, &mut device)
        });
        let config = SoundConfigRecord::default();
        let mut sound = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_sound_create_streaming(device, &config, &mut sound)
        });

        let result = std::thread::spawn(move || {
            let mut state = 0_i32;
            let status = unsafe { mattmc_audio_sound_state(sound, &mut state) };
            (status, state)
        })
        .join()
        .expect("wrong-thread ffi test should not panic");

        assert_eq!((ERR_WRONG_THREAD, AL_STOPPED), result);
    });
}

#[test]
fn ffi_rejects_malformed_arguments_without_openal() {
    with_audio_backend(|| {
        let mut handle = 0_u64;
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 1, 0, &mut handle)
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 0, 0, std::ptr::null_mut())
        });
    });
}

#[test]
fn ffi_audio_asset_lifecycle_validates_pointers_generations_and_handles() {
    with_audio_backend(|| {
        let encoded = b"OggSffi";
        let debug_name = b"minecraft:ffi";
        let mut asset = 0_u64;

        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_create(std::ptr::null(), 1, std::ptr::null(), 0, 1, &mut asset)
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                std::ptr::null(),
                1,
                1,
                &mut asset,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                0,
                &mut asset,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                1,
                std::ptr::null_mut(),
            )
        });
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                1,
                &mut asset,
            )
        });
        let decoded = NativeHandle::decode(asset).expect("asset handle should decode");
        assert_eq!(ResourceKind::Asset, decoded.kind);

        assert_eq!(OK, unsafe { mattmc_audio_asset_destroy(asset) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_asset_destroy(asset)
        });

        let mut old = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                2,
                &mut old,
            )
        });
        assert_eq!(OK, unsafe { mattmc_audio_asset_destroy_generation(2) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                2,
                &mut old,
            )
        });
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                encoded.as_ptr(),
                encoded.len() as u64,
                debug_name.as_ptr(),
                debug_name.len() as u64,
                3,
                &mut old,
            )
        });
    });
}

#[test]
fn ffi_static_decode_parity_reports_exact_match_mismatch_and_decode_errors() {
    with_audio_backend(|| {
        let decoded =
            super::decoder::decode_vorbis(PARITY_MONO_OGG).expect("mono fixture should decode");
        let java_pcm = samples_to_le_bytes(&decoded.samples);
        let mut asset = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                PARITY_MONO_OGG.as_ptr(),
                PARITY_MONO_OGG.len() as u64,
                std::ptr::null(),
                0,
                1,
                &mut asset,
            )
        });

        let mut record = StaticDecodeParityRecord::default();
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                asset,
                java_pcm.as_ptr(),
                java_pcm.len() as u64,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
        assert_eq!(OK, record.rust_status);
        assert_eq!(1, record.format_match);
        assert_eq!(1, record.exact_pcm_match);
        assert_eq!(0, record.mismatch_count);

        let mut mismatched = java_pcm.clone();
        mismatched[0] ^= 0x7f;
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                asset,
                mismatched.as_ptr(),
                mismatched.len() as u64,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
        assert_eq!(0, record.exact_pcm_match);
        assert_eq!(0, record.first_differing_sample);
        assert!(record.max_abs_sample_delta > 0);
        assert!(record.mismatch_count > 0);

        let mut bad_asset = 0_u64;
        let bad = b"not ogg";
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                bad.as_ptr(),
                bad.len() as u64,
                std::ptr::null(),
                0,
                1,
                &mut bad_asset,
            )
        });
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                bad_asset,
                java_pcm.as_ptr(),
                java_pcm.len() as u64,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
        assert_eq!(ERR_DECODE_FAILED, record.rust_status);
    });
}

#[test]
fn ffi_static_decode_parity_rejects_invalid_and_stale_asset_handles() {
    with_audio_backend(|| {
        let decoded =
            super::decoder::decode_vorbis(PARITY_MONO_OGG).expect("mono fixture should decode");
        let java_pcm = samples_to_le_bytes(&decoded.samples);
        let mut record = StaticDecodeParityRecord::default();
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                42,
                java_pcm.as_ptr(),
                java_pcm.len() as u64,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });

        let mut asset = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                PARITY_MONO_OGG.as_ptr(),
                PARITY_MONO_OGG.len() as u64,
                std::ptr::null(),
                0,
                2,
                &mut asset,
            )
        });
        assert_eq!(OK, unsafe { mattmc_audio_asset_destroy_generation(2) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                asset,
                java_pcm.as_ptr(),
                java_pcm.len() as u64,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                asset,
                std::ptr::null(),
                1,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_asset_compare_static_pcm(
                asset,
                std::ptr::NonNull::<u8>::dangling().as_ptr(),
                OVERSIZED_STATIC_PCM_LEN,
                decoded.channels as i32,
                16,
                1,
                decoded.sample_rate as i32,
                &mut record,
            )
        });
    });
}

fn samples_to_le_bytes(samples: &[i16]) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(samples.len() * 2);
    for sample in samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}

#[test]
fn ffi_format_mapping_reports_status_and_value() {
    let mut format = 0_i32;
    assert_eq!(OK, unsafe {
        mattmc_audio_format_to_openal(2, 16, 1, &mut format)
    });
    assert_eq!(0x1103, format);
    assert_eq!(ERR_UNSUPPORTED_FORMAT, unsafe {
        mattmc_audio_format_to_openal(6, 16, 1, &mut format)
    });
    assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
        mattmc_audio_format_to_openal(2, 16, 1, std::ptr::null_mut())
    });
}

#[test]
fn ffi_invalid_sound_state_fails_safely() {
    with_audio_backend(|| {
        let mut state = 0_i32;
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_sound_state(42, &mut state)
        });
        assert_eq!(AL_STOPPED, state);
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_state(42, std::ptr::null_mut())
        });
    });
}

#[test]
fn ffi_lifecycle_rejects_double_destroy_and_stale_handles() {
    with_audio_backend(|| {
        let mut device = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 0, 0, &mut device)
        });
        let config = SoundConfigRecord::default();
        let mut sound = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_sound_create_streaming(device, &config, &mut sound)
        });
        assert_eq!(OK, unsafe { mattmc_audio_sound_stop_and_destroy(sound) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_sound_stop_and_destroy(sound)
        });
        assert_eq!(OK, unsafe { mattmc_audio_device_destroy(device) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_device_destroy(device)
        });
    });
}

#[test]
fn ffi_streaming_queue_validates_pointer_and_handle_ownership() {
    with_audio_backend(|| {
        let mut device = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 0, 0, &mut device)
        });
        let config = SoundConfigRecord::default();
        let mut sound = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_sound_create_streaming(device, &config, &mut sound)
        });
        let mut accepted = -1_i32;
        let bad_chunk = StreamChunkRecord {
            data_ptr: std::ptr::null(),
            data_len: 4,
        };
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_submit_stream_chunks(
                sound,
                &bad_chunk,
                1,
                1,
                16,
                1,
                44_100,
                &mut accepted,
            )
        });

        let pcm = [0_u8, 0, 0, 0];
        let chunk = StreamChunkRecord {
            data_ptr: pcm.as_ptr(),
            data_len: pcm.len() as u64,
        };
        assert_eq!(OK, unsafe {
            mattmc_audio_sound_submit_stream_chunks(
                sound,
                &chunk,
                1,
                1,
                16,
                1,
                44_100,
                &mut accepted,
            )
        });
        assert_eq!(1, accepted);
    });
}

#[test]
fn ffi_coarse_sound_calls_validate_config_chunks_and_outputs() {
    with_audio_backend(|| {
        let mut sound = 0_u64;
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_create_streaming(0, std::ptr::null(), &mut sound)
        });
        let config = SoundConfigRecord::default();
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_create_streaming(0, &config, std::ptr::null_mut())
        });

        let mut device = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_device_create(std::ptr::null(), 0, 0, &mut device)
        });
        assert_eq!(OK, unsafe {
            mattmc_audio_sound_create_streaming(device, &config, &mut sound)
        });

        let mut accepted = -1_i32;
        let bad_chunk = StreamChunkRecord {
            data_ptr: std::ptr::null(),
            data_len: 4,
        };
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_submit_stream_chunks(
                sound,
                &bad_chunk,
                1,
                1,
                16,
                1,
                44_100,
                &mut accepted,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_submit_stream_chunks(
                sound,
                std::ptr::null(),
                1,
                1,
                16,
                1,
                44_100,
                &mut accepted,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_submit_stream_chunks(
                sound,
                std::ptr::null(),
                0,
                1,
                16,
                1,
                44_100,
                std::ptr::null_mut(),
            )
        });

        let mut state = 0_i32;
        assert_eq!(OK, unsafe { mattmc_audio_sound_state(sound, &mut state) });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_state(sound, std::ptr::null_mut())
        });

        let mut asset = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_asset_create(
                PARITY_MONO_OGG.as_ptr(),
                PARITY_MONO_OGG.len() as u64,
                std::ptr::null(),
                0,
                1,
                &mut asset,
            )
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_sound_create_static_from_asset(device, std::ptr::null(), asset, &mut sound)
        });
    });
}

#[test]
fn openal_soft_static_link_opens_default_device_and_context() {
    let alto = load_openal().expect("statically linked OpenAL Soft should initialize");
    let device = NativeDevice::open(&alto, None, false)
        .expect("statically linked OpenAL Soft should open the default output device");
    assert!(!device.current_device_name.is_empty());
}
