//! Policy over the actual destination-map VM resource exposed by the original
//! virtual removeTicketIf method. There is one backing map, with its original
//! identity, iterators, aliases and foreign List resources; no native mirror.
#[no_mangle]
pub extern "C" fn mattmc_ticket_vm_map_begin(mode: i32) -> u64 {
    ((mode as u32 as u64) << 8) | 1
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_vm_map_next(command: u64, answer: i32) -> u64 {
    let mode = (command >> 8) as u32;
    let op = command as u8;
    let next = match mode {
        0 => match op { // activation: map entry -> actual list -> actual addTicket
            1 => 2,
            2 => if answer != 0 { 3 } else { 9 },
            3 => 4,
            4 => 5,
            5 => if answer != 0 { 6 } else { 2 },
            6 => 7,
            7 => 8,
            8 => 5,
            9 => 10,
            value => value,
        },
        1 => match op { // persisted deactivated pairs, after native active traversal
            1 => 2,
            2 => if answer != 0 { 3 } else { 10 },
            3 => 4,
            4 => 5,
            5 => 6,
            6 => if answer != 0 { 7 } else { 2 },
            7 => 8,
            8 => if answer != 0 { 9 } else { 6 },
            9 => 6,
            value => value,
        },
        _ => match op { // decoded pairs: original computeIfAbsent before getSecond
            1 => 2,
            2 => if answer != 0 { 3 } else { 10 },
            3 => 4,
            4 => 5,
            5 => 6,
            6 => 7,
            7 => 2,
            value => value,
        },
    };
    ((mode as u64) << 8) | next as u64
}
#[cfg(test)]
mod tests {
    use super::*;
    fn check(mode: i32, steps: &[(u8, i32)], final_op: u8) {
        let mut command=mattmc_ticket_vm_map_begin(mode);
        for &(op, answer) in steps {
            assert_eq!(command as u8,op);
            assert_eq!(command >> 8,mode as u64);
            command=mattmc_ticket_vm_map_next(command,answer);
        }
        assert_eq!(command as u8,final_op);
    }
    #[test] fn activation_reads_key_after_next_and_clears_only_after_iteration() {
        check(0,&[(1,0),(2,1),(3,0),(4,0),(5,1),(6,0),(7,0),(8,0),(5,0),(2,0),(9,0)],10);
    }
    #[test] fn persisted_walk_checks_each_actual_ticket_and_short_circuits_append() {
        check(1,&[(1,0),(2,1),(3,0),(4,0),(5,0),(6,1),(7,0),(8,0),(6,1),(7,0),(8,1),(9,0),(6,0),(2,0)],10);
    }
    #[test] fn decoded_append_gets_ticket_after_destination_creation() {
        check(2,&[(1,0),(2,1),(3,0),(4,0),(5,0),(6,0),(7,0),(2,0)],10);
    }
}
