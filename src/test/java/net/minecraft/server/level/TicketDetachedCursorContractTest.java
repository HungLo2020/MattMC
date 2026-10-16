package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketDetachedCursorContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static final long KEY=17L;
 static final class Reset extends Ticket {int resets;Reset(){super(TicketType.UNKNOWN,5);}@Override public void resetTicksLeft(){resets++;super.resetTicksLeft();}}
 static final class Setup {
  final TicketStorage storage=new TicketStorage();final Reset match=new Reset();boolean armed;
  final Ticket first=new Ticket(TicketType.PORTAL,33){@Override public TicketType getType(){if(armed){armed=false;storage.removeTicketIf((ticket,key)->true,null);retained.add(new Ticket(TicketType.PORTAL,19));retained.add(match);}return super.getType();}};
  final List<Ticket> retained;
  Setup(){storage.addTicket(KEY,first);retained=storage.getTickets(KEY);armed=true;}
 }
 @Test void minimumContinuesAtItsPositionInTheActualDetachedBacking(){Setup s=new Setup();assertEquals(s.match.toString(),s.storage.getTicketDebugString(KEY,false));assertTrue(s.storage.getTickets(KEY).isEmpty());}
 @Test void admissionMatchesTheLaterTicketInTheActualDetachedBacking(){Setup s=new Setup();assertFalse(s.storage.addTicket(KEY,new Ticket(TicketType.UNKNOWN,5)));assertEquals(1,s.match.resets);assertTrue(s.storage.getTickets(KEY).isEmpty());}
 @Test void removalRemovesTheLaterTicketFromTheActualDetachedBacking(){Setup s=new Setup();assertTrue(s.storage.removeTicket(KEY,new Ticket(TicketType.UNKNOWN,5)));assertEquals(1,s.retained.size());assertEquals(TicketType.PORTAL,s.retained.getFirst().getType());assertTrue(s.storage.getTickets(KEY).isEmpty());} @Test void outerPredicateContinuesTheCapturedListAfterNestedRemovalAndRepopulation(){
  TicketStorage storage=new TicketStorage();Ticket first=new Ticket(TicketType.PORTAL,33),second=new Ticket(TicketType.FORCED,34);
  storage.addTicket(7L,first);storage.addTicket(7L,second);List<Ticket> retained=storage.getTickets(7L);List<Ticket> observed=new java.util.ArrayList<>();
  storage.removeTicketIf((ticket,key)->{
   observed.add(ticket);
   if(ticket==first){storage.removeTicketIf((other,position)->true,null);retained.add(first);retained.add(second);}
   return false;
  },null);
  assertEquals(List.of(first,second),observed,"bulk predicate lost the later Ticket in its captured detached list");
  assertTrue(storage.getTickets(7L).isEmpty());assertEquals(List.of(first,second),retained);
 }
 @Test void bulkWalkRetainsActualVmTicketsThroughALaterGetterGc(){
  TicketStorage storage=LiveTicketConsumerContractTest.gcReentry();int[] visited={0};
  storage.removeTicketIf((ticket,key)->{ticket.getType();visited[0]++;return false;},null);
  assertEquals(3,visited[0],"bulk predicate lost its captured list during getter GC");
 }
 @Test void detachedIteratorRemovalKeepsPostRemovalGetterAndDirtyPrefixBeforeLiveEntryFault(){
  List<String> effects=new java.util.ArrayList<>();boolean[] trace={false};
  TicketStorage storage=new TicketStorage(){@Override public void setDirty(){if(trace[0])effects.add("dirty");super.setDirty();}};
  Ticket first=new Ticket(TicketType.PORTAL,33){@Override public TicketType getType(){if(trace[0])effects.add("type");return super.getType();}};
  Ticket second=new Ticket(TicketType.FORCED,34);storage.addTicket(7L,first);storage.addTicket(7L,second);List<Ticket> retained=storage.getTickets(7L);boolean[] changed={false};
  assertThrows(NullPointerException.class,()->storage.removeTicketIf((ticket,key)->{
   if(ticket==first&&!changed[0]){changed[0]=true;storage.removeTicketIf((other,position)->true,null);retained.add(first);retained.add(second);trace[0]=true;}return ticket==first;
  },null));
  trace[0]=false;assertEquals(List.of("type","type","type","dirty"),effects);assertEquals(List.of(second),retained);assertTrue(storage.getTickets(7L).isEmpty());
 }
}
