/* The end of a stroke shown through the panel's fast update (app/src/main/cpp/panel_end.h) against a fake driver that
 * does what the Manta's does (engine spec, "Lift after the fast update"): it keeps a record of what each pixel shows
 * and drives only the pixels an update asks to change, recording them shown; a fast update leaves what it drives
 * part-driven. After a stroke's end every pixel must show the final, driven there by a gray update after the final was
 * in the plane. Run by scripts/test_inkwash.sh. */
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../../main/cpp/panel_end.h"

enum { W = 96, H = 64, PART = 0x80 };
enum { OLD, END, NEAR };
static uint8_t plane[W * H], record[W * H], shows[W * H];
static int fails, fast_mode, gray_submits, busy, busy_after;

/* The fake driver: an update drives each pixel of its area whose plane value isn't the record. What a pixel shows is
 * the value, or the value | PART after a fast update. Busy (busy updates refused once busy_after have gone), it refuses
 * an update, driving nothing. */
static int submit(void *ctx, int left, int top, int right, int bottom) {
    (void)ctx;
    if (busy > 0 && busy_after-- <= 0) { busy--; return -EAGAIN; }
    for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
        int i = y * W + x;
        if (plane[i] == record[i]) continue;
        record[i] = plane[i];
        shows[i] = (uint8_t)(plane[i] | (fast_mode ? PART : 0));
    }
    if (!fast_mode) gray_submits++;
    return 0;
}
static void check(int ok, const char *what) { if (!ok) { fails++; printf("FAIL: %s\n", what); } }

/* A page of levels as RGBA rows, as a present reads it (level = first byte >> 4). */
static void rgba(uint8_t *out, const uint8_t *levels) {
    for (int i = 0; i < W * H; i++) { uint8_t v = (uint8_t)(levels[i] * 17); out[4*i] = out[4*i+1] = out[4*i+2] = v; out[4*i+3] = 255; }
}

/* One stroke: the page before it (before), what its fast session showed (preview), the page after it (final). Ends it
 * as `how` (OLD: the final copied into the plane, one forced gray update, as before 2026-10-16) and returns how many
 * pixels don't show the final cleanly. */
static int stroke(const uint8_t *before, const uint8_t *preview, const uint8_t *final, int how, int *asked) {
    static uint8_t fast_record[W * H], gray_record[W * H], page[W * H * 4];
    /* Shown cleanly before the stroke, through the gray session. */
    memcpy(plane, before, sizeof plane); memcpy(record, before, sizeof record); memcpy(shows, before, sizeof shows);
    memcpy(gray_record, before, sizeof gray_record); memcpy(fast_record, before, sizeof fast_record);
    /* The fast session: the preview, a band of rows at a time, as the pen goes. */
    fast_mode = 1;
    for (int y0 = 0; y0 < H; y0 += 8) {
        for (int y = y0; y < y0 + 8; y++) for (int x = 0; x < W; x++) plane[y*W+x] = fast_record[y*W+x] = preview[y*W+x];
        submit(NULL, 0, y0, W, y0 + 8);
    }
    fast_mode = 0;
    /* The final rendered, then the end. */
    rgba(page, final);
    if (how == OLD) {
        for (int i = 0; i < W * H; i++) plane[i] = final[i];
        submit(NULL, 0, 0, W, H);
    } else {
        panel_end_report report;
        int r = panel_end(page, W * 4, fast_record, gray_record, W, plane, W, 0, 0, W, H, submit, NULL, how == NEAR, &report);
        check(r >= 0 && report.first >= 0 && report.second >= 0, "the end's updates are submitted");
        if (asked) *asked = report.asked_again;
        check(memcmp(gray_record, final, sizeof gray_record) == 0, "the gray session's record is the final");
    }
    int wrong = 0;
    for (int i = 0; i < W * H; i++) if (shows[i] != final[i]) wrong++;
    check(memcmp(plane, final, sizeof plane) == 0, "the plane holds the final");
    return wrong;
}

int main(void) {
    static uint8_t before[W * H], preview[W * H], final[W * H], other[W * H];
    unsigned seed = 7;
    /* Paper with a few old marks, and a stroke across the middle. */
    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
        int i = y * W + x, inside = x > 10 && x < 86 && y > 12 && y < 52;
        seed = seed * 1103515245u + 12345u;
        before[i] = (x / 7 + y / 5) % 9 == 0 ? 0 : 15;
        /* An opaque paint's preview: its own grey's dots (every other pixel) over what was there. */
        preview[i] = inside ? (uint8_t)(((x + y) & 1) ? 0 : 15) : before[i];
        /* Its final: mostly the preview's dots (a flat paint), some of the paper's tooth, and a little past the preview. */
        final[i] = inside ? ((seed >> 16) % 10 == 0 ? (uint8_t)(15 - preview[i]) : preview[i])
                 : (x > 8 && x < 88 && y > 10 && y < 54 && (seed >> 20) % 3 == 0) ? 0 : before[i];
    }
    int asked = 0, changed = 0;
    for (int i = 0; i < W * H; i++) if (preview[i] != before[i]) changed++;
    int wrong_old = stroke(before, preview, final, OLD, NULL);
    check(wrong_old > 0, "the plain forced gray update leaves pixels the fast update drove as it left them (the bug)");
    check(stroke(before, preview, final, END, &asked) == 0, "after the end every pixel shows the final, driven by a gray update");
    check(asked > 0 && asked < changed, "the pixels a gray update would pass by are asked again, only those");
    check(stroke(before, preview, final, NEAR, &asked) == 0, "and asked again at a level next to the final");
    /* A final that is the preview (a flat stroke): every pixel it changed is asked again. */
    check(stroke(before, preview, preview, END, &asked) == 0 && asked == changed, "a final that is the preview: every pixel it changed driven again");
    /* A final unlike the preview wherever it changed (a light wash's preview): the first update alone shows it. */
    for (int i = 0; i < W * H; i++) other[i] = preview[i] != before[i] ? before[i] : preview[i];
    check(stroke(before, preview, other, END, &asked) == 0 && asked == 0, "a final unlike the preview: nothing asked again");
    /* A busy display: the first update refused submits nothing, records nothing, and the end can be tried again; a
       refused second update is submitted again at once (the first is driving those pixels away from the final). */
    {
        static uint8_t fast_record[W * H], gray_record[W * H], page[W * H * 4];
        memcpy(plane, before, sizeof plane); memcpy(record, before, sizeof record); memcpy(shows, before, sizeof shows);
        memcpy(gray_record, before, sizeof gray_record); memcpy(fast_record, preview, sizeof fast_record);
        fast_mode = 1; memcpy(plane, preview, sizeof plane); submit(NULL, 0, 0, W, H); fast_mode = 0;
        rgba(page, final);
        busy = 1;
        int r = panel_end(page, W * 4, fast_record, gray_record, W, plane, W, 0, 0, W, H, submit, NULL, 0, NULL);
        check(r < 0 && memcmp(gray_record, before, sizeof gray_record) == 0, "refused: the gray record is as before");
        check(memcmp(record, preview, sizeof record) == 0, "refused: the driver drove nothing");
        r = panel_end(page, W * 4, fast_record, gray_record, W, plane, W, 0, 0, W, H, submit, NULL, 0, NULL);
        check(r >= 0 && memcmp(record, final, sizeof record) == 0, "tried again, it ends");
        int clean = 1;
        for (int i = 0; i < W * H; i++) if (shows[i] != final[i]) clean = 0;
        check(clean, "every pixel shows the final");
        /* The second refused twice: submitted again at once, still two updates through. */
        memcpy(record, preview, sizeof record); memcpy(plane, preview, sizeof plane); memcpy(gray_record, before, sizeof gray_record);
        for (int i = 0; i < W * H; i++) shows[i] = (uint8_t)(preview[i] | (preview[i] != before[i] ? PART : 0));
        int calls = gray_submits;
        busy = 2; busy_after = 1;
        r = panel_end(page, W * 4, fast_record, gray_record, W, plane, W, 0, 0, W, H, submit, NULL, 0, NULL);
        clean = 1;
        for (int i = 0; i < W * H; i++) if (shows[i] != final[i]) clean = 0;
        check(r >= 0 && gray_submits == calls + 2 && busy == 0 && clean, "a refused second update goes again at once, and it ends");
        busy = busy_after = 0;
    }
    check(gray_submits > 0, "the end goes through the gray session");
    if (fails) return 1;
    printf("PASS: panel_end (%d px the old end left as the fast update did, none after the end)\n", wrong_old);
    return 0;
}
