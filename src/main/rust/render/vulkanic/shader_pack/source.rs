use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderSourceFile {
    pub path: String,
    pub contents: String,
}

impl ShaderSourceFile {
    pub fn new(path: impl Into<String>, contents: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            contents: contents.into(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackSource {
    name: String,
    generation: u64,
    files: BTreeMap<String, String>,
}

impl ShaderPackSource {
    pub fn new(
        name: impl Into<String>,
        generation: u64,
        files: Vec<ShaderSourceFile>,
    ) -> GalResult<Self> {
        let name = name.into();
        if name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack source name is empty",
            ));
        }
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack source generation must be non-zero",
            ));
        }
        let mut map = BTreeMap::new();
        for file in files {
            if file.path.trim().is_empty() {
                return Err(GalError::invalid_argument("shader source path is empty"));
            }
            if map.insert(file.path, file.contents).is_some() {
                return Err(GalError::invalid_argument("duplicate shader source path"));
            }
        }
        Ok(Self {
            name,
            generation,
            files: map,
        })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn get(&self, path: &str) -> Option<&str> {
        self.files.get(path).map(String::as_str)
    }
}
