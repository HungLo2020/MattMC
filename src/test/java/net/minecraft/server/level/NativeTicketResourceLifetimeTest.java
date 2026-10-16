package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.*;

class NativeTicketResourceLifetimeTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    record Held(MemorySegment owner, WeakReference<Ticket> ticket, int cell) {}
    static Held held() {
        Ticket ticket = new Ticket(TicketType.PORTAL, 33); ticket.decreaseTicksLeft();
        MemorySegment owner = Ticket.prepareNativeCounter(ticket, ticket.unboundTicksLeft());
        return new Held(owner, new WeakReference<>(ticket), owner.get(ValueLayout.JAVA_INT, 28));
    }
    @Test void retainedActualResourceKeepsCounterWithoutPinningItsTicketFacade() throws Exception {
        Held held = held();
        assertTrue(held.owner().isReadOnly(), "internal resource must expose a read-only capability");
        for (int attempt=0; attempt<100 && held.ticket().get()!=null; attempt++) { System.gc(); Thread.sleep(20); }
        assertNull(held.ticket().get(), "resource owner retained its Ticket facade through a backreference");
        for (int i=0; i<800; i++) {
            Ticket churn = new Ticket(TicketType.PORTAL, 33); churn.decreaseTicksLeft();
            MemorySegment owner = Ticket.prepareNativeCounter(churn, churn.unboundTicksLeft());
            assertNotEquals(held.cell(), owner.get(ValueLayout.JAVA_INT, 28), "retained actual owner cell was reused");
        }
        assertEquals(299, held.owner().get(ValueLayout.JAVA_LONG, 0));
        assertEquals(-1, held.owner().get(ValueLayout.JAVA_INT, 24));
        Reference.reachabilityFence(held);
    }
    @SuppressWarnings("unchecked") static WeakReference<?>[] abandoned() {
        Ticket ticket = new Ticket(TicketType.PORTAL, 33); ticket.decreaseTicksLeft();
        MemorySegment owner = Ticket.prepareNativeCounter(ticket, ticket.unboundTicksLeft());
        return new WeakReference<?>[] {new WeakReference<>(ticket), new WeakReference<>(owner)};
    }
    @Test void neverStoredCounterAndFacadeCanBothRetire() throws Exception {
        WeakReference<?>[] gone = abandoned();
        for (int attempt=0; attempt<100 && (gone[0].get()!=null || gone[1].get()!=null); attempt++) { System.gc(); Thread.sleep(20); }
        assertNull(gone[0].get(), "never-stored Ticket stayed reachable");
        assertNull(gone[1].get(), "never-stored counter resource stayed reachable");
    }
}
