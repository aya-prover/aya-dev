// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 * Used for examples and counterexamples
 */
public record NoExportContext(
  @NotNull PhysicalModuleContext parent,
  @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions,
  @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules
) implements ModuleContext {
  @Override
  public @NotNull ImmutableSeq<String> moduleName() {
    return parent.moduleName().appended(":NoExport");
  }

  public NoExportContext(@NotNull PhysicalModuleContext parent) {
    this(parent, MutableMap.create(),
      MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.create()));
  }

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }
}
