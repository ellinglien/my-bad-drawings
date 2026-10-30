package io.github.mpdairy.monopaint;

/**
 * Once (2026-10-28), the owner's review of their saved brushes: each library brush stored under exactly a row's name and
 * code ({@link #ROWS}) is removed, or renamed (in its place and stamp, its code's slug following the name, as {@link
 * Brush#write} writes any name) and given its own size and grey ({@link ToolSwatches#setLook}; the owner: "consider the
 * ideal size and shade for each tool... since we have to set that now"). Rows matching nothing (another device's
 * library) are passed by. A brush already under its new name (the library read back from {@link BrushBackup} after a
 * reinstall) gets its size and grey again. The library is written once, so its backup file follows. The slots, Basic
 * tools ({@link ToolPresets}) and Recent are left as they are. Run at every launch by {@link ToolSlots}, done once per
 * install ({@link #DONE}); a library that starts on the first brushes is never cleaned (Filled shape, which a row removes, is one). Pure, checked on the desktop.
 */
final class LibraryCleanup {
    /** Kept once the cleanup has run. */
    static final String DONE = "brush_library_cleanup";
    /**
     * The owner's approved table: name and code as stored, keep or remove, the new name ("" keeps it), px and darkness
     * (0 white to 1 black; "" none).
     */
    static final String[][] ROWS = {
            {"Fineliner", "fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b", "keep", "Classic fineliner", "3", "1"},
            {"Brush pen", "brush-pen.M10at00000000at_000zz0000000000000_0_000_0_8at0000zz00zz000000nz0.c", "keep", "Classic brush pen", "6", "1"},
            {"Ink wash", "ink-wash.M70bi0000000090_1000000000000_0_000_200000js_8zz00003m0000000000nz0.d", "keep", "Classic ink wash", "100", "0.25"},
            {"Watercolour", "watercolour.M60bi000000005e_1zzcl00000000_0_000_29b000js_8zzcl003m0000000000nz0.e", "keep", "Classic watercolour", "130", "0.15"},
            {"noodly-heron", "noodly-heron.Md1rtxoi0rk0000ljat_000zz2000000000000_0_048_200000js_8ot0000000000910000nz1.1b15wpy", "remove", "", "", ""},
            {"lumpy-llama", "lumpy-llama.Md1fwcui000voh9j7at_000zz6g00000000000_0_000_200000js_8ef00000000002j0000nz1.wievsh", "remove", "", "", ""},
            {"wobbly-owl", "wobbly-owl.Md0i000000000f4_1zzjzc4xhaq00_0_000_28e000js_8xj00003m0000000000nz0.hd", "remove", "", "", ""},
            {"wobbly-ferret", "wobbly-ferret.Md0i0vzbl00q3at_000zz0000000000000_0_0ko_200000js_8lr0000000000000000nz0.ye7v6x", "remove", "", "", ""},
            {"stout-mango", "stout-mango.Md0i000000000eg_1zzf5vftw0000_0_000_2ch000js_8pb00003m0000000000nz0.1br0ju4", "remove", "", "", ""},
            {"dapper-quince", "dapper-quince.Md1zz5xi0a3zz00pqat_000zzlm00000000000_0_3da_200000js_8zz0000000000k50000nz1.1bkaiqh", "keep", "Felt chisel pen", "4", "1"},
            {"stout-mango", "stout-mango.Md0i000000000eg_1zzf5vftw0000_0_1i0_2ch000js_8pb00003m0000000000nz0.1br0ju4", "remove", "", "", ""},
            {"mellow-gourd", "mellow-gourd.M70i000000000sq_1zz78p0ij2j00_0_000_2f1000js_8k800003m0000000000nz0.106wqdj", "remove", "", "", ""},
            {"chipper-yam", "chipper-yam.Md27o7uhv8ajo00d4n9_2odrc_0_400_27x1h02q_8sc000000dcymdgdp00nz0.hbhod1", "keep", "Crumpled flat paint", "60", "0.6"},
            {"bashful-grape", "bashful-grape.M61okeei000000000g8_1u0e700h0ho00_0_000_2nej90jscs00i6csjf_8k30000000000zzzzi0nz1.fprpd0", "keep", "Chisel watercolour", "80", "0.25"},
            {"rustic-shallot", "rustic-shallot.M60i000000000e1_1yehn00j2ad00_0_03m_2clcl0js_8etzzn40000wmwmf500l700n9.1i0hkv6", "remove", "", "", ""},
            {"snug-parsnip", "snug-parsnip.Md1umm5ods70000gf4y_1jvg1m6000000_0_0n4_2j0ss0o5_8xriv00s600srqoko00nz1000zz.uuqlne", "keep", "Speckled slow wash", "120", "0.3"},
            {"mellow-caper", "mellow-caper.Md3hm0ib006v_1v5g2a5gv0000_0_0n4_2c1t30l9_8rr00000000ssa00000nz0.qmaaf6", "keep", "Speckled hake wash", "150", "0.3"},
            {"smug-heron", "smug-heron.Md1ekxoi0rk0000ljyc_000zz0000000000000_0_048_200mk0js_8otoi00nr0000910000nz1.1oox1go", "remove", "", "", ""},
            {"zealous-shallot", "zealous-shallot.M67gzlduui0_1zfmq009t0500_0_000_1jz_8rx0000000000000000nz0.vvrysb", "keep", "Wet splatter", "120", "0.35"},
            {"stubborn-mango", "stubborn-mango.M10i0bijr6k5eat_06bs28v00000000000_0_33b_1jz_8s10000kn00006e0000nz0.rh2pnt", "keep", "Felt ink brush", "6", "1"},
            {"jumbo-caper", "jumbo-caper.M31tceei000000000at_000zzf800000000000_0_000_1jz_8zz0000000000180000yd0.12nsmn7", "remove", "", "", ""},
            {"suave-lemon", "suave-lemon.Md8ge847q1zzjn_01y4zq82kmt0020000_0_100_225vh0sl_8fu0000000000000000pd1.nzhzxv", "keep", "Stitch hatch", "24", "1"},
            {"Pooling brush", "pooling-brush.M1000mwmc0000at_000u5d87f000000000_0_000_1jz_8qp0000fh0000he0000nz0.ib8b3u", "keep", "Fine pooling brush", "4", "1"},
            {"jumbo-squash", "jumbo-squash.M60i00000000085_1sz000000bh00_0_000_2nkcl0js_8j10000000000000000nz0.1ywmrw6", "remove", "", "", ""},
            {"jumbo-squash 2", "jumbo-squash.M60000000000085_1sz8gamj2bh00_0_000_2nkcl0js_8j10000000000000000nz0.1ywmrw6", "remove", "", "", ""},
            {"noodly-heron 2", "noodly-heron.Md1rtxoi0rk0000ljat_000zz2000000000000_0_048_200000js_8zz0000000000910000zz1.1b15wpy", "remove", "", "", ""},
            {"bold-corn", "bold-corn.M67pd6bzzon1_1peo200ia0000_0_000_200cl0js00002w_8zzi000000000000000nz0.1vxe9nr", "remove", "", "", ""},
            {"wild-shallot", "wild-shallot.Md8b47q6y0zzi8_1yth91x00ku00_0_100_1tt_8b0o000o600007u0000nz1.1t5jz7o", "keep", "Crosshatch wash", "90", "0.5"},
            {"nimble-grapewhite", "nimble-grapewhite.Md7cleei0fh_38v5700zz1_0_1ms_1ey_8xs0000vp00iw000000nz0.1311e43", "keep", "White stipple", "40", "0"},
            {"brisk-yam", "brisk-yam.Md3ht1ev0000_2ohnn_0_5ew_1eu_80000zz000000000000nz1.15c0e7u", "remove", "", "", ""},
            {"brisk-yamcalig", "brisk-yamcalig.Md3ht1ev0000_2ohnn_0_5ew_1eu_80000zz000000000000nz1.15c0e7u", "keep", "Calligraphy hake", "60", "0.7"},
            {"regal-caperoilunderpaint", "regal-caperoilunderpaint.Md0zzg200cn7h5i_2zzha_1k35j_000_1gn_8syskwb000000ms0089nz0.1k1aqs0", "keep", "Oil underpaint", "120", "0.4"},
            {"chipper-turnip", "chipper-turnip.Md25aivhx000000005s_2u200_0_000_1g6_8zz00000000zz00jr00zz0.wxnf4u", "keep", "Opaque flat", "50", "0.5"},
            {"regal-quince", "regal-quince.Md8zzdt9v1zzjm_000o7herw000000000_0_000_2c3ri0ob_8y1000000000000zz00nz1.163unwg", "remove", "", "", ""},
            {"regal-quincehatchingshadows", "regal-quincehatchingshadows.Md8uydt9v1zzjm_000o7herw000000000_0_000_2c3ri0ob_8y1000000000000zz00nz1.163unwg", "keep", "Shadow hatching", "30", "1"},
            {"Pooling brush 2", "pooling-brush.M1000mwmc0000at_000u5d87f000000000_0_000_1jz_8qp0000fh0000he0000nz0.ib8b3u", "remove", "", "", ""},
            {"fierce-apple", "fierce-apple.Mb0000000p83ybc_2xno4zz2_0_0kg_2clqt0js_8cx0000000000000000nz0.d23iy8", "keep", "Dry brush behind", "40", "0.5"},
            {"fierce-apple 2", "fierce-apple.Mb0000000p83ybc_2xno4zz3_0_0kg_2clqt0js_8cx0000000000000000nz0.d23iy8", "keep", "Invert brush", "40", "1"},
            {"fierce-apple 3", "fierce-apple.Mb0i00000js0000_2syo4_0_0kg_2qzcl0js_7at0000000000p6i0eenz0.d23iy8", "remove", "", "", ""},
            {"vivid-mango", "vivid-mango.Md0lr2z2b00n1vi_4db00_3p6jsat_4i0_1tw_8zz00000000zz000000gx0.3jlsrm", "keep", "Graphite smudge", "20", "0.5"},
            {"peculiar-beet", "peculiar-beet.Ma0i000004f00at_35c3z_0_3xn_1jz_8bz0000ohjk00000000nz0.1cujvy5", "remove", "", "", ""},
            {"peculiar-beet 2", "peculiar-beet.Ma0i000004f00at_35c3z_0_3n1_1jz_8bz0000ohjk00000000nz0.1cujvy5", "remove", "", "", ""},
            {"pastel4", "pastel.Ma0iw00005e00at_3amgxri_3p6jsat_0o4_1mb_8ko0000s4gh00000000n10.oep2x", "keep", "Dusty pastel", "24", "0.6"},
            {"dramatic-pepper", "dramatic-pepper.Ma1n8eei000006h00at_36f6tqb_3p6jsat_1oe_1jz_8ch0000iiec00000000nz0.lwh6iu", "keep", "Dusty chisel pastel", "24", "0.6"},
            {"hatch-parsnip", "hatch-parsnip.Md0b000000000at_61f5gm_1kati_0hx_3jsi0i0_8zzzzzz000000i00000zz1.16yarcq", "keep", "Floating line tone", "100", "0.8"},
            {"Pooling brush 3", "pooling-brush.M1000mwmc0000at_000u5d87f000000000_0_000_1jz_8zz0000000000zz0000zz100000000530000005000c000.ib8b3u", "keep", "Bold pooling brush", "7", "1"},
            {"Impasto", "impasto.M90i00000il003k_1ulat00770000zz2_0_000_1jz_8d30000ty0000000000nz0.t4mf73", "keep", "Oils behind", "60", "0.4"},
            {"Washorn the Weeping", "washorn-the-weeping.M60i000000000hl_1pl006n000000_3p6jsat_di0_1jz_8q50000000000000000nz0.t9bjy9", "keep", "Grainy smear wash", "100", "0.35"},
            {"Chisel pastel", "chisel-pastel.Ma1j2eei000008c00at_34z5w_0_dsl_1jz_89i0000omk400000000nz0.t4mf73", "keep", "Fine chisel pastel", "16", "0.7"},
            {"Fountain pen", "fountain-pen.M00mo00at00003m_03mzzwe00000000000_0_064_2llcl0js00c8_8zz0000000000000000nz000000000530000005000c099l2.2f1", "keep", "Dripping fountain pen", "3", "1"},
            {"Ben-Day brush", "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz265ji000at_0_000_0_25ess00000000000000nz0.2ef", "keep", "Ben-Day chisel behind", "30", "1"},
            {"Flat oils", "flat-oils.M925aeei0000000004h_2z300_2llp6zz_000_7ne_8fh0000k30000000000nz0.2fv", "remove", "", "", ""},
            {"Flat oils 2", "flat-oils.M925aeei0000000004h_2zz00_2llp6zz_000_7ne_8fh0000k30000000000nz0.2fv", "remove", "", "", ""},
            {"Weeping Wash of Blooms", "weeping-wash-of-blooms.M60i000000000kh_1zz7x7d7q8a00zz2_0_100_1jz_8sj0000000000000000nz0.1ojoqj5", "keep", "Bloom wash behind", "140", "0.2"},
            {"Sketch pencil", "sketch-pencil.M40i000000000at_4at2c_0_di0_1jz_85ezz00000000000000nz000000000530000005000c099zz.inw", "keep", "Fine sketch pencil", "3", "0.8"},
            {"Weeping Wash of Blooms 2", "weeping-wash-of-blooms.M60i000000000kh_1muek1z9wcf00zz2_0_100_1jz_8sj0000000000000000nz0.1ojoqj5", "remove", "", "", ""},
            {"Ben-Day brush 2", "ben-day-brush.Me0ij00000000ll_0i0000000000000000zz265ji000at_0_000_0_25ess00000000000000nz0.2ef", "keep", "Ben-Day behind", "30", "1"},
            {"Wax crayon", "wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_8000000zz00zz000000nz0.2f9", "remove", "", "", ""},
            {"Wax crayon 2", "wax-crayon.Ma0p600000000at_7ll1_0_1i0_0_8000000zz00zz000000nz0.2f9", "keep", "Wide crayon scratch", "40", "1"},
            {"Wax crayon 3", "wax-crayon.Ma0p600000000at_7zz0_0_1i0_0_0000000000000000000nz0.2f9", "remove", "", "", ""},
            {"thirsty thick paint 2", "thirsty-thick-paint.M925nzzi00000mp0044_2zzgi_2hnp6ul_500_1jz_8jm0000tm0000000000nz0.1ircz1t", "keep", "Thirsty flat paint", "50", "0.8"},
            {"Highlighter", "highlighter.M31okzzp60000000000_1967700000000zz2_0_000_1jz_85ei000000000000000nz0.2f2", "keep", "Wet highlighter", "60", "0.1"},
            {"Felt marker", "felt-marker.M31j2eei000000000at_000zzbq00920000000_0_300_1jz_8i00000000000i00000nz1.jjds6x", "remove", "", "", ""},
            {"Felt marker 2", "felt-marker.M30i000000000at_000zzbq00920000000_0_300_1jz_8i00000000000i00000nz1.jjds6x", "keep", "Round felt marker", "6", "1"},
            {"Filled shape", "filled-shape.M30wd00000000at_100at00000000_6_000_0_0000000000000000000nz0.2fi", "remove", "", "", ""},
            {"Fill in", "fill-in.M30wd0000000000_1atat00000000_5zz_000_0_0000000000000000000nz0.2fh", "remove", "", "", ""},
            {"Loaded oils", "loaded-oils.M99vleei0000000005e_2zz00_2llp6zz_000_7ne_1ss0000000000000000nz0.2fu", "keep", "Long loaded oils", "70", "0.8"},
            {"thick oil", "thick-oil.M99vleei0000000005e_2wx00_2llydl890_000_7ne_1ss0000000000000000nz0.2fu", "remove", "", "", ""},
            {"bob ross", "bob-ross.M99vleei0000000005e_2zg00_2llydzzn7_000_7ne_1ss0000000000000000nz0.2fu", "remove", "", "", ""},
            {"Ben-Day brush 3", "ben-day-brush.Me0zz839d0000h3_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef", "keep", "Ben-Day dots", "40", "1"},
            {"Chisel marker", "chisel-marker.M31j2eei000000000at_000zz1g005z0000000_0_000_1jz_82o00000000005u0000nz0.t4mf73", "remove", "", "", ""},
            {"Pooling wash", "pooling-wash.M60i000000000g7_1x3772jdba900_0_190_7qzll_8g70000000000000000nz0.373k3", "keep", "Grainy pooling wash", "140", "0.2"},
            {"Pooling wash 2", "pooling-wash.M60i000000000g7_1x3772jdba900_0_190_7qzll_1ss0000000000000000nz0.373k3", "remove", "", "", ""},
            {"bold-corn watercolour good", "bold-corn-watercolour-good.M67pd6bzzon1_1peo200ia0000_0_000_200cl0js00002w_8zzi000000000000000nz0.1vxe9nr", "keep", "Watercolour splatter", "", ""},
            {"peculiar-beet 3", "peculiar-beet.Ma0i000004i00do_3amdb39_0_3n1_1jz_8bz0000ohjk00000000nz0.1cujvy5", "remove", "", "", ""},
            {"Soft pastel", "soft-pastel.Ma0i000000000at_33y8o_0_dnj_1jz_800i0000000zz000000nz000000000530000005000c099qt.51j8ud", "keep", "Fine soft pastel", "20", "0.6"},
            {"Highlighter 2", "highlighter.M31e1zzp60000000000_1007700000000zz2_0_000_0_0000000000000000000nz0.2f2", "remove", "", "", ""},
            {"Fude pen", "fude-pen.M20ss007700003m_000zzat00000000000_0_000_0_8zz0000i00000000000nz01.1s13pn4", "remove", "", "", ""},
            {"wild-shallot 2", "wild-shallot.Md8b47q6y1zzi8_100at00770000_0_100_0_8b0o000o600007u0000nz1.1t5jz7o", "keep", "Crosshatch tone", "90", "1"},
            {"Sketch pencil 2", "sketch-pencil.M40i000000000at_4ip2c_0_di0_1jz_800000000zzzz000000zz000000000530000005000c099zz.inw", "remove", "", "", ""},
            {"Sketch pencil 2b", "sketch-pencil-b.M40i000000000at_4ip2c_0_di0_1jz_800000000zzzz000000zz000000000530000005000c099zz.inw", "remove", "", "", ""},
            {"Felt marker 3", "felt-marker.M30i0776o0q00u1_000zz0000920000000_0_d00_0_8i00000zz0000400000nz0.jjds6x", "keep", "Verve felt", "4", "1"},
            {"Felt marker 3 verve felt", "felt-marker-verve-felt.M30i0776o0q00u1_000zz0000920000000_0_d00_0_8i00000zz0000400000nz0.jjds6x", "remove", "", "", ""},
            {"Soft wash", "soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73", "remove", "", "", ""},
            {"Wet blooms", "wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_7js_8mx0000000000000000nz0.1s13pn4", "remove", "", "", ""},
            {"watercolour wash", "watercolour-wash.M60i000000000ji_1z5gk00f6e400_0_000_7js_8mx0000000000000000nz0.1s13pn4", "remove", "", "", ""},
            {"watercolour wash 2", "watercolour-wash.M60i04o4b0000ji_1zz8t00ag9600_1zzex_000_7js_8zz0000000000000000zz0.1s13pn4", "keep", "Lifting wash", "120", "0.3"}
    };

    /**
     * Runs {@link #ROWS} over {@code library}, unless done before (then null): how many brushes were renamed or given a
     * size and grey, how many removed, how many given a size and grey. A white brush (Opaque's White) takes its px alone.
     */
    static int[] run(BrushLibrary library, ToolSwatches swatches, BrushLibrary.Store store) {
        if (store.get(DONE) != null) return null;
        int[] counts = new int[3];
        String[] was = new String[ROWS.length];
        Brush[] now = new Brush[ROWS.length];
        for (int i = 0; i < ROWS.length; i++) {
            String[] row = ROWS[i];
            Brush old = Brush.read(row[0] + "\t" + row[1]);
            if (old == null) continue;
            was[i] = old.write();
            now[i] = row[3].isEmpty() ? old : Brush.read(row[3] + "\t" + row[1]);
        }
        library.edit(brush -> {
            String line = brush.write();
            for (int i = 0; i < ROWS.length; i++) {
                String[] row = ROWS[i];
                boolean remove = row[2].equals("remove");
                if (was[i] == null || !line.equals(was[i]) && (remove || !line.equals(now[i].write()))) continue;
                if (remove) { counts[1]++; return null; }
                counts[0]++;
                if (!row[4].isEmpty()) {
                    boolean grey = !row[5].isEmpty() && !WildSample.darkGround(now[i].parts);
                    swatches.setLook(now[i], Double.parseDouble(row[4]), grey ? Double.parseDouble(row[5]) : null);
                    counts[2]++;
                }
                return now[i];
            }
            return brush;
        });
        store.put(DONE, "1");
        return counts;
    }

    /** Kept once {@link #sameNames} has run. */
    static final String NAMES_DONE = "brush_library_names";
    /**
     * Once (2026-10-29, the owner: "in the library there are a bunch of duplicated names... can we remove the older
     * versions of those?"): of the library's brushes under one name, case aside, only the newest stays ({@link
     * BrushLibrary#keepNewestNames}); since then a save never adds a second ({@link BrushLibrary#save}). Run at every
     * launch by {@link ToolSlots}, after {@link #run}; how many went, or null when done before.
     */
    static Integer sameNames(BrushLibrary library, BrushLibrary.Store store) {
        if (store.get(NAMES_DONE) != null && store.get(NUMBERED_DONE) != null) return null;
        int gone = 0;
        if (store.get(NAMES_DONE) == null) { gone += library.keepNewestNames(); store.put(NAMES_DONE, "1"); }
        // Then a name's numbered ones go with it, the newest of all staying (the owner, the same day: "yes remove the numbered ones too").
        if (store.get(NUMBERED_DONE) == null) { gone += library.keepNewestNames(true); store.put(NUMBERED_DONE, "1"); }
        return gone;
    }
    /** Kept once the numbered namesakes went too ({@link #sameNames}). */
    static final String NUMBERED_DONE = "brush_library_numbered";

    private LibraryCleanup() { }
}
