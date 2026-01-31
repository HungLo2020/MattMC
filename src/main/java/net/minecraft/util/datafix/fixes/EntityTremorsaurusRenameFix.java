package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import java.util.Objects;

public class EntityTremorsaurusRenameFix extends SimplestEntityRenameFix {
	public EntityTremorsaurusRenameFix(Schema schema, boolean bl) {
		super("EntityTremorsaurusRenameFix", schema, bl);
	}

	@Override
	protected String rename(String string) {
		return Objects.equals("quantize:tremorsaurus", string) ? "minecraft:tremorsaurus" : string;
	}
}
