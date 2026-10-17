//! Shared immutable map colors. Content owns palette/shading; renderers own images.
mod ffi;
#[cfg(test)]
mod tests;

macro_rules! colors {
    ($( $variant:ident = $id:literal, $name:literal, $rgb:literal; )*) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u8)]
        pub enum MapColor { $( $variant = $id, )* }
        impl MapColor {
            pub const ALL: [Self; COLOR_COUNT] = [$(Self::$variant,)*];
            pub const fn name(self) -> &'static str { match self { $(Self::$variant => $name,)* } }
            pub const fn rgb(self) -> u32 { match self { $(Self::$variant => $rgb,)* } }
            /// Unassigned palette slots 62/63 have the same transparent meaning as None.
            pub const fn from_slot(id: u8) -> Option<Self> {
                match id { $($id => Some(Self::$variant),)* 62 | 63 => Some(Self::None), _ => None }
            }
        }
    };
}

pub const COLOR_COUNT: usize = 62;
colors! {
    None = 0, "NONE", 0x000000;
    Grass = 1, "GRASS", 0x7fb238;
    Sand = 2, "SAND", 0xf7e9a3;
    Wool = 3, "WOOL", 0xc7c7c7;
    Fire = 4, "FIRE", 0xff0000;
    Ice = 5, "ICE", 0xa0a0ff;
    Metal = 6, "METAL", 0xa7a7a7;
    Plant = 7, "PLANT", 0x007c00;
    Snow = 8, "SNOW", 0xffffff;
    Clay = 9, "CLAY", 0xa4a8b8;
    Dirt = 10, "DIRT", 0x976d4d;
    Stone = 11, "STONE", 0x707070;
    Water = 12, "WATER", 0x4040ff;
    Wood = 13, "WOOD", 0x8f7748;
    Quartz = 14, "QUARTZ", 0xfffcf5;
    ColorOrange = 15, "COLOR_ORANGE", 0xd87f33;
    ColorMagenta = 16, "COLOR_MAGENTA", 0xb24cd8;
    ColorLightBlue = 17, "COLOR_LIGHT_BLUE", 0x6699d8;
    ColorYellow = 18, "COLOR_YELLOW", 0xe5e533;
    ColorLightGreen = 19, "COLOR_LIGHT_GREEN", 0x7fcc19;
    ColorPink = 20, "COLOR_PINK", 0xf27fa5;
    ColorGray = 21, "COLOR_GRAY", 0x4c4c4c;
    ColorLightGray = 22, "COLOR_LIGHT_GRAY", 0x999999;
    ColorCyan = 23, "COLOR_CYAN", 0x4c7f99;
    ColorPurple = 24, "COLOR_PURPLE", 0x7f3fb2;
    ColorBlue = 25, "COLOR_BLUE", 0x334cb2;
    ColorBrown = 26, "COLOR_BROWN", 0x664c33;
    ColorGreen = 27, "COLOR_GREEN", 0x667f33;
    ColorRed = 28, "COLOR_RED", 0x993333;
    ColorBlack = 29, "COLOR_BLACK", 0x191919;
    Gold = 30, "GOLD", 0xfaee4d;
    Diamond = 31, "DIAMOND", 0x5cdbd5;
    Lapis = 32, "LAPIS", 0x4a80ff;
    Emerald = 33, "EMERALD", 0x00d93a;
    Podzol = 34, "PODZOL", 0x815631;
    Nether = 35, "NETHER", 0x700200;
    TerracottaWhite = 36, "TERRACOTTA_WHITE", 0xd1b1a1;
    TerracottaOrange = 37, "TERRACOTTA_ORANGE", 0x9f5224;
    TerracottaMagenta = 38, "TERRACOTTA_MAGENTA", 0x95576c;
    TerracottaLightBlue = 39, "TERRACOTTA_LIGHT_BLUE", 0x706c8a;
    TerracottaYellow = 40, "TERRACOTTA_YELLOW", 0xba8524;
    TerracottaLightGreen = 41, "TERRACOTTA_LIGHT_GREEN", 0x677535;
    TerracottaPink = 42, "TERRACOTTA_PINK", 0xa04d4e;
    TerracottaGray = 43, "TERRACOTTA_GRAY", 0x392923;
    TerracottaLightGray = 44, "TERRACOTTA_LIGHT_GRAY", 0x876b62;
    TerracottaCyan = 45, "TERRACOTTA_CYAN", 0x575c5c;
    TerracottaPurple = 46, "TERRACOTTA_PURPLE", 0x7a4958;
    TerracottaBlue = 47, "TERRACOTTA_BLUE", 0x4c3e5c;
    TerracottaBrown = 48, "TERRACOTTA_BROWN", 0x4c3223;
    TerracottaGreen = 49, "TERRACOTTA_GREEN", 0x4c522a;
    TerracottaRed = 50, "TERRACOTTA_RED", 0x8e3c2e;
    TerracottaBlack = 51, "TERRACOTTA_BLACK", 0x251610;
    CrimsonNylium = 52, "CRIMSON_NYLIUM", 0xbd3031;
    CrimsonStem = 53, "CRIMSON_STEM", 0x943f61;
    CrimsonHyphae = 54, "CRIMSON_HYPHAE", 0x5c191d;
    WarpedNylium = 55, "WARPED_NYLIUM", 0x167e86;
    WarpedStem = 56, "WARPED_STEM", 0x3a8e8c;
    WarpedHyphae = 57, "WARPED_HYPHAE", 0x562c3e;
    WarpedWartBlock = 58, "WARPED_WART_BLOCK", 0x14b485;
    Deepslate = 59, "DEEPSLATE", 0x646464;
    RawIron = 60, "RAW_IRON", 0xd8af93;
    GlowLichen = 61, "GLOW_LICHEN", 0x7fa796;
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum Brightness { Low = 0, Normal = 1, High = 2, Lowest = 3 }
impl Brightness {
    pub const ALL: [Self; 4] = [Self::Low, Self::Normal, Self::High, Self::Lowest];
    pub const fn modifier(self) -> u32 {
        match self { Self::Low => 180, Self::Normal => 220, Self::High => 255, Self::Lowest => 135 }
    }
}
impl MapColor {
    pub const fn argb(self, brightness: Brightness) -> u32 {
        if self as u8 == 0 { return 0; }
        let rgb = self.rgb(); let shade = brightness.modifier();
        let red = ((rgb >> 16) & 255) * shade / 255;
        let green = ((rgb >> 8) & 255) * shade / 255;
        let blue = (rgb & 255) * shade / 255;
        0xff00_0000 | (red << 16) | (green << 8) | blue
    }
    pub const fn packed(self, brightness: Brightness) -> u8 { ((self as u8) << 2) | brightness as u8 }
}
const fn argb_table() -> [u32; 256] {
    let mut result = [0; 256]; let mut code = 0;
    while code < result.len() {
        if let Some(color) = MapColor::from_slot((code >> 2) as u8) {
            result[code] = color.argb(Brightness::ALL[code & 3]);
        }
        code += 1;
    }
    result
}
const fn rgba_table() -> [[u8; 4]; 256] {
    let mut result = [[0; 4]; 256]; let mut code = 0;
    while code < result.len() {
        let argb = PACKED_ARGB[code];
        result[code] = [(argb >> 16) as u8, (argb >> 8) as u8, argb as u8, (argb >> 24) as u8];
        code += 1;
    }
    result
}
pub static PACKED_ARGB: [u32; 256] = argb_table();
pub static PACKED_RGBA: [[u8; 4]; 256] = rgba_table();

/// Expands an already bounded caller-owned indexed image once, in RGBA order.
pub fn expand_rgba(colors: &[u8]) -> Vec<u8> {
    let mut rgba = Vec::with_capacity(colors.len().checked_mul(4).expect("bounded map image"));
    for &code in colors { rgba.extend_from_slice(&PACKED_RGBA[code as usize]); }
    rgba
}
