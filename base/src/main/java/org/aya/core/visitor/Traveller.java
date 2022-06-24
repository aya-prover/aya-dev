package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.*;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;

/**
 * A `Traveller` provides the `traverse : Term -> R` method that visits the input term recursively
 * and build up a result of type `R` incrementally using the provided methods in the interface.
 * Notice this is somewhat similar to the tagless final style.
 * Sometimes providing a method for every possible term variant can be tedious and repetitive,
 * and the more specialized Traveller interfaces might be helpful.
 *
 * @author wsx
 */
// Travelling the world is such a rewarding and amazing experience.
// Once in a while, settling down is good, though.
interface Traveller<R> {
  R ref(LocalVar var);
  R lambda(Param<R> param, R body);
  R pi(Param<R> param, R body);
  R sigma(ImmutableSeq<Param<R>> params);
  R univ(int lift);
  R app(R of, Arg<R> arg);
  R fn(DefVar<FnDef, TopTeleDecl.FnDecl> ref, int lift, ImmutableSeq<Arg<R>> args);
  R data(DefVar<DataDef, TopTeleDecl.DataDecl> ref, int lift, ImmutableSeq<Arg<R>> args);
  R con(R data, DefVar<CtorDef, TopTeleDecl.DataCtor> ref, ImmutableSeq<Arg<R>> args);
  R struct(DefVar<StructDef, TopTeleDecl.StructDecl> ref, int lift, ImmutableSeq<Arg<R>> args);
  R prim(DefVar<PrimDef, TopTeleDecl.PrimDecl> ref, PrimDef.ID id, int lift, ImmutableSeq<Arg<R>> args);
  R tuple(ImmutableSeq<R> items);
  R nevv(R struct, ImmutableMap<DefVar<FieldDef, TopTeleDecl.StructField>, R> fields);
  R proj(R of, int ix);
  R access(R of, DefVar<FieldDef, TopTeleDecl.StructField> ref, ImmutableSeq<Arg<R>> structArgs, ImmutableSeq<Arg<R>> fieldArgs);
  R hole(Meta ref, int lift, ImmutableSeq<Arg<R>> contextArgs, ImmutableSeq<Arg<R>> args);
  R field(DefVar<FieldDef, TopTeleDecl.StructField> ref, int lift);
  R error(AyaDocile description, boolean isReallyError);
  R metaPat(Pat.Meta ref, int lift);
  R interval();
  R end(boolean side);
  R str(String s);
  R shaped(int repr, AyaShape shape, R type);

  default R traverse(Term term) {
    return switch (term) {
      case FormTerm.Pi pi -> pi(traverse(pi.param()), traverse(pi.body()));
      case FormTerm.Sigma sigma -> sigma(sigma.params().map(this::traverse));
      case FormTerm.Univ univ -> univ(univ.lift());
      case FormTerm.Interval ignored -> interval();
      case PrimTerm.End end -> end(end.isRight());
      case PrimTerm.Str str -> str(str.string());
      case IntroTerm.Lambda lambda -> lambda(traverse(lambda.param()), traverse(lambda.body()));
      case IntroTerm.Tuple tuple -> tuple(tuple.items().map(this::traverse));
      case IntroTerm.New nevv -> {
        var fields = ImmutableMap.from(nevv.params().view().map((k, v) -> Tuple.of(k, traverse(v))));
        yield nevv(traverse(nevv.struct()), fields);
      }
      case ElimTerm.App app -> app(traverse(app.of()), traverse(app.arg()));
      case ElimTerm.Proj proj -> proj(traverse(proj.of()), proj.ix());
      case CallTerm.Struct struct -> struct(struct.ref(), struct.ulift(), struct.args().map(this::traverse));
      case CallTerm.Data data -> data(data.ref(), data.ulift(), data.args().map(this::traverse));
      case CallTerm.Con con ->
        con(traverse(con.head().underlyingDataCall()), con.head().ref(), con.conArgs().map(this::traverse));
      case CallTerm.Fn fn -> fn(fn.ref(), fn.ulift(), fn.args().map(this::traverse));
      case CallTerm.Access access ->
        access(traverse(access.of()), access.ref(), access.structArgs().map(this::traverse), access.fieldArgs().map(this::traverse));
      case CallTerm.Prim prim -> prim(prim.ref(), prim.id(), prim.ulift(), prim.args().map(this::traverse));
      case CallTerm.Hole hole ->
        hole(hole.ref(), hole.ulift(), hole.contextArgs().map(this::traverse), hole.args().map(this::traverse));
      case LitTerm.ShapedInt shaped -> shaped(shaped.repr(), shaped.shape(), traverse(shaped.type()));
      case RefTerm.Field field -> field(field.ref(), field.lift());
      case RefTerm ref -> ref(ref.var());
      case RefTerm.MetaPat metaPat -> metaPat(metaPat.ref(), metaPat.lift());
      case ErrorTerm error -> error(error.description(), error.isReallyError());
    };
  }

  default Param<R> traverse(Term.Param param) {
    return new Param<>(param.ref(), traverse(param.type()), param.pattern(), param.explicit());
  }

  default Arg<R> traverse(org.aya.generic.Arg<Term> arg) {
    return new Arg<>(traverse(arg.term()), arg.explicit());
  }

  record Param<R>(LocalVar var, R type, boolean pattern, boolean explicit) {}

  record Arg<R>(R term, boolean explicit) {}
}
