package net.citadel.server.entity;

import net.citadel.server.tick.modifier.TickRateModifier;

public interface IModifiesTime {

    boolean isTimeModificationValid(TickRateModifier modifier);

}
