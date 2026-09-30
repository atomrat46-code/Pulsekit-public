package pulsekit.patch;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

/** Shows JavaScript, TypeScript, and the Sogni client on the Android PyJav screen. */
public final class PatchPyJavText {
  public static void main(String[] args) throws Exception {
    ClassPool pool = ClassPool.getDefault();
    for (String path : args[0].split(":")) pool.insertClassPath(path);
    CtClass ctClass = pool.get("pulsekit.MainActivity");
    if (ctClass.getDeclaredMethods("pkLoadSogniClient").length == 0) {
      ctClass.addMethod(CtNewMethod.make(
          "public void pkLoadSogniClient(android.view.View v) {\n"
              + "  this.pyName = \"sogni-client.mjs\";\n"
              + "  this.pkPyBytes = null;\n"
              + "  this.pkPyInputPath = null;\n"
              + "  String src = \"import * as sogni from \\\"@sogni-ai/sogni-client\\\";\\n\\n"
              + "const names = Object.keys(sogni).sort();\\n"
              + "console.log(\\\"Sogni client loaded\\\");\\n"
              + "console.log(names.length ? names.join(\\\", \\\") : \\\"(no named exports)\\\");\\n"
              + "if (!process.env.SOGNI_APP_ID) {\\n"
              + "  console.log(\\\"Set SOGNI_APP_ID, SOGNI_USERNAME, and SOGNI_PASSWORD to sign in.\\\");\\n"
              + "}\\n\";\n"
              + "  if (this.pyEditor != null) this.pyEditor.setText(src);\n"
              + "  this.setNow(\"sogni-client.mjs\");\n"
              + "}\n",
          ctClass));
    }
    String termuxHint =
        "public void pkRefreshNodeUi() {\n"
            + "  if (this.pyPane == null) return;\n"
            + "  String next = \"Python, Java, JavaScript, or TypeScript. On Android, Node.js runs in Termux: pkg install nodejs. npm installs @sogni-ai/sogni-client. Java runs in the app.\";\n"
            + "  for (int i = 0; i < this.pyPane.getChildCount(); i++) {\n"
            + "    android.view.View child = this.pyPane.getChildAt(i);\n"
            + "    if (!(child instanceof android.widget.TextView)) continue;\n"
            + "    android.widget.TextView label = (android.widget.TextView) child;\n"
            + "    String s = label.getText() == null ? \"\" : label.getText().toString();\n"
            + "    if (s.indexOf(\"Python\") >= 0 && s.indexOf(\"Java\") >= 0 && s.indexOf(\"Termux\") < 0) label.setText(next);\n"
            + "  }\n"
            + "  if (this.pyPane.findViewWithTag(\"sogni-client\") != null) return;\n"
            + "  android.widget.TextView btn = this.text(\"Sogni client\", 13, true);\n"
            + "  btn.setTag(\"sogni-client\");\n"
            + "  btn.setGravity(17);\n"
            + "  btn.setBackground(this.round(ELEV, 8));\n"
            + "  btn.setOnClickListener(new pulsekit.SogniClientClick(this));\n"
            + "  int at = this.pyPane.getChildCount() > 2 ? 2 : this.pyPane.getChildCount();\n"
            + "  android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(40));\n"
            + "  lp.bottomMargin = this.dp(8);\n"
            + "  this.pyPane.addView(btn, at, lp);\n"
            + "}\n";
    if (ctClass.getDeclaredMethods("pkRefreshNodeUi").length == 0) {
      ctClass.addMethod(CtNewMethod.make(termuxHint, ctClass));
      CtMethod wire = ctClass.getDeclaredMethod("pkWirePyJav");
      wire.insertAfter("{ this.pkRefreshNodeUi(); }");
    } else {
      ctClass.getDeclaredMethod("pkRefreshNodeUi").setBody(termuxHint.substring(termuxHint.indexOf('{'), termuxHint.lastIndexOf('}') + 1));
    }
    try {
      CtMethod take = ctClass.getDeclaredMethod("pkTakeProgram");
      take.insertBefore(
          "{ if ($1 != null && $2 != null) {\n"
              + "  String low = $2.toLowerCase();\n"
              + "  boolean js = low.endsWith(\".js\") || low.endsWith(\".mjs\") || low.endsWith(\".cjs\") || low.endsWith(\".jsx\") || low.endsWith(\".ts\") || low.endsWith(\".mts\") || low.endsWith(\".tsx\");\n"
              + "  if (js) {\n"
              + "    int slash = Math.max($2.lastIndexOf(47), $2.lastIndexOf(58));\n"
              + "    String base = slash >= 0 ? $2.substring(slash + 1) : $2;\n"
              + "    this.pyName = base;\n"
              + "    this.pkPyBytes = null;\n"
              + "    this.pkPyInputPath = null;\n"
              + "    if (this.pyEditor != null) this.pyEditor.setText(new String($1, java.nio.charset.StandardCharsets.UTF_8));\n"
              + "    this.show(\"py\");\n"
              + "    return true;\n"
              + "  }\n"
              + "} }");
    } catch (javassist.CannotCompileException already) {
      if (already.getMessage() == null || already.getMessage().indexOf("already") < 0) throw already;
    }
    ctClass.writeFile(args[1]);
  }
}
