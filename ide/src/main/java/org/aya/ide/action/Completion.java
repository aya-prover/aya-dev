// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.LazyValue;
import org.aya.cli.library.source.LibrarySource;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.ClassDecl;
import org.aya.syntax.concrete.stmt.decl.TeleDecl;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.CompiledVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.Panic;
import org.aya.util.PrettierOptions;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Completion {
  public record Param(@NotNull String name, @NotNull StmtVisitor.Type type) implements AyaDocile {
    @Override
    public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.parened(Doc.sep(Doc.plain(name), Tokens.HAS_TYPE, type.toDoc(options)));
    }
  }

  public record Telescope(@NotNull ImmutableSeq<Param> telescope,
                          @NotNull StmtVisitor.Type result) implements AyaDocile {
    public static @NotNull Telescope from(@NotNull ImmutableSeq<Expr.Param> params, @Nullable WithPos<Expr> result) {
      return new Telescope(
        params.map(p -> new Param(p.ref().name(), new StmtVisitor.Type(p.type()))),
        result == null ? StmtVisitor.Type.noType : new StmtVisitor.Type(result.data()));
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      // TODO: unify with ConcretePrettier/CorePrettier?
      var docs = MutableList.<Doc>create();
      telescope.forEach(p -> docs.append(p.toDoc(options)));
      docs.append(Tokens.HAS_TYPE);
      docs.append(result.toDoc(options));

      return Doc.sep(docs);
    }
  }

  // FIXME: ugly though, fix me later
  public sealed interface CompletionItemu {
    sealed interface Symbol extends CompletionItemu {
      @NotNull String name();
      @NotNull Telescope type();
    }

    /// @param disambiguous which {@link ModuleName} this declaration defines in
    record Decl(
      @NotNull ModuleName disambiguous,
      @Override @NotNull String name,
      @Override @NotNull Telescope type
    ) implements Symbol { }

    record Module(@NotNull ModuleName moduleName) implements CompletionItemu { }

    record Local(@NotNull AnyVar var, @NotNull StmtVisitor.Type userType) implements AyaDocile, Symbol {
      @Override
      public @NotNull String name() {
        return var.name();
      }

      @Override
      public @NotNull Telescope type() {
        return new Telescope(ImmutableSeq.empty(), userType);
      }

      @Override
      public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
        var typeDoc = userType.toDocile();
        var realTypeDoc = typeDoc == null
          ? Doc.empty()
          : Doc.sep(Tokens.HAS_TYPE, typeDoc.toDoc(options));

        return Doc.sepNonEmpty(BasePrettier.varDoc(var), realTypeDoc);
      }
    }
  }

  /// Resolve all top level (private) declarations
  ///
  /// @return null if failed, probably {@param source} is not parsed/resolved yet.
  public static @Nullable ImmutableSeq<CompletionItemu> resolveTopLevel(@NotNull LibrarySource source) {
    var info = source.resolveInfo().get();
    if (info == null) {
      return null;
    }

    var decls = MutableList.<CompletionItemu.Decl>create();

    info.thisModule().symbols().forEach((name, candy) -> {
      candy.forEach((inMod, var) -> {
        Telescope type = switch (var) {
          case GeneralizedVar gVar -> new Telescope(ImmutableSeq.empty(), new StmtVisitor.Type(gVar.owner.type.data()));
          case DefVar<?, ?> defVar -> {
            // TODO: try defVar.signature? but that requires some tycking
            var concrete = defVar.concrete;
            yield switch (concrete) {
              case ClassDecl classDecl -> throw new UnsupportedOperationException("TODO");
              case TeleDecl teleDecl -> Telescope.from(teleDecl.telescope, teleDecl.result);
            };
          }
          case CompiledVar jitVar -> switch (jitVar.core()) {
            case JitClass jitClass -> throw new UnsupportedOperationException("TODO");
            case JitTele jitTele -> {
              var freeParams = AbstractTele.enrich(jitTele);
              var freeResult = jitTele.result(freeParams.map(it -> new FreeTerm(it.ref())));
              yield new Telescope(
                freeParams.map(it ->
                  new Param(it.ref().name(), new StmtVisitor.Type(LazyValue.ofValue(it.type())))),
                new StmtVisitor.Type(LazyValue.ofValue(freeResult))
              );
            }
          };
          default -> Panic.unreachable();
        };
        var decl = new Completion.CompletionItemu.Decl(inMod, name, type);
        decls.append(decl);
      });
    });

    return ImmutableSeq.narrow(decls.toSeq());
  }
}
