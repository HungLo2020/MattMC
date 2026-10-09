package net.minecraft.sounds;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.SoundType;
import net.alexscaves.server.block.ACSoundTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeSoundDefinitionsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void eventViewsRetainRegistryIdentityAndOrder() {
        assertEquals(BuiltInRegistries.SOUND_EVENT.size(),NativeSoundDefinitions.eventCount());
        for (int id=0;id<NativeSoundDefinitions.eventCount();id++) {
            var holder=NativeSoundDefinitions.holder(id);var event=holder.value();
            assertEquals(id,BuiltInRegistries.SOUND_EVENT.getId(event));
            assertSame(holder,NativeSoundDefinitions.holder(BuiltInRegistries.SOUND_EVENT.getKey(event).toString()));
            assertSame(event,NativeSoundDefinitions.event(event.location().toString()));
        }
        assertSame(SoundEvents.ITEM_PICKUP,NativeSoundDefinitions.event("entity.item.pickup"));
        assertThrows(IllegalStateException.class,()->NativeSoundDefinitions.event("minecraft:absent_test_event"));
    }
    @Test void sharedEventReferencesDoNotMergeSoundProfileIdentities() {
        assertSame(SoundType.WOOD,SoundType.nativeView(NativeSoundDefinitions.typeId("WOOD")));
        assertSame(ACSoundTypes.PEWEN_BRANCH,SoundType.nativeView(NativeSoundDefinitions.typeId("AC_PEWEN_BRANCH")));
        assertNotSame(SoundType.CHERRY_WOOD,ACSoundTypes.PEWEN_BRANCH);
        assertSame(SoundType.CHERRY_WOOD.getBreakSound(),ACSoundTypes.PEWEN_BRANCH.getBreakSound());
        var custom=new SoundType(0.25F,2.0F,SoundEvents.EMPTY,SoundEvents.EMPTY,SoundEvents.EMPTY,SoundEvents.EMPTY,SoundEvents.EMPTY);
        assertEquals(0.25F,custom.getVolume());assertEquals(2.0F,custom.getPitch());
    }
}
