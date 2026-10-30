package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tools page's Basic tools (presets, in the code): five ready brushes for each of the 13 rolled styles (not Anything goes), each a style's own roll
 * with a fixed seed (the last column; {@code ModularStyle.roll(new Random(seed))}), renamed, with a short description
 * that fits one line of the narrowest card; the Gravity Lab's four, which move with the page's tilt while wet
 * (Pooling wash, Tilt blend and Sinking grain with Watercolor, Charcoal dust with Charcoal; Drips retired), set at the middle
 * of the Lab's ranges rather than rolled (their last column is their seed); and Stump, Charcoal's paper stump (a
 * smudge that lays no paint of its own), with Blend stump beside it (MonoPaint's: Soften 1); Sumi and Clear water
 * under Ink wash, Float's pair (suminagashi: ink that floats in rings, and clear water that drops clear rings and
 * combs them), and Comb, Clear water with a Rake; Wash comb under Watercolor (a Rake that is all stump); two bristle brushes, set by hand the same
 * way (Bristle brush, an ink that runs dry into streaks, with Dry brush; Hog bristle, thick ridged paint, with Oils);
 * Oil paint with Oils, the oil paint spec's (a Filbert whose paint runs out, Smudge's Paint Load); Hog bristle and Oil
 * paint retired with the old Gouache and Oils ({@link #RETIRED}) when Wet ones replaced them (the wet paint spec: six
 * each, Gouache water and Loaded oils among them), and the old Wet brush when the wet canvas one replaced it (the wet
 * brush spec: MonoPaint's wet brush pen, ink in water), and Watercolor's and Ink wash's washes when wet ones replaced
 * them (the wet paint spec, section 9 and its ink wash note); and
 * Sketch pencil, a hard pencil that erases clean, to draw under ink; Dirty brush and Water brush under Watercolor, the
 * wet smudge spec's (a smear that keeps its paint, Smudge's Clear Water); and the web lab's Stipple pen, Hatch pen, White gel
 * pen (Fineliner), Stipple sponge, Wash behind, Masking fluid and Wax resist (Watercolor), and Mold ink (Ink wash; the lab's Halo ink),
 * set by hand the same way; the everyday pens (Ballpoint, Gel pen, Fountain pen under Fineliner, Highlighter under Marker); the
 * owner's pens, Brush Studio saves kept as made and renamed (Cheap ballpoint and Smooth fountain under Fineliner, Ink wash
 * brush under Brush pen, Permanent marker, Acrylic paint marker and Ms. Sketch (Mr. Sketch until 2026-10-28) under Marker, Another pencil under Pencil;
 * the Brush pen and Marker ones saved as Watercolor, re-labelled, as style changes nothing they draw); and
 * Print's twelve (five Screentone, seven through Print Shading); and eight that show the engine's choices no other
 * preset did (Spirograph, Vanishing ink, Springy brush, Lemon juice, Colouring pen, Damp tissue, Wax crayon, Crayon
 * scratch), Fill in (Colouring pen filling the whole shape, in the paint bucket's place) and Filled shape (its wash
 * filling what it outlines), built dial by dial by {@code PresetPictures}, which prints
 * their rows and draws them. Each erases clean, Erasable 1, written nowhere in its code (2026-10-22; Masking fluid and
 * Wax resist keep theirs, Resist's fluid or wax). The share codes are the presets: a change
 * to how styles roll changes none of them. Pure, checked on the desktop ({@code ToolPresetsChecks}: every one decodes,
 * is its style, fits and draws).
 */
final class ToolPresets {
    /** A preset: its style, the brush (a fresh copy each time), its name and its one-line description. */
    static final class Preset {
        final ModularStyle style;
        final String name, line, code;
        Preset(ModularStyle style, String name, String code, String line) { this.style = style; this.name = name; this.code = code; this.line = line; }
        /** The preset as a brush under its name, fresh: a slot may change it. */
        Brush brush() { return Brush.of(LegacyBrushes.named(new String[]{style.name, name, code})); }
    }

    /** Style, name, share code, description, the seed it was rolled with. */
    static final String[][] ROWS = {
            // Fineliner
            {"Fineliner", "Steady line", "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "even and crisp", "3"},
            {"Fineliner", "Press liner", "press-liner.M00fy2p000000at_000zz0000000000000_0_000_1jz_85t0000000000000000nz0.1t996q7", "presses wider", "4"},
            {"Fineliner", "Quick sketch", "quick-sketch.M00kj4z000000at_000zz8h00000000000_0_000_1jz_86200000000005q0000nz0.1s13pn4", "thins at speed", "2"},
            {"Fineliner", "Toothy line", "toothy-line.M00fb00390000at_000zz7k00000000000_0_13x_1jz_80u0000000000000000nz0.16ebhgk", "on cold press", "8"},
            {"Fineliner", "Lively liner", "lively-liner.M00l700000000at_000zz0000000000000_0_100_1jz_88z0000000000600000nz0.pg5zxt", "swell, grainy", "19"},
            // Stipple and Hatch (the new modules spec), the web lab's presets: dots scattered along the stroke, crisp lines that cross with pressure
            {"Fineliner", "Stipple pen", "stipple-pen.M07i0clcli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.h6q", "pen dots", "22274"},
            {"Fineliner", "Hatch pen", "hatch-pen.M08at7x901zzi0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "crosshatching", "1191"},
            {"Fineliner", "Hand hatch", "hand-hatch-pen.M0ceeat901i0i0p6i0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "hand hatching", "1191"},
            {"Fineliner", "Neat hatch", "neat-hatch.M0ceeat901i05ecli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.x3", "tidy hatching", "1191"},
            // Blend (the new modules spec), the web lab's: a white gel pen, white ink over dark
            {"Fineliner", "White gel pen", "white-gel-pen.M00i000000000at_000zz0000000000000zz1_0_000_1jz_0000000000000000000nz0.1y9", "white on dark", "2529"},
            // Everyday pens, set by hand (PresetPictures builds them): a ballpoint, grainy, darker pressed, now and then skipping;
            // a gel pen, smooth and bold; a fountain pen that swells pressed and pools where it starts
            {"Fineliner", "Ballpoint", "ballpoint.M00at00005e003m_000zz0000000000077_0_177_0_85ei000000000000000nz0.2ez", "grainy, skips", "3131"},
            {"Fineliner", "Gel pen", "gel-pen.M00wd0000000000_000zz5e00000000000_0_000_0_0000000000000000000nz0.2f0", "smooth, bold", "3132"},
            {"Fineliner", "Fountain pen", "fountain-pen.M00i000at00003m_03mzzwe00000000000_0_000_0_8qz0000000000000000nz0.2f1", "pools at start", "3133"},
            // The owner's pens (2026-10-27): Brush Studio saves (docs/brush-lab/favourites.txt), kept as made, renamed
            {"Fineliner", "Cheap ballpoint", "cheap-ballpoint.M00i00000002g4l_000zzi00000000003l_0_000_1jz_8h77a00000000000000nz0.ir1", "blots if slow", "24301"},
            {"Fineliner", "Smooth fountain", "smooth-fountain.M00i00000000h3p_000zz1u00000000000_0_000_1jz_8ag00000000007h0000nz0.ir1", "thins at speed", "24301"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Spiro (a Spirograph), Fading (Vanishing ink).
            // The Spirograph is in Ring, its ring pulled out to size (the owner, 2026-10-25: "impossible to choose a circle size")
            {"Fineliner", "Spirograph", "spirograph.M0bi010gj08huu801i08r_000zz0000000000000_0_000_0_0000000000000000000nz0.2fb", "toy loops", "3143"},
            {"Fineliner", "Vanishing ink", "vanishing-ink.M00i000000000at_000zz0000000000000_0_000_53a_0000000000000000000nz0.2fe", "fades away", "3146"},
            // Stamp (the owner, 2026-10-28: "just create a tool that is a stamp"): the stamp tool's first brush (ToolSlots.firstStamp)
            // at its own seed, a round stamp until Cut Stamp cuts its nib
            {"Fineliner", "Stamp", "stamp.M0agx1r0qe_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.2gd", "prints a shape", "3181"},
            // Brush pen
            {"Brush pen", "Swell brush", "swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_1jz_8wgft00dr0000ey0000nz0.1s13pn4", "presses darker", "2"},
            {"Brush pen", "Chisel brush", "chisel-brush.M11iteei0fxp50000at_000ur0000000000000_0_04n_1jz_8r8dj00000000ah0000nz0.1t996q7", "chisel tip", "4"},
            {"Brush pen", "Tilt brush", "lean-brush.M10i0rsw80000at_000v50000000000000_0_000_1jz_8wded00jl0000ck0000nz0.69opxg", "wider on tilt", "5"},
            // Wet brush (the wet brush spec; the owner, 2026-10-22: "yep go for it"): MonoPaint's wet brush pen on the wet
            // canvas, ink in water held between strokes (Wet on a Wash), replacing the old line and wash (RETIRED)
            {"Brush pen", "Wet brush", "wet-brush.M10i0d2o50000at_1ul0000000000_0_000_7i0ee_8yrfo00000000ny0000nz0.2g3", "ink in water", "3171"},
            {"Brush pen", "Pooling brush", "pooling-brush.M10i0mwmc0000at_000u5d87f000000000_0_000_1jz_8qp0000fh0000he0000nz0.ib8b3u", "pools if slow", "10"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Control's Springy
            {"Brush pen", "Springy brush", "springy-brush.M10ssati000003m_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.2fg", "bouncy swell", "3148"},
            // The owner's pens (2026-10-27): a Brush Studio save, kept as made, renamed; saved as Watercolor, re-labelled
            // Brush pen (style is a label for it: it draws the same)
            {"Brush pen", "Ink wash brush", "ink-wash-brush.M10i06f0000003t_1w85i000s0000zz2_0_d6e_2clcl000_8t00000000000000000nz0.ir1", "wash, behind", "24301"},
            // Dip pen
            {"Dip pen", "Split nib", "split-nib.M24m0okjmi0000y00_000zz0000000000000_0_000_1jz_8lw00wk0000fq000000nz0.51j8ud", "opens on press", "3"},
            // Fude pen (the owner, 2026-10-26), Broad nib until then (its code name and seed kept): set by hand (PresetPictures)
            {"Dip pen", "Fude pen", "broad-nib.M20ss007700003m_000zzat00000000000_0_000_0_8at0000i000ll000000nz01.1s13pn4", "broad on tilt", "2"},
            {"Dip pen", "Flick nib", "flick-nib.M225a9xi02ce60000at_000zzg6bf000000000_0_100_1jz_8sr00lj000000000000nz0.16ebhgk", "spatters", "8"},
            {"Dip pen", "Chisel nib", "chisel-nib.M21oklpi03v000000at_000zzk58o000000000_0_000_1jz_8go00u10000j2000000nz0.ib8b3u", "turns on tilt", "10"},
            {"Dip pen", "Copperplate", "copperplate.M24g4okdci0970000_000zz0000000000000_0_24n_1jz_8hg00q4000000000000nz0.1t996q7", "split, ribbed", "4"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Lemon Juice, faint until it dries
            {"Dip pen", "Lemon juice", "lemon-juice.M20i000at0000at_000zz0000000000000_0_000_6hp_1ss0000000000000000nz0.2ff", "appears later", "3147"},
            // Marker
            {"Marker", "Chisel marker", "chisel-marker.M31j2eei000000000at_000zzdj005z0000000_0_000_1jz_82o00000000005u0000nz0.t4mf73", "pools if slow", "1"},
            {"Marker", "Round marker", "round-marker.M30i000000000at_000zzde00000000000_0_071_1jz_83j00000000008b0000nz0.1ifv2mx", "thins at speed", "9"},
            {"Marker", "Bleed marker", "bleed-marker.M30i000000000at_05ezzq500000000000_0_300_1jz_83u00000000004w0000nz0.1t996q7", "wet, on felt", "4"},
            {"Marker", "Felt marker", "felt-marker.M31j2eei000000000at_000zzbq00920000000_0_300_1jz_84u0000000000400000nz0.jjds6x", "felt, chisel", "12"},
            {"Marker", "Wet chisel", "wet-chisel.M31smeei000000000at_03mzzc700000000000_0_000_1jz_82h00000000003c0000nz0.1uhentb", "line and wash", "14"},
            // A highlighter (set by hand, PresetPictures): a broad chisel of wash that goes behind the lines (Blend Mode's Behind)
            {"Marker", "Highlighter", "highlighter.M31e1zzp60000000000_1007700000000zz2_0_000_0_0000000000000000000nz0.2f2", "behind marks", "3134"},
            // The owner's pens (2026-10-27): Brush Studio saves, kept as made, renamed; saved as Watercolor, re-labelled
            // Marker (style is a label for them: they draw the same)
            {"Marker", "Permanent marker", "permanent-marker.M30i0231u10003i_1hx1l1k420000_0_d1g_2clcl0js_81x0000000000000000dk0.ir1", "slow drying", "24301"},
            {"Marker", "Acrylic paint marker", "acrylic-paint-marker.M31uomci00000000000_2zz00_0_000_1jz_00000000000000000001r0.ir1", "crisp, opaque", "24301"},
            {"Marker", "Ms. Sketch", "mr-sketch.M31pfzzi00000001n37_2y200_0_000_1jz_0000000000000000000nz0.ir1", "almost opaque", "24301"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Stay in Lines, a wash that keeps inside the ink lines
            {"Marker", "Colouring pen", "colouring-pen.M30wd00000000at_1atat00000000_5cl_000_0_0000000000000000000nz0.2fc", "stays in lines", "3144"},
            // Fill in (the owner, 2026-10-23: "maybe this app doesn't use a bucket, just a brush with fill in the lines"): the
            // Colouring pen at Flood 1, so a few scribbles inside a shape fill it to its lines; the paint bucket's work
            {"Marker", "Fill in", "fill-in.M30wd00000000at_1atat00000000_5zz_000_0_0000000000000000000nz0.2fh", "fills shapes", "3149"},
            // Filled shape (the owner, 2026-10-23: "draw a filled shape"): the Colouring pen's flat wash as Filled Shape, so
            // an outline drawn closes at the lift and fills in its own wash
            {"Marker", "Filled shape", "filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi", "fills outlines", "3150"},
            // Pencil
            {"Pencil", "2H pencil", "h-pencil.M40i000000000at_48n00_0_2s8_1jz_87nge00000000000000nz000000000530000005000c099zz.51j8ud", "hard, ribbed", "3"},
            {"Pencil", "HB pencil", "hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00000000000000nz000000000530000005000c099zz.pg5zxt", "on cold press", "19"},
            {"Pencil", "B pencil", "b-pencil.M40i0007r0000at_4g100_0_1rf_1jz_89lrc00000000000000nz000000000530000005000c099zz.vkxdda", "grainy", "13"},
            {"Pencil", "2B pencil", "b-pencil.M40i0006b0000at_4j700_0_3j5_1jz_8fng400000000000000nz000000000530000005000c099zz.2l8ao6", "soft, on felt", "23"},
            {"Pencil", "5B pencil", "b-pencil.M40i03u000000at_4sh00_0_2m5_1jz_8h4mv00000000000000nz000000000530000005000c099zz.1pksrgx", "darkest", "22"},
            // Erasable: a hard pencil that erases clean, to draw under ink (Graphite H, smooth paper, Press dark)
            {"Pencil", "Sketch pencil", "sketch-pencil.M40i000000000at_4at00_0_000_1jz_25ess00000000000000nz000000000530000005000c099zz.inw", "erases clean", "24188"},
            {"Pencil", "Loose hatch", "loose-hatch.M4ceeat901p6weuli0_4at00_0_000_1jz_25ess00000000000000nz0.inw", "loose lines", "24188"},
            // The owner's pens (2026-10-27): a Brush Studio save, kept as made, renamed; retuned 2026-10-28 as their toolbar's first (ToolSlots.FIRST)
            {"Pencil", "Another pencil", "another-pencil.M40i000000000at_4ip2c_0_di0_1jz_800000000zzzz000000zz000000000530000005000c099zz.inw", "soft, toothy", "24301"},
            // Charcoal
            {"Charcoal", "Messy stick", "messy-stick.M51eueei000005bd4at_3nbcw_0_2tz_1jz_8syjs00000000000000nz000000000530000005000c099zz.t4mf73", "dusty edges", "1"},
            {"Charcoal", "Compressed", "compressed.M51wseei09z000000at_36lnd_0_1z9_1jz_8rpfm00000000000000nz000000000530000005000c099zz.1s13pn4", "fuzzy, grainy", "2"},
            {"Charcoal", "Crisp charcoal", "crisp-charcoal.M51yseei0000000c3at_315bs_0_1v3_1jz_8kvkd00000000000000nz000000000530000005000c099zz.69opxg", "wavers a bit", "5"},
            {"Charcoal", "Soft charcoal", "soft-charcoal.M50i00000000yat_3eqlj_0_1nl_1jz_8pthh00000000000000nz000000000530000005000c099zz.51j8ud", "round, fuzzy", "3"},
            {"Charcoal", "Dark stick", "dark-stick.M51useei06x7mc800at_3lylx_0_2xk_1jz_8lcm100000000000000nz000000000530000005000c099zz.15660dh", "presses darker", "6"},
            // Smudge: a paper stump, Soft charcoal's roll with Smear, Own paint 0 (Pick up .7, Lay down .6): smears, lays nothing
            // MonoPaint's blending stump (the Stump spec), first: the smudge tool starts with it: the Stump at Soften 1, Pick up .8: softens marks in place, pulling them the way it rubs (Blending stump is too wide for Brush Lab's bar)
            {"Charcoal", "Blend stump", "blend-stump.M50i00000000yat_3eqlj_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "softens marks", "3"},
            {"Charcoal", "Stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "smudges only", "3"},
            {"Charcoal", "Charcoal dust", "charcoal-dust.M51r7eei000007700at_3at90vb_0_1qz_1jz_8p6g700000000000000nz0000i0000530000005000c099zz.2yl8n", "sheds dust", "4973063"},
            // Watercolor
            // Wet watercolour (the wet paint spec, section 9; the owner, 2026-10-22: "i think maybe 1"): the Slow washes' tips,
            // media, papers and Control on the wet canvas, so washes painted while wet merge, and dry back with a dark edge,
            // blooms and granulation from their Wash's and Paper's dials; the old ones RETIRED
            {"Watercolor", "Soft wash", "soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73", "even, pale", "1761283695"},
            {"Watercolor", "Wet blooms", "wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_7js_8mx0000000000000000nz0.1s13pn4", "blooms", "3871688080"},
            {"Watercolor", "Backrun", "backrun.M60i000000000gv_1t10000c8gj00_0_13f_7_8sl0000000000000000nz0.ib8b3u", "pale backruns", "1107254586"},
            {"Watercolor", "Granulating", "granulating.M60i000000000ac_1uk0000fh0000_0_186_7_8me0000000000000000nz0.1t996q7", "grainy blooms", "3945846607"},
            {"Watercolor", "Slow bloom", "slow-bloom.M60i000000000i6_1yh0000fc2a00_0_1g7_7ss_8j90000000000000000nz0.vkxdda", "slow blooms", "1909600750"},
            {"Watercolor", "Pooling wash", "pooling-wash.M60i000000000g7_1x3at00770000_0_190_7qzll_8g70000000000000000nz0.373k3", "runs downhill", "5370051"},
            {"Watercolor", "Tilt blend", "tilt-blend.M60i000000000js_1y6at00900000_0_05e_7ulee_8g70000000000000000nz0.4t449", "rock to blend", "8076825"},
            {"Watercolor", "Sinking grain", "sinking-grain.M60i000000000cl_1x37700770000_0_1qz_7p65e_8g70000000000000000nz0.146as", "granulates", "1874404"},
            // Stipple: a sponge print in a flat wash
            {"Watercolor", "Stipple sponge", "stipple-sponge.M67g7eewei0_1cl0000770000_0_000_1jz_0000000000000000000nz0.h6p", "sponged dots", "22273"},
            // Blend, the web lab's: a wash that goes only where the page is still blank, behind the lines
            {"Watercolor", "Wash behind", "wash-behind.M60i000000000at_1ulat00770000zz2_0_000_1jz_0000000000000000000nz0.11kx", "behind marks", "48705"},
            // Wash comb (the Stump spec): a five-line Rake that is all stump (Smear, Own paint 0, Soften 1, Pick up 1): combs a wash into streaks
            {"Watercolor", "Wash comb", "wash-comb.M65zzlli000000000_1ulat00770000_3zzll00zz_000_1jz_0000000000000000000nz0.7", "combs washes", "7"},
            // The wet smudge spec's two smudge brushes (no paint of their own): Dirty brush, a Smear that keeps what it picks
            // up for the next stroke (Keeps Paint .9; Pick up .75, Lay down .5), and Water brush, Smudge's Clear Water
            // (Pick up .5, Lay down .6), Slow 5 s with Flow While Wet .6, Runs Downhill .4 and Pools Downhill .3
            {"Watercolor", "Dirty brush", "dirty-brush.M60i000000000at_1ulat00770000_3qzi00000we_000_1jz_0000000000000000000nz0.20j", "keeps paint", "2611"},
            {"Watercolor", "Water brush", "water-brush.M60i000000000at_1ulat00770000_4i0ll_000_2g7ll0jsee00at_0000000000000000000nz0.20k", "re-wets paint", "2612"},
            // Resist, the web lab's: masking fluid, which the eraser lifts, and wax on cold press, which stays
            {"Watercolor", "Masking fluid", "masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px", "saves whites", "933"},
            {"Watercolor", "Wax resist", "wax-resist.M60i00000cl00at_500_0_1i0_1jz_0000000000000000000nz0.py", "grainy resist", "934"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Lift, a wash laid only behind (on blank paper) that soaks up what it passes
            {"Watercolor", "Damp tissue", "damp-tissue.M60i000000000ll_1atat00000000_4i000_000_0_0000000000000000000nz0.2fd", "lifts paint", "3145"},
            // Ink wash
            // Wet ink wash (the wet paint spec, section 9's ink wash note; the owner, 2026-10-22: "less dark where they overlap,
            // especially when wet"): the five washes' tips, media, papers and Control on the wet canvas, merging wet into wet
            // as the watercolour washes do, a pen line kept crisp beneath; the old ones RETIRED
            {"Ink wash", "Line and wash", "line-and-wash.M70i07pmj0000wd_0pipk0000ke0000000_0_000_7_8ls0000000000000000nz0.1t996q7", "pen and wash", "3945846607"},
            {"Ink wash", "Wet ink", "wet-ink.M70i000000000xb_0h9pe9c00nl0000000_0_000_7_8o50000000000000000nz0.51j8ud", "feathered line", "304908421"},
            {"Ink wash", "Grey wash", "grey-wash.M70i000000000po_1ls00j4000000_0_000_7_8q90000000000000000nz0.69opxg", "feathers", "379066948"},
            {"Ink wash", "Flat wash", "flat-wash.M70i08u860000vx_1gv00jp860000_0_000_7_8re0000000000000000nz0.demerg", "flat, even", "810620476"},
            {"Ink wash", "Slow ink wash", "slow-ink-wash.M70i000nt0000q5_0ts000000cs0000000_0_000_7ss_8qi0000000000000000nz0.1s13pn4", "slow-drying", "3871688080"},
            // Float (suminagashi): ink that floats on water, and Clear water (Smear, Own paint 0, Pick up .9) to alternate with it
            {"Ink wash", "Sumi", "sumi.M70i000000000at_000zz0000000000000_0_000_3lli0i0_25ess00000000000000nz0.15t", "floating rings", "1505"},
            {"Ink wash", "Clear water", "clear-water.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_25ess00000000000000nz0.12ay", "clear rings", "49642"},
            // Comb (the Stump spec): Clear water with a five-line Rake, to comb what floats (suminagashi combing)
            {"Ink wash", "Comb", "comb.M75zzlli000000000_000zz0000000000000_3wejs00_000_3lli0i0_25ess00000000000000nz0.12ay", "combs rings", "49642"},
            // Mold (Halo), the web lab's Halo ink by its display name: an ink line whose veins bridge to nearby marks
            // Its Mold Growth at full and Drying 5.5 s (the lab's 6.6 s; the owner, 2026-10-22: "mold is very faint / light and slow").
            {"Ink wash", "Mold ink", "mold-ink.M70i077at0000at_000zz0000000000000_0_000_4hsjszz_25ess00000000000000nz0.wx", "grows veins", "1185"},
            // Gouache (the wet paint spec; the owner's decision, 2026-10-22: Wet, replacing the old ones, RETIRED): opaque
            // paint laid into MonoPaint's wet canvas, mixing and staying wet until dried; Gouache water lifts it
            {"Gouache", "Wet gouache", "wet-gouache.M80b70000000054_2zz00_0_000_7_83z0000000000000000nz0.2fj", "mixes wet", "3151"},
            {"Gouache", "Runny gouache", "runny-gouache.M80b70000000054_2zz00_0_000_7ulll_83z0000000000000000nz0.2fk", "runs downhill", "3152"},
            {"Gouache", "Wet flat", "wet-flat.M825aeeh2000000000j_2zz00_0_000_7_83p0000000000000000nz0.2fl", "flat, wet", "3153"},
            {"Gouache", "Gouache hake", "gouache-hake.M83h30zz0065_2zz00_0_000_7_80g0000000000000000nz0.2fm", "broad, wet", "3154"},
            {"Gouache", "Tacky gouache", "tacky-gouache.M80g30000000043_2zz00_0_000_75e_86d0000000000000000nz0.2fn", "barely mixes", "3155"},
            {"Gouache", "Gouache water", "gouache-water.M80b70000000054_2zz00_4i0ll_000_7llat_83z0000000000000000nz0.2fo", "lifts paint", "3156"},
            // Oils, Wet too: they drag what they cross and get dirtier as they go; Loaded oils runs out (Paint Load)
            {"Oils", "Wet oils", "wet-oils.M90i0000000003k_2zz00_2llp6zz_000_7ne_8d30000ty0000000000nz0.2ft", "drags, mixes", "3161"},
            {"Oils", "Loaded oils", "loaded-oils.M99vleei0000000005e_2zz00_2llp6zz00js_000_7ne_1ss0000000000000000nz0.2fu", "runs out", "3162"},
            {"Oils", "Flat oils", "flat-oils.M925aeei0000000004h_2zz00_2llp6zz_000_7ne_8fh0000k30000000000nz0.2fv", "flat, drags", "3163"},
            {"Oils", "Broad oils", "broad-oils.M93i00zz0034_2zz00_2llp6zz_000_7ne_8j30000gw0000000000nz0.2fw", "broad, drags", "3164"},
            {"Oils", "Tilt oils", "tilt-oils.M90i00000000020_2zz00_0_000_7ll_8kp0000ht0000000000nz0.2fx", "tilt to widen", "3165"},
            {"Oils", "Oil glaze", "oil-glaze.M90i0000000003k_2js00_0_000_7ne_8d30000ty0000000000nz0.2fy", "thin, mixes", "3166"},
            // Pastels
            {"Pastels", "Chisel pastel", "chisel-pastel.Ma1j2eei000008c00at_34z5w_0_2sl_1jz_89i0000000000000000nz000000000530000005000c099zz.t4mf73", "on ribbed", "1"},
            {"Pastels", "Soft pastel", "soft-pastel.Ma0i000007s00at_3b98o_0_3nj_1jz_8hg0000000000000000nz000000000530000005000c099zz.51j8ud", "on felt", "3"},
            {"Pastels", "Crumpled", "crumpled.Ma1r1eei000008y00at_3c678_0_4n6_1jz_8hv0000000000000000nz000000000530000005000c099zz.1h7plju", "on crumpled", "7"},
            {"Pastels", "Crisp pastel", "crisp-pastel.Ma1vueei000005100at_343dr_0_2x2_1jz_8kv0000000000000000nz000000000530000005000c099zz.15660dh", "crisp edges", "6"},
            {"Pastels", "Side pastel", "side-pastel.Ma0i000009r00at_34w8o_0_1ls_1jz_8hl0000000000000000nz000000000530000005000c099zz.1ifv2mx", "lighter tilted", "9"},
            // The engine's choices no preset showed (the owner's list, 2026-10-22; PresetPictures builds them): Crayon, and Crayon with Scratch on
            {"Pastels", "Wax crayon", "wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9", "waxy layers", "3141"},
            {"Pastels", "Crayon scratch", "crayon-scratch.Ma0p600000000at_7ll1_0_1i0_0_0000000000000000000nz0.2fa", "scratches out", "3142"},
            // Dry brush
            {"Dry brush", "Dry round", "dry-round.Mb0i00000in0074_2k700_0_2r6_1jz_8ny0000000000000000nz0.t4mf73", "opaque, ribbed", "1"},
            {"Dry brush", "Scumble", "scumble.Mb0i00000xd000d_2oi00_0_4f9_1jz_8ce0000000000000000nz0.1s13pn4", "on crumpled", "2"},
            {"Dry brush", "Flat dry", "flat-dry.Mb25aeei00000sg0067_2n200_0_2d8_1jz_8l10000000000000000nz0.51j8ud", "flat, ribbed", "3"},
            {"Dry brush", "Dry glaze", "dry-glaze.Mb25aeei00000lx0033_1caat00770000_0_1l5_1jz_8l40000000000000000nz0.1t996q7", "glaze", "4"},
            {"Dry brush", "Grainy wash", "grainy-wash.Mb0i00000vq0026_10rat00770000_0_1p6_1jz_8i70000000000000000nz0.15660dh", "rough, flat", "6"},
            {"Dry brush", "Bristle brush", "bristle-brush.Mb6rolljsqzi0_000zz0000000000000_0_1ll_1jz_8i00000000000000000nz0.209", "runs dry", "2601"},
            // Hake
            {"Hake", "Wet hake", "wet-hake.Mc3i00zzhf59_1orat00770000_0_000_1jz_85b0000000000000000nz0.t4mf73", "streaky wash", "1"},
            {"Hake", "Rough hake", "rough-hake.Mc3i01zzkh8h_1vyat00770000_0_1i0_1jz_8c30000000000000000nz0.1s13pn4", "on cold press", "2"},
            // Cover hake: the owner's Brush Lab save of it (2026-10-28), in place of its roll (cover-hake.Mc3i00zzedct_2lu00_0_000_1jz_89d0000000000000000nz0.1t996q7, "semi-opaque")
            {"Hake", "Cover hake", "cover-hake.Mc3i00mb00e8_2t52w_0_000_2clcl0js_80000zz000000000000zz0.1t996q7", "covers, flat", "4"},
            {"Hake", "Flat hake", "flat-hake.Mc3i01zz9a7e_1dxat00770000_0_1i0_1jz_88w0000000000000000nz0.1ifv2mx", "flat, even", "9"},
            {"Hake", "Big sky", "big-sky.Mc3i00zz896c_1sbat00770000_0_000_1jz_86n0000000000000000nz0.1uhentb", "smooth wash", "14"},
            // Print, set by hand (PresetPictures builds them): Screentone's five patterns, then ink and dry media through Print
            // Shading (Ben-Day, Rosette, Halftone; Mezzotint, Dither, Engraving, Wavy Lines)
            {"Print", "Dot tone", "dot-tone.Me0i00000000000_60e0_0_000_0_0000000000000000000nz0.2e5", "even dots", "3101"},
            {"Print", "Line tone", "line-tone.Me0i00000000000_61_0_000_0_0000000000000000000nz0.2e6", "fine lines", "3102"},
            {"Print", "Sand tone", "sand-tone.Me0i00000000000_62d9_0_000_0_0000000000000000000nz0.2e7", "speckled grain", "3103"},
            {"Print", "Gradient tone", "gradient-tone.Me0i00000000000_63kub2_0_000_0_0000000000000000000nz0.2e8", "fades down", "3104"},
            {"Print", "Hatched tone", "hatched-tone.Me0i00000000000_64qidu_0_000_0_0000000000000000000nz0.2e9", "crossed lines", "3105"},
            {"Print", "Ben-Day brush", "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef", "comic dots", "3111"},
            {"Print", "Rosette ink", "rosette-ink.Me0zz77at0000ss_0p6000000000000000zz05gm_0_000_0_3p6ll00000000000000nz0.2eg", "rosette dots", "3112"},
            {"Print", "Halftone brush", "halftone-brush.Me0zzati00000ss_0p6000000000000000zz01b2i00090_0_000_0_3p6ll00000000000000nz0.2eh", "dots swell", "3113"},
            {"Print", "Mezzotint", "mezzotint.Me0i000000000ee_390g700zz09b2c000005e_0_1i0_0_5ee0000ssi000000000nz0.2ep", "velvet dashes", "3121"},
            {"Print", "Dither pencil", "dither-pencil.Me0ll00000000at_4zz00zz03_0_1ee_0_3p6ll00000000000000nz0.2eq", "pixel shading", "3122"},
            {"Print", "Engraving pen", "engraving-pen.Me0wdatee0000ll_0p6000000000000000zz028bc0_0_000_0_3p6ll00000000000000nz0.2er", "engraved lines", "3123"},
            {"Print", "Wavy-line pen", "wavy-line-pen.Me0wdatee0000ll_0p6000000000000000zz08b200_0_000_0_3p6ll00000000000000nz0.2es", "banknote style", "3124"},
    };

    /**
     * The Gouache and Oils presets before Wet replaced them (the owner, 2026-10-22), and the old Wet brush (Brush pen) the
     * wet canvas one replaced the same day (the wet brush spec): no longer on the Tools page, kept so
     * their codes stay known (a slot or the library holding one keeps its brush, now shown as a brush of its own) and
     * so the checks that drew them still can. The same columns as {@link #ROWS}.
     */
    static final String[][] RETIRED = {
            // Ink wash: the washes before wet ones replaced them (2026-10-22)
            {"Ink wash", "Line and wash", "line-and-wash.M70i07pmj0000wd_0pipk0000ke0000000_0_000_1jz_8ls0000000000000000nz0.1t996q7", "pen and wash", "4"},
            {"Ink wash", "Wet ink", "wet-ink.M70i000000000xb_0h9pe9c00nl0000000_0_000_1jz_8o50000000000000000nz0.51j8ud", "feathered line", "3"},
            {"Ink wash", "Grey wash", "grey-wash.M70i000000000po_1ls00j4000000_0_000_1jz_8q90000000000000000nz0.69opxg", "feathers", "5"},
            {"Ink wash", "Flat wash", "flat-wash.M70i08u860000vx_1gv00jp860000_0_000_1jz_8re0000000000000000nz0.demerg", "flat, even", "18"},
            {"Ink wash", "Slow ink wash", "slow-ink-wash.M70i000nt0000q5_0ts000000cs0000000_0_000_2picl0js_8qi0000000000000000nz0.1s13pn4", "slow-drying", "2"},
            // Watercolor: the Slow washes before wet ones replaced them (2026-10-22)
            {"Watercolor", "Soft wash", "soft-wash.M60i0000000007q_1wva200000000_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "even, pale", "1"},
            {"Watercolor", "Wet blooms", "wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_1jz_8mx0000000000000000nz0.1s13pn4", "blooms", "2"},
            {"Watercolor", "Backrun", "backrun.M60i000000000gv_1t10000c8gj00_0_13f_1jz_8sl0000000000000000nz0.ib8b3u", "pale backruns", "10"},
            {"Watercolor", "Granulating", "granular.M60i000000000ac_1uk0000fh0000_0_186_1jz_8me0000000000000000nz0.1t996q7", "grainy blooms", "4"},
            {"Watercolor", "Slow bloom", "slow-bloom.M60i000000000i6_1yh0000fc2a00_0_1g7_2i7cl0js_8j90000000000000000nz0.vkxdda", "slow-drying", "13"},
            {"Watercolor", "Pooling wash", "pooling-wash.M60i000000000g7_1x3at00770000_0_190_2s29q0jsi000ul64_8g70000000000000000nz0.373k3", "runs downhill", "5370051"},
            {"Watercolor", "Tilt blend", "tilt-blend.M60i000000000js_1y6at00900000_0_05e_2voat0js5e006400x3_8g70000000000000000nz0.4t449", "rock to blend", "8076825"},
            {"Watercolor", "Sinking grain", "sinking-grain.M60i000000000cl_1x37700770000_0_1qz_2tii00js5e0000x3_8g70000000000000000nz0.146as", "granulates", "1874404"},
            // Gravity's Drips, off the Tools page (the owner, 2026-10-22: "i don't love the drips... too heavy handed"); its
            // Drips dial stays in the Brush Lab
            {"Watercolor", "Drips", "drips.M60i000000000at_1y67700770000_0_05e_2ti7x0jsclllat_8g70000000000000000nz0.1rcs7", "drips, tilted", "2955895"},
            // Brush pen: the old Wet brush, a Fast-wet line and wash, before the wet canvas one replaced it (2026-10-22)
            {"Brush pen", "Wet brush", "wet-brush.M10i0d2o54w00at_09ls2009s000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk", "line and wash", "8"},
            // Gouache
            {"Gouache", "Opaque round", "opaque-round.M80b70000000054_2zz00_0_000_1jz_83z0000000000000000nz0.t4mf73", "flat, opaque", "1"},
            {"Gouache", "Wide gouache", "wide-gouache.M83h30zz0065_2zz00_0_100_1jz_80g0000000000000000nz0.51j8ud", "hake, rough", "3"},
            {"Gouache", "Flat gouache", "flat-gouache.M825aeeh2000000000j_2zz00_0_000_1jz_83p0000000000000000nz0.1uhentb", "flat nib", "14"},
            {"Gouache", "Swell gouache", "swell-gouache.M80g30000000043_2zz00_0_05c_1jz_86d0000000000000000nz0.1h7plju", "presses wider", "7"},
            {"Gouache", "Felt gouache", "felt-gouache.M80g3000000002i_2zz00_0_300_1jz_8490000000000000000nz0.1qsy8k0", "on felt paper", "24"},
            // Oils
            {"Oils", "Impasto", "impasto.M90i00000il003k_2zzh9_0_500_1jz_8d30000ty0000000000nz0.t4mf73", "thick ridges", "1"},
            {"Oils", "Oil hake", "oil-hake.M93i00zzrf34_2zzfl_0_500_1jz_8j30000gw0000000000nz0.51j8ud", "streaky ridges", "3"},
            {"Oils", "Flat bristle", "flat-bristle.M925aeei00000xa004h_2zzkm_0_500_1jz_8fh0000k30000000000nz0.69opxg", "flat nib", "5"},
            {"Oils", "Dry oils", "dry-oils.M90i00000px0040_2zziy_0_519_1jz_8kt0000000000000000nz0.1h7plju", "dry brush", "7"},
            {"Oils", "Side oils", "lean-oils.M90i00000sv0020_2zzug_0_59x_1jz_8kp0000ht0000000000nz0.ib8b3u", "tilt to widen", "10"},
            // Bristles: hairs that split and rejoin, splaying as you press
            {"Oils", "Hog bristle", "hog-bristle.M96x7i0lli0i0p6_2zzll_0_5i0_1jz_8i00000000000000000nz0.20a", "ridged strands", "2602"},
            // Oil paint (the oil paint spec, MonoPaint's oil paint mode): a Filbert of opaque oil paint whose Drag has Paint
            // Load .55 at the Starting Load's .25: it lays its grey solidly, runs out along the stroke, then only drags
            // what is there; a rub on a grey loads it. Press Wide, so a firm stroke spends faster
            {"Oils", "Oil paint", "oil-paint.M99vleei0000090005e_2zza3_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l", "runs out", "2613"},
    };

    private static final List<Preset> ALL, RETIRED_ALL;
    static {
        List<Preset> all = new ArrayList<>();
        for (String[] row : ROWS) all.add(new Preset(ModularStyle.named(row[0]), row[1], row[2], row[3]));
        ALL = Collections.unmodifiableList(all);
        List<Preset> retired = new ArrayList<>();
        for (String[] row : RETIRED) retired.add(new Preset(ModularStyle.named(row[0]), row[1], row[2], row[3]));
        RETIRED_ALL = Collections.unmodifiableList(retired);
    }
    /** Each preset by its brush's {@link Brush#identity}, made the first time it is asked. */
    private static Map<String, Preset> byIdentity;

    private ToolPresets() { }

    /** The styles the page's chips offer, in their order: every style but Anything goes. */
    static List<ModularStyle> styles() {
        List<ModularStyle> styles = new ArrayList<>();
        for (ModularStyle s : ModularStyle.shown()) if (!s.wild) styles.add(s);
        return styles;
    }
    /** Every preset, style by style. */
    static List<Preset> all() { return ALL; }
    /** The presets Wet replaced ({@link #RETIRED}), not offered. */
    static List<Preset> retired() { return RETIRED_ALL; }
    /** Every preset, then the retired ones: for finding one by name wherever it was a preset. */
    static List<Preset> withRetired() { List<Preset> all = new ArrayList<>(ALL); all.addAll(RETIRED_ALL); return all; }
    /** The preset {@code brush} is (the same name and settings), or null. */
    static synchronized Preset find(Brush brush) {
        if (byIdentity == null) {
            Map<String, Preset> map = new HashMap<>();
            for (Preset p : ALL) map.put(p.brush().identity(), p);
            byIdentity = map;
        }
        return byIdentity.get(brush.identity());
    }
    /**
     * The preset {@code brush} came from, or null (a brush of its own: a Brush Lab roll or variation, a new seed). A brush
     * keeps no "from": its preset is the one it is ({@link #find}); else the one of its style and seed, which Brush Lab's
     * dials and a rename leave as they were ("Wax crayon 3", a saved copy with its grain changed), its name in any case (the owner's "another pencil"), less a copy's
     * number, picking among presets sharing a seed (Sketch pencil and Loose hatch); else the one so named.
     */
    static synchronized Preset origin(Brush brush) {
        Preset same = find(brush);
        if (same != null) return same;
        if (bySeed == null) {
            Map<String, List<Preset>> map = new HashMap<>();
            for (Preset p : ALL) { ModularBrush b = p.brush().parts; map.computeIfAbsent(b.style.index + "/" + b.seed, k -> new ArrayList<>()).add(p); }
            bySeed = map;
        }
        // Ms. Sketch was Mr. Sketch until 2026-10-28: a brush kept under the old name still finds it.
        String name = brush.name().replaceAll(" \\d+$", "").replace("Mr. Sketch", "Ms. Sketch");
        List<Preset> seeded = bySeed.get(brush.parts.style.index + "/" + brush.parts.seed);
        if (seeded != null && seeded.size() == 1) return seeded.get(0);
        if (seeded != null) for (Preset p : seeded) if (p.name.equalsIgnoreCase(name)) return p;
        for (Preset p : ALL) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }
    private static Map<String, List<Preset>> bySeed;
    /** {@code style}'s presets; every preset for null (All). */
    static List<Preset> of(ModularStyle style) {
        if (style == null) return ALL;
        List<Preset> some = new ArrayList<>();
        for (Preset p : ALL) if (p.style == style) some.add(p);
        return some;
    }
}
