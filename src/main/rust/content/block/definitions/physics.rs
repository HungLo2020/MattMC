//! Intrinsic physical definitions. No world, renderer or Java state is needed.
//! Profiles are shared by blocks with identical physical values, independently
//! of their state-domain templates. Keep bit assignments stable across the CPU ABI.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PhysicalFlags(u16);
impl PhysicalFlags {
    pub const HAS_COLLISION: u16 = 1 << 0;
    pub const REQUIRES_CORRECT_TOOL: u16 = 1 << 1;
    pub const RANDOM_TICKS: u16 = 1 << 2;
    pub const CAN_OCCLUDE: u16 = 1 << 3;
    pub const AIR: u16 = 1 << 4;
    pub const IGNITED_BY_LAVA: u16 = 1 << 5;
    pub const LIQUID: u16 = 1 << 6;
    pub const FORCE_SOLID_OFF: u16 = 1 << 7;
    pub const FORCE_SOLID_ON: u16 = 1 << 8;
    pub const TERRAIN_PARTICLES: u16 = 1 << 9;
    pub const REPLACEABLE: u16 = 1 << 10;
    pub const DYNAMIC_SHAPE: u16 = 1 << 11;
    pub const fn contains(self, flag: u16) -> bool { self.0 & flag != 0 }
    pub const fn bits(self) -> u16 { self.0 }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum PushReaction { Normal, Destroy, Block, Ignore, PushOnly }

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Physics {
    pub hardness: f32,
    pub resistance: f32,
    pub friction: f32,
    pub speed_factor: f32,
    pub jump_factor: f32,
    pub flags: PhysicalFlags,
    pub push_reaction: PushReaction,
}

impl Physics {
    // Shared defaults match ordinary solid blocks. Declarations below override
    // values explicitly; runtime registration does not inherit another block.
    const DEFAULT: Self = Self {
        hardness: 0.0, resistance: 0.0, friction: 0.6,
        speed_factor: 1.0, jump_factor: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Normal,
    };
    pub(super) fn words(&self) -> [i32; 7] {
        [self.hardness.to_bits() as i32, self.resistance.to_bits() as i32,
         self.friction.to_bits() as i32, self.speed_factor.to_bits() as i32,
         self.jump_factor.to_bits() as i32, self.flags.bits() as i32, self.push_reaction as i32]
    }
}

macro_rules! profiles {
    ($($name:ident => $value:expr),+ $(,)?) => {
        #[derive(Clone, Copy)]
        #[repr(u16)]
        pub(super) enum PhysicalSet { $($name),+ }
        pub(super) static PROFILES: &[Physics] = &[$($value),+];
    };
}

profiles! {
    Air => Physics {
        flags: PhysicalFlags(PhysicalFlags::AIR | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        ..Physics::DEFAULT
    },
    Stone => Physics {
        hardness: 1.5,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Limestone => Physics {
        hardness: 1.2,
        resistance: 4.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    LimestoneWall => Physics {
        hardness: 1.2,
        resistance: 4.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    GrassBlock => Physics {
        hardness: 0.6,
        resistance: 0.6,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Dirt => Physics {
        hardness: 0.5,
        resistance: 0.5,
        ..Physics::DEFAULT
    },
    Cobblestone => Physics {
        hardness: 2.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    OakPlanks => Physics {
        hardness: 2.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PaleOakWood => Physics {
        hardness: 2.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    OakSapling => Physics {
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Bedrock => Physics {
        hardness: -1.0,
        resistance: 3600000.0,
        ..Physics::DEFAULT
    },
    Water => Physics {
        hardness: 100.0,
        resistance: 100.0,
        flags: PhysicalFlags(PhysicalFlags::LIQUID | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Lava => Physics {
        hardness: 100.0,
        resistance: 100.0,
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::LIQUID | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    SuspiciousSand => Physics {
        hardness: 0.25,
        resistance: 0.25,
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Gravel => Physics {
        hardness: 0.6,
        resistance: 0.6,
        ..Physics::DEFAULT
    },
    GoldOre => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DeepslateGoldOre => Physics {
        hardness: 4.5,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    MangroveRoots => Physics {
        hardness: 0.7,
        resistance: 0.7,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    MuddyMangroveRoots => Physics {
        hardness: 0.7,
        resistance: 0.7,
        ..Physics::DEFAULT
    },
    OakLeaves => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Glass => Physics {
        hardness: 0.3,
        resistance: 0.3,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Dispenser => Physics {
        hardness: 3.5,
        resistance: 3.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Sandstone => Physics {
        hardness: 0.8,
        resistance: 0.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    NoteBlock => Physics {
        hardness: 0.8,
        resistance: 0.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    WhiteBed => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    PoweredRail => Physics {
        hardness: 0.7,
        resistance: 0.7,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    StickyPiston => Physics {
        hardness: 1.5,
        resistance: 1.5,
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Cobweb => Physics {
        hardness: 4.0,
        resistance: 4.0,
        flags: PhysicalFlags(PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    ShortGrass => Physics {
        flags: PhysicalFlags(PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Fiddlehead => Physics {
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Flytrap => Physics {
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Cycad => Physics {
        hardness: 1.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        ..Physics::DEFAULT
    },
    Seagrass => Physics {
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    MovingPiston => Physics {
        hardness: -1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Dandelion => Physics {
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    GoldBlock => Physics {
        hardness: 3.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    IronBlock => Physics {
        hardness: 5.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Tnt => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Bookshelf => Physics {
        hardness: 1.5,
        resistance: 1.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Obsidian => Physics {
        hardness: 50.0,
        resistance: 1200.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Spawner => Physics {
        hardness: 5.0,
        resistance: 5.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CreakingHeart => Physics {
        hardness: 10.0,
        resistance: 10.0,
        ..Physics::DEFAULT
    },
    Chest => Physics {
        hardness: 2.5,
        resistance: 2.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    OakSign => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    OakDoor => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Ladder => Physics {
        hardness: 0.4,
        resistance: 0.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    CrimsonHangingSign => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Lever => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    StonePressurePlate => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    IronDoor => Physics {
        hardness: 5.0,
        resistance: 5.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    OakPressurePlate => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    RedstoneOre => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DeepslateRedstoneOre => Physics {
        hardness: 4.5,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Snow => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Ice => Physics {
        hardness: 0.5,
        resistance: 0.5,
        friction: 0.98,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    SnowBlock => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Cactus => Physics {
        hardness: 0.4,
        resistance: 0.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    CactusFlower => Physics {
        flags: PhysicalFlags(PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Jukebox => Physics {
        hardness: 2.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    OakFence => Physics {
        hardness: 2.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Netherrack => Physics {
        hardness: 0.4,
        resistance: 0.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    SoulSand => Physics {
        hardness: 0.5,
        resistance: 0.5,
        speed_factor: 0.4,
        ..Physics::DEFAULT
    },
    Basalt => Physics {
        hardness: 1.25,
        resistance: 4.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Glowstone => Physics {
        hardness: 0.3,
        resistance: 0.3,
        ..Physics::DEFAULT
    },
    NetherPortal => Physics {
        hardness: -1.0,
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    CarvedPumpkin => Physics {
        hardness: 1.0,
        resistance: 1.0,
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Cake => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    DinosaurChop => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        ..Physics::DEFAULT
    },
    CookedDinosaurChop => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        ..Physics::DEFAULT
    },
    Repeater => Physics {
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    OakTrapdoor => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PackedMud => Physics {
        hardness: 1.0,
        resistance: 3.0,
        ..Physics::DEFAULT
    },
    MudBricks => Physics {
        hardness: 1.5,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    InfestedStone => Physics {
        hardness: 0.75,
        resistance: 0.75,
        ..Physics::DEFAULT
    },
    InfestedCobblestone => Physics {
        hardness: 1.0,
        resistance: 0.75,
        ..Physics::DEFAULT
    },
    BrownMushroomBlock => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    IronBars => Physics {
        hardness: 5.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    IronChain => Physics {
        hardness: 5.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Vine => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    GlowLichen => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    LilyPad => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    ResinBlock => Physics {
        ..Physics::DEFAULT
    },
    EnchantingTable => Physics {
        hardness: 5.0,
        resistance: 1200.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    BrewingStand => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Cauldron => Physics {
        hardness: 2.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    EndPortal => Physics {
        hardness: -1.0,
        resistance: 3600000.0,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    EndStone => Physics {
        hardness: 3.0,
        resistance: 9.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DragonEgg => Physics {
        hardness: 3.0,
        resistance: 9.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Cocoa => Physics {
        hardness: 0.2,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    EnderChest => Physics {
        hardness: 22.5,
        resistance: 600.0,
        ..Physics::DEFAULT
    },
    CommandBlock => Physics {
        hardness: -1.0,
        resistance: 3600000.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Beacon => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CobblestoneWall => Physics {
        hardness: 2.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Anvil => Physics {
        hardness: 5.0,
        resistance: 1200.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Hopper => Physics {
        hardness: 3.0,
        resistance: 4.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    SlimeBlock => Physics {
        friction: 0.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Barrier => Physics {
        hardness: -1.0,
        resistance: 3600000.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Light => Physics {
        hardness: -1.0,
        resistance: 3600000.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        ..Physics::DEFAULT
    },
    WhiteCarpet => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PackedIce => Physics {
        hardness: 0.5,
        resistance: 0.5,
        friction: 0.98,
        ..Physics::DEFAULT
    },
    EndRod => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    ChorusFlower => Physics {
        hardness: 0.4,
        resistance: 0.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    DirtPath => Physics {
        hardness: 0.65,
        resistance: 0.65,
        ..Physics::DEFAULT
    },
    FrostedIce => Physics {
        hardness: 0.5,
        resistance: 0.5,
        friction: 0.98,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    MagmaBlock => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    NetherWartBlock => Physics {
        hardness: 1.0,
        resistance: 1.0,
        ..Physics::DEFAULT
    },
    BoneBlock => Physics {
        hardness: 2.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    StructureVoid => Physics {
        flags: PhysicalFlags(PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    ShulkerBox => Physics {
        hardness: 2.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    WhiteGlazedTerracotta => Physics {
        hardness: 1.4,
        resistance: 1.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::PushOnly,
        ..Physics::DEFAULT
    },
    WhiteConcrete => Physics {
        hardness: 1.8,
        resistance: 1.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DriedKelpBlock => Physics {
        hardness: 0.5,
        resistance: 2.5,
        ..Physics::DEFAULT
    },
    TurtleEgg => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    CaimanEgg => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PlatypusEgg => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    DriedGhast => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DeadTubeCoralBlock => Physics {
        hardness: 1.5,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    DeadTubeCoral => Physics {
        flags: PhysicalFlags(PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    BlueIce => Physics {
        hardness: 2.8,
        resistance: 2.8,
        friction: 0.989,
        ..Physics::DEFAULT
    },
    Conduit => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    BambooSapling => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Bamboo => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    BubbleColumn => Physics {
        flags: PhysicalFlags(PhysicalFlags::LIQUID | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    RedSandstoneWall => Physics {
        hardness: 0.8,
        resistance: 0.8,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    MudBrickWall => Physics {
        hardness: 1.5,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    EndStoneBrickWall => Physics {
        hardness: 3.0,
        resistance: 9.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Scaffolding => Physics {
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Grindstone => Physics {
        hardness: 2.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Bell => Physics {
        hardness: 5.0,
        resistance: 5.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Lantern => Physics {
        hardness: 3.5,
        resistance: 3.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Campfire => Physics {
        hardness: 2.0,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    WarpedStem => Physics {
        hardness: 2.0,
        resistance: 2.0,
        ..Physics::DEFAULT
    },
    WarpedNylium => Physics {
        hardness: 0.4,
        resistance: 0.4,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CrimsonPlanks => Physics {
        hardness: 2.0,
        resistance: 3.0,
        ..Physics::DEFAULT
    },
    CrimsonFenceGate => Physics {
        hardness: 2.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CrimsonDoor => Physics {
        hardness: 3.0,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    TestInstanceBlock => Physics {
        hardness: -1.0,
        resistance: 3600000.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Composter => Physics {
        hardness: 0.6,
        resistance: 0.6,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    BeeNest => Physics {
        hardness: 0.3,
        resistance: 0.3,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    HoneyBlock => Physics {
        speed_factor: 0.4,
        jump_factor: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    AncientDebris => Physics {
        hardness: 30.0,
        resistance: 1200.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Lodestone => Physics {
        hardness: 3.5,
        resistance: 3.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Block,
        ..Physics::DEFAULT
    },
    Candle => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    AmethystBlock => Physics {
        hardness: 1.5,
        resistance: 1.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Amber => Physics {
        hardness: 0.3,
        resistance: 2.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Ambersol => Physics {
        hardness: 3.0,
        resistance: 10.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    BuddingAmethyst => Physics {
        hardness: 1.5,
        resistance: 1.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    AmethystCluster => Physics {
        hardness: 1.5,
        resistance: 1.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    Calcite => Physics {
        hardness: 0.75,
        resistance: 0.75,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PowderSnow => Physics {
        hardness: 0.25,
        resistance: 0.25,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        ..Physics::DEFAULT
    },
    SculkSensor => Physics {
        hardness: 1.5,
        resistance: 1.5,
        ..Physics::DEFAULT
    },
    Sculk => Physics {
        hardness: 0.2,
        resistance: 0.2,
        ..Physics::DEFAULT
    },
    SculkVein => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    SculkCatalyst => Physics {
        hardness: 3.0,
        resistance: 3.0,
        ..Physics::DEFAULT
    },
    CopperDoor => Physics {
        hardness: 3.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    CopperTrapdoor => Physics {
        hardness: 3.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CopperGolemStatue => Physics {
        hardness: 3.0,
        resistance: 6.0,
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    LightningRod => Physics {
        hardness: 3.0,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PointedDripstone => Physics {
        hardness: 1.5,
        resistance: 3.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::DYNAMIC_SHAPE),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    DripstoneBlock => Physics {
        hardness: 1.5,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    Azalea => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    MossCarpet => Physics {
        hardness: 0.1,
        resistance: 0.1,
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    BigDripleaf => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_OFF | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    BigDripleafStem => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    CobbledDeepslate => Physics {
        hardness: 3.5,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    CobbledDeepslateWall => Physics {
        hardness: 3.5,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::FORCE_SOLID_ON | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    InfestedDeepslate => Physics {
        hardness: 1.5,
        resistance: 0.75,
        ..Physics::DEFAULT
    },
    ReinforcedDeepslate => Physics {
        hardness: 55.0,
        resistance: 1200.0,
        ..Physics::DEFAULT
    },
    Crafter => Physics {
        hardness: 1.5,
        resistance: 3.5,
        ..Physics::DEFAULT
    },
    TrialSpawner => Physics {
        hardness: 50.0,
        resistance: 50.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    HeavyCore => Physics {
        hardness: 10.0,
        resistance: 1200.0,
        ..Physics::DEFAULT
    },
    GunSmithTable => Physics {
        hardness: 2.5,
        resistance: 6.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PaleMossBlock => Physics {
        hardness: 0.1,
        resistance: 0.1,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::IGNITED_BY_LAVA | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    PottedOpenEyeblossom => Physics {
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        push_reaction: PushReaction::Destroy,
        ..Physics::DEFAULT
    },
    PrimalMagma => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    FloodBasalt => Physics {
        hardness: 3.0,
        resistance: 100.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::REQUIRES_CORRECT_TOOL | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    AncientLeaves => Physics {
        hardness: 0.2,
        resistance: 0.2,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PewenButton => Physics {
        hardness: 0.5,
        resistance: 0.5,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PewenSign => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PewenBranch => Physics {
        hardness: 1.0,
        resistance: 1.0,
        flags: PhysicalFlags(PhysicalFlags::HAS_COLLISION | PhysicalFlags::RANDOM_TICKS | PhysicalFlags::CAN_OCCLUDE | PhysicalFlags::TERRAIN_PARTICLES),
        ..Physics::DEFAULT
    },
    PewenPines => Physics {
        flags: PhysicalFlags(PhysicalFlags::RANDOM_TICKS | PhysicalFlags::TERRAIN_PARTICLES | PhysicalFlags::REPLACEABLE),
        ..Physics::DEFAULT
    },
}
