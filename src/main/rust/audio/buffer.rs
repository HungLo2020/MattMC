use alto::{Buffer, Context, Mono, Stereo};

use super::context::alto_call;
use super::errors::{AudioError, AudioResult};
use super::format::audio_format_to_openal;

pub(crate) fn create_buffer(
    context: &Context,
    bytes: &[u8],
    channels: i32,
    bits: i32,
    pcm: bool,
    sample_rate: i32,
) -> AudioResult<Buffer> {
    audio_format_to_openal(channels, bits, pcm)?;
    if sample_rate <= 0 {
        return Err(AudioError::UnsupportedFormat);
    }

    match (channels, bits) {
        (1, 8) => {
            let samples = bytes
                .iter()
                .map(|sample| Mono { center: *sample })
                .collect::<Vec<_>>();
            alto_call(
                "Create mono 8-bit buffer",
                context.new_buffer(samples, sample_rate),
            )
        }
        (1, 16) => {
            let samples = read_i16_samples(bytes)?
                .into_iter()
                .map(|sample| Mono { center: sample })
                .collect::<Vec<_>>();
            alto_call(
                "Create mono 16-bit buffer",
                context.new_buffer(samples, sample_rate),
            )
        }
        (2, 8) => {
            if bytes.len() % 2 != 0 {
                return Err(AudioError::UnsupportedFormat);
            }
            let samples = bytes
                .chunks_exact(2)
                .map(|sample| Stereo {
                    left: sample[0],
                    right: sample[1],
                })
                .collect::<Vec<_>>();
            alto_call(
                "Create stereo 8-bit buffer",
                context.new_buffer(samples, sample_rate),
            )
        }
        (2, 16) => {
            let raw = read_i16_samples(bytes)?;
            if raw.len() % 2 != 0 {
                return Err(AudioError::UnsupportedFormat);
            }
            let samples = raw
                .chunks_exact(2)
                .map(|sample| Stereo {
                    left: sample[0],
                    right: sample[1],
                })
                .collect::<Vec<_>>();
            alto_call(
                "Create stereo 16-bit buffer",
                context.new_buffer(samples, sample_rate),
            )
        }
        _ => Err(AudioError::UnsupportedFormat),
    }
}

fn read_i16_samples(bytes: &[u8]) -> AudioResult<Vec<i16>> {
    if bytes.len() % 2 != 0 {
        return Err(AudioError::UnsupportedFormat);
    }
    Ok(bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect())
}
