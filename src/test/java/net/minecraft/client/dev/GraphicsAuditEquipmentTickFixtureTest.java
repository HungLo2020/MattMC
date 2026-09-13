package net.minecraft.client.dev;

import net.minecraft.world.TickRateManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsAuditEquipmentTickFixtureTest {
    @Test void authoredYawIsStableAcrossBothVanillaPacketRepresentations(){
        assertEquals(-75.9375F,GraphicsAuditEquipmentTickFixture.replicatedYaw(285F));
        for(int value=-128;value<128;value++){
            float yaw=net.minecraft.util.Mth.unpackDegrees((byte)value);
            assertEquals(yaw,GraphicsAuditEquipmentTickFixture.replicatedYaw(yaw));
            assertEquals((byte)value,net.minecraft.util.Mth.packDegrees(yaw));
        }
        assertThrows(IllegalArgumentException.class,()->GraphicsAuditEquipmentTickFixture.replicatedYaw(Float.NaN));
    }

    @Test void ordinaryTickStepsAdvanceAllFivePosesExactlyOnce(){
        var plan=new GraphicsAuditEquipmentTickFixture.StepPlan();
        var ticks=new TickRateManager();ticks.setFrozen(true);ticks.tick();
        int age=0;
        for(int pose=0;pose<5;pose++){
            int requested=plan.request(pose);
            assertEquals(pose==0?0:10,requested);
            assertEquals(0,plan.request(pose));
            ticks.setFrozenTicksToRun(requested);
            while(ticks.isSteppingForward()){
                ticks.tick();if(ticks.runsNormally())age++;
                assertFalse(plan.matches(age,age,false,false));
            }
            ticks.tick();
            assertEquals(pose*10,age);
            assertTrue(plan.matches(age,age,!ticks.runsNormally(),!ticks.runsNormally()));
            for(int wait=0;wait<100;wait++){ticks.tick();assertFalse(ticks.runsNormally());}
            assertEquals(pose*10,age);
        }
    }
    @Test void missingReplicationOvershootAndReorderedPosesCannotPass(){
        var plan=new GraphicsAuditEquipmentTickFixture.StepPlan();
        assertThrows(IllegalStateException.class,()->plan.request(1));
        plan.request(0);plan.request(1);
        assertFalse(plan.matches(9,10,true,true));
        assertFalse(plan.matches(10,9,true,true));
        assertFalse(plan.matches(10,10,true,false));
        assertThrows(IllegalStateException.class,()->plan.matches(11,10,true,true));
        assertThrows(IllegalStateException.class,()->plan.request(0));
        assertThrows(IllegalStateException.class,()->plan.request(3));
        assertThrows(IllegalStateException.class,()->plan.request(5));
    }
}
