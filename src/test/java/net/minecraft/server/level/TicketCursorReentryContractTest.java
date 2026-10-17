package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketCursorReentryContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void nestedAdmissionDuringResetKeepsTheOuterThrowingPrefixAndCanRetry(){
  List<String> effects=new ArrayList<>();IllegalStateException marker=new IllegalStateException("outer-reset");
  class Storage extends TicketStorage {@Override public void setDirty(){effects.add("dirty");super.setDirty();}}
  Storage storage=new Storage();
  class Stored extends Ticket {boolean armed;Stored(){super(TicketType.PORTAL,33);}@Override public void resetTicksLeft(){if(armed){armed=false;effects.add("reset");storage.addTicket(19L,new Ticket(TicketType.UNKNOWN,5));throw marker;}super.resetTicksLeft();}}
  Stored stored=new Stored();storage.addTicket(17L,stored);effects.clear();stored.armed=true;
  assertSame(marker,assertThrows(IllegalStateException.class,()->storage.addTicket(17L,new Ticket(TicketType.PORTAL,33))));
  assertEquals(List.of("reset","dirty"),effects);assertEquals(0,marker.getSuppressed().length);assertSame(stored,storage.getTickets(17L).getFirst());assertEquals(1,storage.getTickets(19L).size());
  assertFalse(storage.addTicket(17L,new Ticket(TicketType.PORTAL,33)));assertEquals(List.of("reset","dirty","dirty"),effects);
 }
 @Test void dirtyCallbackMayNestAnotherAdmissionWithoutRebindingTheOuterTicket(){
  class Storage extends TicketStorage {boolean armed;int dirties;@Override public void setDirty(){dirties++;if(armed){armed=false;this.addTicket(19L,new Ticket(TicketType.UNKNOWN,5));}super.setDirty();}}
  Storage storage=new Storage();Ticket original=new Ticket(TicketType.PORTAL,33);storage.addTicket(17L,original);storage.dirties=0;storage.armed=true;
  assertFalse(storage.addTicket(17L,new Ticket(TicketType.PORTAL,33)));assertEquals(2,storage.dirties);assertSame(original,storage.getTickets(17L).getFirst());assertEquals(1,storage.getTickets(19L).size());
 }
}
