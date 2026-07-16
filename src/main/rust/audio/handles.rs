use std::collections::HashMap;

pub(crate) struct HandleTable<T> {
    next: u64,
    entries: HashMap<u64, T>,
}

impl<T> Default for HandleTable<T> {
    fn default() -> Self {
        Self {
            next: 0,
            entries: HashMap::new(),
        }
    }
}

impl<T> HandleTable<T> {
    pub(crate) fn insert(&mut self, value: T) -> u64 {
        self.next = self.next.wrapping_add(1).max(1);
        while self.entries.contains_key(&self.next) {
            self.next = self.next.wrapping_add(1).max(1);
        }
        let handle = self.next;
        self.entries.insert(handle, value);
        handle
    }

    pub(crate) fn get(&self, handle: u64) -> Option<&T> {
        self.entries.get(&handle)
    }

    pub(crate) fn get_mut(&mut self, handle: u64) -> Option<&mut T> {
        self.entries.get_mut(&handle)
    }

    pub(crate) fn remove(&mut self, handle: u64) -> Option<T> {
        self.entries.remove(&handle)
    }

    pub(crate) fn handles_for<F>(&self, mut f: F) -> Vec<u64>
    where
        F: FnMut(&T) -> bool,
    {
        self.entries
            .iter()
            .filter_map(|(handle, value)| f(value).then_some(*handle))
            .collect()
    }

    #[cfg(test)]
    pub(crate) fn len(&self) -> usize {
        self.entries.len()
    }
}
