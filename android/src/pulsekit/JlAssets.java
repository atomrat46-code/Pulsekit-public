package pulsekit;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.InputStream;
import javazoom.jl.decoder.JavaLayerHook;
import javazoom.jl.decoder.JavaLayerUtils;

/** JLayer reads Huffman tables with Class.getResourceAsStream, which Android does not serve from the APK. */
public final class JlAssets implements JavaLayerHook {
  private final AssetManager assets;

  private JlAssets(AssetManager assets) {
    this.assets = assets;
  }

  public static void install(Context ctx) {
    if (ctx == null) return;
    JavaLayerUtils.setHook(new JlAssets(ctx.getAssets()));
  }

  public InputStream getResourceAsStream(String name) {
    if (name == null || assets == null) return null;
    String n = name.startsWith("/") ? name.substring(1) : name;
    int slash = n.lastIndexOf('/');
    if (slash >= 0) n = n.substring(slash + 1);
    try {
      return assets.open("jl/" + n);
    } catch (Exception e) {
      return null;
    }
  }
}
