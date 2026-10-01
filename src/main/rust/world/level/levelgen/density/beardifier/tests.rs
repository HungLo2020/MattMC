use super::{
    evaluate::point,
    layout::{Geometry, KERNEL_SIZE},
};
#[test]
fn validation_and_bury() {
    assert!(Geometry::read(&[]).is_none());
    let data = [1, 0, -32, -32, -32, 32, 32, 32, 0, 0, 0, 1, 1, 1, 0, 1];
    let g = Geometry::read(&data).unwrap();
    let kernel = [0.0; KERNEL_SIZE];
    assert_eq!(point(&g, &kernel, 0, 0, 0), 1.0);
    assert_eq!(point(&g, &kernel, 7, 0, 0), 0.0);
    assert_eq!(point(&g, &kernel, 33, 0, 0), 0.0);
    let mut invalid = data;
    invalid[15] = 5;
    assert!(Geometry::read(&invalid).is_none());
}
