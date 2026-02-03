package net.alexsmobs.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class EntityGiantSquidPart extends Entity {

    private final EntityDimensions size;
    public final EntityGiantSquid parentMob;
    public float scale = 1;
    private boolean collisionOnly = false;

    public EntityGiantSquidPart(EntityGiantSquid parent, float sizeX, float sizeY) {
        super(parent.getType(), parent.level());
        this.size = EntityDimensions.scalable(sizeX, sizeY);
        this.parentMob = parent;
        this.refreshDimensions();
    }

    public EntityGiantSquidPart(EntityGiantSquid parent, float sizeX, float sizeY, boolean collisionOnly) {
        this(parent, sizeX, sizeY);
        this.collisionOnly = collisionOnly;
    }

    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return false;
    }
    
    public Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double)this.getEyeHeight() * 0.15F, (double)(this.getBbWidth() * 0.1F));
    }

    protected void collideWithNearbyEntities() {
        final List<Entity> entities = this.level().getEntities(this, this.getBoundingBox().expandTowards(0.2D, 0.0D, 0.2D));
        Entity parent = parentMob;
        if (parent != null) {
            entities.stream().filter(entity -> entity != parent && !(entity instanceof EntityGiantSquidPart && ((EntityGiantSquidPart) entity).parentMob == parent) && entity.isPushable()).forEach(entity -> entity.push(parent));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return parentMob == null ? InteractionResult.PASS : parentMob.mobInteract(player, hand);
    }

    public boolean canBeCollidedWith() {
        return !collisionOnly;
    }

    protected void collideWithEntity(Entity entityIn) {
        if(!collisionOnly){
            entityIn.push(this);
        }
    }

    public boolean isPickable() {
        return !collisionOnly;
    }

    @Nullable
    public ItemStack getPickResult() {
        Entity parent = parentMob;
        return parent != null ? parent.getPickResult() : ItemStack.EMPTY;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return parentMob != null && !collisionOnly && parentMob.attackEntityPartFrom(this, source, amount);
    }

    public boolean is(Entity entityIn) {
        return this == entityIn || parentMob == entityIn;
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }

    public EntityDimensions getDefaultDimensions(Pose poseIn) {
        return this.size == null ? EntityDimensions.scalable(0, 0) : this.size.scale(scale);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    public void tick(){
        super.tick();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {

    }
}
