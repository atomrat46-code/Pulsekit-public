#ifndef PULSEKIT_CLAP_RENDER_H
#define PULSEKIT_CLAP_RENDER_H

/* The plugins of a .clap file, "id\tname\tvendor\n" each into out; how many, or -1 (err says why). */
int pk_clap_list(const char *path, char *out, int out_len, char *err, int err_len);

/* The MIDI file played through the plugin (its id; empty: the file's first) into a 16-bit stereo WAV.
   1 when written (info says what was done), 0 when not (info says why). */
int pk_clap_render(const char *path, const char *plugin_id, const char *midi_path, const char *wav_path, int rate, double tail, double volume,
                   char *info, int info_len);

#endif
