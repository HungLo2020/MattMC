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
	/** The counter until a ticket storage first stores this ticket. */
	private long ticksLeft;
	/**
	 * Once a ticket storage stores this ticket, its counter lives in a native
	 * cell that every holder shares, as they shared this object, for the rest of
	 * the ticket's life; -1 before.
	 */
	private int cell = -1;
	private long cellAddress;

	public Ticket(TicketType ticketType, int i) {
		this(ticketType, i, ticketType.timeout());
	}

	private Ticket(TicketType ticketType, int i, long l) {
		this.type = ticketType;
		this.ticketLevel = i;
		this.ticksLeft = l;
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

	/** The bound cell is freed by this ticket's cleaner, so every access keeps the ticket reachable. */
	private long ticksLeft() {
		try {
			return this.cell < 0 ? this.ticksLeft : CELLS.get(ValueLayout.JAVA_LONG, this.cellAddress);
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	private void setTicksLeft(long l) {
		try {
			if (this.cell < 0) {
				this.ticksLeft = l;
			} else {
				CELLS.set(ValueLayout.JAVA_LONG, this.cellAddress, l);
			}
		} finally {
			Reference.reachabilityFence(this);
		}
	}

	public void resetTicksLeft() {
		this.setTicksLeft(this.type.timeout());
	}

	public void decreaseTicksLeft() {
		if (this.type.hasTimeout()) {
			this.setTicksLeft(this.ticksLeft() - 1);
		}
	}

	public boolean isTimedOut() {
		return this.type.hasTimeout() && this.ticksLeft() < 0L;
	}

	/** Ticket storage binding: the native cell, or -1 when unbound. */
	public int boundCell() {
		return this.cell;
	}

	/** Ticket storage binding: the counter of a ticket never stored yet. */
	public long unboundTicksLeft() {
		return this.ticksLeft;
	}

	/** Ticket storage binding: the counter now lives in this native cell, for good. */
	public void bindCell(int cell, long address) {
		this.cellAddress = address;
		this.cell = cell;
	}
}
