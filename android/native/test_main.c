/* The CLAP host on Linux, to test it: test_main <plugin.clap> [song.mid out.wav [id]]. */
#include <stdio.h>

#include "clap_render.h"

int main(int argc, char **argv) {
  char out[4096], info[1024];
  if (argc < 2) {
    fprintf(stderr, "usage: %s plugin.clap [song.mid out.wav [id]]\n", argv[0]);
    return 2;
  }
  int n = pk_clap_list(argv[1], out, sizeof(out), info, sizeof(info));
  if (n < 0) {
    printf("list failed: %s\n", info);
    return 1;
  }
  printf("plugins (%d):\n%s", n, out);
  if (argc < 4) return 0;
  int ok = pk_clap_render(argv[1], argc > 4 ? argv[4] : "", argv[2], argv[3], 44100, 2.0, 1.0, info, sizeof(info));
  printf("%s: %s\n", ok ? "rendered" : "failed", info);
  return ok ? 0 : 1;
}
