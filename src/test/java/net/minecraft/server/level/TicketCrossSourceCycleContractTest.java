package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.*;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketCrossSourceCycleContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Detached(TicketStorage empty,WeakReference<TicketStorage> other,WeakReference<Ticket> ticket){}
 static Detached detach(){
  TicketStorage first=new TicketStorage(),second=new TicketStorage();
  Ticket ticket=new Ticket(TicketType.PORTAL,33){@Override public TicketType getType(){Reference.reachabilityFence(second);return super.getType();}};
  first.addTicket(7L,ticket);second.addTicket(9L,ticket);first.removeTicket(7L,new Ticket(TicketType.PORTAL,33));
  return new Detached(first,new WeakReference<>(second),new WeakReference<>(ticket));
 }
 @Test void emptyLiveSourceDoesNotAnchorAnUnreachableOtherSource() throws Exception {
  Detached detached=detach();for(int i=0;i<160;i++){System.gc();Thread.sleep(20);if(detached.other().get()==null && detached.ticket().get()==null)break;}
  assertTrue(detached.empty().getTickets(7L).isEmpty());
  assertNull(detached.other().get(),"empty source retained another source through a shared Ticket");assertNull(detached.ticket().get());Reference.reachabilityFence(detached.empty());
 }
}
