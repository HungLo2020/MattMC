package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.*;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketScopedRootContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Live(TicketStorage storage,WeakReference<Ticket> ticket){}
 static Live remainingSource(){
  TicketStorage first=new TicketStorage(),second=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);
  first.addTicket(7L,ticket);second.addTicket(9L,ticket);first.removeTicket(7L,new Ticket(TicketType.PORTAL,33));
  return new Live(second,new WeakReference<>(ticket));
 }
 @Test void oneSourcesLastUnpinDoesNotEraseAnotherLiveSourcesResource() throws Exception {
  Live live=remainingSource();for(int i=0;i<40;i++){System.gc();Thread.sleep(20);}
  assertNotNull(live.ticket().get(),"source-specific unpin erased another live source");
  assertSame(live.ticket().get(),live.storage().getTickets(9L).getFirst());Reference.reachabilityFence(live.storage());
 }
}
