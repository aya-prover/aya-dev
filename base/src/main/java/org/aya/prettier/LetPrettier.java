// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public interface LetPrettier<Bind, Term, LetTerm extends Expr.Nested<Bind, Term, LetTerm>> {
  @NotNull Doc bar();
  @NotNull Doc kwLet();
  @NotNull Doc kwIn();

  @NotNull Doc visitBind(@NotNull Bind bind);
  @NotNull Doc term(@NotNull Term term);

  default @NotNull Doc visitLet(@NotNull LetTerm term) {
    var pair = Expr.destructNested(term);
    var binds = pair.component1();
    var body = pair.component2();
    var oneLine = binds.sizeEquals(1);
    var docBinds = visitBinds(binds);
    var docs = ImmutableSeq.of(kwLet(), docBinds, kwIn());
    var head = oneLine ? Doc.sep(docs) : Doc.vcat(docs);

    return Doc.sep(head, term(body));
  }

  default @NotNull Doc visitBinds(@NotNull ImmutableSeq<Bind> binds) {
    assert binds.isNotEmpty() : "???";
    if (binds.sizeEquals(1)) {
      return visitBind(binds.first());
    } else {
      return Doc.vcat(
        binds.view()
          .map(this::visitBind)
          .map(x -> Doc.sep(bar(), x))
      );
    }
  }
}
