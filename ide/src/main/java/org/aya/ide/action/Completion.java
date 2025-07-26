// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.value.LazyValue;
import org.aya.cli.library.source.LibrarySource;
import org.aya.generic.AyaDocile;
import org.aya.generic.Constants;
import org.aya.ide.action.completion.BindingInfoExtractor;
import org.aya.ide.action.completion.ContextWalker2;
import org.aya.ide.action.completion.NodeWalker;
import org.aya.ide.util.XY;
import org.aya.intellij.GenericNode;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.syntax.context.ModuleExport;
import org.aya.syntax.compile.*;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.ref.*;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.Panic;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourceFile;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class Completion {
  public record Param(@Nullable String name, @NotNull StmtVisitor.Type type, boolean licit) implements AyaDocile {
    @Override
    public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var nameDoc = name == null ? Doc.empty() : Doc.sep(Doc.plain(name), Tokens.HAS_TYPE);
      var typeDoc = type.toDoc(options);
      return licit
        ? (nameDoc.isEmpty()
        ? typeDoc                                         // Foo
        : Doc.parened(Doc.sep(nameDoc, typeDoc)))         // (a : Foo)
        : Doc.braced(Doc.sepNonEmpty(nameDoc, typeDoc));    // {Foo} or {a : Foo}
    }
  }

  public record Telescope(@NotNull ImmutableSeq<Param> telescope,
                          @NotNull StmtVisitor.Type result) implements AyaDocile {
    public static @NotNull Telescope from(@NotNull ImmutableSeq<Expr.Param> params, @Nullable WithPos<Expr> result) {
      return new Telescope(
        params.map(p ->
          new Param(
            p.ref().isGenerated() ? null : p.ref().name(),
            new StmtVisitor.Type(p.type()), p.explicit())
        ),
        result == null ? StmtVisitor.Type.noType : new StmtVisitor.Type(result.data()));
    }

    public @NotNull StmtVisitor.Type headless() {
      assert telescope.isEmpty();
      return result;
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      // TODO: unify with ConcretePrettier/CorePrettier?
      var docs = MutableList.<Doc>create();
      telescope.forEach(p -> docs.append(p.toDoc(options)));

      docs.append(Tokens.HAS_TYPE);

      var resultDoc = result.toDocile();
      if (resultDoc != null) {
        docs.append(resultDoc.toDoc(options));
      } else {
        docs.append(Doc.plain(Constants.ANONYMOUS_PREFIX));
      }

      return Doc.sep(docs);
    }
  }

  public sealed interface Item {
    sealed interface Symbol extends Item {
      @NotNull String name();
      @NotNull Telescope type();
    }

    /// @param disambiguous which {@link ModuleName} this declaration defines in
    /// @param ownerName the name of the owner, used by constructors or fields
    record Decl(
      @NotNull ModuleName disambiguous,
      @Override @NotNull String name,
      @Override @NotNull Telescope type,
      @NotNull Kind kind,
      @Nullable String ownerName
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

    record Local(@NotNull AnyVar var, @Override @NotNull StmtVisitor.Type result) implements AyaDocile, Symbol {
      @Override
      public @NotNull Telescope type() {
        return new Telescope(ImmutableSeq.empty(), result);
      }

      @Override
      public @NotNull String name() {
        return var.name();
      }

      @Override
      public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
        return Doc.sepNonEmpty(BasePrettier.varDoc(var), type().toDoc(options));
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
  private @Nullable ContextWalker2.Location location;

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
  public @NotNull Completion compute() throws IOException {
    var sourceFile = source.codeFile();
    var stmts = source.program().get();
    var rootNode = source.rootNode().get();
    var info = source.resolveInfo().get();
    var context = endsWithSeparator ? incompleteName : incompleteName.dropLast(1);

    if (context.isEmpty()) {
      if (stmts != null && rootNode != null) {
        var walker = resolveLocal(sourceFile, stmts, rootNode, xy);
        this.localContext = walker.localContext.valuesView().toSeq();
        this.location = walker.location();
      }
    }

    if (info != null) {
      var modName = ModuleName.from(context);
      // TODO: provide top level context inside [inModule] with `ModuleContext` rather than `ModuleExport`.
      //  ^ This requires Expr.LetOpen/Command.Modules storing `ModuleContext`, which is invisible.

      switch (modName) {
        case ModuleName.ThisRef _ -> topLevelContext = resolveTopLevel(info.thisModule());
        case ModuleName.Qualified qualified -> {
          var mod = info.thisModule().getModuleMaybe(qualified);
          if (mod == null) break;     // TODO: do something?
          topLevelContext = resolveModLevel(qualified, mod);
        }
      }
    }

    return this;
  }

  public @Nullable ContextWalker2.Location location() { return location; }
  public @Nullable ModuleName inModule() { return inModule; }
  public @Nullable ImmutableSeq<Item.Local> localContext() { return localContext; }
  public @Nullable ImmutableSeq<Item> topLevelContext() { return topLevelContext; }

  public static @NotNull ContextWalker2 resolveLocal(
    @NotNull SourceFile file,
    @NotNull ImmutableSeq<Stmt> stmts,
    @NotNull GenericNode<?> root,
    @NotNull XY xy
  ) {
    var result = NodeWalker.run(file, root, xy, TokenSet.EMPTY);
    var target = NodeWalker.refocus(result.node(), result.offsetInNode());
    var walker = new ContextWalker2(new BindingInfoExtractor().accept(stmts).extracted());
    walker.visit(target);
    return walker;
  }

  private static @NotNull Item.Decl from(@NotNull ModuleName inMod, @NotNull String name, @NotNull AnyVar var) {
    Item.Decl.Kind declKind;
    String ownerName = null;
    Telescope type = switch (var) {
      case GeneralizedVar gVar -> {
        declKind = Item.Decl.Kind.Generalized;
        yield new Telescope(ImmutableSeq.empty(), new StmtVisitor.Type(gVar.owner.type.data()));
      }
      case DefVar<?, ?> defVar -> {
        // TODO: try defVar.signature? but that requires some tycking
        var concrete = defVar.concrete;
        declKind = Item.Decl.Kind.from(concrete);

        if (concrete instanceof DataCon con) {
          ownerName = con.dataRef.concrete.ref.name();
        }

        yield switch (concrete) {
          case ClassDecl _ -> throw new UnsupportedOperationException("TODO");
          // TODO: result can be null, solution: use core signature
          case TeleDecl teleDecl -> Telescope.from(teleDecl.telescope, teleDecl.result);
        };
      }
      case CompiledVar jitVar -> {
        declKind = Item.Decl.Kind.from(jitVar.core());
        yield switch (jitVar.core()) {
          case JitClass _ -> throw new UnsupportedOperationException("TODO");
          case JitTele jitTele -> {
            if (jitTele instanceof JitCon con) {
              ownerName = con.dataType.name();
            }

            var freeParams = AbstractTele.enrich(jitTele);
            var freeResult = jitTele.result(freeParams.map(it -> new FreeTerm(it.ref())));
            yield new Telescope(
              freeParams.map(it ->
                new Param(it.ref().name(), new StmtVisitor.Type(LazyValue.ofValue(it.type())), it.explicit())),
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

    return new Item.Decl(inMod, name, type, declKind, ownerName);
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
