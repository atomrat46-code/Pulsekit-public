#ifndef PULSEKIT_MIDI_WAV_H
#define PULSEKIT_MIDI_WAV_H

#include <stdint.h>

/* A MIDI event at its time in seconds (channel messages; a note-on at velocity 0 is a note-off). */
typedef struct {
  double sec;
  uint8_t b[3];
  int len;
} midi_ev_t;

/* A MIDI file's events in time order (note-offs first at one time), and when the last one is. */
typedef struct {
  midi_ev_t *ev;
  int n, cap;
  double length;
} song_t;

/* Interleaved stereo samples (a mono WAV's channel twice), at its rate. */
typedef struct {
  float *lr;
  uint64_t frames;
  int rate;
} pk_wav_t;

void pk_say(char *err, int len, const char *fmt, ...);
int pk_read_song(const char *path, song_t *s, char *err, int len);
int pk_write_wav(const char *path, const float *lr, uint64_t frames, int rate, char *err, int len);
int pk_read_wav(const char *path, pk_wav_t *w, char *err, int len);

#endif
