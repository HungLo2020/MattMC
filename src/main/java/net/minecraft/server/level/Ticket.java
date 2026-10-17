package net.minecraft.server.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;

public class Ticket {
	public static final MapCodec<Ticket> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				BuiltInRegistries.TICKET_TYPE.byNameCodec().fieldOf("type").forGetter(Ticket::getType),
				ExtraCodecs.NON_NEGATIVE_INT.fieldOf("level").forGetter(Ticket::getTicketLevel),
				Codec.LONG.optionalFieldOf("ticks_left", 0L).forGetter(Ticket::ticksLeft)
			)
			.apply(instance, Ticket::new)
	);
	/** Native memory, for a bound ticket's shared {@code ticksLeft} cell. */
	private static final MemorySegment CELLS = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
	private final TicketType type;
	private final int ticketLevel;
    /** Immutable constructor specification until an actual counter resource is needed. */
    private final long initialTicksLeft;
    /** Actual primary native counter resource; no completion or membership mirror. */
    private MemorySegment nativeCounter;
    // An explicit early adapter preserves overridable subclass dispatch and the
    // original interop fields. Stock Tickets use only their actual native owner.
    private long foreignTicksLeft;
    private int foreignCell = -1;
    private long foreignCellAddress;

	public Ticket(TicketType ticketType, int i) {
		this(ticketType, i, ticketType.timeout());
	}

	private Ticket(TicketType ticketType, int i, long l) {
		this.type = ticketType;
		this.ticketLevel = i;
		this.initialTicksLeft = l;
        this.foreignTicksLeft = l;
	}

	public String toString() {
		return this.type.hasTimeout()
			? "Ticket["
				+ Util.getRegisteredName(BuiltInRegistries.TICKET_TYPE, this.type)
				+ " "
				+ this.ticketLevel
				+ "] with "
				+ this.ticksLeft()
				+ " ticks left ( out of"
				+ this.type.timeout()
				+ ")"
			: "Ticket[" + Util.getRegisteredName(BuiltInRegistries.TICKET_TYPE, this.type) + " " + this.ticketLevel + "] with no timeout";
	}

	public TicketType getType() {
		return this.type;
	}

	public int getTicketLevel() {
		return this.ticketLevel;
	}

    /** Immutable VM binding metadata for native list resources. Admission and
     * consumer decisions must query the actual virtual getters separately. */
    public static TicketType nativeBindingType(Ticket ticket) { return ticket.type; }
    public static int nativeBindingLevel(Ticket ticket) { return ticket.ticketLevel; }

    private boolean stock() { return this.getClass() == Ticket.class; }
    private MemorySegment counter() {
        MemorySegment owner = this.nativeCounter;
        if (owner == null) this.nativeCounter = owner = NativeTicket.reserve(this.initialTicksLeft);
        return owner;
    }
    private long ticksLeft() {
        try {
            if (this.stock()) {
                MemorySegment owner = this.nativeCounter;
                return owner == null ? this.initialTicksLeft : NativeTicket.ticks(owner);
            }
            return this.foreignCell < 0 ? this.foreignTicksLeft : CELLS.get(ValueLayout.JAVA_LONG, this.foreignCellAddress);
        } finally { Reference.reachabilityFence(this); }
    }
    private void setTicksLeft(long value) {
        try {
            if (this.stock()) NativeTicket.set(this.counter(), value);
            else if (this.foreignCell < 0) this.foreignTicksLeft = value;
            else CELLS.set(ValueLayout.JAVA_LONG, this.foreignCellAddress, value);
        } finally { Reference.reachabilityFence(this); }
    }
    public void resetTicksLeft() { this.setTicksLeft(this.type.timeout()); }
    public void decreaseTicksLeft() {
        if (this.type.hasTimeout()) {
            if (this.stock()) NativeTicket.decrease(this.counter());
            else this.setTicksLeft(this.ticksLeft() - 1);
        }
    }
    public boolean isTimedOut() {
        if (!this.type.hasTimeout()) return false;
        if (!this.stock()) return this.ticksLeft() < 0L;
        MemorySegment owner = this.nativeCounter;
        return owner == null ? NativeTicket.initialTimedOut(this.initialTicksLeft) : NativeTicket.timedOut(owner);
    }
    /** Original bind visibility: reserved resources still report -1. */
    public int boundCell() {
        if (!this.stock()) return this.foreignCell;
        MemorySegment owner = this.nativeCounter;
        return owner == null ? -1 : NativeTicket.boundCell(owner);
    }
    /** Historical unbound value freezes when this resource is first bound. */
    public long unboundTicksLeft() {
        if (!this.stock()) return this.foreignTicksLeft;
        MemorySegment owner = this.nativeCounter;
        return owner == null ? this.initialTicksLeft : NativeTicket.historical(owner);
    }
    /** Internal resource preparation, before storage entry publication. */
    public static MemorySegment prepareNativeCounter(Ticket ticket, long initial) {
        if (!ticket.stock()) return null;
        MemorySegment owner = ticket.nativeCounter;
        if (owner == null) ticket.nativeCounter = owner = NativeTicket.reserve(initial);
        return owner;
    }
    public static boolean hasNativeCounter(Ticket ticket) { return ticket.stock() && ticket.nativeCounter != null; }
    /** Ticket storage binding at the original virtual dispatch boundary. */
    public void bindCell(int cell, long address) {
        if (!this.stock()) {
            this.foreignCellAddress = address;
            this.foreignCell = cell;
            return;
        }
        MemorySegment owner = this.nativeCounter;
        if (owner == null || NativeTicket.resourceCell(owner) != cell || owner.address() != address) {
            this.nativeCounter = owner = NativeTicket.adopt(cell);
        }
        NativeTicket.bind(owner, cell);
    }
}
