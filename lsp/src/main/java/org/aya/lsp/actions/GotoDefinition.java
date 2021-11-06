// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.WithPos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.server.AyaService;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.XY;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ice1000, kiva
 */
public class GotoDefinition implements StmtConsumer<XY> {
  public final @NotNull DynamicSeq<WithPos<Var>> locations = DynamicSeq.create();

  @NotNull
  public static List<LocationLink> invoke(@NotNull DefinitionParams params, @NotNull AyaService.AyaFile loadedFile) {
    var locator = new GotoDefinition();
    locator.visitAll(loadedFile.concrete(), new XY(params.getPosition()));
    return locator.locations.view().mapNotNull(pos -> {
      var target = switch (pos.data()) {
        case DefVar<?, ?> defVar -> defVar.concrete.sourcePos();
        case LocalVar localVar -> localVar.definition();
        case default -> null;
      };
      if (target == null) return null;
      var res = LspRange.toLoc(pos.sourcePos(), target);
      if (res != null) Log.d("Resolved: %s in %s", target, res.getTargetUri());
      return res;
    }).collect(Collectors.toList());
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

  @Override public Unit visitBind(@NotNull Pattern.Bind bind, XY xy) {
    if (bind.resolved().value instanceof DefVar<?, ?> defVar)
      check(xy, bind.sourcePos(), defVar);
    return StmtConsumer.super.visitBind(bind, xy);
  }

  @Override public Unit visitCtor(@NotNull Pattern.Ctor ctor, XY xy) {
    if (ctor.resolved().value != null)
      check(xy, ctor.name().sourcePos(), ctor.resolved().get());
    return StmtConsumer.super.visitCtor(ctor, xy);
  }

  private void check(@NotNull XY xy, @NotNull SourcePos sourcePos, Var var) {
    if (xy.inside(sourcePos)) locations.append(new WithPos<>(sourcePos, var));
  }
}
