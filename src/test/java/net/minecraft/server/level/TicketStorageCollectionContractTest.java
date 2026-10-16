package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class TicketStorageCollectionContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Abandoned(WeakReference<TicketStorage> storage,WeakReference<Ticket> ticket){}
 static Abandoned abandon(){TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,ticket);return new Abandoned(new WeakReference<>(storage),new WeakReference<>(ticket));}
 @Test void abandonedStoreReleasesItsTicketWithoutAnotherStorageCall() throws Exception {
  Abandoned abandoned=abandon();
  for(int i=0;i<160;i++){System.gc();Thread.sleep(20);if(abandoned.storage().get()==null && abandoned.ticket().get()==null)break;}
  assertNull(abandoned.storage().get(),"store wrapper retained");
  assertNull(abandoned.ticket().get(),"native store cleanup left a required Java unpin pending indefinitely");
 }
 static Abandoned abandonCycle(){
  TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33){
   @Override public TicketType getType(){java.lang.ref.Reference.reachabilityFence(storage);return super.getType();}
  };
  storage.addTicket(7L,ticket);return new Abandoned(new WeakReference<>(storage),new WeakReference<>(ticket));
 }
 @Test void ticketCapturingItsStoreDoesNotCreateAnExternalRoot() throws Exception {
  Abandoned abandoned=abandonCycle();for(int i=0;i<160;i++){System.gc();Thread.sleep(20);if(abandoned.storage().get()==null && abandoned.ticket().get()==null)break;}
  assertNull(abandoned.storage().get(),"global Ticket binding anchored its captured storage");
  assertNull(abandoned.ticket().get(),"global Ticket binding anchored an unreachable source cycle");
 }

 record Retained(java.util.List<Ticket> list,WeakReference<TicketStorage> facade,WeakReference<Ticket> other){}
 static Retained retainOneList(){TicketStorage storage=new TicketStorage();Ticket first=new Ticket(TicketType.PORTAL,33),other=new Ticket(TicketType.PORTAL,31);storage.addTicket(7L,first);storage.addTicket(19L,other);return new Retained(storage.getTickets(7L),new WeakReference<>(storage),new WeakReference<>(other));}
 static void collectUnrelated(Retained retained)throws Exception{
  for(int i=0;i<160&&(retained.facade().get()!=null||retained.other().get()!=null);i++){System.gc();Thread.sleep(10);}
  assertNull(retained.facade().get(),"storage facade was not collected");assertNull(retained.other().get(),"retained list rooted a different lists Ticket after its facade died");java.lang.ref.Reference.reachabilityFence(retained.list());
 }
 @Test void retainingOneListDoesNotRootOtherListsAfterTheFacadeIsCollected()throws Exception{
  Retained retained=retainOneList();collectUnrelated(retained);assertEquals(33,retained.list().getFirst().getTicketLevel());java.lang.ref.Reference.reachabilityFence(retained.list());
 }
 @Test void retainedListRemainsMutableAfterTheFacadeAndOtherListAreCollected()throws Exception{
  Retained retained=retainOneList();collectUnrelated(retained);java.util.List<Ticket> list=retained.list();Ticket added=new Ticket(TicketType.PORTAL,27),replacement=new Ticket(TicketType.PORTAL,11),original=list.getFirst();
  list.add(added);assertSame(original,list.set(0,replacement));assertSame(added,list.remove(1));assertSame(replacement,list.getFirst());list.clear();assertTrue(list.isEmpty());list.add(added);assertSame(added,list.getFirst());java.lang.ref.Reference.reachabilityFence(list);
 }
 @Test void removedListDoesNotRebindWhenTheSameKeyGetsANewList(){
  TicketStorage storage=new TicketStorage();Ticket first=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,first);java.util.List<Ticket> old=storage.getTickets(7L);assertTrue(storage.removeTicket(7L,first));
  Ticket replacement=new Ticket(TicketType.PORTAL,31);storage.addTicket(7L,replacement);java.util.List<Ticket> current=storage.getTickets(7L);Ticket detached=new Ticket(TicketType.UNKNOWN,5);old.add(detached);
  assertNotSame(old,current);assertSame(current,storage.getTickets(7L));assertSame(detached,old.getFirst());assertSame(replacement,current.getFirst());old.clear();assertSame(replacement,current.getFirst());
 }
}
