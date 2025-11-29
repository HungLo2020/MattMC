package net.matt.quantize.entities.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.Level;

public class EntityCentipedeTail extends EntityCentipedeBody {

    public EntityCentipedeTail(EntityType type, Level worldIn) {
        super(type, worldIn);
    }

    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    /*public static EntityCentipedeTail create(EntityType<EntityCentipedeTail> type, Level level) {
        return new EntityCentipedeTail(type, level);
    }*/

}
