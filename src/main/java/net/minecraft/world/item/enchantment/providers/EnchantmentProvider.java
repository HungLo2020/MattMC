package net.minecraft.world.item.enchantment.providers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public interface EnchantmentProvider {
	Codec<EnchantmentProvider> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(net.minecraft.resources.NativeData.Kind.ENCHANTMENT_PROVIDER, false, net.minecraft.resources.NativeDataProjections::enchantmentProvider, net.minecraft.resources.NativeDataProjections::enchantmentProviderOut);

	void enchant(ItemStack itemStack, ItemEnchantments.Mutable mutable, RandomSource randomSource, DifficultyInstance difficultyInstance);

	MapCodec<? extends EnchantmentProvider> codec();
}
