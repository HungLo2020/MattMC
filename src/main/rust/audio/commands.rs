/// Source configuration shared by static and streaming sound creation/update.
///
/// The Java boundary treats this as a compact `#[repr(C)]` record. Coordinates
/// use Minecraft/OpenAL world units, gain and pitch are already policy-adjusted
/// by Java, and attenuation flags encode which distance model Rust applies to
/// the owned source.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct SoundConfigRecord {
    pub(crate) x: f32,
    pub(crate) y: f32,
    pub(crate) z: f32,
    pub(crate) pitch: f32,
    pub(crate) gain: f32,
    pub(crate) attenuation_distance: f32,
    pub(crate) flags: u32,
}

impl Default for SoundConfigRecord {
    fn default() -> Self {
        Self {
            x: 0.0,
            y: 0.0,
            z: 0.0,
            pitch: 1.0,
            gain: 1.0,
            attenuation_distance: 0.0,
            flags: 0,
        }
    }
}

pub(crate) const SOUND_FLAG_LOOPING: u32 = 1 << 0;
pub(crate) const SOUND_FLAG_RELATIVE: u32 = 1 << 1;
pub(crate) const SOUND_FLAG_DISABLE_ATTENUATION: u32 = 1 << 2;
pub(crate) const SOUND_FLAG_LINEAR_ATTENUATION: u32 = 1 << 3;

pub(crate) const SOUND_UPDATE_POSITION: u32 = 1 << 0;
pub(crate) const SOUND_UPDATE_PITCH: u32 = 1 << 1;
pub(crate) const SOUND_UPDATE_GAIN: u32 = 1 << 2;
pub(crate) const SOUND_UPDATE_LOOPING: u32 = 1 << 3;
pub(crate) const SOUND_UPDATE_RELATIVE: u32 = 1 << 4;
pub(crate) const SOUND_UPDATE_ATTENUATION: u32 = 1 << 5;
pub(crate) const SOUND_UPDATE_ALL: u32 = SOUND_UPDATE_POSITION
    | SOUND_UPDATE_PITCH
    | SOUND_UPDATE_GAIN
    | SOUND_UPDATE_LOOPING
    | SOUND_UPDATE_RELATIVE
    | SOUND_UPDATE_ATTENUATION;

/// One Java-decoded PCM chunk submitted as part of a streaming batch.
///
/// Java owns the pointed-to bytes. Rust copies each chunk into an OpenAL buffer
/// during the FFI call, then owns the OpenAL queue entry until it is processed
/// or the sound/source/device is destroyed.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct StreamChunkRecord {
    pub(crate) data_ptr: *const u8,
    pub(crate) data_len: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ListenerStateRecord {
    pub(crate) position: [f32; 3],
    pub(crate) forward: [f32; 3],
    pub(crate) up: [f32; 3],
    pub(crate) gain: f32,
}

/// Result of shadow static decode parity between Java JOrbis PCM and Rust
/// asset decoding.
///
/// Rust writes this compact record instead of copying Rust-decoded PCM back to
/// Java. `rust_status` is `OK` when Rust decoded the asset; otherwise the other
/// Rust fields stay zero and the status explains the decoder failure.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct StaticDecodeParityRecord {
    pub(crate) java_byte_count: u64,
    pub(crate) rust_byte_count: u64,
    pub(crate) java_frame_count: u64,
    pub(crate) rust_frame_count: u64,
    pub(crate) first_differing_sample: i64,
    pub(crate) mismatch_count: u64,
    pub(crate) rust_status: i32,
    pub(crate) format_match: i32,
    pub(crate) exact_pcm_match: i32,
    pub(crate) max_abs_sample_delta: i32,
    pub(crate) java_sample_rate: i32,
    pub(crate) rust_sample_rate: i32,
    pub(crate) java_channels: i32,
    pub(crate) rust_channels: i32,
    pub(crate) first_differing_frame: i64,
    pub(crate) first_differing_channel: i32,
    pub(crate) first_differing_packet: i32,
    pub(crate) first_java_sample: i32,
    pub(crate) first_rust_sample: i32,
}

#[cfg(test)]
mod tests {
    use std::mem::{align_of, size_of};

    use super::*;

    #[test]
    fn command_records_have_stable_c_layout() {
        assert_eq!(28, size_of::<SoundConfigRecord>());
        assert_eq!(4, align_of::<SoundConfigRecord>());
        assert_eq!(16, size_of::<StreamChunkRecord>());
        assert_eq!(8, align_of::<StreamChunkRecord>());
        assert_eq!(40, size_of::<ListenerStateRecord>());
        assert_eq!(4, align_of::<ListenerStateRecord>());
        assert_eq!(104, size_of::<StaticDecodeParityRecord>());
        assert_eq!(8, align_of::<StaticDecodeParityRecord>());
    }
}
