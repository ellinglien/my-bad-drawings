#ifndef MONOPAINT_COMPOSE_H
#define MONOPAINT_COMPOSE_H

#include <stddef.h>
#include <stdint.h>

/* Composites logical layers, bottom to top, onto paper of tone paper (255: white) for the pixels in
 * left..right, top..bottom (right and bottom exclusive) of a page width pixels wide,
 * by ToneDocument.compositeTone's arithmetic; then, when pattern is not NULL, turns
 * each tone into its screen dot (DotPattern.pixel, 64 entries per tone). Otherwise
 * writes the plain grey. Pure C without JNI, so a desktop check compares it with
 * the Java renderer (app/src/test/cpp/compose_check.c). */
static inline void compose_region(uint8_t *pixels, size_t stride, size_t width, int count,
        const uint8_t *const *tones, const uint8_t *const *alpha, const int32_t *opacity, unsigned paper,
        const int32_t *pattern, int left, int top, int right, int bottom) {
    const uint32_t ground = 0xff000000u | (paper & 255) * 0x010101u;
    for (int y = top; y < bottom; y++) {
        uint32_t *out = (uint32_t *)(pixels + (size_t)y * stride);
        for (int x = left; x < right; x++) out[x] = ground;
        for (int layer = 0; layer < count; layer++) {
            const uint8_t *t = tones[layer] + (size_t)y * width, *a = alpha[layer] + (size_t)y * width;
            for (int x = left; x < right; x++) {
                unsigned coverage = (a[x] * (unsigned)opacity[layer] + 50) / 100;
                unsigned shade = (t[x] * coverage + (out[x] & 255) * (255 - coverage) + 127) / 255;
                out[x] = 0xff000000u | shade * 0x010101u;
            }
        }
        if (pattern) for (int x = left; x < right; x++)
            out[x] = (uint32_t)pattern[(out[x] & 255) * 64 + (y & 7) * 8 + (x & 7)];
    }
}

#endif
