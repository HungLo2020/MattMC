//! Ordered ray/box intersection for the outside branch of VoxelShape.clip.
mod coordinates;
mod evaluate;
mod ffi;
mod packet;
mod prepared;
mod miss;
mod intersection;
#[cfg(test)]
mod tests;
