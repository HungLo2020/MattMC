package com.mojang.blaze3d;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.annotation.meta.TypeQualifierDefault;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@TypeQualifierDefault({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Environment(EnvType.CLIENT)
public @interface DontObfuscate {
}
