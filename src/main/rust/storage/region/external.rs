use std::fs;
use std::path::{Path, PathBuf};

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::format::EXTERNAL_FILE_EXTENSION;

pub fn external_chunk_path(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
) -> RegionResult<PathBuf> {
    let parent = region_path.parent().ok_or_else(|| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            format!("region path has no parent: {}", region_path.display()),
        )
    })?;
    Ok(parent.join(format!(
        "c.{}.{}.{}",
        chunk_x, chunk_z, EXTERNAL_FILE_EXTENSION
    )))
}

pub fn read_external_payload(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
) -> RegionResult<Vec<u8>> {
    let path = external_chunk_path(region_path, chunk_x, chunk_z)?;
    if !path.is_file() {
        return Err(RegionError::new(
            RegionErrorKind::MissingExternalFile,
            0,
            format!("external chunk file is missing: {}", path.display()),
        ));
    }
    let bytes = fs::read(&path).map_err(|error| RegionError::io(0, error))?;
    if bytes.is_empty() {
        return Err(RegionError::new(
            RegionErrorKind::TruncatedExternalFile,
            0,
            format!("external chunk file is empty: {}", path.display()),
        ));
    }
    Ok(bytes)
}
