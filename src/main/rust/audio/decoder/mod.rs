mod vorbis;

use super::commands::StaticDecodeParityRecord;
use super::errors::{AudioError, AudioResult, OK};

pub(crate) use vorbis::decode_vorbis;
pub(crate) use vorbis::final_ogg_granule_position;
pub(crate) use vorbis::java_i16_from_float;

pub(crate) const MAX_DECODED_PCM_BYTES: usize = 256 * 1024 * 1024;

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct DecodedPcm {
    pub(crate) samples: Vec<i16>,
    pub(crate) sample_rate: u32,
    pub(crate) channels: u16,
    pub(crate) frames: u64,
}

pub(crate) fn compare_static_pcm(
    encoded: &[u8],
    java_pcm: &[u8],
    java_channels: i32,
    java_bits: i32,
    java_pcm_flag: bool,
    java_sample_rate: i32,
) -> AudioResult<StaticDecodeParityRecord> {
    if java_channels <= 0
        || java_sample_rate <= 0
        || java_bits != 16
        || !java_pcm_flag
        || java_pcm.len() > MAX_DECODED_PCM_BYTES
    {
        return Err(AudioError::InvalidArgument);
    }

    let java_sample_size = usize::try_from(java_channels)
        .ok()
        .and_then(|channels| channels.checked_mul(2))
        .ok_or(AudioError::InvalidArgument)?;
    if java_sample_size == 0 || java_pcm.len() % java_sample_size != 0 {
        return Err(AudioError::InvalidArgument);
    }

    let java_samples = read_le_i16_samples(java_pcm)?;
    let java_frame_count = (java_samples.len() / java_channels as usize) as u64;
    let mut record = StaticDecodeParityRecord {
        rust_status: OK,
        java_byte_count: java_pcm.len() as u64,
        java_frame_count,
        java_sample_rate,
        java_channels,
        first_differing_sample: -1,
        first_differing_frame: -1,
        first_differing_channel: -1,
        first_differing_packet: -1,
        ..StaticDecodeParityRecord::default()
    };

    let rust = match decode_vorbis(encoded) {
        Ok(decoded) => decoded,
        Err(error) => {
            record.rust_status = error.status();
            return Ok(record);
        }
    };

    record.rust_byte_count = (rust.samples.len() * 2) as u64;
    record.rust_frame_count = rust.frames;
    record.rust_sample_rate = rust.sample_rate as i32;
    record.rust_channels = rust.channels as i32;
    record.format_match = i32::from(
        record.java_sample_rate == record.rust_sample_rate
            && record.java_channels == record.rust_channels,
    );

    compare_samples(
        encoded,
        &java_samples,
        &rust.samples,
        rust.channels,
        &mut record,
    );
    Ok(record)
}

fn compare_samples(
    encoded: &[u8],
    java: &[i16],
    rust: &[i16],
    channels: u16,
    record: &mut StaticDecodeParityRecord,
) {
    let mut mismatch_count = 0_u64;
    let mut max_delta = 0_i32;
    let min_len = java.len().min(rust.len());
    for index in 0..min_len {
        if java[index] != rust[index] {
            if record.first_differing_sample < 0 {
                record.first_differing_sample = index as i64;
                record.first_differing_frame = (index / channels as usize) as i64;
                record.first_differing_channel = (index % channels as usize) as i32;
                record.first_java_sample = java[index] as i32;
                record.first_rust_sample = rust[index] as i32;
                record.first_differing_packet = vorbis::packet_index_for_sample(encoded, index)
                    .ok()
                    .flatten()
                    .and_then(|packet| i32::try_from(packet).ok())
                    .unwrap_or(-1);
            }
            mismatch_count += 1;
            let delta = (java[index] as i32 - rust[index] as i32).abs();
            max_delta = max_delta.max(delta);
        }
    }
    if java.len() != rust.len() {
        if record.first_differing_sample < 0 {
            record.first_differing_sample = min_len as i64;
            record.first_differing_frame = (min_len / channels as usize) as i64;
            record.first_differing_channel = (min_len % channels as usize) as i32;
            record.first_differing_packet = -1;
        }
        mismatch_count += java.len().abs_diff(rust.len()) as u64;
    }
    record.max_abs_sample_delta = max_delta;
    record.mismatch_count = mismatch_count;
    record.exact_pcm_match = i32::from(mismatch_count == 0);
}

fn read_le_i16_samples(bytes: &[u8]) -> AudioResult<Vec<i16>> {
    if bytes.len() % 2 != 0 {
        return Err(AudioError::InvalidArgument);
    }
    Ok(bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    const MONO_OGG: &[u8] =
        include_bytes!("../../../resources/assets/minecraft/sounds/random/pop.ogg");
    const STEREO_OGG: &[u8] =
        include_bytes!("../../../resources/assets/minecraft/sounds/music/game/dry_hands.ogg");

    #[test]
    fn decodes_known_mono_vorbis_fixture() {
        let decoded = decode_vorbis(MONO_OGG).expect("mono fixture should decode");
        assert_eq!(1, decoded.channels);
        assert!(decoded.sample_rate > 0);
        assert_eq!(decoded.frames as usize, decoded.samples.len());
        assert_eq!(7542, decoded.frames);
        assert!(!decoded.samples.is_empty());
    }

    #[test]
    fn decodes_known_stereo_vorbis_fixture_and_interleaves_pcm() {
        let decoded = decode_vorbis(STEREO_OGG).expect("stereo fixture should decode");
        assert_eq!(2, decoded.channels);
        assert!(decoded.sample_rate > 0);
        assert_eq!(decoded.frames as usize * 2, decoded.samples.len());
        assert!(!decoded.samples.is_empty());
    }

    #[test]
    fn rejects_malformed_and_truncated_vorbis() {
        assert!(matches!(
            decode_vorbis(b"not ogg"),
            Err(AudioError::DecodeFailed(_))
        ));
        let truncated = &MONO_OGG[..MONO_OGG.len().min(96)];
        assert!(decode_vorbis(truncated).is_err());
    }

    #[test]
    fn exact_parity_success_reports_matching_format_and_counts() {
        let decoded = decode_vorbis(MONO_OGG).expect("mono fixture should decode");
        let java_pcm = samples_to_le_bytes(&decoded.samples);
        let record = compare_static_pcm(
            MONO_OGG,
            &java_pcm,
            decoded.channels as i32,
            16,
            true,
            decoded.sample_rate as i32,
        )
        .expect("parity comparison should run");

        assert_eq!(OK, record.rust_status);
        assert_eq!(1, record.format_match);
        assert_eq!(1, record.exact_pcm_match);
        assert_eq!(java_pcm.len() as u64, record.java_byte_count);
        assert_eq!(java_pcm.len() as u64, record.rust_byte_count);
        assert_eq!(decoded.frames, record.java_frame_count);
        assert_eq!(decoded.frames, record.rust_frame_count);
        assert_eq!(-1, record.first_differing_sample);
        assert_eq!(-1, record.first_differing_frame);
        assert_eq!(-1, record.first_differing_channel);
        assert_eq!(-1, record.first_differing_packet);
        assert_eq!(0, record.max_abs_sample_delta);
        assert_eq!(0, record.mismatch_count);
    }

    #[test]
    fn parity_mismatch_reports_first_sample_delta_and_count() {
        let decoded = decode_vorbis(MONO_OGG).expect("mono fixture should decode");
        let mut java_samples = decoded.samples.clone();
        java_samples[3] = java_samples[3].wrapping_add(7);
        java_samples.push(123);
        let java_pcm = samples_to_le_bytes(&java_samples);

        let record = compare_static_pcm(
            MONO_OGG,
            &java_pcm,
            decoded.channels as i32,
            16,
            true,
            decoded.sample_rate as i32,
        )
        .expect("parity comparison should run");

        assert_eq!(OK, record.rust_status);
        assert_eq!(0, record.exact_pcm_match);
        assert_eq!(3, record.first_differing_sample);
        assert_eq!(3, record.first_differing_frame);
        assert_eq!(0, record.first_differing_channel);
        assert!(record.first_differing_packet >= 0);
        assert_eq!(java_samples[3] as i32, record.first_java_sample);
        assert_eq!(decoded.samples[3] as i32, record.first_rust_sample);
        assert!(record.max_abs_sample_delta > 0);
        assert_eq!(2, record.mismatch_count);
    }

    #[test]
    fn parity_reports_rust_decode_error_without_copying_pcm_back() {
        let record = compare_static_pcm(b"bad", &[0, 0, 0, 0], 1, 16, true, 44_100)
            .expect("boundary arguments are valid");
        assert_ne!(OK, record.rust_status);
        assert_eq!(0, record.format_match);
        assert_eq!(0, record.exact_pcm_match);
    }

    #[test]
    fn parity_rejects_invalid_java_format_and_size() {
        assert_eq!(
            Err(AudioError::InvalidArgument),
            compare_static_pcm(MONO_OGG, &[0, 0], 6, 16, true, 44_100)
        );
        assert_eq!(
            Err(AudioError::InvalidArgument),
            compare_static_pcm(MONO_OGG, &[0, 0], 1, 8, true, 44_100)
        );
        assert_eq!(
            Err(AudioError::InvalidArgument),
            compare_static_pcm(MONO_OGG, &[0, 0], 1, 16, false, 44_100)
        );
    }

    fn samples_to_le_bytes(samples: &[i16]) -> Vec<u8> {
        let mut bytes = Vec::with_capacity(samples.len() * 2);
        for sample in samples {
            bytes.extend_from_slice(&sample.to_le_bytes());
        }
        bytes
    }
}
