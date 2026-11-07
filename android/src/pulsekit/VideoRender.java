package pulsekit;

import android.app.AlertDialog;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * ReRender (a video thumbnail's item: the Media browser, Downloads, DB/Prompts): the video made again
 * at another size and shape on the phone's own video encoder, as <video>-WxH.mp4 beside it (else in
 * Download). Its shape is reached by cropping (the part kept: centre, top or left, bottom or right) or
 * by black bars, then it is scaled: the decoder draws each frame into an OpenGL surface that feeds the
 * encoder. The sound is the original's, copied as it is. Off the main thread; the status line says how far.
 */
final class VideoRender {
    private VideoRender() {}

    /** The tests' answer to the dialog: {size, CROP or PAD, an AREAS name}; null asks. */
    static String[] answer;
    /** The dialog shown last; where the video went last, and what was said (for the tests). */
    static AlertDialog lastAsk;
    static volatile String last;
    static volatile String lastSaid;
    /** The last render's plan {size and shape}, before the encoder ran (for the tests: the codec is not there). */
    static volatile String lastPlan;

    /** The dialog: size, Crop to fill or Fit with black bars, the part kept; then the render. */
    static void ask(final MainActivity app, final String name, final String path, final Uri uri, final byte[] bytes) {
        if (answer != null) {
            start(app, name, path, uri, bytes, answer);
            return;
        }
        LinearLayout col = app.col();
        col.setPadding(app.dp(16), app.dp(8), app.dp(16), 0);
        final String[] labels = new String[VideoFit.SIZES.length + 1];
        System.arraycopy(VideoFit.SIZES, 0, labels, 0, VideoFit.SIZES.length);
        labels[labels.length - 1] = VideoFit.CUSTOM;
        col.addView(app.text("Size", 13, false));
        final Spinner size = new Spinner(app);
        size.setTag("rerender-size");
        size.setAdapter(new ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, labels));
        col.addView(size);
        final EditText custom = new EditText(app);
        custom.setTag("rerender-custom");
        custom.setSingleLine(true);
        custom.setHint("Custom size, e.g. 1080x1920");
        col.addView(custom);
        final RadioGroup fit = new RadioGroup(app);
        final RadioButton crop = new RadioButton(app);
        crop.setText(VideoFit.CROP);
        crop.setId(android.view.View.generateViewId());
        final RadioButton pad = new RadioButton(app);
        pad.setText(VideoFit.PAD);
        pad.setId(android.view.View.generateViewId());
        fit.addView(crop);
        fit.addView(pad);
        fit.check(crop.getId());
        col.addView(fit);
        TextView areaLabel = app.text("Part kept (crop)", 13, false);
        col.addView(areaLabel);
        final Spinner area = new Spinner(app);
        area.setTag("rerender-area");
        area.setAdapter(new ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, VideoFit.AREAS));
        col.addView(area);
        lastAsk = new AlertDialog.Builder(app).setTitle("ReRender · " + name).setView(col)
            .setPositiveButton("ReRender", (d, w) -> {
                String chosen = String.valueOf(size.getSelectedItem());
                if (VideoFit.CUSTOM.equals(chosen)) chosen = custom.getText().toString();
                start(app, name, path, uri, bytes, new String[] {chosen, crop.isChecked() ? VideoFit.CROP : VideoFit.PAD, String.valueOf(area.getSelectedItem())});
            })
            .setNegativeButton("Cancel", null).show();
    }

    static void start(MainActivity app, String name, String path, Uri uri, byte[] bytes, String[] got) {
        int[] wh = VideoFit.parseSize(got[0]);
        if (wh == null) {
            app.setNow("ReRender: " + got[0] + " is no size (W x H, each 16 to 4096)");
            return;
        }
        int a = java.util.Arrays.asList(VideoFit.AREAS).indexOf(got[2]);
        run(app, name, path, uri, bytes, wh[0], wh[1], VideoFit.PAD.equals(got[1]), VideoFit.ANCHORS[Math.max(0, a)]);
    }

    /** The render, off the main thread: the picture made again, then the original's sound with it. */
    static void run(final MainActivity app, final String name, final String path, final Uri uri, final byte[] bytes,
                    final int w, final int h, final boolean pad, final double anchor) {
        app.setNow("ReRendering " + name + " at " + w + "×" + h + "…");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String said;
            String where = null;
            File work = new File(app.getCacheDir(), "rerender");
            File tmp = null;
            try {
                if (!work.isDirectory()) work.mkdirs();
                File video = path != null ? new File(path) : null;
                if (video == null) {
                    tmp = new File(work, "source.mp4");
                    java.io.InputStream in = bytes != null ? new java.io.ByteArrayInputStream(bytes) : app.getContentResolver().openInputStream(uri);
                    if (in == null) throw new java.io.IOException("it could not be read");
                    copy(in, tmp);
                    video = tmp;
                }
                File picture = new File(work, "picture.mp4");
                String plan = render(video, picture, w, h, pad, anchor, f -> say(app, main, name, f));
                File made = new File(work, VideoFit.name(name, w, h));
                Mp4Media.withSound(picture, video, made, w, h);
                picture.delete();
                File beside = path != null ? new File(path).getAbsoluteFile().getParentFile() : null;
                if (beside != null && beside.canWrite()) {
                    String base = made.getName().replaceAll("\\.mp4$", "");
                    File to = new File(beside, base + ".mp4");
                    for (int n = 1; to.exists(); n++) to = new File(beside, base + "(" + n + ").mp4");
                    copy(new java.io.FileInputStream(made), to);
                    where = to.getPath();
                } else {
                    where = ArtJava.publish(app, made.getName(), AudioExtract.read(made));
                    if (where == null) throw new java.io.IOException("Download took no file");
                }
                made.delete();
                said = "ReRendered " + name + " at " + w + "×" + h + " (" + plan + "): " + where;
            } catch (Throwable ex) {
                where = null;
                said = "Could not ReRender " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                if (tmp != null) tmp.delete();
            }
            final String shown = said;
            last = where;
            lastSaid = said;
            main.post(() -> {
                app.setNow(shown);
                android.widget.Toast.makeText(app, shown, android.widget.Toast.LENGTH_SHORT).show();
            });
        }, "pulsekit-rerender").start();
    }

    private static long lastSay;

    static void say(final MainActivity app, android.os.Handler main, String name, double f) {
        long now = System.currentTimeMillis();
        if (now - lastSay < 500) return;
        lastSay = now;
        final String text = "ReRendering " + name + "… " + Math.round(f * 100) + "%";
        main.post(() -> app.setNow(text));
    }

    static void copy(java.io.InputStream in, File to) throws java.io.IOException {
        java.io.FileOutputStream out = new java.io.FileOutputStream(to);
        try {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally {
            out.close();
            in.close();
        }
    }

    interface Progress {
        void at(double f);
    }

    /**
     * The video's picture (no sound) made again at ow×oh into `dst`: decoded into an OpenGL texture,
     * drawn cropped (or with bars) and scaled into the encoder's surface. What it did, in words.
     */
    static String render(File src, File dst, int ow, int oh, boolean pad, double anchor, Progress progress) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(src.getAbsolutePath());
        int track = -1;
        MediaFormat in = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                track = i;
                in = f;
                break;
            }
        }
        if (track < 0) {
            ex.release();
            throw new java.io.IOException(src.getName() + " has no picture");
        }
        ex.selectTrack(track);
        int sw = in.getInteger(MediaFormat.KEY_WIDTH), sh = in.getInteger(MediaFormat.KEY_HEIGHT);
        int turned = in.containsKey("rotation-degrees") ? in.getInteger("rotation-degrees") : 0;
        if (turned % 180 != 0) {
            int t = sw;
            sw = sh;
            sh = t;
        }
        long duration = in.containsKey(MediaFormat.KEY_DURATION) ? in.getLong(MediaFormat.KEY_DURATION) : 0;
        int fps = in.containsKey(MediaFormat.KEY_FRAME_RATE) ? Math.max(1, in.getInteger(MediaFormat.KEY_FRAME_RATE)) : 30;
        final int[] p = VideoFit.plan(sw, sh, ow, oh, pad, anchor);
        lastPlan = sw + "x" + sh + " to " + ow + "x" + oh + ": " + VideoFit.said(p);

        MediaFormat out = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, ow, oh);
        out.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        out.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(2000000, Math.min(20000000, ow * oh * 6)));
        out.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        out.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        enc.configure(out, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface encIn = enc.createInputSurface();
        enc.start();

        Gl gl = new Gl(encIn);
        final Object lock = new Object();
        final boolean[] ready = new boolean[1];
        android.os.HandlerThread frames = new android.os.HandlerThread("pulsekit-rerender-frames");
        frames.start();
        SurfaceTexture st = new SurfaceTexture(gl.texture);
        st.setOnFrameAvailableListener(t -> {
            synchronized (lock) {
                ready[0] = true;
                lock.notifyAll();
            }
        }, new android.os.Handler(frames.getLooper()));
        Surface decOut = new Surface(st);
        MediaCodec dec = MediaCodec.createDecoderByType(in.getString(MediaFormat.KEY_MIME));
        dec.configure(in, decOut, null, 0);
        dec.start();

        MediaMuxer mux = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int muxTrack = -1;
        boolean muxing = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inDone = false, decDone = false, encDone = false;
        float[] matrix = new float[16];
        int written = 0;
        try {
            while (!encDone) {
                if (!inDone) {
                    int ix = dec.dequeueInputBuffer(10000);
                    if (ix >= 0) {
                        ByteBuffer buf = dec.getInputBuffer(ix);
                        int n = ex.readSampleData(buf, 0);
                        if (n < 0) {
                            dec.queueInputBuffer(ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            dec.queueInputBuffer(ix, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                if (!decDone) {
                    int ox = dec.dequeueOutputBuffer(info, 10000);
                    if (ox >= 0) {
                        boolean show = info.size > 0;
                        long pts = info.presentationTimeUs;
                        boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        dec.releaseOutputBuffer(ox, show);
                        if (show) {
                            synchronized (lock) {
                                long until = System.currentTimeMillis() + 2500;
                                while (!ready[0] && System.currentTimeMillis() < until) lock.wait(100);
                                if (!ready[0]) throw new java.io.IOException("the decoder gave no frame");
                                ready[0] = false;
                            }
                            st.updateTexImage();
                            st.getTransformMatrix(matrix);
                            gl.draw(matrix, p, sw, sh, ow, oh);
                            EGLExt.eglPresentationTimeANDROID(gl.display, gl.surface, pts * 1000);
                            EGL14.eglSwapBuffers(gl.display, gl.surface);
                            if (progress != null && duration > 0) progress.at(Math.min(1, pts / (double) duration));
                        }
                        if (end) {
                            enc.signalEndOfInputStream();
                            decDone = true;
                        }
                    }
                }
                int ex2 = enc.dequeueOutputBuffer(info, decDone ? 10000 : 0);
                if (ex2 == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    muxTrack = mux.addTrack(enc.getOutputFormat());
                    mux.start();
                    muxing = true;
                } else if (ex2 >= 0) {
                    ByteBuffer data = enc.getOutputBuffer(ex2);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && muxing) {
                        data.position(info.offset);
                        data.limit(info.offset + info.size);
                        mux.writeSampleData(muxTrack, data, info);
                        written++;
                    }
                    enc.releaseOutputBuffer(ex2, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encDone = true;
                }
            }
        } finally {
            try {
                dec.stop();
            } catch (Throwable ignored) {
                // stopping
            }
            dec.release();
            try {
                enc.stop();
            } catch (Throwable ignored) {
                // stopping
            }
            enc.release();
            ex.release();
            decOut.release();
            st.release();
            frames.quitSafely();
            gl.release();
            encIn.release();
            try {
                if (muxing) mux.stop();
            } catch (Throwable ignored) {
                // nothing written
            }
            mux.release();
        }
        if (written == 0) throw new java.io.IOException("the encoder made no frame");
        return VideoFit.said(p);
    }

    /** The OpenGL side: an EGL surface on the encoder's input, and a quad drawing the decoder's texture. */
    static final class Gl {
        EGLDisplay display;
        EGLContext context;
        EGLSurface surface;
        int program;
        int texture;
        final FloatBuffer quad;

        static final String VERTEX = "uniform mat4 uTex;\nattribute vec4 aPos;\nattribute vec4 aUv;\nvarying vec2 vUv;\n"
            + "void main() {\n  gl_Position = aPos;\n  vUv = (uTex * aUv).xy;\n}\n";
        static final String FRAGMENT = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 vUv;\n"
            + "uniform samplerExternalOES sTex;\nvoid main() {\n  gl_FragColor = texture2D(sTex, vUv);\n}\n";

        Gl(Surface target) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("no EGL");
            int[] attribs = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0);
            if (count[0] == 0) throw new IllegalStateException("no EGL setup for the encoder");
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreateWindowSurface(display, configs[0], target, new int[] {EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(display, surface, surface, context);
            program = link(VERTEX, FRAGMENT);
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            texture = t[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }

        static int link(String vs, String fs) {
            int v = shader(GLES20.GL_VERTEX_SHADER, vs), f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, v);
            GLES20.glAttachShader(prog, f);
            GLES20.glLinkProgram(prog);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) throw new IllegalStateException("OpenGL: " + GLES20.glGetProgramInfoLog(prog));
            return prog;
        }

        static int shader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) throw new IllegalStateException("OpenGL: " + GLES20.glGetShaderInfoLog(s));
            return s;
        }

        /**
         * One frame: crop (the texture's part kept) or bars (the picture drawn in a smaller area on black),
         * at the encoder's ow×oh. The plan `p` is in the source's pixels, top-left origin.
         */
        void draw(float[] matrix, int[] p, int sw, int sh, int ow, int oh) {
            GLES20.glViewport(0, 0, ow, oh);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            // Texture coordinates of the part kept (0,0 the bottom left of the picture).
            float u0 = p[0] / (float) sw, u1 = 1 - p[2] / (float) sw;
            float v0 = p[3] / (float) sh, v1 = 1 - p[1] / (float) sh;
            int pw = sw + p[4] + p[6], ph = sh + p[5] + p[7];
            int vx = Math.round(p[4] * ow / (float) pw), vy = Math.round(p[7] * oh / (float) ph);
            int vw = Math.round(sw * ow / (float) pw), vh = Math.round(sh * oh / (float) ph);
            GLES20.glViewport(vx, vy, vw, vh);
            quad.clear();
            quad.put(new float[] {-1, -1, u0, v0, 1, -1, u1, v0, -1, 1, u0, v1, 1, 1, u1, v1});
            GLES20.glUseProgram(program);
            int pos = GLES20.glGetAttribLocation(program, "aPos"), uv = GLES20.glGetAttribLocation(program, "aUv");
            quad.position(0);
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(pos);
            quad.position(2);
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(uv);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTex"), 1, false, matrix, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        void release() {
            if (display != null) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surface != null) EGL14.eglDestroySurface(display, surface);
                if (context != null) EGL14.eglDestroyContext(display, context);
                EGL14.eglTerminate(display);
            }
        }
    }
}
