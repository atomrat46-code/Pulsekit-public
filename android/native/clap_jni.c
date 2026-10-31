/* ClapService's calls into the CLAP host (clap_render.c): "<n>\t<text>" back, n < 1 when it failed. */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "clap_render.h"

static jstring answer(JNIEnv *env, int n, const char *text) {
  size_t len = 32 + (text ? strlen(text) : 0);
  char *s = (char *)malloc(len);
  snprintf(s, len, "%d\t%s", n, text ? text : "");
  jstring out = (*env)->NewStringUTF(env, s);
  free(s);
  return out;
}

JNIEXPORT jstring JNICALL Java_pulsekit_ClapService_nativeList(JNIEnv *env, jclass cls, jstring path) {
  (void)cls;
  const char *p = (*env)->GetStringUTFChars(env, path, NULL);
  char list[16384], err[1024];
  int n = pk_clap_list(p, list, sizeof(list), err, sizeof(err));
  (*env)->ReleaseStringUTFChars(env, path, p);
  return answer(env, n, n < 0 ? err : list);
}

JNIEXPORT jstring JNICALL Java_pulsekit_ClapService_nativeRender(JNIEnv *env, jclass cls, jstring path, jstring id, jstring midi, jstring wav, jint rate,
                                                                 jdouble tail, jdouble volume) {
  (void)cls;
  const char *p = (*env)->GetStringUTFChars(env, path, NULL);
  const char *i = (*env)->GetStringUTFChars(env, id, NULL);
  const char *m = (*env)->GetStringUTFChars(env, midi, NULL);
  const char *w = (*env)->GetStringUTFChars(env, wav, NULL);
  char info[2048];
  int ok = pk_clap_render(p, i, m, w, (int)rate, (double)tail, (double)volume, info, sizeof(info));
  (*env)->ReleaseStringUTFChars(env, path, p);
  (*env)->ReleaseStringUTFChars(env, id, i);
  (*env)->ReleaseStringUTFChars(env, midi, m);
  (*env)->ReleaseStringUTFChars(env, wav, w);
  return answer(env, ok, info);
}
