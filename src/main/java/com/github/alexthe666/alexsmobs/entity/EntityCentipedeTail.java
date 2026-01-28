package com.github.alexthe666.alexsmobs.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

public class EntityCentipedeTail extends EntityCentipedeBody {

    public EntityCentipedeTail(EntityType type, Level worldIn) {
        super(type, worldIn);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return EntityCentipedeBody.bakeAttributes();
    }
}
