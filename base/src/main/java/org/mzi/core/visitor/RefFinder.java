// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.core.def.Def;
import org.mzi.core.term.*;

/**
 * @author re-xyr
 */
public final class RefFinder implements TermConsumer<@NotNull Buffer<Def>> {
  public static RefFinder INSTANCE = new RefFinder();

  private RefFinder() {}

  @Override
  public Unit visitRef(@NotNull RefTerm term, @NotNull Buffer<Def> defs) {
    if (term.var() instanceof DefVar<?> ref && ref.def() instanceof Def def) defs.append(def); // [xyr]: why is this an error?
    return Unit.unit();
  }
}
