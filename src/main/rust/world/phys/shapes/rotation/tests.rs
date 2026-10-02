use super::{ffi::mattmc_voxel_rotate, transform::transform};
const PERMS: [[usize; 3]; 6] = [[0,1,2],[0,2,1],[1,0,2],[1,2,0],[2,0,1],[2,1,0]];

// Independent cell loop and per-cell coordinate mapping; no affine strides/word scans.
fn original(words: &[u64], d: [usize; 3], p: [usize; 3], flips: u32) -> (Vec<u64>, [i32; 6]) {
    let target = p.map(|a| d[a]);
    let mut out = vec![0u64; (d.iter().product::<usize>() + 63) / 64];
    let mut bounds = [target[0] as i32,target[1] as i32,target[2] as i32,0,0,0];
    for x in 0..d[0] { for y in 0..d[1] { for z in 0..d[2] {
        let at = (x*d[1]+y)*d[2]+z;
        if words.get(at/64).copied().unwrap_or(0) & (1 << (at%64)) == 0 { continue; }
        let source = [x,y,z]; let mut pos = [0;3];
        for t in 0..3 {
            pos[t] = if flips & (1<<t) == 0 {source[p[t]]} else {target[t]-1-source[p[t]]};
            bounds[t] = bounds[t].min(pos[t] as i32); bounds[t+3] = bounds[t+3].max(pos[t] as i32+1);
        }
        let dest = (pos[0]*target[1]+pos[1])*target[2]+pos[2]; out[dest/64] |= 1 << (dest%64);
    }}}
    (out,bounds)
}
fn compare(words: &[u64], d: [usize;3]) {
    for p in PERMS { for f in 0..8 {
        let (expected,b) = original(words,d,p,f);
        let mut out = vec![u64::MAX;expected.len()]; let mut bounds=[-77;6];
        transform(words,d,p,f,&mut out,&mut bounds);
        assert_eq!(out,expected,"dims={d:?} axes={p:?} flips={f}"); assert_eq!(bounds,b);
        let control=p[0] as i32|(p[1] as i32)<<2|(p[2] as i32)<<4|(f as i32)<<6;
        out.fill(u64::MAX);bounds.fill(-77);
        if words.len() > 1024 {
            unsafe { assert_eq!(mattmc_voxel_rotate(words.as_ptr(),words.len() as i32,d[0] as i32,
                d[1] as i32,d[2] as i32,control,out.as_mut_ptr(),out.len() as i32,bounds.as_mut_ptr()),-1); }
            assert!(out.iter().all(|&w|w==u64::MAX));assert_eq!(bounds,[-77;6]);continue;
        }
        unsafe { assert_eq!(mattmc_voxel_rotate(words.as_ptr(),words.len() as i32,d[0] as i32,
            d[1] as i32,d[2] as i32,control,out.as_mut_ptr(),out.len() as i32,bounds.as_mut_ptr()),0); }
        assert_eq!(out,expected);assert_eq!(bounds,b);
    }}
}
#[test]
fn exhaustive_all_48_transforms() {
    for bits in 0..4096 { compare(&[bits], [2,2,3]); }
    eprintln!("ROTATION_RUST_PARITY exhaustive_grids=4096 transformations=196608 kernel_and_ffi=true");
}
#[test]
fn word_boundaries_padding_compressed_empty_and_large_grids() {
    let mut state=1977u64;
    for d in [[1,1,63],[1,1,64],[1,1,65],[8,8,8],[17,9,7],[3,3,65],[8,1,256],[256,1,256],[16,16,16]] {
        let len=(d.iter().product::<usize>()+63)/64;
        compare(&[],d);compare(&vec![u64::MAX;len+1],d);
        for _ in 0..8 {
            let words:Vec<u64>=(0..len).map(|_|{state^=state<<13;state^=state>>7;state^=state<<17;state}).collect();
            compare(&words,d);
        }
        compare(&[1],d);
    }
}
#[test]
fn ffi_invalid_metadata_publishes_nothing() {
    let input=[1u64;8];let mut out=[77u64;8];let mut bounds=[91;6];
    let ok=0|(1<<2)|(2<<4);
    for (nx,ny,nz,c,len,n) in [(0,8,8,ok,8,8),(257,1,2,ok,8,8),(256,256,2,ok,8,8),
        (8,8,8,512,8,8),(8,8,8,-1,8,8),(8,8,8,0,8,8),(8,8,8,3,8,8),
        (8,8,8,ok,7,8),(8,8,8,ok,8,-1),(8,8,8,ok,8,1025)] {
        unsafe{assert_eq!(mattmc_voxel_rotate(input.as_ptr(),n,nx,ny,nz,c,out.as_mut_ptr(),len,bounds.as_mut_ptr()),-1);}
        assert_eq!(out,[77;8]);assert_eq!(bounds,[91;6]);
    }
    unsafe {
        assert_eq!(mattmc_voxel_rotate(std::ptr::null(),8,8,8,8,ok,out.as_mut_ptr(),8,bounds.as_mut_ptr()),-1);
        assert_eq!(mattmc_voxel_rotate(input.as_ptr(),8,8,8,8,ok,std::ptr::null_mut(),8,bounds.as_mut_ptr()),-1);
        assert_eq!(mattmc_voxel_rotate(input.as_ptr(),8,8,8,8,ok,out.as_mut_ptr(),8,std::ptr::null_mut()),-1);
        assert_eq!(mattmc_voxel_rotate((input.as_ptr() as *const u8).add(1) as *const u64,8,8,8,8,ok,out.as_mut_ptr(),8,bounds.as_mut_ptr()),-1);
    }
}
