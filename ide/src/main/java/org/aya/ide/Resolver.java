// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.util.ModuleVar;
import org.aya.ide.util.XY;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.*;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface Resolver {
  /** resolve a symbol by its qualified name in the whole library */
  static @NotNull Option<@NotNull TyckDef> resolveDef(
    @NotNull LibraryOwner owner,
    @NotNull ModulePath module,
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
    var collect = MutableList.<WithPos<AnyVar>>create();
    program.view().forEach(new XYResolver(xy, collect));
    return collect.view().mapNotNull(pos -> switch (pos.data()) {
      case DefVar<?, ?> defVar -> new WithPos<>(pos.sourcePos(), defVar);
      case LocalVar localVar -> new WithPos<>(pos.sourcePos(), localVar);
      case ModuleVar moduleVar -> new WithPos<>(pos.sourcePos(), moduleVar);
      case GeneralizedVar gVar -> new WithPos<>(pos.sourcePos(), gVar);
      // defVar is an imported and serialized symbol, so we need to find the original one
      // case CompiledVar cVar -> {
      //  yield Resolver.resolveDef(source.owner(), cVar.module.module(), defVar.name())
      //     .map(target -> new WithPos<AnyVar>(pos.sourcePos(), target.ref()))
      //     .getOrNull();
      // }
      // defVar is from a skipped module (see OrgaTycker), we can do nothing
      case null, default -> null;
    });
  }

  private static @NotNull SeqView<TyckDef> withChildren(@NotNull TyckDef def) {
    return switch (def) {
      case DataDef data -> SeqView.<TyckDef>of(data).appendedAll(data.body);
      // case ClassDef struct -> SeqView.<Def>of(struct).appendedAll(struct.members);
      default -> SeqView.of(def);
    };
  }

  static @NotNull SeqView<DefVar<?, ?>> withChildren(@NotNull Decl def) {
    return switch (def) {
      case DataDecl data -> SeqView.<DefVar<?, ?>>of(data.ref).appendedAll(data.body.map(DataCon::ref));
      // case ClassDecl struct ->
      //   SeqView.<DefVar<?, ?>>of(struct.ref).appendedAll(struct.members.map(TeleDecl.ClassMember::ref));
      default -> SeqView.of(def.ref());
    };
  }

  /** resolve a top-level module by its qualified name */
  static @NotNull Option<LibrarySource> resolveModule(@NotNull LibraryOwner owner, @NotNull ModulePath module) {
    if (module.isEmpty()) return Option.none();
    var mod = owner.findModule(module);
    return mod != null ? Option.some(mod) : resolveModule(owner, module.dropLast(1));
  }

  /** resolve a top-level module by its qualified name */
  static @NotNull Option<LibrarySource> resolveModule(@NotNull SeqView<LibraryOwner> owners, @NotNull ModulePath module) {
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
   * variable declaration and variable references, unlike {@link StmtVisitor} and
   * {@link org.aya.cli.literate.SyntaxHighlight}.
   * <p>
   * The rationale is that users may place the cursor at the name part of a function,
   * a tele, an import command, etc. And we are expected to find the correct {@link AnyVar}
   * no matter if it is a declaration or a reference.
   *
   * @author ice1000, kiva, wsx
   */
  record XYResolver(XY xy, MutableList<WithPos<AnyVar>> collect) implements StmtVisitor {
    @Override
    public void visitVar(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull LazyValue<@Nullable Term> type) {
      if (xy.inside(pos)) collect.append(new WithPos<>(pos, var));
    }
    @Override
    public void visitVarDecl(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull LazyValue<@Nullable Term> type) {
      if (var instanceof LocalVar v && v.isGenerated()) return;
      StmtVisitor.super.visitVarDecl(pos, var, type);
    }
    // TODO[for hoshino]: what to do about ModulePath?
    @Override public void visitModuleRef(@NotNull SourcePos pos, @NotNull ModuleName path) {
      visitVarRef(pos, new ModuleVar(path), noType);
    }
    @Override public void visitModuleDecl(@NotNull SourcePos pos, @NotNull ModuleName path) {
      visitVarDecl(pos, new ModuleVar(path), noType);
    }
  }

  /**
   * This class finds usages of a variable. So we only traverse variable references.
   *
   * @author kiva, wsx
   */
  record UsageResolver(@NotNull AnyVar target, @NotNull MutableList<SourcePos> collect) implements StmtVisitor {
    @Override
    public void visitVarRef(@NotNull SourcePos pos, @NotNull AnyVar var, @NotNull LazyValue<@Nullable Term> type) {
      // for imported serialized definitions, let's compare by qualified name
      var usage = (target == var)
        || var instanceof DefVar<?, ?> def
        && target instanceof DefVar<?, ?> targetDef
        && Objects.equals(def.module, targetDef.module)
        && def.name().equals(targetDef.name());
      if (usage) collect.append(pos);
    }
  }
}
