package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Prompts tab. Prompts, categories, reference files, and versions live in an encrypted database. */
public final class PromptSheet {
  private static final int MAX = 16 * 1024 * 1024;
  private static LinearLayout host;
  private static PromptVault vault;
  private static long categoryId;
  private static long promptId;
  private static long loadedVersionId;
  private static EditText title;
  private static EditText model;
  private static EditText description;
  private static EditText prompt;
  private static TextView ref1;
  private static TextView ref2;
  private static TextView result;
  private static int picking;
  private static int dbFor;
  private static int previewBack;
  private static boolean refsOpen;
  /** The Result files gallery (the same view as Ref files, over the result files). */
  private static boolean resultsOpen;
  private static boolean resultTextOpen;
  private static int refsToken;
  private static float previewZoom = 1f;
  private static int previewBaseW;
  private static int previewBaseH;
  private static ZoomBox previewBox;
  private static TextView zoomLabel;
  private static String previewName = "";
  private static byte[] previewBytes = new byte[0];
  private static TextView previewBody;
  private static ImageView previewImage;
  private static VideoView previewVideo;
  private static MediaPlayer previewVideoPlayer;
  private static TextView previewMuteBtn;
  private static TextView previewVolLabel;
  private static int previewVideoVol = 80;
  private static boolean previewVideoMuted;
  private static MediaPlayer previewAudio;
  /** A sound playing in place in the Ref files / Result files gallery: which file, and its card's mark and label. */
  private static String galleryPlaying;
  private static TextView galleryMark;
  private static String galleryMarkText;
  private static File previewFile;
  private static byte[] ref1Bytes = new byte[0];
  private static byte[] ref2Bytes = new byte[0];
  private static byte[] resultBytes = new byte[0];
  private static String ref1Name = "";
  private static String ref2Name = "";
  private static String resultName = "";
  private static String resultText = "";
  private static String codeType = "";
  private static byte[] exportBytes;
  private static String exportName = "prompt.prompt";

  private PromptSheet() {}

  public static LinearLayout create(Activity activity) {
    host = new LinearLayout(activity);
    host.setOrientation(LinearLayout.VERTICAL);
    host.setBackgroundColor(Color.parseColor("#0A0B0C"));
    host.setClickable(true);
    java.io.File file = new java.io.File(activity.getFilesDir(), "prompts.vault");
    if (PromptVault.ready(activity.getFilesDir()) != null || !file.isFile() || PromptVault.storedSize(activity.getFilesDir()) < OPEN_IN_BACKGROUND) {
      try {
        vault = PromptVault.open(activity.getFilesDir());
        if (categoryId == 0 && !vault.categories().isEmpty()) categoryId = vault.categories().get(0).id;
      } catch (Exception ex) {
        vault = null;
        toast(activity, ex);
      }
      rebuild(activity);
      return host;
    }
    // A large library (videos kept in it) takes a while to read and decrypt: in the background, so
    // the app starts at once (on the main thread it froze for half a minute).
    vault = null;
    opening = true;
    OPENING.incrementAndGet();
    rebuild(activity);
    final java.io.File dir = activity.getFilesDir();
    new Thread(new Runnable() {
      @Override
      public void run() {
        PromptVault opened = null;
        Exception failed = null;
        try {
          opened = PromptVault.open(dir);
          // A library from before this app keeps its videos inside: kept on their own now, once.
          opened.upgrade();
        } catch (Exception ex) {
          failed = ex;
        }
        final PromptVault done = opened;
        final Exception why = failed;
        activity.runOnUiThread(() -> {
          vault = done;
          opening = false;
          OPENING.decrementAndGet();
          if (done != null && categoryId == 0 && !done.categories().isEmpty()) categoryId = done.categories().get(0).id;
          if (why != null) toast(activity, why);
          rebuild(activity);
        });
      }
    }, "pulsekit-prompts-open").start();
    return host;
  }

  /** A library file this large (bytes) is opened in the background at start. */
  static final long OPEN_IN_BACKGROUND = 2L * 1024 * 1024;
  /** True while the library is being opened in the background. */
  static volatile boolean opening;
  /** Libraries still being opened in the background (the tests wait for 0). */
  static final java.util.concurrent.atomic.AtomicInteger OPENING = new java.util.concurrent.atomic.AtomicInteger();

  public static void beginPick(Activity activity, int which) {
    picking = which;
    Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
    intent.addCategory("android.intent.category.OPENABLE");
    intent.setType("*/*");
    activity.startActivityForResult(intent, 27);
  }

  public static void take(Activity activity, Uri uri) {
    int which = picking == 2 ? 2 : picking == 3 ? 3 : 1;
    String name = displayName(activity, uri);
    String label = which == 1 ? "Reference file 1" : which == 2 ? "Reference file 2" : "Result file";
    try {
      InputStream in = activity.getContentResolver().openInputStream(uri);
      if (in == null) throw new IllegalArgumentException("Could not open that file");
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[65536];
      int total = 0;
      int n;
      try {
        while ((n = in.read(buf)) > 0) {
          total += n;
          if (total > MAX) {
            toast(activity, label + " is too large (max 16 MB)");
            return;
          }
          bos.write(buf, 0, n);
        }
      } finally {
        in.close();
      }
      if (which == 1) {
        ref1Bytes = bos.toByteArray();
        ref1Name = name;
        if (ref1 != null) ref1.setText(name);
      } else if (which == 2) {
        ref2Bytes = bos.toByteArray();
        ref2Name = name;
        if (ref2 != null) ref2.setText(name);
      } else {
        resultBytes = bos.toByteArray();
        resultName = name;
        if (result != null) result.setText(name);
      }
      storePickedFile(activity, which, label);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static void storePickedFile(Activity activity, int which, String label) {
    if (vault == null || promptId == 0) {
      toast(activity, "Open a prompt first");
      return;
    }
    try {
      if (loadedVersionId == 0 || vault.version(loadedVersionId) == null) {
        String titleText = text(title).replace('\n', ' ').replace('\r', ' ').trim();
        if (titleText.length() == 0) {
          PromptVault.Prompt item = vault.prompt(promptId);
          titleText = item != null && item.title != null ? item.title : "Untitled";
        }
        loadedVersionId = vault.addVersion(promptId, titleText, text(description), text(prompt), text(model), ref1Name, ref1Bytes, ref2Name, ref2Bytes, resultName, resultBytes, codeType, resultText);
      } else {
        byte[] bytes = which == 1 ? ref1Bytes : which == 2 ? ref2Bytes : resultBytes;
        String name = which == 1 ? ref1Name : which == 2 ? ref2Name : resultName;
        vault.putFile(loadedVersionId, which, name, bytes);
      }
      toast(activity, label + " added to the encrypted database");
      TextView shown = which == 1 ? ref1 : which == 2 ? ref2 : result;
      String shownName = which == 1 ? ref1Name : which == 2 ? ref2Name : resultName;
      if (shown != null) shown.setText(shownName.length() == 0 ? "No file selected" : shownName + " · encrypted");
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  public static void save(Activity activity) {
    if (vault == null || promptId == 0) {
      toast(activity, "Open a prompt first");
      return;
    }
    try {
      loadedVersionId = vault.addVersion(promptId, text(title), text(description), text(prompt), text(model), ref1Name, ref1Bytes, ref2Name, ref2Bytes, resultName, resultBytes, codeType, resultText);
      toast(activity, "Saved a new version");
      rebuild(activity);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  public static void writeExport(Activity activity, Uri uri) {
    if (uri == null || exportBytes == null) return;
    try {
      java.io.OutputStream out = activity.getContentResolver().openOutputStream(uri);
      if (out == null) {
        toast(activity, "Could not export that prompt");
        return;
      }
      try {
        out.write(exportBytes);
      } finally {
        out.close();
      }
      toast(activity, "Exported · " + exportName);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static void rebuild(Activity activity) {
    if (host == null) return;
    host.removeAllViews();
    if (vault != null && resultTextOpen && promptId != 0) {
      buildResultText(activity);
      return;
    }
    int pad = dp(activity, 16);
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(pad, dp(activity, 12), pad, pad);
    col.addView(label(activity, "Prompts", 20, "#ECEBE6", true));
    TextView lead = label(activity, "Encrypted database on this phone. Each save keeps a version. One version can be final.", 14, "#8A8B86", false);
    lead.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
    col.addView(lead);
    if (vault == null && opening) {
      col.addView(label(activity, "Opening the encrypted database\u2026", 14, "#ECEBE6", false));
    } else if (vault == null) {
      col.addView(label(activity, "The encrypted database could not be opened.", 14, "#ECEBE6", false));
    } else if (previewBack != 0) {
      buildPreview(activity, col);
    } else if (refsOpen || resultsOpen) {
      buildRefGallery(activity, col);
    } else if (dbFor == 1 || dbFor == 2 || dbFor == 3) {
      buildDb(activity, col);
    } else if (promptId == 0) {
      resultTextOpen = false;
      buildList(activity, col);
    } else {
      buildEditor(activity, col);
    }
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(col);
    host.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
  }

  private static void buildList(Activity activity, LinearLayout col) {
    releasePreview();
    TextView refs = button(activity, "Ref files", "#1B1D1F", "#ECEBE6");
    refs.setOnClickListener(v -> {
      refsOpen = true;
      rebuild(activity);
    });
    col.addView(refs, buttonLp(activity));
    TextView results = button(activity, "Result files", "#1B1D1F", "#ECEBE6");
    results.setOnClickListener(v -> {
      resultsOpen = true;
      rebuild(activity);
    });
    col.addView(results, buttonLp(activity));
    col.addView(caption(activity, "CATEGORIES"));
    col.addView(categoryBlock(activity, false));
    TextView addCat = button(activity, "New category", "#1B1D1F", "#ECEBE6");
    addCat.setOnClickListener(v -> ask(activity, "New category", "Name", name -> {
      try {
        categoryId = vault.addCategory(name);
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }));
    col.addView(addCat, buttonLp(activity));
    TextView addSub = button(activity, "Add subcategory", "#1B1D1F", "#ECEBE6");
    addSub.setOnClickListener(v -> addSubcategory(activity));
    col.addView(addSub, buttonLp(activity));
    col.addView(caption(activity, "PROMPTS"));
    List<PromptVault.Prompt> prompts = vault.prompts(categoryId);
    if (prompts.isEmpty()) {
      col.addView(label(activity, "none", 14, "#8A8B86", false));
    }
    for (int i = 0; i < prompts.size(); i++) {
      PromptVault.Prompt item = prompts.get(i);
      PromptVault.Version fin = vault.finalVersion(item.id);
      String detail = fin == null || fin.description.length() == 0 ? "No description" : firstLine(fin.description);
      LinearLayout line = new LinearLayout(activity);
      line.setOrientation(LinearLayout.HORIZONTAL);
      TextView row = button(activity, item.title + "\n" + detail, "#131416", "#ECEBE6");
      row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
      long id = item.id;
      String titleText = item.title;
      row.setOnClickListener(v -> openPrompt(activity, id));
      row.setOnLongClickListener(v -> {
        promptMenu(activity, id, titleText);
        return true;
      });
      line.addView(row, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      TextView menu = button(activity, "Menu", "#1B1D1F", "#ECEBE6");
      menu.setOnClickListener(v -> promptMenu(activity, id, titleText));
      LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(activity, 72), LinearLayout.LayoutParams.MATCH_PARENT);
      menuLp.leftMargin = dp(activity, 8);
      line.addView(menu, menuLp);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.bottomMargin = dp(activity, 10);
      col.addView(line, lp);
    }
    TextView add = button(activity, "New prompt", "#ECEBE6", "#0A0B0C");
    add.setOnClickListener(v -> ask(activity, "New prompt", "Title", name -> {
      try {
        long id = vault.addPrompt(categoryId == 0 ? vault.categories().get(0).id : categoryId, name);
        openPrompt(activity, id);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }));
    col.addView(add, buttonLp(activity));
  }

  private static void buildRefGallery(Activity activity, LinearLayout col) {
    releasePreview();
    int token = ++refsToken;
    TextView back = button(activity, "Back", "#1B1D1F", "#ECEBE6");
    back.setOnClickListener(v -> {
      refsOpen = false;
      resultsOpen = false;
      rebuild(activity);
    });
    col.addView(back, buttonLp(activity));
    // Ref files or Result files: the same gallery, with previews (a video's first frame) and the long-press menu.
    boolean results = resultsOpen && !refsOpen;
    col.addView(caption(activity, results ? "RESULT FILES" : "REFERENCE FILES"));
    // Sort by type, date or size: the previews are laid out again in that order (kept for next time).
    FileSort.current = FileSort.valid(activity.getSharedPreferences(SORT_PREFS, 0).getString("sort", FileSort.current));
    LinearLayout sortRow = new LinearLayout(activity);
    sortRow.setOrientation(LinearLayout.HORIZONTAL);
    sortRow.setGravity(Gravity.CENTER_VERTICAL);
    sortRow.addView(label(activity, "Sort by", 14, "#ECEBE6", false));
    android.widget.Spinner sort = new android.widget.Spinner(activity);
    sort.setTag("refs-sort");
    android.widget.ArrayAdapter<String> choices = new android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, FileSort.CHOICES);
    choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sort.setAdapter(choices);
    sort.setSelection(java.util.Arrays.asList(FileSort.CHOICES).indexOf(FileSort.current), false);
    sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
        String picked = FileSort.CHOICES[position];
        if (picked.equals(FileSort.current)) return;
        FileSort.current = picked;
        activity.getSharedPreferences(SORT_PREFS, 0).edit().putString("sort", picked).apply();
        rebuild(activity);
      }

      @Override
      public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });
    LinearLayout.LayoutParams sortLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    sortLp.leftMargin = dp(activity, 12);
    sortRow.addView(sort, sortLp);
    LinearLayout.LayoutParams rowLpSort = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
    rowLpSort.bottomMargin = dp(activity, 8);
    col.addView(sortRow, rowLpSort);
    List<PromptVault.StoredFile> files = FileSort.sorted(results ? vault.resultFiles() : vault.referenceFiles(), FileSort.current);
    if (files.isEmpty()) {
      col.addView(label(activity, "none", 14, "#8A8B86", false));
      return;
    }
    int gap = dp(activity, 8);
    int width = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32);
    int cell = Math.max(dp(activity, 120), (width - gap) / 2);
    ImageView[] views = new ImageView[files.size()];
    TextView[] marks = new TextView[files.size()];
    long[] ids = new long[files.size()];
    int[] slots = new int[files.size()];
    String[] names = new String[files.size()];
    LinearLayout row = null;
    for (int i = 0; i < files.size(); i++) {
      PromptVault.StoredFile file = files.get(i);
      if (i % 2 == 0) {
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = gap;
        col.addView(row, rowLp);
      }
      android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
      frame.setBackgroundColor(Color.parseColor("#131416"));
      ImageView thumb = new ImageView(activity);
      thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
      // A sound shows "▶ WAV": tapping the card plays it in place.
      boolean sound = previewKindByName(file.name) == 4;
      TextView mark = label(activity, (sound ? "\u25b6 " : "") + extLabel(file.name), 18, "#8A8B86", true);
      mark.setGravity(Gravity.CENTER);
      frame.addView(thumb, new android.widget.FrameLayout.LayoutParams(-1, cell));
      frame.addView(mark, new android.widget.FrameLayout.LayoutParams(-1, cell));
      LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setBackground(box(activity));
      card.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 8));
      card.addView(frame);
      String name = file.name == null ? "file" : file.name;
      TextView caption = label(activity, name, 12, "#ECEBE6", false);
      caption.setPadding(0, dp(activity, 6), 0, 0);
      caption.setMaxLines(2);
      card.addView(caption);
      TextView sub = label(activity, (file.promptTitle == null ? "" : file.promptTitle) + " · " + sizeText(file.size), 11, "#8A8B86", false);
      sub.setPadding(0, dp(activity, 2), 0, 0);
      sub.setMaxLines(1);
      card.addView(sub);
      long versionId = file.versionId;
      int which = file.which;
      String itemName = name;
      View.OnLongClickListener menu = v -> {
        thumbMenu(activity, versionId, which, itemName);
        return true;
      };
      card.setOnLongClickListener(menu);
      frame.setOnLongClickListener(menu);
      thumb.setOnLongClickListener(menu);
      View.OnClickListener play = v -> galleryPlay(activity, versionId, which, itemName, mark);
      card.setOnClickListener(play);
      frame.setOnClickListener(play);
      thumb.setOnClickListener(play);
      LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cell, LinearLayout.LayoutParams.WRAP_CONTENT);
      if (i % 2 == 1) cardLp.leftMargin = gap;
      row.addView(card, cardLp);
      views[i] = thumb;
      marks[i] = mark;
      ids[i] = versionId;
      slots[i] = which;
      names[i] = name;
    }
    loadRefThumbs(activity, token, views, marks, ids, slots, names, cell);
  }

  private static void loadRefThumbs(Activity activity, int token, ImageView[] views, TextView[] marks, long[] ids, int[] slots, String[] names, int px) {
    new Thread(() -> {
      for (int i = 0; i < views.length; i++) {
        if (token != refsToken) return;
        Bitmap bitmap = refThumb(activity, names[i], vault.fileBytes(ids[i], slots[i]), px);
        if (token != refsToken) {
          if (bitmap != null) bitmap.recycle();
          return;
        }
        if (bitmap == null) continue;
        ImageView view = views[i];
        TextView mark = marks[i];
        Bitmap shown = bitmap;
        activity.runOnUiThread(() -> {
          if (token != refsToken) {
            shown.recycle();
            return;
          }
          view.setImageBitmap(shown);
          mark.setVisibility(View.GONE);
        });
      }
    }).start();
  }

  /** A thumbnail of a stored file (a picture, a video frame), or null; also for PyJav's Browse DB. */
  static Bitmap refThumb(Activity activity, String name, byte[] bytes, int px) {
    if (bytes == null || bytes.length == 0) return null;
    try {
      int kind = previewKind(name, bytes);
      if (kind == 2) return sampleBitmap(bytes, px * 2);
      if (kind == 3) return videoThumb(activity, name, bytes, px * 2);
    } catch (Exception ignored) {}
    return null;
  }

  private static Bitmap sampleBitmap(byte[] bytes, int target) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    int sample = 1;
    int max = Math.max(bounds.outWidth, bounds.outHeight);
    while (max / sample > target && sample < 64) sample *= 2;
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = Math.max(1, sample);
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
  }

  private static Bitmap videoThumb(Activity activity, String name, byte[] bytes, int target) throws Exception {
    File file = spill(activity, "thumb-" + name, bytes, "mp4");
    MediaMetadataRetriever media = new MediaMetadataRetriever();
    try {
      media.setDataSource(file.getAbsolutePath());
      Bitmap frame = frameAt(media, 0);
      if (frame == null) return null;
      return scaleDown(frame, target);
    } finally {
      try {
        media.release();
      } catch (Exception ignored) {}
      file.delete();
    }
  }

  private static Bitmap scaleDown(Bitmap bitmap, int target) {
    int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
    if (max <= target || max < 1) return bitmap;
    float scale = target / (float) max;
    int w = Math.max(1, Math.round(bitmap.getWidth() * scale));
    int h = Math.max(1, Math.round(bitmap.getHeight() * scale));
    Bitmap small = Bitmap.createScaledBitmap(bitmap, w, h, true);
    if (small != bitmap) bitmap.recycle();
    return small;
  }

  private static String extLabel(String name) {
    String file = name == null ? "" : name;
    int dot = file.lastIndexOf('.');
    if (dot < 0 || dot >= file.length() - 1) return "FILE";
    String ext = file.substring(dot + 1).toUpperCase(Locale.US);
    return ext.length() > 4 ? ext.substring(0, 4) : ext;
  }

  private static void buildEditor(Activity activity, LinearLayout col) {
    releasePreview();
    PromptVault.Prompt item = vault.prompt(promptId);
    if (item == null) {
      promptId = 0;
      buildList(activity, col);
      return;
    }
    TextView back = button(activity, "Back", "#1B1D1F", "#ECEBE6");
    back.setOnClickListener(v -> {
      promptId = 0;
      dbFor = 0;
      rebuild(activity);
    });
    TextView menu = button(activity, "Menu", "#1B1D1F", "#ECEBE6");
    String promptTitle = item.title == null ? "" : item.title;
    menu.setOnClickListener(v -> promptMenu(activity, promptId, promptTitle));
    LinearLayout top = new LinearLayout(activity);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.addView(back, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
    LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 44));
    menuLp.leftMargin = dp(activity, 8);
    top.addView(menu, menuLp);
    LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    topLp.bottomMargin = dp(activity, 10);
    col.addView(top, topLp);
    col.addView(caption(activity, "CATEGORY"));
    col.addView(categoryBlock(activity, true));
    TextView addSub = button(activity, "Add subcategory", "#1B1D1F", "#ECEBE6");
    addSub.setOnClickListener(v -> addSubcategory(activity));
    col.addView(addSub, buttonLp(activity));
    PromptVault.Version typed = vault.version(loadedVersionId);
    if (typed != null && typed.promptId == promptId && typed.codeType != null && typed.codeType.length() > 0) codeType = typed.codeType;
    else codeType = inCode(item.categoryId) ? typeFor(item.id) : "";
    if (inCode(item.categoryId)) col.addView(typeRow(activity));
    else col.addView(aiBox(activity));
    col.addView(caption(activity, "TITLE"));
    title = line(activity);
    title.setText(item.title);
    col.addView(title, lineLp(activity));
    col.addView(caption(activity, "MODEL"));
    model = line(activity);
    model.setHint("Krea 2Identity edit 1.2 · MiniMax fast H3");
    col.addView(model, lineLp(activity));
    col.addView(caption(activity, "DESCRIPTION"));
    description = area(activity, 4);
    col.addView(description, areaLp(activity, 110));
    ref1 = fileName(activity);
    col.addView(fileBlock(activity, "Reference file 1", ref1, 1));
    ref2 = fileName(activity);
    col.addView(fileBlock(activity, "Reference file 2", ref2, 2));
    result = fileName(activity);
    col.addView(fileBlock(activity, "Result file · optional", result, 3));
    TextView resultTextBtn = button(activity, resultText.length() > 0 ? "Result text · saved" : "Result text", "#1B1D1F", "#ECEBE6");
    resultTextBtn.setOnClickListener(v -> {
      resultTextOpen = true;
      rebuild(activity);
    });
    col.addView(resultTextBtn, buttonLp(activity));
    col.addView(caption(activity, "PROMPT"));
    prompt = area(activity, 8);
    prompt.setTag("prompt-body");
    textMenu(activity, prompt);
    col.addView(prompt, areaLp(activity, 200));
    // The prompt from a text file (an Answer Prompt 1.txt made by Extract prompt): Select file or Browse DB.
    LinearLayout fromFile = new LinearLayout(activity);
    fromFile.setOrientation(LinearLayout.HORIZONTAL);
    final EditText promptField = prompt;
    TextView selectFile = button(activity, "Select file", "#1B1D1F", "#ECEBE6");
    selectFile.setTag("prompt-body-file");
    selectFile.setOnClickListener(v -> PyJavParams.pickPromptFile(activity, promptField));
    fromFile.addView(selectFile, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
    TextView browseDb = button(activity, "Browse DB", "#1B1D1F", "#ECEBE6");
    browseDb.setTag("prompt-body-db");
    boolean texts = !RefBrowser.allFiles(activity, RefBrowser.TEXTS).isEmpty();
    browseDb.setEnabled(texts);
    browseDb.setAlpha(texts ? 1f : 0.4f);
    browseDb.setOnClickListener(v -> {
      // A prompt is a text file: Browse DB starts on T.
      DbFilter.current = "T";
      RefBrowser.browse(activity, RefBrowser.TEXTS, (picked, file) -> PyJavParams.promptFrom(activity, promptField, file));
    });
    LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    browseLp.leftMargin = dp(activity, 8);
    fromFile.addView(browseDb, browseLp);
    col.addView(fromFile, buttonLp(activity));
    PromptVault.Version loaded = vault.version(loadedVersionId);
    if (loaded != null && loaded.promptId == promptId) fill(loaded);
    else {
      if (model != null) model.setText(modelFor(promptId, ""));
      ref1Name = "";
      ref2Name = "";
      resultName = "";
      resultText = "";
      ref1Bytes = new byte[0];
      ref2Bytes = new byte[0];
      resultBytes = new byte[0];
      if (ref1 != null) ref1.setText("No file selected");
      if (ref2 != null) ref2.setText("No file selected");
      if (result != null) result.setText("No file selected");
    }
    resultTextBtn.setText(resultText.length() > 0 ? "Result text · saved" : "Result text");
    TextView save = button(activity, "Save as new version", "#ECEBE6", "#0A0B0C");
    save.setOnClickListener(v -> save(activity));
    col.addView(save, buttonLp(activity));
    TextView export = button(activity, "Export .prompt", "#1B1D1F", "#ECEBE6");
    export.setOnClickListener(v -> exportCurrent(activity));
    col.addView(export, buttonLp(activity));
    TextView openPy = button(activity, "Open in PyJav", "#ECEBE6", "#0A0B0C");
    openPy.setOnClickListener(v -> openInPyJav(activity));
    col.addView(openPy, buttonLp(activity));
    col.addView(caption(activity, "VERSIONS"));
    List<PromptVault.Version> rows = vault.versions(promptId);
    SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
    for (int i = 0; i < rows.size(); i++) {
      PromptVault.Version row = rows.get(i);
      LinearLayout line = new LinearLayout(activity);
      line.setOrientation(LinearLayout.VERTICAL);
      line.setBackground(box(activity));
      line.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
      String refs = (row.ref1Name.length() == 0 ? "no file" : row.ref1Name) + " · " + (row.ref2Name.length() == 0 ? "no file" : row.ref2Name);
      String modelLine = row.model == null || row.model.length() == 0 ? "" : "\n" + row.model;
      String resultLabel = row.resultName == null || row.resultName.length() == 0 ? "" : " · " + row.resultName;
      String typeLabel = row.codeType == null || row.codeType.length() == 0 ? "" : " · " + row.codeType;
      TextView info = label(activity, "v" + (rows.size() - i) + " · " + format.format(new Date(row.created)) + (row.finalVersion ? " · FINAL" : "") + modelLine + typeLabel + "\n" + refs + resultLabel, 13, "#ECEBE6", row.finalVersion);
      long id = row.id;
      info.setOnClickListener(v -> loadVersion(activity, id));
      line.addView(info);
      if (!row.finalVersion) {
        TextView mark = button(activity, "Mark final", "#ECEBE6", "#0A0B0C");
        mark.setOnClickListener(v -> {
          try {
            vault.markFinal(id);
            toast(activity, "Marked final");
            rebuild(activity);
          } catch (Exception ex) {
            toast(activity, ex);
          }
        });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(activity, 36));
        mp.topMargin = dp(activity, 8);
        line.addView(mark, mp);
      }
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.bottomMargin = dp(activity, 8);
      col.addView(line, lp);
    }
  }

  private static void fill(PromptVault.Version version) {
    if (description != null) description.setText(version.description);
    if (prompt != null) prompt.setText(version.body);
    if (model != null) model.setText(modelFor(version.promptId, version.model));
    ref1Name = version.ref1Name == null ? "" : version.ref1Name;
    ref2Name = version.ref2Name == null ? "" : version.ref2Name;
    ref1Bytes = version.ref1 == null ? new byte[0] : version.ref1;
    ref2Bytes = version.ref2 == null ? new byte[0] : version.ref2;
    resultName = version.resultName == null ? "" : version.resultName;
    resultBytes = version.result == null ? new byte[0] : version.result;
    resultText = version.resultText == null ? "" : version.resultText;
    if (ref1 != null) ref1.setText(storedLabel(ref1Name, ref1Bytes));
    if (ref2 != null) ref2.setText(storedLabel(ref2Name, ref2Bytes));
    if (result != null) result.setText(storedLabel(resultName, resultBytes));
    codeType = version.codeType == null ? "" : version.codeType;
    if (codeType.length() == 0) codeType = typeFor(version.promptId);
  }

  private static void buildResultText(Activity activity) {
    int pad = dp(activity, 16);
    LinearLayout col = new LinearLayout(activity);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setPadding(pad, dp(activity, 12), pad, pad);
    col.addView(label(activity, "Result text", 20, "#ECEBE6", true));
    TextView back = button(activity, "Back", "#1B1D1F", "#ECEBE6");
    back.setOnClickListener(v -> {
      resultTextOpen = false;
      rebuild(activity);
    });
    col.addView(back, buttonLp(activity));
    EditText field = area(activity, 16);
    field.setHint("Paste the result here");
    field.setText(resultText);
    field.setVerticalScrollBarEnabled(true);
    field.setTextSize(16);
    LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, 0, 1f);
    fieldLp.bottomMargin = dp(activity, 10);
    col.addView(field, fieldLp);
    TextView paste = button(activity, "Paste", "#1B1D1F", "#ECEBE6");
    paste.setOnClickListener(v -> pasteResult(activity, field));
    col.addView(paste, buttonLp(activity));
    TextView save = button(activity, "Save", "#ECEBE6", "#0A0B0C");
    save.setOnClickListener(v -> saveResultText(activity, field.getText().toString()));
    col.addView(save, buttonLp(activity));
    host.addView(col, new LinearLayout.LayoutParams(-1, -1));
  }

  private static void pasteResult(Activity activity, EditText field) {
    ClipboardManager clips = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clips == null || !clips.hasPrimaryClip()) {
      toast(activity, "Clipboard is empty");
      return;
    }
    ClipData clip = clips.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      toast(activity, "Clipboard is empty");
      return;
    }
    CharSequence value = clip.getItemAt(0).coerceToText(activity);
    if (value == null || value.length() == 0) {
      toast(activity, "Clipboard is empty");
      return;
    }
    int start = field.getSelectionStart();
    int end = field.getSelectionEnd();
    if (start < 0 || end < 0) {
      field.append(value);
      return;
    }
    field.getText().replace(Math.min(start, end), Math.max(start, end), value);
  }

  private static void saveResultText(Activity activity, String text) {
    if (vault == null || promptId == 0) {
      toast(activity, "Open a prompt first");
      return;
    }
    try {
      resultText = text == null ? "" : text;
      if (resultText.length() > 1000000) {
        toast(activity, "Result text is too large");
        return;
      }
      if (loadedVersionId == 0 || vault.version(loadedVersionId) == null) {
        String titleText = text(title).replace('\n', ' ').replace('\r', ' ').trim();
        if (titleText.length() == 0) {
          PromptVault.Prompt item = vault.prompt(promptId);
          titleText = item != null && item.title != null ? item.title : "Untitled";
        }
        loadedVersionId = vault.addVersion(promptId, titleText, text(description), text(prompt), text(model), ref1Name, ref1Bytes, ref2Name, ref2Bytes, resultName, resultBytes, codeType, resultText);
      } else {
        vault.putResultText(loadedVersionId, resultText);
      }
      toast(activity, "Result text saved");
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static boolean inCode(long id) {
    PromptVault.Category category = vault.category(id);
    if (category == null || category.name == null) return false;
    if ("code".equalsIgnoreCase(category.name) && category.parentId == 0) return true;
    if (category.parentId == 0) return false;
    PromptVault.Category parent = vault.category(category.parentId);
    return parent != null && parent.name != null && "code".equalsIgnoreCase(parent.name);
  }

  private static String typeFor(long promptId) {
    PromptVault.Prompt item = vault.prompt(promptId);
    if (item == null || !inCode(item.categoryId)) return "";
    PromptVault.Category category = vault.category(item.categoryId);
    String named = category == null ? "" : PromptRun.normalizeType(category.name);
    return named.length() == 0 ? "bash" : named;
  }

  private static View typeRow(Activity activity) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(caption(activity, "TYPE"));
    if (codeType.length() == 0) codeType = typeFor(promptId);
    String[] types = {"bash", "cmd", "python", "java", "javascript", "typescript", "powershell"};
    FlowLayout row = new FlowLayout(activity, dp(activity, 8), dp(activity, 8));
    for (int i = 0; i < types.length; i++) {
      final String type = types[i];
      boolean on = type.equals(codeType);
      TextView chip = button(activity, type, on ? "#ECEBE6" : "#1B1D1F", on ? "#0A0B0C" : "#ECEBE6");
      chip.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
      chip.setOnClickListener(v -> chooseType(activity, type, row));
      row.addView(chip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 36)));
    }
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
    rowLp.bottomMargin = dp(activity, 10);
    row.setLayoutParams(rowLp);
    box.addView(row);
    return box;
  }

  private static void chooseType(Activity activity, String type, FlowLayout row) {
    codeType = type;
    for (int i = 0; i < row.getChildCount(); i++) {
      View child = row.getChildAt(i);
      if (!(child instanceof TextView)) continue;
      TextView chip = (TextView) child;
      boolean on = type.equals(String.valueOf(chip.getText()));
      chip.setBackground(fill(activity, on ? "#ECEBE6" : "#1B1D1F"));
      chip.setTextColor(Color.parseColor(on ? "#0A0B0C" : "#ECEBE6"));
    }
    try {
      if (loadedVersionId != 0) vault.putCodeType(loadedVersionId, type);
      toast(activity, "Type · " + type);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static View aiBox(Activity activity) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(caption(activity, "TYPE"));
    CheckBox check = new CheckBox(activity);
    check.setText("AI");
    check.setTextColor(Color.parseColor("#ECEBE6"));
    check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#ECEBE6")));
    check.setChecked("ai".equals(codeType));
    check.setOnCheckedChangeListener((button, on) -> {
      codeType = on ? "ai" : "";
      try {
        if (loadedVersionId != 0) vault.putCodeType(loadedVersionId, codeType);
        toast(activity, on ? "AI · test in Grok, not on Android" : "AI off");
      } catch (Exception ex) {
        toast(activity, ex);
      }
    });
    box.addView(check);
    TextView note = label(activity, "Checked means PyJav cannot run this on Android. Test it in the Grok subsystem.", 13, "#8A8B86", false);
    note.setPadding(0, 0, 0, dp(activity, 10));
    box.addView(note);
    return box;
  }

  private static String modelFor(long id, String saved) {
    String current = saved == null ? "" : saved.trim();
    String suggested = "";
    PromptVault.Prompt item = vault.prompt(id);
    if (item != null) {
      PromptVault.Category cat = vault.category(item.categoryId);
      String name = cat == null || cat.name == null ? "" : cat.name;
      if ("image".equalsIgnoreCase(name)) suggested = "Krea 2Identity edit 1.2";
      else if ("video".equalsIgnoreCase(name)) suggested = "MiniMax fast H3";
    }
    if (current.length() == 0) return suggested;
    if (suggested.length() > 0 && !current.equals(suggested)
        && ("Krea 2Identity edit 1.2".equals(current) || "MiniMax fast H3".equals(current))) {
      return suggested;
    }
    return current;
  }

  private static void openPrompt(Activity activity, long id) {
    promptId = id;
    PromptVault.Version version = vault.finalVersion(id);
    loadedVersionId = version == null ? 0 : version.id;
    rebuild(activity);
  }

  private static void loadVersion(Activity activity, long id) {
    loadedVersionId = id;
    rebuild(activity);
  }

  private static void openInPyJav(Activity activity) {
    try {
      String[] sheet = currentSheet();
      if (sheet == null) {
        toast(activity, "Name the prompt");
        return;
      }
      if (activity instanceof MainActivity) ((MainActivity) activity).pyJav.pkOpenPromptText(sheet[0], sheet[1]);
    } catch (Exception ex) {
      Throwable cause = ex.getCause();
      String message = cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
      toast(activity, message == null ? "Could not open that prompt" : message);
    }
  }

  private static String[] currentSheet() {
    String name = text(title).replace('\n', ' ').replace('\r', ' ').trim();
    String fileName = PromptRun.fileName(name);
    if (fileName.length() == 0) return null;
    PromptVault.Prompt item = vault.prompt(promptId);
    String category = "";
    if (item != null) {
      PromptVault.Category cat = vault.category(item.categoryId);
      if (cat != null && cat.name != null) category = cat.name;
    }
    String version = "unsaved";
    List<PromptVault.Version> rows = vault.versions(promptId);
    for (int i = 0; i < rows.size(); i++) {
      PromptVault.Version ver = rows.get(i);
      if (ver.id != loadedVersionId) continue;
      String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ver.created));
      version = "v" + (rows.size() - i) + (ver.finalVersion ? " · FINAL" : "") + " · " + when;
      break;
    }
    String modelName = text(model).replace('\n', ' ').replace('\r', ' ').trim();
    String desc = text(description).replace("\r\n", "\n").replace('\r', '\n').trim();
    String body = text(prompt).replace("\r\n", "\n").replace('\r', '\n');
    String sheet = PromptRun.encode(name, "", "", ref1Name, ref2Name, category, version, modelName, resultName, codeType, body);
    if (desc.length() > 0) {
      String line = "Description: " + desc.replace('\n', ' ').replace('\r', ' ') + "\n";
      int at = sheet.indexOf("\n---\n");
      if (at >= 0) sheet = sheet.substring(0, at + 1) + line + sheet.substring(at + 1);
    }
    return new String[] {fileName, sheet};
  }

  private static void exportCurrent(Activity activity) {
    try {
      String[] sheet = currentSheet();
      if (sheet == null) {
        toast(activity, "Name the prompt");
        return;
      }
      exportBytes = sheet[1].getBytes(StandardCharsets.UTF_8);
      exportName = sheet[0];
      Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
      intent.addCategory("android.intent.category.OPENABLE");
      intent.setType("application/octet-stream");
      intent.putExtra("android.intent.extra.TITLE", sheet[0]);
      activity.startActivityForResult(intent, 28);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static View categoryBlock(Activity activity, boolean assign) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    long selected = categoryId;
    if (assign && promptId != 0) {
      PromptVault.Prompt item = vault.prompt(promptId);
      if (item != null) selected = item.categoryId;
    }
    long mainId = vault.mainOf(selected);
    box.addView(chipRow(activity, vault.mains(), mainId, true));
    List<PromptVault.Category> subs = vault.children(mainId);
    if (!subs.isEmpty()) box.addView(chipRow(activity, subs, selected, false));
    return box;
  }

  private static FlowLayout chipRow(Activity activity, List<PromptVault.Category> categories, long selectedId, boolean mainRow) {
    FlowLayout row = new FlowLayout(activity, dp(activity, 8), dp(activity, 8));
    for (int i = 0; i < categories.size(); i++) {
      PromptVault.Category category = categories.get(i);
      boolean on = category.id == selectedId;
      TextView chip = button(activity, category.name, on ? "#ECEBE6" : "#1B1D1F", on ? "#0A0B0C" : "#ECEBE6");
      chip.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
      long id = category.id;
      String name = category.name == null ? "" : category.name;
      boolean sub = category.parentId != 0;
      chip.setOnClickListener(v -> {
        categoryId = id;
        promptId = 0;
        dbFor = 0;
        rebuild(activity);
      });
      chip.setOnLongClickListener(v -> {
        categoryMenu(activity, id, name, sub);
        return true;
      });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 36));
      row.addView(chip, lp);
    }
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
    rowLp.bottomMargin = dp(activity, mainRow ? 8 : 10);
    row.setLayoutParams(rowLp);
    return row;
  }

  private static void categoryMenu(Activity activity, long id, String name, boolean sub) {
    String kind = sub ? "subcategory" : "category";
    itemMenu(activity, name.length() == 0 ? kind : name, () -> ask(activity, "Rename " + kind, "Name", name, next -> {
      try {
        vault.renameCategory(id, next);
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }), () -> confirm(activity, "Delete " + kind, "Delete " + name + "? Prompts inside it are deleted too.", () -> {
      try {
        long fallback = vault.mainOf(id);
        vault.deleteCategory(id);
        if (vault.category(categoryId) == null) {
          if (vault.category(fallback) != null) categoryId = fallback;
          else {
            List<PromptVault.Category> mains = vault.mains();
            categoryId = mains.isEmpty() ? 0 : mains.get(0).id;
          }
        }
        if (promptId != 0 && vault.prompt(promptId) == null) {
          promptId = 0;
          loadedVersionId = 0;
        }
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }));
  }

  private static void promptMenu(Activity activity, long id, String name) {
    itemMenu(activity, name.length() == 0 ? "Prompt" : name, () -> ask(activity, "Rename prompt", "Title", name, next -> {
      try {
        vault.renamePrompt(id, next);
        String clean = next.replace('\n', ' ').replace('\r', ' ').trim();
        if (promptId == id && title != null) {
          title.setText(clean);
          toast(activity, "Renamed · " + clean);
        } else {
          rebuild(activity);
        }
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }), () -> confirm(activity, "Delete prompt", "Delete " + name + " and its versions?", () -> {
      try {
        vault.deletePrompt(id);
        if (promptId == id) {
          promptId = 0;
          loadedVersionId = 0;
        }
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }));
  }

  private static void slotMenu(Activity activity, int which) {
    String name = which == 1 ? ref1Name : which == 2 ? ref2Name : resultName;
    byte[] bytes = which == 1 ? ref1Bytes : which == 2 ? ref2Bytes : resultBytes;
    if (name == null || name.length() == 0 || bytes == null || bytes.length == 0) {
      toast(activity, "No file selected");
      return;
    }
    String kind = which == 3 ? "result file" : "reference file";
    itemMenu(activity, name, () -> ask(activity, "Rename " + kind, "Name", name, next -> {
      try {
        String clean = loadedVersionId != 0 ? vault.renameFile(loadedVersionId, which, next) : PromptVault.fileTitle(next);
        if (clean.length() == 0) {
          toast(activity, "Name the file");
          return;
        }
        if (which == 1) {
          ref1Name = clean;
          if (ref1 != null) ref1.setText(storedLabel(clean, ref1Bytes));
        } else if (which == 2) {
          ref2Name = clean;
          if (ref2 != null) ref2.setText(storedLabel(clean, ref2Bytes));
        } else {
          resultName = clean;
          if (result != null) result.setText(storedLabel(clean, resultBytes));
        }
        toast(activity, "Renamed · " + clean);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }), () -> confirm(activity, "Delete " + kind, "Delete " + name + "?", () -> {
      try {
        if (loadedVersionId != 0) vault.deleteFile(loadedVersionId, which);
        if (which == 1) {
          ref1Name = "";
          ref1Bytes = new byte[0];
          if (ref1 != null) ref1.setText("No file selected");
        } else if (which == 2) {
          ref2Name = "";
          ref2Bytes = new byte[0];
          if (ref2 != null) ref2.setText("No file selected");
        } else {
          resultName = "";
          resultBytes = new byte[0];
          if (result != null) result.setText("No file selected");
        }
        toast(activity, "Deleted · " + name);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    }));
  }

  private static void storedMenu(Activity activity, long versionId, int which, String name) {
    fileMenu(activity, versionId, which, name, false);
  }

  private static void thumbMenu(Activity activity, long versionId, int which, String name) {
    fileMenu(activity, versionId, which, name, true);
  }

  private static void fileMenu(Activity activity, long versionId, int which, String name, boolean fullSize) {
    String kind = which == 3 ? "result file" : "reference file";
    Runnable rename = () -> ask(activity, "Rename " + kind, "Name", name, next -> {
      try {
        vault.renameFile(versionId, which, next);
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    });
    Runnable delete = () -> confirm(activity, "Delete " + kind, "Delete " + name + "?", () -> {
      try {
        vault.deleteFile(versionId, which);
        if (loadedVersionId == versionId) {
          if (which == 1) {
            ref1Name = "";
            ref1Bytes = new byte[0];
          } else if (which == 2) {
            ref2Name = "";
            ref2Bytes = new byte[0];
          } else {
            resultName = "";
            resultBytes = new byte[0];
          }
        }
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    });
    // A text file with a **Prompt:** (a SogniChat answer.txt): Extract prompt keeps the text after its **Prompt:** as a new file.
    boolean extract = PromptExtract.offered(vault, versionId, which, name);
    java.util.List<CharSequence> items = new java.util.ArrayList<CharSequence>();
    java.util.List<Runnable> runs = new java.util.ArrayList<Runnable>();
    if (fullSize) {
      items.add("Open in full size");
      runs.add(() -> openPreview(activity, name, vault.fileBytes(versionId, which), 3));
    }
    if (extract) {
      items.add("Extract prompt");
      runs.add(() -> extractPrompt(activity, versionId, which, name));
    }
    // Lines that start with **Header:**: Strip headers keeps the text without them as <file> noheaders.txt.
    boolean strip = PromptExtract.stripOffered(vault, versionId, which, name);
    if (strip) {
      items.add("Strip headers");
      runs.add(() -> stripHeaders(activity, versionId, which, name));
    }
    items.add("Rename");
    runs.add(rename);
    items.add("Delete");
    runs.add(delete);
    if (!fullSize && !extract && !strip) {
      itemMenu(activity, name, rename, delete);
      return;
    }
    lastFileMenu = new AlertDialog.Builder(activity)
        .setTitle(name)
        .setItems(items.toArray(new CharSequence[0]), (dialog, pick) -> runs.get(pick).run())
        .show();
  }

  /** Where Sort by (the Ref files and Result files galleries) is kept. */
  static final String SORT_PREFS = "pulsekit-file-sort";

  /** The file menu shown last, for the tests. */
  static AlertDialog lastFileMenu;

  /** Strip headers: the file's text without its line headers kept as <file> noheaders.txt, of the same kind. */
  private static void stripHeaders(Activity activity, long versionId, int which, String name) {
    try {
      String made = PromptExtract.keepStripped(vault, versionId, which, name, storedTitle(versionId, which));
      rebuild(activity);
      toast(activity, "Saved " + made);
      if (activity instanceof MainActivity) ((MainActivity) activity).setNow("Stripped the headers of " + name + " as " + made);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  /** The prompt title (or import note) a stored file is listed under. */
  private static String storedTitle(long versionId, int which) {
    String title = "";
    for (PromptVault.StoredFile f : which == 3 ? vault.resultFiles() : vault.referenceFiles()) {
      if (f.versionId == versionId && f.which == which && f.promptTitle != null) title = f.promptTitle;
    }
    return title;
  }

  /** Extract prompt: the text after the file's last **Prompt:** kept as <File> Prompt <n>.txt, of the same kind. */
  private static void extractPrompt(Activity activity, long versionId, int which, String name) {
    try {
      String title = "";
      for (PromptVault.StoredFile f : which == 3 ? vault.resultFiles() : vault.referenceFiles()) {
        if (f.versionId == versionId && f.which == which && f.promptTitle != null) title = f.promptTitle;
      }
      String made = PromptExtract.keep(vault, versionId, which, name, title);
      rebuild(activity);
      toast(activity, "Saved " + made);
      if (activity instanceof MainActivity) ((MainActivity) activity).setNow("Extracted the prompt of " + name + " as " + made);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static void itemMenu(Activity activity, String title, Runnable rename, Runnable delete) {
    new AlertDialog.Builder(activity)
        .setTitle(title)
        .setItems(new CharSequence[] {"Rename", "Delete"}, (dialog, which) -> {
          if (which == 0) rename.run();
          else delete.run();
        })
        .show();
  }

  private static void confirm(Activity activity, String title, String message, Runnable ok) {
    new AlertDialog.Builder(activity)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Delete", (dialog, which) -> ok.run())
        .setNegativeButton("Cancel", null)
        .show();
  }

  private static void addSubcategory(Activity activity) {
    ask(activity, "Add subcategory", "Name", name -> {
      try {
        long selected = categoryId;
        if (promptId != 0) {
          PromptVault.Prompt item = vault.prompt(promptId);
          if (item != null) selected = item.categoryId;
        }
        categoryId = vault.addSubcategory(selected, name);
        promptId = 0;
        dbFor = 0;
        rebuild(activity);
      } catch (Exception ex) {
        toast(activity, ex);
      }
    });
  }

  private interface NameFn {
    void take(String name);
  }

  private static void ask(Activity activity, String heading, String hint, NameFn fn) {
    ask(activity, heading, hint, "", fn);
  }

  private static void ask(Activity activity, String heading, String hint, String preset, NameFn fn) {
    EditText input = new EditText(activity);
    input.setHint(hint);
    input.setSingleLine(true);
    if (preset != null && preset.length() > 0) {
      input.setText(preset);
      input.setSelection(input.getText().length());
    }
    new AlertDialog.Builder(activity)
        .setTitle(heading)
        .setView(input)
        .setPositiveButton("Save", (dialog, which) -> fn.take(input.getText() == null ? "" : input.getText().toString()))
        .setNegativeButton("Cancel", null)
        .show();
  }

  private static LinearLayout fileBlock(Activity activity, String heading, TextView name, int which) {
    LinearLayout box = new LinearLayout(activity);
    box.setOrientation(LinearLayout.VERTICAL);
    box.addView(caption(activity, heading.toUpperCase()));
    box.addView(name);
    box.addView(caption(activity, "STORED ENCRYPTED"));
    TextView pick = button(activity, "Choose file", "#1B1D1F", "#ECEBE6");
    pick.setOnClickListener(v -> beginPick(activity, which));
    LinearLayout actions = new LinearLayout(activity);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    actions.addView(pick, pickLp);
    if ((which == 1 || which == 2) && vault != null && !vault.referenceFiles().isEmpty()) {
      addDb(activity, actions, which);
    }
    if (which == 3 && vault != null && !vault.resultFiles().isEmpty()) {
      addDb(activity, actions, which);
    }
    TextView menu = button(activity, "Menu", "#1B1D1F", "#ECEBE6");
    int slot = which;
    menu.setOnClickListener(v -> slotMenu(activity, slot));
    LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 44));
    menuLp.leftMargin = dp(activity, 8);
    actions.addView(menu, menuLp);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.topMargin = dp(activity, 6);
    lp.bottomMargin = dp(activity, 8);
    box.addView(actions, lp);
    if (which == 1 || which == 2 || which == 3) {
      TextView preview = button(activity, "Preview", "#1B1D1F", "#ECEBE6");
      preview.setOnClickListener(v -> {
        String pickedName = slot == 1 ? ref1Name : slot == 2 ? ref2Name : resultName;
        byte[] picked = slot == 1 ? ref1Bytes : slot == 2 ? ref2Bytes : resultBytes;
        openPreview(activity, pickedName, picked, 1);
      });
      LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
      previewLp.bottomMargin = dp(activity, 12);
      box.addView(preview, previewLp);
    }
    return box;
  }

  private static void addDb(Activity activity, LinearLayout actions, int which) {
    TextView db = button(activity, "DB", "#ECEBE6", "#0A0B0C");
    db.setOnClickListener(v -> {
      dbFor = which;
      rebuild(activity);
    });
    LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 44));
    dbLp.leftMargin = dp(activity, 8);
    actions.addView(db, dbLp);
  }

  private static String storedLabel(String name, byte[] bytes) {
    if (name == null || name.length() == 0 || bytes == null || bytes.length == 0) return "No file selected";
    return name + " · encrypted";
  }

  private static void buildDb(Activity activity, LinearLayout col) {
    releasePreview();
    TextView back = button(activity, "Back", "#1B1D1F", "#ECEBE6");
    back.setOnClickListener(v -> {
      dbFor = 0;
      rebuild(activity);
    });
    col.addView(back, buttonLp(activity));
    String slot = dbFor == 3 ? "Result file" : dbFor == 2 ? "Reference file 2" : "Reference file 1";
    col.addView(caption(activity, "DATABASE · " + slot.toUpperCase()));
    List<PromptVault.StoredFile> files = dbFor == 3 ? vault.resultFiles() : vault.referenceFiles();
    if (files.isEmpty()) {
      col.addView(label(activity, "none", 14, "#8A8B86", false));
      return;
    }
    for (int i = 0; i < files.size(); i++) {
      PromptVault.StoredFile file = files.get(i);
      String from = file.which == 3 ? "Result file" : file.which == 2 ? "Reference file 2" : "Reference file 1";
      TextView row = button(activity, file.name + "\n" + file.promptTitle + " · " + from + " · " + sizeText(file.size), "#131416", "#ECEBE6");
      row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
      long versionId = file.versionId;
      int which = file.which;
      String name = file.name;
      row.setOnClickListener(v -> useStored(activity, versionId, which, name));
      row.setOnLongClickListener(v -> {
        storedMenu(activity, versionId, which, name);
        return true;
      });
      LinearLayout line = new LinearLayout(activity);
      line.setOrientation(LinearLayout.HORIZONTAL);
      line.addView(row, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      TextView menu = button(activity, "Menu", "#1B1D1F", "#ECEBE6");
      menu.setOnClickListener(v -> storedMenu(activity, versionId, which, name));
      LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(activity, 72), LinearLayout.LayoutParams.MATCH_PARENT);
      menuLp.leftMargin = dp(activity, 8);
      line.addView(menu, menuLp);
      LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
      rowLp.bottomMargin = dp(activity, 8);
      col.addView(line, rowLp);
      TextView preview = button(activity, "Preview", "#1B1D1F", "#ECEBE6");
      preview.setOnClickListener(v -> openPreview(activity, name, vault.fileBytes(versionId, which), 2));
      col.addView(preview, buttonLp(activity));
    }
  }

  private static void useStored(Activity activity, long versionId, int which, String name) {
    byte[] bytes = vault.fileBytes(versionId, which);
    if (bytes.length == 0) {
      toast(activity, "That file is empty");
      return;
    }
    int slot = dbFor == 2 ? 2 : dbFor == 3 ? 3 : 1;
    if (slot == 1) {
      ref1Bytes = bytes;
      ref1Name = name;
    } else if (slot == 2) {
      ref2Bytes = bytes;
      ref2Name = name;
    } else {
      resultBytes = bytes;
      resultName = name;
    }
    dbFor = 0;
    storePickedFile(activity, slot, slot == 1 ? "Reference file 1" : slot == 2 ? "Reference file 2" : "Result file");
    rebuild(activity);
  }

  private static void openPreview(Activity activity, String name, byte[] bytes, int back) {
    if (bytes == null || bytes.length == 0) {
      toast(activity, "No file selected");
      return;
    }
    releasePreview();
    previewName = name == null || name.length() == 0 ? "file" : name;
    previewBytes = bytes;
    previewZoom = 1f;
    // A video opens as the last one was left: its zoom, volume and Loop video.
    if (previewKind(previewName, bytes) == 3) {
      loadVideoPrefs(activity);
      previewZoom = (float) PromptVideo.zoom;
      previewVideoVol = PromptVideo.volume;
    }
    previewBack = back;
    rebuild(activity);
  }

  private static void buildPreview(Activity activity, LinearLayout col) {
    TextView back = button(activity, "Back", "#1B1D1F", "#ECEBE6");
    back.setOnClickListener(v -> {
      releasePreview();
      previewBack = 0;
      previewBytes = new byte[0];
      rebuild(activity);
    });
    col.addView(back, buttonLp(activity));
    col.addView(caption(activity, "PREVIEW"));
    col.addView(label(activity, previewName, 16, "#ECEBE6", true));
    int kind = previewKind(previewName, previewBytes);
    if (kind == 1 || kind == 2 || kind == 3) col.addView(zoomRow(activity));
    previewBody = null;
    previewImage = null;
    previewBox = null;
    if (kind == 1) {
      TextView body = label(activity, previewText(previewBytes), 14, "#ECEBE6", false);
      body.setTextSize(14f * previewZoom);
      body.setPadding(0, dp(activity, 8), 0, 0);
      body.setTag("preview-text");
      readOnlyMenu(activity, body);
      previewBody = body;
      col.addView(body);
    } else if (kind == 2) {
      Bitmap bitmap = previewBitmap(previewBytes);
      if (bitmap == null) {
        col.addView(label(activity, "This image can't be previewed.", 14, "#8A8B86", false));
      } else {
        previewBaseW = Math.max(dp(activity, 120), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
        previewBaseH = Math.max(dp(activity, 80), Math.round(previewBaseW * (bitmap.getHeight() / (float) Math.max(1, bitmap.getWidth()))));
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImageBitmap(bitmap);
        previewImage = image;
        ZoomBox box = new ZoomBox(activity);
        box.setClipChildren(false);
        box.size(Math.round(previewBaseW * previewZoom), Math.round(previewBaseH * previewZoom));
        box.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
        previewBox = box;
        attachPinch(activity, box);
        col.addView(scroller(activity, box));
      }
    } else if (kind == 3) {
      showVideo(activity, col);
    } else if (kind == 4) {
      showSound(activity, col);
    } else {
      col.addView(label(activity, "No preview for this file. Preview works for text, image, video, and sound (including MIDI).", 14, "#8A8B86", false));
    }
  }

  private static LinearLayout zoomRow(Activity activity) {
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    TextView out = button(activity, "Zoom out", "#1B1D1F", "#ECEBE6");
    TextView in = button(activity, "Zoom in", "#ECEBE6", "#0A0B0C");
    zoomLabel = button(activity, zoomPercent(), "#131416", "#ECEBE6");
    out.setOnClickListener(v -> setZoom(activity, previewZoom / 1.25f));
    in.setOnClickListener(v -> setZoom(activity, previewZoom * 1.25f));
    LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 44));
    mid.leftMargin = dp(activity, 8);
    LinearLayout.LayoutParams halfGap = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    halfGap.leftMargin = dp(activity, 8);
    row.addView(out, half);
    row.addView(zoomLabel, mid);
    row.addView(in, halfGap);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.topMargin = dp(activity, 8);
    lp.bottomMargin = dp(activity, 4);
    row.setLayoutParams(lp);
    return row;
  }

  private static void setZoom(Activity activity, float next) {
    if (next < 0.5f) next = 0.5f;
    if (next > 4f) next = 4f;
    previewZoom = next;
    if (previewVideo != null) {
      PromptVideo.zoom = next;
      saveVideoPrefs(activity);
    }
    if (zoomLabel != null) zoomLabel.setText(zoomPercent());
    if (previewBody != null) {
      previewBody.setTextSize(14f * previewZoom);
      previewBody.requestLayout();
    }
    if (previewBox != null) previewBox.size(Math.round(previewBaseW * previewZoom), Math.round(previewBaseH * previewZoom));
  }

  private static String zoomPercent() {
    return Math.round(previewZoom * 100f) + "%";
  }

  private static android.widget.HorizontalScrollView scroller(Activity activity, View child) {
    android.widget.HorizontalScrollView pan = new android.widget.HorizontalScrollView(activity);
    pan.setHorizontalScrollBarEnabled(false);
    pan.setFillViewport(false);
    android.widget.FrameLayout.LayoutParams childLp = new android.widget.FrameLayout.LayoutParams(-2, -2);
    childLp.topMargin = dp(activity, 8);
    pan.addView(child, childLp);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
    pan.setLayoutParams(lp);
    return pan;
  }

  private static void attachPinch(Activity activity, View target) {
    ScaleGestureDetector pinch = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      public boolean onScale(ScaleGestureDetector detector) {
        setZoom(activity, previewZoom * detector.getScaleFactor());
        return true;
      }
    });
    target.setOnTouchListener((v, event) -> {
      if (event.getPointerCount() > 1 && v.getParent() != null) {
        v.getParent().requestDisallowInterceptTouchEvent(true);
        if (v.getParent().getParent() != null) v.getParent().getParent().requestDisallowInterceptTouchEvent(true);
      }
      pinch.onTouchEvent(event);
      return event.getPointerCount() > 1;
    });
  }

  private static void showVideo(Activity activity, LinearLayout col) {
    try {
      previewFile = spill(activity, previewName, previewBytes, "mp4");
      previewBaseW = Math.max(dp(activity, 120), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32));
      previewBaseH = dp(activity, 240);
      VideoView view = new VideoView(activity);
      previewVideo = view;
      ZoomBox box = new ZoomBox(activity);
      box.setClipChildren(false);
      box.size(Math.round(previewBaseW * previewZoom), Math.round(previewBaseH * previewZoom));
      box.addView(view, new android.widget.FrameLayout.LayoutParams(previewBaseW, previewBaseH));
      previewBox = box;
      col.addView(scroller(activity, box));
      view.setVideoPath(previewFile.getAbsolutePath());
      view.setOnPreparedListener(mp -> {
        previewVideoPlayer = mp;
        try {
          mp.setLooping(PromptVideo.loop);
        } catch (Exception ignored) {
          // released
        }
        applyVideoVolume();
        view.seekTo(1);
      });
      view.setOnErrorListener((mp, what, extra) -> {
        toast(activity, "This video can't be previewed.");
        return true;
      });
      addVideoControls(activity, col);
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static void addVideoControls(Activity activity, LinearLayout col) {
    LinearLayout transport = new LinearLayout(activity);
    transport.setOrientation(LinearLayout.HORIZONTAL);
    TextView play = button(activity, "Play", "#ECEBE6", "#0A0B0C");
    TextView pause = button(activity, "Pause", "#1B1D1F", "#ECEBE6");
    TextView stop = button(activity, "Stop", "#1B1D1F", "#ECEBE6");
    play.setOnClickListener(v -> videoPlay());
    pause.setOnClickListener(v -> videoPause());
    stop.setOnClickListener(v -> videoStop());
    LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
    gap.leftMargin = dp(activity, 8);
    transport.addView(play, cell);
    transport.addView(pause, gap);
    transport.addView(stop, gap);
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    rowLp.topMargin = dp(activity, 8);
    transport.setLayoutParams(rowLp);
    col.addView(transport);
    TextView frames = button(activity, "Frames", "#ECEBE6", "#0A0B0C");
    frames.setOnClickListener(v -> extractFrames(activity));
    LinearLayout.LayoutParams framesLp = buttonLp(activity);
    framesLp.topMargin = dp(activity, 8);
    col.addView(frames, framesLp);

    LinearLayout vol = new LinearLayout(activity);
    vol.setOrientation(LinearLayout.HORIZONTAL);
    TextView mute = button(activity, previewVideoMuted ? "Unmute" : "Mute", "#1B1D1F", "#ECEBE6");
    previewMuteBtn = mute;
    mute.setOnClickListener(v -> {
      previewVideoMuted = !previewVideoMuted;
      if (previewMuteBtn != null) previewMuteBtn.setText(previewVideoMuted ? "Unmute" : "Mute");
      applyVideoVolume();
    });
    TextView down = button(activity, "−", "#1B1D1F", "#ECEBE6");
    previewVolLabel = button(activity, previewVideoVol + "%", "#131416", "#ECEBE6");
    TextView up = button(activity, "+", "#ECEBE6", "#0A0B0C");
    down.setOnClickListener(v -> {
      nudgeVideoVolume(-10);
      saveVideoPrefs(activity);
    });
    up.setOnClickListener(v -> {
      nudgeVideoVolume(10);
      saveVideoPrefs(activity);
    });
    LinearLayout.LayoutParams muteLp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1.4f);
    LinearLayout.LayoutParams step = new LinearLayout.LayoutParams(0, dp(activity, 44), 0.7f);
    step.leftMargin = dp(activity, 8);
    vol.addView(mute, muteLp);
    vol.addView(down, step);
    vol.addView(previewVolLabel, step);
    vol.addView(up, step);
    LinearLayout.LayoutParams volLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    volLp.topMargin = dp(activity, 8);
    vol.setLayoutParams(volLp);
    col.addView(vol);
    // Loop video: the video starts again at its end; kept for the next video.
    android.widget.CheckBox loop = new android.widget.CheckBox(activity);
    loop.setText("Loop video");
    loop.setTag("prompt-video-loop");
    loop.setTextColor(Color.parseColor("#ECEBE6"));
    loop.setChecked(PromptVideo.loop);
    loop.setOnCheckedChangeListener((b, on) -> {
      PromptVideo.loop = on;
      saveVideoPrefs(activity);
      try {
        if (previewVideoPlayer != null) previewVideoPlayer.setLooping(on);
      } catch (Exception ignored) {
        // released
      }
    });
    LinearLayout.LayoutParams loopLp = new LinearLayout.LayoutParams(-1, -2);
    loopLp.topMargin = dp(activity, 4);
    col.addView(loop, loopLp);
  }

  private static final String VIDEO_PREFS = "pulsekit-prompt-video";

  /** The Prompts page's video settings (PromptVideo), from the app's preferences. */
  static void loadVideoPrefs(Activity activity) {
    PromptVideo.decode(activity.getSharedPreferences(VIDEO_PREFS, 0).getString("settings", null));
  }

  static void saveVideoPrefs(Activity activity) {
    PromptVideo.volume = previewVideoVol;
    activity.getSharedPreferences(VIDEO_PREFS, 0).edit().putString("settings", PromptVideo.encode()).apply();
  }

  private static void nudgeVideoVolume(int delta) {
    int next = previewVideoVol + delta;
    if (next < 0) next = 0;
    if (next > 100) next = 100;
    previewVideoVol = next;
    if (next > 0) previewVideoMuted = false;
    if (previewMuteBtn != null) previewMuteBtn.setText(previewVideoMuted ? "Unmute" : "Mute");
    applyVideoVolume();
  }

  private static void applyVideoVolume() {
    if (previewVolLabel != null) previewVolLabel.setText(previewVideoVol + "%");
    MediaPlayer mp = previewVideoPlayer;
    if (mp == null) return;
    float level = previewVideoMuted ? 0f : previewVideoVol / 100f;
    try {
      mp.setVolume(level, level);
    } catch (Exception ignored) {}
  }

  private static void videoPlay() {
    VideoView view = previewVideo;
    if (view == null) return;
    try {
      int dur = view.getDuration();
      int pos = view.getCurrentPosition();
      if (dur > 0 && pos >= dur - 250) view.seekTo(0);
      view.start();
    } catch (Exception ignored) {}
  }

  private static void videoPause() {
    VideoView view = previewVideo;
    if (view == null) return;
    try {
      if (view.isPlaying()) view.pause();
    } catch (Exception ignored) {}
  }

  private static void videoStop() {
    VideoView view = previewVideo;
    if (view == null) return;
    try {
      if (view.isPlaying()) view.pause();
      view.seekTo(0);
    } catch (Exception ignored) {}
  }

  private static void extractFrames(Activity activity) {
    File file = previewFile;
    if (file == null || !file.isFile() || vault == null) {
      toast(activity, "This video can't be split into frames.");
      return;
    }
    String source = previewName;
    toast(activity, "Reading frames…");
    new Thread(() -> {
      try {
        byte[][] frames = grabFrames(file);
        activity.runOnUiThread(() -> {
          if (activity.isFinishing()) return;
          try {
            if (frames[0] == null && frames[1] == null) throw new IllegalArgumentException("This video has no frames.");
            String stem = frameStem(source);
            int saved = 0;
            if (frames[0] != null) {
              vault.addReferenceImage(stem + "-first.jpg", frames[0], stem, 1);
              saved++;
            }
            if (frames[1] != null) {
              vault.addReferenceImage(stem + "-last.jpg", frames[1], stem, 2);
              saved++;
            }
            toast(activity, saved == 2 ? "Saved first and last frame as reference files." : "Saved a frame as a reference file.");
          } catch (Exception ex) {
            toast(activity, ex);
          }
        });
      } catch (Exception ex) {
        String detail = ex.getMessage();
        String note = detail != null && detail.length() > 0 ? detail : "Could not read frames.";
        activity.runOnUiThread(() -> {
          if (!activity.isFinishing()) toast(activity, note);
        });
      }
    }).start();
  }

  private static byte[][] grabFrames(File file) throws Exception {
    MediaMetadataRetriever media = new MediaMetadataRetriever();
    try {
      media.setDataSource(file.getAbsolutePath());
      Bitmap first = frameAt(media, 0);
      long durationMs = 0;
      String raw = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (raw != null) {
        try {
          durationMs = Long.parseLong(raw);
        } catch (NumberFormatException ignored) {}
      }
      Bitmap last = durationMs > 0 ? frameAt(media, Math.max(0L, durationMs * 1000L - 1000L)) : null;
      if (first == null) first = last;
      if (last == null) last = first;
      if (first == null) return new byte[][] {null, null};
      if (last == first) {
        byte[] one = jpeg(first);
        return new byte[][] {one, one};
      }
      return new byte[][] {jpeg(first), jpeg(last)};
    } finally {
      try {
        media.release();
      } catch (Exception ignored) {}
    }
  }

  private static Bitmap frameAt(MediaMetadataRetriever media, long timeUs) {
    Bitmap frame = media.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
    if (frame == null) frame = media.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
    return frame;
  }

  private static byte[] jpeg(Bitmap bitmap) {
    if (bitmap == null) return null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) return null;
    byte[] bytes = out.toByteArray();
    return bytes.length == 0 ? null : bytes;
  }

  private static String frameStem(String name) {
    String clean = PromptVault.fileTitle(name);
    int dot = clean.lastIndexOf('.');
    if (dot > 0) clean = clean.substring(0, dot);
    clean = clean.replaceAll("[^A-Za-z0-9._-]", "_");
    if (clean.length() == 0) return "video";
    return clean.length() > 40 ? clean.substring(0, 40) : clean;
  }

  private static boolean isMidi(byte[] bytes) {
    return bytes != null && bytes.length >= 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd';
  }

  private static void showSound(Activity activity, LinearLayout col) {
    try {
      boolean midi = isMidi(previewBytes);
      short[] kit = midi ? kitRender(activity, previewBytes) : null;
      if (kit != null) {
        // The MIDI's drums with Pulsekit's own kit sounds (an imported SoundFont), as a file set plays its source MIDI.
        previewFile = spill(activity, "preview.wav", AudioIo.encodeWav(kit, 22050), "wav");
        col.addView(label(activity, "MIDI drums, played with Pulsekit's kit sounds (your imported SoundFont, if any).", 14, "#8A8B86", false));
      } else {
        // The player tells MIDI by its extension: a MIDI file without one is given .mid.
        previewFile = spill(activity, midi && previewKindByName(previewName) != 4 ? "preview.mid" : previewName, previewBytes, midi ? "mid" : "mp3");
        if (midi) col.addView(label(activity, "MIDI, played with the phone's General MIDI sounds (it has no drums for Pulsekit's kit).", 14, "#8A8B86", false));
      }
      MediaPlayer player = new MediaPlayer();
      previewAudio = player;
      player.setDataSource(previewFile.getAbsolutePath());
      player.setOnPreparedListener(MediaPlayer::start);
      player.setOnErrorListener((mp, what, extra) -> {
        toast(activity, "This sound can't be previewed.");
        return true;
      });
      player.prepareAsync();
      TextView play = button(activity, "Play", "#ECEBE6", "#0A0B0C");
      play.setOnClickListener(v -> {
        if (previewAudio == null) return;
        try {
          previewAudio.seekTo(0);
          previewAudio.start();
        } catch (Exception ex) {
          toast(activity, ex);
        }
      });
      TextView stop = button(activity, "Stop", "#1B1D1F", "#ECEBE6");
      stop.setOnClickListener(v -> {
        if (previewAudio == null) return;
        try {
          if (previewAudio.isPlaying()) previewAudio.pause();
          previewAudio.seekTo(0);
        } catch (Exception ex) {
          toast(activity, ex);
        }
      });
      LinearLayout.LayoutParams playLp = buttonLp(activity);
      playLp.topMargin = dp(activity, 12);
      col.addView(play, playLp);
      col.addView(stop, buttonLp(activity));
    } catch (Exception ex) {
      toast(activity, ex);
    }
  }

  private static int previewKind(String name, byte[] bytes) {
    String lower = name == null ? "" : name.toLowerCase(Locale.US);
    if (ends(lower, ".txt", ".md", ".json", ".prompt", ".csv", ".xml", ".html", ".py", ".java", ".log", ".css", ".js")) return 1;
    if (ends(lower, ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp")) return 2;
    if (ends(lower, ".mp4", ".webm", ".mkv", ".3gp", ".mov")) return 3;
    if (ends(lower, ".wav", ".mp3", ".ogg", ".m4a", ".aac", ".flac", ".mid", ".midi")) return 4;
    // A MIDI file (MThd): Android's player sounds it with its General MIDI synth.
    if (isMidi(bytes)) return 4;
    if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return 2;
    if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return 2;
    if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return 2;
    if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E') return 4;
    if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return 2;
    if (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return 4;
    if (bytes.length >= 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') return 4;
    if (bytes.length >= 4 && bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C') return 4;
    if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') return 3;
    if (looksLikeText(bytes)) return 1;
    return 0;
  }

  /**
   * Taps on a gallery card: a sound (WAV, MP3, MIDI...) plays in place, the card showing "■ playing";
   * a second tap, or a tap on another card, stops it. Other files keep their long-press menu only.
   */
  private static void galleryPlay(Activity activity, long versionId, int which, String name, TextView mark) {
    String key = versionId + ":" + which;
    boolean same = key.equals(galleryPlaying);
    stopGallery();
    if (same) return;
    byte[] bytes = vault.fileBytes(versionId, which);
    if (bytes == null || bytes.length == 0 || previewKind(name, bytes) != 4) return;
    try {
      boolean midi = isMidi(bytes);
      short[] kit = midi ? kitRender(activity, bytes) : null;
      // MIDI drums with the kit's sounds, as in the full preview; a sound file as it is.
      previewFile = kit != null ? spill(activity, "preview.wav", AudioIo.encodeWav(kit, 22050), "wav")
          : spill(activity, midi && previewKindByName(name) != 4 ? "preview.mid" : name, bytes, midi ? "mid" : "mp3");
      MediaPlayer player = new MediaPlayer();
      previewAudio = player;
      player.setDataSource(previewFile.getAbsolutePath());
      player.setOnPreparedListener(MediaPlayer::start);
      player.setOnCompletionListener(mp -> stopGallery());
      player.setOnErrorListener((mp, what, extra) -> {
        toast(activity, "This sound can't be played.");
        stopGallery();
        return true;
      });
      player.prepareAsync();
      galleryPlaying = key;
      galleryMark = mark;
      galleryMarkText = mark.getText().toString();
      mark.setText("\u25a0 playing");
      mark.setVisibility(View.VISIBLE);
    } catch (Exception ex) {
      toast(activity, ex);
      stopGallery();
    }
  }

  /** Stops the gallery's sound and puts its card's label back. */
  private static void stopGallery() {
    if (galleryMark != null && galleryMarkText != null) galleryMark.setText(galleryMarkText);
    galleryMark = null;
    galleryMarkText = null;
    galleryPlaying = null;
    if (previewAudio != null) {
      try {
        previewAudio.release();
      } catch (Exception ignored) {}
      previewAudio = null;
    }
    if (previewFile != null) {
      previewFile.delete();
      previewFile = null;
    }
  }

  /** The MIDI's drum notes rendered with the kit's current sounds, or null when there is no kit or no drums. */
  private static short[] kitRender(Activity activity, byte[] midi) {
    if (!(activity instanceof MainActivity) || ((MainActivity) activity).playback == null) return null;
    try {
      short[] pcm = AudioIo.renderMidiDrums(midi, ((MainActivity) activity).playback.mixVoices(), 22050);
      return pcm == null || pcm.length == 0 ? null : pcm;
    } catch (Exception ex) {
      return null;
    }
  }

  /** 4 when the name alone says sound (.wav, .mp3, .mid...), else 0. */
  private static int previewKindByName(String name) {
    String lower = name == null ? "" : name.toLowerCase(Locale.US);
    return ends(lower, ".wav", ".mp3", ".ogg", ".m4a", ".aac", ".flac", ".mid", ".midi") ? 4 : 0;
  }

  private static boolean ends(String name, String... suffixes) {
    for (int i = 0; i < suffixes.length; i++) {
      if (name.endsWith(suffixes[i])) return true;
    }
    return false;
  }

  private static boolean looksLikeText(byte[] bytes) {
    int n = Math.min(bytes.length, 4096);
    if (n == 0) return false;
    int ok = 0;
    for (int i = 0; i < n; i++) {
      int b = bytes[i] & 0xff;
      if (b == 0) return false;
      if (b == 9 || b == 10 || b == 13 || b >= 32) ok++;
    }
    return ok * 10 >= n * 9;
  }

  private static String previewText(byte[] bytes) {
    int n = Math.min(bytes.length, 64 * 1024);
    String text = new String(bytes, 0, n, StandardCharsets.UTF_8).replace('\u0000', ' ');
    if (bytes.length > n) text = text + "\n\nShowing the start of the file.";
    return text;
  }

  private static Bitmap previewBitmap(byte[] bytes) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    int sample = 1;
    while ((bounds.outWidth / sample) > 2048 || (bounds.outHeight / sample) > 2048) sample *= 2;
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = sample;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
  }

  private static File spill(Activity activity, String name, byte[] bytes, String fallback) throws Exception {
    String ext = fallback;
    int dot = name == null ? -1 : name.lastIndexOf('.');
    if (dot >= 0 && dot + 1 < name.length()) {
      String raw = name.substring(dot + 1);
      String clean = "";
      for (int i = 0; i < raw.length() && clean.length() < 8; i++) {
        char c = raw.charAt(i);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) clean += c;
      }
      if (clean.length() > 0) ext = clean;
    }
    File file = new File(activity.getCacheDir(), "pulsekit-preview." + ext);
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(bytes);
    } finally {
      out.close();
    }
    return file;
  }

  private static void releasePreview() {
    if (previewVideo != null) {
      try {
        previewVideo.stopPlayback();
      } catch (Exception ignored) {}
      previewVideo = null;
    }
    previewVideoPlayer = null;
    previewMuteBtn = null;
    previewVolLabel = null;
    previewBody = null;
    previewImage = null;
    previewBox = null;
    zoomLabel = null;
    galleryPlaying = null;
    galleryMark = null;
    galleryMarkText = null;
    if (previewAudio != null) {
      try {
        previewAudio.release();
      } catch (Exception ignored) {}
      previewAudio = null;
    }
    if (previewFile != null) {
      previewFile.delete();
      previewFile = null;
    }
  }

  private static final class ZoomBox extends android.widget.FrameLayout {
    private int wantW = 1;
    private int wantH = 1;

    ZoomBox(android.content.Context context) {
      super(context);
    }

    void size(int w, int h) {
      wantW = Math.max(1, w);
      wantH = Math.max(1, h);
      requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int w = Math.max(1, wantW);
      int h = Math.max(1, wantH);
      int exactW = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
      int exactH = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
      for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(exactW, exactH);
      setMeasuredDimension(w, h);
    }
  }

  private static String sizeText(int n) {
    if (n < 1024) return n + " B";
    if (n < 1024 * 1024) return (n / 1024) + " KB";
    return (n / (1024 * 1024)) + " MB";
  }

  private static TextView fileName(Activity activity) {
    TextView view = new TextView(activity);
    view.setText("No file selected");
    view.setTextColor(Color.parseColor("#ECEBE6"));
    view.setTextSize(13);
    view.setTypeface(Typeface.MONOSPACE);
    view.setBackground(box(activity));
    view.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
    return view;
  }

  private static EditText line(Activity activity) {
    EditText field = new EditText(activity);
    field.setSingleLine(true);
    field.setTextColor(Color.parseColor("#ECEBE6"));
    field.setHintTextColor(Color.parseColor("#5C5D59"));
    field.setBackground(box(activity));
    field.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
    return field;
  }

  /**
   * A long press on the field: Select all, Copy, Cut, Paste. Copy and Cut work on the selection
   * (greyed without one), Paste puts the clipboard's text in its place. The field also scrolls
   * under a finger, inside the page.
   */
  static void textMenu(Activity activity, EditText field) {
    field.setOnLongClickListener(v -> {
      android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, field);
      int start = Math.min(field.getSelectionStart(), field.getSelectionEnd());
      int end = Math.max(field.getSelectionStart(), field.getSelectionEnd());
      boolean picked = start >= 0 && end > start;
      android.content.ClipboardManager clips = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      boolean canPaste = clips != null && clips.hasPrimaryClip() && clips.getPrimaryClip() != null && clips.getPrimaryClip().getItemCount() > 0;
      menu.getMenu().add(0, 1, 0, "Select all");
      menu.getMenu().add(0, 2, 1, "Copy").setEnabled(picked);
      menu.getMenu().add(0, 3, 2, "Cut").setEnabled(picked);
      menu.getMenu().add(0, 4, 3, "Paste").setEnabled(canPaste);
      menu.setOnMenuItemClickListener(item -> {
        CharSequence text = field.getText() == null ? "" : field.getText();
        int a = Math.max(0, Math.min(field.getSelectionStart(), field.getSelectionEnd()));
        int b = Math.max(0, Math.max(field.getSelectionStart(), field.getSelectionEnd()));
        if (item.getItemId() == 1) {
          field.requestFocus();
          field.selectAll();
        } else if (item.getItemId() == 2 || item.getItemId() == 3) {
          if (clips != null && b > a) clips.setPrimaryClip(android.content.ClipData.newPlainText("prompt", text.subSequence(a, b)));
          if (item.getItemId() == 3 && b > a && field.getText() != null) field.getText().delete(a, b);
        } else if (item.getItemId() == 4 && clips != null && clips.getPrimaryClip() != null && clips.getPrimaryClip().getItemCount() > 0) {
          CharSequence paste = clips.getPrimaryClip().getItemAt(0).coerceToText(activity);
          if (paste != null && field.getText() != null) {
            field.getText().replace(a, b, paste);
            field.setSelection(Math.min(field.length(), a + paste.length()));
          }
        }
        return true;
      });
      lastTextMenu = menu;
      menu.show();
      return true;
    });
    field.setOnTouchListener((v, ev) -> {
      if (v.canScrollVertically(1) || v.canScrollVertically(-1)) v.getParent().requestDisallowInterceptTouchEvent(true);
      if ((ev.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) v.getParent().requestDisallowInterceptTouchEvent(false);
      return false;
    });
  }

  /**
   * A long press on read-only text (a text file shown full size): Select all, Copy. Copy takes
   * the selection, or the whole text when nothing is selected.
   */
  static void readOnlyMenu(Activity activity, TextView view) {
    view.setTextIsSelectable(true);
    view.setOnLongClickListener(v -> {
      android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, view);
      menu.getMenu().add(0, 1, 0, "Select all");
      menu.getMenu().add(0, 2, 1, "Copy");
      menu.setOnMenuItemClickListener(item -> {
        CharSequence text = view.getText() == null ? "" : view.getText();
        if (item.getItemId() == 1) {
          view.requestFocus();
          if (text instanceof android.text.Spannable) android.text.Selection.selectAll((android.text.Spannable) text);
        } else {
          int a = Math.max(0, Math.min(view.getSelectionStart(), view.getSelectionEnd()));
          int b = Math.max(0, Math.max(view.getSelectionStart(), view.getSelectionEnd()));
          CharSequence copied = b > a ? text.subSequence(a, b) : text;
          android.content.ClipboardManager clips = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
          if (clips != null) clips.setPrimaryClip(android.content.ClipData.newPlainText("text", copied));
          toast(activity, "Copied " + copied.length() + " characters");
        }
        return true;
      });
      lastTextMenu = menu;
      menu.show();
      return true;
    });
  }

  /** The text menu shown last, for the tests. */
  static android.widget.PopupMenu lastTextMenu;

  private static EditText area(Activity activity, int lines) {
    EditText field = new EditText(activity);
    field.setMinLines(lines);
    field.setGravity(Gravity.TOP);
    field.setTextColor(Color.parseColor("#ECEBE6"));
    field.setHintTextColor(Color.parseColor("#5C5D59"));
    field.setBackground(box(activity));
    field.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
    return field;
  }

  private static LinearLayout.LayoutParams lineLp(Activity activity) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.bottomMargin = dp(activity, 10);
    return lp;
  }

  private static LinearLayout.LayoutParams areaLp(Activity activity, int height) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, height));
    lp.bottomMargin = dp(activity, 12);
    return lp;
  }

  private static LinearLayout.LayoutParams buttonLp(Activity activity) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
    lp.bottomMargin = dp(activity, 10);
    return lp;
  }

  private static TextView button(Activity activity, String text, String bg, String fg) {
    TextView view = new TextView(activity);
    view.setText(text);
    view.setTextColor(Color.parseColor(fg));
    view.setTextSize(15);
    view.setGravity(Gravity.CENTER);
    view.setBackground(fill(activity, bg));
    return view;
  }

  private static TextView caption(Activity activity, String text) {
    TextView view = label(activity, text, 11, "#5C5D59", true);
    view.setPadding(0, 0, 0, dp(activity, 4));
    return view;
  }

  private static TextView label(Activity activity, String text, int sp, String color, boolean bold) {
    TextView view = new TextView(activity);
    view.setText(text);
    view.setTextSize(sp);
    view.setTextColor(Color.parseColor(color));
    if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
    return view;
  }

  private static GradientDrawable box(Activity activity) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor("#131416"));
    d.setCornerRadius(dp(activity, 8));
    d.setStroke(dp(activity, 1), Color.parseColor("#2A2B2C"));
    return d;
  }

  private static GradientDrawable fill(Activity activity, String color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor(color));
    d.setCornerRadius(dp(activity, 8));
    return d;
  }

  private static String displayName(Activity activity, Uri uri) {
    String name = null;
    android.database.Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
    if (cursor != null) {
      try {
        if (cursor.moveToFirst()) {
          int col = cursor.getColumnIndex("_display_name");
          if (col >= 0) name = cursor.getString(col);
        }
      } finally {
        cursor.close();
      }
    }
    if (name == null || name.length() == 0) name = uri.getLastPathSegment();
    if (name == null || name.length() == 0) name = "file";
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
    if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
    return name;
  }

  private static String text(TextView view) {
    if (view == null || view.getText() == null) return "";
    return view.getText().toString();
  }

  private static String firstLine(String text) {
    int n = text.indexOf('\n');
    String line = n < 0 ? text : text.substring(0, n);
    if (line.length() > 80) line = line.substring(0, 80);
    return line;
  }

  private static void toast(Activity activity, Exception ex) {
    String m = ex.getMessage();
    toast(activity, m != null && m.length() > 0 ? m : "Could not use the prompt database");
  }

  private static void toast(Activity activity, String message) {
    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
  }

  private static int dp(Activity activity, int n) {
    return Math.round(n * activity.getResources().getDisplayMetrics().density);
  }
}
