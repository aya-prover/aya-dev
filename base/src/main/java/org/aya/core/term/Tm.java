// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;

import java.util.function.Function;

/**
 * A `Tm<T>` is a more general representation of the `Term` AST.
 * It can be better understood as representing a `Term`-like node,
 * but with all immediate sub-`Term`s taking values from type `T`.
 * Here we also provide casting between `Tm<Term>` and `Term` as they are isomorphic.
 *
 * @author wsx
 */
public sealed interface Tm<T> {
  record Pi<T>(Param<T> param, T body) implements Tm<T> {}

  record Sigma<T>(ImmutableSeq<Param<T>> params) implements Tm<T> {}

  record Univ<T>(int lift) implements Tm<T> {}

  record Interval<T>() implements Tm<T> {}

  record End<T>(boolean side) implements Tm<T> {}

  record Str<T>(String s) implements Tm<T> {}

  record Lambda<T>(Param<T> param, T body) implements Tm<T> {}

  record Tuple<T>(ImmutableSeq<T> items) implements Tm<T> {}

  record New<T>(T struct, ImmutableMap<DefVar<FieldDef, TopTeleDecl.StructField>, T> fields) implements Tm<T> {}

  record App<T>(T of, Arg<T> arg) implements Tm<T> {}

  record Proj<T>(T of, int ix) implements Tm<T> {}

  record Struct<T>(DefVar<StructDef, TopTeleDecl.StructDecl> ref, int lift,
                   ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record Data<T>(DefVar<DataDef, TopTeleDecl.DataDecl> ref, int lift, ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record Con<T>(ConHead<T> head, ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record Fn<T>(DefVar<FnDef, TopTeleDecl.FnDecl> ref, int lift, ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record Access<T>(T of, DefVar<FieldDef, TopTeleDecl.StructField> ref, ImmutableSeq<Arg<T>> structArgs,
                   ImmutableSeq<Arg<T>> fieldArgs) implements Tm<T> {}

  record Prim<T>(DefVar<PrimDef, TopTeleDecl.PrimDecl> ref, PrimDef.ID id, int lift,
                 ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record Hole<T>(Meta ref, int lift, ImmutableSeq<Arg<T>> contextArgs, ImmutableSeq<Arg<T>> args) implements Tm<T> {}

  record ShapedInt<T>(int repr, AyaShape shape, T type) implements Tm<T> {}

  record Ref<T>(LocalVar var) implements Tm<T> {}

  record MetaPat<T>(Pat.Meta ref, int lift) implements Tm<T> {}

  record Field<T>(DefVar<FieldDef, TopTeleDecl.StructField> ref, int lift) implements Tm<T> {}

  record Error<T>(AyaDocile description, boolean isReallyError) implements Tm<T> {}

  default <R> Tm<R> map(Function<T, R> f) {
    return switch (this) {
      case Pi<T> pi -> new Pi<>(pi.param().map(f), f.apply(pi.body()));
      case Sigma<T> sigma -> new Sigma<>(sigma.params().map(param -> param.map(f)));
      case Univ<T> univ -> new Univ<>(univ.lift());
      case Interval<T> ignored -> new Interval<>();
      case End<T> end -> new End<>(end.side());
      case Str<T> str -> new Str<>(str.s());
      case Lambda<T> lambda -> new Lambda<>(lambda.param().map(f), f.apply(lambda.body()));
      case Tuple<T> tuple -> new Tuple<>(tuple.items().map(f));
      case New<T> nevv -> new New<>(
        f.apply(nevv.struct()),
        ImmutableMap.from(nevv.fields().view().map((k, v) -> kala.tuple.Tuple.of(k, f.apply(v))))
      );
      case App<T> app -> new App<>(f.apply(app.of()), app.arg().map(f));
      case Proj<T> proj -> new Proj<>(f.apply(proj.of()), proj.ix());
      case Struct<T> struct -> new Struct<>(struct.ref(), struct.lift(), struct.args().map(arg -> arg.map(f)));
      case Data<T> data -> new Data<>(data.ref(), data.lift(), data.args().map(arg -> arg.map(f)));
      case Con<T> con -> new Con<>(con.head().map(f), con.args().map(arg -> arg.map(f)));
      case Fn<T> fn -> new Fn<>(fn.ref(), fn.lift(), fn.args().map(arg -> arg.map(f)));
      case Access<T> access -> new Access<>(
        f.apply(access.of()), access.ref(),
        access.structArgs().map(arg -> arg.map(f)),
        access.fieldArgs().map(arg -> arg.map(f))
      );
      case Prim<T> prim -> new Prim<>(prim.ref(), prim.id(), prim.lift(), prim.args().map(arg -> arg.map(f)));
      case Hole<T> hole -> new Hole<>(
        hole.ref(), hole.lift(),
        hole.contextArgs().map(arg -> arg.map(f)),
        hole.args().map(arg -> arg.map(f))
      );
      case ShapedInt<T> shaped -> new ShapedInt<>(shaped.repr(), shaped.shape(), f.apply(shaped.type()));
      case Ref<T> ref -> new Ref<>(ref.var());
      case MetaPat<T> metaPat -> new MetaPat<>(metaPat.ref(), metaPat.lift());
      case Field<T> field -> new Field<>(field.ref(), field.lift());
      case Error<T> error -> new Error<>(error.description(), error.isReallyError());
    };
  }

  static Term cast(Tm<Term> tm) {
    return switch (tm) {
      case Pi<Term> pi -> new FormTerm.Pi(Param.cast(pi.param()), pi.body());
      case Sigma<Term> sigma -> new FormTerm.Sigma(sigma.params().map(Param::cast));
      case Univ<Term> univ -> new FormTerm.Univ(univ.lift());
      case Interval<Term> ignored -> FormTerm.Interval.INSTANCE;
      case End<Term> end -> end.side() ? PrimTerm.End.LEFT : PrimTerm.End.RIGHT;
      case Str<Term> str -> new PrimTerm.Str(str.s());
      case Lambda<Term> lambda -> new IntroTerm.Lambda(Param.cast(lambda.param()), lambda.body());
      case Tuple<Term> tuple -> new IntroTerm.Tuple(tuple.items());
      case New<Term> nevv -> new IntroTerm.New((CallTerm.Struct) nevv.struct(), nevv.fields());
      case App<Term> app -> new ElimTerm.App(app.of(), Arg.cast(app.arg()));
      case Proj<Term> proj -> new ElimTerm.Proj(proj.of(), proj.ix());
      case Struct<Term> struct -> new CallTerm.Struct(struct.ref(), struct.lift(), struct.args().map(Arg::cast));
      case Data<Term> data -> new CallTerm.Data(data.ref(), data.lift(), data.args().map(Arg::cast));
      case Con<Term> con -> new CallTerm.Con(ConHead.cast(con.head()), con.args().map(Arg::cast));
      case Fn<Term> fn -> new CallTerm.Fn(fn.ref(), fn.lift(), fn.args().map(Arg::cast));
      case Access<Term> access ->
        new CallTerm.Access(access.of(), access.ref(), access.structArgs().map(Arg::cast), access.fieldArgs().map(Arg::cast));
      case Prim<Term> prim -> new CallTerm.Prim(prim.ref(), prim.id(), prim.lift(), prim.args().map(Arg::cast));
      case Hole<Term> hole ->
        new CallTerm.Hole(hole.ref(), hole.lift(), hole.contextArgs().map(Arg::cast), hole.args().map(Arg::cast));
      case ShapedInt<Term> shaped -> new LitTerm.ShapedInt(shaped.repr(), shaped.shape(), shaped.type());
      case Ref<Term> ref -> new RefTerm(ref.var(), 0);
      case MetaPat<Term> metaPat -> new RefTerm.MetaPat(metaPat.ref(), metaPat.lift());
      case Field<Term> field -> new RefTerm.Field(field.ref(), field.lift());
      case Error<Term> error -> new ErrorTerm(error.description(), error.isReallyError());
    };
  }

  static Tm<Term> cast(Term term) {
    return switch (term) {
      case FormTerm.Pi pi -> new Tm.Pi<>(Param.cast(pi.param()), pi.body());
      case FormTerm.Sigma sigma -> new Tm.Sigma<>(sigma.params().map(Param::cast));
      case FormTerm.Univ univ -> new Tm.Univ<>(univ.lift());
      case FormTerm.Interval ignored -> new Tm.Interval<>();
      case PrimTerm.End end -> new Tm.End<>(end.isRight());
      case PrimTerm.Str str -> new Tm.Str<>(str.string());
      case IntroTerm.Lambda lambda -> new Tm.Lambda<>(Param.cast(lambda.param()), lambda.body());
      case IntroTerm.Tuple tuple -> new Tm.Tuple<>(tuple.items());
      case IntroTerm.New nevv -> new New<>(nevv.struct(), nevv.params());
      case ElimTerm.App app -> new Tm.App<>(app.of(), Arg.cast(app.arg()));
      case ElimTerm.Proj proj -> new Tm.Proj<>(proj.of(), proj.ix());
      case CallTerm.Struct struct -> new Tm.Struct<>(struct.ref(), struct.ulift(), struct.args().map(Arg::cast));
      case CallTerm.Data data -> new Tm.Data<>(data.ref(), data.ulift(), data.args().map(Arg::cast));
      case CallTerm.Con con -> new Tm.Con<>(ConHead.cast(con.head()), con.conArgs().map(Arg::cast));
      case CallTerm.Fn fn -> new Tm.Fn<>(fn.ref(), fn.ulift(), fn.args().map(Arg::cast));
      case CallTerm.Access access ->
        new Tm.Access<>(access.of(), access.ref(), access.structArgs().map(Arg::cast), access.fieldArgs().map(Arg::cast));
      case CallTerm.Prim prim -> new Tm.Prim<>(prim.ref(), prim.id(), prim.ulift(), prim.args().map(Arg::cast));
      case CallTerm.Hole hole ->
        new Tm.Hole<>(hole.ref(), hole.ulift(), hole.contextArgs().map(Arg::cast), hole.args().map(Arg::cast));
      case LitTerm.ShapedInt shaped -> new Tm.ShapedInt<>(shaped.repr(), shaped.shape(), shaped.type());
      case RefTerm ref -> new Tm.Ref<>(ref.var());
      case RefTerm.MetaPat metaPat -> new Tm.MetaPat<>(metaPat.ref(), metaPat.lift());
      case RefTerm.Field field -> new Tm.Field<>(field.ref(), field.lift());
      case ErrorTerm error -> new Tm.Error<>(error.description(), error.isReallyError());
    };
  }

  record Param<T>(LocalVar ref, T type, boolean pattern, boolean explicit) {
    private <R> Param<R> map(Function<T, R> f) {
      return new Param<>(ref, f.apply(type), pattern, explicit);
    }

    private static Term.Param cast(Param<Term> param) {
      return new Term.Param(param.ref(), param.type(), param.pattern(), param.explicit());
    }

    private static Param<Term> cast(Term.Param param) {
      return new Param<>(param.ref(), param.type(), param.pattern(), param.explicit());
    }
  }

  record Arg<T>(T term, boolean explicit) {
    private <R> Arg<R> map(Function<T, R> f) {
      return new Arg<>(f.apply(term), explicit);
    }

    private static org.aya.generic.Arg<Term> cast(Arg<Term> arg) {
      return new org.aya.generic.Arg<>(arg.term(), arg.explicit());
    }

    private static Arg<Term> cast(org.aya.generic.Arg<Term> arg) {
      return new Arg<>(arg.term(), arg.explicit());
    }
  }

  record ConHead<T>(
    DefVar<DataDef, TopTeleDecl.DataDecl> dataRef,
    DefVar<CtorDef, TopTeleDecl.DataCtor> ref,
    int lift, ImmutableSeq<Arg<T>> dataArgs) {
    private <R> ConHead<R> map(Function<T, R> f) {
      return new ConHead<>(dataRef, ref, lift, dataArgs.map(arg -> arg.map(f)));
    }

    private static CallTerm.ConHead cast(ConHead<Term> head) {
      return new CallTerm.ConHead(head.dataRef(), head.ref(), head.lift(), head.dataArgs().map(Arg::cast));
    }

    private static ConHead<Term> cast(CallTerm.ConHead head) {
      return new ConHead<>(head.dataRef(), head.ref(), head.ulift(), head.dataArgs().map(Arg::cast));
    }
  }
}
