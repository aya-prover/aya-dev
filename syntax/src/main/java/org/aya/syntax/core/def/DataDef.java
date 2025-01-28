// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * core data definition, corresponding to {@link DataDecl}
 *
 * @author kiva
 */
public record DataDef(
  @Override @NotNull DefVar<DataDef, DataDecl> ref,
  @NotNull ImmutableSeq<ConDef> body
) implements TopLevelDef {
  public DataDef { ref.initialize(this); }

  @Override public @NotNull SortTerm result() { return (SortTerm) TopLevelDef.super.result(); }

  public static final class Delegate extends TyckAnyDef<DataDef> implements DataDefLike {
    public Delegate(@NotNull DefVar<DataDef, ?> ref) { super(ref); }
    @Override public @NotNull ImmutableSeq<ConDef.Delegate>
    body() { return ref.core.body.map(x -> new ConDef.Delegate(x.ref)); }
  }
}
