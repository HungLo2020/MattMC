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

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
	// Class bindings preserve Java class identity/iteration; native groups own members.
	private final NativeClassInstanceStorage<T> nativeStorage = new NativeClassInstanceStorage<>();
	private final Map<Class<?>, NativeClassInstanceStorage<T>.Group> byClass = Maps.newHashMap();
	private final Class<T> baseClass;
	private final NativeClassInstanceStorage<T>.Group allInstances = this.nativeStorage.root();

	public ClassInstanceMultiMap(Class<T> class_) {
		this.baseClass = class_;
		this.byClass.put(class_, this.allInstances);
	}

	public boolean add(T object) {
        long token=0;
        boolean added=false;
        try {
            for (var entry : this.byClass.entrySet()) {
                if (entry.getKey().isInstance(object)) {
                    if(token==0)token=this.nativeStorage.bind(object);
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
                return group;
            });
			return (Collection<S>)Collections.unmodifiableCollection(list);
		}
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
