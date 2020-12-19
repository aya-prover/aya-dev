// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.core.Tele;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.core.term.*;

/**
 * @author re-xyr
 */
public final class RefFinder implements Def.Visitor<@NotNull Buffer<Def>, Unit> {
  private static final class TermRefFinder implements TermConsumer<@NotNull Buffer<Def>> {
    @Override
    public Unit visitRef(@NotNull RefTerm term, @NotNull Buffer<Def> defs) {
      if (term.var() instanceof DefVar<?> ref && ref.def() instanceof Def def) defs.append(def);
      return Unit.unit();
    }
  }
  private final TermRefFinder INSTANCE = new TermRefFinder();

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
      typed.type().accept(INSTANCE, references);
    });
    fn.result.accept(INSTANCE, references);
    if (withBody) {
      fn.body.accept(INSTANCE, references);
    }
    return Unit.unit();
  }
}
