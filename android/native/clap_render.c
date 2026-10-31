/*
 * Pulsekit's CLAP host: a .clap plugin (built for this system) plays a MIDI file, offline, into a
 * 16-bit stereo WAV. No plugin window: the plugin plays with its own defaults. Built for Android
 * (libpulsekitclap.so, called through clap_jni.c) and for Linux to test it (test_main.c).
 */
#include <dlfcn.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "clap/clap.h"
#include "clap_render.h"

#define BLOCK 512

static void say(char *err, int len, const char *fmt, ...) {
  if (!err || len <= 0) return;
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(err, (size_t)len, fmt, ap);
  va_end(ap);
}

/* ------------------------------------------------------------ host */

static const void *host_get_extension(const clap_host_t *h, const char *id) { (void)h; (void)id; return NULL; }
static void host_request_restart(const clap_host_t *h) { (void)h; }
static void host_request_process(const clap_host_t *h) { (void)h; }
static void host_request_callback(const clap_host_t *h) { (void)h; }

static const clap_host_t HOST = {
  CLAP_VERSION_INIT, NULL, "Pulsekit", "Pulsekit", "", "1.0",
  host_get_extension, host_request_restart, host_request_process, host_request_callback,
};

/* ------------------------------------------------------------ the library */

typedef struct {
  void *lib;
  const clap_plugin_entry_t *entry;
  const clap_plugin_factory_t *factory;
} library_t;

static int open_library(library_t *l, const char *path, char *err, int len) {
  memset(l, 0, sizeof(*l));
  l->lib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
  if (!l->lib) {
    say(err, len, "could not open the plugin: %s", dlerror());
    return 0;
  }
  l->entry = (const clap_plugin_entry_t *)dlsym(l->lib, "clap_entry");
  if (!l->entry) {
    say(err, len, "not a CLAP plugin (no clap_entry)");
    dlclose(l->lib);
    return 0;
  }
  if (!clap_version_is_compatible(l->entry->clap_version)) {
    say(err, len, "its CLAP version %u.%u is not one this host plays", l->entry->clap_version.major, l->entry->clap_version.minor);
    dlclose(l->lib);
    return 0;
  }
  if (!l->entry->init(path)) {
    say(err, len, "the plugin did not start (clap_entry init)");
    dlclose(l->lib);
    return 0;
  }
  l->factory = (const clap_plugin_factory_t *)l->entry->get_factory(CLAP_PLUGIN_FACTORY_ID);
  if (!l->factory || l->factory->get_plugin_count(l->factory) == 0) {
    say(err, len, "the file has no plugins");
    l->entry->deinit();
    dlclose(l->lib);
    return 0;
  }
  return 1;
}

static void close_library(library_t *l) {
  if (l->entry) l->entry->deinit();
  if (l->lib) dlclose(l->lib);
}

int pk_clap_list(const char *path, char *out, int out_len, char *err, int err_len) {
  library_t l;
  if (!open_library(&l, path, err, err_len)) return -1;
  uint32_t n = l.factory->get_plugin_count(l.factory);
  int at = 0;
  if (out_len > 0) out[0] = 0;
  for (uint32_t i = 0; i < n; i++) {
    const clap_plugin_descriptor_t *d = l.factory->get_plugin_descriptor(l.factory, i);
    if (!d) continue;
    int w = snprintf(out + at, (size_t)(out_len > at ? out_len - at : 0), "%s\t%s\t%s\n", d->id ? d->id : "", d->name ? d->name : "", d->vendor ? d->vendor : "");
    if (w > 0) at += w;
    if (at >= out_len) break;
  }
  close_library(&l);
  return (int)n;
}

/* ------------------------------------------------------------ the MIDI file */

typedef struct {
  double sec;
  uint8_t b[3];
  int len;
} midi_ev_t;

typedef struct {
  midi_ev_t *ev;
  int n, cap;
  double length;
} song_t;

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

static int read_song(const char *path, song_t *s, char *err, int len) {
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

/* ------------------------------------------------------------ events for a block */

typedef struct {
  union {
    clap_event_note_t note;
    clap_event_midi_t midi;
  } u;
} any_event_t;

typedef struct {
  any_event_t *ev;
  int n;
} block_events_t;

static uint32_t in_size(const clap_input_events_t *list) { return (uint32_t)((const block_events_t *)list->ctx)->n; }

static const clap_event_header_t *in_get(const clap_input_events_t *list, uint32_t i) {
  const block_events_t *b = (const block_events_t *)list->ctx;
  return i < (uint32_t)b->n ? &b->ev[i].u.note.header : NULL;
}

static bool out_push(const clap_output_events_t *list, const clap_event_header_t *e) { (void)list; (void)e; return true; }

/* ------------------------------------------------------------ the WAV */

static void put16(FILE *f, int v) { fputc(v & 0xFF, f); fputc((v >> 8) & 0xFF, f); }
static void put32(FILE *f, uint32_t v) { put16(f, (int)(v & 0xFFFF)); put16(f, (int)(v >> 16)); }

static int write_wav(const char *path, const float *lr, uint64_t frames, int rate, char *err, int len) {
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

/* ------------------------------------------------------------ rendering */

int pk_clap_render(const char *path, const char *plugin_id, const char *midi_path, const char *wav_path, int rate, double tail, double volume,
                   char *info, int info_len) {
  library_t l;
  if (!open_library(&l, path, info, info_len)) return 0;
  const clap_plugin_descriptor_t *desc = NULL;
  uint32_t count = l.factory->get_plugin_count(l.factory);
  for (uint32_t i = 0; i < count; i++) {
    const clap_plugin_descriptor_t *d = l.factory->get_plugin_descriptor(l.factory, i);
    if (d && (!plugin_id || !plugin_id[0] || (d->id && strcmp(d->id, plugin_id) == 0))) {
      desc = d;
      break;
    }
  }
  if (!desc) {
    say(info, info_len, "the file has no plugin %s", plugin_id);
    close_library(&l);
    return 0;
  }
  song_t song;
  if (!read_song(midi_path, &song, info, info_len)) {
    close_library(&l);
    return 0;
  }
  const clap_plugin_t *p = l.factory->create_plugin(l.factory, &HOST, desc->id);
  if (!p || !p->init(p)) {
    say(info, info_len, "%s could not be made", desc->name);
    if (p) p->destroy(p);
    free(song.ev);
    close_library(&l);
    return 0;
  }
  /* Its main output's channels; how it takes notes (CLAP note events, or MIDI). */
  uint32_t out_ch = 2;
  const clap_plugin_audio_ports_t *ap = (const clap_plugin_audio_ports_t *)p->get_extension(p, CLAP_EXT_AUDIO_PORTS);
  uint32_t n_out = 0, n_in = 0;
  if (ap) {
    n_out = ap->count(p, false);
    n_in = ap->count(p, true);
    clap_audio_port_info_t pi;
    if (n_out > 0 && ap->get(p, 0, false, &pi)) out_ch = pi.channel_count ? pi.channel_count : 2;
  }
  if (n_out == 0) n_out = 1;
  int use_midi = 0;
  const clap_plugin_note_ports_t *np = (const clap_plugin_note_ports_t *)p->get_extension(p, CLAP_EXT_NOTE_PORTS);
  if (np && np->count(p, true) > 0) {
    clap_note_port_info_t ni;
    if (np->get(p, 0, true, &ni)) use_midi = !(ni.supported_dialects & CLAP_NOTE_DIALECT_CLAP) && (ni.supported_dialects & CLAP_NOTE_DIALECT_MIDI);
  } else if (!np) {
    say(info, info_len, "%s takes no notes (an effect, not an instrument)", desc->name);
  }
  if (!p->activate(p, (double)rate, 1, BLOCK)) {
    say(info, info_len, "%s did not start at %d Hz", desc->name, rate);
    p->destroy(p);
    free(song.ev);
    close_library(&l);
    return 0;
  }
  p->start_processing(p);
  uint64_t frames = (uint64_t)((song.length + tail) * rate) + 1;
  float *lr = (float *)calloc((size_t)frames * 2, sizeof(float));
  /* Every output port's channels (the first one kept), inputs silent. */
  float **chans = (float **)calloc(n_out * (out_ch > 2 ? out_ch : 2) + 2, sizeof(float *));
  clap_audio_buffer_t *outs = (clap_audio_buffer_t *)calloc(n_out, sizeof(clap_audio_buffer_t));
  float *pool = (float *)calloc((size_t)BLOCK * (n_out * (out_ch > 2 ? out_ch : 2) + 2), sizeof(float));
  int at = 0;
  for (uint32_t o = 0; o < n_out; o++) {
    uint32_t ch = out_ch;
    if (o > 0 && ap) {
      clap_audio_port_info_t pi;
      ch = ap->get(p, o, false, &pi) && pi.channel_count ? pi.channel_count : 2;
    }
    outs[o].data32 = chans + at;
    outs[o].channel_count = ch;
    for (uint32_t c = 0; c < ch; c++, at++) chans[at] = pool + (size_t)BLOCK * (size_t)at;
  }
  clap_audio_buffer_t *ins = n_in ? (clap_audio_buffer_t *)calloc(n_in, sizeof(clap_audio_buffer_t)) : NULL;
  float *silent = (float *)calloc(BLOCK * 2, sizeof(float));
  float *silent_ch[2] = {silent, silent + BLOCK};
  for (uint32_t i = 0; i < n_in; i++) {
    ins[i].data32 = silent_ch;
    ins[i].channel_count = 2;
  }
  any_event_t *evs = (any_event_t *)calloc((size_t)song.n + 1, sizeof(any_event_t));
  block_events_t be = {evs, 0};
  clap_input_events_t in_events = {&be, in_size, in_get};
  clap_output_events_t out_events = {NULL, out_push};
  int next = 0, ok = 1;
  float peak = 0;
  for (uint64_t start = 0; start < frames; start += BLOCK) {
    uint32_t n = (uint32_t)(frames - start < BLOCK ? frames - start : BLOCK);
    be.n = 0;
    while (next < song.n && (uint64_t)(song.ev[next].sec * rate) < start + n) {
      const midi_ev_t *m = &song.ev[next++];
      uint32_t when = (uint32_t)((uint64_t)(m->sec * rate) - start);
      if ((uint64_t)(m->sec * rate) < start) when = 0;
      any_event_t *e = &evs[be.n];
      memset(e, 0, sizeof(*e));
      int kind = m->b[0] & 0xF0;
      if (!use_midi && (kind == 0x90 || kind == 0x80)) {
        e->u.note.header.size = sizeof(clap_event_note_t);
        e->u.note.header.time = when;
        e->u.note.header.space_id = CLAP_CORE_EVENT_SPACE_ID;
        e->u.note.header.type = kind == 0x90 ? CLAP_EVENT_NOTE_ON : CLAP_EVENT_NOTE_OFF;
        e->u.note.note_id = -1;
        e->u.note.port_index = 0;
        e->u.note.channel = (int16_t)(m->b[0] & 0x0F);
        e->u.note.key = (int16_t)m->b[1];
        e->u.note.velocity = m->b[2] / 127.0;
      } else if (use_midi) {
        e->u.midi.header.size = sizeof(clap_event_midi_t);
        e->u.midi.header.time = when;
        e->u.midi.header.space_id = CLAP_CORE_EVENT_SPACE_ID;
        e->u.midi.header.type = CLAP_EVENT_MIDI;
        e->u.midi.port_index = 0;
        memcpy(e->u.midi.data, m->b, 3);
      } else {
        continue;
      }
      be.n++;
    }
    for (uint32_t o = 0; o < n_out; o++)
      for (uint32_t c = 0; c < outs[o].channel_count; c++) memset(outs[o].data32[c], 0, sizeof(float) * BLOCK);
    clap_process_t pr;
    memset(&pr, 0, sizeof(pr));
    pr.steady_time = (int64_t)start;
    pr.frames_count = n;
    pr.audio_inputs = ins;
    pr.audio_inputs_count = n_in;
    pr.audio_outputs = outs;
    pr.audio_outputs_count = n_out;
    pr.in_events = &in_events;
    pr.out_events = &out_events;
    if (p->process(p, &pr) == CLAP_PROCESS_ERROR) {
      say(info, info_len, "%s stopped with an error at %.1f s", desc->name, (double)start / rate);
      ok = 0;
      break;
    }
    float *L = outs[0].data32[0], *R = outs[0].channel_count > 1 ? outs[0].data32[1] : outs[0].data32[0];
    for (uint32_t i = 0; i < n; i++) {
      float a = (float)(L[i] * volume), b = (float)(R[i] * volume);
      lr[(start + i) * 2] = a;
      lr[(start + i) * 2 + 1] = b;
      if (fabsf(a) > peak) peak = fabsf(a);
      if (fabsf(b) > peak) peak = fabsf(b);
    }
  }
  p->stop_processing(p);
  p->deactivate(p);
  p->destroy(p);
  if (ok) {
    ok = write_wav(wav_path, lr, frames, rate, info, info_len);
    if (ok)
      say(info, info_len, "%s played %d MIDI events into %.1f s (%s, peak %.0f%%%s)", desc->name, song.n, (double)frames / rate,
          use_midi ? "MIDI" : "CLAP notes", peak * 100, peak > 1 ? ", clipped: lower the volume" : peak < 0.0001f ? ", silent" : "");
  }
  free(evs);
  free(silent);
  free(ins);
  free(pool);
  free(outs);
  free(chans);
  free(lr);
  free(song.ev);
  close_library(&l);
  return ok;
}
