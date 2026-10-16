package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;

class LiveTicketConsumerContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 static class Mutable extends Ticket {
  TicketType type; int level=33, queries;
  Mutable(TicketType type){super(type,33);this.type=type;}
  @Override public TicketType getType(){queries++;return type;}
  @Override public int getTicketLevel(){return level;}
 }
 @Test void removalMatchesTheActualStoredTypeAndLevel(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.PORTAL);storage.addTicket(7L,stored);stored.type=TicketType.FORCED;stored.level=31;
  assertTrue(storage.removeTicket(7L,new Ticket(TicketType.FORCED,31)));assertTrue(storage.getTickets(7L).isEmpty());
 }
 @Test void keepActiveQueriesTheActualType(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.PLAYER_LOADING);storage.addTicket(7L,stored);stored.type=TicketType.FORCED;
  assertTrue(storage.shouldKeepDimensionActive());
 }
 @Test void persistedFilterQueriesTheActualType(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.PLAYER_LOADING);storage.addTicket(7L,stored);stored.type=TicketType.FORCED;
  var packed=TicketStorage.CODEC.encodeStart(JsonOps.INSTANCE,storage).getOrThrow().getAsJsonObject();
  assertEquals(1,packed.getAsJsonArray("tickets").size());
 }
 @Test void replacementMatchesTheActualType(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.PORTAL);storage.addTicket(7L,stored);stored.type=TicketType.FORCED;
  storage.replaceTicketLevelOfType(30,TicketType.FORCED);assertEquals(30,storage.getTicketLevelAt(7L,false));
 }
 @Test void removalPredicateRetainsTheActualPostRemovalGetterEffects(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.PLAYER_LOADING);storage.addTicket(7L,stored);stored.queries=0;
  storage.removeTicketIf((ticket,key)->true,null);assertEquals(3,stored.queries);
 }
 @Test void deactivationQueriesTheActualType(){
  TicketStorage storage=new TicketStorage();Mutable stored=new Mutable(TicketType.UNKNOWN);storage.addTicket(7L,stored);stored.type=TicketType.FORCED;
  storage.deactivateTicketsOnClosing();assertTrue(storage.getTickets(7L).isEmpty());
 }
 @Test void nullAdmissionKeepsThePublishedListPrefix(){
  TicketStorage storage=new TicketStorage();assertThrows(NullPointerException.class,()->storage.addTicket(7L,null));
  assertEquals(1,storage.getTickets(7L).size());assertNull(storage.getTickets(7L).getFirst());
 }
 @Test void postRemovalGetterFailurePreservesIdentityAndUnretiredMapEntry(){
  IllegalStateException marker=new IllegalStateException("post-remove");
  class Stored extends Ticket {boolean armed;Stored(){super(TicketType.PLAYER_LOADING,33);}@Override public TicketType getType(){if(armed)throw marker;return super.getType();}}
  TicketStorage storage=new TicketStorage();Stored stored=new Stored();storage.addTicket(7L,stored);
  assertSame(marker,assertThrows(IllegalStateException.class,()->storage.removeTicketIf((ticket,key)->{stored.armed=true;return true;},null)));
  assertTrue(storage.hasTickets());assertTrue(storage.getTickets(7L).isEmpty());
 }
 @Test void dirtyFailureOccursBeforeEmptyMapEntryRetirement(){
  IllegalStateException marker=new IllegalStateException("dirty");
  class Storage extends TicketStorage {boolean armed;@Override public void setDirty(){if(armed)throw marker;super.setDirty();}}
  Storage storage=new Storage();storage.addTicket(7L,new Ticket(TicketType.PLAYER_LOADING,33));storage.armed=true;
  assertSame(marker,assertThrows(IllegalStateException.class,()->storage.removeTicketIf((ticket,key)->true,null)));
  assertTrue(storage.hasTickets());assertTrue(storage.getTickets(7L).isEmpty());
 }
 @Test void removalListenerReceiverPrecedesTheLiveMinimumGetter(){
  java.util.ArrayList<String> effects=new java.util.ArrayList<>();TicketStorage storage=new TicketStorage();
  class Stored extends Ticket {boolean armed;Stored(){super(TicketType.PLAYER_LOADING,34);}@Override public int getTicketLevel(){if(armed){armed=false;storage.setLoadingChunkUpdatedListener((key,level,decrease)->effects.add("replacement"));}return super.getTicketLevel();}}
  Stored remaining=new Stored();storage.addTicket(7L,new Ticket(TicketType.PLAYER_LOADING,33));storage.addTicket(7L,remaining);
  storage.setLoadingChunkUpdatedListener((key,level,decrease)->effects.add("original:"+level));remaining.armed=true;
  storage.removeTicketIf((ticket,key)->ticket!=remaining,null);assertEquals(List.of("original:34"),effects);
 }
 @Test void rehashedNullEntryStillDeliversAbsentLevelBeforeDirtyFailure(){
  TicketStorage storage=new TicketStorage();Ticket source=new Ticket(TicketType.FORCED,33);storage.addTicket(5L,source);
  java.util.ArrayList<Integer> levels=new java.util.ArrayList<>();
  storage.setLoadingChunkUpdatedListener((key,level,decrease)->{if(!decrease)levels.add(level);});
  assertThrows(NullPointerException.class,()->storage.removeTicketIf((ticket,key)->{
   if(ticket!=source)return false;
   for(long added=100;added<=130;added++)storage.addTicket(added,new Ticket(TicketType.FORCED,33));
   return true;
  },null));
  assertEquals(List.of(ChunkLevel.MAX_LEVEL+1),levels);
 } static TicketStorage reentry(){
  TicketStorage storage=new TicketStorage();List<Ticket>[] retained=new List[1];Ticket next=new Ticket(TicketType.FORCED,34);
  class First extends Ticket {boolean armed;First(){super(TicketType.PLAYER_LOADING,33);}@Override public TicketType getType(){
   if(armed){armed=false;storage.removeTicketIf((ticket,key)->true,null);retained[0].add(this);retained[0].add(next);}
   return super.getType();
  }}
  First first=new First();storage.addTicket(7L,first);storage.addTicket(7L,next);retained[0]=storage.getTickets(7L);first.armed=true;return storage;
 }
 @Test void keepActiveContinuesItsCapturedListAfterTypeQueryUnmapsAndRepairsIt(){assertTrue(reentry().shouldKeepDimensionActive(),"captured detached list lost its later keep-active Ticket");}
 @Test void persistenceContinuesItsCapturedListAfterTypeQueryUnmapsAndRepairsIt(){
  var value=TicketStorage.CODEC.encodeStart(JsonOps.INSTANCE,reentry()).getOrThrow().getAsJsonObject();
  assertNotNull(value.getAsJsonArray("tickets"),"captured detached list lost its later persisted Ticket");
  assertEquals(1,value.getAsJsonArray("tickets").size(),"captured detached list lost its later persisted Ticket");
 } static TicketStorage gcReentry(){
  TicketStorage storage=new TicketStorage();List<Ticket>[] retained=new List[1];Ticket[] tickets=new Ticket[3];
  java.lang.ref.WeakReference<?>[] witnesses=new java.lang.ref.WeakReference<?>[2];
  java.util.concurrent.atomic.AtomicBoolean middleArmed=new java.util.concurrent.atomic.AtomicBoolean();
  class First extends Ticket {boolean armed;First(){super(TicketType.PLAYER_LOADING,33);}@Override public TicketType getType(){
   if(armed){armed=false;storage.removeTicketIf((ticket,key)->true,null);retained[0].addAll(java.util.Arrays.asList(tickets));middleArmed.set(true);}
   return super.getType();
  }}
  class Middle extends Ticket {Middle(){super(TicketType.PLAYER_LOADING,34);}@Override public TicketType getType(){
   if(middleArmed.getAndSet(false)){for(int n=0;n<20;n++){System.gc();try{Thread.sleep(10);}catch(InterruptedException e){throw new AssertionError(e);}}
    assertNotNull(witnesses[0].get(),"consumer lost its captured list VM Ticket roots");
    assertNotNull(witnesses[1].get(),"consumer lost its captured list VM Ticket roots");
   }return super.getType();
  }}
  First first=new First();Middle middle=new Middle();tickets[0]=first;tickets[1]=middle;tickets[2]=new Ticket(TicketType.FORCED,35);
  for(Ticket ticket:tickets)storage.addTicket(7L,ticket);
  retained[0]=storage.getTickets(7L);witnesses[0]=new java.lang.ref.WeakReference<>(first);witnesses[1]=new java.lang.ref.WeakReference<>(tickets[2]);
  first.armed=true;return storage;
 }
 @Test void detachedConsumerRetainsActualVmTicketRootsAcrossLaterGetterGc(){assertTrue(gcReentry().shouldKeepDimensionActive(),"consumer failed to continue its captured list after GC");}

}
