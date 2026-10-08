//! The Distant Horizons semantic collector's column ledger: which column
//! generation is current, pending, in flight, published or retiring; the
//! owner leases of DH buffer containers; lifecycle resets; the visible
//! segments of the frame being prepared; and the route receipts.
//!
//! The ledger also owns each generation's copied payload (packed vertices,
//! [`Payload`]), so ordinary publication goes straight from here to the world
//! frontend. `DistantHorizonsSemanticCollector` keeps material provenance
//! (it needs Java's model resolution), the capture diagnostics and payload
//! copies for them; it applies each call's [`Effect`]s to its provenance maps.
//! Every Java map operation is reproduced in order, including the access
//! order of the column LRU.
pub(crate) mod order;
mod payload;
#[cfg(test)]
mod tests;

use order::{OrderedMap, OrderedSet};
pub(crate) use payload::{Payload, Segment};
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

pub(crate) const MAX_RETAINED_COLUMNS: usize = 512;
pub(crate) const MAX_RETAINED_BYTES: i64 = 64 * 1024 * 1024;
/// One asset update is a bounded slice: it keeps each Rust residency
/// transaction short while DH streams, and bounds the copied staging (and
/// exact-provenance model extraction) per frame. The queue stays lossless;
/// later frames drain it.
const MAX_PENDING_ASSET_COLUMNS_PER_UPDATE: usize = 16;
const MAX_PENDING_ASSET_BYTES_PER_UPDATE: i64 = 16 * 1024 * 1024;
const MAX_VISIBLE_SEGMENTS: usize = 16_384;
pub(crate) const MAX_PENDING_VISIBLE_COLUMN_KEYS: usize = 16_384;
const MAX_PUBLICATION_TRACE_EVENTS: u32 = 24;

/// `RENDER_FLAG_RUST_NON_WATER_ROUTE_SELECTED`.
pub(crate) const ROUTE_SELECTED_FLAG: i32 = 1 << 4;

/// Java's configuration at the time of a call.
#[derive(Clone, Copy, Debug, Default)]
pub(crate) struct Config {
    /// `usesRustWholeFrameSemanticBuild()`.
    pub rust_whole_frame: bool,
    /// `retainsLegacyObservationSnapshots()`.
    pub legacy_observation: bool,
    /// `executionSnapshotsEnabled()`.
    pub execution_snapshots: bool,
    /// The `mattmc.dev.graphicsAuditSliceMetrics` publication trace.
    pub trace_publication: bool,
}

/// Why a call failed; Java throws its original exception for each.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Failure {
    PendingVisibleAdmission,
    VisibleSegmentCapture,
    SelectWithoutFrame,
    SelectUnsupportedSegments,
    RetentionBounds,
    ByteOverflow,
}

pub(crate) type Result<T> = std::result::Result<T, Failure>;

/// Emitted segments per layer: opaque, transparent side, transparent up, water.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Counts {
    pub opaque: i32,
    pub side: i32,
    pub up: i32,
    pub water: i32,
}

/// One visible segment (`WorldLodColumnInstanceRecord`).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Instance {
    pub column_key: i64,
    pub column_generation: i64,
    pub layer: i32,
    pub segment_index: i32,
    pub order: i32,
}

/// A column snapshot: its generation and payload.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Column {
    pub generation: i64,
    pub byte_size: i64,
    pub counts: Counts,
    pub payload: Arc<Payload>,
}

impl Column {
    pub(crate) fn new(generation: i64, payload: Payload) -> Self {
        Self { generation, byte_size: payload.byte_size(), counts: payload.counts(), payload: Arc::new(payload) }
    }
}

/// What Java must do to its payload maps, in order.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Effect {
    /// The current snapshot becomes this call's snapshot, and the current
    /// provenance this call's provenance (removed when it has none).
    SetCurrent(i64),
    /// Remove the current snapshot and its provenance.
    DropCurrent(i64),
    /// Legacy observation: publish this call's snapshot. Its provenance is
    /// put, or removed when absent if `remove_absent_provenance`.
    PublishCurrent { key: i64, remove_absent_provenance: bool },
    /// Publish asset `index` of the acknowledged update: keep its snapshot
    /// when `retain_payload` (else remove the published snapshot), and its
    /// provenance from the update (removed when absent).
    PublishUpdate { key: i64, index: usize, retain_payload: bool },
    /// Remove the published snapshot and provenance.
    DropPublished(i64),
    /// Remove every snapshot and provenance.
    Clear,
}

#[derive(Clone, Copy, Debug)]
struct Owner {
    generation: i64,
    owners: i32,
    token: u64,
}

#[derive(Clone, Copy, Debug)]
struct PublishedMeta {
    generation: i64,
    counts: Counts,
}

/// The DH frame being prepared: Java keeps the copied render parameters;
/// the ledger owns whether it is enabled and its flags.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Frame {
    pub enabled: bool,
    pub flags: i32,
}

/// A newly built column for [`Ledger::record_built`].
#[derive(Clone, Debug)]
pub(crate) struct Built {
    pub column: Column,
    /// The provenance sidecar's byte size, when it has one.
    pub provenance_bytes: Option<i64>,
    /// Java's provenance equality against the current provenance.
    pub same_provenance: bool,
}

/// The result of [`Ledger::visible_column`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Visible {
    Empty,
    Admit { generation: i64, counts: Counts },
}

/// One selected asset of a pending update.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Selected {
    pub column_key: i64,
    pub generation: i64,
    pub payload: Arc<Payload>,
    /// The column has a material provenance sidecar (Java must publish it).
    pub has_provenance: bool,
}

#[derive(Debug)]
pub(crate) struct PendingUpdate {
    pub generation: i64,
    pub assets: Vec<Selected>,
    pub retirements: Vec<(i64, i64)>,
}

/// [`Ledger::select_flush`]'s result.
#[derive(Debug)]
pub(crate) enum Flush {
    Nothing,
    NeedsJava,
    Selected(PendingUpdate),
}

/// An acknowledged asset: its key and generation, and the payload sent.
#[derive(Clone, Debug)]
pub(crate) struct Acknowledged {
    pub column_key: i64,
    pub generation: i64,
    pub payload: Arc<Payload>,
}

/// Route state reported by `routeDiagnosticsSnapshot`.
#[derive(Clone, Debug)]
pub(crate) struct Route {
    pub frame: i64,
    pub decision: String,
    pub reason: String,
    pub matrix_status: String,
    pub matrix_detail: String,
    pub clip_distance: f32,
    pub opaque_segments: i32,
    pub transparent_segments: i32,
    pub water_segments: i32,
    pub visible_columns: i32,
    pub unpublished_visible_columns: i32,
    pub cached_columns: i32,
    pub candidate_columns: i32,
    pub unpublished_candidates: i32,
    pub selected: bool,
    pub execution_count: i32,
    pub last_executed_route_frame: i64,
    pub last_executed_world_frame: i64,
    pub last_executed_submission: i64,
    pub last_executed_capture_frame: i64,
    pub last_executed_instances: i32,
    pub last_executed_opaque: i32,
    pub last_executed_transparent: i32,
    pub last_executed_water: i32,
    pub last_executed_semantics: bool,
}

impl Default for Route {
    fn default() -> Self {
        Self {
            frame: 0,
            decision: "not-attempted".into(),
            reason: "not-requested".into(),
            matrix_status: "not-observed".into(),
            matrix_detail: "not-observed".into(),
            clip_distance: -1.0,
            opaque_segments: 0,
            transparent_segments: 0,
            water_segments: 0,
            visible_columns: 0,
            unpublished_visible_columns: 0,
            cached_columns: 0,
            candidate_columns: 0,
            unpublished_candidates: 0,
            selected: false,
            execution_count: 0,
            last_executed_route_frame: 0,
            last_executed_world_frame: 0,
            last_executed_submission: 0,
            last_executed_capture_frame: 0,
            last_executed_instances: 0,
            last_executed_opaque: 0,
            last_executed_transparent: 0,
            last_executed_water: 0,
            last_executed_semantics: false,
        }
    }
}

/// Lifecycle and semantic-build receipts.
#[derive(Clone, Debug)]
pub(crate) struct Receipts {
    pub build_attempts: i64,
    pub built: i64,
    pub reused: i64,
    pub replaced: i64,
    pub last_payload_difference: String,
    pub last_payload_change_route_frame: i64,
    pub last_visible_set_signature: i64,
    pub last_visible_set_change_route_frame: i64,
    pub lifecycle_resets: i64,
    pub stale_route_executions: i64,
    pub resource_reload_resets: i64,
    pub world_unload_resets: i64,
    pub last_reset_reason: String,
    pub last_published_retirements: i32,
    pub last_invalidated_in_flight: i32,
    pub last_retirements_acknowledged: i32,
    pub last_retirements_superseded: i32,
    pub last_generation_floor: i64,
}

impl Default for Receipts {
    fn default() -> Self {
        Self {
            build_attempts: 0,
            built: 0,
            reused: 0,
            replaced: 0,
            last_payload_difference: "none".into(),
            last_payload_change_route_frame: i64::MIN,
            last_visible_set_signature: i64::MIN,
            last_visible_set_change_route_frame: i64::MIN,
            lifecycle_resets: 0,
            stale_route_executions: 0,
            resource_reload_resets: 0,
            world_unload_resets: 0,
            last_reset_reason: "none".into(),
            last_published_retirements: 0,
            last_invalidated_in_flight: 0,
            last_retirements_acknowledged: 0,
            last_retirements_superseded: 0,
            last_generation_floor: 0,
        }
    }
}

/// `NEXT_GENERATION`: allocated outside the ledger lock, as in Java.
static NEXT_GENERATION: AtomicI64 = AtomicI64::new(1);

pub(crate) fn allocate_generation() -> i64 {
    NEXT_GENERATION.fetch_add(1, Ordering::SeqCst)
}

pub(crate) fn peek_next_generation() -> i64 {
    NEXT_GENERATION.load(Ordering::SeqCst)
}

pub(crate) fn reset_generations() {
    NEXT_GENERATION.store(1, Ordering::SeqCst);
}

/// The process-wide ledger (the Java collector is static).
pub(crate) fn ledger() -> &'static Mutex<Ledger> {
    static LEDGER: OnceLock<Mutex<Ledger>> = OnceLock::new();
    LEDGER.get_or_init(|| Mutex::new(Ledger::new()))
}

pub(crate) struct Ledger {
    columns: OrderedMap<Column>,
    owners: HashMap<i64, Owner>,
    next_owner_token: u64,
    provenance: OrderedMap<i64>,
    published_generations: OrderedMap<i64>,
    published_meta: OrderedMap<PublishedMeta>,
    published_payload: OrderedMap<(i64, Arc<Payload>)>,
    pending: OrderedMap<i64>,
    pending_visible_keys: OrderedSet,
    candidates: OrderedSet,
    in_flight: OrderedMap<i64>,
    invalidated_in_flight: OrderedMap<i64>,
    /// The payload of each (key, generation) in flight, until it is
    /// acknowledged or released (a lifecycle reset keeps it).
    sent: HashMap<(i64, i64), Arc<Payload>>,
    pending_retirements: OrderedMap<i64>,
    last_lifecycle_retirements: OrderedMap<i64>,
    payload_differences: HashMap<i64, String>,
    pending_segments: Vec<Instance>,
    next_visible_order: i32,
    last_consumed: Vec<Instance>,
    frame: Frame,
    next_update_generation: i64,
    retained_bytes: i64,
    retained_provenance_bytes: i64,
    publication_trace_events: u32,
    pub(crate) route: Route,
    pub(crate) receipts: Receipts,
}

impl Ledger {
    pub(crate) fn new() -> Self {
        Self {
            columns: OrderedMap::access_ordered(),
            owners: HashMap::new(),
            next_owner_token: 1,
            provenance: OrderedMap::new(),
            published_generations: OrderedMap::new(),
            published_meta: OrderedMap::new(),
            published_payload: OrderedMap::new(),
            pending: OrderedMap::new(),
            pending_visible_keys: OrderedSet::new(),
            candidates: OrderedSet::new(),
            in_flight: OrderedMap::new(),
            invalidated_in_flight: OrderedMap::new(),
            sent: HashMap::new(),
            pending_retirements: OrderedMap::new(),
            last_lifecycle_retirements: OrderedMap::new(),
            payload_differences: HashMap::new(),
            pending_segments: Vec::new(),
            next_visible_order: 0,
            last_consumed: Vec::new(),
            frame: Frame::default(),
            next_update_generation: 1,
            retained_bytes: 0,
            retained_provenance_bytes: 0,
            publication_trace_events: 0,
            route: Route::default(),
            receipts: Receipts::default(),
        }
    }

    // ---- Queries ---------------------------------------------------------

    fn published_meta_valid(&self, key: i64) -> Option<PublishedMeta> {
        let meta = *self.published_meta.peek(key)?;
        (self.published_generations.peek(key) == Some(&meta.generation)).then_some(meta)
    }

    /// `publishedColumnLocked`: the generation of a retained published payload.
    pub(crate) fn published_payload_generation(&self, key: i64) -> Option<i64> {
        self.published_payload_of(key).map(|(generation, _)| generation)
    }

    /// The retained published payload, when it is the published generation.
    pub(crate) fn published_payload_of(&self, key: i64) -> Option<(i64, &Arc<Payload>)> {
        let (generation, payload) = self.published_payload.peek(key)?;
        (self.published_generations.peek(key) == Some(generation)).then_some((*generation, payload))
    }

    /// The current column without counting as an access.
    pub(crate) fn current(&self, key: i64) -> Option<&Column> {
        self.columns.peek(key)
    }

    pub(crate) fn published_generation(&self, key: i64) -> Option<i64> {
        self.published_generations.peek(key).copied()
    }

    /// `hasColumn(key)`.
    pub(crate) fn has_column(&self, key: i64) -> bool {
        self.columns.contains_key(key) || (self.published_meta_valid(key).is_some() && !self.pending_retirements.contains_key(key))
    }

    /// `hasColumn(key, generation)`.
    pub(crate) fn has_column_generation(&mut self, key: i64, generation: i64) -> bool {
        if generation == 0 {
            return false;
        }
        if let Some(column) = self.columns.get_touch(key) {
            if column.generation == generation {
                return true;
            }
        }
        self.published_meta_valid(key)
            .is_some_and(|meta| meta.generation == generation && self.pending_retirements.peek(key) != Some(&generation))
    }

    /// `hasPublishedColumn`.
    pub(crate) fn has_published_column(&self, key: i64) -> bool {
        self.published_meta_valid(key).is_some() || self.published_payload_generation(key).is_some()
    }

    /// `COLUMNS.get` for a diagnostic read: the current generation.
    pub(crate) fn touch(&mut self, key: i64) -> Option<i64> {
        self.columns.get_touch(key).map(|c| c.generation)
    }

    pub(crate) fn column_keys(&self) -> impl Iterator<Item = i64> + '_ {
        self.columns.keys()
    }

    pub(crate) fn column_count(&self) -> usize {
        self.columns.len()
    }

    pub(crate) fn payload_difference(&self, key: i64) -> Option<&str> {
        self.payload_differences.get(&key).map(String::as_str)
    }

    pub(crate) fn pending_segments(&self) -> &[Instance] {
        &self.pending_segments
    }

    pub(crate) fn last_consumed(&self) -> &[Instance] {
        &self.last_consumed
    }

    pub(crate) fn frame(&self) -> Frame {
        self.frame
    }

    pub(crate) fn lifecycle(&self) -> i64 {
        self.receipts.lifecycle_resets
    }

    pub(crate) fn retained_bytes(&self) -> i64 {
        self.retained_bytes
    }

    pub(crate) fn last_lifecycle_retirement_count(&self) -> usize {
        self.last_lifecycle_retirements.len()
    }

    pub(crate) fn pending_retirement_count(&self) -> usize {
        self.pending_retirements.len()
    }

    pub(crate) fn invalidated_in_flight_count(&self) -> usize {
        self.invalidated_in_flight.len()
    }

    pub(crate) fn minimum_published_generation(&self) -> i64 {
        self.published_generations.values().copied().min().unwrap_or(0)
    }

    pub(crate) fn oversized_column_count(&self) -> usize {
        self.columns.values().filter(|c| c.byte_size > MAX_RETAINED_BYTES).count()
    }

    /// `visiblePayloadStableForCapture`.
    pub(crate) fn visible_payload_stable(&self, required_frames: i32) -> bool {
        if required_frames <= 0 {
            return true;
        }
        let frames = required_frames as i64;
        let stable = |since: i64| since == i64::MIN || self.route.frame.wrapping_sub(since) >= frames;
        stable(self.receipts.last_payload_change_route_frame) && stable(self.receipts.last_visible_set_change_route_frame)
    }

    // ---- Recording built columns -----------------------------------------

    fn mark_pending_visible(&mut self, key: i64) -> Result<()> {
        if self.pending_visible_keys.contains(key) {
            return Ok(());
        }
        if self.pending_visible_keys.len() >= MAX_PENDING_VISIBLE_COLUMN_KEYS {
            return Err(Failure::PendingVisibleAdmission);
        }
        self.pending_visible_keys.insert(key);
        Ok(())
    }

    fn replace_provenance(&mut self, key: i64, bytes: Option<i64>) {
        match bytes {
            Some(bytes) => {
                if let Some(previous) = self.provenance.put(key, bytes) {
                    self.retained_provenance_bytes -= previous;
                }
                self.retained_provenance_bytes += bytes;
            }
            None => self.remove_provenance(key),
        }
    }

    fn remove_provenance(&mut self, key: i64) {
        if let Some(removed) = self.provenance.remove(key) {
            self.retained_provenance_bytes -= removed;
        }
    }

    /// `recordBuiltSnapshotLocked`. Returns the generation that represents
    /// the column (0 for an empty build).
    pub(crate) fn record_built(&mut self, config: Config, key: i64, built: Built, effects: &mut Vec<Effect>) -> Result<i64> {
        if !built.column.payload.has_segments() {
            return Ok(0);
        }
        let replaced = self.columns.get_touch(key).cloned();
        let same_payload = replaced.as_ref().is_some_and(|r| r.payload == built.column.payload);
        if let Some(replaced) = replaced.as_ref().filter(|_| same_payload) {
            if built.same_provenance {
                if config.rust_whole_frame && self.published_generations.peek(key) != Some(&replaced.generation) {
                    self.mark_pending_visible(key)?;
                }
                self.receipts.built += 1;
                self.receipts.reused += 1;
                return Ok(replaced.generation);
            }
            let generation = built.column.generation;
            self.columns.put(key, built.column.clone());
            effects.push(Effect::SetCurrent(key));
            self.replace_provenance(key, built.provenance_bytes);
            if config.legacy_observation {
                self.published_generations.put(key, generation);
                self.published_payload.put(key, (generation, built.column.payload.clone()));
                effects.push(Effect::PublishCurrent { key, remove_absent_provenance: true });
                self.pending.remove(key);
            } else {
                self.pending.put(key, generation);
                if config.rust_whole_frame {
                    self.mark_pending_visible(key)?;
                }
            }
            self.receipts.built += 1;
            return Ok(generation);
        }
        if let Some(replaced) = &replaced {
            self.receipts.replaced += 1;
            self.receipts.last_payload_change_route_frame = self.route.frame;
            let difference = replaced.payload.difference(&built.column.payload);
            self.receipts.last_payload_difference = difference.clone();
            self.payload_differences.insert(key, difference);
        }
        self.columns.put(key, built.column.clone());
        effects.push(Effect::SetCurrent(key));
        self.replace_provenance(key, built.provenance_bytes);
        if let Some(replaced) = &replaced {
            self.retained_bytes -= replaced.byte_size;
        }
        self.retained_bytes += built.column.byte_size;
        if let Some(superseded) = self.pending_retirements.remove(key) {
            if self.last_lifecycle_retirements.peek(key) == Some(&superseded) {
                self.last_lifecycle_retirements.remove(key);
                self.receipts.last_retirements_superseded += 1;
            }
        }
        if config.legacy_observation {
            self.published_generations.put(key, built.column.generation);
            self.published_payload.put(key, (built.column.generation, built.column.payload.clone()));
            effects.push(Effect::PublishCurrent { key, remove_absent_provenance: false });
            self.pending.remove(key);
        } else {
            self.pending.put(key, built.column.generation);
            if config.rust_whole_frame {
                self.mark_pending_visible(key)?;
            }
        }
        self.trim(MAX_RETAINED_COLUMNS, MAX_RETAINED_BYTES, effects)?;
        self.receipts.built += 1;
        Ok(built.column.generation)
    }

    /// The owner lease of `recordOwnedPackedColumnSnapshot`, taken after
    /// [`Self::record_built`] under the same lock. Returns the owner token, 0
    /// for none.
    pub(crate) fn acquire_owner(&mut self, key: i64, generation: i64) -> u64 {
        if generation == 0 || !self.has_column_generation(key, generation) {
            return 0;
        }
        let owner = match self.owners.get(&key) {
            Some(owner) if owner.generation == generation => *owner,
            _ => {
                let token = self.next_owner_token;
                self.next_owner_token += 1;
                Owner { generation, owners: 0, token }
            }
        };
        let owner = Owner { owners: owner.owners.saturating_add(1), ..owner };
        self.owners.insert(key, owner);
        owner.token
    }

    /// `SemanticColumnLease.close`, after its own idempotence check.
    pub(crate) fn release_owner(&mut self, config: Config, key: i64, generation: i64, token: u64, effects: &mut Vec<Effect>) {
        if token == 0 {
            return;
        }
        let Some(owner) = self.owners.get_mut(&key) else { return };
        if owner.token != token {
            return;
        }
        owner.owners -= 1;
        if owner.owners == 0 {
            self.owners.remove(&key);
            self.remove_column_generation(config, key, generation, effects);
        }
    }

    pub(crate) fn record_build_attempt(&mut self, config: Config) {
        if config.rust_whole_frame {
            self.receipts.build_attempts += 1;
        }
    }

    // ---- Removing columns ------------------------------------------------

    /// `removeColumn(key)`.
    pub(crate) fn remove_column(&mut self, config: Config, key: i64, effects: &mut Vec<Effect>) {
        if config.legacy_observation {
            return;
        }
        self.remove_column_locked(key, effects);
    }

    /// `removeColumn(key, generation)`.
    pub(crate) fn remove_column_generation(&mut self, config: Config, key: i64, generation: i64, effects: &mut Vec<Effect>) {
        if config.legacy_observation || generation <= 0 {
            return;
        }
        let current = self.columns.get_touch(key).map(|c| c.generation);
        if current == Some(generation) {
            self.remove_column_locked(key, effects);
            return;
        }
        if current.is_none() && self.published_meta_valid(key).is_some_and(|m| m.generation == generation) {
            self.remove_column_locked(key, effects);
        }
    }

    fn remove_column_locked(&mut self, key: i64, effects: &mut Vec<Effect>) {
        self.owners.remove(&key);
        let removed = self.columns.remove(key);
        effects.push(Effect::DropCurrent(key));
        self.remove_provenance(key);
        if let Some(removed) = removed {
            self.retained_bytes -= removed.byte_size;
        }
        self.pending.remove(key);
        self.pending_visible_keys.remove(key);
        let before = self.pending_segments.len();
        self.pending_segments.retain(|i| i.column_key != key);
        if self.pending_segments.len() != before {
            self.recompute_route_counts();
            if self.pending_segments.is_empty() {
                self.route.selected = false;
                self.route.decision = "rejected".into();
                self.route.reason = "visible-columns-retired-before-submit".into();
                self.frame.flags &= !ROUTE_SELECTED_FLAG;
            }
        }
        self.in_flight.remove(key);
        self.payload_differences.remove(&key);
        if let Some(&published) = self.published_generations.peek(key) {
            self.pending_retirements.put(key, published);
        }
    }

    /// `trimRetainedColumnsLocked`.
    pub(crate) fn trim(&mut self, maximum_columns: usize, maximum_bytes: i64, effects: &mut Vec<Effect>) -> Result<()> {
        if maximum_columns == 0 || maximum_bytes <= 0 {
            return Err(Failure::RetentionBounds);
        }
        while (self.columns.len() > maximum_columns || self.retained_bytes + self.retained_provenance_bytes > maximum_bytes)
            && self.columns.len() > 1
        {
            let Some(key) = self.eldest_unprotected_column() else { break };
            self.remove_column_locked(key, effects);
        }
        Ok(())
    }

    fn eldest_unprotected_column(&self) -> Option<i64> {
        self.columns.keys().find(|&key| {
            !self.owners.contains_key(&key)
                && !self.candidates.contains(key)
                && !self.pending_visible_keys.contains(key)
                && !self.last_consumed.iter().any(|i| i.column_key == key)
        })
    }

    // ---- Publication -------------------------------------------------------

    /// `requestColumnPublication` (Java checks `enabled()` first).
    pub(crate) fn request_publication(&mut self, key: i64) -> Result<bool> {
        let current = self.columns.get_touch(key).map(|c| c.generation);
        let published = self.published_generations.peek(key).copied();
        if let Some(current) = current {
            if published != Some(current) {
                self.mark_pending_visible(key)?;
            }
        }
        Ok(self.has_published_column(key))
    }

    /// `pendingUpdate(visibleCandidatesOnly)`. Marks the selected assets in
    /// flight.
    pub(crate) fn pending_update(&mut self, config: Config, visible_candidates_only: bool) -> Result<Option<PendingUpdate>> {
        let Some(update) = self.peek_pending_update(visible_candidates_only)? else { return Ok(None) };
        self.mark_in_flight(config, &update.assets);
        Ok(Some(update))
    }

    /// The update [`Self::pending_update`] would select, without marking it
    /// in flight.
    pub(crate) fn peek_pending_update(&self, visible_candidates_only: bool) -> Result<Option<PendingUpdate>> {
        if self.pending.is_empty() && self.pending_retirements.is_empty() {
            return Ok(None);
        }
        let assets = self.select_pending_assets(visible_candidates_only)?;
        if assets.is_empty() && self.pending_retirements.is_empty() {
            return Ok(None);
        }
        Ok(Some(PendingUpdate {
            generation: self.next_update_generation,
            assets,
            retirements: self.pending_retirements.iter().map(|(k, g)| (k, *g)).collect(),
        }))
    }

    /// The Rust flush's selection: the visible-candidate update, marked in
    /// flight, unless one of its columns carries material provenance (then
    /// nothing is marked and Java publishes the update).
    pub(crate) fn select_flush(&mut self, config: Config) -> Result<Flush> {
        let Some(update) = self.peek_pending_update(true)? else { return Ok(Flush::Nothing) };
        if update.assets.iter().any(|a| a.has_provenance) {
            return Ok(Flush::NeedsJava);
        }
        self.mark_in_flight(config, &update.assets);
        Ok(Flush::Selected(update))
    }

    /// Marks a selection in flight, as `selectPendingAssetSnapshotsLocked` did.
    fn mark_in_flight(&mut self, config: Config, selected: &[Selected]) {
        for asset in selected {
            self.in_flight.put(asset.column_key, asset.generation);
            self.sent.insert((asset.column_key, asset.generation), asset.payload.clone());
        }
        self.trace_selection(config, selected);
    }

    fn select_pending_assets(&self, visible_candidates_only: bool) -> Result<Vec<Selected>> {
        let mut ordered: Vec<i64> = self.pending_visible_keys.iter().filter(|&k| self.pending.contains_key(k)).collect();
        if self.pending_visible_keys.is_empty() && visible_candidates_only {
            ordered.extend(self.candidates.iter().filter(|&k| self.pending.contains_key(k)));
        } else if self.pending_visible_keys.is_empty() {
            ordered.extend(self.pending.keys());
        }
        let selected_keys: Option<std::collections::HashSet<i64>> =
            (!self.pending_segments.is_empty()).then(|| self.pending_segments.iter().map(|i| i.column_key).collect());
        let mut selected = Vec::with_capacity(MAX_PENDING_ASSET_COLUMNS_PER_UPDATE);
        let mut selected_bytes = 0i64;
        for key in ordered {
            if selected_keys.as_ref().is_some_and(|keys| keys.contains(&key)) || self.in_flight.contains_key(key) {
                continue;
            }
            // A pending snapshot is always the current one (both maps change together).
            let Some(column) = self.columns.peek(key) else {
                debug_assert!(false, "pending column {key} is not current");
                continue;
            };
            let bytes = match self.provenance.peek(key) {
                Some(&provenance) => column.byte_size.checked_add(provenance).ok_or(Failure::ByteOverflow)?,
                None => column.byte_size,
            };
            let exceeds = selected_bytes > 0 && bytes > MAX_PENDING_ASSET_BYTES_PER_UPDATE - selected_bytes;
            if selected.len() == MAX_PENDING_ASSET_COLUMNS_PER_UPDATE || exceeds {
                break;
            }
            selected.push(Selected {
                column_key: key,
                generation: column.generation,
                payload: column.payload.clone(),
                has_provenance: self.provenance.contains_key(key),
            });
            selected_bytes = selected_bytes.checked_add(bytes).ok_or(Failure::ByteOverflow)?;
        }
        Ok(selected)
    }

    fn trace_selection(&mut self, config: Config, selected: &[Selected]) {
        if self.publication_trace_events >= MAX_PUBLICATION_TRACE_EVENTS || self.pending_visible_keys.is_empty() || !config.trace_publication {
            return;
        }
        let keys: Vec<String> = selected.iter().take(4).map(|s| format!("{}:{}", s.column_key, s.generation)).collect();
        self.trace(
            config,
            &format!(
                "select pending={} visible_pending={} selected={} keys={}",
                self.pending.len(),
                self.pending_visible_keys.len(),
                selected.len(),
                keys.join(",")
            ),
        );
    }

    fn trace(&mut self, config: Config, message: &str) {
        if self.publication_trace_events >= MAX_PUBLICATION_TRACE_EVENTS || !config.trace_publication {
            return;
        }
        self.publication_trace_events += 1;
        println!("[MattMC graphics audit] DH asset publication {message}");
    }

    /// `acknowledge(update)`.
    pub(crate) fn acknowledge(
        &mut self,
        config: Config,
        assets: &[Acknowledged],
        retirements: &[(i64, i64)],
        effects: &mut Vec<Effect>,
    ) {
        let mut advanced: Vec<(i64, i64)> = Vec::new();
        for (index, asset) in assets.iter().enumerate() {
            let (key, generation) = (asset.column_key, asset.generation);
            self.sent.remove(&(key, generation));
            if self.in_flight.peek(key) == Some(&generation) {
                self.in_flight.remove(key);
            }
            let invalidated = self.invalidated_in_flight.peek(key) == Some(&generation);
            if invalidated {
                self.invalidated_in_flight.remove(key);
            }
            let was_visible_demand = self.pending_visible_keys.contains(key);
            if self.pending.peek(key) == Some(&generation) {
                self.pending.remove(key);
            }
            if self.published_generations.peek(key).is_some_and(|&p| p != generation) {
                advanced.push((key, generation));
            }
            let current = self.columns.get_touch(key).map(|c| (c.generation, c.byte_size));
            match current {
                None => {
                    self.drop_published(key, effects);
                    self.pending_retirements.put(key, generation);
                }
                Some((current, _)) if current != generation && invalidated => {
                    self.pending_retirements.put(key, generation);
                }
                Some((current, current_bytes)) => {
                    self.published_generations.put(key, generation);
                    self.published_meta.put(key, PublishedMeta { generation, counts: asset.payload.counts() });
                    if config.execution_snapshots {
                        self.published_payload.put(key, (generation, asset.payload.clone()));
                    } else {
                        self.published_payload.remove(key);
                    }
                    effects.push(Effect::PublishUpdate { key, index, retain_payload: config.execution_snapshots });
                    if !config.execution_snapshots && current == generation {
                        // discardAcknowledgedPayloadLocked
                        self.columns.remove(key);
                        effects.push(Effect::DropCurrent(key));
                        self.retained_bytes -= current_bytes;
                        self.remove_provenance(key);
                    }
                }
            }
            if current.is_some_and(|(c, _)| c == generation) {
                self.pending_visible_keys.remove(key);
            }
            if was_visible_demand {
                let message = format!(
                    "ack key={key} published={generation} current={} retained_visible_demand={}",
                    current.map_or("missing".to_string(), |(c, _)| c.to_string()),
                    self.pending_visible_keys.contains(key)
                );
                self.trace(config, &message);
            }
        }
        self.invalidate_advanced(&advanced);
        for &(key, generation) in retirements {
            if self.pending_retirements.peek(key) == Some(&generation) {
                self.pending_retirements.remove(key);
            }
            if self.published_generations.peek(key) == Some(&generation) {
                self.drop_published(key, effects);
            }
            if self.last_lifecycle_retirements.peek(key) == Some(&generation) {
                self.last_lifecycle_retirements.remove(key);
                self.receipts.last_retirements_acknowledged += 1;
            }
        }
        self.invalidate_retired(retirements);
        self.next_update_generation += 1;
    }

    fn drop_published(&mut self, key: i64, effects: &mut Vec<Effect>) {
        self.published_generations.remove(key);
        self.published_payload.remove(key);
        self.published_meta.remove(key);
        effects.push(Effect::DropPublished(key));
    }

    fn invalidate_advanced(&mut self, advanced: &[(i64, i64)]) {
        if advanced.is_empty() || self.pending_segments.is_empty() {
            return;
        }
        let before = self.pending_segments.len();
        self.pending_segments
            .retain(|i| !advanced.iter().any(|&(key, generation)| key == i.column_key && generation != i.column_generation));
        if self.pending_segments.len() == before {
            return;
        }
        self.recompute_route_counts();
        if self.pending_segments.is_empty() {
            self.frame.flags &= !ROUTE_SELECTED_FLAG;
            self.route.decision = "rejected".into();
            self.route.reason = "asset-generation-advanced-before-submit".into();
            self.route.selected = false;
        } else {
            self.route.decision = "selected".into();
            self.route.reason = "stale-visible-references-pruned".into();
        }
    }

    fn invalidate_retired(&mut self, retirements: &[(i64, i64)]) {
        if retirements.is_empty() || self.pending_segments.is_empty() {
            return;
        }
        let before = self.pending_segments.len();
        self.pending_segments
            .retain(|i| !retirements.iter().any(|&(key, generation)| key == i.column_key && generation == i.column_generation));
        if self.pending_segments.len() == before {
            return;
        }
        self.recompute_route_counts();
        if self.pending_segments.is_empty() {
            self.frame.flags &= !ROUTE_SELECTED_FLAG;
            self.route.decision = "rejected".into();
            self.route.reason = "asset-retired-before-submit".into();
            self.route.selected = false;
        }
    }

    /// `releaseInFlightAssets(update)`.
    /// The sent payload of an asset in flight, for its acknowledgement.
    pub(crate) fn sent_payload(&self, key: i64, generation: i64) -> Option<Arc<Payload>> {
        self.sent.get(&(key, generation)).cloned()
    }

    /// Published payload keys in Java's `PUBLISHED_COLUMNS` order.
    pub(crate) fn published_payload_keys(&self) -> impl Iterator<Item = i64> + '_ {
        self.published_payload.keys()
    }

    pub(crate) fn release_in_flight(&mut self, assets: &[(i64, i64)]) {
        for &(key, generation) in assets {
            self.sent.remove(&(key, generation));
            if self.in_flight.peek(key) == Some(&generation) {
                self.in_flight.remove(key);
            }
            if self.invalidated_in_flight.peek(key) == Some(&generation) {
                self.invalidated_in_flight.remove(key);
            }
        }
    }

    // ---- The visible frame -----------------------------------------------

    fn recompute_route_counts(&mut self) {
        let (mut opaque, mut transparent, mut water) = (0, 0, 0);
        for instance in &self.pending_segments {
            match instance.layer {
                1 => opaque += 1,
                2 | 3 => transparent += 1,
                4 => water += 1,
                _ => {}
            }
        }
        self.route.opaque_segments = opaque;
        self.route.transparent_segments = transparent;
        self.route.water_segments = water;
    }

    fn reset_frame_route(&mut self) {
        self.route.opaque_segments = 0;
        self.route.transparent_segments = 0;
        self.route.water_segments = 0;
        self.route.visible_columns = 0;
        self.route.unpublished_visible_columns = 0;
        self.route.cached_columns = self.columns.len() as i32;
        self.route.selected = false;
    }

    /// `beginVisibleFrame` after Java evaluated the matrices. `frame` is
    /// `None` when the matrices were rejected, with `reason` Java's reason.
    pub(crate) fn begin_frame(&mut self, frame: Option<i32>, reason: &str, matrix_status: &str, matrix_detail: &str) {
        self.pending_segments.clear();
        self.next_visible_order = 0;
        self.route.frame += 1;
        match frame {
            Some(flags) => {
                self.frame = Frame { enabled: true, flags };
                self.route.decision = "preflight".into();
                self.route.reason = "pending".into();
            }
            None => {
                self.frame = Frame::default();
                self.route.decision = "rejected".into();
                self.route.reason = reason.into();
            }
        }
        self.route.matrix_status = matrix_status.into();
        self.route.matrix_detail = matrix_detail.into();
        self.reset_frame_route();
    }

    pub(crate) fn set_clip_distance(&mut self, clip_distance: f32) {
        self.route.clip_distance = clip_distance;
    }

    /// `recordVisibleMaterialColumn`'s admission (Java checks `enabled()`).
    pub(crate) fn visible_column(&mut self, key: i64) -> Result<Visible> {
        let current = self.columns.get_touch(key).map(|c| c.generation);
        if current.is_none() && self.pending_retirements.contains_key(key) {
            return Ok(Visible::Empty);
        }
        let Some(column) = self.published_meta_valid(key) else {
            if current.is_none() {
                return Ok(Visible::Empty);
            }
            self.mark_pending_visible(key)?;
            self.route.unpublished_visible_columns += 1;
            return Ok(Visible::Empty);
        };
        if current.is_some_and(|c| c != column.generation) {
            self.mark_pending_visible(key)?;
        }
        Ok(Visible::Admit { generation: column.generation, counts: column.counts })
    }

    /// Appends an admitted column's segments, each layer at its global offset.
    pub(crate) fn append_visible_column(&mut self, key: i64, generation: i64, counts: Counts) -> Result<()> {
        let admitted = (counts.opaque + counts.side + counts.up + counts.water) as usize;
        if self.pending_segments.len() + admitted > MAX_VISIBLE_SEGMENTS {
            return Err(Failure::VisibleSegmentCapture);
        }
        let mut offset = 0;
        for (layer, count) in [(1, counts.opaque), (2, counts.side), (3, counts.up), (4, counts.water)] {
            for compact in 0..count {
                self.push_instance(key, generation, layer, offset + compact);
            }
            offset += count;
        }
        Ok(())
    }

    /// `recordVisibleSegment`'s append of compacted segment indexes.
    pub(crate) fn append_segments(&mut self, key: i64, generation: i64, layer: i32, indexes: &[i32]) -> Result<()> {
        if self.pending_segments.len() + indexes.len() > MAX_VISIBLE_SEGMENTS {
            return Err(Failure::VisibleSegmentCapture);
        }
        for &index in indexes {
            self.push_instance(key, generation, layer, index);
        }
        Ok(())
    }

    fn push_instance(&mut self, column_key: i64, column_generation: i64, layer: i32, segment_index: i32) {
        self.pending_segments.push(Instance { column_key, column_generation, layer, segment_index, order: self.next_visible_order });
        self.next_visible_order += 1;
    }

    fn visible_set_signature(visible: &[Instance]) -> i64 {
        fn update(hash: u64, value: i64) -> u64 {
            let mut result = hash;
            for shift in (0..64).step_by(8) {
                result = (result ^ ((value as u64 >> shift) & 0xff)).wrapping_mul(0x100000001b3);
            }
            result
        }
        let mut hash = 0xcbf29ce484222325u64;
        for i in visible {
            hash = update(hash, i.column_key);
            hash = update(hash, i.column_generation);
            hash = update(hash, i.layer as i64);
            hash = update(hash, i.segment_index as i64);
            hash = update(hash, i.order as i64);
        }
        update(hash, visible.len() as i64) as i64
    }

    /// `consumeVisibleFrame` (Java checks `enabled()`): the selected visible
    /// segments and the frame they were prepared for.
    pub(crate) fn consume_frame(&mut self) -> (Vec<Instance>, Frame) {
        let frame = self.frame;
        let selected = self.route.selected && frame.enabled && frame.flags & ROUTE_SELECTED_FLAG != 0;
        let result = if selected { self.pending_segments.clone() } else { Vec::new() };
        let signature = Self::visible_set_signature(&result);
        if signature != self.receipts.last_visible_set_signature {
            self.receipts.last_visible_set_signature = signature;
            self.receipts.last_visible_set_change_route_frame = self.route.frame;
        }
        self.last_consumed = result.clone();
        self.pending_segments.clear();
        self.frame = Frame::default();
        (result, frame)
    }

    /// `consumeRenderFrame`.
    pub(crate) fn consume_render_frame(&mut self) -> Frame {
        std::mem::take(&mut self.frame)
    }

    /// `consumeVisibleSegments`.
    pub(crate) fn consume_segments(&mut self) -> Vec<Instance> {
        let result = if self.route.selected { self.pending_segments.clone() } else { Vec::new() };
        self.last_consumed = result.clone();
        self.pending_segments.clear();
        result
    }

    /// `markRustNonWaterRouteSelected` (Java checks `enabled()`). Returns
    /// whether the route became selected; `complete_exact_atlas` is Java's
    /// `hasCompleteVisibleExactAtlasCoverage()`.
    pub(crate) fn select_route(&mut self, complete_exact_atlas: bool) -> Result<bool> {
        if !self.frame.enabled {
            return Err(Failure::SelectWithoutFrame);
        }
        if self.pending_segments.iter().any(|i| i.layer < 1 || i.layer > 4) {
            return Err(Failure::SelectUnsupportedSegments);
        }
        if self.frame.flags & ROUTE_SELECTED_FLAG != 0 {
            return Ok(false);
        }
        self.frame.flags |= ROUTE_SELECTED_FLAG;
        self.route.decision = "selected".into();
        self.route.reason = if complete_exact_atlas {
            "all-visible-material-segments-supported"
        } else {
            "reduced-color-with-partial-exact-atlas"
        }
        .into();
        self.recompute_route_counts();
        self.route.selected = true;
        Ok(true)
    }

    /// `recordRustNonWaterRouteRejected` after Java's validation.
    pub(crate) fn reject_route(&mut self, reason: &str, opaque: i32, transparent: i32, water: i32) {
        self.route.decision = "rejected".into();
        self.route.reason = reason.into();
        self.route.opaque_segments = opaque;
        self.route.transparent_segments = transparent;
        self.route.water_segments = water;
        self.route.selected = false;
        self.pending_segments.clear();
        self.frame.flags &= !ROUTE_SELECTED_FLAG;
    }

    /// `recordRustMaterialRouteExecution` after Java's validation. Returns
    /// false for a frame from an ended lifecycle, whose receipt is dropped.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn record_execution(
        &mut self,
        lifecycle: i64,
        world_frame: i64,
        submission: i64,
        capture_frame: i64,
        instances: i32,
        opaque: i32,
        transparent: i32,
        water: i32,
    ) -> bool {
        if lifecycle != self.receipts.lifecycle_resets {
            self.receipts.stale_route_executions += 1;
            return false;
        }
        let route = &mut self.route;
        route.last_executed_route_frame = route.frame;
        route.last_executed_world_frame = world_frame;
        route.last_executed_submission = submission;
        route.last_executed_capture_frame = capture_frame;
        route.last_executed_instances = instances;
        route.last_executed_opaque = opaque;
        route.last_executed_transparent = transparent;
        route.last_executed_water = water;
        route.last_executed_semantics = true;
        route.execution_count += 1;
        true
    }

    /// `recordRenderListObservation` after validation.
    pub(crate) fn observe_render_list(&mut self, visible_columns: i32) {
        self.route.visible_columns = visible_columns;
        self.route.cached_columns = self.columns.len() as i32;
    }

    /// `recordRenderListVisibilityStats` after validation.
    pub(crate) fn record_visibility(&mut self, candidates: i32, unpublished: i32, keys: &[i64]) {
        self.route.candidate_columns = candidates;
        self.route.unpublished_candidates = unpublished;
        self.candidates.clear();
        for &key in keys {
            self.candidates.insert(key);
        }
    }

    // ---- Lifecycle -------------------------------------------------------

    /// `clearWithReason`.
    pub(crate) fn clear(&mut self, reason: &str, effects: &mut Vec<Effect>) {
        let r = &mut self.receipts;
        r.lifecycle_resets += 1;
        if reason == "resource-reload" {
            r.resource_reload_resets += 1;
        }
        if reason == "world-unload" {
            r.world_unload_resets += 1;
        }
        if !self.published_generations.is_empty() || !self.in_flight.is_empty() {
            r.last_reset_reason = reason.into();
            r.last_published_retirements = self.published_generations.len() as i32;
            r.last_invalidated_in_flight = self.in_flight.len() as i32;
            r.last_retirements_acknowledged = 0;
            r.last_retirements_superseded = 0;
            r.last_generation_floor = peek_next_generation();
            self.last_lifecycle_retirements.replace_with(&self.published_generations);
        }
        let published: Vec<(i64, i64)> = self.published_generations.iter().map(|(k, g)| (k, *g)).collect();
        for (key, generation) in published {
            self.pending_retirements.put(key, generation);
        }
        self.columns.clear();
        self.owners.clear();
        self.published_payload.clear();
        self.published_meta.clear();
        self.provenance.clear();
        self.pending.clear();
        self.pending_visible_keys.clear();
        self.candidates.clear();
        self.invalidated_in_flight.replace_with(&self.in_flight);
        self.in_flight.clear();
        self.published_generations.clear();
        self.payload_differences.clear();
        self.pending_segments.clear();
        self.last_consumed.clear();
        self.frame = Frame::default();
        self.next_visible_order = 0;
        let route = &mut self.route;
        route.decision = "cleared".into();
        route.reason = reason.into();
        route.matrix_status = "not-observed".into();
        route.matrix_detail = "not-observed".into();
        route.opaque_segments = 0;
        route.transparent_segments = 0;
        route.water_segments = 0;
        route.visible_columns = 0;
        route.unpublished_visible_columns = 0;
        route.cached_columns = 0;
        route.candidate_columns = 0;
        route.unpublished_candidates = 0;
        route.selected = false;
        route.execution_count = 0;
        route.last_executed_route_frame = 0;
        route.last_executed_world_frame = 0;
        route.last_executed_submission = 0;
        route.last_executed_capture_frame = 0;
        route.last_executed_instances = 0;
        route.last_executed_opaque = 0;
        route.last_executed_transparent = 0;
        route.last_executed_water = 0;
        route.last_executed_semantics = false;
        let r = &mut self.receipts;
        r.build_attempts = 0;
        r.built = 0;
        r.reused = 0;
        r.replaced = 0;
        r.last_payload_difference = "none".into();
        r.last_payload_change_route_frame = i64::MIN;
        r.last_visible_set_signature = i64::MIN;
        r.last_visible_set_change_route_frame = i64::MIN;
        self.retained_bytes = 0;
        self.retained_provenance_bytes = 0;
        effects.push(Effect::Clear);
    }

    /// `resetForTest`: the initial state (generations restart at 1), except
    /// the route fields Java's reset leaves alone. Unlike [`Self::clear`] it
    /// leaves no retirements.
    pub(crate) fn reset_for_test(&mut self, effects: &mut Vec<Effect>) {
        let kept = (self.route.clip_distance, self.route.execution_count, self.route.candidate_columns, self.route.unpublished_candidates);
        *self = Self::new();
        (self.route.clip_distance, self.route.execution_count, self.route.candidate_columns, self.route.unpublished_candidates) = kept;
        reset_generations();
        effects.push(Effect::Clear);
    }

    /// `beginVisibleFrameForTest` and, with `enabled`,
    /// `beginRustOpaqueRouteFrameForTest`.
    pub(crate) fn begin_test_frame(&mut self, enabled: bool) {
        self.pending_segments.clear();
        self.next_visible_order = 0;
        self.frame = Frame { enabled, flags: 0 };
        self.route.frame += 1;
        self.route.decision = "test-preflight".into();
        self.route.reason = "pending".into();
        self.route.opaque_segments = 0;
        self.route.transparent_segments = 0;
        self.route.water_segments = 0;
        self.route.visible_columns = 0;
        self.route.unpublished_visible_columns = 0;
        self.route.selected = false;
        if !enabled {
            self.route.cached_columns = self.columns.len() as i32;
            return;
        }
        self.route.cached_columns = 0;
        self.route.candidate_columns = 0;
        self.route.unpublished_candidates = 0;
        let r = &mut self.receipts;
        r.build_attempts = 0;
        r.built = 0;
        r.reused = 0;
        r.replaced = 0;
        r.last_payload_difference = "none".into();
        r.last_payload_change_route_frame = i64::MIN;
        r.last_visible_set_signature = i64::MIN;
        r.last_visible_set_change_route_frame = i64::MIN;
        let route = &mut self.route;
        route.last_executed_route_frame = 0;
        route.last_executed_world_frame = 0;
        route.last_executed_submission = 0;
        route.last_executed_capture_frame = 0;
        route.last_executed_instances = 0;
        route.last_executed_opaque = 0;
        route.last_executed_transparent = 0;
        route.last_executed_water = 0;
        route.last_executed_semantics = false;
    }
}
