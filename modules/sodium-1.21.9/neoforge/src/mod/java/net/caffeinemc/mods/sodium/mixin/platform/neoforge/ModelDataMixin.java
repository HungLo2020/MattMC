package net.caffeinemc.mods.sodium.mixin.platform.neoforge;

import net.minecraft.client.renderer.sodium.services.SodiumModelData;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelData.class)
public class ModelDataMixin implements SodiumModelData {
}
