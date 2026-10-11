//! Texture atlases built from resource packs: PNG decoding, sprite sources,
//! Sodium's sprite preprocessing and mip chains, and stitching. Java keeps
//! the `TextureAtlas`/`SpriteContents` objects its consumers use, filled
//! from these builds.

pub mod atlas;
pub mod color;
pub mod ffi;
pub mod image;
pub mod sources;
pub mod sprite;
pub mod stitcher;
