//! Native source-state consumer. Derives the real 5x256 sky-seeding snapshot.
//! No Java height matrix or source policy is authoritative; source handles are borrowed
//! synchronously while the caller fences each VM facade. This ordinary call may lock.
use super::{Error, Source};
fn capture(source: &Source) -> Result<[i32; 256], Error> {
    let store = source.0.lock().map_err(|_| Error::Poisoned)?;
    let mut values = [0; 256];
    for (index, value) in values.iter_mut().enumerate() {
        let y = store.get(index as i32)?.wrapping_add(store.min);
        *value = if y == store.min { i32::MIN } else { y };
    }
    Ok(values)
}
fn columns(sources: [&Source; 5], output: &mut [i32; 1280]) -> Result<(), Error> {
    let values = [capture(sources[0])?, capture(sources[1])?, capture(sources[2])?,
        capture(sources[3])?, capture(sources[4])?];
    for z in 0..16 {
        for x in 0..16 {
            let index = z * 16 + x;
            output[index] = values[0][index];
            output[256 + index] = if z == 0 { values[1][240 + x] } else { values[0][index - 16] };
            output[512 + index] = if z == 15 { values[2][x] } else { values[0][index + 16] };
            output[768 + index] = if x == 0 { values[3][z * 16 + 15] } else { values[0][index - 1] };
            output[1024 + index] = if x == 15 { values[4][z * 16] } else { values[0][index + 1] };
        }
    }
    Ok(())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_seed_columns(a: u64, b: u64, c: u64, d: u64,
    e: u64, output: *mut [i32; 1280]) -> i32 {
    if [a,b,c,d,e].contains(&0) || output.is_null() || output as usize % 4 != 0 { return -1; }
    let sources = [a,b,c,d,e].map(|id| &*(id as usize as *const Source));
    columns(sources, &mut *output).map_or(-1, |_| 0)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_sources_feed_edges_and_center_with_independent_snapshot_lifetime() {
        let sources = std::array::from_fn::<_,5,_>(|_|Source::new(-65,9).unwrap());
        for (side,source) in sources.iter().enumerate() {
            for index in 0..256 {source.set(index, side as i32 * 32 + index % 29).unwrap();}
        }
        let mut output = [0;1280];
        columns(std::array::from_fn(|i|&sources[i]), &mut output).unwrap();
        for z in 0..16 {for x in 0..16 {
            let i=z*16+x;
            assert_eq!(output[i as usize],i%29);
            assert_eq!(output[256+i as usize],if z==0 {32+(240+x)%29}else{(i-16)%29});
            assert_eq!(output[512+i as usize],if z==15 {64+x%29}else{(i+16)%29});
            assert_eq!(output[768+i as usize],if x==0 {96+(z*16+15)%29}else{(i-1)%29});
            assert_eq!(output[1024+i as usize],if x==15 {128+(z*16)%29}else{(i+1)%29});
        }}
        drop(sources);
        assert_eq!(output[0],0);
        assert_eq!(output[256],32+240%29);
    }
}
