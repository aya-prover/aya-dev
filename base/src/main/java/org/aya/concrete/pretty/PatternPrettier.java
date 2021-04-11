// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.pretty.PatPrettier;
import org.aya.core.pretty.TermPrettier;
import org.aya.generic.Matching;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 * @see PatPrettier
 */
public final class PatternPrettier implements Pattern.Visitor<Boolean, Doc> {
  public static final @NotNull PatternPrettier INSTANCE = new PatternPrettier();

  private PatternPrettier() {
  }

  @Override public Doc visitTuple(Pattern.@NotNull Tuple tuple, Boolean nestedCall) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.patterns().view().map(Pattern::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(TermPrettier.KEYWORD, " as "), Doc.plain(tuple.as().name()));
  }

  @Override public Doc visitNumber(Pattern.@NotNull Number number, Boolean nestedCall) {
    boolean ex = number.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(String.valueOf(number.number())));
  }

  @Override public Doc visitBind(Pattern.@NotNull Bind bind, Boolean nestedCall) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(bind.bind().name()));
  }

  @Override public Doc visitAbsurd(Pattern.@NotNull Absurd absurd, Boolean aBoolean) {
    boolean ex = absurd.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.styled(TermPrettier.KEYWORD, "impossible"));
  }

  @Override public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Boolean nestedCall) {
    boolean ex = calmFace.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(Constants.ANONYMOUS_PREFIX));
  }

  @Override public Doc visitCtor(Pattern.@NotNull Ctor ctor, Boolean nestedCall) {
    var ctorDoc = Doc.cat(
      Doc.styled(TermPrettier.CON_CALL, ctor.name()),
      visitMaybeCtorPatterns(ctor.params(), true, Doc.plain(" "))
    );
    return PatPrettier.ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, boolean nestedCall, @NotNull Doc delim) {
    return patterns.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "), Doc.join(delim,
      patterns.view().map(p -> p.accept(this, nestedCall))));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns(), false, Doc.plain(", "));
    return match.expr().map(e -> Doc.cat(doc, Doc.plain(" => "), e.toDoc())).getOrDefault(doc);
  }

  public Doc matchy(Matching<Pattern, Expr> match) {
    return matchy(new Pattern.Clause(SourcePos.NONE, match.patterns(), Option.some(match.body())));
  }
}
