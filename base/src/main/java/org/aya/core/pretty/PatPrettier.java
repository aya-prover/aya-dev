// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pretty;

import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Matching;
import org.aya.pretty.backend.string.StringLink;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see org.aya.concrete.pretty.PatternPrettier
 */
public final class PatPrettier implements Pat.Visitor<Boolean, Doc> {
  public static final @NotNull PatPrettier INSTANCE = new PatPrettier();

  private PatPrettier() {
  }

  @Override public Doc visitTuple(Pat.@NotNull Tuple tuple, Boolean nested) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.pats().stream().map(Pat::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(TermPrettier.KEYWORD, " \\as "), Doc.plain(tuple.as().name()));
  }

  @Override public Doc visitBind(Pat.@NotNull Bind bind, Boolean aBoolean) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      DefPrettier.plainLink(bind.as()));
  }

  @Override public Doc visitAbsurd(Pat.@NotNull Absurd absurd, Boolean aBoolean) {
    boolean ex = absurd.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.styled(TermPrettier.KEYWORD, "\\impossible"));
  }

  @Override public Doc visitCtor(Pat.@NotNull Ctor ctor, Boolean nestedCall) {
    var hyperLink = Doc.hyperLink(Doc.styled(TermPrettier.CON_CALL,
      ctor.ref().name()), new StringLink("#" + ctor.ref().hashCode()), null);
    var ctorDoc = Doc.cat(hyperLink, visitMaybeCtorPatterns(ctor.params(), true, Doc.plain(" ")));
    return ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as());
  }

  public static @NotNull Doc ctorDoc(Boolean nestedCall, boolean ex, Doc ctorDoc, LocalVar ctorAs) {
    boolean as = ctorAs != null;
    var withEx = Doc.wrap(ex ? "" : "{", ex ? "" : "}", ctorDoc);
    var withAs = as
      ? Doc.cat(Doc.wrap("(", ")", withEx), Doc.plain(" \\as "), Doc.plain(ctorAs.name()))
      : withEx;
    return !ex && !as ? withAs : nestedCall ? Doc.wrap("(", ")", withAs) : withAs;
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pat> patterns, boolean nestedCall, @NotNull Doc delim) {
    return patterns.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "), Doc.join(delim,
      patterns.view().map(p -> p.accept(this, nestedCall))));
  }

  public Doc matchy(@NotNull Matching<Pat, Term> match) {
    var doc = visitMaybeCtorPatterns(match.patterns(), false, Doc.plain(", "));
    return Doc.cat(doc, Doc.plain(" => "), match.body().toDoc());
  }
}
