// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IrExp;
import org.aya.anf.ir.struct.IrVarDecl;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

/// `FuncBuilder` builds the body of a function from a given `Term`.
/// This class does not use a functional design pattern as some semantic context used for `IrFunc`
/// construction are persistent and do not follow a stack structure (e.g., unnamed bindings whose
/// name are to be generated after suffcient context gathering).
public class FuncBuilder {

  private final @NotNull MutableList<IrVarDecl.Generated> unnamedVars = MutableList.create();
  private @NotNull MutableLinkedHashMap<String, IrVarDecl> binds = MutableLinkedHashMap.of();

  // private @NotNull IrExp buildTermUnderBinding(ImmutableSeq<LetClause> vars, Function<Term, IrExp> builder, Term term) {
  //   // XXX: use incremental hash map
  //   var restore = binds;
  //   binds = MutableLinkedHashMap.from(restore);
  //   vars.forEach(v -> binds.put(v.decl().identifier(), v.decl()));
  //   var exp = buildTerm(term);
  //   var wrapped = vars.foldRight(exp, (v, e) -> new IrExp.Let());
  //   binds = restore;
  //   return wrapped;
  // }

  private @NotNull IrExp buildTerm(@NotNull Term term) {
    return switch (term) {
      case FreeTerm(var bind) -> {
        yield null;
      }
      default -> throw new Panic("implement this plz: " + term.getClass());
    };
  }
}
