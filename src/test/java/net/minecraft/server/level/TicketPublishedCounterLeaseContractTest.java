package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.foreign.*;
import java.lang.ref.Reference;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketPublishedCounterLeaseContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void bindingReentryMayRemoveTheEntryWithoutRetiringTheLiveTicketsResource(){
  TicketStorage storage=new TicketStorage();
  class Stored extends Ticket {
   long address;Stored(){super(TicketType.PORTAL,33);}
   @Override public void bindCell(int cell,long address){super.bindCell(cell,address);this.address=address;storage.getTickets(7L).clear();}
  }
  Stored ticket=new Stored();assertTrue(storage.addTicket(7L,ticket));assertTrue(storage.getTickets(7L).isEmpty());
  MemorySegment cell=MemorySegment.ofAddress(ticket.address).reinterpret(48).asReadOnly();
  assertEquals(1,cell.get(ValueLayout.JAVA_BYTE,37),"entry removal during binding retired the live Tickets counter resource");
  assertEquals(1,cell.get(ValueLayout.JAVA_INT,32));ticket.decreaseTicksLeft();assertFalse(ticket.isTimedOut());Reference.reachabilityFence(ticket);
 }
}
