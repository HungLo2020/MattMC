#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct JavaString {
    units: Vec<u16>,
}

impl JavaString {
    pub fn empty() -> Self {
        Self { units: Vec::new() }
    }

    pub fn from_units(units: Vec<u16>) -> Self {
        Self { units }
    }

    pub fn from_str(value: &str) -> Self {
        Self {
            units: value.encode_utf16().collect(),
        }
    }

    pub fn units(&self) -> &[u16] {
        &self.units
    }

    pub fn to_string_lossless_if_valid(&self) -> Option<String> {
        String::from_utf16(&self.units).ok()
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum TagId {
    End = 0,
    Byte = 1,
    Short = 2,
    Int = 3,
    Long = 4,
    Float = 5,
    Double = 6,
    ByteArray = 7,
    String = 8,
    List = 9,
    Compound = 10,
    IntArray = 11,
    LongArray = 12,
}

impl TagId {
    pub fn from_u8(value: u8) -> Option<Self> {
        Some(match value {
            0 => Self::End,
            1 => Self::Byte,
            2 => Self::Short,
            3 => Self::Int,
            4 => Self::Long,
            5 => Self::Float,
            6 => Self::Double,
            7 => Self::ByteArray,
            8 => Self::String,
            9 => Self::List,
            10 => Self::Compound,
            11 => Self::IntArray,
            12 => Self::LongArray,
            _ => return None,
        })
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct NbtDocument {
    pub name: JavaString,
    pub root: NbtTag,
}

#[derive(Clone, Debug, PartialEq)]
pub struct ListTag {
    pub element_type: TagId,
    pub elements: Vec<NbtTag>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CompoundEntry {
    pub name: JavaString,
    pub value: NbtTag,
}

#[derive(Clone, Debug, PartialEq)]
pub enum NbtTag {
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(u32),
    Double(u64),
    ByteArray(Vec<i8>),
    String(JavaString),
    List(ListTag),
    Compound(Vec<CompoundEntry>),
    IntArray(Vec<i32>),
    LongArray(Vec<i64>),
}

impl NbtTag {
    pub fn id(&self) -> TagId {
        match self {
            Self::Byte(_) => TagId::Byte,
            Self::Short(_) => TagId::Short,
            Self::Int(_) => TagId::Int,
            Self::Long(_) => TagId::Long,
            Self::Float(_) => TagId::Float,
            Self::Double(_) => TagId::Double,
            Self::ByteArray(_) => TagId::ByteArray,
            Self::String(_) => TagId::String,
            Self::List(_) => TagId::List,
            Self::Compound(_) => TagId::Compound,
            Self::IntArray(_) => TagId::IntArray,
            Self::LongArray(_) => TagId::LongArray,
        }
    }
}
