package net.minecraft.world.item;

import java.util.List;
import java.util.Map;

public final class TaczGunFireModes {
    private static final Map<String, List<TaczFireMode>> MODES = Map.ofEntries(
        Map.entry("aa12", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("ai_awp", List.of(TaczFireMode.SEMI)),
        Map.entry("ak47", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("aug", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("b93r", List.of(TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("cz75", List.of(TaczFireMode.AUTO)),
        Map.entry("db_long", List.of(TaczFireMode.SEMI)),
        Map.entry("db_short", List.of(TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("deagle", List.of(TaczFireMode.SEMI)),
        Map.entry("deagle_golden", List.of(TaczFireMode.SEMI)),
        Map.entry("fn_evolys", List.of(TaczFireMode.AUTO)),
        Map.entry("fn_fal", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("g36k", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("glock_17", List.of(TaczFireMode.SEMI)),
        Map.entry("hk_g3", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("hk_mk23", List.of(TaczFireMode.SEMI, TaczFireMode.BURST)),
        Map.entry("hk_mp5a5", List.of(TaczFireMode.AUTO, TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("hk416d", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("kar98", List.of(TaczFireMode.SEMI)),
        Map.entry("lonetrail", List.of(TaczFireMode.SEMI)),
        Map.entry("m1", List.of(TaczFireMode.SEMI)),
        Map.entry("m1_garand", List.of(TaczFireMode.SEMI)),
        Map.entry("m1a1", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("m1014", List.of(TaczFireMode.SEMI)),
        Map.entry("m107", List.of(TaczFireMode.SEMI)),
        Map.entry("m16a1", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("m16a4", List.of(TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("m1897", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("m1911", List.of(TaczFireMode.SEMI)),
        Map.entry("m249", List.of(TaczFireMode.AUTO)),
        Map.entry("m320", List.of(TaczFireMode.SEMI)),
        Map.entry("m4a1", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("m700", List.of(TaczFireMode.SEMI)),
        Map.entry("m870", List.of(TaczFireMode.SEMI)),
        Map.entry("m95", List.of(TaczFireMode.SEMI)),
        Map.entry("m9a4", List.of(TaczFireMode.SEMI)),
        Map.entry("mp40", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("minigun", List.of(TaczFireMode.AUTO, TaczFireMode.BURST)),
        Map.entry("mk14", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("p320", List.of(TaczFireMode.SEMI)),
        Map.entry("p90", List.of(TaczFireMode.AUTO, TaczFireMode.BURST)),
        Map.entry("qbz_191", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("qbz_95", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI, TaczFireMode.BURST)),
        Map.entry("rhino357", List.of(TaczFireMode.SEMI)),
        Map.entry("rpg7", List.of(TaczFireMode.SEMI)),
        Map.entry("rpk", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("scar_h", List.of(TaczFireMode.SEMI, TaczFireMode.AUTO)),
        Map.entry("scar_l", List.of(TaczFireMode.AUTO, TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("sks_tactical", List.of(TaczFireMode.SEMI)),
        Map.entry("spas_12", List.of(TaczFireMode.SEMI, TaczFireMode.BURST)),
        Map.entry("spr15hb", List.of(TaczFireMode.SEMI, TaczFireMode.BURST)),
        Map.entry("springfield1873", List.of(TaczFireMode.SEMI)),
        Map.entry("stg44", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("taurus500", List.of(TaczFireMode.SEMI)),
        Map.entry("taurus943", List.of(TaczFireMode.SEMI)),
        Map.entry("timeless50", List.of(TaczFireMode.SEMI)),
        Map.entry("trs_bull", List.of(TaczFireMode.SEMI)),
        Map.entry("type_81", List.of(TaczFireMode.AUTO, TaczFireMode.SEMI)),
        Map.entry("ump45", List.of(TaczFireMode.AUTO, TaczFireMode.BURST)),
        Map.entry("uzi", List.of(TaczFireMode.AUTO)),
        Map.entry("vector45", List.of(TaczFireMode.AUTO, TaczFireMode.BURST, TaczFireMode.SEMI)),
        Map.entry("g43", List.of(TaczFireMode.SEMI)),
        Map.entry("raygun_bo6", List.of(TaczFireMode.AUTO))
    );

    private TaczGunFireModes() {}

    public static List<TaczFireMode> modes(String gunId) {
        return MODES.getOrDefault(gunId, List.of(TaczFireMode.SEMI));
    }
}
