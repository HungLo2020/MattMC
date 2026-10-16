package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.TicketStorage;
import org.junit.jupiter.api.*;

@org.junit.jupiter.api.parallel.Isolated
class NativeTicketStoreLifetimeContractTest {
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 record Lease(WeakReference<TicketStorage> storage,WeakReference<Ticket> ticket,Object[] owner,long address){}
 static Lease abandon(boolean retainGraph){
  TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,ticket);
  Object owner=null;
  if(retainGraph){
   NativeChunkHolders holders=new NativeChunkHolders(ChunkLevel.MAX_LEVEL);
   ChunkMap map=org.mockito.Mockito.mock(ChunkMap.class);
   org.mockito.Mockito.when(map.generationRegistryOwner()).thenReturn(holders.requestOwner());
   NativeChunkGeneration.RequestGraph graph=new NativeChunkGeneration.RequestGraph(map,storage);owner=graph.resource;
  }
  return new Lease(new WeakReference<>(storage),new WeakReference<>(ticket),new Object[]{owner},Ticket.prepareNativeCounter(ticket,ticket.unboundTicksLeft()).address());
 }
 static void awaitCollection(WeakReference<?> target)throws Exception {
  for(int i=0;i<160;i++){System.gc();Thread.sleep(20);if(target.get()==null)return;}
 }
 static void awaitNativeRetirement(long address)throws Exception { awaitNativeRetirement(address,"final native source release left its counter retirement pending"); }
 static void awaitNativeRetirement(long address,String message)throws Exception {
  MemorySegment cell=MemorySegment.ofAddress(address).reinterpret(48).asReadOnly();
  for(int i=0;i<160;i++){System.gc();Thread.sleep(20);if(cell.get(java.lang.foreign.ValueLayout.JAVA_BYTE,37)==0)return;}
  assertEquals(0,cell.get(java.lang.foreign.ValueLayout.JAVA_BYTE,37),message);
 }
 @Test void storeCleanupUnpinsTicketsWithoutAnotherStorageOperation()throws Exception {
  Lease lease=abandon(false);awaitCollection(lease.storage());assertNull(lease.storage().get());
  awaitCollection(lease.ticket());assertNull(lease.ticket().get(),"collected store left its Ticket strongly rooted");awaitNativeRetirement(lease.address());
 }
 @Test void retainedGenerationResourceKeepsTicketsUntilItsLastNativeStoreRelease()throws Exception {
  Lease lease=abandon(true);
  for(int i=0;i<10;i++){System.gc();Thread.sleep(20);}
  assertNotNull(lease.storage().get(),"complete generation resource lost its actual VM source");
  assertNotNull(lease.ticket().get(),"native generation resource lost its actual Ticket root");
  Reference.reachabilityFence(lease.owner()[0]);lease.owner()[0]=null;
  awaitCollection(lease.ticket());assertNull(lease.ticket().get(),"last generation resource left a Ticket unpin pending");awaitNativeRetirement(lease.address());
 }

 record ListLease(WeakReference<TicketStorage> facade,WeakReference<Ticket> ticket,Object[] view,long listAddress,long cellAddress){}
 static ListLease retainList()throws Exception{
  TicketStorage storage=new TicketStorage();Ticket ticket=new Ticket(TicketType.PORTAL,33);storage.addTicket(7L,ticket);java.util.List<Ticket> view=storage.getTickets(7L);
  var rootsField=view.getClass().getDeclaredField("roots");rootsField.setAccessible(true);Object roots=rootsField.get(view);var ownerField=roots.getClass().getDeclaredField("owner");ownerField.setAccessible(true);MemorySegment owner=(MemorySegment)ownerField.get(roots);
  return new ListLease(new WeakReference<>(storage),new WeakReference<>(ticket),new Object[]{view},owner.address(),Ticket.prepareNativeCounter(ticket,ticket.unboundTicksLeft()).address());
 }
 @Test void retainedListLastOwnerCompletesCounterRetirementAfterMapCollection()throws Exception{
  ListLease lease=retainList();awaitCollection(lease.facade());assertNull(lease.facade().get());
  MemorySegment header=MemorySegment.ofAddress(lease.listAddress()).reinterpret(16).asReadOnly();
  var references=java.lang.foreign.ValueLayout.JAVA_LONG.varHandle();
  for(int i=0;i<160&&(long)references.getVolatile(header,0L)!=1L;i++){System.gc();Thread.sleep(20);}
  assertEquals(1L,(long)references.getVolatile(header,0L),"native map still owns the retained list");
  assertNotNull(lease.ticket().get());Reference.reachabilityFence(lease.view()[0]);lease.view()[0]=null;
  awaitCollection(lease.ticket());assertNull(lease.ticket().get());awaitNativeRetirement(lease.cellAddress(),"final retained-list resource left its counter retirement pending");
 }
}
