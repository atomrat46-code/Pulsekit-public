#ifndef PULSEKIT_LV2_RENDER_H
#define PULSEKIT_LV2_RENDER_H

/* A port's kind, as Lv2Bundle reads it from the .ttl files. */
#define PK_LV2_AUDIO 0
#define PK_LV2_CONTROL 1
#define PK_LV2_ATOM 2
#define PK_LV2_CV 3
#define PK_LV2_OTHER 4

/* The plugin `uri` in `binary` (its bundle folder `bundle`; libraries it needs found in `libdir`)
   plays the MIDI file (or, with no MIDI, the WAV `wav_in` through it) into `wav_out`. Ports:
   kinds[i], outs[i] (1: an output), values[i] (a control input's value). 1 when written (info
   says what was done), 0 when not (info says why). */
int pk_lv2_render(const char *binary, const char *uri, const char *bundle, const char *libdir, int nports, const int *kinds, const int *outs,
                  const float *values, const char *midi_path, const char *wav_in, const char *wav_out, int rate, double tail, double volume,
                  char *info, int info_len);

#endif
