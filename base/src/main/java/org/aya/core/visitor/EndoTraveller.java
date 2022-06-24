// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
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
 * A convenient interface to obtain an endofunction on `Term`.
 * One only need to provide the `act : Term -> Term` method specifying one step in traversal,
 * and with the help of pattern matching and default branch,
 * this can be significantly shorter than providing the implementation for all variants.
 * Notice the derived `traverse` method attempt to preserve object identity when possible,
 * hence implementation of `act` might take advantage of this.
 *
 * @author wsx
 */
public interface EndoTraveller extends Traveller<Term> {
  Term act(Term term);

  default Term ref(LocalVar var) {
    return act(new RefTerm(var, 0));
  }
  default Term lambda(Param<Term> param, Term body) {
    return act(new IntroTerm.Lambda(coerce(param), body));
  }
  default Term pi(Param<Term> param, Term body) {
    return act(new FormTerm.Pi(coerce(param), body));
  }
  default Term sigma(ImmutableSeq<Param<Term>> params) {
    return act(new FormTerm.Sigma(params.map(this::coerce)));
  }
  default Term univ(int lift) {
    return act(new FormTerm.Univ(lift));
  }
  default Term app(Term of, Arg<Term> arg) {
    return act(new ElimTerm.App(of, coerce(arg)));
  }
  default Term fn(DefVar<FnDef, TopTeleDecl.FnDecl> ref, int lift, ImmutableSeq<Arg<Term>> args) {
    return act(new CallTerm.Fn(ref, lift, args.map(this::coerce)));
  }
  default Term data(DefVar<DataDef, TopTeleDecl.DataDecl> ref, int lift, ImmutableSeq<Arg<Term>> args) {
    return act(new CallTerm.Data(ref, lift, args.map(this::coerce)));
  }
  default Term con(Term data, DefVar<CtorDef, TopTeleDecl.DataCtor> ref, ImmutableSeq<Arg<Term>> args) {
    var d = (CallTerm.Data) data;
    return act(new CallTerm.Con(new CallTerm.ConHead(d.ref(), ref, d.ulift(), d.args()), args.map(this::coerce)));
  }
  default Term struct(DefVar<StructDef, TopTeleDecl.StructDecl> ref, int lift, ImmutableSeq<Arg<Term>> args) {
    return act(new CallTerm.Struct(ref, lift, args.map(this::coerce)));
  }
  default Term prim(DefVar<PrimDef, TopTeleDecl.PrimDecl> ref, PrimDef.ID id, int lift, ImmutableSeq<Arg<Term>> args) {
    return act(new CallTerm.Prim(ref, id, lift, args.map(this::coerce)));
  }
  default Term tuple(ImmutableSeq<Term> items) {
    return act(new IntroTerm.Tuple(items));
  }
  default Term nevv(Term struct, ImmutableMap<DefVar<FieldDef, TopTeleDecl.StructField>, Term> fields) {
    return act(new IntroTerm.New((CallTerm.Struct) struct, fields));
  }
  default Term proj(Term of, int ix) {
    return act(new ElimTerm.Proj(of, ix));
  }
  default Term access(Term of, DefVar<FieldDef, TopTeleDecl.StructField> ref, ImmutableSeq<Arg<Term>> structArgs, ImmutableSeq<Arg<Term>> fieldArgs) {
    return act(new CallTerm.Access(of, ref, structArgs.map(this::coerce), fieldArgs.map(this::coerce)));
  }
  default Term hole(Meta ref, int lift, ImmutableSeq<Arg<Term>> contextArgs, ImmutableSeq<Arg<Term>> args) {
    return act(new CallTerm.Hole(ref, lift, contextArgs.map(this::coerce), args.map(this::coerce)));
  }
  default Term field(DefVar<FieldDef, TopTeleDecl.StructField> ref, int lift) {
    return act(new RefTerm.Field(ref, lift));
  }
  default Term error(AyaDocile description, boolean isReallyError) {
    return act(new ErrorTerm(description, isReallyError));
  }
  default Term metaPat(Pat.Meta ref, int lift) {
    return act(new RefTerm.MetaPat(ref, lift));
  }
  default Term interval() {
    return act(FormTerm.Interval.INSTANCE);
  }
  default Term end(boolean side) {
    return act(side ? PrimTerm.End.LEFT : PrimTerm.End.RIGHT);
  }
  default Term str(String s) {
    return act(new PrimTerm.Str(s));
  }
  default Term shaped(int repr, AyaShape shape, Term type) {
    return act(new LitTerm.ShapedInt(repr, shape, type));
  }

  default Term traverse(Term term) {
    return act(switch (term) {
      case FormTerm.Pi pi -> {
        var param = traverseParam(pi.param());
        var body = traverse(pi.body());
        if (param == pi.param() && body == pi.body()) yield pi;
        yield new FormTerm.Pi(param, body);
      }
      case FormTerm.Sigma sigma -> {
        var params = sigma.params().map(this::traverseParam);
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new FormTerm.Sigma(params);
      }
      case FormTerm.Univ univ -> univ;
      case FormTerm.Interval interval -> interval;
      case PrimTerm.End end -> end;
      case PrimTerm.Str str -> str;
      case IntroTerm.Lambda lambda -> {
        var param = traverseParam(lambda.param());
        var body = traverse(lambda.body());
        if (param == lambda.param() && body == lambda.body()) yield lambda;
        yield new IntroTerm.Lambda(param, body);
      }
      case IntroTerm.Tuple tuple -> {
        var items = tuple.items().map(this::traverse);
        if (items.sameElements(tuple.items(), true)) yield tuple;
        yield new IntroTerm.Tuple(items);
      }
      case IntroTerm.New neu -> {
        var struct = traverse(neu.struct());
        var fields = ImmutableMap.from(neu.params().view().map((k, v) -> Tuple.of(k, traverse(v))));
        if (struct == neu.struct() && fields.valuesView().sameElements(neu.params().valuesView())) yield neu;
        yield new IntroTerm.New((CallTerm.Struct) struct, fields);
      }
      case ElimTerm.App app -> {
        var function = traverse(app.of());
        var arg = traverseArg(app.arg());
        if (function == app.of() && arg == app.arg()) yield app;
        yield CallTerm.make(function, arg);
      }
      case ElimTerm.Proj proj -> {
        var tuple = traverse(proj.of());
        if (tuple == proj.of()) yield proj;
        yield new ElimTerm.Proj(tuple, proj.ix());
      }
      case CallTerm.Struct struct -> {
        var args = struct.args().map(this::traverseArg);
        if (args.sameElements(struct.args(), true)) yield struct;
        yield new CallTerm.Struct(struct.ref(), struct.ulift(), args);
      }
      case CallTerm.Data data -> {
        var args = data.args().map(this::traverseArg);
        if (args.sameElements(data.args(), true)) yield data;
        yield new CallTerm.Data(data.ref(), data.ulift(), args);
      }
      case CallTerm.Con con -> {
        var head = traverseHead(con.head());
        var args = con.conArgs().map(this::traverseArg);
        if (head == con.head() && args.sameElements(con.conArgs(), true)) yield con;
        yield new CallTerm.Con(head, args);
      }
      case CallTerm.Fn fn -> {
        var args = fn.args().map(this::traverseArg);
        if (args.sameElements(fn.args(), true)) yield fn;
        yield new CallTerm.Fn(fn.ref(), fn.ulift(), args);
      }
      case CallTerm.Access access -> {
        var struct = traverse(access.of());
        var structArgs = access.structArgs().map(this::traverseArg);
        var fieldArgs = access.fieldArgs().map(this::traverseArg);
        if (struct == access.of()
          && structArgs.sameElements(access.structArgs(), true)
          && fieldArgs.sameElements(access.fieldArgs(), true))
          yield access;
        yield new CallTerm.Access(struct, access.ref(), structArgs, fieldArgs);
      }
      case CallTerm.Prim prim -> {
        var args = prim.args().map(this::traverseArg);
        if (args.sameElements(prim.args(), true)) yield prim;
        yield new CallTerm.Prim(prim.ref(), prim.ulift(), args);
      }
      case CallTerm.Hole hole -> {
        var contextArgs = hole.contextArgs().map(this::traverseArg);
        var args = hole.args().map(this::traverseArg);
        if (contextArgs.sameElements(hole.contextArgs(), true) && args.sameElements(hole.args(), true)) yield hole;
        yield new CallTerm.Hole(hole.ref(), hole.ulift(), contextArgs, args);
      }
      case LitTerm.ShapedInt shaped -> {
        var type = traverse(shaped.type());
        if (type == shaped.type()) yield shaped;
        yield new LitTerm.ShapedInt(shaped.repr(), shaped.shape(), type);
      }
      case RefTerm.Field field -> field;
      case RefTerm ref -> ref;
      case RefTerm.MetaPat metaPat -> metaPat;
      case ErrorTerm error -> error;
    });
  }

  private Term.Param traverseParam(Term.Param param) {
    var type = traverse(param.type());
    if (type == param.type()) return param;
    return new Term.Param(param, type);
  }
  private org.aya.generic.Arg<Term> traverseArg(org.aya.generic.Arg<Term> arg) {
    var term = traverse(arg.term());
    if (term == arg.term()) return arg;
    return new org.aya.generic.Arg<>(term, arg.explicit());
  }
  private CallTerm.ConHead traverseHead(CallTerm.ConHead head) {
    var args = head.dataArgs().map(this::traverseArg);
    if (args.sameElements(head.dataArgs(), true)) return head;
    return new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift(), args);
  }
  private Term.Param coerce(Param<Term> param) {
    return new Term.Param(param.var(), param.type(), param.pattern(), param.explicit());
  }
  private org.aya.generic.Arg<Term> coerce(Arg<Term> arg) {
    return new org.aya.generic.Arg<>(arg.term(), arg.explicit());
  }
}
