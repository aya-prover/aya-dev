// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import org.glavo.kala.tuple.Unit;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.api.ref.Var;
import org.mzi.core.def.DataDef;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;

/**
 * @author re-xyr
 * @see RefFinder#HEADER_ONLY
 * @see RefFinder#HEADER_AND_BODY
 */
public record RefFinder(boolean withBody) implements Def.Visitor<@NotNull Buffer<Def>, Unit> {
  private static final class TermRefFinder implements VarConsumer<@NotNull Buffer<Def>> {
    public static final @NotNull TermRefFinder INSTANCE = new TermRefFinder();

    @Override public void visitVar(Var usage, @NotNull Buffer<Def> defs) {
      if (usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def) defs.append(def);
    }
  }

  public static RefFinder HEADER_ONLY = new RefFinder(false);
  public static RefFinder HEADER_AND_BODY = new RefFinder(true);

  @Override public Unit visitFn(@NotNull FnDef fn, @NotNull Buffer<Def> references) {
    fn.telescope().forEach(param -> param.type().accept(TermRefFinder.INSTANCE, references));
    fn.result().accept(TermRefFinder.INSTANCE, references);
    if (withBody) {
      fn.body().accept(TermRefFinder.INSTANCE, references);
    }
    return Unit.unit();
  }

  @Override public Unit visitData(@NotNull DataDef def, @NotNull Buffer<Def> references) {
    def.telescope().forEach(param -> param.type().accept(TermRefFinder.INSTANCE, references));
    // TODO: data def
    return Unit.unit();
  }
}
