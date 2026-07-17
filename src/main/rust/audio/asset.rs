use std::sync::Arc;

/// Immutable encoded audio asset owned by the Rust audio backend.
///
/// Java supplies bytes read from Minecraft's `ResourceProvider`; Rust copies
/// those bytes during asset creation and never keeps Java memory alive after
/// the FFI call. Static playback decodes from this owned byte blob lazily on
/// the sound thread and stores any OpenAL buffer in the device-local Rust cache;
/// resource-pack lookup and streaming decode remain Java-owned for now.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct AudioAsset {
    encoded: Arc<[u8]>,
    debug_name: Option<String>,
    reload_generation: u64,
    metadata: EncodedAssetMetadata,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct EncodedAssetMetadata {
    pub(crate) byte_len: u64,
    pub(crate) ogg_container: bool,
}

#[cfg(test)]
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct AudioAssetSnapshot {
    pub(crate) encoded: Vec<u8>,
    pub(crate) debug_name: Option<String>,
    pub(crate) reload_generation: u64,
    pub(crate) metadata: EncodedAssetMetadata,
}

impl AudioAsset {
    pub(crate) fn new(encoded: &[u8], debug_name: Option<String>, reload_generation: u64) -> Self {
        Self {
            encoded: Arc::<[u8]>::from(encoded),
            debug_name,
            reload_generation,
            metadata: EncodedAssetMetadata {
                byte_len: encoded.len() as u64,
                ogg_container: encoded.starts_with(b"OggS"),
            },
        }
    }

    pub(crate) fn reload_generation(&self) -> u64 {
        self.reload_generation
    }

    pub(crate) fn encoded(&self) -> &[u8] {
        &self.encoded
    }

    pub(crate) fn encoded_arc(&self) -> Arc<[u8]> {
        self.encoded.clone()
    }

    #[cfg(test)]
    pub(crate) fn snapshot(&self) -> AudioAssetSnapshot {
        AudioAssetSnapshot {
            encoded: self.encoded.to_vec(),
            debug_name: self.debug_name.clone(),
            reload_generation: self.reload_generation,
            metadata: self.metadata,
        }
    }
}
