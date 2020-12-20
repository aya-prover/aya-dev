// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.api.ref.Var;
import org.mzi.core.Tele;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;

/**
 * @author re-xyr
 */
public final class RefFinder implements Def.Visitor<@NotNull Buffer<Def>, Unit> {
  private static final class TermRefFinder implements VarConsumer<@NotNull Buffer<Def>> {
    public static final @NotNull TermRefFinder INSTANCE = new TermRefFinder();

    @Override public void visitVar(Var usage, @NotNull Buffer<Def> defs) {
      if (usage instanceof DefVar<?> ref && ref.def() instanceof Def def) defs.append(def);
    }
  }

  public static RefFinder HEADER_ONLY = new RefFinder(false);
  public static RefFinder HEADER_AND_BODY = new RefFinder(true);

  private final boolean withBody;

  private RefFinder(boolean withBody) {
    this.withBody = withBody;
  }

  @Override
  public Unit visitFn(@NotNull FnDef fn, @NotNull Buffer<Def> references) {
    fn.telescope.forEach((ix, tele) -> {
      if (!(tele instanceof Tele.TypedTele typed)) return;
      typed.type().accept(TermRefFinder.INSTANCE, references);
    });
    fn.result.accept(TermRefFinder.INSTANCE, references);
    if (withBody) {
      fn.body.accept(TermRefFinder.INSTANCE, references);
    }
    return Unit.unit();
  }
}
