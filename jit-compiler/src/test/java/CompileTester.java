// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.serializers.AyaSerializer;
import org.aya.compiler.serializers.NameSerializer;
import org.aya.prelude.GeneratedVersion;
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
  public static Path GEN_DIR = Paths.get("build/tmp/testGenerated");

  private final Path baka;
  public final ClassLoader cl;

  public CompileTester(@NotNull String code) throws IOException {
    var genDir = GEN_DIR.resolve(AyaSerializer.PACKAGE_BASE);
    FileUtil.writeString(baka = genDir.resolve("_baka.java"), code);
    cl = new URLClassLoader(new URL[]{GEN_DIR.toUri().toURL()});
  }

  public void compile() {
    try {
      var compiler = ToolProvider.getSystemJavaCompiler();
      var fileManager = compiler.getStandardFileManager(null, null, null);
      var compilationUnits = fileManager.getJavaFileObjects(baka);
      var options = List.of("--enable-preview", "--release", GeneratedVersion.JDK_VERSION);
      var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
      task.call();
      var fqName = NameSerializer.getModuleClassName(DumbModuleLoader.DUMB_MODULE_NAME);
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
