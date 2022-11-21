// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.generic.util.AyaFiles;
import org.aya.generic.util.InternalException;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.tyck.trace.Trace;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public record FileModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull Path basePath,
  @Override @NotNull Reporter reporter,
  @NotNull GenericAyaParser parser,
  @NotNull PrimDef.Factory primFactory,
  Trace.@Nullable Builder builder
) implements ModuleLoader {
  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePath, path);
    try {
      var program = parser.program(locator, sourcePath);
      var context = new EmptyContext(reporter, sourcePath).derive(path);
      var shapeFactory = new AyaShape.Factory();
      var opSet = new AyaBinOpSet(reporter);
      return tyckModule(builder, resolveModule(primFactory, shapeFactory, opSet, context, program, recurseLoader), null);
    } catch (IOException e) {
      return null;
    }
  }

  public static void handleInternalError(@NotNull InternalException e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}
