/* Compares the native renderer (app/src/main/cpp/compose.h) with the Java one, from the
 * page and renders ComposeFixture.java wrote. Run by scripts/test_inkwash.sh. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../../main/cpp/compose.h"

static FILE *in;
static int32_t next(void) {
    unsigned char b[4];
    if (fread(b, 1, 4, in) != 4) { fprintf(stderr, "FAIL: fixture ends early\n"); exit(1); }
    return (int32_t)((uint32_t)b[0] << 24 | (uint32_t)b[1] << 16 | (uint32_t)b[2] << 8 | b[3]);
}

int main(int argc, char **argv) {
    if (argc != 2 || !(in = fopen(argv[1], "rb"))) { fprintf(stderr, "usage: compose_check fixture\n"); return 1; }
    int width = next(), height = next(), count = next(), paper = next();
    int32_t opacity[8];
    const uint8_t *tones[8], *alpha[8];
    for (int i = 0; i < count; i++) opacity[i] = next();
    for (int i = 0; i < count; i++) {
        uint8_t *t = malloc((size_t)width * height), *a = malloc((size_t)width * height);
        if (fread(t, 1, (size_t)width * height, in) != (size_t)width * height
                || fread(a, 1, (size_t)width * height, in) != (size_t)width * height) { fprintf(stderr, "FAIL: short layer\n"); return 1; }
        tones[i] = t; alpha[i] = a;
    }
    int32_t *dots = malloc(256 * 64 * sizeof(int32_t));
    for (int i = 0; i < 256 * 64; i++) dots[i] = next();
    uint32_t *page = malloc((size_t)width * height * 4);
    int areas = next();
    for (int n = 0; n < areas; n++) {
        int left = next(), top = next(), right = next(), bottom = next();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < width * height; i++) page[i] = 0x12345678u;
            compose_region((uint8_t *)page, (size_t)width * 4, (size_t)width, count, tones, alpha, opacity, (unsigned)paper,
                    pass == 0 ? dots : NULL, left, top, right, bottom);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int inside = x >= left && x < right && y >= top && y < bottom;
                uint32_t expected = inside ? (uint32_t)next() : 0x12345678u;
                if (!inside) continue;
                if (page[y * width + x] != expected) {
                    fprintf(stderr, "FAIL: %s pixel %d,%d is %08x, Java renders %08x\n", pass ? "plain" : "dotted", x, y, page[y * width + x], expected);
                    return 1;
                }
            }
            for (int i = 0; i < width * height; i++) {
                int x = i % width, y = i / width;
                if (!(x >= left && x < right && y >= top && y < bottom) && page[i] != 0x12345678u) {
                    fprintf(stderr, "FAIL: pixel %d,%d outside the area was written\n", x, y); return 1;
                }
            }
        }
    }
    printf("PASS: the native renderer gives the Java renderer's dots and greys on paper %d, only inside its area\n", paper);
    return 0;
}
