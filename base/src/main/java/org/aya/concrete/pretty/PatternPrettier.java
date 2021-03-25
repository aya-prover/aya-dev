// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.Pattern;
import org.aya.core.pretty.PatPrettier;
import org.aya.core.pretty.TermPrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.NotNull;

public final class PatternPrettier implements Pattern.Visitor<Boolean, Doc> {
  public static final @NotNull PatternPrettier INSTANCE = new PatternPrettier();

  private PatternPrettier() {
  }

  @Override
  public Doc visitTuple(Pattern.@NotNull Tuple tuple, Boolean nestedCall) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.patterns().stream().map(Pattern::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(TermPrettier.KEYWORD, " \\as "), Doc.plain(tuple.as().name()));
  }

  @Override
  public Doc visitNumber(Pattern.@NotNull Number number, Boolean nestedCall) {
    boolean ex = number.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(String.valueOf(number.number())));
  }

  @Override
  public Doc visitBind(Pattern.@NotNull Bind bind, Boolean nestedCall) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(bind.bind().name()));
  }

  @Override public Doc visitAbsurd(Pattern.@NotNull Absurd absurd, Boolean aBoolean) {
    boolean ex = absurd.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.styled(TermPrettier.KEYWORD, "\\impossible"));
  }

  @Override
  public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Boolean nestedCall) {
    boolean ex = calmFace.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(Constants.ANONYMOUS_PREFIX));
  }

  @Override
  public Doc visitCtor(Pattern.@NotNull Ctor ctor, Boolean nestedCall) {
    var ctorDoc = Doc.cat(
      Doc.plain(ctor.name()),
      Doc.plain(" "),
      visitMaybeCtorPatterns(ctor.params(), true)
    );
    return PatPrettier.ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as());
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, boolean nestedCall) {
    return patterns.stream()
      .map(p -> p.accept(this, nestedCall))
      .reduce(Doc.empty(), Doc::hsep);
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns(), false);
    return match.expr().map(e -> Doc.cat(doc, Doc.plain(" => "), e.toDoc())).getOrDefault(doc);
  }
}
