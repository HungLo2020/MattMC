package net.minecraft.world.level.levelgen;

import java.util.*;

/** Same-package getter methods must not become parity-formatting callbacks. */
public final class PositionalFormattingEffects {
    static final Exception MARKER=new Exception("original checked formatting getter");
    @SuppressWarnings("unchecked")static <T extends Throwable>RuntimeException raise(Throwable failure)throws T{throw(T)failure;}
    static final class Legacy extends LegacyRandomSource.LegacyPositionalRandomFactory {
        final List<String> calls=new ArrayList<>();final boolean reject;
        Legacy(boolean reject){super(7);this.reject=reject;}
        // No Override annotation: original Frozen has no such package getter.
        long seed(){calls.add("seed");if(reject)throw raise(MARKER);return 99;}
    }
    static final class Xoro extends XoroshiroRandomSource.XoroshiroPositionalRandomFactory {
        final List<String> calls=new ArrayList<>();final boolean reject;
        Xoro(boolean reject){super(11,13);this.reject=reject;}
        long seedLo(){calls.add("lo");if(reject)throw raise(MARKER);return 99;}
        long seedHi(){calls.add("hi");if(reject)throw raise(MARKER);return 99;}
    }
    static void render(String name,boolean reject,PositionalRandomFactory factory,List<String> calls){
        var builder=new StringBuilder("prefix|");Throwable failure=null;
        try{factory.parityConfigString(builder);}catch(Throwable actual){failure=actual;}
        System.out.println("POSITIONAL_FORMAT name="+name+" reject="+reject+" value="+builder+" calls="+calls
            +" same-marker="+(failure==MARKER)+" error="+(failure==null?"none":failure.getClass().getName()+":"+failure.getMessage()));
    }
    public static void main(String[]args){
        for(boolean reject:new boolean[]{false,true}){
            var legacy=new Legacy(reject);render("legacy",reject,legacy,legacy.calls);
            var xoro=new Xoro(reject);render("xoro",reject,xoro,xoro.calls);
        }
    }
}
