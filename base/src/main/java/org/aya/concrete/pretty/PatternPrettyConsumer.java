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
  Pattern.Visitor<Boolean, Doc>,
  Pattern.Clause.Visitor<Unit, Doc> {
  public static final PatternPrettyConsumer INSTANCE = new PatternPrettyConsumer();

  @Override
  public Doc visitTuple(Pattern.@NotNull Tuple tuple, Boolean nestedCall) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.patterns().stream().map(Pattern::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.plain(" \\as "), Doc.plain(tuple.as().name()));
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

  @Override
  public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Boolean nestedCall) {
    boolean ex = calmFace.explicit();
    return Doc.wrap(ex ? "" : "{", ex ? "" : "}",
      Doc.plain(Constants.ANONYMOUS_PREFIX));
  }

  @Override
  public Doc visitCtor(Pattern.@NotNull Ctor ctor, Boolean nestedCall) {
    boolean ex = ctor.explicit();
    boolean as = ctor.as() != null;
    var ctorDoc = Doc.cat(
      Doc.plain(ctor.name()),
      Doc.plain(" "),
      visitMaybeCtorPatterns(ctor.params(), true)
    );
    var withEx = Doc.wrap(ex ? "" : "{", ex ? "" : "}", ctorDoc);
    var withAs = as
      ? Doc.cat(Doc.wrap("(", ")", withEx), Doc.plain(" \\as "), Doc.plain(ctor.as().name()))
      : withEx;
    return !ex && !as ? withAs : nestedCall ? Doc.wrap("(", ")", withAs) : withAs;
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, boolean nestedCall) {
    return patterns.stream()
      .map(p -> p.accept(PatternPrettyConsumer.INSTANCE, nestedCall))
      .reduce(Doc.empty(), Doc::hsep);
  }

  @Override
  public Doc visitMatch(Pattern.Clause.@NotNull Match match, Unit unit) {
    return Doc.cat(visitMaybeCtorPatterns(match.patterns(), false),
      Doc.plain(" => "),
      match.expr().toDoc());
  }

  @Override
  public Doc visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Unit unit) {
    return Doc.plain(Constants.ANONYMOUS_PREFIX);
  }
}
