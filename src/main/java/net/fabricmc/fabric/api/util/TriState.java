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

package net.fabricmc.fabric.api.util;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a boolean value that can be true, false, or default/unset.
 */
public enum TriState {
    /**
     * Represents the boolean value of {@code false}.
     */
    FALSE,
    /**
     * Represents the default/unset value.
     */
    DEFAULT,
    /**
     * Represents the boolean value of {@code true}.
     */
    TRUE;

    /**
     * Gets a TriState from a boolean value.
     *
     * @param value the boolean value
     * @return {@link #TRUE} if true, {@link #FALSE} if false
     */
    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Gets a TriState from a nullable Boolean.
     *
     * @param value the nullable boolean value
     * @return {@link #TRUE} if true, {@link #FALSE} if false, {@link #DEFAULT} if null
     */
    public static TriState of(@Nullable Boolean value) {
        return value == null ? DEFAULT : of(value.booleanValue());
    }

    /**
     * Gets the boolean value of this TriState.
     *
     * @return the boolean value if set, null if default
     */
    public @Nullable Boolean getBoxed() {
        return switch (this) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Gets the boolean value, or throws if default.
     *
     * @return the boolean value
     * @throws IllegalStateException if this is DEFAULT
     */
    public boolean get() {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            default -> throw new IllegalStateException("TriState is DEFAULT");
        };
    }

    /**
     * Gets the boolean value with a fallback.
     *
     * @param defaultValue the default value if this is DEFAULT
     * @return the boolean value or the default
     */
    public boolean orElse(boolean defaultValue) {
        return this == DEFAULT ? defaultValue : this == TRUE;
    }

    /**
     * Gets the boolean value with a supplier fallback.
     *
     * @param supplier supplies the default value if this is DEFAULT
     * @return the boolean value or the supplied default
     */
    public boolean orElseGet(BooleanSupplier supplier) {
        return this == DEFAULT ? supplier.getAsBoolean() : this == TRUE;
    }

    /**
     * Maps this TriState to an Optional.
     *
     * @param <T>           the type to map to
     * @param trueValue     the value for TRUE
     * @param falseValue    the value for FALSE
     * @return an Optional containing the mapped value, or empty if DEFAULT
     */
    public <T> Optional<T> map(Supplier<? extends T> trueValue, Supplier<? extends T> falseValue) {
        return switch (this) {
            case TRUE -> Optional.of(trueValue.get());
            case FALSE -> Optional.of(falseValue.get());
            default -> Optional.empty();
        };
    }
}
