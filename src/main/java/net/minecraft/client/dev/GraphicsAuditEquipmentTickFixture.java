package net.minecraft.client.dev;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Zombie;

/** Opt-in ordinary simulation commands; never writes entity ages or render clocks. */
public final class GraphicsAuditEquipmentTickFixture {
    private static CompletableFuture<Boolean> freeze;
    private static CompletableFuture<ServerObservation> observation;
    private static volatile int entityId=-1;
    private static final StepPlan plan=new StepPlan();
    private static JsonObject receipt=new JsonObject();
    private record ServerObservation(boolean frozen,boolean running,int ticks) {}

    static boolean enabled(){return Boolean.getBoolean("mattmc.dev.equipmentTickStepping");}
    static boolean prepare(Minecraft minecraft){
        if(!enabled())return true;
        if(!GraphicsAuditEquipmentFixture.requested() || !GraphicsAuditEquipmentFoilTiming.movingRequested())
            throw new IllegalStateException("equipment tick stepping requires moving equipment fixture");
        var server=minecraft.getSingleplayerServer();
        if(server==null || minecraft.level==null)return false;
        if(freeze==null)freeze=server.submit(()->{
            var manager=server.tickRateManager();
            if(manager.isSteppingForward() || manager.isSprinting() || manager.tickrate()!=20.0F)
                throw new IllegalStateException("equipment tick fixture requires idle 20TPS simulation");
            boolean previousFrozen=manager.isFrozen();
            manager.setFrozen(true); // Same ordinary game operation as /tick freeze.
            return previousFrozen;
        });
        if(!freeze.isDone())return false;
        freeze.join();
        var client=minecraft.level.tickRateManager();
        return client.isFrozen() && !client.runsNormally() && !client.isSteppingForward();
    }
    static void configured(Zombie zombie){
        if(!enabled())return;
        if(entityId!=zombie.getId() && plan.pose>=0)
            throw new IllegalStateException("equipment entity replaced during tick plan");
        // Author an angle represented identically by spawn-byte and float sync packets.
        zombie.setYRot(replicatedYaw(zombie.getYRot()));
        entityId=zombie.getId();
    }
    static float replicatedYaw(float yaw){
        if(!Float.isFinite(yaw))throw new IllegalArgumentException("equipment yaw must be finite");
        return net.minecraft.util.Mth.unpackDegrees(net.minecraft.util.Mth.packDegrees(yaw));
    }
    static boolean readyForPose(Minecraft minecraft,int pose){
        if(!enabled())return true;
        if(freeze==null || !freeze.isDone() || minecraft.level==null || minecraft.getSingleplayerServer()==null)return false;
        freeze.join();
        var entity=minecraft.level.getEntity(entityId);
        if(!(entity instanceof Zombie))return false;
        var server=minecraft.getSingleplayerServer();
        int steps=plan.request(pose);
        if(steps>0){
            var dimension=minecraft.level.dimension();
            observation=server.submit(()->{
                if(!server.tickRateManager().stepGameIfPaused(steps))
                    throw new IllegalStateException("equipment simulation unexpectedly resumed");
                return observeServer(server.getLevel(dimension),server.tickRateManager());
            });
        }
        ServerObservation state=null;
        if(observation!=null && observation.isDone()){state=observation.join();observation=null;}
        if(observation==null){
            var dimension=minecraft.level.dimension();
            observation=server.submit(()->observeServer(server.getLevel(dimension),server.tickRateManager()));
        }
        var client=minecraft.level.tickRateManager();
        boolean complete=state!=null && plan.matches(entity.tickCount,state.ticks,
            client.isFrozen() && !client.runsNormally() && !client.isSteppingForward(),
            state.frozen && !state.running);
        receipt=new JsonObject();receipt.addProperty("schema","equipment-game-tick-step-v1");
        receipt.addProperty("pose",pose);receipt.addProperty("targetTicks",pose*10);
        receipt.addProperty("clientTicks",entity.tickCount);
        receipt.addProperty("serverTicks",state==null?-1:state.ticks);
        receipt.addProperty("clientFrozen",client.isFrozen() && !client.runsNormally());
        receipt.addProperty("serverFrozen",state!=null && state.frozen && !state.running);
        receipt.addProperty("complete",complete);
        return complete;
    }
    private static ServerObservation observeServer(net.minecraft.server.level.ServerLevel level,
            net.minecraft.server.ServerTickRateManager manager){
        var entity=level==null?null:level.getEntity(entityId);
        return new ServerObservation(manager.isFrozen(),manager.runsNormally(),entity==null?-1:entity.tickCount);
    }
    static JsonObject snapshot(){return receipt.deepCopy();}
    static void restore(Minecraft minecraft){
        if(freeze!=null && minecraft.getSingleplayerServer()!=null){
            var server=minecraft.getSingleplayerServer();var pending=freeze;
            server.execute(()->{
                boolean previousFrozen=pending.join();
                server.tickRateManager().stopStepping();
                server.tickRateManager().setFrozen(previousFrozen);
            });
        }
        freeze=null;observation=null;entityId=-1;plan.pose=-1;receipt=new JsonObject();
    }
    static final class StepPlan {
        volatile int pose=-1;
        int request(int next){
            if(next<0 || next>=5 || next<pose || next>pose+1)
                throw new IllegalStateException("equipment tick plan requires five ordered poses");
            if(next==pose)return 0;
            pose=next;return next==0?0:10;
        }
        boolean matches(int clientTicks,int serverTicks,boolean clientStopped,boolean serverStopped){
            int expected=pose*10;
            if(pose<0 || clientTicks>expected || serverTicks>expected)
                throw new IllegalStateException("equipment simulation exceeded authored tick pose");
            return clientStopped && serverStopped && clientTicks==expected && serverTicks==expected;
        }
    }
    private GraphicsAuditEquipmentTickFixture(){}
}
