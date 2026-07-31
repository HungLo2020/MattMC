use crate::render::vulkanic::error::{GalError, GalResult};

use super::programs::ProgramIdentity;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackManifest {
    name: String,
    generation: u64,
    programs: Vec<ProgramIdentity>,
}

impl ShaderPackManifest {
    pub fn new(
        name: impl Into<String>,
        generation: u64,
        programs: Vec<ProgramIdentity>,
    ) -> GalResult<Self> {
        let name = name.into();
        if name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack manifest name is empty",
            ));
        }
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack manifest generation must be non-zero",
            ));
        }
        if programs.is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack manifest must declare at least one program",
            ));
        }
        Ok(Self {
            name,
            generation,
            programs,
        })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn programs(&self) -> &[ProgramIdentity] {
        &self.programs
    }
}
