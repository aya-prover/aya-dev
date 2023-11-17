// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.parse.ModifierParser;
import org.aya.cli.parse.ModifierParser.Filter;
import org.aya.cli.parse.ModifierParser.Modifier;
import org.aya.cli.parse.ModifierProblem;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.DeclInfo;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.Function;

import static org.aya.cli.parse.ModifierParser.Modifier.*;
import static org.aya.cli.parse.ModifierParser.Modifiers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ModifierParserTest {
  private <R> @NotNull Tuple2<CollectingReporter, R> withParser(@NotNull Function<ModifierParser, R> action) {
    var reporter = new BufferReporter();
    var parser = new ModifierParser(reporter);
    var result = action.apply(parser);
    return Tuple.of(reporter, result);
  }

  @Test public void implication() {
    var posModis = ImmutableSeq.of(Private, Example).map(x -> new WithPos<>(SourcePos.NONE, x));
    var result = withParser(parser -> parse(parser, posModis));
    var reporter = result.component1();
    var returns = result.component2();

    assertEquals(1, reporter.problems().size());
    assertEquals(1, reporter.problemSize(Problem.Severity.WARN));

    var warn = reporter.problems().getFirst();
    assertInstanceOf(ModifierProblem.class, warn);
    assertEquals(Private, ((ModifierProblem) warn).modifier());

    var modis2 = ImmutableSeq.of(new WithPos<>(SourcePos.NONE, Example));
    var returns2 = withParser(parser -> parse(parser, modis2)).component2();

    assertEquals(Stmt.Accessibility.Private, returns.accessibility().data());
    assertEquals(DeclInfo.Personality.EXAMPLE, returns.personality().data());
    assertEquals(Stmt.Accessibility.Private, returns2.accessibility().data());
    assertEquals(DeclInfo.Personality.EXAMPLE, returns2.personality().data());
  }

  private @NotNull Modifiers parse(@NotNull ModifierParser parser, @NotNull ImmutableSeq<WithPos<Modifier>> modifiers) {
    var filter = Filter.create(
      new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public),
      new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL),
      EnumSet.of(Opaque, Inline, Overlap, Open));
    return parser.parse(modifiers, filter);
  }
}
