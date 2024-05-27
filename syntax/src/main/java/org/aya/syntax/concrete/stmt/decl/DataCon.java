// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt.decl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.ref.DefVar;
import org.aya.util.Arg;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @implNote {@link DefVar#signature} is always null.
 */
public final class DataCon extends Decl {
  public final @NotNull DefVar<ConDef, DataCon> ref;
  public DefVar<DataDef, DataDecl> dataRef;
  public @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns;
  public final boolean coerce;

  public DataCon(
    @NotNull DeclInfo info,
    @NotNull String name,
    @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    boolean coerce, @Nullable WithPos<Expr> result
  ) {
    super(info, telescope, result);
    this.patterns = patterns;
    this.coerce = coerce;
    this.ref = DefVar.concrete(this, name);
    this.telescope = telescope;
  }

  @Override
  public void descentInPlace(@NotNull PosedUnaryOperator<Expr> f, @NotNull PosedUnaryOperator<Pattern> p) {
    super.descentInPlace(f, p);
    // descent patterns
    patterns = patterns.map(x -> x.descent(wp -> wp.descent(p)));
  }
  @Override public @NotNull DefVar<ConDef, DataCon> ref() { return ref; }
}
