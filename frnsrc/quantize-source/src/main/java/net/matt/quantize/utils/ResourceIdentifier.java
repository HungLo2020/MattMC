package net.matt.quantize.utils;


import net.matt.quantize.Quantize;
import net.minecraft.resources.ResourceLocation;

/*
    I hate ResourceLocation mojang
    I hate ResourceLocation mojang
    I hate ResourceLocation mojang
    I hate ResourceLocation mojang
    I hate ResourceLocation mojang
    I hate ResourceLocation mojang
 */

@SuppressWarnings("removal")
public class ResourceIdentifier extends ResourceLocation {
    public ResourceIdentifier(String path) {
        super(Quantize.MOD_ID, path);
    }
}