//! Data-parallel loops on scoped threads, for reload work such as decoding
//! textures or baking models: results keep input order. Workers hold their
//! Java log lines, which the calling thread logs after they join.

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Mutex;

use super::java_log;

/// Logs the lines workers held, on the calling thread.
fn release(lines: Mutex<Vec<(i32, String)>>) {
    for (level, message) in lines.into_inner().unwrap_or_else(|poisoned| poisoned.into_inner()) {
        java_log::log(level, &message);
    }
}

/// Worker threads for a loop over `items` (at least one, at most the CPU count).
fn workers(items: usize) -> usize {
    let cpus = std::thread::available_parallelism().map_or(1, |count| count.get());
    cpus.min(items).max(1)
}

/// `items.iter().map(work)`, spread over threads in chunks.
pub(crate) fn map<T: Sync, R: Send>(items: &[T], work: impl Fn(&T) -> R + Sync) -> Vec<R> {
    let threads = workers(items.len());
    if threads <= 1 || items.len() < 2 {
        return items.iter().map(work).collect();
    }
    // Small chunks: reload work is uneven (a few huge sprites or generated items).
    let chunk = (items.len() / (threads * 64)).max(1);
    let next = AtomicUsize::new(0);
    let results: Mutex<Vec<Option<R>>> = Mutex::new((0..items.len()).map(|_| None).collect());
    let lines = Mutex::new(Vec::new());
    std::thread::scope(|scope| {
        for _ in 0..threads {
            scope.spawn(|| {
                java_log::defer();
                loop {
                    let start = next.fetch_add(chunk, Ordering::Relaxed);
                    if start >= items.len() {
                        break;
                    }
                    let end = (start + chunk).min(items.len());
                    let produced: Vec<R> = items[start..end].iter().map(&work).collect();
                    let mut results = results.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
                    for (offset, result) in produced.into_iter().enumerate() {
                        results[start + offset] = Some(result);
                    }
                }
                lines.lock().unwrap_or_else(|poisoned| poisoned.into_inner()).extend(java_log::take_deferred());
            });
        }
    });
    release(lines);
    results.into_inner().unwrap_or_else(|poisoned| poisoned.into_inner()).into_iter().map(|result| result.expect("every item ran")).collect()
}

/// `items.iter_mut().for_each(work)` over threads.
pub(crate) fn for_each_mut<T: Send>(items: &mut [T], work: impl Fn(&mut T) + Sync) {
    let threads = workers(items.len());
    if threads <= 1 {
        items.iter_mut().for_each(work);
        return;
    }
    let chunk = items.len().div_ceil(threads * 8).max(1);
    let queue = Mutex::new(items.chunks_mut(chunk));
    let lines = Mutex::new(Vec::new());
    std::thread::scope(|scope| {
        for _ in 0..threads {
            scope.spawn(|| {
                java_log::defer();
                loop {
                    let next = queue.lock().unwrap_or_else(|poisoned| poisoned.into_inner()).next();
                    match next {
                        Some(slice) => slice.iter_mut().for_each(&work),
                        None => break,
                    }
                }
                lines.lock().unwrap_or_else(|poisoned| poisoned.into_inner()).extend(java_log::take_deferred());
            });
        }
    });
    release(lines);
}

#[cfg(test)]
mod tests {
    #[test]
    fn keeps_order() {
        let items: Vec<u32> = (0..1000).collect();
        assert_eq!(items.iter().map(|value| value * 2).collect::<Vec<_>>(), super::map(&items, |value| value * 2));
        let mut values = items.clone();
        super::for_each_mut(&mut values, |value| *value += 1);
        assert_eq!(1000, values[999]);
    }
}
