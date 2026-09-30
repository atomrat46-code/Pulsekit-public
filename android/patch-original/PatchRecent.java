package pulsekit.patch;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.NewExpr;

/** Points the already-patched PyJav spinner at a dialog list and records AI runs. */
public final class PatchRecent {
  public static void main(String[] args) throws Exception {
    ClassPool pool = ClassPool.getDefault();
    String[] paths = args[0].split(":");
    for (int i = 0; i < paths.length; i++) pool.insertClassPath(paths[i]);
    CtClass ctClass = pool.get("pulsekit.MainActivity");
    CtMethod wire = ctClass.getDeclaredMethod("pkWirePyJav");
    wire.instrument(new ExprEditor() {
      @Override
      public void edit(NewExpr expr) throws javassist.CannotCompileException {
        if ("android.widget.Spinner".equals(expr.getClassName())) {
          expr.replace("{ $_ = new android.widget.Spinner($1, 1); }");
        }
      }
    });
    wire.insertAfter("{ if (this.pkPyRecent != null && this.pyPane != null && this.pkPyRecent.getParent() == this.pyPane) { this.pyPane.removeView(this.pkPyRecent); int at = this.pyPane.getChildCount() > 0 ? 1 : 0; this.pyPane.addView(this.pkPyRecent, at); this.pkPyRecent.setBackgroundColor(0xFF1B1D1F); } }");
    CtMethod run = ctClass.getDeclaredMethod("pkRunPyJav");
    run.instrument(new ExprEditor() {
      @Override
      public void edit(javassist.expr.MethodCall call) throws javassist.CannotCompileException {
        if ("chooseAi".equals(call.getMethodName())) {
          call.replace("{ pulsekit.PyJavRecent.remember(this.getFilesDir(), this.pyName == null ? \"prompt.prompt\" : this.pyName, \"\", this.pkPromptSource == null ? $2 : this.pkPromptSource, null); this.pkRefreshRecent(0); $proceed($$); }");
        }
      }
    });
    ctClass.writeFile(args[1]);
  }
}
