use super::model::{JavaString, NbtDocument, NbtTag};

const FNV_OFFSET: u64 = 0xcbf29ce484222325;
const FNV_PRIME: u64 = 0x100000001b3;

pub fn fingerprint_document(document: &NbtDocument) -> u64 {
    let mut hasher = Fingerprinter::new();
    hasher.byte(0x4d);
    hasher.java_string(&document.name);
    hasher.tag(&document.root);
    hasher.finish()
}

struct Fingerprinter {
    hash: u64,
}

impl Fingerprinter {
    fn new() -> Self {
        Self { hash: FNV_OFFSET }
    }

    fn finish(self) -> u64 {
        self.hash
    }

    fn byte(&mut self, value: u8) {
        self.hash ^= value as u64;
        self.hash = self.hash.wrapping_mul(FNV_PRIME);
    }

    fn bytes(&mut self, bytes: &[u8]) {
        for byte in bytes {
            self.byte(*byte);
        }
    }

    fn u16(&mut self, value: u16) {
        self.bytes(&value.to_be_bytes());
    }

    fn u32(&mut self, value: u32) {
        self.bytes(&value.to_be_bytes());
    }

    fn u64(&mut self, value: u64) {
        self.bytes(&value.to_be_bytes());
    }

    fn java_string(&mut self, value: &JavaString) {
        self.u32(value.units().len() as u32);
        for unit in value.units() {
            self.u16(*unit);
        }
    }

    fn tag(&mut self, tag: &NbtTag) {
        self.byte(tag.id() as u8);
        match tag {
            NbtTag::Byte(value) => self.byte(*value as u8),
            NbtTag::Short(value) => self.bytes(&value.to_be_bytes()),
            NbtTag::Int(value) => self.bytes(&value.to_be_bytes()),
            NbtTag::Long(value) => self.bytes(&value.to_be_bytes()),
            NbtTag::Float(bits) => self.u32(*bits),
            NbtTag::Double(bits) => self.u64(*bits),
            NbtTag::ByteArray(values) => {
                self.u32(values.len() as u32);
                for value in values {
                    self.byte(*value as u8);
                }
            }
            NbtTag::String(value) => self.java_string(value),
            NbtTag::List(list) => {
                self.byte(list.element_type as u8);
                self.u32(list.elements.len() as u32);
                for element in &list.elements {
                    self.tag(element);
                }
            }
            NbtTag::Compound(entries) => {
                self.u32(entries.len() as u32);
                for entry in entries {
                    self.java_string(&entry.name);
                    self.tag(&entry.value);
                }
            }
            NbtTag::IntArray(values) => {
                self.u32(values.len() as u32);
                for value in values {
                    self.bytes(&value.to_be_bytes());
                }
            }
            NbtTag::LongArray(values) => {
                self.u32(values.len() as u32);
                for value in values {
                    self.bytes(&value.to_be_bytes());
                }
            }
        }
    }
}
