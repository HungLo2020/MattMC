package net.minecraft.network.chat.contents.objects;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.world.item.component.ResolvableProfile;

public record PlayerSprite(ResolvableProfile player, boolean hat) implements ObjectInfo {
	@Override
	public FontDescription fontDescription() {
		return new FontDescription.PlayerSprite(this.player, this.hat);
	}

	@Override
	public String description() {
		return (String)this.player.name().map(string -> "[" + string + " head]").orElse("[unknown player head]");
	}

}
