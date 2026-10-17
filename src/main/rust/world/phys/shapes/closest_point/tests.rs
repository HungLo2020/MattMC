use super::{evaluate::evaluate, ffi::mattmc_voxel_closest_point};

fn literal(mut bits: Vec<bool>, dims: [usize; 3], coords: &[f64], q: [f64; 3]) -> Option<[f64; 3]> {
    let [nx, ny, nz] = dims;
    let index = |x, y, z| (x * ny + y) * nz + z;
    let mut best: Option<[f64; 3]> = None;
    let distance = |p: [f64; 3]| { let d = [p[0]-q[0],p[1]-q[1],p[2]-q[2]]; d[0]*d[0]+d[1]*d[1]+d[2]*d[2] };
    for y in 0..ny { for x in 0..nx {
        let mut z = 0;
        while z < nz {
            if !bits[index(x,y,z)] { z += 1; continue; }
            let start = z;
            while z < nz && bits[index(x,y,z)] { z += 1; }
            let end = z;
            for zz in start..end { bits[index(x,y,zz)] = false; }
            let mut xx = x + 1;
            while xx < nx && (start..end).all(|zz|bits[index(xx,y,zz)]) {
                for zz in start..end { bits[index(xx,y,zz)] = false; } xx += 1;
            }
            let mut yy = y + 1;
            while yy < ny && (x..xx).all(|a|(start..end).all(|zz|bits[index(a,yy,zz)])) {
                for a in x..xx { for zz in start..end { bits[index(a,yy,zz)] = false; } } yy += 1;
            }
            let p = [q[0].max(coords[x]).min(coords[xx]),
                q[1].max(coords[nx+1+y]).min(coords[nx+1+yy]),
                q[2].max(coords[nx+ny+2+start]).min(coords[nx+ny+2+end])];
            if best.is_none() || distance(p) < distance(best.unwrap()) { best = Some(p); }
        }
    } }
    best
}

#[test]
fn exhaustive_ordered_small_grids_through_kernel_and_ffi() {
    let dims = [2,2,3]; let coords = [0.,0.5,1.,0.,0.5,1.,0.,0.25,0.75,1.];
    for pattern in 0..4096u64 {
        let bits = (0..12).map(|i|pattern & (1 << i) != 0).collect::<Vec<_>>();
        for q in [[-1.,0.2,0.9],[0.5,0.5,0.5],[1.5,1.5,-1.]] {
            let expected = literal(bits.clone(), dims, &coords, q);
            let mut words = [pattern];
            assert_eq!(evaluate(&mut words, dims, &coords, 0, q), expected);
            let mut words = [pattern]; let mut output = [123.;3];
            let status = unsafe { mattmc_voxel_closest_point(words.as_mut_ptr(),1,2,2,3,coords.as_ptr(),10,0,
                q[0],q[1],q[2],output.as_mut_ptr()) };
            assert_eq!(status, i32::from(expected.is_some()));
            if let Some(p) = expected { assert_eq!(output.map(f64::to_bits),p.map(f64::to_bits)); }
            else { assert_eq!(output,[123.;3]); }
        }
    }
}

#[test]
fn signed_zero_reversed_bounds_ties_and_overflow() {
    let mut words = [3];
    let coords = [0.,-0.,0.,1.,0.,1.];
    let p = evaluate(&mut words,[1,1,2],&[0.,-0.,0.,1.,0.,1.,2.],0,[0.,0.5,0.5]).unwrap();
    assert_eq!(p[0].to_bits(),(-0.0f64).to_bits());
    let mut words = [1];
    let p = evaluate(&mut words,[1,1,1],&coords,0,[f64::MAX,0.5,0.5]).unwrap();
    assert_eq!(p[0].to_bits(),(-0.0f64).to_bits());
    // Two equally distant isolated cells: first y/x/z box wins.
    let mut words = [5];
    let p = evaluate(&mut words,[1,1,3],&[0.,1.,0.,1.,0.,1.,2.,3.],0,[0.5,0.5,1.5]).unwrap();
    assert_eq!(p,[0.5,0.5,1.]);
    let mut words = [1];
    assert_eq!(evaluate(&mut words,[1,1,1],&[3.,1.,3.,1.,3.,1.],0,[2.,2.,2.]),Some([3.;3]));
}

#[test]
fn metadata_nonfinite_and_empty_snapshots_do_not_publish() {
    let coords=[0.,1.,0.,1.,0.,1.]; let mut words=[u64::MAX]; let mut out=[99.;3];
    for (nx,len,cubes,q) in [(0,6,0,0.),(1,5,0,0.),(1,6,8,0.),(1,6,0,f64::NAN)] {
        let before=words;
        let status=unsafe { mattmc_voxel_closest_point(words.as_mut_ptr(),1,nx,1,1,coords.as_ptr(),len,cubes,q,0.,0.,out.as_mut_ptr()) };
        assert!(status<0);assert_eq!(words,before);assert_eq!(out,[99.;3]);
    }
    let status=unsafe { mattmc_voxel_closest_point(words.as_mut_ptr(),0,1,1,1,coords.as_ptr(),6,0,0.,0.,0.,out.as_mut_ptr()) };
    assert_eq!(status,0);assert_eq!(words,[0]);assert_eq!(out,[99.;3]);
}

#[test]
fn cube_division_and_uninitialized_coordinate_slots() {
    let coords=[f64::NAN;12];let mut words=[u64::MAX];let mut out=[0.;3];
    let status=unsafe { mattmc_voxel_closest_point(words.as_mut_ptr(),1,3,3,3,coords.as_ptr(),12,7,-1.,1.1,0.7,out.as_mut_ptr()) };
    assert_eq!(status,1);assert_eq!(out,[0.,1.,0.7]);
}

#[test]
fn proven_full_box_matches_general_path_and_rejects_before_writes() {
    use super::ffi::mattmc_voxel_closest_box;
    let coords = [-0.0,0.25,0.5,1.,-1.,0.,1.,2.,0.,0.1,0.2,0.3,0.4,0.5,0.6,1.];
    for q in [[-0.,0.,0.5],[f64::MAX,-f64::MAX,f64::MIN_POSITIVE],[-1.,3.,2.]] {
        let mut words=[u64::MAX];
        let p=evaluate(&mut words,[3,3,7],&coords,0,q).unwrap();
        let mut out=[99.;3];
        assert_eq!(unsafe { mattmc_voxel_closest_box(q[0],q[1],q[2],coords[0],coords[4],coords[8],coords[3],coords[7],coords[15],out.as_mut_ptr()) },1);
        assert_eq!(out.map(f64::to_bits),p.map(f64::to_bits));
    }
    let mut out=[99.;3];
    assert_eq!(unsafe { mattmc_voxel_closest_box(0.,0.,0.,f64::NAN,0.,0.,1.,1.,1.,out.as_mut_ptr()) },-2);
    assert_eq!(out,[99.;3]);
    assert_eq!(unsafe { mattmc_voxel_closest_box(0.,0.,0.,0.,0.,0.,1.,1.,1.,std::ptr::null_mut()) },-1);
}

#[test]
fn all_eight_coordinate_layouts_match_explicit_current_coordinates() {
    let dims=[3,4,5];
    for flags in 0..8u32 {
        let mut coords=Vec::new(); let mut source=Vec::new();
        for (axis,&d) in dims.iter().enumerate() {for i in 0..=d {
            let value=if flags&(1<<axis)!=0 {i as f64/d as f64} else {(i*i) as f64/(d*d) as f64};
            coords.push(value); source.push(if flags&(1<<axis)!=0 {f64::NAN} else {value});
        }}
        for q in [[-1.,0.5,1.1],[0.4,0.6,0.8],[0.,0.,0.],[f64::MAX,f64::MAX,f64::MAX]] {
            let pattern=0x050a_050a_050a_050a;let mut a=[pattern];let mut b=[pattern];
            let expected=evaluate(&mut a,dims,&coords,0,q);
            let actual=evaluate(&mut b,dims,&source,flags,q);
            assert_eq!(actual.map(|p|p.map(f64::to_bits)),expected.map(|p|p.map(f64::to_bits)));
        }
    }
}

#[test]
fn projection_requires_unique_ieee_distance_and_raw_point() {
    use super::{coordinates::Coordinates,projection::unique};
    let values=[0.,0.5,1.,0.,1.,0.,1.];let coords=Coordinates::new([2,1,1],&values);
    assert_eq!(unique::<0>(&[3],&coords,[0.3,0.4,0.6]),Some([0.3,0.4,0.6]));
    // Huge finite distances collapse distinct real distances to the same float.
    assert_eq!(unique::<0>(&[3],&coords,[1e16,0.4,0.6]),None);
    assert_eq!(unique::<0>(&[3],&coords,[-0.0,0.4,0.6]),None);
    assert_eq!(unique::<0>(&[3],&coords,[f64::MAX,0.4,0.6]),None);
    let tiny=[0.,1e-170,2e-170,0.,1.,0.,1.];let coords=Coordinates::new([2,1,1],&tiny);
    assert_eq!(unique::<0>(&[3],&coords,[1.5e-170,0.4,0.6]),None);
    let reversed=[1.,0.5,0.,0.,1.,0.,1.];let coords=Coordinates::new([2,1,1],&reversed);
    assert_eq!(unique::<0>(&[3],&coords,[0.3,0.4,0.6]),None);
    let zeros=[-0.,0.5,1.,0.,1.,0.,1.];let coords=Coordinates::new([2,1,1],&zeros);
    assert_eq!(unique::<0>(&[3],&coords,[0.3,0.4,0.6]),None);
}
