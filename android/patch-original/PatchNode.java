package pulsekit.patch;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/** Lets the Android file picker open JavaScript and TypeScript in PyJav. */
public final class PatchNode {
  public static void main(String[] args) throws Exception {
    ClassPool pool = ClassPool.getDefault();
    for (String path : args[0].split(":")) pool.insertClassPath(path);
    CtClass ctClass = pool.get("pulsekit.MainActivity");
    CtMethod method = ctClass.getDeclaredMethod("pkTakePickedProgram");
    method.insertBefore("{\n"
        + "  byte[] pkData = this.readUri($1);\n"
        + "  String pkName = null;\n"
        + "  android.database.Cursor pkCur = this.getContentResolver().query($1, null, null, null, null);\n"
        + "  if (pkCur != null) {\n"
        + "    try {\n"
        + "      if (pkCur.moveToFirst()) {\n"
        + "        int col = pkCur.getColumnIndex(\"_display_name\");\n"
        + "        if (col >= 0) pkName = pkCur.getString(col);\n"
        + "      }\n"
        + "    } finally { pkCur.close(); }\n"
        + "  }\n"
        + "  if (pkName == null) pkName = $1.getLastPathSegment();\n"
        + "  if (pkName == null) pkName = \"program.js\";\n"
        + "  int pkSlash = Math.max(pkName.lastIndexOf(47), pkName.lastIndexOf(58));\n"
        + "  if (pkSlash >= 0) pkName = pkName.substring(pkSlash + 1);\n"
        + "  String pkLow = pkName.toLowerCase();\n"
        + "  boolean pkJs = pkLow.endsWith(\".js\") || pkLow.endsWith(\".mjs\") || pkLow.endsWith(\".cjs\") || pkLow.endsWith(\".jsx\") || pkLow.endsWith(\".ts\") || pkLow.endsWith(\".mts\") || pkLow.endsWith(\".tsx\");\n"
        + "  if (pkJs && pkData != null) {\n"
        + "    this.pyName = pkName;\n"
        + "    this.pkPyBytes = null;\n"
        + "    this.pkPyInputPath = null;\n"
        + "    String pkText = new String(pkData, java.nio.charset.StandardCharsets.UTF_8);\n"
        + "    if (this.pyEditor != null) this.pyEditor.setText(pkText);\n"
        + "    this.show(\"py\");\n"
        + "    return true;\n"
        + "  }\n"
        + "}");
    ctClass.writeFile(args[1]);
  }
}
