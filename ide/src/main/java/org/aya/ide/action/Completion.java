// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableList;
import kala.value.LazyValue;
import org.aya.cli.library.source.LibrarySource;
import org.aya.generic.AyaDocile;
import org.aya.ide.util.XY;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.ModuleExport;
import org.aya.syntax.compile.*;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.*;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Completion {
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

  public sealed interface Item {
    sealed interface Symbol extends Item {
      @NotNull String name();
      @NotNull Telescope type();
    }

    /// @param disambiguous which {@link ModuleName} this declaration defines in
    record Decl(
      @NotNull ModuleName disambiguous,
      @Override @NotNull String name,
      @Override @NotNull Telescope type,
      @NotNull Kind kind
    ) implements Symbol {
      // TODO: I guess we can place this in [syntax] module
      public enum Kind {
        Generalized, Fn, Data, Con, Class, Member, Prim;

        public static @NotNull Kind from(@NotNull org.aya.syntax.concrete.stmt.decl.Decl decl) {
          return switch (decl) {
            case ClassDecl _ -> Kind.Class;
            case ClassMember _ -> Kind.Member;
            case DataCon _ -> Kind.Con;
            case DataDecl _ -> Kind.Data;
            case FnDecl _ -> Kind.Fn;
            case PrimDecl _ -> Kind.Prim;
          };
        }

        public static @NotNull Kind from(@NotNull JitDef def) {
          return switch (def) {
            case JitClass _ -> Kind.Class;
            case JitFn _ -> Kind.Fn;
            case JitCon _ -> Kind.Con;
            case JitData _ -> Kind.Data;
            case JitMember _ -> Kind.Member;
            case JitPrim _ -> Kind.Prim;
          };
        }
      }
    }

    record Module(@NotNull ModuleName.Qualified moduleName) implements Item { }

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

  public final @NotNull LibrarySource source;
  public final @NotNull XY xy;
  private final @NotNull ImmutableSeq<String> incompleteName;
  private final boolean endsWithSeparator;
  private @Nullable ModuleName inModule = null;
  private @Nullable ImmutableSeq<Item.Local> localContext;
  private @Nullable ImmutableSeq<Item> topLevelContext;

  /// @param incompleteName    the incomplete name under the cursor, only used for determine the context of completion
  /// @param endsWithSeparator ditto
  public Completion(
    @NotNull LibrarySource source,
    @NotNull XY xy,
    @NotNull ImmutableSeq<String> incompleteName,
    boolean endsWithSeparator
  ) {
    this.source = source;
    this.xy = xy;
    this.incompleteName = incompleteName;
    this.endsWithSeparator = endsWithSeparator;
  }

  @Contract("-> this")
  public @NotNull Completion compute() {
    var stmts = source.program().get();
    var info = source.resolveInfo().get();
    var context = endsWithSeparator ? incompleteName : incompleteName.dropLast(1);

    if (context.isEmpty()) {
      if (stmts != null) {
        var walker = resolveLocal(stmts, xy);
        this.inModule = walker.moduleContext();
        this.localContext = walker.localContext();
      }
    }

    if (info != null) {
      var modName = ModuleName.from(context);
      switch (modName) {
        case ModuleName.ThisRef _ -> {
          topLevelContext = resolveTopLevel(info.thisModule());
        }
        case ModuleName.Qualified qualified -> {
          var mod = info.thisModule().getModuleMaybe(qualified);
          if (mod == null) break;     // TODO: do something?
          topLevelContext = resolveModLevel(qualified, mod);
        }
      }
    }

    return this;
  }

  public @Nullable ModuleName inModule() { return inModule; }
  public @Nullable ImmutableSeq<Item.Local> localContext() { return localContext; }
  public @Nullable ImmutableSeq<Item> topLevelContext() { return topLevelContext; }

  public static @NotNull ContextWalker resolveLocal(@NotNull ImmutableSeq<Stmt> stmts, @NotNull XY xy) {
    var walker = new ContextWalker(xy);
    stmts.forEach(walker);
    return walker;
  }

  private static @NotNull Item.Decl from(@NotNull ModuleName inMod, @NotNull String name, @NotNull AnyVar var) {
    Item.Decl.Kind declKind;
    Telescope type = switch (var) {
      case GeneralizedVar gVar -> {
        declKind = Item.Decl.Kind.Generalized;
        yield new Telescope(ImmutableSeq.empty(), new StmtVisitor.Type(gVar.owner.type.data()));
      }
      case DefVar<?, ?> defVar -> {
        // TODO: try defVar.signature? but that requires some tycking
        var concrete = defVar.concrete;
        declKind = Item.Decl.Kind.from(concrete);
        yield switch (concrete) {
          case ClassDecl classDecl -> throw new UnsupportedOperationException("TODO");
          case TeleDecl teleDecl -> Telescope.from(teleDecl.telescope, teleDecl.result);
        };
      }
      case CompiledVar jitVar -> {
        declKind = Item.Decl.Kind.from(jitVar.core());
        yield switch (jitVar.core()) {
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
      }
      default -> {
        declKind = Item.Decl.Kind.Prim;     // make compiler happy
        yield Panic.unreachable();
      }
    };

    return new Item.Decl(inMod, name, type, declKind);
  }

  /// Resolve all top level declarations
  ///
  /// @implNote be aware that a symbol defined in a submodule can be imported (by `open`) in the parent module.
  public static @NotNull ImmutableSeq<Item> resolveTopLevel(@NotNull ModuleContext ctx) {
    var decls = MutableHashMap.<String, MutableList<Item.Decl>>create();
    var modules = MutableHashMap.<ModuleName.Qualified, Item.Module>create();

    Context someInterestingLoopVariableWhichIDontKnowHowToNameIt = ctx;

    while (someInterestingLoopVariableWhichIDontKnowHowToNameIt instanceof ModuleContext mCtx) {
      mCtx.symbols().forEach((name, candy) -> {
        if (decls.containsKey(name)) return;
        var candycandy = MutableList.<Item.Decl>create();
        decls.put(name, candycandy);
        candy.forEach((inMod, var) -> {
          var decl = from(inMod, name, var);
          candycandy.append(decl);
        });
      });

      mCtx.modules().forEach((modName, _) ->
        modules.putIfAbsent(modName, new Item.Module(modName)));

      someInterestingLoopVariableWhichIDontKnowHowToNameIt = mCtx.parent();
    }

    return decls.valuesView()
      .flatMap(it -> SeqView.<Item>narrow(it.view()))
      .toSeq()        // TODO: fix this copy
      .appendedAll(modules.valuesView());
  }

  public static @NotNull ImmutableSeq<Item> resolveModLevel(@NotNull ModuleName.Qualified inMod, @NotNull ModuleExport export) {
    var decls = MutableList.<Item.Decl>create();
    var modules = MutableList.<Item.Module>create();

    export.symbols().forEach((name, var) -> {
      var decl = from(inMod, name, var);
      decls.append(decl);
    });

    export.modules().forEach((mod, _) -> {
      var fullName = inMod.concat(mod);
      modules.append(new Item.Module(fullName));
    });

    return SeqView.<Item>narrow(decls.view())
      .concat(modules)
      .toSeq();
  }
}
