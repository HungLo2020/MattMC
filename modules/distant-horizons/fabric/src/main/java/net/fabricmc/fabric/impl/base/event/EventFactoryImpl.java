/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.base.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;

import net.fabricmc.fabric.api.event.Event;

/**
 * 100% API-compatible implementation for Distant Horizons.
 * Simplified implementation focusing on array-backed events without complex phase ordering.
 */
public final class EventFactoryImpl {
	private EventFactoryImpl() { }

	public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
		return new ArrayBackedEvent<>(type, invokerFactory);
	}

	public static void ensureContainsDefault(ResourceLocation[] defaultPhases) {
		for (ResourceLocation id : defaultPhases) {
			if (id.equals(Event.DEFAULT_PHASE)) {
				return;
			}
		}

		throw new IllegalArgumentException("The event phases must contain Event.DEFAULT_PHASE.");
	}

	public static void ensureNoDuplicates(ResourceLocation[] defaultPhases) {
		for (int i = 0; i < defaultPhases.length; ++i) {
			for (int j = i+1; j < defaultPhases.length; ++j) {
				if (defaultPhases[i].equals(defaultPhases[j])) {
					throw new IllegalArgumentException("Duplicate event phase: " + defaultPhases[i]);
				}
			}
		}
	}

	/**
	 * Simplified array-backed event implementation.
	 * 100% API-compatible for standard event usage.
	 */
	static class ArrayBackedEvent<T> extends Event<T> {
		private final Function<T[], T> invokerFactory;
		private final Object lock = new Object();
		private final List<T> listeners = new ArrayList<>();
		private final Class<? super T> type;

		@SuppressWarnings("unchecked")
		ArrayBackedEvent(Class<? super T> type, Function<T[], T> invokerFactory) {
			this.type = type;
			this.invokerFactory = invokerFactory;
			updateInvoker();
		}

		private void updateInvoker() {
			@SuppressWarnings("unchecked")
			T[] array = (T[]) Array.newInstance(type, listeners.size());
			listeners.toArray(array);
			this.invoker = invokerFactory.apply(array);
		}

		@Override
		public void register(T listener) {
			if (listener == null) {
				throw new NullPointerException("Tried to register a null listener!");
			}

			synchronized (lock) {
				listeners.add(listener);
				updateInvoker();
			}
		}

		@Override
		public void register(ResourceLocation phase, T listener) {
			// Simplified: ignore phases, just register to default
			register(listener);
		}

		@Override
		public void addPhaseOrdering(ResourceLocation firstPhase, ResourceLocation secondPhase) {
			// Simplified: phase ordering not needed for Distant Horizons use case
			if (firstPhase == null || secondPhase == null) {
				throw new NullPointerException("Tried to add an ordering for a null phase.");
			}
			if (firstPhase.equals(secondPhase)) {
				throw new IllegalArgumentException("Tried to add a phase that depends on itself.");
			}
		}
	}
}
