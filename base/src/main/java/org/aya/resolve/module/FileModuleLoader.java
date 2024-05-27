// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.Panic;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @Override @NotNull Reporter reporter,
  @NotNull GenericAyaParser parser,
  @NotNull GenericAyaFile.Factory fileManager,
  @NotNull PrimFactory primFactory
) implements ModuleLoader {
  public FileModuleLoader(
    @NotNull SourceFileLocator locator, @NotNull Path basePath, @NotNull Reporter reporter,
    @NotNull GenericAyaParser parser, @NotNull GenericAyaFile.Factory fileManager
  ) {
    this(locator, basePath, reporter, parser, fileManager, new PrimFactory());
  }
  @Override
  public @Nullable ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
    try {
      var program = fileManager.createAyaFile(locator, sourcePath).parseMe(parser);
      var context = new EmptyContext(reporter, sourcePath).derive(path);
      return tyckModule(resolveModule(primFactory, context, program, recurseLoader), null);
    } catch (IOException e) {
      return null;
    }
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
    return Files.exists(sourcePath);
  }

  public static void handleInternalError(@NotNull Panic e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}
