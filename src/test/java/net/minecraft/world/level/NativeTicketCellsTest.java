package net.minecraft.world.level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Lifetime of the native ticksLeft cells behind stored tickets. */
class NativeTicketCellsTest {
	@BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

	@Test
	void ticketHandedBetweenThreadsKeepsItsCellAndCounter() throws Exception {
		ExecutorService a = Executors.newSingleThreadExecutor();
		ExecutorService b = Executors.newSingleThreadExecutor();
		try {
			TicketStorage first = new TicketStorage();
			TicketStorage second = new TicketStorage();
			Ticket ticket = new Ticket(TicketType.PORTAL, 33);
			// Stored and removed on thread A: its last entry is gone, so it is queued.
			int cell = CompletableFuture.supplyAsync(() -> {
				first.addTicket(7L, ticket);
				ticket.decreaseTicksLeft();
				first.removeTicket(7L, new Ticket(TicketType.PORTAL, 33));
				return ticket.boundCell();
			}, a).get();
			assertTrue(cell >= 0);
			// Handed to thread B, whose operations drain the queue, then stored again there.
			CompletableFuture.runAsync(() -> {
				for (int i = 0; i < 50; i++) {
					Ticket churn = new Ticket(TicketType.UNKNOWN, 31);
					second.addTicket(100L + i, churn);
					second.removeTicket(100L + i, churn);
				}
				second.addTicket(9L, ticket);
				ticket.decreaseTicksLeft();
			}, b).get();
			assertEquals(cell, ticket.boundCell());
			assertSame(ticket, second.getTickets(9L).get(0));
			assertTrue(second.getTicketDebugString(9L, false).contains("298 ticks left"), second.getTicketDebugString(9L, false));
			// Thread A can keep using it after another handoff.
			CompletableFuture.runAsync(() -> {
				first.addTicket(7L, ticket);
				ticket.resetTicksLeft();
			}, a).get();
			assertTrue(second.getTicketDebugString(9L, false).contains("300 ticks left"));
		} finally {
			a.shutdownNow();
			b.shutdownNow();
		}
	}

	@Test
	void cellsOfUnreachableTicketsAreReused() throws Exception {
		TicketStorage storage = new TicketStorage();
		List<WeakReference<Ticket>> gone = new ArrayList<>();
		int highest = -1;
		for (int i = 0; i < 2000; i++) {
			Ticket ticket = new Ticket(TicketType.UNKNOWN, 31);
			storage.addTicket(i, ticket);
			highest = Math.max(highest, ticket.boundCell());
			gone.add(new WeakReference<>(ticket));
		}
		for (int i = 0; i < 2000; i++) {
			storage.removeTicket(i, new Ticket(TicketType.UNKNOWN, 31));
		}
		// Unpinned by the removals' drains, so collectable; each cleaner frees its cell.
		for (int attempt = 0; attempt < 50 && gone.stream().anyMatch(reference -> reference.get() != null); attempt++) {
			System.gc();
			Thread.sleep(20);
		}
		assertTrue(gone.stream().allMatch(reference -> reference.get() == null), "removed tickets stayed reachable");
		boolean reused = false;
		for (int attempt = 0; attempt < 50 && !reused; attempt++) {
			Thread.sleep(20);
			Ticket fresh = new Ticket(TicketType.FORCED, 31);
			storage.addTicket(5000L + attempt, fresh);
			reused = fresh.boundCell() <= highest;
		}
		assertTrue(reused, "no freed cell was reused");
	}

	@Test
	void unreachableStorageReleasesItsTickets() throws Exception {
		WeakReference<Ticket> held;
		{
			TicketStorage storage = new TicketStorage();
			Ticket ticket = new Ticket(TicketType.FORCED, 31);
			storage.addTicket(1L, ticket);
			held = new WeakReference<>(ticket);
		}
		// The storage's own cleanup releases its entries; a later drain unpins the ticket.
		for (int attempt = 0; attempt < 100 && held.get() != null; attempt++) {
			System.gc();
			Thread.sleep(20);
			TicketStorage other = new TicketStorage();
			Ticket probe = new Ticket(TicketType.UNKNOWN, 31);
			other.addTicket(1L, probe);
			other.removeTicket(1L, probe);
		}
		assertNull(held.get(), "a ticket of an unreachable storage stayed pinned");
	}
}
