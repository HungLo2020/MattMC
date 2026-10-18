//! Exact stock furnace owner: its three rows and four timers, and the per-tick
//! decisions of `AbstractFurnaceBlockEntity.serverTick`. Java begins each tick
//! with the original code (lit decrement on the native timers, slot reads,
//! recipe lookup, `getMaxStackSize`); one native step then decides both canBurn
//! checks and the cook timer. The rare lighting (fuel duration, fuel shrink,
//! crafting remainder) and burn (cook time, `burn`, recipe used) branches stay
//! original Java code, with one more step after lighting. A decision only
//! Java can make (foreign component equality, an unknown maximum size) returns a
//! fallback code before the step changes anything, and Java continues the
//! original code from that point.
use super::slots::{visible_count, BoundaryOwner, Slots};
use super::stack::Stack;
use std::sync::atomic::{AtomicI32, Ordering};
use std::sync::{Arc, Mutex};

pub const LIT: usize = 0;
pub const LIT_TOTAL: usize = 1;
pub const COOK: usize = 2;
pub const COOK_TOTAL: usize = 3;

/// Step codes (low byte) returned to Java.
pub const DONE: i64 = 0;
/// Run the original lighting block (`!isLit() && canBurn` held), then step 2.
pub const NEED_LIGHT: i64 = 2;
/// The cook timer reached its total and was reset: run the original
/// `cookingTotalTime = getTotalCookTime(...)`, `burn`, `setRecipeUsed`, `bl2 = true`.
pub const NEED_BURN: i64 = 3;
/// Rows unbound or pending: continue Java at this step's segment; nothing changed.
pub const FALLBACK_BEGIN: i64 = 0x10;
/// Continue Java at the first `!isLit() && canBurn(...)`.
pub const FALLBACK_LIGHT: i64 = 0x11;
/// Continue Java at the second `isLit() && canBurn(...)`.
pub const FALLBACK_COOK: i64 = 0x12;

#[repr(C)]
pub struct Timers { pub values: [AtomicI32; 4] }

pub struct Furnace {
    pub timers: Timers,
    rows: Mutex<Option<Arc<BoundaryOwner<Slots>>>>,
}

impl Furnace {
    pub fn new(rows: Option<Arc<BoundaryOwner<Slots>>>) -> Self {
        Self { timers: Timers { values: Default::default() }, rows: Mutex::new(rows) }
    }
    pub fn timers_address(&self) -> usize { &self.timers as *const Timers as usize }
    pub fn bind(&self, rows: Option<Arc<BoundaryOwner<Slots>>>) { *self.rows.lock().unwrap() = rows; }
    fn get(&self, index: usize) -> i32 { self.timers.values[index].load(Ordering::Relaxed) }
    fn set(&self, index: usize, value: i32) { self.timers.values[index].store(value, Ordering::Relaxed) }
    fn lit(&self) -> bool { self.get(LIT) > 0 }

    /// One step after Java began the tick (lit decrement, slot reads, recipe
    /// lookup, getMaxStackSize). `template` is the stock recipe result (None
    /// without a recipe); `arg` = present << 32 | max stack size. Phase 1 runs
    /// both canBurn checks and the cook timer, or stops for Java's lighting
    /// block; phase 2 continues after it.
    pub fn step(&self, phase: i64, template: Option<&Stack>, arg: i64, air: u64) -> i64 {
        let guard = self.rows.lock().unwrap();
        let Some(rows) = guard.as_ref() else { return FALLBACK_BEGIN };
        let rows = rows.lock().unwrap();
        if rows.len() != 3 || (0..3).any(|i| rows.get(i).is_none_or(|entry| super::slots::is_pending(entry.facade))) { return FALLBACK_BEGIN; }
        let (present, max) = (arg >> 32 != 0, arg as i32);
        match phase {
            1 => {
                if !self.lit() {
                    match can_burn(&rows, template, present, max, air) {
                        None => return FALLBACK_LIGHT,
                        Some(true) => return NEED_LIGHT,
                        Some(false) => {}
                    }
                }
                self.cook(&rows, template, present, max, air)
            }
            2 => self.cook(&rows, template, present, max, air),
            _ => FALLBACK_BEGIN,
        }
    }

    /// The original `if (isLit() && canBurn(...)) { cookingTimer++; ... } else cookingTimer = 0`.
    fn cook(&self, rows: &Slots, template: Option<&Stack>, present: bool, max: i32, air: u64) -> i64 {
        if !self.lit() { self.set(COOK, 0); return DONE; }
        match can_burn(rows, template, present, max, air) {
            None => FALLBACK_COOK,
            Some(false) => { self.set(COOK, 0); DONE }
            Some(true) => {
                self.set(COOK, self.get(COOK) + 1);
                if self.get(COOK) == self.get(COOK_TOTAL) { self.set(COOK, 0); NEED_BURN } else { DONE }
            }
        }
    }
}

/// The original `canBurn`: None when only Java can decide (foreign equality or
/// an unknown maximum size). Reads only.
pub fn can_burn(rows: &Slots, template: Option<&Stack>, present: bool, max: i32, air: u64) -> Option<bool> {
    if visible_count(&rows.get(0)?.stack, air) == 0 || !present { return Some(false); }
    let template = template?;
    if visible_count(template, air) == 0 { return Some(false); }
    let result = &rows.get(2)?.stack;
    if visible_count(result, air) == 0 { return Some(true); }
    if !super::slots::inventory_same(result, template, air)? { return Some(false); }
    let roles = super::components::roles()?;
    let count = visible_count(result, air);
    if count < max && count < super::components::max_size_with(result, roles)? { return Some(true); }
    Some(count < super::components::max_size_with(template, roles)?)
}

#[cfg(test)] mod tests {
    use super::*;
    use super::super::patch::Overlay;
    use super::super::slots::Entry;
    const AIR: u64 = 1;
    const ARG: i64 = 1 << 32 | 64;
    fn stack(item: u64, count: i32) -> Arc<Stack> { Arc::new(Stack::from_overlay(item, count, Overlay::new(item))) }
    fn furnace(input: (u64, i32), fuel: (u64, i32), result: (u64, i32)) -> (Furnace, Arc<BoundaryOwner<Slots>>) {
        let rows = Arc::new(BoundaryOwner::new(Slots::new(3, Entry { facade: 1, stack: stack(AIR, 0) })));
        { let mut guard = rows.lock().unwrap();
          for (index, (item, count)) in [input, fuel, result].into_iter().enumerate() { guard.set(index, Entry { facade: 10 + index as i64, stack: stack(item, count) }); } }
        (Furnace::new(Some(rows.clone())), rows)
    }

    #[test] fn no_recipe_resets_the_cook_timer() {
        let (f, _) = furnace((5, 3), (7, 1), (AIR, 0));
        f.set(LIT, 9); f.set(COOK, 3);
        assert_eq!(f.step(1, None, 64, AIR), DONE);
        assert_eq!(f.get(COOK), 0);
    }
    #[test] fn empty_result_slot_cooks_until_the_total_then_hands_burn_to_java() {
        let (f, _) = furnace((5, 3), (7, 1), (AIR, 0));
        let template = stack(9, 1);
        f.set(LIT, 10); f.set(COOK_TOTAL, 2);
        assert_eq!(f.step(1, Some(&template), ARG, AIR), DONE);
        assert_eq!(f.get(COOK), 1);
        assert_eq!(f.step(1, Some(&template), ARG, AIR), NEED_BURN);
        assert_eq!(f.get(COOK), 0, "reset before Java recomputes the total");
    }
    #[test] fn unlit_furnace_with_a_burnable_input_asks_java_to_light_then_cooks() {
        let (f, _) = furnace((5, 3), (7, 1), (AIR, 0));
        let template = stack(9, 1);
        assert_eq!(f.step(1, Some(&template), ARG, AIR), NEED_LIGHT);
        f.set(LIT, 40); f.set(LIT_TOTAL, 40); f.set(COOK_TOTAL, 200);
        assert_eq!(f.step(2, Some(&template), ARG, AIR), DONE);
        assert_eq!(f.get(COOK), 1);
    }
    #[test] fn a_different_result_item_cannot_burn_and_a_matching_one_needs_java_without_roles() {
        let (f, rows) = furnace((5, 3), (7, 1), (8, 4));
        let template = stack(9, 1);
        f.set(LIT, 10); f.set(COOK, 4);
        assert_eq!(f.step(1, Some(&template), ARG, AIR), DONE, "other item: canBurn false");
        assert_eq!(f.get(COOK), 0);
        rows.lock().unwrap().set(2, Entry { facade: 12, stack: stack(9, 4) });
        f.set(COOK, 4);
        assert_eq!(f.step(1, Some(&template), ARG, AIR), FALLBACK_COOK, "same item, no installed roles: Java decides");
        assert_eq!(f.get(COOK), 4, "a fallback changes nothing");
    }
    #[test] fn pending_or_unbound_rows_continue_in_java_without_changes() {
        let (f, rows) = furnace((5, 3), (7, 1), (AIR, 0));
        f.set(LIT, 3); f.set(COOK, 2);
        rows.lock().unwrap().set(0, Entry { facade: super::super::slots::next_pending_key(), stack: stack(5, 3) });
        assert_eq!(f.step(1, None, ARG, AIR), FALLBACK_BEGIN);
        assert_eq!((f.get(LIT), f.get(COOK)), (3, 2), "nothing changed");
        f.bind(None);
        assert_eq!(f.step(1, None, ARG, AIR), FALLBACK_BEGIN);
    }
}
