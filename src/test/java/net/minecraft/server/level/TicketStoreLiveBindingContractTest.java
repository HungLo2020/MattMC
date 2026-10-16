package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketStoreLiveBindingContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Live(TicketStorage storage,WeakReference<Ticket> ticket){}
 static Live live(){TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,ticket);return new Live(storage,new WeakReference<>(ticket));}
 @Test void liveStoreKeepsItsActualTicketAfterCallerDropsIt() throws Exception {
  Live live=live();for(int i=0;i<40;i++){System.gc();Thread.sleep(20);}
  assertNotNull(live.ticket().get(),"live native store lost its actual Ticket resource");assertSame(live.ticket().get(),live.storage().getTickets(7L).getFirst());
 }
}
