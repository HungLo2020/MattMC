use std::io::{Read, Seek, SeekFrom};
use std::sync::Arc;

use lewton::inside_ogg::OggStreamReader;
use lewton::samples::InterleavedSamples;

use super::decoder::{final_ogg_granule_position, java_i16_from_float};
use super::errors::{AudioError, AudioResult};

pub(crate) const STREAM_CHUNK_FRAMES: usize = 16_384;
pub(crate) const STREAM_QUEUE_DEPTH: usize = 4;
const MAX_EMPTY_PACKETS: usize = 64;

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct StreamChunk {
    pub(crate) samples: Vec<i16>,
    pub(crate) sample_rate: u32,
    pub(crate) channels: u16,
    pub(crate) frames: usize,
}

pub(crate) struct StreamDecoder {
    encoded: Arc<[u8]>,
    reader: OggStreamReader<SharedCursor>,
    sample_rate: u32,
    channels: u16,
    final_frames: Option<u64>,
    emitted_samples: usize,
    pending: Vec<i16>,
    packet_index: u64,
    eof: bool,
}

impl StreamDecoder {
    pub(crate) fn new(encoded: Arc<[u8]>) -> AudioResult<Self> {
        let final_frames = final_ogg_granule_position(&encoded)?;
        let reader = OggStreamReader::new(SharedCursor::new(encoded.clone()))
            .map_err(|error| AudioError::DecodeFailed(error.to_string()))?;
        let channels = reader.ident_hdr.audio_channels as u16;
        if channels == 0 || channels > 2 {
            return Err(AudioError::UnsupportedFormat);
        }
        let sample_rate = reader.ident_hdr.audio_sample_rate;
        if sample_rate == 0 {
            return Err(AudioError::UnsupportedFormat);
        }

        Ok(Self {
            encoded,
            reader,
            sample_rate,
            channels,
            final_frames,
            emitted_samples: 0,
            pending: Vec::new(),
            packet_index: 0,
            eof: false,
        })
    }

    pub(crate) fn restart(&mut self) -> AudioResult<()> {
        *self = Self::new(self.encoded.clone())?;
        Ok(())
    }

    pub(crate) fn read_chunk(&mut self, max_frames: usize) -> AudioResult<Option<StreamChunk>> {
        if max_frames == 0 {
            return Err(AudioError::InvalidArgument);
        }
        let max_samples = max_frames
            .checked_mul(self.channels as usize)
            .ok_or(AudioError::UnsupportedFormat)?;
        let mut empty_packets = 0_usize;

        while !self.eof && self.pending.len() < max_samples {
            let packet = self
                .reader
                .read_dec_packet_generic::<InterleavedSamples<f32>>()
                .map_err(|error| AudioError::DecodeFailed(error.to_string()))?;
            let Some(packet) = packet else {
                self.eof = true;
                break;
            };
            self.packet_index += 1;
            if packet.samples.is_empty() {
                empty_packets += 1;
                if empty_packets > MAX_EMPTY_PACKETS {
                    return Err(AudioError::DecodeFailed(
                        "stream decoder made no sample progress".to_string(),
                    ));
                }
                continue;
            }
            empty_packets = 0;

            let mut samples = packet
                .samples
                .into_iter()
                .map(java_i16_from_float)
                .collect::<Vec<_>>();
            if let Some(final_frames) = self.final_frames {
                let final_samples = usize::try_from(final_frames)
                    .ok()
                    .and_then(|frames| frames.checked_mul(self.channels as usize))
                    .ok_or(AudioError::UnsupportedFormat)?;
                if self.emitted_samples >= final_samples {
                    self.eof = true;
                    break;
                }
                let remaining = final_samples - self.emitted_samples;
                if samples.len() > remaining {
                    samples.truncate(remaining);
                    self.eof = true;
                }
            }
            self.emitted_samples = self
                .emitted_samples
                .checked_add(samples.len())
                .ok_or(AudioError::UnsupportedFormat)?;
            self.pending.extend(samples);
        }

        if self.pending.is_empty() {
            return Ok(None);
        }

        let take = self.pending.len().min(max_samples);
        let samples = self.pending.drain(..take).collect::<Vec<_>>();
        let frames = samples.len() / self.channels as usize;
        Ok(Some(StreamChunk {
            samples,
            sample_rate: self.sample_rate,
            channels: self.channels,
            frames,
        }))
    }

    #[cfg(test)]
    pub(crate) fn is_eof(&self) -> bool {
        self.eof && self.pending.is_empty()
    }

    #[cfg(test)]
    pub(crate) fn packet_index(&self) -> u64 {
        self.packet_index
    }
}

#[derive(Clone)]
struct SharedCursor {
    data: Arc<[u8]>,
    position: u64,
}

impl SharedCursor {
    fn new(data: Arc<[u8]>) -> Self {
        Self { data, position: 0 }
    }
}

impl Read for SharedCursor {
    fn read(&mut self, output: &mut [u8]) -> std::io::Result<usize> {
        let start = usize::try_from(self.position).unwrap_or(usize::MAX);
        if start >= self.data.len() {
            return Ok(0);
        }
        let available = &self.data[start..];
        let len = available.len().min(output.len());
        output[..len].copy_from_slice(&available[..len]);
        self.position += len as u64;
        Ok(len)
    }
}

impl Seek for SharedCursor {
    fn seek(&mut self, position: SeekFrom) -> std::io::Result<u64> {
        let len = self.data.len() as i128;
        let next = match position {
            SeekFrom::Start(offset) => offset as i128,
            SeekFrom::End(offset) => len + offset as i128,
            SeekFrom::Current(offset) => self.position as i128 + offset as i128,
        };
        if next < 0 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                "seek before start of audio asset",
            ));
        }
        self.position = next as u64;
        Ok(self.position)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const MONO_OGG: &[u8] =
        include_bytes!("../../resources/assets/minecraft/sounds/random/pop.ogg");
    const STEREO_OGG: &[u8] =
        include_bytes!("../../resources/assets/minecraft/sounds/music/game/dry_hands.ogg");

    #[test]
    fn incremental_decoder_reads_mono_to_eof_with_granule_trim() {
        let mut decoder =
            StreamDecoder::new(Arc::<[u8]>::from(MONO_OGG)).expect("mono should open");
        let mut frames = 0_usize;
        while let Some(chunk) = decoder.read_chunk(256).expect("chunk should decode") {
            assert_eq!(1, chunk.channels);
            assert!(chunk.frames <= 256);
            frames += chunk.frames;
        }
        assert_eq!(7542, frames);
        assert!(decoder.is_eof());
        assert!(decoder.packet_index() > 0);
    }

    #[test]
    fn incremental_decoder_reads_stereo_interleaved_chunks() {
        let mut decoder =
            StreamDecoder::new(Arc::<[u8]>::from(STEREO_OGG)).expect("stereo should open");
        let chunk = decoder
            .read_chunk(1024)
            .expect("decode should succeed")
            .expect("first chunk should exist");
        assert_eq!(2, chunk.channels);
        assert_eq!(chunk.frames * 2, chunk.samples.len());
        assert!(chunk.sample_rate > 0);
    }

    #[test]
    fn decoder_restart_rewinds_independent_cursor() {
        let mut decoder =
            StreamDecoder::new(Arc::<[u8]>::from(MONO_OGG)).expect("mono should open");
        let first = decoder
            .read_chunk(128)
            .expect("decode should succeed")
            .expect("first chunk should exist");
        decoder.restart().expect("restart should succeed");
        let restarted = decoder
            .read_chunk(128)
            .expect("decode should succeed")
            .expect("restarted chunk should exist");
        assert_eq!(first.samples, restarted.samples);
    }

    #[test]
    fn malformed_decoder_fails_cleanly() {
        assert!(matches!(
            StreamDecoder::new(Arc::<[u8]>::from(&b"not ogg"[..])),
            Err(AudioError::DecodeFailed(_))
        ));
    }
}
