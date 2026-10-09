//! Shared built-in property declarations. Order is bridge identity only;
//! block-registry property IDs retain their existing first-use order.
use super::{Declaration, Domain};

macro_rules! properties {
    ($( $id:ident, $key:literal, $name:literal, $domain:expr; )*) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u16)]
        pub enum Builtin { $( $id, )* }
        pub(super) const DECLARATIONS: &[Declaration] = &[
            $( Declaration { key: $key, name: $name, domain: $domain }, )*
        ];
    };
}

properties! {
    Attached, "ATTACHED", "attached", Domain::Boolean;
    Berries, "BERRIES", "berries", Domain::Boolean;
    Bloom, "BLOOM", "bloom", Domain::Boolean;
    Bottom, "BOTTOM", "bottom", Domain::Boolean;
    CanSummon, "CAN_SUMMON", "can_summon", Domain::Boolean;
    Conditional, "CONDITIONAL", "conditional", Domain::Boolean;
    Disarmed, "DISARMED", "disarmed", Domain::Boolean;
    Drag, "DRAG", "drag", Domain::Boolean;
    Enabled, "ENABLED", "enabled", Domain::Boolean;
    Extended, "EXTENDED", "extended", Domain::Boolean;
    Eye, "EYE", "eye", Domain::Boolean;
    Falling, "FALLING", "falling", Domain::Boolean;
    Hanging, "HANGING", "hanging", Domain::Boolean;
    HasBottle0, "HAS_BOTTLE_0", "has_bottle_0", Domain::Boolean;
    HasBottle1, "HAS_BOTTLE_1", "has_bottle_1", Domain::Boolean;
    HasBottle2, "HAS_BOTTLE_2", "has_bottle_2", Domain::Boolean;
    HasRecord, "HAS_RECORD", "has_record", Domain::Boolean;
    HasBook, "HAS_BOOK", "has_book", Domain::Boolean;
    Inverted, "INVERTED", "inverted", Domain::Boolean;
    InWall, "IN_WALL", "in_wall", Domain::Boolean;
    Lit, "LIT", "lit", Domain::Boolean;
    Locked, "LOCKED", "locked", Domain::Boolean;
    Natural, "NATURAL", "natural", Domain::Boolean;
    Occupied, "OCCUPIED", "occupied", Domain::Boolean;
    Open, "OPEN", "open", Domain::Boolean;
    Persistent, "PERSISTENT", "persistent", Domain::Boolean;
    Powered, "POWERED", "powered", Domain::Boolean;
    Short, "SHORT", "short", Domain::Boolean;
    Shrieking, "SHRIEKING", "shrieking", Domain::Boolean;
    SignalFire, "SIGNAL_FIRE", "signal_fire", Domain::Boolean;
    Snowy, "SNOWY", "snowy", Domain::Boolean;
    Tip, "TIP", "tip", Domain::Boolean;
    Triggered, "TRIGGERED", "triggered", Domain::Boolean;
    Unstable, "UNSTABLE", "unstable", Domain::Boolean;
    Waterlogged, "WATERLOGGED", "waterlogged", Domain::Boolean;
    HorizontalAxis, "HORIZONTAL_AXIS", "axis", Domain::Enum(&["x", "z"]);
    Axis, "AXIS", "axis", Domain::Enum(&["x", "y", "z"]);
    Up, "UP", "up", Domain::Boolean;
    Down, "DOWN", "down", Domain::Boolean;
    North, "NORTH", "north", Domain::Boolean;
    East, "EAST", "east", Domain::Boolean;
    South, "SOUTH", "south", Domain::Boolean;
    West, "WEST", "west", Domain::Boolean;
    Facing, "FACING", "facing", Domain::Enum(&["north", "east", "south", "west", "up", "down"]);
    FacingHopper, "FACING_HOPPER", "facing", Domain::Enum(&["down", "north", "south", "west", "east"]);
    HorizontalFacing, "HORIZONTAL_FACING", "facing", Domain::Enum(&["north", "south", "west", "east"]);
    FlowerAmount, "FLOWER_AMOUNT", "flower_amount", Domain::Integer { min: 1, max: 4 };
    SegmentAmount, "SEGMENT_AMOUNT", "segment_amount", Domain::Integer { min: 1, max: 4 };
    Orientation, "ORIENTATION", "orientation", Domain::Enum(&["down_east", "down_north", "down_south", "down_west", "up_east", "up_north", "up_south", "up_west", "west_up", "east_up", "north_up", "south_up"]);
    AttachFace, "ATTACH_FACE", "face", Domain::Enum(&["floor", "wall", "ceiling"]);
    BellAttachment, "BELL_ATTACHMENT", "attachment", Domain::Enum(&["floor", "ceiling", "single_wall", "double_wall"]);
    EastWall, "EAST_WALL", "east", Domain::Enum(&["none", "low", "tall"]);
    NorthWall, "NORTH_WALL", "north", Domain::Enum(&["none", "low", "tall"]);
    SouthWall, "SOUTH_WALL", "south", Domain::Enum(&["none", "low", "tall"]);
    WestWall, "WEST_WALL", "west", Domain::Enum(&["none", "low", "tall"]);
    EastRedstone, "EAST_REDSTONE", "east", Domain::Enum(&["up", "side", "none"]);
    NorthRedstone, "NORTH_REDSTONE", "north", Domain::Enum(&["up", "side", "none"]);
    SouthRedstone, "SOUTH_REDSTONE", "south", Domain::Enum(&["up", "side", "none"]);
    WestRedstone, "WEST_REDSTONE", "west", Domain::Enum(&["up", "side", "none"]);
    DoubleBlockHalf, "DOUBLE_BLOCK_HALF", "half", Domain::Enum(&["upper", "lower"]);
    Half, "HALF", "half", Domain::Enum(&["top", "bottom"]);
    SideChainPart, "SIDE_CHAIN_PART", "side_chain", Domain::Enum(&["unconnected", "right", "center", "left"]);
    RailShape, "RAIL_SHAPE", "shape", Domain::Enum(&["north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south", "south_east", "south_west", "north_west", "north_east"]);
    RailShapeStraight, "RAIL_SHAPE_STRAIGHT", "shape", Domain::Enum(&["north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"]);
    Age1, "AGE_1", "age", Domain::Integer { min: 0, max: 1 };
    Age2, "AGE_2", "age", Domain::Integer { min: 0, max: 2 };
    Age3, "AGE_3", "age", Domain::Integer { min: 0, max: 3 };
    Age4, "AGE_4", "age", Domain::Integer { min: 0, max: 4 };
    Age5, "AGE_5", "age", Domain::Integer { min: 0, max: 5 };
    Age7, "AGE_7", "age", Domain::Integer { min: 0, max: 7 };
    Age15, "AGE_15", "age", Domain::Integer { min: 0, max: 15 };
    Age25, "AGE_25", "age", Domain::Integer { min: 0, max: 25 };
    Bites, "BITES", "bites", Domain::Integer { min: 0, max: 6 };
    Candles, "CANDLES", "candles", Domain::Integer { min: 1, max: 4 };
    Delay, "DELAY", "delay", Domain::Integer { min: 1, max: 4 };
    Distance, "DISTANCE", "distance", Domain::Integer { min: 1, max: 7 };
    Eggs, "EGGS", "eggs", Domain::Integer { min: 1, max: 4 };
    Hatch, "HATCH", "hatch", Domain::Integer { min: 0, max: 2 };
    Layers, "LAYERS", "layers", Domain::Integer { min: 1, max: 8 };
    LevelCauldron, "LEVEL_CAULDRON", "level", Domain::Integer { min: 1, max: 3 };
    LevelComposter, "LEVEL_COMPOSTER", "level", Domain::Integer { min: 0, max: 8 };
    LevelFlowing, "LEVEL_FLOWING", "level", Domain::Integer { min: 1, max: 8 };
    LevelHoney, "LEVEL_HONEY", "honey_level", Domain::Integer { min: 0, max: 5 };
    Level, "LEVEL", "level", Domain::Integer { min: 0, max: 15 };
    Moisture, "MOISTURE", "moisture", Domain::Integer { min: 0, max: 7 };
    Note, "NOTE", "note", Domain::Integer { min: 0, max: 24 };
    Pickles, "PICKLES", "pickles", Domain::Integer { min: 1, max: 4 };
    Power, "POWER", "power", Domain::Integer { min: 0, max: 15 };
    Stage, "STAGE", "stage", Domain::Integer { min: 0, max: 1 };
    StabilityDistance, "STABILITY_DISTANCE", "distance", Domain::Integer { min: 0, max: 7 };
    RespawnAnchorCharges, "RESPAWN_ANCHOR_CHARGES", "charges", Domain::Integer { min: 0, max: 4 };
    DriedGhastHydrationLevels, "DRIED_GHAST_HYDRATION_LEVELS", "hydration", Domain::Integer { min: 0, max: 3 };
    Rotation16, "ROTATION_16", "rotation", Domain::Integer { min: 0, max: 15 };
    BedPart, "BED_PART", "part", Domain::Enum(&["head", "foot"]);
    ChestType, "CHEST_TYPE", "type", Domain::Enum(&["single", "left", "right"]);
    ModeComparator, "MODE_COMPARATOR", "mode", Domain::Enum(&["compare", "subtract"]);
    DoorHinge, "DOOR_HINGE", "hinge", Domain::Enum(&["left", "right"]);
    NoteblockInstrument, "NOTEBLOCK_INSTRUMENT", "instrument", Domain::Enum(crate::content::sound::INSTRUMENT_NAMES);
    PistonType, "PISTON_TYPE", "type", Domain::Enum(&["normal", "sticky"]);
    SlabType, "SLAB_TYPE", "type", Domain::Enum(&["top", "bottom", "double"]);
    StairsShape, "STAIRS_SHAPE", "shape", Domain::Enum(&["straight", "inner_left", "inner_right", "outer_left", "outer_right"]);
    StructureblockMode, "STRUCTUREBLOCK_MODE", "mode", Domain::Enum(&["save", "load", "corner", "data"]);
    BambooLeaves, "BAMBOO_LEAVES", "leaves", Domain::Enum(&["none", "small", "large"]);
    Tilt, "TILT", "tilt", Domain::Enum(&["none", "unstable", "partial", "full"]);
    VerticalDirection, "VERTICAL_DIRECTION", "vertical_direction", Domain::Enum(&["up", "down"]);
    DripstoneThickness, "DRIPSTONE_THICKNESS", "thickness", Domain::Enum(&["tip_merge", "tip", "frustum", "middle", "base"]);
    SculkSensorPhase, "SCULK_SENSOR_PHASE", "sculk_sensor_phase", Domain::Enum(&["inactive", "active", "cooldown"]);
    Slot0Occupied, "SLOT_0_OCCUPIED", "slot_0_occupied", Domain::Boolean;
    Slot1Occupied, "SLOT_1_OCCUPIED", "slot_1_occupied", Domain::Boolean;
    Slot2Occupied, "SLOT_2_OCCUPIED", "slot_2_occupied", Domain::Boolean;
    Slot3Occupied, "SLOT_3_OCCUPIED", "slot_3_occupied", Domain::Boolean;
    Slot4Occupied, "SLOT_4_OCCUPIED", "slot_4_occupied", Domain::Boolean;
    Slot5Occupied, "SLOT_5_OCCUPIED", "slot_5_occupied", Domain::Boolean;
    Dusted, "DUSTED", "dusted", Domain::Integer { min: 0, max: 3 };
    Cracked, "CRACKED", "cracked", Domain::Boolean;
    Crafting, "CRAFTING", "crafting", Domain::Boolean;
    TrialSpawnerState, "TRIAL_SPAWNER_STATE", "trial_spawner_state", Domain::Enum(&["inactive", "waiting_for_players", "active", "waiting_for_reward_ejection", "ejecting_reward", "cooldown"]);
    VaultState, "VAULT_STATE", "vault_state", Domain::Enum(&["inactive", "active", "unlocking", "ejecting"]);
    CreakingHeartState, "CREAKING_HEART_STATE", "creaking_heart_state", Domain::Enum(&["uprooted", "dormant", "awake"]);
    Ominous, "OMINOUS", "ominous", Domain::Boolean;
    TestBlockMode, "TEST_BLOCK_MODE", "mode", Domain::Enum(&["start", "log", "fail", "accept"]);
    Map, "MAP", "map", Domain::Boolean;
    CopperGolemPose, "COPPER_GOLEM_POSE", "copper_golem_pose", Domain::Enum(&["standing", "sitting", "running", "star"]);

    // Integrated content retains distinct property object/declaration identity.
    DinosaurEggBlockNeedsPlayer, "DINOSAUREGGBLOCK_NEEDS_PLAYER", "needs_player", Domain::Boolean;
    PrimalMagmaBlockActive, "PRIMALMAGMABLOCK_ACTIVE", "active", Domain::Boolean;
    PrimalMagmaBlockPermanent, "PRIMALMAGMABLOCK_PERMANENT", "permanent", Domain::Boolean;
    PewenBranchBlockPines, "PEWENBRANCHBLOCK_PINES", "pines", Domain::Boolean;
    PewenBranchBlockRotation, "PEWENBRANCHBLOCK_ROTATION", "rotation", Domain::Integer { min: 0, max: 7 };
    DinosaurChopBlockBites, "DINOSAURCHOPBLOCK_BITES", "bites", Domain::Integer { min: 0, max: 3 };
    FissurePrimalMagmaBlockRegenHeight, "FISSUREPRIMALMAGMABLOCK_REGEN_HEIGHT", "regen_height", Domain::Integer { min: 0, max: 4 };
    BlockHummingbirdFeederContents, "BLOCKHUMMINGBIRDFEEDER_CONTENTS", "contents", Domain::Integer { min: 0, max: 3 };
    RedstoneRandomizerBlockOutputSide, "REDSTONERANDOMIZERBLOCK_OUTPUT_SIDE", "output", Domain::Enum(&["left", "right"]);
    FlytrapBlockOpen, "FLYTRAPBLOCK_OPEN", "open", Domain::Boolean;
    CycadBlockTop, "CYCADBLOCK_TOP", "top", Domain::Boolean;
}
