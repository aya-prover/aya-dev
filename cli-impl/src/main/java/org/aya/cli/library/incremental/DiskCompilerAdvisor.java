// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.utils.CompilerUtil;
import org.aya.compiler.CompiledModule;
import org.aya.compiler.serializers.ModuleSerializer;
import org.aya.compiler.serializers.NameSerializer;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.core.def.TopLevelDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QPath;
import org.aya.util.FileUtil;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiskCompilerAdvisor implements CompilerAdvisor {
  private static class AyaClassLoader extends URLClassLoader {
    public MutableList<Path> urls = MutableList.create();
    public AyaClassLoader() {
      super(new URL[0], DiskCompilerAdvisor.class.getClassLoader());
    }
    public void addURL(Path url) throws MalformedURLException {
      addURL(url.toUri().toURL());
      urls.append(url);
    }
  }
  private final AyaClassLoader cl = new AyaClassLoader();
  @Override public void close() throws Exception { cl.close(); }

  @Override public boolean isSourceModified(@NotNull LibrarySource source) {
    try {
      var core = source.compiledCorePath();
      if (!Files.exists(core)) return true;
      return Files.getLastModifiedTime(source.underlyingFile)
        .compareTo(Files.getLastModifiedTime(core)) > 0;
    } catch (IOException ignore) {
      return true;
    }
  }

  @Override public void updateLastModified(@NotNull LibrarySource source) {
    try {
      var core = source.compiledCorePath();
      Files.setLastModifiedTime(core, Files.getLastModifiedTime(source.underlyingFile));
    } catch (IOException ignore) {
    }
  }

  @Override public void prepareLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    Files.createDirectories(owner.outDir());
  }

  @Override public void clearLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    FileUtil.deleteRecursively(owner.outDir());
  }

  @Override public void clearModuleOutput(@NotNull LibrarySource source) throws IOException {
    Files.deleteIfExists(source.compiledCorePath());
  }

  /**
   * Load all core file to the ClassLoader
   *
   * @return a compiled ResolveInfo
   */
  private @NotNull ResolveInfo doLoadCompiledCore(
    @NotNull CompiledModule compiledAya,
    @NotNull Reporter reporter,
    @NotNull ModulePath mod,
    @NotNull Path sourcePath,
    @NotNull Path libraryRoot,
    @NotNull ModuleLoader recurseLoader,
    @NotNull PrimFactory primFactory
  ) throws ClassNotFoundException, MalformedURLException {
    var context = new EmptyContext(sourcePath).derive(mod);
    var coreDir = computeBaseDir(libraryRoot);
    cl.addURL(coreDir);
    cl.loadClass(NameSerializer.getModuleClassName(QPath.fileLevel(mod)));
    return compiledAya.toResolveInfo(recurseLoader, context, cl, primFactory, reporter);
  }

  @Override public @Nullable ResolveInfo doLoadCompiledCore(
    @NotNull Reporter reporter,
    @NotNull LibraryOwner owner, @NotNull ModulePath mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException {
    if (corePath == null || sourcePath == null) return null;
    if (!Files.exists(corePath)) return null;

    try (var inputStream = FileUtil.ois(corePath)) {
      var compiledAya = (CompiledModule) inputStream.readObject();
      var parentCount = mod.size();
      var libraryRoot = corePath;
      for (int i = 0; i < parentCount; i++) libraryRoot = libraryRoot.getParent();
      return doLoadCompiledCore(compiledAya, reporter, mod, sourcePath, libraryRoot, recurseLoader, new PrimFactory());
    }
  }

  @Override public @NotNull ResolveInfo doSaveCompiledCore(
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<TyckDef> defs,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException {
    var javaCode = new ModuleSerializer(resolveInfo.shapeFactory())
      .serializeWithBestBuilder(new ModuleSerializer.ModuleResult(
        QPath.fileLevel(file.moduleName()),
        defs.filterIsInstance(TopLevelDef.class)));
    var libraryRoot = file.owner.outDir();
    javaCode.writeTo(computeBaseDir(libraryRoot).toAbsolutePath());
    var coreFile = file.compiledCorePath();

    // save compiled core and load compiled ResolveInfo
    var coreMod = CompilerUtil.saveCompiledCore(coreFile, defs, resolveInfo);
    return doLoadCompiledCore(
      coreMod, resolveInfo.reporter(),
      resolveInfo.modulePath(), file.underlyingFile, libraryRoot,
      recurseLoader, resolveInfo.primFactory()
    );
  }

  private static @NotNull Path computeBaseDir(@NotNull Path outDir) {
    return outDir.resolve("compiled");
  }
}
