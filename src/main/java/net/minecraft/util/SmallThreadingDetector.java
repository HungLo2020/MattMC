package net.minecraft.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * FerriteCore optimization #9: Lightweight threading detector using a byte field instead of ThreadingDetector object.
 * Memory savings: 10-15 MB base (scales with loaded chunks) by using 1 byte vs ~48+ bytes per PalettedContainer
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class SmallThreadingDetector {
    public static final byte UNLOCKED = 0;
    public static final byte LOCKED = 1;
    public static final byte CRASHING = 2;

    public interface SmallThreadDetectable {
        byte ferritecore$getState();
        void ferritecore$setState(byte newState);
    }

    public static void acquire(SmallThreadDetectable obj, String name) {
        byte oldState;
        synchronized (obj) {
            oldState = obj.ferritecore$getState();
            if (oldState == UNLOCKED) {
                // Fast path: previously unlocked, everything is fine
                obj.ferritecore$setState(LOCKED);
                return;
            } else if (oldState == LOCKED) {
                // Locking twice => start crash in synchronized block
                GlobalCrashHandler.startCrash(obj, name);
                obj.ferritecore$setState(CRASHING);
            }
        }
        if (oldState == LOCKED) {
            // Locking twice
            GlobalCrashHandler.crashAcquire(obj);
        } else {
            // already crashing
            GlobalCrashHandler.crashBystander(obj);
        }
    }

    public static void release(SmallThreadDetectable obj) {
        byte oldState;
        synchronized (obj) {
            oldState = obj.ferritecore$getState();
            if (oldState == LOCKED) {
                // Fast path
                obj.ferritecore$setState(UNLOCKED);
                return;
            }
        }
        if (oldState == CRASHING) {
            GlobalCrashHandler.crashRelease(obj);
        }
    }

    /**
     * This code only runs when preparing a threading crash, so none of it needs to be remotely fast
     */
    private static class GlobalCrashHandler {
        private static final Object MONITOR = new Object();
        private static final Map<SmallThreadDetectable, CrashingState> ACTIVE_CRASHES = new IdentityHashMap<>();

        private static void startCrash(SmallThreadDetectable owner, String name) {
            synchronized (MONITOR) {
                ACTIVE_CRASHES.put(owner, new CrashingState(name, owner));
            }
        }

        private static void crashAcquire(SmallThreadDetectable owner) {
            var state = getAndWait(owner, ThreadRole.ACQUIRE);
            throw state.mainException;
        }

        private static void crashRelease(SmallThreadDetectable owner) {
            var state = getAndWait(owner, ThreadRole.RELEASE);
            throw state.mainException;
        }

        private static void crashBystander(SmallThreadDetectable owner) {
            var state = getAndWait(owner, ThreadRole.BYSTANDER);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(
                    "Bystander to crash of type " + state.name + " on threads " + state.releaseThread + ", " + state.acquireThread
            );
        }

        private static CrashingState getAndWait(SmallThreadDetectable owner, ThreadRole role) {
            CrashingState result;
            synchronized (MONITOR) {
                result = Objects.requireNonNull(ACTIVE_CRASHES.get(owner));
            }
            result.waitUntilReady(role);
            return result;
        }
    }

    private static class CrashingState {
        final String name;
        final SmallThreadDetectable owner;
        Thread acquireThread;
        Thread releaseThread;
        RuntimeException mainException;

        private CrashingState(String name, SmallThreadDetectable owner) {
            this.name = name;
            this.owner = owner;
        }

        public synchronized void waitUntilReady(ThreadRole role) {
            if (role == ThreadRole.ACQUIRE) {
                acquireThread = Thread.currentThread();
            } else if (role == ThreadRole.RELEASE) {
                releaseThread = Thread.currentThread();
            }
            notifyAll();
            try {
                waitUntilOrCrash(() -> acquireThread != null && releaseThread != null);
                if (role == ThreadRole.ACQUIRE) {
                    mainException = ThreadingDetector.makeThreadingException(name, releaseThread);
                    notifyAll();
                } else {
                    waitUntilOrCrash(() -> mainException != null);
                }
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
        }

        private synchronized void waitUntilOrCrash(BooleanSupplier isReady) throws InterruptedException {
            final long maxTotalTime = 10_000;
            final var start = System.currentTimeMillis();
            while (!isReady.getAsBoolean()) {
                if (System.currentTimeMillis() - start > 6 * maxTotalTime) {
                    throw new RuntimeException(
                            "Threading detector crash did not find other thread, missing release call?" +
                            " Owner: " + this.owner + " (ID hash: " + System.identityHashCode(this.owner) + ")" +
                            ", time: " + System.currentTimeMillis()
                    );
                }
                this.wait(maxTotalTime);
            }
        }
    }

    private enum ThreadRole {
        ACQUIRE, RELEASE, BYSTANDER
    }
}
