//! Java behavior binding leases. Native arrays own membership references.
use std::sync::Mutex;
#[derive(Default)]
struct Binding {
    generation: u32,
    references: usize,
}
struct State {
    rows: Vec<Binding>,
    spare: Vec<u32>,
    retired: Vec<u64>,
}
pub(super) struct Bindings {
    state: Mutex<State>,
}
impl Default for Bindings {
    fn default() -> Self {
        Self {
            state: Mutex::new(State {
                rows: vec![Binding::default()],
                spare: Vec::new(),
                retired: Vec::new(),
            }),
        }
    }
}
impl Bindings {
    pub(super) fn allocate(&self) -> u64 {
        let mut s = self.state.lock().unwrap();
        while let Some(slot) = s.spare.pop() {
            if let Some(generation) = s.rows[slot as usize].generation.checked_add(1) {
                s.rows[slot as usize] = Binding {
                    generation,
                    references: 1,
                };
                return (u64::from(generation) << 32) | u64::from(slot);
            }
        }
        let slot = u32::try_from(s.rows.len()).expect("section binding extent exhausted");
        assert!(slot <= i32::MAX as u32);
        s.rows.push(Binding {
            generation: 1,
            references: 1,
        });
        (1u64 << 32) | u64::from(slot)
    }
    pub(super) fn retain(&self, token: u64) {
        if token == 0 {
            return;
        }
        let mut s = self.state.lock().unwrap();
        let row = &mut s.rows[token as u32 as usize];
        assert_eq!(row.generation, (token >> 32) as u32);
        assert!(row.references > 0);
        row.references += 1;
    }
    pub(super) fn release(&self, token: u64) {
        if token == 0 {
            return;
        }
        let mut s = self.state.lock().unwrap();
        let row = &mut s.rows[token as u32 as usize];
        assert_eq!(row.generation, (token >> 32) as u32);
        assert!(row.references > 0);
        row.references -= 1;
        if row.references == 0 {
            s.spare.push(token as u32);
            s.retired.push(token);
        }
    }
    pub(super) fn retired(&self) -> u64 {
        self.state.lock().unwrap().retired.pop().unwrap_or(0)
    }
}
