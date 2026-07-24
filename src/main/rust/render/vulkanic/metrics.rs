use std::collections::BTreeMap;
use std::time::Instant;

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct Metrics {
    pub tracy_enabled: bool,
    pub zones: BTreeMap<&'static str, ZoneMetrics>,
    pub resource_creates: u64,
    pub resource_destroys: u64,
    pub validation_failures: u64,
    pub submissions: u64,
    pub deferred_retires: u64,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct ZoneMetrics {
    pub count: u64,
    pub total_nanos: u128,
}

impl Metrics {
    pub fn new(tracy_enabled: bool) -> Self {
        Self {
            tracy_enabled,
            ..Self::default()
        }
    }

    pub fn zone(&mut self, name: &'static str) -> TracyZone<'_> {
        let enabled = self.tracy_enabled;
        TracyZone {
            name,
            metrics: self,
            start: Instant::now(),
            enabled,
        }
    }
}

pub struct TracyZone<'a> {
    name: &'static str,
    metrics: &'a mut Metrics,
    start: Instant,
    enabled: bool,
}

impl Drop for TracyZone<'_> {
    fn drop(&mut self) {
        let record = self.metrics.zones.entry(self.name).or_default();
        record.count += 1;
        if self.enabled {
            record.total_nanos += self.start.elapsed().as_nanos();
        }
    }
}
