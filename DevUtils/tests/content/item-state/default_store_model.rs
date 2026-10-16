#[path = "../../../../src/main/rust/content/item/runtime/inventory.rs"] mod inventory;
#[path = "../../../../src/main/rust/content/item/runtime/stack.rs"] mod stack;
#[path = "../../../../src/main/rust/content/item/runtime/slots.rs"] mod slots;
// Focused tests of the real default owner; no duplicate map implementation.
#[path = "../../../../src/main/rust/content/item/runtime/patch.rs"] mod patch;
use patch::defaults::{Store,Row,Cursor};
fn row(key:u64,hash:u32,value:i32)->Row { Row{key,hash,leaf:key as i64,kind:1,scalar:value,reserved:0} }
#[test] fn defaults_wrap_remove_cursor_keeps_all_rows_once() {
    // Force the same home bucket at the end of a16-row table.
    let hashes:Vec<u32>=(1u32..).filter(|h|{let x=h.wrapping_mul(0x9e3779b9);((x^(x>>16))&15)==14}).take(12).collect();
    let mut store=Store::new(12);for(i,hash)in hashes.into_iter().enumerate(){store.put(row(i as u64+1,hash,i as i32));}
    assert_eq!(store.n(),16);let mut cursor=Cursor::new(&store);let mut seen=Vec::new();
    while cursor.has_next(&store){let index=cursor.next(&store).unwrap();let value=store.at(index).unwrap();seen.push(value.key);assert_eq!(cursor.remove(&mut store).unwrap().key,value.key);}
    seen.sort();assert_eq!(seen,(1..=12).collect::<Vec<_>>());assert_eq!(store.len(),0);
}
#[test] fn defaults_small_array_order_and_live_cursor_shift_are_native() {
    let mut store=Store::new(3);for i in 1..=3 {store.put(row(i,i as u32,i as i32));}
    let mut cursor=Cursor::new(&store);assert_eq!(cursor.next(&store),Some(0));cursor.remove(&mut store);
    assert_eq!(store.at(cursor.next(&store).unwrap()).unwrap().key,2);
    store.remove(3,3);assert!(!cursor.has_next(&store));assert_eq!(store.scalar(1),Some(2));
}
#[test] fn defaults_typed_value_is_in_same_mutable_membership_owner() {
    let mut store=Store::new(12);store.put(row(1,22,64));assert_eq!(store.scalar(1),Some(64));
    store.put(row(1,22,16));assert_eq!(store.scalar(1),Some(16));store.remove(1,22);assert_eq!(store.scalar(1),None);
}

#[test] fn defaults_typed_index_resolves_canonical_rows_after_rehash_shift_and_clear() {
    let mut store=Store::new(8);store.put(row(1,77,64));
    for key in 2..=200 {store.put(Row{key,hash:key as u32,leaf:key as i64,kind:0,scalar:0,reserved:0});}
    assert_eq!(store.scalar(1),Some(64));for key in 2..=200 {store.remove(key,key as u32);}
    assert_eq!(store.scalar(1),Some(64));assert!(store.scalar_valid(1));
    store.put(Row{key:1,hash:77,leaf:300,kind:0x80000001,scalar:0,reserved:0});assert!(!store.scalar_valid(1));assert_eq!(store.scalar(1),None);
    store.clear();assert!(store.scalar_valid(1));assert_eq!(store.scalar(1),None);
}

#[test] fn defaults_hash_cursor_clear_keeps_original_wrapped_null_failure_state() {
    let mut store=Store::new(12);for key in 1..=12 {store.put(row(key,key as u32,key as i32));}
    let mut cursor=Cursor::new(&store);assert!(cursor.next(&store).is_some());store.clear();
    assert!(cursor.has_next(&store));assert_eq!(cursor.next(&store),None);assert_eq!(cursor.failure,1);
    assert_eq!(cursor.next(&store),None);assert_eq!(cursor.failure,1);
}
#[test] fn defaults_hash_foreach_throw_defers_remaining_count_until_callback_completes() {
    let mut store=Store::new(12);for key in 1..=12 {store.put(row(key,key as u32,key as i32));}
    let mut cursor=Cursor::new(&store);assert!(cursor.next_foreach(&store).is_some());
    // Model a thrown consumer: do not acknowledge foreach_complete.
    let mut resumed=0;while cursor.has_next(&store){if cursor.next(&store).is_some(){resumed+=1;}else{break;}}
    assert_eq!(resumed,11);assert_eq!(cursor.failure,1);
}
