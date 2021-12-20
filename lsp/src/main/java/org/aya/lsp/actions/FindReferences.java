// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public interface FindReferences {
  static @NotNull List<Location> invoke(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    return findRefs(source, position, libraries)
      .map(ref -> LspRange.toLoc(ref.sourcePos()))
      .collect(Collectors.toList());
  }

  static @NotNull SeqView<Expr.RefExpr> findRefs(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var vars = Resolver.resolveVar(source, position);
    var resolver = new ReferenceResolver(DynamicSeq.create());
    vars.forEach(var -> libraries.forEach(lib -> resolve(resolver, lib, var.data())));
    return resolver.refs.view();
  }

  private static void resolve(@NotNull ReferenceResolver resolver, @NotNull LibraryOwner owner, @NotNull Var var) {
    owner.librarySources().forEach(src -> {
      var program = src.program().value;
      if (program != null) resolver.visitAll(program, var);
    });
    owner.libraryDeps().forEach(dep -> resolve(resolver, dep, var));
  }

  record ReferenceResolver(@NotNull DynamicSeq<Expr.RefExpr> refs) implements StmtConsumer<Var> {
    @Override public Unit visitRef(@NotNull Expr.RefExpr expr, Var var) {
      if (check(var, expr.resolvedVar())) refs.append(expr);
      return Unit.unit();
    }

    private boolean check(Var var, Var check) {
      if (check == var) return true;
      // for imported serialized definitions, let's compare by qualified name
      return var instanceof DefVar<?, ?> defVar
        && check instanceof DefVar<?, ?> checkDef
        && defVar.module.equals(checkDef.module)
        && defVar.name().equals(checkDef.name());
    }
  }
}
