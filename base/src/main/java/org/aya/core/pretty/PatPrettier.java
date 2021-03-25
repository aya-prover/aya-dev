// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

public class PatPrettier implements Pat.Visitor<Boolean, Doc> {
  public static final @NotNull PatPrettier INSTANCE = new PatPrettier();

  private PatPrettier() {
  }

  @Override public Doc visitTuple(Pat.@NotNull Tuple tuple, Boolean nested) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.pats().stream().map(Pat::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(TermPrettier.keyword, " \\as "), Doc.plain(tuple.as().name()));
  }

  @Override public Doc visitBind(Pat.@NotNull Bind bind, Boolean aBoolean) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(bind.as().name()));
  }

  @Override public Doc visitAbsurd(Pat.@NotNull Absurd absurd, Boolean aBoolean) {
    boolean ex = absurd.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.styled(TermPrettier.keyword, "\\impossible"));
  }

  @Override public Doc visitCtor(Pat.@NotNull Ctor ctor, Boolean nestedCall) {
    boolean ex = ctor.explicit();
    boolean as = ctor.as() != null;
    var ctorDoc = Doc.cat(
      Doc.plain(ctor.ref().name()),
      Doc.plain(" "),
      visitMaybeCtorPatterns(ctor.params(), true)
    );
    var withEx = Doc.wrap(ex ? "" : "{", ex ? "" : "}", ctorDoc);
    var withAs = as
      ? Doc.cat(Doc.wrap("(", ")", withEx), Doc.plain(" \\as "), Doc.plain(ctor.as().name()))
      : withEx;
    return !ex && !as ? withAs : nestedCall ? Doc.wrap("(", ")", withAs) : withAs;
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, boolean nestedCall) {
    return patterns.stream()
      .map(p -> p.accept(PatPrettier.INSTANCE, nestedCall))
      .reduce(Doc.empty(), Doc::hsep);
  }

  public Doc matchy(@NotNull Matching<Pat, Option<Term>> match) {
    var doc = visitMaybeCtorPatterns(match.patterns(), false);
    return match.body().map(e -> Doc.cat(doc, Doc.plain(" => "), e.toDoc())).getOrDefault(doc);
  }
}
