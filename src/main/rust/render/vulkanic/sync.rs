use std::collections::VecDeque;

use super::handles::Handle;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct SubmissionId(pub u64);

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, PartialEq)]
pub struct SyncToken {
    pub submission: SubmissionId,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RetiredResource {
    pub handle: Handle,
    pub submission: SubmissionId,
}

#[derive(Clone, Debug, Default)]
pub struct RetirementQueue {
    pending: VecDeque<RetiredResource>,
}

impl RetirementQueue {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn defer(&mut self, handle: Handle, submission: SubmissionId) {
        self.pending
            .push_back(RetiredResource { handle, submission });
    }

    pub fn drain_completed(&mut self, completed: SubmissionId) -> Vec<RetiredResource> {
        let mut retired = Vec::new();
        let mut remaining = VecDeque::new();
        while let Some(resource) = self.pending.pop_front() {
            if resource.submission <= completed {
                retired.push(resource);
            } else {
                remaining.push_back(resource);
            }
        }
        self.pending = remaining;
        retired
    }

    pub fn len(&self) -> usize {
        self.pending.len()
    }

    pub fn is_empty(&self) -> bool {
        self.pending.is_empty()
    }
}
