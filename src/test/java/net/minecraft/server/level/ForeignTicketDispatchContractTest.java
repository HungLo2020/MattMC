package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;
class ForeignTicketDispatchContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void duplicateCallsTheStoredTicketsActualResetOverride(){
  class Stored extends Ticket {int resets;Stored(){super(TicketType.PORTAL,33);}@Override public void resetTicksLeft(){resets++;super.resetTicksLeft();}}
  TicketStorage storage=new TicketStorage();Stored stored=new Stored();storage.addTicket(7L,stored);stored.decreaseTicksLeft();
  assertFalse(storage.addTicket(7L,new Ticket(TicketType.PORTAL,33)));
  assertEquals(1,stored.resets,"duplicate admission bypassed stored Ticket.resetTicksLeft override");
 }
 @Test void purgeCallsActualDecreaseAndTimeoutOverrides(){
  class Stored extends Ticket {int decreases,timeouts;Stored(){super(TicketType.PORTAL,33);}@Override public void decreaseTicksLeft(){decreases++;super.decreaseTicksLeft();}@Override public boolean isTimedOut(){timeouts++;return super.isTimedOut();}}
  TicketStorage storage=new TicketStorage();Stored stored=new Stored();storage.addTicket(7L,stored);
  storage.purgeStaleTickets(org.mockito.Mockito.mock(ChunkMap.class));
  assertEquals(1,stored.decreases,"purge bypassed actual Ticket.decreaseTicksLeft override");
  assertEquals(1,stored.timeouts,"purge bypassed actual Ticket.isTimedOut override");
 }
 @Test void newAdmissionRetainsGetterEffectOrder(){
  class Stored extends Ticket {final List<String> calls=new ArrayList<>();Stored(){super(TicketType.PLAYER_LOADING,33);}@Override public TicketType getType(){calls.add("T");return super.getType();}@Override public int getTicketLevel(){calls.add("L");return super.getTicketLevel();}}
  TicketStorage storage=new TicketStorage();Stored stored=new Stored();assertTrue(storage.addTicket(7L,stored));
  assertEquals("TTLT",String.join("",stored.calls),"new admission reordered actual Ticket getters");
 }
 @Test void distancePropagationUsesTheLiveTicketGetterEffects(){
  class Stored extends Ticket {int reads;Stored(){super(TicketType.FORCED,31);}@Override public int getTicketLevel(){reads++;return super.getTicketLevel();}}
  TicketStorage storage=new TicketStorage();SimulationChunkTracker tracker=new SimulationChunkTracker(storage);Stored stored=new Stored();
  storage.addTicket(7L,stored);tracker.runAllUpdates();
  tracker.update(7L,33,false);stored.reads=0;tracker.runAllUpdates();
  assertTrue(stored.reads>0,"distance propagation bypassed live Ticket.getTicketLevel effects");
 }
 @Test void minimumQueryKeepsValueAndActualGetterOrder(){
  List<String> calls=new ArrayList<>();
  class Stored extends Ticket {final String name;Stored(String name,TicketType type,int level){super(type,level);this.name=name;}@Override public TicketType getType(){calls.add("T"+name);return super.getType();}@Override public int getTicketLevel(){calls.add("L"+name);return super.getTicketLevel();}}
  TicketStorage storage=new TicketStorage();storage.addTicket(7L,new Stored("1",TicketType.PORTAL,33));storage.addTicket(7L,new Stored("2",TicketType.FORCED,31));calls.clear();
  assertEquals(31,storage.getTicketLevelAt(7L,true));
  assertEquals(List.of("T1","L2","L1","T2","L2"),calls,"minimum query bypassed or reordered actual Ticket getters");
 }
 @Test void minimumQueryRereadsBestAfterCurrentGetterChangesIt(){
  class Stored extends Ticket {int level;Runnable change;Stored(int level){super(TicketType.PORTAL,level);this.level=level;}@Override public int getTicketLevel(){if(change!=null)change.run();return level;}}
  TicketStorage storage=new TicketStorage();Stored first=new Stored(33),second=new Stored(31);storage.addTicket(7L,first);storage.addTicket(7L,second);
  second.change=()->first.level=30;
  assertEquals(30,storage.getTicketLevelAt(7L,true),"minimum query used a stale best level across actual getter effects");
 }
 @Test void duplicateResetFailureKeepsActualIdentityAndStopsBeforeDirty() {
  IllegalStateException marker=new IllegalStateException("reset");
  class Storage extends TicketStorage {int dirties;@Override public void setDirty(){dirties++;super.setDirty();}}
  class Stored extends Ticket {Stored(){super(TicketType.PORTAL,33);}@Override public void resetTicksLeft(){throw marker;}}
  Storage storage=new Storage();Stored stored=new Stored();storage.addTicket(7L,stored);storage.dirties=0;
  assertSame(marker,assertThrows(IllegalStateException.class,()->storage.addTicket(7L,new Ticket(TicketType.PORTAL,33))));
  assertEquals(0,storage.dirties);assertSame(stored,storage.getTickets(7L).getFirst());
 }
 @Test void duplicateUsesLiveTypeAndLevelInsteadOfConstructionMetadata() {
  class Stored extends Ticket {TicketType type=TicketType.PORTAL;int level=33,resets;Stored(){super(TicketType.PORTAL,33);}@Override public TicketType getType(){return type;}@Override public int getTicketLevel(){return level;}@Override public void resetTicksLeft(){resets++;}}
  TicketStorage storage=new TicketStorage();Stored stored=new Stored();storage.addTicket(7L,stored);stored.type=TicketType.FORCED;stored.level=31;
  assertFalse(storage.addTicket(7L,new Ticket(TicketType.FORCED,31)));assertEquals(1,stored.resets);assertEquals(1,storage.getTickets(7L).size());
 }
 @Test void notificationReceiverIsCapturedBeforeItsLevelGetterChangesListener() {
  List<String> events=new ArrayList<>();TicketStorage storage=new TicketStorage();
  class Stored extends Ticket {int reads;Stored(){super(TicketType.PLAYER_LOADING,33);}@Override public int getTicketLevel(){if(++reads==2)storage.setLoadingChunkUpdatedListener((key,level,decrease)->events.add("replacement"));return super.getTicketLevel();}}
  storage.setLoadingChunkUpdatedListener((key,level,decrease)->events.add("original"));
  storage.addTicket(7L,new Stored());assertEquals(List.of("original"),events);
 }
}
