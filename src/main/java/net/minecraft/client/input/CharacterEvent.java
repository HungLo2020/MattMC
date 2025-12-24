package net.minecraft.client.input;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.StringUtil;

@Environment(EnvType.CLIENT)
public record CharacterEvent(int codepoint, int modifiers) {
	public String codepointAsString() {
		return Character.toString(this.codepoint);
	}

	public boolean isAllowedChatCharacter() {
		return StringUtil.isAllowedChatCharacter(this.codepoint);
	}
}
