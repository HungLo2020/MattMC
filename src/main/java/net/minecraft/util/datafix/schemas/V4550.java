package net.minecraft.util.datafix.schemas;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.util.Map;
import java.util.function.Supplier;

public class V4550 extends NamespacedSchema {
	public V4550(int versionKey, Schema parent) {
		super(versionKey, parent);
	}

	@Override
	public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
		Map<String, Supplier<TypeTemplate>> map = super.registerEntities(schema);
		
		// Register Alex's Mobs entities
		schema.registerSimple(map, "minecraft:alligator_snapping_turtle");
		schema.registerSimple(map, "minecraft:anaconda");
		schema.registerSimple(map, "minecraft:anaconda_part");
		schema.registerSimple(map, "minecraft:anteater");
		schema.registerSimple(map, "minecraft:bald_eagle");
		schema.registerSimple(map, "minecraft:bison");
		schema.registerSimple(map, "minecraft:blobfish");
		schema.registerSimple(map, "minecraft:blue_jay");
		schema.registerSimple(map, "minecraft:bunfungus");
		schema.registerSimple(map, "minecraft:cachalot_echo");
		schema.registerSimple(map, "minecraft:cachalot_whale");
		schema.registerSimple(map, "minecraft:caiman");
		schema.registerSimple(map, "minecraft:capuchin_monkey");
		schema.registerSimple(map, "minecraft:catfish");
		schema.registerSimple(map, "minecraft:cave_centipede_body");
		schema.registerSimple(map, "minecraft:cave_centipede_head");
		schema.registerSimple(map, "minecraft:cave_centipede_tail");
		schema.registerSimple(map, "minecraft:cockroach");
		schema.registerSimple(map, "minecraft:cockroach_egg");
		schema.registerSimple(map, "minecraft:comb_jelly");
		schema.registerSimple(map, "minecraft:cosmaw");
		schema.registerSimple(map, "minecraft:cosmic_cod");
		schema.registerSimple(map, "minecraft:crocodile");
		schema.registerSimple(map, "minecraft:crow");
		schema.registerSimple(map, "minecraft:elephant");
		schema.registerSimple(map, "minecraft:emu");
		schema.registerSimple(map, "minecraft:emu_egg");
		schema.registerSimple(map, "minecraft:endergrade");
		schema.registerSimple(map, "minecraft:enderiophage");
		schema.registerSimple(map, "minecraft:flying_fish");
		schema.registerSimple(map, "minecraft:frilled_shark");
		schema.registerSimple(map, "minecraft:gazelle");
		schema.registerSimple(map, "minecraft:gelada_monkey");
		schema.registerSimple(map, "minecraft:giant_squid");
		schema.registerSimple(map, "minecraft:gorilla");
		schema.registerSimple(map, "minecraft:grizzly_bear");
		schema.registerSimple(map, "minecraft:hammerhead_shark");
		schema.registerSimple(map, "minecraft:hummingbird");
		schema.registerSimple(map, "minecraft:jerboa");
		schema.registerSimple(map, "minecraft:kangaroo");
		schema.registerSimple(map, "minecraft:komodo_dragon");
		schema.registerSimple(map, "minecraft:leafcutter_ant");
		schema.registerSimple(map, "minecraft:lobster");
		schema.registerSimple(map, "minecraft:mantis_shrimp");
		schema.registerSimple(map, "minecraft:mimic_octopus");
		schema.registerSimple(map, "minecraft:mimicube");
		schema.registerSimple(map, "minecraft:moose");
		schema.registerSimple(map, "minecraft:mud_ball");
		schema.registerSimple(map, "minecraft:mudskipper");
		schema.registerSimple(map, "minecraft:mungus");
		schema.registerSimple(map, "minecraft:orca");
		schema.registerSimple(map, "minecraft:pewen_boat");
		schema.registerSimple(map, "minecraft:pewen_chest_boat");
		schema.registerSimple(map, "minecraft:platypus");
		schema.registerSimple(map, "minecraft:potoo");
		schema.registerSimple(map, "minecraft:raccoon");
		schema.registerSimple(map, "minecraft:rain_frog");
		schema.registerSimple(map, "minecraft:rattlesnake");
		schema.registerSimple(map, "minecraft:rhinoceros");
		schema.registerSimple(map, "minecraft:roadrunner");
		schema.registerSimple(map, "minecraft:seagull");
		schema.registerSimple(map, "minecraft:shoebill");
		schema.registerSimple(map, "minecraft:skelewag");
		schema.registerSimple(map, "minecraft:skunk");
		schema.registerSimple(map, "minecraft:snow_leopard");
		schema.registerSimple(map, "minecraft:spectre");
		schema.registerSimple(map, "minecraft:sugar_glider");
		schema.registerSimple(map, "minecraft:sunbird");
		schema.registerSimple(map, "minecraft:tarantula_hawk");
		schema.registerSimple(map, "minecraft:tasmanian_devil");
		schema.registerSimple(map, "minecraft:terrapin");
		schema.registerSimple(map, "minecraft:tiger");
		schema.registerSimple(map, "minecraft:tossed_item");
		schema.registerSimple(map, "minecraft:toucan");
		schema.registerSimple(map, "minecraft:underminer");
		schema.registerSimple(map, "minecraft:warped_toad");
		
		// Register Alex's Caves entities
		schema.registerSimple(map, "minecraft:atlatitan");
		schema.registerSimple(map, "minecraft:grottoceratops");
		schema.registerSimple(map, "minecraft:relicheirus");
		schema.registerSimple(map, "minecraft:subterranodon");
		schema.registerSimple(map, "minecraft:tremorsaurus");
		schema.registerSimple(map, "minecraft:trilocaris");
		schema.registerSimple(map, "minecraft:vallumraptor");
		
		return map;
	}

	@Override
	public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
		Map<String, Supplier<TypeTemplate>> map = super.registerBlockEntities(schema);
		
		// Register mod block entities
		schema.registerSimple(map, "minecraft:leafcutter_anthill");
		schema.registerSimple(map, "minecraft:terrapin_egg");
		
		return map;
	}
}
