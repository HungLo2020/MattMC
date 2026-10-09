package net.minecraft.world.level.block.state.properties;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.NativeSoundDefinitions;
import net.minecraft.util.StringRepresentable;

public enum NoteBlockInstrument implements StringRepresentable {
	HARP("harp"),
	BASEDRUM("basedrum"),
	SNARE("snare"),
	HAT("hat"),
	BASS("bass"),
	FLUTE("flute"),
	BELL("bell"),
	GUITAR("guitar"),
	CHIME("chime"),
	XYLOPHONE("xylophone"),
	IRON_XYLOPHONE("iron_xylophone"),
	COW_BELL("cow_bell"),
	DIDGERIDOO("didgeridoo"),
	BIT("bit"),
	BANJO("banjo"),
	PLING("pling"),
	ZOMBIE("zombie"),
	SKELETON("skeleton"),
	CREEPER("creeper"),
	DRAGON("dragon"),
	WITHER_SKELETON("wither_skeleton"),
	PIGLIN("piglin"),
	CUSTOM_HEAD("custom_head");

    private final String name;
    private final Holder<SoundEvent> soundEvent;
    private final boolean tunable, custom, above;

    private NoteBlockInstrument(String key) {
        var definition = NativeSoundDefinitions.instrument(key);
        this.name = definition.name();
        this.soundEvent = NativeSoundDefinitions.holder(definition.event());
        this.tunable = definition.kind() == 0;
        this.custom = definition.kind() == 2;
        this.above = definition.kind() != 0;
    }
    @Override public String getSerializedName() { return this.name; }
    public Holder<SoundEvent> getSoundEvent() { return this.soundEvent; }
    public boolean isTunable() { return this.tunable; }
    public boolean hasCustomSound() { return this.custom; }
    public boolean worksAboveNoteBlock() { return this.above; }
}
