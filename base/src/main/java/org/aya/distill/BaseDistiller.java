// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.control.Option;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.Arg;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

public interface BaseDistiller {
  @NotNull Style KEYWORD = Style.preset("aya:Keyword");
  @NotNull Style FN_CALL = Style.preset("aya:FnCall");
  @NotNull Style DATA_CALL = Style.preset("aya:DataCall");
  @NotNull Style STRUCT_CALL = Style.preset("aya:StructCall");
  @NotNull Style CON_CALL = Style.preset("aya:ConCall");
  @NotNull Style FIELD_CALL = Style.preset("aya:FieldCall");
  @NotNull Style GENERALIZED = Style.preset("aya:Generalized");
  @Contract(pure = true) @NotNull DistillerOptions options();

  default @NotNull Doc univDoc(boolean nestedCall, String head, @NotNull AyaDocile lvl) {
    var hd = Doc.styled(KEYWORD, head);
    if (!options().showLevels()) return hd;
    return visitCalls(hd, Seq.of(new Arg<>(lvl, true)),
      (nc, l) -> l.toDoc(options()), nestedCall);
  }

  default <T extends AyaDocile> @NotNull Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
    @NotNull BiFunction<Boolean, T, Doc> formatter, boolean nestedCall
  ) {
    if (args.isEmpty()) return fn;
    var call = Doc.sep(
      fn, Doc.sep(args.view().flatMap(arg -> {
        // Do not use `arg.term().toDoc()` because we want to
        // wrap args in parens if we are inside a nested call
        // such as `suc (suc (suc n))`
        if (arg.explicit()) return Option.of(formatter.apply(true, arg.term()));
        if (options().showImplicitArgs()) return Option.of(Doc.braced(formatter.apply(false, arg.term())));
        return Option.none();
      }))
    );
    return nestedCall ? Doc.parened(call) : call;
  }
}
