// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.trace;

import kala.collection.mutable.MutableList;
import org.aya.distill.AyaDistillerOptions;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public class MarkdownTrace {
  public final int indent;
  public final @NotNull DistillerOptions options;
  public static final @NotNull Doc plus = Doc.symbol("+");
  public static final @NotNull Doc colon = Doc.symbol(":");
  private final @NotNull Doc vdash, equiv, uparr;

  public MarkdownTrace(int indent, @NotNull DistillerOptions options, boolean asciiOnly) {
    this.indent = indent;
    this.options = options;
    vdash = asciiOnly ? Doc.symbol("|-") : Doc.symbol("\u22A2");
    equiv = asciiOnly ? Doc.symbol("==") : Doc.symbol("\u2261");
    uparr = asciiOnly ? Doc.symbol("^") : Doc.symbol("\u2191");
  }

  public MarkdownTrace() {
    this(2, AyaDistillerOptions.informative(), false);
  }

  private @NotNull Doc indentedChildren(MutableList<@NotNull Trace> children) {
    return Doc.nest(indent, Doc.vcatNonEmpty(children.view().map(this::docify)));
  }

  public @NotNull Doc docify(@NotNull Trace trace) {
    return switch (trace) {
      case Trace.DeclT t -> Doc.vcatNonEmpty(Doc.sep(plus, BaseDistiller.varDoc(t.var())),
        indentedChildren(t.children()));
      case Trace.LabelT t -> Doc.vcatNonEmpty(Doc.sep(plus, Doc.english(t.label())),
        indentedChildren(t.children()));
      case Trace.ExprT t -> {
        var buf = MutableList.of(plus, vdash, Doc.code(t.expr().toDoc(options)));
        if (t.term() != null) {
          buf.append(colon);
          buf.append(Doc.code(t.term().toDoc(options)));
        }
        yield Doc.vcatNonEmpty(Doc.sep(buf), indentedChildren(t.children()));
      }
      case Trace.PatT t -> Doc.vcatNonEmpty(Doc.sep(plus, Doc.plain("pat"), vdash,
          Doc.code(t.pat().toDoc(options)), colon,
          Doc.code(t.type().toDoc(options))),
        indentedChildren(t.children()));
      case Trace.TyckT t -> {
        assert t.children().isEmpty();
        yield Doc.sep(plus, Doc.plain("result"), vdash,
          Doc.code(t.term().toDoc(options)), uparr,
          Doc.code(t.type().toDoc(options)));
      }
      case Trace.UnifyT t -> {
        var buf = MutableList.of(plus,
          vdash,
          Doc.code(t.lhs().toDoc(options)),
          equiv,
          Doc.code(t.rhs().toDoc(options)));
        if (t.type() != null) {
          buf.append(colon);
          buf.append(Doc.code(t.type().toDoc(options)));
        }
        yield Doc.vcatNonEmpty(Doc.sep(buf), indentedChildren(t.children()));
      }
    };
  }

  public @NotNull Doc docify(Trace.@NotNull Builder traceBuilder) {
    return Doc.vcatNonEmpty(traceBuilder.root().view().map(this::docify));
  }
}
