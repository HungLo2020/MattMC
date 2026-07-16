use alto::{Context, ContextAttrs, DeviceObject, DistanceModel, OutputDevice};
use std::thread::{self, ThreadId};

use super::context::{alto_call, cstring_to_string, NativeAlto};
use super::errors::{AudioError, AudioResult};

pub(crate) const DEFAULT_CHANNEL_COUNT: i32 = 30;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ChannelPool {
    Static,
    Streaming,
}

impl ChannelPool {
    pub(crate) fn from_id(id: i32) -> Option<Self> {
        match id {
            0 => Some(Self::Static),
            1 => Some(Self::Streaming),
            _ => None,
        }
    }
}

pub(crate) struct NativeDevice {
    pub(crate) device: OutputDevice,
    pub(crate) context: Context,
    pub(crate) static_limit: usize,
    pub(crate) streaming_limit: usize,
    pub(crate) static_used: usize,
    pub(crate) streaming_used: usize,
    pub(crate) default_device_name: Option<String>,
    pub(crate) current_device_name: String,
    owner_thread: ThreadId,
}

impl NativeDevice {
    pub(crate) fn open(
        alto: &NativeAlto,
        preferred: Option<String>,
        hrtf: bool,
    ) -> AudioResult<Self> {
        let default_device_name = alto.0.default_output().map(cstring_to_string);
        let mut last_error = None;
        for candidate in 0..3 {
            let candidate = match candidate {
                0 => preferred.as_deref(),
                1 => default_device_name.as_deref(),
                _ => None,
            };
            match open_candidate(alto, candidate, hrtf) {
                Ok((device, context)) => {
                    let current_device_name = device
                        .specifier()
                        .map(|name| name.to_string_lossy().into_owned())
                        .or_else(|| default_device_name.clone())
                        .unwrap_or_else(|| "Unknown".to_string());
                    let (static_limit, streaming_limit) =
                        split_channel_counts(DEFAULT_CHANNEL_COUNT);
                    context.set_distance_model(DistanceModel::InverseClamped);
                    return Ok(Self {
                        device,
                        context,
                        static_limit,
                        streaming_limit,
                        static_used: 0,
                        streaming_used: 0,
                        default_device_name,
                        current_device_name,
                        owner_thread: thread::current().id(),
                    });
                }
                Err(error) => last_error = Some(error),
            }
        }
        Err(last_error.unwrap_or_else(|| {
            AudioError::OpenAlCall(
                "Open device",
                "failed to open preferred, default, or implicit device".to_string(),
            )
        }))
    }

    pub(crate) fn acquire_pool(&mut self, pool: ChannelPool) -> bool {
        match pool {
            ChannelPool::Static if self.static_used < self.static_limit => {
                self.static_used += 1;
                true
            }
            ChannelPool::Streaming if self.streaming_used < self.streaming_limit => {
                self.streaming_used += 1;
                true
            }
            _ => false,
        }
    }

    pub(crate) fn release_pool(&mut self, pool: ChannelPool) {
        match pool {
            ChannelPool::Static => self.static_used = self.static_used.saturating_sub(1),
            ChannelPool::Streaming => self.streaming_used = self.streaming_used.saturating_sub(1),
        }
    }

    pub(crate) fn is_disconnected(&self) -> bool {
        self.device
            .connected()
            .map(|connected| !connected)
            .unwrap_or(false)
    }

    pub(crate) fn ensure_owner_thread(&self) -> AudioResult<()> {
        if self.owner_thread == thread::current().id() {
            Ok(())
        } else {
            Err(AudioError::WrongThread)
        }
    }
}

fn open_candidate(
    alto: &NativeAlto,
    candidate: Option<&str>,
    hrtf: bool,
) -> AudioResult<(OutputDevice, Context)> {
    let spec = candidate
        .map(std::ffi::CString::new)
        .transpose()
        .map_err(|_| AudioError::InvalidArgument)?;
    let device = alto_call("Open device", alto.0.open(spec.as_deref()))?;
    let attrs = ContextAttrs {
        soft_hrtf: Some(hrtf),
        soft_hrtf_id: Some(0),
        soft_output_limiter: Some(true),
        ..Default::default()
    };
    let context = alto_call("Create context", device.new_context(Some(attrs)))?;
    Ok((device, context))
}

pub(crate) fn split_channel_counts(total: i32) -> (usize, usize) {
    let total = if total > 0 {
        total
    } else {
        DEFAULT_CHANNEL_COUNT
    };
    let streaming = ((total as f32).sqrt() as i32).clamp(2, 8);
    let static_count = (total - streaming).clamp(8, 255);
    (static_count as usize, streaming as usize)
}
