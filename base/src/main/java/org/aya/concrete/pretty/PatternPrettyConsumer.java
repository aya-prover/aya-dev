// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.Pattern;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public class PatternPrettyConsumer implements
  Pattern.Visitor<Unit, Doc>,
  Pattern.Clause.Visitor<Unit, Doc> {
  public static final PatternPrettyConsumer INSTANCE = new PatternPrettyConsumer();

  @Override
  public Doc visitTuple(Pattern.@NotNull Tuple tuple, Unit unit) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      tuple.patterns().stream()
        .map(Pattern::toDoc)
        .reduce(Doc.empty(), (acc, doc) -> Doc.join(Doc.plain(","), acc, doc))
    );
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.plain(" as "), Doc.plain(tuple.as().name()));
  }

  @Override
  public Doc visitNumber(Pattern.@NotNull Number number, Unit unit) {
    boolean ex = number.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(String.valueOf(number.number())));
  }

  @Override
  public Doc visitBind(Pattern.@NotNull Bind bind, Unit unit) {
    boolean ex = bind.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(bind.bind().name()));
  }

  @Override
  public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Unit unit) {
    boolean ex = calmFace.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(Constants.ANONYMOUS_PREFIX));
  }

  @Override
  public Doc visitCtor(Pattern.@NotNull Ctor ctor, Unit unit) {
    boolean ex = ctor.explicit();
    var c = Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      visitMaybeCtorPatterns(ctor.params()));
    return ctor.as() == null ? c
      : Doc.cat(Doc.wrap("(", ")", c), Doc.plain(" as "), Doc.plain(ctor.as().name()));
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns) {
    return patterns.stream()
      .map(Pattern::toDoc)
      .reduce(Doc.empty(), Doc::hsep);
  }

  @Override
  public Doc visitMatch(Pattern.Clause.@NotNull Match match, Unit unit) {
    return Doc.cat(visitMaybeCtorPatterns(match.patterns()),
      Doc.plain(" => "),
      match.expr().toDoc());
  }

  @Override
  public Doc visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Unit unit) {
    return Doc.plain(Constants.ANONYMOUS_PREFIX);
  }
}
