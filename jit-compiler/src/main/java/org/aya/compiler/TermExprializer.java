// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.generic.stmt.Shaped;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.core.term.repr.*;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static org.aya.compiler.AyaSerializer.*;
import static org.aya.compiler.ExprializeUtils.*;
import static org.aya.compiler.NameSerializer.getClassRef;

/**
 * Build the "constructor form" of {@link Term}, but in Java.
 */
public final class TermExprializer extends AbstractExprializer<Term> {
  public static final String CLASS_LAMTERM = ExprializeUtils.getJavaRef(LamTerm.class);
  public static final String CLASS_JITLAMTERM = ExprializeUtils.getJavaRef(Closure.Jit.class);
  public static final String CLASS_APPTERM = ExprializeUtils.getJavaRef(AppTerm.class);
  public static final String CLASS_SORTKIND = ExprializeUtils.getJavaRef(SortKind.class);
  public static final String CLASS_INTOPS = ExprializeUtils.getJavaRef(IntegerOps.class);
  public static final String CLASS_LISTOPS = ExprializeUtils.getJavaRef(ListOps.class);
  public static final String CLASS_INTEGER = ExprializeUtils.getJavaRef(IntegerTerm.class);
  public static final String CLASS_LIST = ExprializeUtils.getJavaRef(ListTerm.class);
  public static final String CLASS_STRING = ExprializeUtils.getJavaRef(StringTerm.class);
  public static final String CLASS_LOCALTERM = ExprializeUtils.getJavaRef(LocalTerm.class);
  public static final String CLASS_INT_CONRULE = ExprializeUtils.makeSub(CLASS_INTOPS, ExprializeUtils.getJavaRef(IntegerOps.ConRule.class));
  public static final String CLASS_INT_FNRULE = ExprializeUtils.makeSub(CLASS_INTOPS, ExprializeUtils.getJavaRef(IntegerOps.FnRule.class));
  public static final String CLASS_LIST_CONRULE = ExprializeUtils.makeSub(CLASS_LISTOPS, ExprializeUtils.getJavaRef(ListOps.ConRule.class));
  public static final String CLASS_FNRULE_KIND = ExprializeUtils.makeSub(CLASS_INT_FNRULE, ExprializeUtils.getJavaRef(IntegerOps.FnRule.Kind.class));
  public static final String CLASS_RULEREDUCER = ExprializeUtils.getJavaRef(RuleReducer.class);
  public static final String CLASS_RULE_CON = ExprializeUtils.makeSub(CLASS_RULEREDUCER, ExprializeUtils.getJavaRef(RuleReducer.Con.class));
  public static final String CLASS_RULE_FN = ExprializeUtils.makeSub(CLASS_RULEREDUCER, ExprializeUtils.getJavaRef(RuleReducer.Fn.class));
  public static final String CLASS_NEW = ExprializeUtils.getJavaRef(NewTerm.class);
  public static final String CLASS_MEMCALL = ExprializeUtils.getJavaRef(MemberCall.class);
  public static final String CLASS_CASTTERM = ExprializeUtils.getJavaRef(ClassCastTerm.class);
  public static final String CLASS_CLSCALL = ExprializeUtils.getJavaRef(ClassCall.class);
  public static final String CLASS_CLOSURE = ExprializeUtils.getJavaRef(Closure.class);

  /**
   * Terms that should be instantiated
   */
  private final @NotNull ImmutableSeq<String> instantiates;
  private final @NotNull MutableMap<LocalVar, String> binds;

  /**
   * Whether allow LocalTerm, false in default (in order to report unexpected LocalTerm)
   */
  private final boolean allowLocalTerm;

  public TermExprializer(@NotNull NameGenerator nameGen, @NotNull ImmutableSeq<String> instantiates) {
    this(nameGen, instantiates, false);
  }

  public TermExprializer(@NotNull NameGenerator nameGen, @NotNull ImmutableSeq<String> instantiates, boolean allowLocalTer) {
    super(nameGen);
    this.instantiates = instantiates;
    this.allowLocalTerm = allowLocalTer;
    this.binds = MutableMap.create();
  }

  private @NotNull String serializeApplicable(@NotNull Shaped.Applicable<?> applicable) {
    return switch (applicable) {
      case IntegerOps.ConRule conRule ->
        ExprializeUtils.makeNew(CLASS_INT_CONRULE, ExprializeUtils.getInstance(NameSerializer.getClassRef(conRule.ref())),
          doSerialize(conRule.zero())
        );
      case IntegerOps.FnRule fnRule -> ExprializeUtils.makeNew(CLASS_INT_FNRULE,
        ExprializeUtils.getInstance(NameSerializer.getClassRef(fnRule.ref())),
        ExprializeUtils.makeSub(CLASS_FNRULE_KIND, fnRule.kind().toString())
      );
      case ListOps.ConRule conRule -> ExprializeUtils.makeNew(CLASS_LIST_CONRULE,
        ExprializeUtils.getInstance(NameSerializer.getClassRef(conRule.ref())),
        doSerialize(conRule.empty())
      );
      default -> Panic.unreachable();
    };
  }

  /**
   * This code requires that {@link FnCall}, {@link RuleReducer.Fn} and {@link RuleReducer.Con}
   * {@code ulift} is the second parameter, {@code args.get(i)} is the {@code i + 3}th parameter
   *
   * @param fixed whether {@param reducible} has fixed `invoke`
   */
  private @NotNull String buildReducibleCall(
    @NotNull String reducible,
    @NotNull String callName,
    int ulift,
    @NotNull ImmutableSeq<ImmutableSeq<Term>> args,
    boolean fixed
  ) {
    var seredArgs = args.map(x -> x.map(this::doSerialize));
    var seredSeq = seredArgs.map(x -> ExprializeUtils.makeImmutableSeq(CLASS_TERM, x));
    var flatArgs = seredArgs.flatMap(x -> x);

    var callArgs = new String[seredSeq.size() + 2];
    callArgs[0] = reducible;
    callArgs[1] = "0"; // elevate later
    for (var i = 0; i < seredSeq.size(); ++i) {
      callArgs[i + 2] = seredSeq.get(i);
    }

    var elevate = ulift > 0 ? ".elevate(" + ulift + ")" : "";
    var onStuck = makeThunk(ExprializeUtils.makeNew(callName, callArgs));
    var finalArgs = fixed
      ? flatArgs.view().prepended(onStuck).joinToString()
      : onStuck + ", " + ExprializeUtils.makeImmutableSeq(CLASS_TERM, flatArgs);

    return reducible + ".invoke(" + finalArgs + ")" + elevate;
  }

  @Override protected @NotNull String doSerialize(@NotNull Term term) {
    return switch (term) {
      case FreeTerm(var bind) -> {
        // It is possible that we meet bind here,
        // the serializer will instantiate some variable while serializing LamTerm
        var subst = binds.getOrNull(bind);
        if (subst == null) {
          throw new Panic("No substitution for " + bind + " during serialization");
        }

        yield subst;
      }
      case TyckInternal i -> throw new Panic(i.getClass().toString());
      case Callable.SharableCall call when call.ulift() == 0 && call.args().isEmpty() ->
        ExprializeUtils.getEmptyCallTerm(NameSerializer.getClassRef(call.ref()));
      case ClassCall(var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_CLSCALL,
        getInstance(NameSerializer.getClassRef(ref)),
        Integer.toString(ulift),
        serializeClosureToImmutableSeq(args)
      );
      case MemberCall(var of, var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_MEMCALL,
        doSerialize(of),
        ExprializeUtils.getInstance(NameSerializer.getClassRef(ref)),
        Integer.toString(ulift),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case AppTerm appTerm -> makeAppNew(CLASS_APPTERM, appTerm.fun(), appTerm.arg());
      case LocalTerm _ when !allowLocalTerm -> throw new Panic("LocalTerm");
      case LocalTerm(var index) -> ExprializeUtils.makeNew(CLASS_LOCALTERM, Integer.toString(index));
      case LamTerm lamTerm -> ExprializeUtils.makeNew(CLASS_LAMTERM, serializeClosure(lamTerm.body()));
      case DataCall(var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_DATACALL,
        ExprializeUtils.getInstance(NameSerializer.getClassRef(ref)),
        Integer.toString(ulift),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case ConCall(var head, var args) -> ExprializeUtils.makeNew(CLASS_CONCALL,
        ExprializeUtils.getInstance(NameSerializer.getClassRef(head.ref())),
        serializeToImmutableSeq(CLASS_TERM, head.ownerArgs()),
        Integer.toString(head.ulift()),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case FnCall call -> {
        var ref = switch (call.ref()) {
          case JitFn jit -> ExprializeUtils.getInstance(NameSerializer.getClassRef(jit));
          case FnDef.Delegate def -> ExprializeUtils.getInstance(getClassRef(def.ref));
        };

        var args = call.args();
        yield buildReducibleCall(ref, CLASS_FNCALL, call.ulift(), ImmutableSeq.of(args), true);
      }
      case RuleReducer.Con conRuler -> buildReducibleCall(
        serializeApplicable(conRuler.rule()),
        CLASS_RULE_CON, conRuler.ulift(),
        ImmutableSeq.of(conRuler.ownerArgs(), conRuler.conArgs()),
        false
      );
      case RuleReducer.Fn fnRuler -> buildReducibleCall(
        serializeApplicable(fnRuler.rule()),
        CLASS_RULE_FN, fnRuler.ulift(),
        ImmutableSeq.of(fnRuler.args()),
        false
      );
      case SortTerm sort when sort.equals(SortTerm.Type0) -> ExprializeUtils.makeSub(
        ExprializeUtils.getJavaRef(SortTerm.class), "Type0");
      case SortTerm sort when sort.equals(SortTerm.ISet) -> ExprializeUtils.makeSub(
        ExprializeUtils.getJavaRef(SortTerm.class), "ISet");
      case SortTerm(var kind, var ulift) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(SortTerm.class),
        ExprializeUtils.makeSub(CLASS_SORTKIND, kind.name()),
        Integer.toString(ulift));
      case DepTypeTerm(var kind, var param, var body) -> ExprializeUtils.makeNew(
        ExprializeUtils.getJavaRef(DepTypeTerm.class),
        ExprializeUtils.makeSub(ExprializeUtils.getJavaRef(DTKind.class), kind.name()),
        doSerialize(param),
        serializeClosure(body)
      );
      case CoeTerm(var type, var r, var s) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(CoeTerm.class),
        serializeClosure(type),
        doSerialize(r),
        doSerialize(s)
      );
      case ProjTerm(var of, var ix) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(ProjTerm.class),
        doSerialize(of),
        Integer.toString(ix)
      );
      case PAppTerm(var fun, var arg, var a, var b) -> makeAppNew(ExprializeUtils.getJavaRef(PAppTerm.class),
        fun, arg, a, b
      );
      case EqTerm(var A, var a, var b) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(EqTerm.class),
        serializeClosure(A),
        doSerialize(a), doSerialize(b)
      );
      case DimTyTerm _ -> ExprializeUtils.getInstance(ExprializeUtils.getJavaRef(DimTyTerm.class));
      case DimTerm dim -> ExprializeUtils.makeSub(ExprializeUtils.getJavaRef(DimTerm.class), dim.name());
      case TupTerm(var l, var r) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(TupTerm.class),
        serializeToImmutableSeq(CLASS_TERM, ImmutableSeq.of(l, r))
      );
      case PrimCall(var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_PRIMCALL,
        ExprializeUtils.getInstance(NameSerializer.getClassRef(ref)),
        Integer.toString(ulift),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case IntegerTerm(var repr, var zero, var suc, var type) -> ExprializeUtils.makeNew(CLASS_INTEGER,
        Integer.toString(repr),
        ExprializeUtils.getInstance(NameSerializer.getClassRef(zero)),
        ExprializeUtils.getInstance(NameSerializer.getClassRef(suc)),
        doSerialize(type)
      );
      case ListTerm(var repr, var nil, var cons, var type) -> ExprializeUtils.makeNew(CLASS_LIST,
        ExprializeUtils.makeImmutableSeq(CLASS_TERM, repr.map(this::doSerialize), CLASS_PIMMSEQ),
        ExprializeUtils.getInstance(NameSerializer.getClassRef(nil)),
        ExprializeUtils.getInstance(NameSerializer.getClassRef(cons)),
        doSerialize(type)
      );
      case StringTerm stringTerm -> ExprializeUtils.makeNew(CLASS_STRING,
        ExprializeUtils.makeString(StringUtil.escapeStringCharacters(stringTerm.string())));
      case ClassCastTerm(var classRef, var subterm, var rember, var forgor) -> makeNew(CLASS_CASTTERM,
        getInstance(NameSerializer.getClassRef(classRef)),
        serialize(subterm),
        serializeClosureToImmutableSeq(rember),
        serializeClosureToImmutableSeq(forgor)
      );
      case NewTerm(var classCall) -> ExprializeUtils.makeNew(CLASS_NEW, doSerialize(classCall));
    };
  }

  // def f (A : Type) : Fn (a : A) -> A
  // (A : Type) : Pi(^0, IdxClosure(^1))
  // (A : Type) : Pi(^0, JitClosure(_ -> ^1))

  private @NotNull String with(@NotNull String subst, @NotNull Function<Term, String> continuation) {
    var bind = new LocalVar(subst);
    this.binds.put(bind, subst);
    var result = continuation.apply(new FreeTerm(bind));
    this.binds.remove(bind);
    return result;
  }

  private @NotNull String serializeClosureToImmutableSeq(@NotNull ImmutableSeq<Closure> cls) {
    return makeImmutableSeq(CLASS_CLOSURE, cls.map(this::serializeClosure));
  }

  private @NotNull String serializeClosure(@NotNull Closure body) {
    return serializeClosure(nameGen.nextName(), body);
  }

  private @NotNull String serializeClosure(@NotNull String param, @NotNull Closure body) {
    return ExprializeUtils.makeNew(CLASS_JITLAMTERM, param + " -> " + with(param, t -> doSerialize(body.apply(t))));
  }

  @Override public @NotNull String serialize(Term unit) {
    binds.clear();
    var vars = ImmutableSeq.fill(instantiates.size(), i -> new LocalVar("arg" + i));
    unit = unit.instantiateTeleVar(vars.view());
    vars.forEachWith(instantiates, binds::put);

    return doSerialize(unit);
  }
}
