// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.GeneralizedVar;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.ClassDef;
import org.aya.core.def.DataDef;
import org.aya.core.def.GenericDef;
import org.aya.core.term.Term;
import org.aya.ide.util.ModuleVar;
import org.aya.ide.util.XY;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.ModulePath;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface Resolver {
  /** resolve a symbol by its qualified name in the whole library */
  static @NotNull Option<@NotNull GenericDef> resolveDef(
    @NotNull LibraryOwner owner,
    @NotNull ImmutableSeq<String> module,
    @NotNull String name
  ) {
    var mod = resolveModule(owner, module);
    return mod.mapNotNull(m -> m.tycked().get())
      .map(defs -> defs.flatMap(Resolver::withChildren))
      .flatMap(defs -> defs.find(def -> def.ref().name().equals(name)));
  }

  /** resolve the position to its referring target */
  static @NotNull SeqView<WithPos<@NotNull AnyVar>> resolveVar(
    @NotNull LibrarySource source, XY xy
  ) {
    var program = source.program().get();
    if (program == null) return SeqView.empty();
    return program.view().flatMap(new XYResolver(xy)).mapNotNull(pos -> switch (pos.data()) {
      case DefVar<?, ?> defVar -> {
        if (defVar.concrete != null) yield new WithPos<>(pos.sourcePos(), defVar);
        else if (defVar.module != null) {
          // defVar is an imported and serialized symbol, so we need to find the original one
          yield Resolver.resolveDef(source.owner(), defVar.module, defVar.name())
            .map(target -> new WithPos<AnyVar>(pos.sourcePos(), target.ref()))
            .getOrNull();
        }
        // defVar is from a skipped module (see OrgaTycker), we can do nothing
        else yield null;
      }
      case LocalVar localVar -> new WithPos<>(pos.sourcePos(), localVar);
      case ModuleVar moduleVar -> new WithPos<>(pos.sourcePos(), moduleVar);
      case GeneralizedVar gVar -> new WithPos<>(pos.sourcePos(), gVar);
      case null, default -> null;
    });
  }

  private static @NotNull SeqView<GenericDef> withChildren(@NotNull GenericDef def) {
    return switch (def) {
      case DataDef data -> SeqView.<GenericDef>of(data).appendedAll(data.body);
      case ClassDef struct -> SeqView.<GenericDef>of(struct).appendedAll(struct.members);
      default -> SeqView.of(def);
    };
  }

  static @NotNull SeqView<DefVar<?, ?>> withChildren(@NotNull Decl def) {
    return switch (def) {
      case TeleDecl.DataDecl data ->
        SeqView.<DefVar<?, ?>>of(data.ref).appendedAll(data.body.map(TeleDecl.DataCtor::ref));
      case ClassDecl struct ->
        SeqView.<DefVar<?, ?>>of(struct.ref).appendedAll(struct.members.map(TeleDecl.ClassMember::ref));
      default -> SeqView.of(def.ref());
    };
  }

  /** resolve a top-level module by its qualified name */
  static @NotNull Option<LibrarySource> resolveModule(@NotNull LibraryOwner owner, @NotNull ImmutableSeq<String> module) {
    if (module.isEmpty()) return Option.none();
    var mod = owner.findModule(module);
    return mod != null ? Option.some(mod) : resolveModule(owner, module.dropLast(1));
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
   * In short, this class resolves cursor position to PsiNameIdentifierOwner or PsiNamedElement.
   * <p>
   * This class should traverse all {@link AnyVar}s, ignoring the differences between
   * variable declaration and variable references, unlike {@link StmtFolder} and
   * {@link org.aya.cli.literate.SyntaxHighlight}.
   * <p>
   * The rationale is that users may place the cursor at the name part of a function,
   * a tele, an import command, etc. And we are expected to find the correct {@link AnyVar}
   * no matter if it is a declaration or a reference.
   *
   * @author ice1000, kiva, wsx
   */
  record XYResolver(XY xy) implements StmtFolder<SeqView<WithPos<AnyVar>>> {
    @Override public @NotNull SeqView<WithPos<AnyVar>> init() {
      return SeqView.empty();
    }

    @Override public @NotNull SeqView<WithPos<AnyVar>>
    foldVar(@NotNull SeqView<WithPos<AnyVar>> targets, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<Term> type) {
      return xy.inside(pos) ? targets.appended(new WithPos<>(pos, var)) : targets;
    }

    @Override public @NotNull SeqView<WithPos<AnyVar>>
    foldVarDecl(@NotNull SeqView<WithPos<AnyVar>> acc, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<@Nullable Term> type) {
      if (var instanceof LocalVar v && v.isGenerated()) return acc;
      return StmtFolder.super.foldVarDecl(acc, var, pos, type);
    }

    @Override
    public @NotNull SeqView<WithPos<AnyVar>> foldModuleRef(@NotNull SeqView<WithPos<AnyVar>> acc, @NotNull SourcePos pos, @NotNull ModulePath path) {
      return foldVarRef(acc, new ModuleVar(path), pos, noType());
    }

    @Override
    public @NotNull SeqView<WithPos<AnyVar>> foldModuleDecl(@NotNull SeqView<WithPos<AnyVar>> acc, @NotNull SourcePos pos, @NotNull ModulePath path) {
      return foldVarDecl(acc, new ModuleVar(path), pos, noType());
    }
  }

  /**
   * This class finds usages of a variable. So we only traverse variable references.
   *
   * @author kiva, wsx
   */
  record UsageResolver(@NotNull AnyVar target) implements StmtFolder<SeqView<SourcePos>> {
    @Override public @NotNull SeqView<SourcePos> init() {
      return SeqView.empty();
    }

    @Override public @NotNull SeqView<SourcePos>
    foldVarRef(@NotNull SeqView<SourcePos> refs, @NotNull AnyVar var, @NotNull SourcePos pos, @NotNull LazyValue<Term> type) {
      // for imported serialized definitions, let's compare by qualified name
      var usage = (target == var)
        || var instanceof DefVar<?, ?> def
        && target instanceof DefVar<?, ?> targetDef
        && Objects.equals(def.module, targetDef.module)
        && def.name().equals(targetDef.name());
      return usage ? refs.appended(pos) : refs;
    }
  }
}
