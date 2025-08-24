// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.syntax.SyntaxNodeAction;
import org.aya.ide.util.XYXY;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.core.term.Term;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

public record InlayHints(
  @NotNull PrettierOptions options,
  @NotNull XYXY location,
  @NotNull MutableList<Hint> hints
) implements SyntaxNodeAction.Ranged {
  public static @NotNull ImmutableSeq<Hint> invoke(@NotNull PrettierOptions options, @NotNull LibrarySource source, @NotNull XYXY range) {
    var program = source.program();
    if (program == null) return ImmutableSeq.empty();
    var maker = new InlayHints(options, range, MutableList.create());
    program.forEach(maker);
    return maker.hints.toSeq();
  }
  @Override public void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    if (pat instanceof Pattern.Bind bind && bind.theCoreType().get() instanceof Term term) {
      var type = Doc.sep(Tokens.HAS_TYPE, term.toDoc(options));
      hints.append(new Hint(pos, type, true));
    }
    Ranged.super.visitPattern(pos, pat);
  }

  @Override
  public void visitLetBind(Expr.@NotNull LetBind bind) {
    if (bind.result().data() instanceof Expr.Hole) {
      // the user doesn't give the type explicitly
      var term = bind.theCoreType().get();
      if (term != null) {
        // TODO: how do we get the result of [bind] rather than the type of [bind]
        hints.append(new Hint(bind.nameSourcePos(), Doc.sep(Tokens.HAS_TYPE, term.toDoc(options)), true));
      }
    }

    Ranged.super.visitLetBind(bind);
  }

  @Override
  public void visitParam(Expr.@NotNull Param param) {
    if (param.type() instanceof Expr.Hole && param.theCoreType().get() instanceof Term core) {
      hints.append(new Hint(param.nameSourcePos(), Doc.sep(Tokens.HAS_TYPE, core.toDoc(options)), true));
    }

    var what = Integer.bitCount(1);

    Ranged.super.visitParam(param);
  }

  @Override
  public void visitUntypedParamDecl(Expr.@NotNull UntypedParam param) {
    if (param.coreType() instanceof Term coa) {
      hints.append(new Hint(param.nameSourcePos(), Doc.sep(Tokens.HAS_TYPE, coa.toDoc(options)), true));
    }

    Ranged.super.visitUntypedParamDecl(param);
  }

  public record Hint(
    @NotNull SourcePos sourcePos,
    @NotNull Doc doc,
    boolean isType
  ) { }
}
