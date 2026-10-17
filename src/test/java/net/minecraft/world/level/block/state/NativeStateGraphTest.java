package net.minecraft.world.level.block.state;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.List;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

class NativeStateGraphTest {
    private static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final IntegerProperty AGE = IntegerProperty.create("age", 2, 4);

    private static final class State extends StateHolder<String, State> {
        State(String owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<State> codec) {
            super(owner, values, codec);
        }
    }

    @Test
    void graphViewsSurviveInputArenaClosureAndCollection() {
        NativeStateGraph graph = new NativeStateGraph(new int[]{2, 3});
        assertEquals(6, graph.stateCount);
        for (int repeat = 0; repeat < 3; repeat++) {
            System.gc();
            assertEquals(1, graph.value(4, 0));
            assertEquals(1, graph.value(4, 1));
            assertEquals(1, graph.target(4, 0, 0));
            assertEquals(5, graph.target(4, 1, 2));
        }
        assertEquals(1, new NativeStateGraph(new int[0]).stateCount);
        assertThrows(IllegalArgumentException.class, () -> new NativeStateGraph(new int[]{0}));
        assertThrows(IllegalArgumentException.class, () -> new NativeStateGraph(new int[]{256, 256}));
    }

    @Test
    void everyStateAndTransitionMatchesIndependentCartesianEnumeration() {
        var definition = new StateDefinition.Builder<String, State>("fixture").add(AGE, ACTIVE)
            .create(owner -> null, State::new);
        var states = definition.getPossibleStates();
        assertEquals(List.of(ACTIVE, AGE), List.copyOf(definition.getProperties()));
        assertEquals(6, states.size());
        int index = 0;
        for (boolean active : ACTIVE.getPossibleValues()) {
            for (int age : AGE.getPossibleValues()) {
                State state = states.get(index++);
                assertEquals(active, state.getValue(ACTIVE));
                assertEquals(age, state.getValue(AGE));
                for (boolean next : ACTIVE.getPossibleValues()) {
                    State expected = states.stream().filter(s -> s.getValue(ACTIVE) == next && s.getValue(AGE) == age).findFirst().orElseThrow();
                    assertSame(expected, state.setValue(ACTIVE, next));
                }
                for (int next : AGE.getPossibleValues()) {
                    State expected = states.stream().filter(s -> s.getValue(ACTIVE) == active && s.getValue(AGE) == next).findFirst().orElseThrow();
                    assertSame(expected, state.setValue(AGE, next));
                }
                assertSame(state, state.setValue(AGE, age));
                assertThrows(IllegalArgumentException.class, () -> state.setValue(AGE, 5));
            }
        }
    }
}
