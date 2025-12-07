package net.minecraft.client.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record AdultAndBabyModelPair<T extends Model>(T adultModel, T babyModel) {
	public T getModel(boolean bl) {
		return bl ? this.babyModel : this.adultModel;
	}
}
