package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original formatting reads private state without virtual package-getter effects. */
class PositionalFormattingContractTest {
    private static final Exception MARKER=new Exception("checked package seed getter");
    @SuppressWarnings("unchecked")private static <T extends Throwable>RuntimeException raise(Throwable failure)throws T{throw(T)failure;}
    private static void legacy(boolean reject){
        var calls=new ArrayList<String>();
        var factory=new LegacyRandomSource.LegacyPositionalRandomFactory(7){
            long seed(){calls.add("seed");if(reject)throw raise(MARKER);return 99;}
        };
        var out=new StringBuilder("prefix|");
        assertDoesNotThrow(()->factory.parityConfigString(out));
        assertEquals("prefix|LegacyPositionalRandomFactory{7}",out.toString());
        assertTrue(calls.isEmpty(),"Formatting must not invoke a subclass seed getter");
    }
    private static void xoro(boolean reject){
        var calls=new ArrayList<String>();
        var factory=new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(11,13){
            long seedLo(){calls.add("lo");if(reject)throw raise(MARKER);return 99;}
            long seedHi(){calls.add("hi");if(reject)throw raise(MARKER);return 99;}
        };
        var out=new StringBuilder("prefix|");
        assertDoesNotThrow(()->factory.parityConfigString(out));
        assertEquals("prefix|seedLo: 11, seedHi: 13",out.toString());
        assertTrue(calls.isEmpty(),"Formatting must not invoke subclass seed getters");
    }
    @Test void legacyFormattingIgnoresDifferentPackageGetterValue(){legacy(false);}
    @Test void legacyFormattingDoesNotInvokeCheckedThrowingGetter(){legacy(true);}
    @Test void xoroFormattingIgnoresDifferentPackageGetterValues(){xoro(false);}
    @Test void xoroFormattingDoesNotInvokeCheckedThrowingGetters(){xoro(true);}
}
