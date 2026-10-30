// The end of a pen stroke shown through the panel's fast update (StrokePanel; on the bench only since 2026-10-16): its
// last pixels go to the panel through the gray session so that the driver drives every pixel the fast session changed,
// not only those whose value changes.
//
// Why (Manta, firmware 2606141001; engine spec, "Lift after the fast update"): the driver keeps a record of what each
// pixel shows and drives a pixel only where an update asks for another value, forced or not. A fast (mode 5) update
// records its pixels as shown, yet a run of them a few ms apart (a quick stroke's preview) leaves pixels part-driven.
// Where the stroke's final value is the one the preview showed (a flat opaque paint, a solid line: 99% of a Gouache
// stroke's pixels), a gray update asks for what the record already holds and the driver passes them by: they keep the
// fast update's ghosts until the system's full refresh.
//
// So the end is two gray updates back to back. First the whole area as the final, except those pixels, which ask for
// what the page showed before the stroke (the gray session's record, untouched while the fast session ran) or, `near`,
// a level next to the final: the driver's record there now differs from the final. Then those pixels as the final. The
// driver runs the two one after the other (the panel's frames: about 42 after the end, not 21), so the stroke blinks
// for about a quarter second after the lift with the page before it; `near` is untried on the panel.
//
// Pure C over plain buffers (no JNI), so the desktop check (app/src/test/cpp/panel_end_check.c) runs this code against
// a fake driver.
#ifndef PANEL_END_H
#define PANEL_END_H
#include <stddef.h>
#include <stdint.h>

// Submits {left, top, right, bottom} (canvas pixels) as a gray update; returns the driver's result (< 0 on failure).
typedef int (*panel_submit)(void *ctx, int left, int top, int right, int bottom);

// What an end did: the pixels asked again, and the two submits' results.
typedef struct { int asked_again, first, second; } panel_end_report;

// One pixel's case: the fast session changed it (its record differs from the gray one) and the final keeps what it
// showed. Those are the pixels a plain gray update would pass by.
static inline int panel_end_ask_again(uint8_t fast, uint8_t gray, uint8_t final) { return fast != gray && final == fast; }

// Ends the fast session's showing over {left, top, right, bottom}. final: RGBA rows (final_stride bytes), its level the
// first byte >> 4, as every present reads it; fast, gray: the sessions' records (record_width levels a row); plane: the
// panel's plane at the canvas' origin (plane_stride bytes a row). Returns the second submit's result, or the first's if
// it failed (then nothing is submitted: the caller tries again). The gray record becomes the final once both succeed.
static int panel_end(const uint8_t *final, size_t final_stride, const uint8_t *fast, uint8_t *gray, int record_width,
                     uint8_t *plane, size_t plane_stride, int left, int top, int right, int bottom,
                     panel_submit submit, void *ctx, int near, panel_end_report *report) {
    int asked = 0, l = right, t = bottom, r = left, b = top;
    for (int y = top; y < bottom; y++) {
        const uint8_t *src = final + (size_t)y * final_stride + (size_t)left * 4;
        const uint8_t *a = fast + (size_t)y * record_width + left, *g = gray + (size_t)y * record_width + left;
        uint8_t *dst = plane + (size_t)y * plane_stride + left;
        for (int x = left; x < right; x++, src += 4, a++, g++, dst++) {
            uint8_t f = src[0] >> 4;
            if (panel_end_ask_again(*a, *g, f)) {
                *dst = near ? (uint8_t)(f > 0 ? f - 1 : 1) : *g;
                asked++;
                if (x < l) l = x;
                if (x + 1 > r) r = x + 1;
                if (y < t) t = y;
                if (y + 1 > b) b = y + 1;
            } else *dst = f;
        }
    }
    __sync_synchronize();
    int first = submit(ctx, left, top, right, bottom), second = 0;
    if (first >= 0 && asked > 0) {
        for (int y = t; y < b; y++) {
            const uint8_t *src = final + (size_t)y * final_stride + (size_t)l * 4;
            const uint8_t *a = fast + (size_t)y * record_width + l, *g = gray + (size_t)y * record_width + l;
            uint8_t *dst = plane + (size_t)y * plane_stride + l;
            for (int x = l; x < r; x++, src += 4, a++, g++, dst++) {
                uint8_t f = src[0] >> 4;
                if (panel_end_ask_again(*a, *g, f)) *dst = f;
            }
        }
        __sync_synchronize();
        // The first is driving those pixels away from the final: the second must go, however busy the display.
        second = submit(ctx, l, t, r, b);
        for (int tries = 0; second < 0 && tries < 200; tries++) second = submit(ctx, l, t, r, b);
    }
    if (first >= 0 && second >= 0)
        for (int y = top; y < bottom; y++) {
            const uint8_t *src = final + (size_t)y * final_stride + (size_t)left * 4;
            uint8_t *g = gray + (size_t)y * record_width + left;
            for (int x = left; x < right; x++, src += 4) *g++ = src[0] >> 4;
        }
    if (report) { report->asked_again = asked; report->first = first; report->second = second; }
    return first < 0 ? first : second;
}
#endif
