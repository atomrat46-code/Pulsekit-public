/*
 * Pulsekit's LV2 host: one plugin of an LV2 bundle (its ports described by the bundle's .ttl files,
 * read in Java: Lv2Bundle) plays a MIDI file (an instrument) or a WAV (an effect), offline, into a
 * 16-bit stereo WAV. No plugin window: its controls keep their defaults or the values given. The
 * libraries a plugin needs that are not the system's (an aap-lv2 APK's libc++_shared.so, ...) are
 * loaded first from `libdir`. Built for Android (through host_jni.c) and for Linux to test it.
 */
#include <dlfcn.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lv2/atom/atom.h"
#include "lv2/atom/util.h"
#include "lv2/buf-size/buf-size.h"
#include "lv2/core/lv2.h"
#include "lv2/log/log.h"
#include "lv2/midi/midi.h"
#include "lv2/options/options.h"
#include "lv2/urid/urid.h"
#include "lv2_render.h"
#include "midi_wav.h"

#define say pk_say
#define BLOCK 512
#define ATOM_CAPACITY 16384

/* ------------------------------------------------------------ URIDs */

typedef struct {
  char **uris;
  int n, cap;
} urids_t;

static LV2_URID map_uri(LV2_URID_Map_Handle h, const char *uri) {
  urids_t *u = (urids_t *)h;
  for (int i = 0; i < u->n; i++)
    if (strcmp(u->uris[i], uri) == 0) return (LV2_URID)(i + 1);
  if (u->n == u->cap) {
    u->cap = u->cap ? u->cap * 2 : 64;
    u->uris = (char **)realloc(u->uris, sizeof(char *) * (size_t)u->cap);
  }
  u->uris[u->n] = strdup(uri);
  return (LV2_URID)++u->n;
}

static const char *unmap_uri(LV2_URID_Unmap_Handle h, LV2_URID id) {
  urids_t *u = (urids_t *)h;
  return id >= 1 && (int)id <= u->n ? u->uris[id - 1] : NULL;
}

static int log_printf(LV2_Log_Handle h, LV2_URID type, const char *fmt, ...) {
  (void)h;
  (void)type;
  va_list ap;
  va_start(ap, fmt);
  int n = vfprintf(stderr, fmt, ap);
  va_end(ap);
  return n;
}

static int log_vprintf(LV2_Log_Handle h, LV2_URID type, const char *fmt, va_list ap) {
  (void)h;
  (void)type;
  return vfprintf(stderr, fmt, ap);
}

/* ------------------------------------------------------------ libraries */

/* The library, the ones it needs from libdir loaded first (found by what the loader says is missing). */
static void *load_with_deps(const char *path, const char *libdir, int depth, char *err, int len) {
  for (int tries = 0; tries < 24; tries++) {
    void *lib = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (lib) return lib;
    const char *e = dlerror();
    char missing[256] = "";
    /* Android: library "libfoo.so" not found; Linux: libfoo.so: cannot open shared object file. */
    const char *q = e ? strstr(e, "library \"") : NULL;
    if (q) {
      q += 9;
      const char *end = strchr(q, '"');
      if (end && end - q < (long)sizeof(missing)) memcpy(missing, q, (size_t)(end - q)), missing[end - q] = 0;
    } else if (e && strstr(e, ": cannot open shared object file")) {
      const char *end = strstr(e, ": cannot open shared object file");
      const char *start = end;
      while (start > e && start[-1] != ' ' && start[-1] != ':') start--;
      if (end - start < (long)sizeof(missing)) memcpy(missing, start, (size_t)(end - start)), missing[end - start] = 0;
    }
    char dep[1024];
    snprintf(dep, sizeof(dep), "%s/%s", libdir ? libdir : "", missing);
    FILE *there = missing[0] && libdir && depth < 8 ? fopen(dep, "rb") : NULL;
    if (!there) {
      say(err, len, "could not load %s: %s", strrchr(path, '/') ? strrchr(path, '/') + 1 : path, e ? e : "?");
      return NULL;
    }
    fclose(there);
    if (!load_with_deps(dep, libdir, depth + 1, err, len)) return NULL;
  }
  say(err, len, "could not load %s (its libraries go round in circles)", path);
  return NULL;
}

/* ------------------------------------------------------------ rendering */

int pk_lv2_render(const char *binary, const char *uri, const char *bundle, const char *libdir, int nports, const int *kinds, const int *outs,
                  const float *values, const char *midi_path, const char *wav_in, const char *wav_out, int rate, double tail, double volume,
                  char *info, int info_len) {
  song_t song;
  memset(&song, 0, sizeof(song));
  pk_wav_t in;
  memset(&in, 0, sizeof(in));
  int audio_in = 0, audio_out = 0, atom_in = -1;
  for (int i = 0; i < nports; i++) {
    if (kinds[i] == PK_LV2_AUDIO && outs[i]) audio_out++;
    if (kinds[i] == PK_LV2_AUDIO && !outs[i]) audio_in++;
    if (kinds[i] == PK_LV2_ATOM && !outs[i] && atom_in < 0) atom_in = i;
  }
  if (audio_out == 0) {
    say(info, info_len, "the plugin has no audio output");
    return 0;
  }
  if (midi_path && midi_path[0]) {
    if (atom_in < 0) {
      say(info, info_len, "the plugin takes no MIDI (an effect: give it a WAV)");
      return 0;
    }
    if (!pk_read_song(midi_path, &song, info, info_len)) return 0;
  } else if (wav_in && wav_in[0]) {
    if (audio_in == 0) {
      say(info, info_len, "the plugin takes no audio (an instrument: give it MIDI)");
      return 0;
    }
    if (!pk_read_wav(wav_in, &in, info, info_len)) return 0;
    rate = in.rate;
  } else {
    say(info, info_len, "give a MIDI file or a WAV");
    return 0;
  }
  void *lib = load_with_deps(binary, libdir, 0, info, info_len);
  if (!lib) {
    free(song.ev);
    free(in.lr);
    return 0;
  }
  LV2_Descriptor_Function get = (LV2_Descriptor_Function)dlsym(lib, "lv2_descriptor");
  const LV2_Descriptor *desc = NULL;
  for (uint32_t i = 0; get && (desc = get(i)) != NULL; i++)
    if (desc->URI && strcmp(desc->URI, uri) == 0) break;
  if (!desc) {
    say(info, info_len, "%s has no plugin %s", strrchr(binary, '/') ? strrchr(binary, '/') + 1 : binary, uri);
    dlclose(lib);
    free(song.ev);
    free(in.lr);
    return 0;
  }
  /* The host's features: URIDs, options (block lengths, rate), bounded blocks, a log. */
  urids_t urids = {NULL, 0, 0};
  LV2_URID_Map map = {&urids, map_uri};
  LV2_URID_Unmap unmap = {&urids, unmap_uri};
  LV2_Log_Log log = {NULL, log_printf, log_vprintf};
  int32_t block = BLOCK, min_block = 1;
  float srate = (float)rate;
  LV2_URID atom_int = map_uri(&urids, LV2_ATOM__Int), atom_float = map_uri(&urids, LV2_ATOM__Float);
  LV2_Options_Option options[] = {
    {LV2_OPTIONS_INSTANCE, 0, map_uri(&urids, LV2_BUF_SIZE__maxBlockLength), sizeof(int32_t), atom_int, &block},
    {LV2_OPTIONS_INSTANCE, 0, map_uri(&urids, LV2_BUF_SIZE__minBlockLength), sizeof(int32_t), atom_int, &min_block},
    {LV2_OPTIONS_INSTANCE, 0, map_uri(&urids, LV2_BUF_SIZE__nominalBlockLength), sizeof(int32_t), atom_int, &block},
    {LV2_OPTIONS_INSTANCE, 0, map_uri(&urids, "http://lv2plug.in/ns/ext/parameters#sampleRate"), sizeof(float), atom_float, &srate},
    {LV2_OPTIONS_INSTANCE, 0, 0, 0, 0, NULL},
  };
  LV2_Feature f_map = {LV2_URID__map, &map}, f_unmap = {LV2_URID__unmap, &unmap}, f_log = {LV2_LOG__log, &log};
  LV2_Feature f_opts = {LV2_OPTIONS__options, options}, f_bounded = {LV2_BUF_SIZE__boundedBlockLength, NULL};
  LV2_Feature f_pow2 = {LV2_BUF_SIZE__powerOf2BlockLength, NULL};
  const LV2_Feature *features[] = {&f_map, &f_unmap, &f_log, &f_opts, &f_bounded, &f_pow2, NULL};
  /* The bundle's path ends with '/'. */
  char bundle_path[1024];
  snprintf(bundle_path, sizeof(bundle_path), "%s%s", bundle, bundle[0] && bundle[strlen(bundle) - 1] == '/' ? "" : "/");
  LV2_Handle h = desc->instantiate(desc, (double)rate, bundle_path, features);
  if (!h) {
    say(info, info_len, "the plugin did not start (it may need a host feature Pulsekit does not have yet)");
    dlclose(lib);
    free(song.ev);
    free(in.lr);
    return 0;
  }
  /* Its ports' buffers. */
  float *controls = (float *)calloc((size_t)nports, sizeof(float));
  float **audio = (float **)calloc((size_t)nports, sizeof(float *));
  uint8_t **atoms = (uint8_t **)calloc((size_t)nports, sizeof(uint8_t *));
  float *silence = (float *)calloc(BLOCK, sizeof(float));
  int ins[2] = {-1, -1}, outs2[2] = {-1, -1}, ni = 0, no = 0;
  for (int i = 0; i < nports; i++) {
    if (kinds[i] == PK_LV2_CONTROL) {
      controls[i] = values[i];
      desc->connect_port(h, (uint32_t)i, &controls[i]);
    } else if (kinds[i] == PK_LV2_AUDIO || kinds[i] == PK_LV2_CV) {
      audio[i] = (float *)calloc(BLOCK, sizeof(float));
      desc->connect_port(h, (uint32_t)i, audio[i]);
      if (kinds[i] == PK_LV2_AUDIO && !outs[i] && ni < 2) ins[ni++] = i;
      if (kinds[i] == PK_LV2_AUDIO && outs[i] && no < 2) outs2[no++] = i;
    } else if (kinds[i] == PK_LV2_ATOM) {
      void *mem = NULL;
      if (posix_memalign(&mem, 8, ATOM_CAPACITY) != 0) mem = NULL;
      atoms[i] = (uint8_t *)mem;
      if (atoms[i]) memset(atoms[i], 0, ATOM_CAPACITY);
      desc->connect_port(h, (uint32_t)i, atoms[i]);
    } else {
      desc->connect_port(h, (uint32_t)i, silence);
    }
  }
  LV2_URID seq_type = map_uri(&urids, LV2_ATOM__Sequence), chunk_type = map_uri(&urids, LV2_ATOM__Chunk);
  LV2_URID midi_type = map_uri(&urids, LV2_MIDI__MidiEvent);
  if (desc->activate) desc->activate(h);
  double seconds = song.ev ? song.length : (double)in.frames / rate;
  uint64_t frames = (uint64_t)((seconds + tail) * rate) + 1;
  float *lr = (float *)calloc((size_t)frames * 2, sizeof(float));
  float peak = 0;
  int next = 0;
  for (uint64_t start = 0; start < frames; start += BLOCK) {
    uint32_t n = (uint32_t)(frames - start < BLOCK ? frames - start : BLOCK);
    for (int i = 0; i < nports; i++) {
      if (!atoms[i]) continue;
      LV2_Atom_Sequence *seq = (LV2_Atom_Sequence *)atoms[i];
      if (outs[i]) {
        /* An output: room to write, as a chunk. */
        seq->atom.type = chunk_type;
        seq->atom.size = ATOM_CAPACITY - sizeof(LV2_Atom);
        continue;
      }
      seq->atom.type = seq_type;
      seq->atom.size = sizeof(LV2_Atom_Sequence_Body);
      seq->body.unit = 0;
      seq->body.pad = 0;
      if (i != atom_in) continue;
      while (next < song.n && (uint64_t)(song.ev[next].sec * rate) < start + n) {
        const midi_ev_t *m = &song.ev[next++];
        uint64_t at = (uint64_t)(m->sec * rate);
        uint32_t room = ATOM_CAPACITY - (uint32_t)sizeof(LV2_Atom) - seq->atom.size;
        if (room < sizeof(LV2_Atom_Event) + 8) continue;
        LV2_Atom_Event *ev = (LV2_Atom_Event *)((uint8_t *)LV2_ATOM_BODY(&seq->atom) + seq->atom.size);
        ev->time.frames = at < start ? 0 : (int64_t)(at - start);
        ev->body.type = midi_type;
        ev->body.size = (uint32_t)m->len;
        memcpy(LV2_ATOM_BODY(&ev->body), m->b, (size_t)m->len);
        seq->atom.size += (uint32_t)lv2_atom_pad_size(sizeof(LV2_Atom_Event) + (uint32_t)m->len);
      }
    }
    for (int c = 0; c < 2; c++) {
      if (ins[c] < 0) continue;
      float *buf = audio[ins[c]];
      for (uint32_t i = 0; i < n; i++) {
        uint64_t f = start + i;
        float l = f < in.frames ? in.lr[f * 2] : 0, r = f < in.frames ? in.lr[f * 2 + 1] : 0;
        /* One input: the WAV's two sides together. */
        buf[i] = ni == 1 ? (l + r) * 0.5f : c == 0 ? l : r;
      }
    }
    desc->run(h, n);
    float *L = audio[outs2[0]], *R = outs2[1] >= 0 ? audio[outs2[1]] : audio[outs2[0]];
    for (uint32_t i = 0; i < n; i++) {
      float a = (float)(L[i] * volume), b = (float)(R[i] * volume);
      lr[(start + i) * 2] = a;
      lr[(start + i) * 2 + 1] = b;
      if (fabsf(a) > peak) peak = fabsf(a);
      if (fabsf(b) > peak) peak = fabsf(b);
    }
  }
  if (desc->deactivate) desc->deactivate(h);
  desc->cleanup(h);
  int ok = pk_write_wav(wav_out, lr, frames, rate, info, info_len);
  if (ok)
    say(info, info_len, "played %s into %.1f s (peak %.0f%%%s)", song.ev ? "the MIDI file" : "the WAV", (double)frames / rate, peak * 100,
        peak > 1 ? ", clipped: lower the volume" : peak < 0.0001f ? ", silent" : "");
  for (int i = 0; i < nports; i++) {
    free(audio[i]);
    free(atoms[i]);
  }
  for (int i = 0; i < urids.n; i++) free(urids.uris[i]);
  free(urids.uris);
  free(audio);
  free(atoms);
  free(controls);
  free(silence);
  free(lr);
  free(song.ev);
  free(in.lr);
  dlclose(lib);
  return ok;
}
