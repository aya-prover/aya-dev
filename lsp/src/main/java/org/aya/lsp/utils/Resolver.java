// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.StmtFolder;
import org.aya.core.def.DataDef;
import org.aya.core.def.GenericDef;
import org.aya.core.def.StructDef;
import org.aya.generic.Constants;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.javacs.lsp.Position;
import org.jetbrains.annotations.NotNull;

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
    @NotNull LibrarySource source,
    @NotNull Position position
  ) {
    var program = source.program().get();
    if (program == null) return SeqView.empty();
    return program.view().flatMap(new PositionResolver(new XY(position))).mapNotNull(pos -> switch (pos.data()) {
      case DefVar<?, ?> defVar -> {
        if (defVar.concrete != null) yield new WithPos<>(pos.sourcePos(), defVar);
          // defVar is an imported and serialized symbol, so we need to find the original one
        else if (defVar.module != null) {
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
      case StructDef struct -> SeqView.<GenericDef>of(struct).appendedAll(struct.fields);
      default -> SeqView.of(def);
    };
  }

  static @NotNull SeqView<DefVar<?, ?>> withChildren(@NotNull Decl def) {
    return switch (def) {
      case TeleDecl.DataDecl data -> SeqView.<DefVar<?, ?>>of(data.ref).appendedAll(data.body.map(TeleDecl.DataCtor::ref));
      case TeleDecl.StructDecl struct -> SeqView.<DefVar<?, ?>>of(struct.ref).appendedAll(struct.fields.map(TeleDecl.StructField::ref));
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
   * In short, this class resolves position to PsiNameIdentifierOwner or PsiNamedElement.
   * <p>
   * Resolve position to its referring target. This class extends the
   * search to definitions and module commands compared to {@link StmtFolder},
   * because the position may be placed at the name part of a function, a tele,
   * an import command, etc.
   *
   * @author ice1000, kiva
   */
  record PositionResolver(XY xy) implements StmtFolder<SeqView<WithPos<AnyVar>>> {
    @Override public @NotNull SeqView<WithPos<AnyVar>> init() {
      return SeqView.empty();
    }

    @Override public @NotNull SeqView<WithPos<AnyVar>>
    fold(@NotNull SeqView<WithPos<AnyVar>> targets, @NotNull AnyVar var, @NotNull SourcePos pos) {
      return xy.inside(pos) ? targets.appended(new WithPos<>(pos, var)) : StmtFolder.super.fold(targets, var, pos);
    }

    @Override
    public @NotNull SeqView<WithPos<AnyVar>> fold(@NotNull SeqView<WithPos<AnyVar>> targets, @NotNull Stmt stmt) {
      return switch (stmt) {
        case Command.Import imp -> fold(targets, new ModuleVar(imp.path()), imp.path().sourcePos());
        case Command.Open open -> fold(targets, new ModuleVar(open.path()), open.path().sourcePos());
        case Decl decl when decl instanceof Decl.Telescopic tele -> {
          targets = tele.telescope().filterNot(p -> p.ref().name().startsWith(Constants.ANONYMOUS_PREFIX))
            .foldLeft(targets, (ac, p) -> fold(ac, p.ref(), p.sourcePos()));
          yield fold(targets, decl.ref(), decl.sourcePos());
        }
        case Generalize g -> g.variables.foldLeft(targets, (t, v) -> fold(t, v, v.sourcePos));
        default -> StmtFolder.super.fold(targets, stmt);
      };
    }
  }

  /** find usages of a variable */
  record UsageResolver(@NotNull AnyVar target) implements StmtFolder<SeqView<SourcePos>> {
    @Override public @NotNull SeqView<SourcePos> init() {
      return SeqView.empty();
    }

    @Override
    public @NotNull SeqView<SourcePos> fold(@NotNull SeqView<SourcePos> refs, @NotNull AnyVar var, @NotNull SourcePos pos) {
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
