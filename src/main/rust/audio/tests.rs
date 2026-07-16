use super::buffer::audio_format_to_openal;
use super::context::load_openal;
use super::device::{split_channel_counts, NativeDevice};
use super::errors::{
    AudioError, ERR_INVALID_ARGUMENT, ERR_INVALID_HANDLE, ERR_POOL_EXHAUSTED,
    ERR_UNSUPPORTED_FORMAT,
};
use super::ffi::{
    mattmc_audio_buffer_create, mattmc_audio_buffer_destroy, mattmc_audio_device_create,
    mattmc_audio_format_to_openal, mattmc_audio_source_state, test_support,
};
use super::handles::HandleTable;
use super::source::AL_STOPPED;

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
fn channel_pool_split_matches_java_limits() {
    assert_eq!((25, 5), split_channel_counts(30));
    assert_eq!((8, 2), split_channel_counts(1));
    assert_eq!((255, 8), split_channel_counts(400));
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
    test_support::reset_backend_for_tests();

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
}

#[test]
fn ffi_format_mapping_reports_status_and_value() {
    let mut format = 0_i32;
    assert_eq!(0, unsafe {
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
    test_support::reset_backend_for_tests();

    let mut state = 0_i32;
    assert_eq!(ERR_INVALID_HANDLE, unsafe {
        mattmc_audio_source_state(42, &mut state)
    });
    assert_eq!(AL_STOPPED, state);
    assert_eq!(ERR_INVALID_ARGUMENT, unsafe {
        mattmc_audio_source_state(42, std::ptr::null_mut())
    });
}

#[test]
fn openal_soft_static_link_opens_default_device_and_context() {
    let alto = load_openal().expect("statically linked OpenAL Soft should initialize");
    let device = NativeDevice::open(&alto, None, false)
        .expect("statically linked OpenAL Soft should open the default output device");
    assert!(!device.current_device_name.is_empty());
}
