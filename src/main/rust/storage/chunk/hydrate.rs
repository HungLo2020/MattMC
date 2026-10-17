//! Decode-to-primary-owner handoff. Palette labels, packed words and light
//! bytes remain in Rust; Java receives scoped metadata and typed owner leases.
use super::load::Decoded;
use crate::world::level::chunk::{biomes, live, palette::saved};
use crate::world::level::lighting::layers::{self, Layer};
use std::sync::Arc;
mod ffi;

pub(crate) struct Config {
    pub block_limit: u32,
    pub block_bits: usize,
    pub default_block: i32,
    pub biome_limit: u32,
    pub biome_bits: usize,
    pub default_biome: i32,
}
pub(crate) struct Row {
    pub y: i32,
    pub flags: i32,
    pub blocks: Option<Arc<live::Owner>>,
    pub biomes: Option<Arc<biomes::Owner>>,
    pub block_light: Option<Arc<Layer>>,
    pub sky_light: Option<Arc<Layer>>,
}
pub(crate) struct Hydrated {
    pub rows: Vec<Row>,
}

struct Reader<'a> {
    input: &'a Decoded,
    ints: usize,
    words: usize,
    bytes: usize,
}
impl<'a> Reader<'a> {
    fn int(&mut self) -> Option<i32> {
        let value = *self.input.ints.get(self.ints)?;
        self.ints += 1;
        Some(value)
    }
    fn container(&mut self) -> Option<(&'a [i32], &'a [u64])> {
        let count = usize::try_from(self.int()?).ok()?;
        let length = self.int()?;
        if !(1..=65536).contains(&count) || length < -1 {
            return None;
        }
        let end = self.ints.checked_add(count)?;
        let palette = self.input.ints.get(self.ints..end)?;
        self.ints = end;
        let length = length.max(0) as usize;
        let end = self.words.checked_add(length)?;
        let raw = self.input.longs.get(self.words..end)?;
        self.words = end;
        // i64/u64 have identical layout; preserve every saved bit without copying.
        let words = unsafe { std::slice::from_raw_parts(raw.as_ptr().cast::<u64>(), raw.len()) };
        // Frozen does not inspect a zero-width container's optional words.
        Some((palette, if count == 1 { &[] } else { words }))
    }
    fn light(&mut self) -> Option<Arc<Layer>> {
        let end = self.bytes.checked_add(layers::BYTES)?;
        let values = self.input.bytes.get(self.bytes..end)?.try_into().ok()?;
        self.bytes = end;
        Some(Arc::new(Layer::from_bytes(values)))
    }
}

pub(crate) fn hydrate(input: &Decoded, config: &Config) -> Option<Hydrated> {
    let mut reader = Reader {
        input,
        ints: 0,
        words: 0,
        bytes: 0,
    };
    let count = usize::try_from(reader.int()?).ok()?;
    if count > 4096
        || config.default_block < 0
        || config.default_block as u32 >= config.block_limit
        || config.default_biome < 0
        || config.default_biome as u32 >= config.biome_limit
    {
        return None;
    }
    let mut rows = Vec::with_capacity(count);
    for _ in 0..count {
        let y = reader.int()?;
        let flags = reader.int()?;
        if flags < 0 || flags & !31 != 0 {
            return None;
        }
        let mut row = Row {
            y,
            flags,
            blocks: None,
            biomes: None,
            block_light: None,
            sky_light: None,
        };
        if flags & 16 != 0 {
            let (palette, words) = if flags & 1 != 0 {
                reader.container()?
            } else {
                (std::slice::from_ref(&config.default_block), &[][..])
            };
            row.blocks = Some(Arc::new(saved::load_blocks(
                palette,
                words,
                config.block_limit,
                config.block_bits,
            )?));
            let (palette, words) = if flags & 2 != 0 {
                reader.container()?
            } else {
                (std::slice::from_ref(&config.default_biome), &[][..])
            };
            row.biomes = Some(Arc::new(saved::load_biomes(
                palette,
                words,
                config.biome_limit,
                config.biome_bits,
            )?));
        }
        if flags & 4 != 0 {
            row.block_light = Some(reader.light()?);
        }
        if flags & 8 != 0 {
            row.sky_light = Some(reader.light()?);
        }
        rows.push(row);
    }
    if reader.ints != input.ints.len()
        || reader.words != input.longs.len()
        || reader.bytes != input.bytes.len()
    {
        return None;
    }
    Some(Hydrated { rows })
}

#[cfg(test)]
mod tests {
    use super::*;
    fn config() -> Config {
        Config {
            block_limit: 512,
            block_bits: 9,
            default_block: 2,
            biome_limit: 512,
            biome_bits: 9,
            default_biome: 3,
        }
    }
    #[test]
    fn defaults_ignored_zero_words_and_outside_height_lights_keep_list_order() {
        let input = Decoded {
            ints: vec![3, 7, 16, -4, 19, 1, 2, 5, 1, -1, 6, 100, 12],
            longs: vec![-1, 42],
            bytes: vec![0xab; 4096],
            root: vec![],
        };
        let out = hydrate(&input, &config()).unwrap();
        assert_eq!(
            out.rows.iter().map(|r| r.y).collect::<Vec<_>>(),
            vec![7, -4, 100]
        );
        assert_eq!(out.rows[0].blocks.as_ref().unwrap().capture()[0], 2);
        assert_eq!(out.rows[1].blocks.as_ref().unwrap().capture()[4095], 5);
        assert!(out.rows[2].blocks.is_none());
        assert!(out.rows[2].biomes.is_none());
        let light = out.rows[2].block_light.as_ref().unwrap().view();
        assert!(light.allocated());
        assert_eq!(light.default_value(), 0);
    }
    #[test]
    fn malformed_late_row_declines_all_owners_without_consuming_decode() {
        let mut words = vec![0; 256];
        words[255] = 15;
        let input = Decoded {
            ints: vec![2, 0, 16, 1, 17, 2, 256, 0, 1],
            longs: words,
            bytes: vec![],
            root: vec![],
        };
        assert!(hydrate(&input, &config()).is_none());
        assert_eq!(input.ints.len(), 9);
        assert_eq!(input.longs.len(), 256);
        assert!(hydrate(
            &Decoded {
                ints: vec![1, 0, 16, 99],
                ..Decoded::default()
            },
            &config()
        )
        .is_none());
    }
}
