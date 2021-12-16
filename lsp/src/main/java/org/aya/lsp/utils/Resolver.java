// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.control.Option;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.StructDef;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public interface Resolver {
  /** resolve a symbol by its qualified name in the whole library */
  static @NotNull Option<@NotNull Def> resolveDef(
    @NotNull LibraryOwner owner,
    @NotNull ImmutableSeq<String> module,
    @NotNull String name
  ) {
    var mod = findModule(owner, module);
    return mod.mapNotNull(m -> m.tycked().value)
      .map(defs -> defs.flatMap(Resolver::withSubLevel))
      .flatMap(defs -> defs.find(def -> def.ref().name().equals(name)));
  }

  /** resolve the position to the minimal term that contains the position */
  static @NotNull SeqView<Expr> resolveExpr(
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var program = source.program().value;
    if (program == null) return SeqView.empty();
    var resolver = new ExprPositionResolver();
    resolver.visitAll(program, new XY(position));
    return resolver.stack.view();
  }

  static @NotNull Option<Expr> resolveAppHead(
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var expr = Resolver.resolveExpr(source, position);
    if (expr.isEmpty()) return Option.none();
    var app = expr.reversed().filterIsInstance(Expr.AppExpr.class).firstOrNull();
    if (app == null) return Option.none();
    return Option.some(Expr.unapp(app, null));
  }

  /** resolve the position to its referring target */
  static @NotNull SeqView<WithPos<@NotNull Var>> resolveVar(
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var program = source.program().value;
    if (program == null) return SeqView.empty();
    var resolver = new DefPositionResolver();
    resolver.visitAll(program, new XY(position));
    return resolver.locations.view().mapNotNull(pos -> switch (pos.data()) {
      case DefVar<?, ?> defVar -> {
        if (defVar.concrete != null) yield new WithPos<>(pos.sourcePos(), defVar);
        else {
          // defVar is an imported and serialized symbol, so we need to find the original one
          yield Resolver.resolveDef(source.owner(), defVar.module, defVar.name())
            .map(target -> new WithPos<Var>(pos.sourcePos(), target.ref()))
            .getOrNull();
        }
      }
      case LocalVar localVar -> new WithPos<>(pos.sourcePos(), localVar);
      case null, default -> null;
    });
  }

  private static @NotNull SeqView<Def> withSubLevel(@NotNull Def def) {
    return switch (def) {
      case DataDef data -> SeqView.<Def>of(data).appendedAll(data.body);
      case StructDef struct -> SeqView.<Def>of(struct).appendedAll(struct.fields);
      default -> SeqView.of(def);
    };
  }

  private static @NotNull Option<LibrarySource> findModule(@NotNull LibraryOwner owner, @NotNull ImmutableSeq<String> module) {
    if (module.isEmpty()) return Option.none();
    var mod = owner.findModule(module);
    return mod != null ? Option.of(mod) : findModule(owner, module.dropLast(1));
  }

  /**
   * Resolve position to the minimal term that contains the position
   */
  class ExprPositionResolver implements StmtConsumer<XY> {
    private final @NotNull DynamicSeq<Expr> stack = DynamicSeq.create();

    @Override public void traceEntrance(@NotNull Expr expr, @NotNull XY xy) {
      if (xy.inside(expr.sourcePos())) {
        if (stack.isEmpty()) stack.append(expr);
        else if (expr.sourcePos().compareTo(stack.last().sourcePos()) > 0) stack.append(expr);
      }
    }
  }

  /**
   * Resolve position to its referring target
   *
   * @author ice1000, kiva
   */
  class DefPositionResolver implements StmtConsumer<XY> {
    public final @NotNull DynamicSeq<WithPos<Var>> locations = DynamicSeq.create();

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
}
