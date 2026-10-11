//! The engine and its device-owner audio thread (`SoundEngineExecutor` +
//! `ChannelAccess`).
//!
//! OpenAL devices may only be touched by the thread that created them, so one
//! Rust thread owns the device and every voice. Callers lock the [`Policy`],
//! and its [`VoiceOp`]s are queued in order; device creation, teardown and
//! "release all" block until done. Stopped voices are detected once per device
//! tick and reported back to the policy, as `ChannelAccess.scheduleTick` does.

use std::collections::{HashMap, VecDeque};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{sync_channel, SyncSender};
use std::sync::{Condvar, LazyLock, Mutex, MutexGuard};
use std::time::{Duration, Instant};

use super::super::backend;
use super::super::commands::{
    SoundConfigRecord, SOUND_UPDATE_GAIN, SOUND_UPDATE_PITCH, SOUND_UPDATE_POSITION,
};
use super::super::errors::AudioError;
use super::super::listener::ListenerTransform;
use super::super::source::{AL_PAUSED, AL_PLAYING, AL_STOPPED};
use super::engine::{Policy, VoiceOp};

/// `DEFAULT_DEVICE_CHECK_INTERVAL_MS`.
const DEVICE_CHECK_INTERVAL: Duration = Duration::from_millis(1000);

pub(crate) const INITIAL_LISTENER: ListenerTransform = ListenerTransform {
    position: [0.0, 0.0, 0.0],
    forward: [0.0, 0.0, -1.0],
    up: [0.0, 1.0, 0.0],
};

enum Command {
    Ops(Vec<VoiceOp>),
    Init { preferred: Option<String>, hrtf: bool, reply: SyncSender<Result<(), String>> },
    ReleaseAll { reply: SyncSender<()> },
    Cleanup { reply: SyncSender<()> },
}

pub(crate) struct Engine {
    policy: Mutex<Policy>,
    queue: Mutex<VecDeque<Command>>,
    wake: Condvar,
    released: Mutex<Vec<u64>>,
    reload_needed: AtomicBool,
    pool_counts: Mutex<[i32; 4]>,
    started: std::sync::Once,
}

pub(crate) static ENGINE: LazyLock<Engine> = LazyLock::new(|| Engine {
    policy: Mutex::new(Policy::default()),
    queue: Mutex::new(VecDeque::new()),
    wake: Condvar::new(),
    released: Mutex::new(Vec::new()),
    reload_needed: AtomicBool::new(false),
    pool_counts: Mutex::new([0; 4]),
    started: std::sync::Once::new(),
});

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

impl Engine {
    fn ensure_thread(&'static self) {
        self.started.call_once(|| {
            std::thread::Builder::new()
                .name("Sound engine".into())
                .spawn(move || AudioThread::default().run(self))
                .expect("spawn the sound engine thread");
        });
    }

    fn push(&'static self, command: Command) {
        self.ensure_thread();
        lock(&self.queue).push_back(command);
        self.wake.notify_one();
    }

    /// Runs `f` on the policy, then queues the device work it produced.
    pub(crate) fn with_policy<R>(&'static self, f: impl FnOnce(&mut Policy) -> R) -> R {
        let mut policy = lock(&self.policy);
        for voice in std::mem::take(&mut *lock(&self.released)) {
            policy.voice_released(voice);
        }
        let result = f(&mut policy);
        let ops = policy.take_ops();
        if !ops.is_empty() {
            self.push(Command::Ops(ops));
        }
        result
    }

    /// Creates (or recreates) the device on the audio thread.
    pub(crate) fn init(&'static self, preferred: Option<String>, hrtf: bool) -> Result<(), String> {
        let (reply, result) = sync_channel(1);
        self.push(Command::Init { preferred, hrtf, reply });
        result.recv().unwrap_or_else(|_| Err("sound engine thread stopped".into()))
    }

    /// `stopAll`: drops queued work, releases every voice and forgets the policy.
    pub(crate) fn stop_all(&'static self) {
        let mut policy = lock(&self.policy);
        lock(&self.queue).retain(|command| !matches!(command, Command::Ops(_)));
        policy.clear();
        lock(&self.released).clear();
        let (reply, done) = sync_channel(1);
        self.push(Command::ReleaseAll { reply });
        drop(policy);
        let _ = done.recv();
    }

    /// Destroys the device (`Library.cleanup`).
    pub(crate) fn cleanup(&'static self) {
        let (reply, done) = sync_channel(1);
        self.push(Command::Cleanup { reply });
        let _ = done.recv();
    }

    pub(crate) fn take_reload_needed(&self) -> bool {
        self.reload_needed.swap(false, Ordering::AcqRel)
    }

    pub(crate) fn pool_counts(&self) -> [i32; 4] {
        *lock(&self.pool_counts)
    }
}

struct Voice {
    source: u64,
    config: SoundConfigRecord,
    stream: bool,
    stream_looping: bool,
    awaiting_attachment: bool,
    stop_requested: bool,
}

struct AudioThread {
    device: u64,
    voices: HashMap<u64, Voice>,
    preferred: Option<String>,
    default_name: Option<String>,
    last_device_check: Instant,
}

impl Default for AudioThread {
    fn default() -> Self {
        Self {
            device: 0,
            voices: HashMap::new(),
            preferred: None,
            default_name: backend::default_device_name().ok().filter(|name| !name.is_empty()),
            last_device_check: Instant::now(),
        }
    }
}

impl AudioThread {
    fn run(mut self, engine: &'static Engine) {
        loop {
            let command = {
                let mut queue = lock(&engine.queue);
                loop {
                    if let Some(command) = queue.pop_front() {
                        break command;
                    }
                    queue = engine.wake.wait(queue).unwrap_or_else(|poisoned| poisoned.into_inner());
                }
            };
            match command {
                Command::Ops(ops) => {
                    for op in ops {
                        self.execute(engine, op);
                    }
                }
                Command::Init { preferred, hrtf, reply } => {
                    let _ = reply.send(self.init(engine, preferred, hrtf));
                }
                Command::ReleaseAll { reply } => {
                    self.release_all();
                    let _ = reply.send(());
                }
                Command::Cleanup { reply } => {
                    self.cleanup(engine);
                    let _ = reply.send(());
                }
            }
        }
    }

    fn init(&mut self, engine: &Engine, preferred: Option<String>, hrtf: bool) -> Result<(), String> {
        if self.device != 0 {
            self.cleanup(engine);
        }
        self.preferred = preferred.clone();
        let device = backend::create_device(preferred, hrtf).map_err(|error| error.to_string())?;
        self.device = device;
        self.default_name = backend::default_device_name().ok().filter(|name| !name.is_empty());
        let _ = backend::listener_set_transform(device, INITIAL_LISTENER);
        let _ = backend::listener_set_gain(device, 1.0);
        self.refresh_pool_counts(engine);
        Ok(())
    }

    fn cleanup(&mut self, engine: &Engine) {
        self.release_all();
        if self.device != 0 {
            let _ = backend::destroy_device(self.device);
            self.device = 0;
        }
        *lock(&engine.pool_counts) = [0; 4];
    }

    fn release_all(&mut self) {
        for (_, voice) in self.voices.drain() {
            if voice.source != 0 {
                let _ = backend::destroy_source(voice.source);
            }
        }
    }

    fn execute(&mut self, engine: &Engine, op: VoiceOp) {
        match op {
            VoiceOp::Start { voice, config, stream, stream_looping, asset } => {
                self.voices.insert(
                    voice,
                    Voice {
                        source: 0,
                        config,
                        stream,
                        stream_looping,
                        awaiting_attachment: true,
                        stop_requested: false,
                    },
                );
                if let Some(asset) = asset {
                    self.attach(voice, asset);
                }
            }
            VoiceOp::Attach { voice, asset } => self.attach(voice, asset),
            VoiceOp::FailAttachment { voice } => {
                // `ChannelHandle.failAttachment` releases at once.
                if let Some(voice_state) = self.voices.remove(&voice) {
                    if voice_state.source != 0 {
                        let _ = backend::destroy_source(voice_state.source);
                    }
                    lock(&engine.released).push(voice);
                }
            }
            VoiceOp::Update { voice, mask, config } => {
                if let Some(state) = self.voices.get_mut(&voice) {
                    merge_config(&mut state.config, mask, config);
                    if state.source != 0 {
                        let _ = backend::update_sound(state.source, mask, state.config);
                    }
                }
            }
            VoiceOp::Stop { voice } => {
                if let Some(state) = self.voices.get_mut(&voice) {
                    state.awaiting_attachment = false;
                    state.stop_requested = true;
                    if state.source != 0 {
                        let _ = backend::source_stop(state.source);
                    }
                }
            }
            VoiceOp::Pause { voice } => {
                if let Some(state) = self.voices.get(&voice) {
                    if source_state(state.source) == AL_PLAYING {
                        let _ = backend::source_pause(state.source);
                    }
                }
            }
            VoiceOp::ResumeAll => {
                for state in self.voices.values() {
                    if source_state(state.source) == AL_PAUSED {
                        let _ = backend::source_play(state.source);
                    }
                }
            }
            VoiceOp::Listener(transform) => {
                if self.device != 0 {
                    let _ = backend::listener_set_transform(self.device, transform);
                }
            }
            VoiceOp::Tick => self.tick(engine),
        }
    }

    /// `Channel.attachStaticAsset` / `attachStreamingAsset`, then `play`.
    fn attach(&mut self, voice: u64, asset: u64) {
        let device = self.device;
        let Some(state) = self.voices.get_mut(&voice) else {
            return;
        };
        if state.stop_requested || state.source != 0 || device == 0 {
            state.awaiting_attachment = false;
            return;
        }
        let created = if state.stream {
            backend::create_streaming_sound_from_asset(device, state.config, asset, state.stream_looping)
        } else {
            backend::create_static_sound(device, state.config, asset)
        };
        state.awaiting_attachment = false;
        match created {
            Ok(source) => {
                state.source = source;
                let _ = backend::source_play(source);
            }
            // The pool is full: the voice ends silently, as a zero Java handle does.
            Err(AudioError::PoolExhausted) => {}
            Err(_) => {}
        }
    }

    fn tick(&mut self, engine: &Engine) {
        if self.device != 0 {
            let _ = backend::tick_device(self.device);
        }
        let stopped: Vec<u64> = self
            .voices
            .iter()
            .filter(|(_, voice)| {
                !voice.awaiting_attachment && (voice.source == 0 || source_state(voice.source) == AL_STOPPED)
            })
            .map(|(&voice, _)| voice)
            .collect();
        if !stopped.is_empty() {
            let mut released = lock(&engine.released);
            for voice in stopped {
                if let Some(state) = self.voices.remove(&voice) {
                    if state.source != 0 {
                        let _ = backend::destroy_source(state.source);
                    }
                }
                released.push(voice);
            }
        }
        self.refresh_pool_counts(engine);
        if self.device_changed() {
            engine.reload_needed.store(true, Ordering::Release);
        }
    }

    /// `SoundEngine.shouldChangeDevice`.
    fn device_changed(&mut self) -> bool {
        if self.device != 0 && backend::device_is_disconnected(self.device).unwrap_or(false) {
            return true;
        }
        if self.last_device_check.elapsed() < DEVICE_CHECK_INTERVAL {
            return false;
        }
        self.last_device_check = Instant::now();
        match &self.preferred {
            None => {
                if self.device != 0 {
                    backend::device_has_default_changed(self.device).unwrap_or(false)
                } else {
                    let current = backend::default_device_name().ok().filter(|name| !name.is_empty());
                    let changed = current != self.default_name;
                    self.default_name = current;
                    changed
                }
            }
            Some(preferred) => {
                let current = if self.device == 0 {
                    "Unknown".to_string()
                } else {
                    backend::current_device_name(self.device).unwrap_or_default()
                };
                &current != preferred
                    && backend::available_devices()
                        .map(|devices| devices.split('\n').any(|device| device == preferred))
                        .unwrap_or(false)
            }
        }
    }

    fn refresh_pool_counts(&self, engine: &Engine) {
        let counts = if self.device == 0 {
            [0; 4]
        } else {
            backend::device_pool_counts(self.device)
                .map(|counts| {
                    [counts.static_used, counts.static_limit, counts.streaming_used, counts.streaming_limit]
                })
                .unwrap_or([0; 4])
        };
        *lock(&engine.pool_counts) = counts;
    }
}

fn source_state(source: u64) -> i32 {
    if source == 0 {
        AL_STOPPED
    } else {
        backend::source_state(source).unwrap_or(AL_STOPPED)
    }
}

/// Applies the masked fields, as Java's `Channel` keeps one config record.
fn merge_config(target: &mut SoundConfigRecord, mask: u32, update: SoundConfigRecord) {
    if mask & SOUND_UPDATE_POSITION != 0 {
        target.x = update.x;
        target.y = update.y;
        target.z = update.z;
    }
    if mask & SOUND_UPDATE_PITCH != 0 {
        target.pitch = update.pitch;
    }
    if mask & SOUND_UPDATE_GAIN != 0 {
        target.gain = update.gain;
    }
}
