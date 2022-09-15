// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.distill.BaseDistiller;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public interface NameProblem extends Problem {
  @Override default @NotNull Stage stage() {return Stage.RESOLVE;}
  interface Error extends NameProblem {
    @Override default @NotNull Severity level() {return Severity.ERROR;}
  }

  interface Warn extends NameProblem {
    @Override default @NotNull Severity level() {return Severity.WARN;}
  }

  record AmbiguousNameError(
    @NotNull String name,
    @NotNull ImmutableSeq<Seq<String>> disambiguation,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.sep(
          Doc.english("The unqualified name"),
          Doc.styled(Style.code(), Doc.plain(name)),
          Doc.english("is ambiguous")),
        Doc.english("Use one of the following module names to qualify the name to disambiguate:"),
        Doc.styled(Style.code(), Doc.nest(1, Doc.vcat(disambiguation.view()
          .map(QualifiedID::join)
          .map(Doc::plain)))));
    }
  }

  record AmbiguousNameWarn(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Warn {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.vcat(Doc.sep(
        Doc.english("The name"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("introduces ambiguity and can only be accessed through a qualified name")));
    }
  }

  record DuplicateExportError(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The name"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("being exported clashes with another exported definition with the same name"));
    }

    @Override @NotNull public Severity level() {
      return Severity.ERROR;
    }
  }

  record DuplicateModNameError(
    @NotNull Seq<String> modName,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
        Doc.english("is already defined elsewhere")
      );
    }
  }

  record DuplicateNameError(
    @NotNull String name, @NotNull AnyVar ref,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The name"),
        Doc.plain(name),
        Doc.parened(Doc.styled(Style.code(), BaseDistiller.varDoc(ref))),
        Doc.english("is already defined elsewhere")
      );
    }
  }

  record ModNameNotFoundError(
    @NotNull Seq<String> modName,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
        Doc.english("is not defined in the current scope")
      );
    }
  }

  record ModNotFoundError(
    @NotNull Seq<String> modName,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
        Doc.english("is not found")
      );
    }
  }

  record ModShadowingWarn(
    @NotNull Seq<String> modName,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Warn {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The module name"),
        Doc.styled(Style.code(), Doc.plain(QualifiedID.join(modName))),
        Doc.english("shadows a previous definition from outer scope")
      );
    }
  }

  record ShadowingWarn(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Warn {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(Doc.english("The name"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("shadows a previous local definition from outer scope")
      );
    }
  }

  record QualifiedNameNotFoundError(
    @NotNull Seq<String> modName,
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The qualified name"),
        Doc.styled(Style.code(),
          Doc.cat(Doc.plain(QualifiedID.join(modName)), Doc.plain(Constants.SCOPE_SEPARATOR), Doc.plain(name))),
        Doc.english("is not defined in the current scope")
      );
    }
  }

  record UnqualifiedNameNotFoundError(
    @NotNull String name,
    @Override @NotNull SourcePos sourcePos
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("The name"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("is not defined in the current scope")
      );
    }
  }

  record OperatorNameNotFound(
    @Override @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements NameProblem.Error {
    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Unknown operator"),
        Doc.styled(Style.code(), Doc.plain(name)),
        Doc.english("used in bind statement")
      );
    }
  }
}
