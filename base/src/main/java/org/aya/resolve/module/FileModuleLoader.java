// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.control.Result;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.error.LoadErrorKind;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.position.SourceFileLocator;
import org.aya.util.reporter.ClearableReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @Override @NotNull ClearableReporter reporter,
  @NotNull GenericAyaParser parser,
  @NotNull GenericAyaFile.Factory fileManager,
  @NotNull PrimFactory primFactory
) implements ModuleLoader {
  public FileModuleLoader(
    @NotNull SourceFileLocator locator, @NotNull Path basePath, @NotNull ClearableReporter reporter,
    @NotNull GenericAyaParser parser, @NotNull GenericAyaFile.Factory fileManager
  ) {
    this(locator, basePath, reporter, parser, fileManager, new PrimFactory());
  }

  @Override public @NotNull Result<ResolveInfo, LoadErrorKind> load(
    @NotNull ModulePath path,
    @NotNull ModuleLoader recurseLoader
  ) {
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
    try {
      var program = fileManager.createAyaFile(locator, sourcePath).parseMe(parser).program();
      var context = new EmptyContext(sourcePath).derive(path);
      var info = resolveModule(primFactory, context, program, recurseLoader);
      if (info == null) return Result.err(LoadErrorKind.Resolve);
      return Result.ok(tyckModule(info, null));
    } catch (IOException e) {
      return Result.err(LoadErrorKind.NotFound);
    }
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    try {
      var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path.module());
      return Files.exists(sourcePath);
    } catch (InvalidPathException e) {
      return false;
    }
  }
}
