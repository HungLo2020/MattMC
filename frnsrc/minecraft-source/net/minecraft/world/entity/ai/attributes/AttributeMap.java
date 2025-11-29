package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class AttributeMap {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Map<Attribute, AttributeInstance> attributes = Maps.newHashMap();
   private final Set<AttributeInstance> dirtyAttributes = Sets.newHashSet();
   private final AttributeSupplier supplier;

   public AttributeMap(AttributeSupplier pSupplier) {
      this.supplier = pSupplier;
   }

   private void onAttributeModified(AttributeInstance p_22158_) {
      if (p_22158_.getAttribute().isClientSyncable()) {
         this.dirtyAttributes.add(p_22158_);
      }

   }

   public Set<AttributeInstance> getDirtyAttributes() {
      return this.dirtyAttributes;
   }

   public Collection<AttributeInstance> getSyncableAttributes() {
      return this.attributes.values().stream().filter((p_22184_) -> {
         return p_22184_.getAttribute().isClientSyncable();
      }).collect(Collectors.toList());
   }

   @Nullable
   public AttributeInstance getInstance(Attribute pAttribute) {
      return this.attributes.computeIfAbsent(pAttribute, (p_22188_) -> {
         return this.supplier.createInstance(this::onAttributeModified, p_22188_);
      });
   }

   @Nullable
   public AttributeInstance getInstance(Holder<Attribute> p_250010_) {
      return this.getInstance(p_250010_.value());
   }

   public boolean hasAttribute(Attribute pAttribute) {
      return this.attributes.get(pAttribute) != null || this.supplier.hasAttribute(pAttribute);
   }

   public boolean hasAttribute(Holder<Attribute> p_248893_) {
      return this.hasAttribute(p_248893_.value());
   }

   public boolean hasModifier(Attribute pAttribute, UUID pUuid) {
      AttributeInstance attributeinstance = this.attributes.get(pAttribute);
      return attributeinstance != null ? attributeinstance.getModifier(pUuid) != null : this.supplier.hasModifier(pAttribute, pUuid);
   }

   public boolean hasModifier(Holder<Attribute> p_250299_, UUID p_250415_) {
      return this.hasModifier(p_250299_.value(), p_250415_);
   }

   public double getValue(Attribute pAttribute) {
      AttributeInstance attributeinstance = this.attributes.get(pAttribute);
      return attributeinstance != null ? attributeinstance.getValue() : this.supplier.getValue(pAttribute);
   }

   public double getBaseValue(Attribute pAttribute) {
      AttributeInstance attributeinstance = this.attributes.get(pAttribute);
      return attributeinstance != null ? attributeinstance.getBaseValue() : this.supplier.getBaseValue(pAttribute);
   }

   public double getModifierValue(Attribute pAttribute, UUID pUuid) {
      AttributeInstance attributeinstance = this.attributes.get(pAttribute);
      return attributeinstance != null ? attributeinstance.getModifier(pUuid).getAmount() : this.supplier.getModifierValue(pAttribute, pUuid);
   }

   public double getModifierValue(Holder<Attribute> p_251534_, UUID p_250438_) {
      return this.getModifierValue(p_251534_.value(), p_250438_);
   }

   public void removeAttributeModifiers(Multimap<Attribute, AttributeModifier> pMap) {
      pMap.asMap().forEach((p_22152_, p_22153_) -> {
         AttributeInstance attributeinstance = this.attributes.get(p_22152_);
         if (attributeinstance != null) {
            p_22153_.forEach(attributeinstance::removeModifier);
         }

      });
   }

   public void addTransientAttributeModifiers(Multimap<Attribute, AttributeModifier> pMap) {
      pMap.forEach((p_22149_, p_22150_) -> {
         AttributeInstance attributeinstance = this.getInstance(p_22149_);
         if (attributeinstance != null) {
            attributeinstance.removeModifier(p_22150_);
            attributeinstance.addTransientModifier(p_22150_);
         }

      });
   }

   public void assignValues(AttributeMap pManager) {
      pManager.attributes.values().forEach((p_22177_) -> {
         AttributeInstance attributeinstance = this.getInstance(p_22177_.getAttribute());
         if (attributeinstance != null) {
            attributeinstance.replaceFrom(p_22177_);
         }

      });
   }

   public ListTag save() {
      ListTag listtag = new ListTag();

      for(AttributeInstance attributeinstance : this.attributes.values()) {
         listtag.add(attributeinstance.save());
      }

      return listtag;
   }

   public void load(ListTag pNbt) {
      for(int i = 0; i < pNbt.size(); ++i) {
         CompoundTag compoundtag = pNbt.getCompound(i);
         String s = compoundtag.getString("Name");
         Util.ifElse(BuiltInRegistries.ATTRIBUTE.getOptional(ResourceLocation.tryParse(s)), (p_22167_) -> {
            AttributeInstance attributeinstance = this.getInstance(p_22167_);
            if (attributeinstance != null) {
               attributeinstance.load(compoundtag);
            }

         }, () -> {
            LOGGER.warn("Ignoring unknown attribute '{}'", (Object)s);
         });
      }

   }
}