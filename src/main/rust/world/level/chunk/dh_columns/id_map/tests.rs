use super::*;
fn key(b: &str, s: &str) -> Entry { Entry { key: Arc::new(Key { biome: b.encode_utf16().collect(), state: s.encode_utf16().collect() }), state_id: u32::MAX } }
#[test]
fn duplicates_import_lookup_and_self_merge_keep_order_and_signed_hashes() {
    let (a, b) = (key("minecraft:plains", "minecraft:air"), key("Aa", "BB"));
    let mut map = Map::new(); assert_eq!(map.add(a.clone(), 2), Ok(0));
    assert_eq!(map.add(a.clone(), 0), Ok(1)); assert_eq!(map.add(a.clone(), 0), Ok(1));
    assert_eq!(map.add(b.clone(), 1), Ok(2)); assert_eq!(map.add(a.clone(), 1), Ok(3));
    assert_eq!(map.add(a, 0), Ok(3)); assert_eq!(map.indexed, [0, 1, 1, 1]);
    for pos in [0, u64::MAX, 0x8000_0000_7fff_ffff, 123456789] {
        let list = map.entries.iter().fold(1i32, |h, k| h.wrapping_mul(31).wrapping_add(k.hash()));
        let expected = map.entries.iter().fold((pos ^ (pos >> 32)) as i32, |h, _| h.wrapping_mul(31).wrapping_add(list));
        assert_eq!(map.hash(pos), expected);
    }
    let before = map.entries.len(); let mut ids = vec![0; before];
    let pointer = &mut map as *mut Map;
    unsafe { assert_eq!(mattmc_dh_id_map_merge(pointer, pointer, ids.as_mut_ptr(), before as i32, 0), before as i32); }
    assert_eq!(ids, [3, 3, 2, 3]); assert_eq!(map.entries.len(), before);
    unsafe { assert_eq!(mattmc_dh_id_map_merge(pointer, pointer, ids.as_mut_ptr(), before as i32, 1), before as i32); }
    assert_eq!(ids, [4, 5, 6, 7]); assert_eq!(map.entries.len(), 8);
}
#[test]
fn utf16_hash_collisions_are_distinct_and_affine_hash_matches_long_lists() {
    let mut map = Map::new(); let (a, b) = (key("Aa", "x"), key("BB", "x"));
    assert_eq!(a.hash(), b.hash()); assert_eq!(map.add(a.clone(), 0), Ok(0)); assert_eq!(map.add(b.clone(), 0), Ok(1));
    assert_eq!(map.add(a, 0), Ok(0)); assert_eq!(map.add(b, 0), Ok(1));
    for n in 0..2048 {
        let k = key("😃\0", &format!("test:{n}")); map.add(k, 1).unwrap();
        let expected = map.entries.iter().fold(-12345i32, |h, _| h.wrapping_mul(31).wrapping_add(map.list_hash));
        assert_eq!(map.hash((-12345i64) as u64 & 0xffff_ffff), expected);
    }
}
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, n: usize) -> &[u8] { let start=self.1; self.1+=n; &self.0[start..self.1] }
    fn byte(&mut self) -> usize { self.bytes(1)[0] as usize }
    fn int(&mut self) -> i32 { i32::from_be_bytes(self.bytes(4).try_into().unwrap()) }
    fn long(&mut self) -> u64 { u64::from_be_bytes(self.bytes(8).try_into().unwrap()) }
    fn text(&mut self) -> String { let n=u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize;String::from_utf8(self.bytes(n).to_vec()).unwrap() }
}
#[test]
fn all_actual_frozen_ordered_material_map_operations_and_failure_prefixes_match() {
    use std::io::Read;
    let mut bytes=Vec::new();flate2::read::GzDecoder::new(include_bytes!("../../../../../../../test/resources/world/chunk/frozen-dh-id-map.bin.gz").as_slice()).read_to_end(&mut bytes).unwrap();
    let mut r=Reader(bytes,0);assert_eq!(r.int(),0x44484931);
    assert_eq!(r.text(),"67f8da0de69158f97fec4b3f00e00cf48827d3ae02a944de5c13c0fbb570f07a");
    assert_eq!(r.text().len(),64);assert!(r.text().contains("Actual Frozen ordered map"));
    let bank:Vec<_>=(0..r.int()).map(|_|key(&r.text(),&r.text())).collect();assert_eq!(r.int(),300);
    let mut maps=[Map::new(),Map::new(),Map::new()];let mut positions=[6,u64::MAX,0x8000_0000_7fff_ffff];let mut exceptions=0;
    for step in 0..300 {
        let op=r.byte();let dst=r.byte();let src=r.byte();let entry=r.int() as usize;let pos=r.long();
        let mut exception=String::new();let mut message=String::new();let mut ids=Vec::new();
        match op {
            0 => ids.push(maps[dst].add(bank[entry].clone(),0).unwrap()),
            1|2 => {
                let count=maps[src].entries.len();ids.resize(count,0);let before=maps[dst].entries.len();let base=maps.as_mut_ptr();
                unsafe { assert_eq!(mattmc_dh_id_map_merge(base.add(dst),base.add(src),ids.as_mut_ptr(),count as i32,(op==2) as i32),count as i32); }
                if op==2 { assert_ne!(src,dst);ids.clear(); }
                else if src==dst && maps[dst].entries.len()>before {
                    exception="ArrayIndexOutOfBoundsException".into();message=format!("Index {count} out of bounds for length {count}");ids.clear();exceptions+=1;
                }
            },
            3 => { maps[dst]=Map::new();positions[dst]=pos; },
            4 => { maps[dst].lookup.clear();maps[dst].indexed.fill(0); },
            _ => panic!("Unsupported action"),
        }
        assert_eq!(r.text(),exception,"step{step} exception");assert_eq!(r.text(),message,"step{step} message");
        let expected:Vec<_>=(0..r.int()).map(|_|r.int()).collect();assert_eq!(ids,expected,"step{step} remap");
        for i in 0..3 {
            assert_eq!(r.long(),positions[i]);assert_eq!(r.int(),maps[i].hash(positions[i]),"step{step} map{i} hash");
            assert_eq!(r.int() as usize,maps[i].entries.len());
            for actual in &maps[i].entries {assert_eq!(r.text().encode_utf16().collect::<Vec<_>>(),*actual.biome);assert_eq!(r.text().encode_utf16().collect::<Vec<_>>(),*actual.state);}
        }
    }
    assert!(exceptions>0);assert_eq!(r.1,r.0.len());
}

#[test]
fn material_state_identity_follows_the_selected_binding_across_merge_and_duplicates() {
    let mut source = Map::default(); let mut target = Map::default();
    let mut original = key("biome:a", "state:same"); original.state_id = 41;
    let mut incoming = original.clone(); incoming.state_id = 99;
    target.add(original, 0).unwrap(); source.add(incoming.clone(), 1).unwrap();
    let mut another = key("biome:b", "state:other"); another.state_id = 7; source.add(another, 2).unwrap();
    let mut ids = [0; 2];
    unsafe { assert_eq!(mattmc_dh_id_map_merge(&mut target, &source, ids.as_mut_ptr(), 2, 0), 2); }
    assert_eq!(ids, [0, 1]); assert_eq!(target.state_ids().collect::<Vec<_>>(), [41, 7]);
    target.add(incoming, 1).unwrap(); assert_eq!(target.state_ids().collect::<Vec<_>>(), [41, 7, 99]);
    drop(source); assert_eq!(target.state_ids().collect::<Vec<_>>(), [41, 7, 99]);
    // Default construction must keep the same empty-list hash seed as the ABI constructor.
    assert_eq!(Map::default().list_hash, Map::new().list_hash);
}
