package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;

/** Public ticket semantics shared by the frozen and retained native owners. */
class TicketOwnershipBoundaryContractTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    static long ticks(Ticket ticket) {
        JsonObject value = Ticket.CODEC.codec().encodeStart(JsonOps.INSTANCE, ticket).getOrThrow().getAsJsonObject();
        return value.has("ticks_left") ? value.get("ticks_left").getAsLong() : 0;
    }
    static Ticket decode(long counter, int level) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "minecraft:portal");
        object.addProperty("level", level);
        object.addProperty("ticks_left", counter);
        return Ticket.CODEC.codec().parse(JsonOps.INSTANCE, object).getOrThrow();
    }
    static void historical(Ticket ticket, long expected) throws Exception {
        try { assertEquals(expected, Ticket.class.getMethod("unboundTicksLeft").invoke(ticket)); }
        catch (NoSuchMethodException frozenWithoutNativeBinding) {}
    }
    @Test void codecExplicitAndDefaultCountersDifferFromPublicConstructor() {
        assertEquals(300, ticks(new Ticket(TicketType.PORTAL, 33)));
        assertEquals(-9, ticks(decode(-9, 33)));
        assertTrue(decode(-9, 33).isTimedOut());
        JsonObject object = new JsonObject(); object.addProperty("type", "minecraft:portal"); object.addProperty("level", 33);
        assertEquals(0, ticks(Ticket.CODEC.codec().parse(JsonOps.INSTANCE, object).getOrThrow()));
        object.addProperty("level", -1);
        assertTrue(Ticket.CODEC.codec().parse(JsonOps.INSTANCE, object).error().isPresent());
        assertEquals(-1, new Ticket(TicketType.PORTAL, -1).getTicketLevel());
        assertThrows(NullPointerException.class, () -> new Ticket(null, 33));
    }
    @Test void nativeBindingDoesNotReplaceHistoricalUnboundCounter() throws Exception {
        Ticket ticket = new Ticket(TicketType.PORTAL, 33);
        ticket.decreaseTicksLeft(); historical(ticket, 299);
        TicketStorage storage = new TicketStorage(); storage.addTicket(7L, ticket);
        assertSame(ticket, storage.getTickets(7L).get(0));
        ticket.decreaseTicksLeft(); assertEquals(298, ticks(ticket)); historical(ticket, 299);
        ticket.resetTicksLeft(); assertEquals(300, ticks(ticket)); historical(ticket, 299);
    }
    @Test void retainedTicketSharesCounterAcrossStoragesAndRemoveReadd() throws Exception {
        Ticket ticket = new Ticket(TicketType.PORTAL, 33);
        TicketStorage first = new TicketStorage(), second = new TicketStorage();
        first.addTicket(7L, ticket); second.addTicket(9L, ticket);
        ticket.decreaseTicksLeft(); assertEquals(299, ticks(second.getTickets(9L).get(0)));
        first.removeTicket(7L, new Ticket(TicketType.PORTAL, 33));
        assertSame(ticket, second.getTickets(9L).get(0));
        second.removeTicket(9L, new Ticket(TicketType.PORTAL, 33));
        ticket.decreaseTicksLeft(); assertEquals(298, ticks(ticket)); historical(ticket, 300);
        first.addTicket(11L, ticket); assertSame(ticket, first.getTickets(11L).get(0));
        assertEquals(298, ticks(first.getTickets(11L).get(0)));
        ticket.resetTicksLeft(); assertEquals(300, ticks(ticket));
    }
    @Test void explicitCounterOverflowAndNoTimeoutRetainOriginalArithmetic() {
        Ticket overflow = decode(Long.MIN_VALUE, 33);
        assertTrue(overflow.isTimedOut()); overflow.decreaseTicksLeft();
        assertEquals(Long.MAX_VALUE, ticks(overflow)); assertFalse(overflow.isTimedOut());
        Ticket timeless = new Ticket(TicketType.FORCED, 33);
        timeless.decreaseTicksLeft(); assertFalse(timeless.isTimedOut());
        assertTrue(timeless.toString().endsWith("with no timeout"));
        Ticket negativeTimeout = new Ticket(new TicketType(-1, 2), 33);
        assertTrue(negativeTimeout.isTimedOut()); negativeTimeout.decreaseTicksLeft();
        negativeTimeout.resetTicksLeft(); assertTrue(negativeTimeout.isTimedOut());
    }
    @Test void typeIdentityMatchingAndForcedEqualityStayDistinct() {
        TicketStorage storage = new TicketStorage();
        Ticket canonical = new Ticket(TicketType.FORCED, 33);
        Ticket equalType = new Ticket(new TicketType(TicketType.FORCED.timeout(), TicketType.FORCED.flags()), 33);
        assertNotSame(canonical.getType(), equalType.getType()); assertEquals(canonical.getType(), equalType.getType());
        storage.addTicket(7L, canonical); storage.addTicket(7L, equalType);
        assertEquals(2, storage.getTickets(7L).size());
        assertTrue(storage.getForceLoadedChunks().contains(7L));
        assertSame(canonical, storage.getTickets(7L).get(0)); assertSame(equalType, storage.getTickets(7L).get(1));
    }
    @Test void counterMethodsUseOriginalTypeWithoutOverridableGetterDispatch() {
        Ticket ticket = new Ticket(TicketType.PORTAL, 33) {
            @Override public TicketType getType() { throw new AssertionError("counter dispatched getType"); }
            @Override public int getTicketLevel() { throw new AssertionError("counter dispatched getTicketLevel"); }
        };
        assertFalse(ticket.isTimedOut()); ticket.decreaseTicksLeft(); ticket.resetTicksLeft();
        assertTrue(ticket.toString().contains("300 ticks left"));
    }
    @Test void listInsertAndReplacementRetainTheActualRemovedTicketAndCounters() throws Exception {
        TicketStorage storage = new TicketStorage();
        Ticket original = new Ticket(TicketType.PORTAL, 33);
        storage.addTicket(7L, original);
        java.util.List<Ticket> list = storage.getTickets(7L);
        Ticket appended = new Ticket(TicketType.PORTAL, 32);
        list.add(1, appended); assertSame(appended, list.get(1));
        Ticket replacement = new Ticket(TicketType.PORTAL, 31);
        Ticket removed = list.set(0, replacement);
        assertSame(original, removed);
        assertSame(replacement, list.get(0));
        removed.decreaseTicksLeft(); assertEquals(299, ticks(removed));
        storage.addTicket(9L, removed); assertSame(removed, storage.getTickets(9L).get(0));
        assertEquals(299, ticks(storage.getTickets(9L).get(0)));
        historical(removed, 300);
    }
    @Test void invalidListIndexLeavesIncomingTicketUnboundAndListUnchanged() throws Exception {
        TicketStorage storage = new TicketStorage(); Ticket original = new Ticket(TicketType.PORTAL, 33);
        storage.addTicket(7L, original); Ticket incoming = new Ticket(TicketType.PORTAL, 31);
        assertThrows(IndexOutOfBoundsException.class, () -> storage.getTickets(7L).set(1, incoming));
        assertThrows(IndexOutOfBoundsException.class, () -> storage.getTickets(7L).add(2, incoming));
        assertSame(original, storage.getTickets(7L).get(0)); assertEquals(1, storage.getTickets(7L).size());
        historical(incoming, 300);
        try { assertEquals(-1, Ticket.class.getMethod("boundCell").invoke(incoming)); }
        catch (NoSuchMethodException frozenWithoutNativeBinding) {}
    }
    @Test void decodedDeactivatedTicketsKeepTheirCounterThroughActivation() {
        TicketStorage original = new TicketStorage(); Ticket ticket = new Ticket(TicketType.PORTAL, 33);
        ticket.decreaseTicksLeft(); original.addTicket(7L, ticket);
        var encoded = TicketStorage.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        TicketStorage decoded = TicketStorage.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(decoded.getTickets(7L).isEmpty());
        decoded.activateAllDeactivatedTickets();
        Ticket restored = decoded.getTickets(7L).get(0); assertEquals(299, ticks(restored));
        restored.decreaseTicksLeft(); assertEquals(298, ticks(decoded.getTickets(7L).get(0)));
        restored.resetTicksLeft(); assertEquals(300, ticks(restored));
    }
    @Test void constructorAndCounterEffectsKeepTheOriginalTypeQueryCounts() throws Exception {
        TicketType type = org.mockito.Mockito.mock(TicketType.class);
        org.mockito.Mockito.when(type.timeout()).thenReturn(17L);
        org.mockito.Mockito.when(type.hasTimeout()).thenReturn(true);
        org.mockito.Mockito.clearInvocations(type);
        Ticket ticket = new Ticket(type, 33);
        org.mockito.Mockito.verify(type).timeout(); org.mockito.Mockito.verifyNoMoreInteractions(type);
        org.mockito.Mockito.clearInvocations(type);
        ticket.resetTicksLeft(); org.mockito.Mockito.verify(type).timeout(); org.mockito.Mockito.verifyNoMoreInteractions(type);
        org.mockito.Mockito.clearInvocations(type);
        ticket.decreaseTicksLeft(); org.mockito.Mockito.verify(type).hasTimeout(); org.mockito.Mockito.verifyNoMoreInteractions(type);
        org.mockito.Mockito.clearInvocations(type);
        assertFalse(ticket.isTimedOut()); org.mockito.Mockito.verify(type).hasTimeout(); org.mockito.Mockito.verifyNoMoreInteractions(type);
        org.mockito.Mockito.clearInvocations(type);
        var explicit = Ticket.class.getDeclaredConstructor(TicketType.class, int.class, long.class);
        explicit.setAccessible(true); explicit.newInstance(type, 33, -7L);
        org.mockito.Mockito.verifyNoInteractions(type);
    }
}
