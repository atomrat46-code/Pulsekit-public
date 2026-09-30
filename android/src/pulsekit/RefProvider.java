package pulsekit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Lets Grok or Sogni read a reference file PyJav just decrypted. */
public final class RefProvider extends ContentProvider {
  public static final String AUTHORITY = "pulsekit.app.refs";

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    File file = file(uri);
    if (file == null || !file.isFile()) throw new FileNotFoundException();
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    File file = file(uri);
    if (file == null) return null;
    MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
    cursor.addRow(new Object[] {file.getName(), Long.valueOf(file.length())});
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return typeOf(uri == null ? "" : uri.getLastPathSegment());
  }

  /** MIME type from a file name, including the ref1- / ref2- prefix PyJav stores. */
  public static String typeOf(String name) {
    String file = name == null ? "" : name.toLowerCase(java.util.Locale.US);
    int dot = file.lastIndexOf('.');
    String ext = dot < 0 ? "" : file.substring(dot + 1);
    if (ext.equals("jpg") || ext.equals("jpeg")) return "image/jpeg";
    if (ext.equals("png")) return "image/png";
    if (ext.equals("webp")) return "image/webp";
    if (ext.equals("gif")) return "image/gif";
    if (ext.equals("heic") || ext.equals("heif")) return "image/heic";
    if (ext.equals("mp4") || ext.equals("m4v")) return "video/mp4";
    if (ext.equals("mov")) return "video/quicktime";
    if (ext.equals("webm")) return "video/webm";
    if (ext.equals("mp3")) return "audio/mpeg";
    if (ext.equals("wav")) return "audio/wav";
    if (ext.equals("m4a")) return "audio/mp4";
    if (ext.equals("pdf")) return "application/pdf";
    if (ext.equals("txt") || ext.equals("md")) return "text/plain";
    return "application/octet-stream";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  private File file(Uri uri) {
    if (uri == null || getContext() == null) return null;
    String name = uri.getLastPathSegment();
    if (name == null || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf("..") >= 0) return null;
    if (!name.startsWith("ref1-") && !name.startsWith("ref2-")) return null;
    return new File(new File(getContext().getCacheDir(), "pyjav-in"), name);
  }
}
