// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.ClassDef;
import org.aya.core.def.Def;
import org.aya.core.def.MemberDef;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.Subst;
import org.aya.ref.DefVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.FieldError;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * ClassCall is a very special construction in Aya.
 * <ul>
 *   <li>It is like a type when partially instantiated -- the type of "specifications" of the rest of the fields.</li>
 *   <li>It is like a term when fully instantiated, whose type can be anything.</li>
 *   <li>It can be applied like a function, which essentially inserts the nearest missing field.</li>
 * </ul>
 *
 * @author kiva
 */
public record ClassCall(
  @Override @NotNull DefVar<ClassDef, ClassDecl> ref,
  @Override int ulift,
  @NotNull ImmutableMap<DefVar<MemberDef, TeleDecl.ClassMember>, Arg<Term>> args
) implements StableWHNF, Formation {
  public @NotNull Subst fieldSubst(@Nullable MemberDef member) {
    var fieldSubst = new Subst();
    for (var mapping : ref.core.members) {
      if (mapping == member) break;
      var inst = args.get(mapping.ref);
      fieldSubst.add(mapping.ref, inst.term());
    }
    return fieldSubst;
  }

  public boolean instantiated(@NotNull MemberDef member) {
    return args.containsKey(member.ref);
  }

  /** @return true if these two calls have same members applied */
  public boolean sameApply(@NotNull ClassCall other) {
    return ref == other.ref && args.keysView().toImmutableSet().sameElements(other.args.keysView().toImmutableSet(), true);
  }

  /** must be called after checking {@link #sameApply(ClassCall)} if the return value is used in comparator. */
  public @NotNull ImmutableSeq<Arg<Term>> orderedArgs() {
    return ref.core.members.flatMap(m -> args.getOption(m.ref));
  }

  public @NotNull Result<ClassCall, Problem> addMember(@NotNull Expr.Field<Expr> member, @NotNull ExprTycker exprTycker) {
    var fieldRefOpt = ref.core.members.find(m -> m.ref.name().equals(member.name().data()));
    if (fieldRefOpt.isEmpty())
      return Result.err(new FieldError.NoSuchField(ref, member));
    var memberRef = fieldRefOpt.get().ref.core;
    if (instantiated(memberRef))
      throw new UnsupportedOperationException("TODO: report duplicate field");
    member.resolvedField().set(memberRef.ref);
    var subst = fieldSubst(memberRef);
    var type = Def.defType(memberRef.ref).subst(subst, ulift);
    var telescope = Term.Param.subst(Def.defTele(memberRef.ref), subst, ulift);
    var bindings = member.bindings();
    if (telescope.sizeLessThan(bindings.size())) {
      var errPos = member.sourcePos().sourcePosForSubExpr(bindings.view().map(WithPos::sourcePos));
      return Result.err(new FieldError.ArgMismatch(errPos, memberRef, bindings.size()));
    }
    var fieldExpr = bindings.zipView(telescope).foldRight(member.body(), (pair, lamExpr) ->
      new Expr.Lambda(member.body().sourcePos(),
        new Expr.Param(pair.component1().sourcePos(),
          pair.component1().data(), pair.component2().explicit()), lamExpr));
    var field = exprTycker.inherit(fieldExpr, type).wellTyped();
    var newArgs = args.putted(memberRef.ref, new Arg<>(field, true));
    return Result.ok(new ClassCall(ref, ulift, newArgs));
  }

  public @NotNull ClassCall update(@NotNull ImmutableSeq<Tuple2<DefVar<MemberDef, TeleDecl.ClassMember>, Arg<Term>>> args) {
    return args.sameElements(args().toImmutableSeq(), true) ? this : new ClassCall(ref(), ulift(), ImmutableMap.from(args));
  }

  @Override public @NotNull ClassCall descent(@NotNull UnaryOperator<@NotNull Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(args.toImmutableSeq().map(t -> Tuple.of(t.component1(), t.component2().descent(f))));
  }
}
