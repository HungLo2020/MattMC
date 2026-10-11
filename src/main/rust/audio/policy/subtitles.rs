//! Subtitle tracking (`SubtitleOverlay`'s listener and bookkeeping).
//!
//! Every play that reaches the listener stage records its subtitle here; the
//! overlay asks for the audible entries once per frame and only draws them.
//! Entries are never removed, only their played-at instances expire, as in
//! the Java overlay.

use std::sync::Arc;

#[derive(Clone, Debug, PartialEq)]
struct PlayedAt {
    position: [f64; 3],
    time: i64,
}

#[derive(Clone, Debug)]
struct Entry {
    /// The subtitle key, or the event path for `DEBUG_SUBTITLES`.
    text: Arc<str>,
    /// Hearing range of the first play (`Float.POSITIVE_INFINITY` for
    /// relative or unattenuated sounds).
    range: f32,
    played: Vec<PlayedAt>,
}

/// One subtitle to draw this frame.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Visible {
    pub(crate) text: Arc<str>,
    /// The played-at location closest to the listener.
    pub(crate) position: [f64; 3],
    pub(crate) age_ms: i64,
}

#[derive(Debug, Default)]
pub(crate) struct Subtitles {
    /// The overlay is registered (`showSubtitles` is on).
    pub(crate) listening: bool,
    /// `SharedConstants.DEBUG_SUBTITLES`: every event subtitles as its path.
    pub(crate) debug: bool,
    entries: Vec<Entry>,
}

/// `Vec3.equals`: `Double.compare` per axis (NaNs equal, `-0.0 != 0.0`).
fn same_position(a: [f64; 3], b: [f64; 3]) -> bool {
    a.iter().zip(b).all(|(&a, b)| (a.is_nan() && b.is_nan()) || a.to_bits() == b.to_bits())
}

fn distance_sqr(a: [f64; 3], b: [f64; 3]) -> f64 {
    let (x, y, z) = (a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    x * x + y * y + z * z
}

impl Entry {
    /// `Subtitle.getClosest`: the first minimum by `distanceTo`.
    fn closest(&self, listener: [f64; 3]) -> Option<&PlayedAt> {
        if self.played.len() == 1 {
            return self.played.first();
        }
        let mut best: Option<(&PlayedAt, f64)> = None;
        for played in &self.played {
            let distance = distance_sqr(played.position, listener).sqrt();
            // `Comparator.comparingDouble`: `Double.compare` keeps the first on ties.
            if best.is_none_or(|(_, current)| distance.total_cmp(&current).is_lt()) {
                best = Some((played, distance));
            }
        }
        best.map(|(played, _)| played)
    }

    fn is_audible_from(&self, listener: [f64; 3]) -> bool {
        if self.range.is_infinite() {
            return true;
        }
        let range = f64::from(self.range);
        self.closest(listener)
            .is_some_and(|played| distance_sqr(listener, played.position) < range * range)
    }
}

impl Subtitles {
    /// `SubtitleOverlay.onPlaySound` for an event with a subtitle (or any
    /// registered event when debugging).
    pub(crate) fn on_play(&mut self, text: Arc<str>, position: [f64; 3], range: f32, now: i64) {
        if !self.listening {
            return;
        }
        if let Some(entry) = self.entries.iter_mut().find(|entry| entry.text == text) {
            entry.played.retain(|played| !same_position(position, played.position));
            entry.played.push(PlayedAt { position, time: now });
            return;
        }
        self.entries.push(Entry { text, range, played: vec![PlayedAt { position, time: now }] });
    }

    /// The overlay's per-frame pass: the audible entries, their expired
    /// played-at instances purged, in draw order.
    pub(crate) fn frame(&mut self, listener: [f64; 3], display_ms: f64, now: i64) -> Vec<Visible> {
        let mut visible = Vec::new();
        for entry in self.entries.iter_mut() {
            if !entry.is_audible_from(listener) {
                continue;
            }
            entry.played.retain(|played| ((now - played.time) as f64) <= display_ms);
            if let Some(played) = entry.closest(listener) {
                visible.push(Visible { text: entry.text.clone(), position: played.position, age_ms: now - played.time });
            }
        }
        visible
    }
}
