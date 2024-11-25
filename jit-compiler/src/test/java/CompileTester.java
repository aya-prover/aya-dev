// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.AyaSerializer;
import org.aya.compiler.NameSerializer;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.compile.JitDef;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CompileTester {
  private final Path baka;
  public final ClassLoader cl;

  public CompileTester(@NotNull String code) throws IOException {
    var root = Paths.get("build/tmp/testGenerated");
    var genDir = root.resolve(AyaSerializer.PACKAGE_BASE);
    FileUtil.writeString(baka = genDir.resolve("$baka.java"), code);
    cl = new URLClassLoader(new URL[]{root.toUri().toURL()});
  }

  public void compile() {
    try {
      var compiler = ToolProvider.getSystemJavaCompiler();
      var fileManager = compiler.getStandardFileManager(null, null, null);
      var compilationUnits = fileManager.getJavaFileObjects(baka);
      var options = List.of("--enable-preview", "--release", "21");
      var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
      task.call();
      var fqName = NameSerializer.getClassRef(DumbModuleLoader.DUMB_MODULE_NAME, null);
      cl.loadClass(fqName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> @NotNull Class<T> load(String qualified) {
    try {
      return (Class<T>) cl.loadClass(qualified);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends JitDef> T getInstance(@NotNull Class<T> clazz) {
    try {
      Field field = clazz.getField(AyaSerializer.STATIC_FIELD_INSTANCE);
      field.setAccessible(true);
      return (T) field.get(null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public <T extends JitDef> T loadInstance(String qualified) {
    return getInstance(load(qualified));
  }
}
