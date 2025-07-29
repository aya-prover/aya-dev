// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.context;

import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

public interface ModuleContextView extends ContextView {
  @Override @NotNull ContextView parent();

  /// All available symbols in this context
  @NotNull ModuleSymbol<AnyVar> symbols();

  /// All imported modules in this context.
  /// `Qualified Module -> Module Export`
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules();
}
