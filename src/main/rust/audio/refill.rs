use alto::{Context, Source, SourceState, StreamingSource};

use super::buffer::create_buffer;
use super::errors::{AudioError, AudioResult};
use super::stream;
use super::stream_decoder::{StreamDecoder, STREAM_CHUNK_FRAMES, STREAM_QUEUE_DEPTH};

const MAX_LOOP_RESTARTS_PER_REFILL: usize = STREAM_QUEUE_DEPTH;

pub(crate) struct StreamingPlayback {
    asset_handle: u64,
    decoder: StreamDecoder,
    looping: bool,
    eof: bool,
    decode_failed: bool,
    decoded_chunks: u64,
    loop_restarts: u64,
}

impl StreamingPlayback {
    pub(crate) fn new(asset_handle: u64, decoder: StreamDecoder, looping: bool) -> Self {
        Self {
            asset_handle,
            decoder,
            looping,
            eof: false,
            decode_failed: false,
            decoded_chunks: 0,
            loop_restarts: 0,
        }
    }

    pub(crate) fn asset_handle(&self) -> u64 {
        self.asset_handle
    }

    pub(crate) fn decoded_chunks(&self) -> u64 {
        self.decoded_chunks
    }

    #[cfg(test)]
    pub(crate) fn loop_restarts(&self) -> u64 {
        self.loop_restarts
    }

    #[cfg(test)]
    pub(crate) fn decode_failed(&self) -> bool {
        self.decode_failed
    }

    pub(crate) fn remove_processed(
        &mut self,
        source: &mut StreamingSource,
        queued_buffers: &mut i32,
    ) -> AudioResult<i32> {
        let processed = stream::remove_processed_buffers(source)?;
        *queued_buffers = queued_buffers.saturating_sub(processed);
        Ok(processed)
    }

    pub(crate) fn refill(
        &mut self,
        context: &Context,
        source: &mut StreamingSource,
        queued_buffers: &mut i32,
    ) -> AudioResult<()> {
        if self.decode_failed || self.eof {
            return Ok(());
        }

        let mut loop_restarts_this_refill = 0_usize;
        while (*queued_buffers as usize) < STREAM_QUEUE_DEPTH {
            match self.decoder.read_chunk(STREAM_CHUNK_FRAMES) {
                Ok(Some(chunk)) => {
                    if chunk.frames == 0 {
                        return Err(AudioError::DecodeFailed(
                            "stream decoder returned an empty chunk".to_string(),
                        ));
                    }
                    let bytes = samples_to_le_bytes(&chunk.samples);
                    let buffer = create_buffer(
                        context,
                        &bytes,
                        chunk.channels as i32,
                        16,
                        true,
                        chunk.sample_rate as i32,
                    )?;
                    stream::queue_buffer(source, buffer)?;
                    *queued_buffers += 1;
                    self.decoded_chunks += 1;
                }
                Ok(None) => {
                    if self.looping {
                        loop_restarts_this_refill += 1;
                        if loop_restarts_this_refill > MAX_LOOP_RESTARTS_PER_REFILL {
                            return Err(AudioError::DecodeFailed(
                                "looping stream made no refill progress".to_string(),
                            ));
                        }
                        self.decoder.restart()?;
                        self.loop_restarts += 1;
                        continue;
                    }
                    self.eof = true;
                    break;
                }
                Err(error) => {
                    self.decode_failed = true;
                    return Err(error);
                }
            }
        }
        Ok(())
    }

    pub(crate) fn tick(
        &mut self,
        context: &Context,
        source: &mut StreamingSource,
        queued_buffers: &mut i32,
    ) -> AudioResult<()> {
        self.remove_processed(source, queued_buffers)?;
        match source.state() {
            SourceState::Paused => Ok(()),
            SourceState::Stopped if self.eof && *queued_buffers == 0 => Ok(()),
            _ => self.refill(context, source, queued_buffers),
        }
    }

    pub(crate) fn finished(&self, queued_buffers: i32) -> bool {
        self.eof && queued_buffers == 0
    }
}

fn samples_to_le_bytes(samples: &[i16]) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(samples.len() * 2);
    for sample in samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}
