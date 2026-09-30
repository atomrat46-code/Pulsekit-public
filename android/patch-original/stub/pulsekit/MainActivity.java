package pulsekit;

import android.app.Activity;

/** Compile-time stub for AnalyzeClicks. Not packaged. */
public class MainActivity extends Activity {
  public void pickAnalyzeFile() {}

  public void runAnalyze() {}

  public void pickComposeFile() {}

  public void runCompose() {}

  public void openKitView(String view) {}

  public void openFileSetInfo(String key, String label) {}

  public void closeFileSetInfo() {}

  public void setNow(String text) {}

  public void makeFileSetSong(String key, String label) {}

  public String[] styleDbNames() { return new String[0]; }

  public int currentStyleDbIndex(String key) { return -1; }

  public void applyStylePick(String key, String label, int index) {}

  public void applyFileSetStyle(String key, String label, String kit, String styleName, int hats, float four, float dkick, int styleBpm, float styleBack) {}

  public void combineFileSetTracks(String key, String label) {}

  public void playCombinedFile(String src) {}

  public void stopCombinedFile() {}

  public void playSourceMidi(String src) {}

  public void pauseSourceMidi() {}

  public void stopSourceMidi() {}

  public void saveCombinedFile(String src) {}

  public void setMixLevel(int kind, int value) {}

  public void applyMixLevels(String src) {}

  public boolean fileSetIsCompose(String key) { return false; }
  public boolean fileSetStyleOn(String key) { return true; }

  public void addImportedMidiSong(String name, java.util.List parts) {}

  public void openPrompts() {}

  public void takePromptRef(android.net.Uri uri) {}

  public void pkSetPromptRun(String mode) {}

  public void pkRunPyJav() {}

  public void pkShowAiResult(String line) {}

  public void pkAcceptOutput(String answer) {}

  public void pkCancelOutput() {}

  public void pkBrowsePyJav() {}

  public void pkBrowseInput() {}

  public void pkLoadFilePattern(String id) {}

  public void pkLoadFileFill(String id) {}

  public void pkLoadFileFillern(String id) {}

  public void pkOpenParams() {}

  public void pkSetPyArgs(String args) {}

  public void pkShowPyResult(pulsekit.JavaRun.Result result) {}

  public void pkApplyRecent(int index) {}
}
