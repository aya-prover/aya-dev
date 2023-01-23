// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import org.aya.concrete.stmt.QualifiedID;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * A data class that contains all public definitions/re-exports of some module.
 */
public record MutableModuleExport(@NotNull MutableModuleSymbol<ContextUnit.Outside> symbols) implements ModuleExport {
  public MutableModuleExport() {
    this(new MutableModuleSymbol<>());
  }

  /**
   * @return false if failed
   */
  public boolean export(@NotNull ModulePath component, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    var exists = symbols.add(component, name, ContextUnit.ofOutside(ref));
    return exists.isEmpty();
  }

  public boolean export(@NotNull QualifiedID qualifiedName, @NotNull DefVar<?, ?> ref) {
    return export(qualifiedName.component(), qualifiedName.name(), ref);
  }

  public void exportAnyway(@NotNull ModulePath component, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    symbols.addAnyway(component, name, ContextUnit.ofOutside(ref));
  }
}
