package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.NativeEntityGeometry;
import net.minecraft.world.phys.AABB;

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
    private static final java.util.concurrent.atomic.AtomicLong NEXT_QUERY_TYPE = new java.util.concurrent.atomic.AtomicLong();
    private static final ClassValue<Long> QUERY_TYPES = new ClassValue<>() {
        @Override protected Long computeValue(Class<?> type) {
            long identity=NEXT_QUERY_TYPE.incrementAndGet();
            if(identity==0)throw new IllegalStateException("Section query type identities exhausted");
            return identity;
        }
    };
    /** Stable semantic class identity; neither a Java object pointer nor a GPU handle. */
    public static long queryTypeIdentity(Class<?> type) { return QUERY_TYPES.get(type); }

    /** Reusable bounded CPU output for the enclosing native section cursor. No entity references. */
    public static final class QueryBatch {
        final long[] rows = new long[130];
        public final java.lang.foreign.MemorySegment output = java.lang.foreign.MemorySegment.ofArray(rows);
        public int count, expected;
        boolean busy;
        public void begin(int result) {
            count=result==129?-2:result==130?-1:result;
            if(count>0)expected=(int)(rows[1]>>>32);
        }
    }
	// Class bindings preserve Java class identity/iteration; native groups own members.
	private final NativeClassInstanceStorage<T> nativeStorage = new NativeClassInstanceStorage<>();
	private final Map<Class<?>, NativeClassInstanceStorage<T>.Group> byClass = Maps.newHashMap();
	private final Class<T> baseClass;
	private final NativeClassInstanceStorage<T>.Group allInstances = this.nativeStorage.root();

	public ClassInstanceMultiMap(Class<T> class_) {
		this.baseClass = class_;
		this.byClass.put(class_, this.allInstances);
        if(class_!=null)this.allInstances.bindType(queryTypeIdentity(class_));
	}

    public boolean add(T object) { return add(object,null); }

    /** Optional primary geometry is retained by the native membership binding. */
    public boolean add(T object,NativeEntityGeometry.Body geometry) {
        long token=0;
        boolean added=false;
        try {
            for (var entry : this.byClass.entrySet()) {
                if (entry.getKey().isInstance(object)) {
                    if(token==0)token=this.nativeStorage.bind(object,geometry);
                    entry.getValue().append(token);
                    added=true;
                }
            }
            return added;
        } finally {if(token!=0)this.nativeStorage.finish(token);}
    }

	public boolean remove(Object object) {
		boolean bl = false;

		for (var entry : this.byClass.entrySet()) {
			if (((Class)entry.getKey()).isInstance(object)) {
				var list = entry.getValue();
				bl |= list.remove(object);
			}
		}

		return bl;
	}

	public boolean contains(Object object) {
		return this.find(object.getClass()).contains(object);
	}

	@SuppressWarnings("unchecked")
	public <S> Collection<S> find(Class<S> class_) {
		if (!this.baseClass.isAssignableFrom(class_)) {
			throw new IllegalArgumentException("Don't know how to search for " + class_);
		} else {
            var list = this.byClass.computeIfAbsent(class_, class_x -> {
                var group=this.nativeStorage.newGroup();
                group.copyMatching(this.allInstances,class_x);
                group.bindType(queryTypeIdentity(class_x));
                return group;
            });
			return (Collection<S>)Collections.unmodifiableCollection(list);
		}
	}

    /** Rust scans attached stock geometry; bounds runs only for behavior requiring Java. */
    public AbortableIterationConsumer.Continuation forEachIntersecting(AABB query,BiPredicate<? super T,AABB> bounds,AbortableIterationConsumer<? super T> consumer) {
        return this.allInstances.forEachIntersecting(query,bounds,consumer);
    }

    /** Consumes the first batch already scanned by the native section index. */
    public AbortableIterationConsumer.Continuation forEachIntersecting(AABB query,BiPredicate<? super T,AABB> bounds,AbortableIterationConsumer<? super T> consumer,QueryBatch batch) {
        return this.allInstances.forEachIntersecting(query,bounds,consumer,batch,batch.count,batch.expected);
    }

    /** Identity projection only; the collector cannot run user code while projecting a batch. */
    public T collectionBinding(long token) { return this.nativeStorage.collectionBinding(token); }

    /** Resume the original iterator at the first callback not handled by native collection. */
    public void continueCollection(AABB query,BiPredicate<? super T,AABB> bounds,AbortableIterationConsumer<? super T> consumer,QueryBatch batch,int cursor,int expected) {
        this.allInstances.forEachIntersecting(query,bounds,consumer,batch,0,expected,cursor);
    }

    public int sectionVisibility() { return this.nativeStorage.sectionVisibility(); }
    public int updateSectionVisibility(int value) { return this.nativeStorage.updateSectionVisibility(value); }
    public void attachSectionIndex(java.lang.foreign.MemorySegment index,long token) { this.nativeStorage.attachSectionIndex(index,token); }

    @SuppressWarnings({"unchecked","rawtypes"})
    public <S> AbortableIterationConsumer.Continuation forEachIntersecting(Class<S> type,AABB query,BiPredicate<? super S,AABB> bounds,AbortableIterationConsumer<? super S> consumer) {
        this.find(type); // Preserve lazy class admission and group construction.
        NativeClassInstanceStorage<S>.Group group=(NativeClassInstanceStorage.Group)this.byClass.get(type);
        return group.forEachIntersecting(query,bounds,consumer);
    }

    /** The index scanned this already admitted class group before returning. */
    @SuppressWarnings({"unchecked","rawtypes"})
    public <S> AbortableIterationConsumer.Continuation consumeTypedBatch(Class<S> type,AABB query,BiPredicate<? super S,AABB> bounds,AbortableIterationConsumer<? super S> consumer,QueryBatch batch) {
        NativeClassInstanceStorage<S>.Group group=(NativeClassInstanceStorage.Group)this.byClass.get(type);
        return group.forEachIntersecting(query,bounds,consumer,batch,batch.count,batch.expected);
    }

	public Iterator<T> iterator() {
		return (Iterator<T>)(this.allInstances.isEmpty() ? Collections.emptyIterator() : Iterators.unmodifiableIterator(this.allInstances.iterator()));
	}

	public List<T> getAllInstances() {
		return ImmutableList.copyOf(this.allInstances);
	}

	public int size() {
		return this.allInstances.size();
	}
}
