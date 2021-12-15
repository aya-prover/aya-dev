// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.DynamicSeq;
import kala.control.Option;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.XY;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ice1000, kiva
 */
public class GotoDefinition implements StmtConsumer<XY> {
  public final @NotNull DynamicSeq<WithPos<Var>> locations = DynamicSeq.create();

  @NotNull
  public static List<LocationLink> invoke(@NotNull DefinitionParams params, @NotNull LibrarySource loadedFile) {
    var locator = new GotoDefinition();
    var program = loadedFile.program().value;
    if (program == null) return Collections.emptyList();

    locator.visitAll(program, new XY(params.getPosition()));
    return locator.locations.view().mapNotNull(pos -> {
      var target = switch (pos.data()) {
        case DefVar<?, ?> defVar -> {
          if (defVar.concrete != null) yield defVar.concrete.sourcePos();
          else yield searchLibrary(loadedFile, defVar);
        }
        case LocalVar localVar -> localVar.definition();
        case null, default -> null;
      };
      if (target == null) return null;
      var res = LspRange.toLoc(pos.sourcePos(), target);
      if (res != null) Log.d("Resolved: %s in %s", target, res.getTargetUri());
      return res;
    }).collect(Collectors.toList());
  }

  private static @Nullable SourcePos searchLibrary(@NotNull LibrarySource loadedFile, @NotNull DefVar<?, ?> defVar) {
    var owner = loadedFile.owner();
    return Option.of(owner.findModule(defVar.module))
      .mapNotNull(m -> m.tycked().value)
      .mapNotNull(defs -> defs.find(def -> def.ref().name().equals(defVar.name())).getOrNull())
      .mapNotNull(def -> def.ref().concrete)
      .mapNotNull(concrete -> concrete.sourcePos)
      .getOrNull();
  }

  @Override public @NotNull Unit visitRef(@NotNull Expr.RefExpr expr, XY xy) {
    check(xy, expr.sourcePos(), expr.resolvedVar());
    return Unit.unit();
  }

  @Override public @NotNull Unit visitProj(@NotNull Expr.ProjExpr expr, XY xy) {
    if (expr.ix().isRight()) {
      var pos = expr.ix().getRightValue();
      check(xy, pos.sourcePos(), expr.resolvedIx().get());
    }
    return StmtConsumer.super.visitProj(expr, xy);
  }

  @Override public void visitPattern(@NotNull Pattern pattern, XY xy) {
    switch (pattern) {
      case Pattern.Ctor ctor -> {
        check(xy, ctor.resolved().sourcePos(), ctor.resolved().data());
        ctor.params().forEach(pat -> visitPattern(pat, xy));
      }
      case Pattern.Tuple tup -> tup.patterns().forEach(p -> visitPattern(p, xy));
      case Pattern.BinOpSeq seq -> seq.seq().forEach(p -> visitPattern(p, xy));
      default -> {}
    }
  }

  private void check(@NotNull XY xy, @NotNull SourcePos sourcePos, Var var) {
    if (xy.inside(sourcePos)) locations.append(new WithPos<>(sourcePos, var));
  }
}
