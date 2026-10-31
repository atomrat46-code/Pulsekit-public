/* NativeHost's calls into the plugin hosts (clap_render.c, lv2_render.c): "<n>\t<text>" back, n < 1 when it failed. */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "clap_render.h"
#include "lv2_render.h"

static jstring answer(JNIEnv *env, int n, const char *text) {
  size_t len = 32 + (text ? strlen(text) : 0);
  char *s = (char *)malloc(len);
  snprintf(s, len, "%d\t%s", n, text ? text : "");
  jstring out = (*env)->NewStringUTF(env, s);
  free(s);
  return out;
}

JNIEXPORT jstring JNICALL Java_pulsekit_NativeHost_clapList(JNIEnv *env, jclass cls, jstring path) {
  (void)cls;
  const char *p = (*env)->GetStringUTFChars(env, path, NULL);
  char list[16384], err[1024];
  int n = pk_clap_list(p, list, sizeof(list), err, sizeof(err));
  (*env)->ReleaseStringUTFChars(env, path, p);
  return answer(env, n, n < 0 ? err : list);
}

JNIEXPORT jstring JNICALL Java_pulsekit_NativeHost_clapRender(JNIEnv *env, jclass cls, jstring path, jstring id, jstring midi, jstring wav, jint rate,
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

JNIEXPORT jstring JNICALL Java_pulsekit_NativeHost_lv2Render(JNIEnv *env, jclass cls, jstring binary, jstring uri, jstring bundle, jstring libdir,
                                                             jintArray kinds, jintArray outs, jfloatArray values, jstring midi, jstring wav_in,
                                                             jstring wav_out, jint rate, jdouble tail, jdouble volume) {
  (void)cls;
  const char *b = (*env)->GetStringUTFChars(env, binary, NULL);
  const char *u = (*env)->GetStringUTFChars(env, uri, NULL);
  const char *d = (*env)->GetStringUTFChars(env, bundle, NULL);
  const char *l = (*env)->GetStringUTFChars(env, libdir, NULL);
  const char *m = (*env)->GetStringUTFChars(env, midi, NULL);
  const char *wi = (*env)->GetStringUTFChars(env, wav_in, NULL);
  const char *wo = (*env)->GetStringUTFChars(env, wav_out, NULL);
  jint n = (*env)->GetArrayLength(env, kinds);
  jint *k = (*env)->GetIntArrayElements(env, kinds, NULL);
  jint *o = (*env)->GetIntArrayElements(env, outs, NULL);
  jfloat *v = (*env)->GetFloatArrayElements(env, values, NULL);
  char info[2048];
  int ok = pk_lv2_render(b, u, d, l, (int)n, (const int *)k, (const int *)o, (const float *)v, m, wi, wo, (int)rate, (double)tail, (double)volume, info,
                         sizeof(info));
  (*env)->ReleaseIntArrayElements(env, kinds, k, JNI_ABORT);
  (*env)->ReleaseIntArrayElements(env, outs, o, JNI_ABORT);
  (*env)->ReleaseFloatArrayElements(env, values, v, JNI_ABORT);
  (*env)->ReleaseStringUTFChars(env, binary, b);
  (*env)->ReleaseStringUTFChars(env, uri, u);
  (*env)->ReleaseStringUTFChars(env, bundle, d);
  (*env)->ReleaseStringUTFChars(env, libdir, l);
  (*env)->ReleaseStringUTFChars(env, midi, m);
  (*env)->ReleaseStringUTFChars(env, wav_in, wi);
  (*env)->ReleaseStringUTFChars(env, wav_out, wo);
  return answer(env, ok, info);
}
