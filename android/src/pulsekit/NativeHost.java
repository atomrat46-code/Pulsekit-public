package pulsekit;

/**
 * The plugin hosts' native calls (libpulsekitclap.so: clap_render.c, lv2_render.c through
 * host_jni.c). ClapService calls them in its own process; nothing Android in it, so the same
 * library built for Linux can be tested on a computer (android/native/test.sh).
 */
public final class NativeHost {
    private NativeHost() {}

    /** A .clap's plugins: "<n>\tid\tname\tvendor\n...", or "-1\t<why>". */
    static native String clapList(String path);

    /** "1\t<what was done>" or "0\t<why not>". */
    static native String clapRender(String path, String id, String midi, String wav, int rate, double tail, double volume);

    /**
     * An LV2 plugin (`uri` in `binary`, its bundle folder; libraries it needs in `libdir`) plays the
     * MIDI file, or with midi "" the WAV `wavIn`, into `wavOut`. Ports by index: kinds (Lv2Bundle.AUDIO...),
     * outs (1: an output), values (a control input's value). "1\t<what was done>" or "0\t<why not>".
     */
    static native String lv2Render(String binary, String uri, String bundle, String libdir, int[] kinds, int[] outs, float[] values, String midi,
        String wavIn, String wavOut, int rate, double tail, double volume);
}
