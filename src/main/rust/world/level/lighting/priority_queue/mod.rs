//! Leveled FIFO work queue behind the Rust chunk/section distance graphs
//! (`world::level::chunk_distance`): the original `LeveledPriorityQueue`
//! semantics, with the graph's compound scheduling operations. No Java adapter.
mod ordered_set;
mod position_hash;
pub(crate) mod queue;
#[cfg(test)]
mod tests;
