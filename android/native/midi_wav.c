/*
 * What Pulsekit's plugin hosts (clap_render.c, lv2_render.c) share: a MIDI file read into timed
 * events (its tempo changes followed), WAVs read and written.
 */
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "midi_wav.h"

void pk_say(char *err, int len, const char *fmt, ...) {
  if (!err || len <= 0) return;
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(err, (size_t)len, fmt, ap);
  va_end(ap);
}

#define say pk_say

/* ------------------------------------------------------------ the MIDI file */


static void add_event(song_t *s, double sec, const uint8_t *b, int len) {
  if (s->n == s->cap) {
    s->cap = s->cap ? s->cap * 2 : 256;
    s->ev = (midi_ev_t *)realloc(s->ev, sizeof(midi_ev_t) * (size_t)s->cap);
  }
  midi_ev_t *e = &s->ev[s->n++];
  e->sec = sec;
  e->len = len;
  memcpy(e->b, b, (size_t)len);
  if (sec > s->length) s->length = sec;
}

static uint32_t var_len(const uint8_t *d, int end, int *p) {
  uint32_t v = 0;
  while (*p < end) {
    uint8_t c = d[(*p)++];
    v = (v << 7) | (c & 0x7F);
    if (!(c & 0x80)) break;
  }
  return v;
}

typedef struct {
  uint64_t tick;
  double us_per_beat;
} tempo_t;

static int by_time(const void *a, const void *b) {
  double x = ((const midi_ev_t *)a)->sec, y = ((const midi_ev_t *)b)->sec;
  if (x != y) return x < y ? -1 : 1;
  /* At one time: note-offs before note-ons. */
  int xo = (((const midi_ev_t *)a)->b[0] & 0xF0) == 0x80, yo = (((const midi_ev_t *)b)->b[0] & 0xF0) == 0x80;
  return yo - xo;
}

/* Seconds at a tick through the tempo changes (sorted). */
static double seconds_at(const tempo_t *t, int nt, uint64_t tick, int division) {
  double sec = 0, us = 500000;
  uint64_t last = 0;
  for (int i = 0; i < nt && t[i].tick <= tick; i++) {
    sec += (double)(t[i].tick - last) * us / division / 1e6;
    last = t[i].tick;
    us = t[i].us_per_beat;
  }
  return sec + (double)(tick - last) * us / division / 1e6;
}

int pk_read_song(const char *path, song_t *s, char *err, int len) {
  memset(s, 0, sizeof(*s));
  FILE *f = fopen(path, "rb");
  if (!f) {
    say(err, len, "could not open the MIDI file");
    return 0;
  }
  fseek(f, 0, SEEK_END);
  long size = ftell(f);
  fseek(f, 0, SEEK_SET);
  uint8_t *d = (uint8_t *)malloc((size_t)size);
  if (fread(d, 1, (size_t)size, f) != (size_t)size) size = 0;
  fclose(f);
  if (size < 14 || memcmp(d, "MThd", 4) != 0) {
    free(d);
    say(err, len, "not a MIDI file");
    return 0;
  }
  int division = (d[12] << 8) | d[13];
  if (division <= 0 || (division & 0x8000)) {
    free(d);
    say(err, len, "MIDI files timed in SMPTE frames are not read");
    return 0;
  }
  /* Two passes: the tempo changes of every track first, then the events timed with them. */
  tempo_t *tempos = NULL;
  int nt = 0, ct = 0;
  for (int pass = 0; pass < 2; pass++) {
    int p = 8 + (int)((d[4] << 24) | (d[5] << 16) | (d[6] << 8) | d[7]);
    while (p + 8 <= size) {
      int tlen = (int)((d[p + 4] << 24) | (d[p + 5] << 16) | (d[p + 6] << 8) | d[p + 7]);
      int q = p + 8, end = q + tlen > size ? (int)size : q + tlen;
      int is_track = memcmp(d + p, "MTrk", 4) == 0;
      p = q + tlen;
      if (!is_track) continue;
      uint64_t tick = 0;
      uint8_t status = 0;
      while (q < end) {
        tick += var_len(d, end, &q);
        if (q >= end) break;
        uint8_t c = d[q];
        if (c == 0xFF) {
          if (q + 2 > end) break;
          uint8_t type = d[q + 1];
          q += 2;
          uint32_t ml = var_len(d, end, &q);
          if (type == 0x51 && ml == 3 && pass == 0 && q + 3 <= end) {
            if (nt == ct) {
              ct = ct ? ct * 2 : 16;
              tempos = (tempo_t *)realloc(tempos, sizeof(tempo_t) * (size_t)ct);
            }
            tempos[nt].tick = tick;
            tempos[nt].us_per_beat = (double)((d[q] << 16) | (d[q + 1] << 8) | d[q + 2]);
            nt++;
          }
          q += (int)ml;
          continue;
        }
        if (c == 0xF0 || c == 0xF7) {
          q++;
          q += (int)var_len(d, end, &q);
          continue;
        }
        if (c & 0x80) {
          status = c;
          q++;
        }
        if (!status) break;
        int need = ((status & 0xF0) == 0xC0 || (status & 0xF0) == 0xD0) ? 1 : 2;
        if (q + need > end) break;
        uint8_t b[3] = {status, d[q], need == 2 ? d[q + 1] : 0};
        q += need;
        if (pass == 1) {
          /* A note-on at velocity 0 is a note-off. */
          if ((b[0] & 0xF0) == 0x90 && b[2] == 0) b[0] = (uint8_t)(0x80 | (b[0] & 0x0F));
          add_event(s, seconds_at(tempos, nt, tick, division), b, need + 1);
        }
      }
    }
    if (pass == 0 && nt > 1) {
      /* Sorted by tick (tracks can hold them in any order). */
      for (int i = 1; i < nt; i++)
        for (int j = i; j > 0 && tempos[j - 1].tick > tempos[j].tick; j--) {
          tempo_t x = tempos[j];
          tempos[j] = tempos[j - 1];
          tempos[j - 1] = x;
        }
    }
  }
  free(tempos);
  free(d);
  qsort(s->ev, (size_t)s->n, sizeof(midi_ev_t), by_time);
  return 1;
}

/* ------------------------------------------------------------ WAVs */


static void put16(FILE *f, int v) { fputc(v & 0xFF, f); fputc((v >> 8) & 0xFF, f); }
static void put32(FILE *f, uint32_t v) { put16(f, (int)(v & 0xFFFF)); put16(f, (int)(v >> 16)); }

int pk_write_wav(const char *path, const float *lr, uint64_t frames, int rate, char *err, int len) {
  FILE *f = fopen(path, "wb");
  if (!f) {
    say(err, len, "could not write %s", path);
    return 0;
  }
  uint32_t bytes = (uint32_t)(frames * 4);
  fwrite("RIFF", 1, 4, f);
  put32(f, 36 + bytes);
  fwrite("WAVEfmt ", 1, 8, f);
  put32(f, 16);
  put16(f, 1);
  put16(f, 2);
  put32(f, (uint32_t)rate);
  put32(f, (uint32_t)rate * 4);
  put16(f, 4);
  put16(f, 16);
  fwrite("data", 1, 4, f);
  put32(f, bytes);
  for (uint64_t i = 0; i < frames * 2; i++) {
    float v = lr[i];
    if (v > 1) v = 1;
    if (v < -1) v = -1;
    put16(f, (int)lrintf(v * 32767.0f));
  }
  fclose(f);
  return 1;
}

static uint32_t le32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24); }

int pk_read_wav(const char *path, pk_wav_t *w, char *err, int len) {
  memset(w, 0, sizeof(*w));
  FILE *f = fopen(path, "rb");
  if (!f) {
    say(err, len, "could not open the WAV file");
    return 0;
  }
  fseek(f, 0, SEEK_END);
  long size = ftell(f);
  fseek(f, 0, SEEK_SET);
  uint8_t *d = (uint8_t *)malloc((size_t)size);
  if (fread(d, 1, (size_t)size, f) != (size_t)size) size = 0;
  fclose(f);
  if (size < 44 || memcmp(d, "RIFF", 4) != 0 || memcmp(d + 8, "WAVE", 4) != 0) {
    free(d);
    say(err, len, "not a WAV file");
    return 0;
  }
  int format = 0, channels = 0, bits = 0;
  long p = 12;
  const uint8_t *data = NULL;
  uint32_t data_len = 0;
  while (p + 8 <= size) {
    uint32_t n = le32(d + p + 4);
    if (memcmp(d + p, "fmt ", 4) == 0 && p + 24 <= size) {
      format = d[p + 8] | (d[p + 9] << 8);
      channels = d[p + 10] | (d[p + 11] << 8);
      w->rate = (int)le32(d + p + 12);
      bits = d[p + 22] | (d[p + 23] << 8);
      /* WAVE_FORMAT_EXTENSIBLE: the sub-format's first two bytes. */
      if (format == 0xFFFE && n >= 26) format = d[p + 32] | (d[p + 33] << 8);
    } else if (memcmp(d + p, "data", 4) == 0) {
      data = d + p + 8;
      data_len = (uint32_t)((long)n > size - p - 8 ? size - p - 8 : n);
      break;
    }
    p += 8 + n + (n & 1);
  }
  int bytes = bits / 8;
  if (!data || channels < 1 || (!(format == 1 && (bits == 16 || bits == 24 || bits == 32)) && !(format == 3 && bits == 32))) {
    free(d);
    say(err, len, "a WAV Pulsekit does not read (16, 24 or 32-bit PCM, or 32-bit float)");
    return 0;
  }
  w->frames = data_len / (uint32_t)(bytes * channels);
  w->lr = (float *)calloc((size_t)w->frames * 2, sizeof(float));
  for (uint64_t i = 0; i < w->frames; i++) {
    for (int c = 0; c < 2; c++) {
      const uint8_t *s = data + (i * (uint64_t)channels + (uint64_t)(c < channels ? c : 0)) * (uint64_t)bytes;
      float v;
      if (format == 3) {
        uint32_t u = le32(s);
        memcpy(&v, &u, 4);
      } else if (bits == 16) {
        v = (int16_t)(s[0] | (s[1] << 8)) / 32768.0f;
      } else if (bits == 24) {
        int32_t x = (int32_t)((uint32_t)s[0] << 8 | (uint32_t)s[1] << 16 | (uint32_t)s[2] << 24) >> 8;
        v = x / 8388608.0f;
      } else {
        v = (int32_t)le32(s) / 2147483648.0f;
      }
      w->lr[i * 2 + (uint64_t)c] = v;
    }
  }
  free(d);
  return 1;
}
