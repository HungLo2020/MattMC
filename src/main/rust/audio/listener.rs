use alto::Context;

use super::context::alto_call;
use super::errors::AudioResult;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct ListenerTransform {
    pub(crate) position: [f32; 3],
    pub(crate) forward: [f32; 3],
    pub(crate) up: [f32; 3],
}

/// Applies listener transform state to the device context owned by Rust.
///
/// Java keeps the gameplay/camera policy and sends the latest listener
/// transform over FFI. Rust owns only the OpenAL listener state bound to the
/// live context. Device destruction invalidates that context and all later
/// listener calls for the old handle fail as stale handles.
pub(crate) fn set_transform(context: &Context, transform: ListenerTransform) -> AudioResult<()> {
    alto_call(
        "Set listener position",
        context.set_position(transform.position),
    )?;
    alto_call(
        "Set listener orientation",
        context.set_orientation((transform.forward, transform.up)),
    )
}

pub(crate) fn set_gain(context: &Context, gain: f32) -> AudioResult<()> {
    alto_call("Set listener gain", context.set_gain(gain))
}
