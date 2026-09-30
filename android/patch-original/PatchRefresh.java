package pulsekit.patch;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/** Replaces the spinner refresh with a visible list of recent programs. */
public final class PatchRefresh {
  public static void main(String[] args) throws Exception {
    ClassPool pool = ClassPool.getDefault();
    for (String path : args[0].split(":")) pool.insertClassPath(path);
    CtClass ctClass = pool.get("pulsekit.MainActivity");
    CtMethod method = ctClass.getDeclaredMethod("pkRefreshRecent");
    method.setBody("{\n  if (this.pkPyRecent == null) return;\n  this.pkPyRecentItems = pulsekit.PyJavRecent.load(this.getFilesDir());\n  pulsekit.PyJavUi.fillRecent(this, this.pkPyRecent, this.pkPyRecentItems);\n}");
    ctClass.writeFile(args[1]);
  }
}
