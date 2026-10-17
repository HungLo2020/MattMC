package net.minecraft.server.level;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.*;
import net.minecraft.world.level.*;
import org.junit.jupiter.api.*;

class ChunkSyncDependencyContractTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    static class Holder extends ChunkHolder {
        Holder(){super(new ChunkPos(4,-7),33,LevelHeightAccessor.create(0,16),null,(p,q,l,set)->set.accept(l),null);}
        void save(CompletableFuture<?> value){addSaveDependency(value);}
    }
    record Route(Consumer<CompletableFuture<?>> add,Supplier<CompletableFuture<?>> get){}
    static Route route(boolean save) {Holder holder=new Holder();return new Route(save?holder::save:holder::addSendDependency,save?holder::getSaveSyncFuture:holder::getSendSyncFuture);}
    @Test void directDependencyIdentityAndRecoveryBeforePairCompletionArePreserved(){
        for(boolean save:new boolean[]{false,true}){
            Route route=route(save);CompletableFuture<Void> first=new CompletableFuture<>(),second=new CompletableFuture<>();
            route.add.accept(first);assertSame(first,route.get.get());route.add.accept(second);CompletableFuture<?> combined=route.get.get();assertNotSame(first,combined);assertNotSame(second,combined);
            first.completeExceptionally(new RuntimeException("temporary"));assertFalse(combined.isDone());first.obtrudeValue(null);second.complete(null);assertNull(combined.join());
        }
    }
    @Test void aPublishedInnerFailureSurvivesLeafRecoveryAndALaterDependency(){
        for(boolean save:new boolean[]{false,true}){
            Route route=route(save);CompletableFuture<Void> first=new CompletableFuture<>(),second=new CompletableFuture<>(),third=new CompletableFuture<>();
            route.add.accept(first);route.add.accept(second);CompletableFuture<?> inner=route.get.get();route.add.accept(third);CompletableFuture<?> outer=route.get.get();
            RuntimeException failure=new RuntimeException("published inner");first.completeExceptionally(failure);second.complete(null);assertSame(failure,assertThrows(CompletionException.class,inner::join).getCause());
            first.obtrudeValue(null);assertFalse(outer.isDone());third.complete(null);assertSame(failure,assertThrows(CompletionException.class,outer::join).getCause());
        }
    }
    @Test void customCompletionStageConversionIsCalledOnceAndItsActualFutureIsUsed(){
        for(boolean save:new boolean[]{false,true}){
            Route route=route(save);CompletableFuture<Void> first=new CompletableFuture<>(),actual=new CompletableFuture<>();int[] calls={0};
            CompletableFuture<Void> supplied=new CompletableFuture<>(){@Override public CompletableFuture<Void> toCompletableFuture(){calls[0]++;return actual;}};
            route.add.accept(first);route.add.accept(supplied);CompletableFuture<?> combined=route.get.get();assertEquals(1,calls[0]);first.complete(null);supplied.complete(null);assertFalse(combined.isDone());actual.complete(null);assertNull(combined.join());assertEquals(1,calls[0]);
        }
    }
    @Test void externalCancellationOfTheAdapterDoesNotCancelItsActualPrerequisites(){
        for(boolean save:new boolean[]{false,true}){
            Route route=route(save);CompletableFuture<Void> first=new CompletableFuture<>(),second=new CompletableFuture<>();route.add.accept(first);route.add.accept(second);CompletableFuture<?> combined=route.get.get();assertTrue(combined.cancel(false));assertFalse(first.isDone());assertFalse(second.isDone());first.complete(null);second.complete(null);assertTrue(combined.isCancelled());
        }
    }
    @Test void actualVirtualCombineIsNotSkippedForAnInstrumentedStockClass(){
        for(boolean save:new boolean[]{false,true}){
            Route route=route(save);
            CompletableFuture<Void> first=org.mockito.Mockito.mock(CompletableFuture.class);
            CompletableFuture<Void> second=new CompletableFuture<>(),returned=new CompletableFuture<>();
            assertEquals(CompletableFuture.class,first.getClass());
            org.mockito.Mockito.doReturn(false).when(first).isDone();
            org.mockito.Mockito.doReturn(returned).when(first).thenCombine(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            route.add.accept(first);route.add.accept(second);
            assertSame(returned,route.get.get(),"actual thenCombine replacement must remain observable");
            org.mockito.Mockito.verify(first).thenCombine(org.mockito.ArgumentMatchers.same(second),org.mockito.ArgumentMatchers.any());
        }
    }
}
