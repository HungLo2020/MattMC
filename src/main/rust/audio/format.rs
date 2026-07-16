use super::errors::{AudioError, AudioResult};

/// Maps the Java `AudioFormat` fields supported by the current sound pipeline
/// to the OpenAL constants exposed through the native ABI.
///
/// Java still owns Ogg decoding and passes decoded PCM bytes to Rust. Rust only
/// accepts mono/stereo 8-bit and 16-bit PCM here because those are the formats
/// the existing Java OpenAL path handled directly.
pub(crate) fn audio_format_to_openal(channels: i32, bits: i32, pcm: bool) -> AudioResult<i32> {
    if !pcm {
        return Err(AudioError::UnsupportedFormat);
    }
    match (channels, bits) {
        (1, 8) => Ok(0x1100),
        (1, 16) => Ok(0x1101),
        (2, 8) => Ok(0x1102),
        (2, 16) => Ok(0x1103),
        _ => Err(AudioError::UnsupportedFormat),
    }
}
