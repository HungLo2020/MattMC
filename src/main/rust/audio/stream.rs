use alto::{Buffer, StreamingSource};

use super::context::alto_call;
use super::errors::AudioResult;

/// Transfers ownership of a freshly created OpenAL buffer into a streaming
/// source queue.
///
/// The Java stream and decoded PCM bytes remain Java-owned. By the time this
/// function is called, Rust has already copied those bytes into `buffer`; after
/// queueing, OpenAL owns playback of that buffer until it is processed and
/// unqueued.
pub(crate) fn queue_buffer(source: &mut StreamingSource, buffer: Buffer) -> AudioResult<()> {
    alto_call("Queue streaming buffer", source.queue_buffer(buffer))
}

/// Unqueues all buffers OpenAL reports as processed and drops them on the Rust
/// side, returning the number of Java stream chunks that may be refilled.
pub(crate) fn remove_processed_buffers(source: &mut StreamingSource) -> AudioResult<i32> {
    let processed = source.buffers_processed().max(0);
    for _ in 0..processed {
        alto_call("Unqueue streaming buffer", source.unqueue_buffer())?;
    }
    Ok(processed)
}
