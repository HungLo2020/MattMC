//! Built-in state sets: property identities and serialized default values.
//! Shared across blocks with the same domain/default contract.
use crate::content::property::Builtin;

macro_rules! state_sets {
    ($( $name:ident => [$( $property:ident => $value:literal ),*]; )*) => {
        #[derive(Clone, Copy, Debug, PartialEq, Eq)]
        #[repr(u16)]
        pub(super) enum StateSet { $( $name, )* }
        pub(super) const STATE_SETS: &[&[(Builtin, &str)]] = &[
            $( &[$( (Builtin::$property, $value), )*], )*
        ];
    };
}

state_sets! {
    Empty => [];
    Stair => [HorizontalFacing => "north", Half => "bottom", StairsShape => "straight", Waterlogged => "false"];
    Slab => [SlabType => "bottom", Waterlogged => "false"];
    Wall => [EastWall => "none", NorthWall => "none", SouthWall => "none", Up => "true", Waterlogged => "false", WestWall => "none"];
    AxisY => [Axis => "y"];
    FacingNorth => [Facing => "north"];
    SnowyFalse => [Snowy => "false"];
    Stage0 => [Stage => "0"];
    MangrovePropagule => [Age4 => "0", Hanging => "false", Stage => "0", Waterlogged => "false"];
    Level0 => [Level => "0"];
    Dusted0 => [Dusted => "0"];
    WaterloggedFalse => [Waterlogged => "false"];
    TintedParticleLeaves => [Distance => "7", Persistent => "false", Waterlogged => "false"];
    Dispenser => [Facing => "north", Triggered => "false"];
    Note => [NoteblockInstrument => "harp", Note => "0", Powered => "false"];
    Bed => [HorizontalFacing => "north", Occupied => "false", BedPart => "foot"];
    PoweredRail => [Powered => "false", RailShapeStraight => "north_south", Waterlogged => "false"];
    PistonBase => [Extended => "false", Facing => "north"];
    FlytrapblockOpenTrue => [FlytrapBlockOpen => "true"];
    TreeStar => [Facing => "up", Waterlogged => "false"];
    CycadblockTopTrue => [CycadBlockTop => "true"];
    DoubleBlockHalfLower => [DoubleBlockHalf => "lower"];
    PistonHead => [Facing => "north", Short => "false", PistonType => "normal"];
    MovingPiston => [Facing => "north", PistonType => "normal"];
    UnstableFalse => [Unstable => "false"];
    ChiseledBookShelf => [HorizontalFacing => "north", Slot0Occupied => "false", Slot1Occupied => "false", Slot2Occupied => "false", Slot3Occupied => "false", Slot4Occupied => "false", Slot5Occupied => "false"];
    Shelf => [HorizontalFacing => "north", Powered => "false", SideChainPart => "unconnected", Waterlogged => "false"];
    HorizontalFacingNorth => [HorizontalFacing => "north"];
    Fire => [Age15 => "0", East => "false", North => "false", South => "false", Up => "false", West => "false"];
    CreakingHeart => [Axis => "y", CreakingHeartState => "uprooted", Natural => "false"];
    Chest => [HorizontalFacing => "north", ChestType => "single", Waterlogged => "false"];
    RedStoneWire => [EastRedstone => "none", NorthRedstone => "none", Power => "0", SouthRedstone => "none", WestRedstone => "none"];
    Age70 => [Age7 => "0"];
    Moisture0 => [Moisture => "0"];
    Furnace => [HorizontalFacing => "north", Lit => "false"];
    StandingSign => [Rotation16 => "0", Waterlogged => "false"];
    Door => [HorizontalFacing => "north", DoubleBlockHalf => "lower", DoorHinge => "left", Open => "false", Powered => "false"];
    Ladder => [HorizontalFacing => "north", Waterlogged => "false"];
    Rail => [RailShape => "north_south", Waterlogged => "false"];
    CeilingHangingSign => [Attached => "false", Rotation16 => "0", Waterlogged => "false"];
    Lever => [AttachFace => "wall", HorizontalFacing => "north", Powered => "false"];
    PoweredFalse => [Powered => "false"];
    LitFalse => [Lit => "false"];
    LitTrue => [Lit => "true"];
    RedstoneWallTorch => [HorizontalFacing => "north", Lit => "true"];
    Layers1 => [Layers => "1"];
    Age150 => [Age15 => "0"];
    HasRecordFalse => [HasRecord => "false"];
    Fence => [East => "false", North => "false", South => "false", Waterlogged => "false", West => "false"];
    HorizontalAxisX => [HorizontalAxis => "x"];
    Bites0 => [Bites => "0"];
    DinosaurChop => [DinosaurChopBlockBites => "0", Facing => "up", Waterlogged => "false"];
    Repeater => [Delay => "1", HorizontalFacing => "north", Locked => "false", Powered => "false"];
    TrapDoor => [HorizontalFacing => "north", Half => "bottom", Open => "false", Powered => "false", Waterlogged => "false"];
    HugeMushroom => [Down => "true", East => "true", North => "true", South => "true", Up => "true", West => "true"];
    Chain => [Axis => "y", Waterlogged => "false"];
    Vine => [East => "false", North => "false", South => "false", Up => "false", West => "false"];
    GlowLichen => [Down => "false", East => "false", North => "false", South => "false", Up => "false", Waterlogged => "false", West => "false"];
    SkunkSpray => [Age3 => "0", Down => "false", East => "false", North => "false", South => "false", Up => "false", Waterlogged => "false", West => "false"];
    FenceGate => [HorizontalFacing => "north", InWall => "false", Open => "false", Powered => "false"];
    Age30 => [Age3 => "0"];
    BrewingStand => [HasBottle0 => "false", HasBottle1 => "false", HasBottle2 => "false"];
    LevelCauldron1 => [LevelCauldron => "1"];
    EndPortalFrame => [Eye => "false", HorizontalFacing => "north"];
    Cocoa => [Age2 => "0", HorizontalFacing => "north"];
    TripWireHook => [Attached => "false", HorizontalFacing => "north", Powered => "false"];
    TripWire => [Attached => "false", Disarmed => "false", East => "false", North => "false", Powered => "false", South => "false", West => "false"];
    Command => [Conditional => "false", Facing => "north"];
    Skull => [Powered => "false", Rotation16 => "0"];
    WallSkull => [HorizontalFacing => "north", Powered => "false"];
    Power0 => [Power => "0"];
    Comparator => [HorizontalFacing => "north", ModeComparator => "compare", Powered => "false"];
    RedstoneRandomizer => [HorizontalFacing => "north", RedstoneRandomizerBlockOutputSide => "left", Powered => "false"];
    DaylightDetector => [Inverted => "false", Power => "0"];
    Hopper => [Enabled => "true", FacingHopper => "down"];
    Light => [Level => "15", Waterlogged => "false"];
    Rotation160 => [Rotation16 => "0"];
    FacingUp => [Facing => "up"];
    ChorusPlant => [Down => "false", East => "false", North => "false", South => "false", Up => "false", West => "false"];
    Age50 => [Age5 => "0"];
    Age10 => [Age1 => "0"];
    PitcherCrop => [Age4 => "0", DoubleBlockHalf => "lower"];
    Observer => [Facing => "south", Powered => "false"];
    Age250 => [Age25 => "0"];
    TurtleEgg => [Eggs => "1", Hatch => "0"];
    Hatch0 => [Hatch => "0"];
    SubterranodonEgg => [Eggs => "1", Hatch => "0", DinosaurEggBlockNeedsPlayer => "false"];
    GrottoceratopsEgg => [Hatch => "0", DinosaurEggBlockNeedsPlayer => "false"];
    DriedGhast => [HorizontalFacing => "north", DriedGhastHydrationLevels => "0", Waterlogged => "false"];
    WaterloggedTrue => [Waterlogged => "true"];
    BaseCoralWallFan => [HorizontalFacing => "north", Waterlogged => "true"];
    SeaPickle => [Pickles => "1", Waterlogged => "true"];
    BambooStalk => [Age1 => "0", BambooLeaves => "none", Stage => "0"];
    DragTrue => [Drag => "true"];
    Scaffolding => [Bottom => "false", StabilityDistance => "7", Waterlogged => "false"];
    Barrel => [Facing => "north", Open => "false"];
    Grindstone => [AttachFace => "wall", HorizontalFacing => "north"];
    Lectern => [HorizontalFacing => "north", HasBook => "false", Powered => "false"];
    Bell => [BellAttachment => "floor", HorizontalFacing => "north", Powered => "false"];
    Lantern => [Hanging => "false", Waterlogged => "false"];
    Campfire => [HorizontalFacing => "north", Lit => "true", SignalFire => "false", Waterlogged => "false"];
    StructureblockModeLoad => [StructureblockMode => "load"];
    OrientationNorthUp => [Orientation => "north_up"];
    TestBlockModeStart => [TestBlockMode => "start"];
    LevelComposter0 => [LevelComposter => "0"];
    Beehive => [HorizontalFacing => "north", LevelHoney => "0"];
    RespawnAnchorCharges0 => [RespawnAnchorCharges => "0"];
    Candle => [Candles => "1", Lit => "false", Waterlogged => "false"];
    SculkSensor => [Power => "0", SculkSensorPhase => "inactive", Waterlogged => "false"];
    CalibratedSculkSensor => [HorizontalFacing => "north", Power => "0", SculkSensorPhase => "inactive", Waterlogged => "false"];
    BloomFalse => [Bloom => "false"];
    SculkShrieker => [CanSummon => "false", Shrieking => "false", Waterlogged => "false"];
    WeatheringCopperBulb => [Lit => "false", Powered => "false"];
    WeatheringCopperGolemStatue => [CopperGolemPose => "standing", HorizontalFacing => "north", Waterlogged => "false"];
    WeatheringLightningRod => [Facing => "up", Powered => "false", Waterlogged => "false"];
    PointedDripstone => [DripstoneThickness => "tip", VerticalDirection => "up", Waterlogged => "false"];
    CaveVines => [Age25 => "0", Berries => "false"];
    BerriesFalse => [Berries => "false"];
    FlowerBed => [HorizontalFacing => "north", FlowerAmount => "1"];
    LeafLitter => [HorizontalFacing => "north", SegmentAmount => "1"];
    BigDripleaf => [HorizontalFacing => "north", Tilt => "none", Waterlogged => "false"];
    SmallDripleaf => [HorizontalFacing => "north", DoubleBlockHalf => "lower", Waterlogged => "false"];
    DecoratedPot => [Cracked => "false", HorizontalFacing => "north", Waterlogged => "false"];
    Crafter => [Crafting => "false", Orientation => "north_up", Triggered => "false"];
    TrialSpawner => [Ominous => "false", TrialSpawnerState => "inactive"];
    Vault => [HorizontalFacing => "north", Ominous => "false", VaultState => "inactive"];
    MossyCarpet => [Bottom => "true", EastWall => "none", NorthWall => "none", SouthWall => "none", WestWall => "none"];
    TipTrue => [Tip => "true"];
    PrimalMagma => [PrimalMagmaBlockActive => "false", PrimalMagmaBlockPermanent => "false"];
    FissureprimalmagmablockRegenHeight0 => [FissurePrimalMagmaBlockRegenHeight => "0"];
    PewenBranch => [PewenBranchBlockPines => "true", PewenBranchBlockRotation => "0", Waterlogged => "false"];
}
