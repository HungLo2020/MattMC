use std::io::Cursor;

use lewton::inside_ogg::OggStreamReader;
use lewton::samples::InterleavedSamples;

use super::{DecodedPcm, MAX_DECODED_PCM_BYTES};
use crate::audio::errors::{AudioError, AudioResult};

pub(crate) fn decode_vorbis(encoded: &[u8]) -> AudioResult<DecodedPcm> {
    let final_granule = final_ogg_granule_position(encoded)?;
    let mut reader = OggStreamReader::new(Cursor::new(encoded))
        .map_err(|error| AudioError::DecodeFailed(error.to_string()))?;
    let channels = reader.ident_hdr.audio_channels as u16;
    if channels == 0 || channels > 2 {
        return Err(AudioError::UnsupportedFormat);
    }
    let sample_rate = reader.ident_hdr.audio_sample_rate;
    if sample_rate == 0 {
        return Err(AudioError::UnsupportedFormat);
    }

    let mut samples = Vec::new();
    while let Some(packet) = reader
        .read_dec_packet_generic::<InterleavedSamples<f32>>()
        .map_err(|error| AudioError::DecodeFailed(error.to_string()))?
    {
        let new_len = samples
            .len()
            .checked_add(packet.samples.len())
            .ok_or(AudioError::UnsupportedFormat)?;
        if new_len
            .checked_mul(std::mem::size_of::<i16>())
            .is_none_or(|bytes| bytes > MAX_DECODED_PCM_BYTES)
        {
            return Err(AudioError::UnsupportedFormat);
        }
        samples.extend(packet.samples.into_iter().map(java_i16_from_float));
    }

    if let Some(final_frames) = final_granule {
        let target_samples = usize::try_from(final_frames)
            .ok()
            .and_then(|frames| frames.checked_mul(channels as usize))
            .ok_or(AudioError::UnsupportedFormat)?;
        if target_samples < samples.len() {
            samples.truncate(target_samples);
        }
    }

    if samples.is_empty() || samples.len() % channels as usize != 0 {
        return Err(AudioError::UnsupportedFormat);
    }

    Ok(DecodedPcm {
        frames: (samples.len() / channels as usize) as u64,
        samples,
        sample_rate,
        channels,
    })
}

pub(crate) fn packet_index_for_sample(
    encoded: &[u8],
    sample_index: usize,
) -> AudioResult<Option<u64>> {
    let mut reader = OggStreamReader::new(Cursor::new(encoded))
        .map_err(|error| AudioError::DecodeFailed(error.to_string()))?;
    let mut first_packet_sample = 0_usize;
    let mut packet_index = 0_u64;
    while let Some(packet) = reader
        .read_dec_packet_generic::<InterleavedSamples<f32>>()
        .map_err(|error| AudioError::DecodeFailed(error.to_string()))?
    {
        let next_packet_sample = first_packet_sample
            .checked_add(packet.samples.len())
            .ok_or(AudioError::UnsupportedFormat)?;
        if sample_index < next_packet_sample {
            return Ok(Some(packet_index));
        }
        first_packet_sample = next_packet_sample;
        packet_index += 1;
    }
    Ok(None)
}

fn java_i16_from_float(sample: f32) -> i16 {
    let value = (sample * 32767.5 - 0.5) as i32;
    value.clamp(-32768, 32767) as i16
}

fn final_ogg_granule_position(encoded: &[u8]) -> AudioResult<Option<u64>> {
    let mut cursor = 0_usize;
    let mut final_granule = None;
    while let Some(relative) = encoded[cursor..]
        .windows(4)
        .position(|window| window == b"OggS")
    {
        let offset = cursor + relative;
        if offset + 27 > encoded.len() {
            return Err(AudioError::DecodeFailed(
                "truncated Ogg page header".to_string(),
            ));
        }
        let header_type = encoded[offset + 5];
        let granule = u64::from_le_bytes([
            encoded[offset + 6],
            encoded[offset + 7],
            encoded[offset + 8],
            encoded[offset + 9],
            encoded[offset + 10],
            encoded[offset + 11],
            encoded[offset + 12],
            encoded[offset + 13],
        ]);
        if header_type & 0x04 != 0 {
            final_granule = Some(granule);
        }
        cursor = offset + 4;
    }
    Ok(final_granule)
}
