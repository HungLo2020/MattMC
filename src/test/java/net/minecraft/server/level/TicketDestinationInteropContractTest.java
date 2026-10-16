package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketDestinationInteropContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void deactivationCallsTheActualRemovalOverrideWithItsDestination(){
  class Storage extends TicketStorage {
   int calls;boolean destination;
   @Override public void removeTicketIf(TicketPredicate predicate,Long2ObjectOpenHashMap<List<Ticket>> target){calls++;destination=target!=null;}
  }
  Storage storage=new Storage();storage.addTicket(7L,new Ticket(TicketType.PORTAL,33));storage.deactivateTicketsOnClosing();
  assertEquals(1,storage.calls);assertTrue(storage.destination);assertEquals(1,storage.getTickets(7L).size());
 }
 @Test void destinationMappingAllocatesFromTheLiveSourceListSize(){
  TicketStorage storage=new TicketStorage();Ticket first=new Ticket(TicketType.PORTAL,33);
  storage.addTicket(7L,first);for(int i=1;i<5;i++)storage.addTicket(7L,new Ticket(TicketType.PORTAL,33+i));
  class Destination extends Long2ObjectOpenHashMap<List<Ticket>> {
   int capacity=-1;
   @Override public List<Ticket> computeIfAbsent(long key,Long2ObjectFunction<? extends List<Ticket>> mapping){
    List<Ticket> list=mapping.get(key);capacity=((ObjectArrayList<?>)list).elements().length;return list;
   }
  }
  Destination destination=new Destination();storage.removeTicketIf((ticket,key)->ticket==first,destination);assertEquals(5,destination.capacity);
 }
 @Test void capturedDestinationIsTheActualMutableSourceForActivation(){
  class Storage extends TicketStorage {
   Long2ObjectOpenHashMap<List<Ticket>> captured;
   @Override public void removeTicketIf(TicketPredicate predicate,Long2ObjectOpenHashMap<List<Ticket>> target){captured=target;super.removeTicketIf(predicate,target);}
  }
  Storage storage=new Storage();Ticket stored=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,stored);storage.deactivateTicketsOnClosing();
  assertNotNull(storage.captured);assertEquals(Long2ObjectOpenHashMap.class,storage.captured.getClass());assertSame(stored,storage.captured.get(7L).getFirst());
  Ticket inserted=new Ticket(TicketType.FORCED,31);storage.captured.put(9L,new ObjectArrayList<>(List.of(inserted)));
  storage.activateAllDeactivatedTickets();assertSame(stored,storage.getTickets(7L).getFirst());assertSame(inserted,storage.getTickets(9L).getFirst());assertTrue(storage.captured.isEmpty());
 }
}
