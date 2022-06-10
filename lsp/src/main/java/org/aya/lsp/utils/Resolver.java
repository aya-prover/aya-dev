// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.Signatured;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.visitor.StmtOps;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.StructDef;
import org.aya.generic.Constants;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
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
    var mod = resolveModule(owner, module);
    return mod.mapNotNull(m -> m.tycked().value)
      .map(defs -> defs.flatMap(Resolver::withSubLevel))
      .flatMap(defs -> defs.find(def -> def.ref().name().equals(name)));
  }

  /** resolve the position to its referring target */
  static @NotNull SeqView<WithPos<@NotNull Var>> resolveVar(
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var program = source.program().value;
    if (program == null) return SeqView.empty();
    var resolver = new PositionResolver();
    resolver.visitAll(program, new XY(position));
    return resolver.targetVars.view().mapNotNull(pos -> switch (pos.data()) {
      case DefVar<?, ?> defVar -> {
        if (defVar.concrete != null) yield new WithPos<>(pos.sourcePos(), defVar);
          // defVar is an imported and serialized symbol, so we need to find the original one
        else if (defVar.module != null) {
          yield Resolver.resolveDef(source.owner(), defVar.module, defVar.name())
            .map(target -> new WithPos<Var>(pos.sourcePos(), target.ref()))
            .getOrNull();
        }
        // defVar is from a skipped module (see OrgaTycker), we can do nothing
        else yield null;
      }
      case LocalVar localVar -> new WithPos<>(pos.sourcePos(), localVar);
      case ModuleVar moduleVar -> new WithPos<>(pos.sourcePos(), moduleVar);
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

  /** resolve a top-level module by its qualified name */
  static @NotNull Option<LibrarySource> resolveModule(@NotNull LibraryOwner owner, @NotNull ImmutableSeq<String> module) {
    if (module.isEmpty()) return Option.none();
    var mod = owner.findModule(module);
    return mod != null ? Option.of(mod) : resolveModule(owner, module.dropLast(1));
  }

  /** resolve a top-level module by its qualified name */
  static @NotNull Option<LibrarySource> resolveModule(@NotNull SeqView<LibraryOwner> owners, @NotNull ImmutableSeq<String> module) {
    for (var owner : owners) {
      var found = resolveModule(owner, module);
      if (found.isDefined()) return found;
    }
    return Option.none();
  }

  /**
   * Traverse all referring terms including:
   * {@link Expr.RefExpr}, {@link Expr.ProjExpr}, {@link Expr.NewExpr}
   * and {@link Pattern} and check against a given condition implemented in
   * {@link ReferringResolver#check(P, Var, SourcePos)}
   */
  abstract class ReferringResolver<P> implements StmtOps<P> {
    public void visitAll(ImmutableSeq<Stmt> program, P xy) {
      program.forEach(stmt -> visit(stmt, xy));
    }

    /**
     * check whether a referable term's referring variable satisfies the parameter
     * at given source pos.
     */
    protected abstract void check(@NotNull P param, @NotNull Var var, @NotNull SourcePos sourcePos);

    @Override public @NotNull Expr visitExpr(@NotNull Expr expr, P pp) {
      switch (expr) {
        case Expr.RefExpr ref -> check(pp, ref.resolvedVar(), ref.sourcePos());
        case Expr.ProjExpr proj -> {
          if (proj.ix().isRight() && proj.resolvedIx() != null) {
            var pos = proj.ix().getRightValue();
            check(pp, proj.resolvedIx(), pos.sourcePos());
          }
        }
        case Expr.NewExpr neo -> neo.fields().forEach(field -> {
          var fieldRef = field.resolvedField().value;
          if (fieldRef != null)
            check(pp, fieldRef, field.name().sourcePos());
        });
        default -> {}
      }
      return StmtOps.super.visitExpr(expr, pp);
    }

    @Override public @NotNull Pattern visitPattern(@NotNull Pattern pattern, P param) {
      switch (pattern) {
        case Pattern.Ctor ctor -> check(param, ctor.resolved().data(), ctor.resolved().sourcePos());
        case Pattern.Bind bind -> check(param, bind.bind(), bind.sourcePos());
        default -> {}
      }
      return StmtOps.super.visitPattern(pattern, param);
    }
  }

  /**
   * In short, this class resolves position to PsiNameIdentifierOwner or PsiNamedElement.
   * <p>
   * Resolve position to its referring target. This class extends the
   * search to definitions and module commands compared to {@link ReferringResolver},
   * because the position may be placed at the name part of a function, a tele,
   * an import command, etc.
   *
   * @author ice1000, kiva
   */
  class PositionResolver extends ReferringResolver<XY> {
    public final @NotNull MutableList<WithPos<Var>> targetVars = MutableList.create();

    @Override public void visitCommand(@NotNull Command cmd, XY pp) {
      switch (cmd) {
        case Command.Import imp -> check(pp, new ModuleVar(imp.path()), imp.path().sourcePos());
        case Command.Open open -> check(pp, new ModuleVar(open.path()), open.path().sourcePos());
        case Command.Module module -> {}
      }
      super.visitCommand(cmd, pp);
    }

    @Override public void visitSignatured(@NotNull Signatured signatured, XY xy) {
      signatured.telescope
        .filterNot(tele -> tele.ref().name().startsWith(Constants.ANONYMOUS_PREFIX))
        .forEach(tele -> check(xy, tele.ref(), tele.sourcePos()));
      check(xy, signatured.ref(), signatured.sourcePos());
      super.visitSignatured(signatured, xy);
    }

    @Override public @NotNull Expr visitExpr(@NotNull Expr expr, XY pp) {
      if (expr instanceof Expr.NewExpr neo) neo.fields().forEach(field -> {
        field.bindings().forEach(binding ->
          check(pp, binding.data(), binding.sourcePos()));
        var fieldRef = field.resolvedField().value;
        if (fieldRef != null) check(pp, fieldRef, field.name().sourcePos());
      });
      return super.visitExpr(expr, pp);
    }

    @Override protected void check(@NotNull XY xy, @NotNull Var var, @NotNull SourcePos sourcePos) {
      if (xy.inside(sourcePos)) targetVars.append(new WithPos<>(sourcePos, var));
    }
  }

  /** find usages of a variable */
  class UsageResolver extends ReferringResolver<Var> {
    public final @NotNull MutableList<SourcePos> refs = MutableList.create();

    @Override protected void check(@NotNull Var var, @NotNull Var check, @NotNull SourcePos sourcePos) {
      if (isUsage(var, check)) refs.append(sourcePos);
    }

    private boolean isUsage(@NotNull Var var, @NotNull Var check) {
      if (check == var) return true;
      // for imported serialized definitions, let's compare by qualified name
      return var instanceof DefVar<?, ?> defVar
        && check instanceof DefVar<?, ?> checkDef
        && defVar.module != null
        && checkDef.module != null
        && defVar.module.equals(checkDef.module)
        && defVar.name().equals(checkDef.name());
    }
  }
}
