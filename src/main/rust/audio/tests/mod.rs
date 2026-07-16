use std::sync::{Mutex, OnceLock};

use super::backend::{self, test_support};
use super::device::{split_channel_counts, ChannelPool, NativeDevice};
use super::errors::{
    AudioError, ERR_INVALID_ARGUMENT, ERR_INVALID_HANDLE, ERR_POOL_EXHAUSTED,
    ERR_UNSUPPORTED_FORMAT, OK,
};
use super::ffi::{
    mattmc_audio_buffer_create, mattmc_audio_buffer_destroy, mattmc_audio_device_create,
    mattmc_audio_device_destroy, mattmc_audio_format_to_openal, mattmc_audio_source_create,
    mattmc_audio_source_destroy, mattmc_audio_source_queue_stream_buffer,
    mattmc_audio_source_state,
};
use super::format::audio_format_to_openal;
use super::handles::HandleTable;
use super::listener::ListenerTransform;
use super::source::AL_STOPPED;
use super::{context::load_openal, errors::AudioResult};

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
fn handle_table_rejects_removed_handles() {
    let mut table = HandleTable::default();
    let handle = table.insert("device");
    assert_eq!(Some(&"device"), table.get(handle));
    assert_eq!(Some("device"), table.remove(handle));
    assert_eq!(None, table.get(handle));
    assert_eq!(0, table.len());
}

#[test]
fn stale_handles_after_backend_reset_fail_safely() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");
        test_support::reset_backend_for_tests();

        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(source)
        );
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::device_pool_counts(device)
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
fn device_cleanup_removes_owned_sources_and_buffers() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Static)
            .expect("static source should be created");
        let pcm = [0_u8, 0, 0, 0];
        let buffer = backend::create_buffer_handle(device, &pcm, 1, 16, true, 44_100)
            .expect("buffer should be created");

        let before = test_support::counts_for_tests();
        assert_eq!(1, before.devices);
        assert_eq!(1, before.sources);
        assert_eq!(1, before.buffers);

        backend::destroy_device(device).expect("device destroy should clean owned handles");
        let after = test_support::counts_for_tests();
        assert_eq!(0, after.devices);
        assert_eq!(0, after.sources);
        assert_eq!(0, after.buffers);
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::source_state(source)
        );
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::destroy_buffer(buffer)
        );
    });
}

#[test]
fn streaming_queue_ownership_is_released_with_source() {
    with_audio_backend(|| {
        let device = create_test_device().expect("default audio device should open");
        let source = backend::create_source(device, ChannelPool::Streaming)
            .expect("streaming source should be created");
        let pcm = [0_u8, 0, 0, 0, 0, 0, 0, 0];
        backend::queue_stream_buffer(source, &pcm, 1, 16, true, 44_100)
            .expect("streaming buffer should queue");
        let processed = backend::remove_processed_stream_buffers(source)
            .expect("processed buffers should read");
        assert!(processed >= 0);
        backend::destroy_source(source).expect("streaming source should drop queued buffers");
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
        backend::listener_reset(device).expect("listener reset should update");
        backend::destroy_device(device).expect("device should destroy");
        assert_eq!(
            Err(AudioError::InvalidHandle),
            backend::listener_reset(device)
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
    assert_eq!(ERR_INVALID_HANDLE, AudioError::InvalidHandle.status());
    assert_eq!(ERR_INVALID_ARGUMENT, AudioError::InvalidArgument.status());
    assert_eq!(ERR_POOL_EXHAUSTED, AudioError::PoolExhausted.status());
    assert_eq!(
        ERR_UNSUPPORTED_FORMAT,
        AudioError::UnsupportedFormat.status()
    );
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
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_buffer_destroy(0xdead_beef)
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_buffer_create(0, std::ptr::null(), 1, 1, 16, 1, 44_100, &mut handle)
        });
    });
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
fn ffi_invalid_source_state_fails_safely() {
    with_audio_backend(|| {
        let mut state = 0_i32;
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_source_state(42, &mut state)
        });
        assert_eq!(AL_STOPPED, state);
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_source_state(42, std::ptr::null_mut())
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
        let mut source = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_source_create(device, 0, &mut source)
        });
        assert_eq!(OK, unsafe { mattmc_audio_source_destroy(source) });
        assert_eq!(ERR_INVALID_HANDLE, unsafe {
            mattmc_audio_source_destroy(source)
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
        let mut source = 0_u64;
        assert_eq!(OK, unsafe {
            mattmc_audio_source_create(device, 1, &mut source)
        });
        assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
            mattmc_audio_source_queue_stream_buffer(source, std::ptr::null(), 4, 1, 16, 1, 44_100)
        });

        let pcm = [0_u8, 0, 0, 0];
        assert_eq!(OK, unsafe {
            mattmc_audio_source_queue_stream_buffer(
                source,
                pcm.as_ptr(),
                pcm.len() as u64,
                1,
                16,
                1,
                44_100,
            )
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
