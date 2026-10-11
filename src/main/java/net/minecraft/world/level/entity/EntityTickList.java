package net.minecraft.world.level.entity;

import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;

/** Ordered ticking membership belongs to Rust; Java supplies behavior callbacks. */
public class EntityTickList {
    private final NativeEntityOrder order=new NativeEntityOrder();

    public void add(Entity entity) { order.add(entity); }
    public void remove(Entity entity) { order.remove(entity); }
    public boolean contains(Entity entity) { return order.contains(entity); }
    public void forEach(Consumer<Entity> consumer) { order.forEach(consumer); }
}
