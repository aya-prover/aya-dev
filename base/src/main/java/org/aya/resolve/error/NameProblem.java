// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.context.BindContext;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public interface NameProblem extends Problem {
  @Override default @NotNull Stage stage() { return Stage.RESOLVE; }
  interface Error extends NameProblem {
    @Override default @NotNull Severity level() { return Severity.ERROR; }
  }

  interface Warn extends NameProblem {
    @Override default @NotNull Severity level() { return Severity.WARN; }
  }

  record AmbiguousNameError(
    @NotNull String name,
    @NotNull ImmutableSeq<ModuleName> disambiguation,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(Doc.sep(
          Doc.english("The unqualified name"),
          Doc.code(name),
          Doc.english("is ambiguous")),
        Doc.english("Did you mean:"),
        Doc.nest(2, Doc.vcat(didYouMean().map(Doc::code))));
    }

    public @NotNull ImmutableSeq<String> didYouMean() {
      return disambiguation.view().map(mod -> mod.resolve(name).toString()).toImmutableSeq();
    }
  }

  record AmbiguousNameWarn(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements Warn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.vcat(Doc.sep(
        Doc.english("The name"),
        Doc.code(name),
        Doc.english("introduces ambiguity and can only be accessed through a qualified name")));
    }
  }

  record DuplicateExportError(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The name"),
        Doc.code(name),
        Doc.english("being exported clashes with another exported definition with the same name"));
    }

    @Override @NotNull public Severity level() { return Severity.ERROR; }
  }

  record DuplicateModNameError(
    @NotNull ModuleName modName,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.code(modName.toString()),
        Doc.english("is already defined elsewhere")
      );
    }
  }

  record ClashModNameError(
    @NotNull ModulePath modulePath,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The inner module"),
        Doc.code(modulePath.toString()),
        Doc.english("clashes with a file level module"),
        Doc.code(STR."\{modulePath.module().joinToString("/")}.aya")
      );
    }
  }

  record DuplicateNameError(
    @NotNull String name, @NotNull AnyVar ref,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The name"),
        Doc.plain(name),
        Doc.parened(Doc.code(BasePrettier.varDoc(ref))),
        Doc.english("is already defined elsewhere")
      );
    }
  }

  record ModNameNotFoundError(
    @NotNull ModuleName modName,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.code(modName.toString()),
        Doc.english("is not defined in the current scope")
      );
    }
  }

  record ModNotFoundError(@NotNull ModulePath path, @Override @NotNull SourcePos sourcePos) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.code(path.toString()),
        Doc.english("is not found")
      );
    }
  }

  record ModShadowingWarn(
    @NotNull ModuleName modName,
    @Override @NotNull SourcePos sourcePos
  ) implements Warn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.code(modName.toString()),
        Doc.english("shadows a previous definition from outer scope")
      );
    }
  }

  record ShadowingWarn(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements Warn {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("The name"),
        Doc.code(name),
        Doc.english("shadows a previous local definition from outer scope")
      );
    }
  }

  record QualifiedNameNotFoundError(
    @NotNull ModuleName modName,
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("The qualified name"),
        Doc.code(modName.resolve(name).toString()),
        Doc.english("is not defined in the current scope")
      );
    }
  }

  record UnqualifiedNameNotFoundError(
    @NotNull Context context,
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      var head = Doc.sep(
        Doc.english("The name"),
        Doc.code(name),
        Doc.english("is not defined in the current scope"));
      var possible = didYouMean();
      if (possible.isEmpty()) return head;
      var tail = possible.sizeEquals(1)
        ? Doc.sep(Doc.english("Did you mean:"), Doc.code(possible.getFirst()))
        : Doc.vcat(Doc.english("Did you mean:"),
          Doc.nest(2, Doc.vcat(possible.view().map(Doc::code))));
      return Doc.vcat(head, tail);
    }

    public @NotNull ImmutableSeq<String> didYouMean() {
      var ctx = context;
      while (ctx instanceof BindContext bindCtx) ctx = bindCtx.parent();
      var possible = MutableList.<String>create();
      if (ctx instanceof ModuleContext moduleContext) moduleContext.modules().forEach((modName, mod) -> {
        if (mod.symbols().contains(name)) {
          // TODO: The name `{modName}::{name}` is probably ambiguous
          possible.append(modName.resolve(name).toString());
        }
      });
      return possible.toImmutableSeq();
    }
  }

  record OperatorNameNotFound(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements Error {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Unknown operator"),
        Doc.code(name),
        Doc.english("used in bind statement")
      );
    }
  }
}
