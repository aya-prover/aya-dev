// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeJava;
import org.aya.compiler.free.FreeJavaBuilder;
import org.aya.compiler.free.FreeUtils;
import org.aya.compiler.free.data.MethodData;
import org.aya.generic.stmt.Reducible;
import org.aya.generic.stmt.Shaped;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.prettier.FindUsage;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.core.term.repr.*;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.util.Objects;
import java.util.function.*;

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
  public static final String CLASS_MATCHTERM = ExprializeUtils.getJavaRef(MatchTerm.class);
  public static final String CLASS_MATCHING = ExprializeUtils.makeSub(CLASS_TERM, getJavaRef(Term.Matching.class));

  /**
   * Terms that should be instantiated
   */
  private final @NotNull ImmutableSeq<FreeJava> instantiates;
  private final @NotNull MutableLinkedHashMap<LocalVar, FreeJava> binds;

  /**
   * Whether allow LocalTerm, false in default (in order to report unexpected LocalTerm)
   */
  private final boolean allowLocalTerm;

  public TermExprializer(@NotNull FreeJavaBuilder.ExprBuilder nameGen, @NotNull ImmutableSeq<FreeJava> instantiates) {
    this(nameGen, instantiates, false);
  }

  public TermExprializer(@NotNull FreeJavaBuilder.ExprBuilder nameGen, @NotNull ImmutableSeq<FreeJava> instantiates, boolean allowLocalTer) {
    super(nameGen);
    this.instantiates = instantiates;
    this.allowLocalTerm = allowLocalTer;
    this.binds = MutableLinkedHashMap.of();
  }

  private TermExprializer(
    @NotNull FreeJavaBuilder.ExprBuilder builder,
    @NotNull MutableLinkedHashMap<LocalVar, FreeJava> newBinds,
    boolean allowLocalTerm
  ) {
    super(builder);
    this.instantiates = ImmutableSeq.empty();
    this.binds = newBinds;
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull FreeJava serializeApplicable(@NotNull Shaped.Applicable<?> applicable) {
    return switch (applicable) {
      case IntegerOps.ConRule conRule -> builder.mkNew(IntegerOps.ConRule.class, ImmutableSeq.of(
        getInstance(conRule.ref()),
        doSerialize(conRule.zero())
      ));
      case IntegerOps.FnRule fnRule -> builder.mkNew(IntegerOps.FnRule.class, ImmutableSeq.of(
        getInstance(fnRule.ref()),
        builder.refEnum(fnRule.kind())
      ));
      case ListOps.ConRule conRule -> builder.mkNew(ListOps.ConRule.class, ImmutableSeq.of(
        getInstance(conRule.ref()),
        doSerialize(conRule.empty())
      ));
      default -> Panic.unreachable();
    };
  }

  /**
   * This code requires that {@link FnCall}, {@link RuleReducer.Fn} and {@link RuleReducer.Con}
   * {@code ulift} is the second parameter, {@code args.get(i)} is the {@code i + 3}th parameter
   *
   * @param reducibleType the ref class of {@param reducible}, used when {@param fixed} is true
   * @param fixed         whether {@param reducible} has fixed `invoke`,
   *                      i.e. {@link JitFn} does but {@link org.aya.generic.stmt.Shaped.Applicable} doesn't.
   */
  private @NotNull FreeJava buildReducibleCall(
    @Nullable ClassDesc reducibleType,
    @NotNull FreeJava reducible,
    @NotNull Class<?> callName,
    int ulift,
    @NotNull ImmutableSeq<ImmutableSeq<Term>> args,
    boolean fixed
  ) {
    var seredArgs = args.map(x -> x.map(this::doSerialize));
    var seredSeq = seredArgs.map(x -> makeImmutableSeq(Term.class, x));
    var flatArgs = seredArgs.flatMap(x -> x);

    var callArgs = new FreeJava[seredSeq.size() + 2];
    callArgs[0] = reducible;
    callArgs[1] = builder.iconst(0); // elevate later
    for (var i = 0; i < seredSeq.size(); ++i) {
      callArgs[i + 2] = seredSeq.get(i);
    }

    UnaryOperator<FreeJava> doElevate = free -> ulift == 0
      ? free
      : builder.invoke(Constants.ELEVATE, free, ImmutableSeq.of(builder.iconst(ulift)));
    var onStuck = makeThunk(te -> te.builder.mkNew(callName, ImmutableArray.Unsafe.wrap(callArgs)));
    var finalInvocation = fixed
      ? builder.invoke(
      resolveInvoke(Objects.requireNonNull(reducibleType), flatArgs.size()),
      reducible,
      flatArgs.view().prepended(onStuck).toImmutableSeq())
      : builder.invoke(Constants.REDUCIBLE_INVOKE, reducible, ImmutableSeq.of(
        onStuck,
        makeImmutableSeq(Term.class, flatArgs)
      ));

    return doElevate.apply(finalInvocation);
  }

  @Override protected @NotNull FreeJava doSerialize(@NotNull Term term) {
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
        getCallInstance(CallKind.from(call), call.ref());
      case ClassCall(var ref, var ulift, var args) -> {
        //   ExprializeUtils.makeNew(CLASS_CLSCALL,
        //   getInstance(NameSerializer.getClassRef(ref)),
        //   Integer.toString(ulift),
        //   serializeClosureToImmutableSeq(args)
        // );
        throw new UnsupportedOperationException("TODO");
      }
      case MemberCall(var of, var ref, var ulift, var args) -> builder.mkNew(MemberCall.class, ImmutableSeq.of(
        doSerialize(of),
        getInstance(ref),
        builder.iconst(ulift),
        serializeToImmutableSeq(Term.class, args)
      ));
      case AppTerm appTerm -> makeAppNew(AppTerm.class, appTerm.fun(), appTerm.arg());
      case LocalTerm _ when !allowLocalTerm -> throw new Panic("LocalTerm");
      case LocalTerm(var index) -> builder.mkNew(LocalTerm.class, ImmutableSeq.of(builder.iconst(index)));
      case LamTerm lamTerm -> builder.mkNew(LamTerm.class, ImmutableSeq.of(serializeClosure(lamTerm.body())));
      case DataCall(var ref, var ulift, var args) -> builder.mkNew(DataCall.class, ImmutableSeq.of(
        getInstance(ref),
        builder.iconst(ulift),
        serializeToImmutableSeq(Term.class, args)
      ));
      case ConCall(var head, var args) -> builder.mkNew(ConCall.class, ImmutableSeq.of(
        getInstance(head.ref()),
        serializeToImmutableSeq(Term.class, head.ownerArgs()),
        builder.iconst(head.ulift()),
        serializeToImmutableSeq(Term.class, args)
      ));
      case FnCall call -> {
        var refClass = switch (call.ref()) {
          case JitFn jit -> NameSerializer.getClassDesc(jit);
          case FnDef.Delegate def -> NameSerializer.getClassDesc(AnyDef.fromVar(def.ref));
        };

        var ref = getInstance(refClass);
        var args = call.args();
        yield buildReducibleCall(refClass, ref, FnCall.class, call.ulift(), ImmutableSeq.of(args), true);
      }
      case RuleReducer.Con conRuler -> buildReducibleCall(
        null,
        serializeApplicable(conRuler.rule()),
        RuleReducer.Con.class, conRuler.ulift(),
        ImmutableSeq.of(conRuler.ownerArgs(), conRuler.conArgs()),
        false
      );
      case RuleReducer.Fn fnRuler -> buildReducibleCall(
        null,
        serializeApplicable(fnRuler.rule()),
        RuleReducer.Fn.class, fnRuler.ulift(),
        ImmutableSeq.of(fnRuler.args()),
        false
      );
      // TODO: make the resolving const
      case SortTerm sort when sort.equals(SortTerm.Type0) ->
        builder.refField(builder.resolver().resolve(SortTerm.class, "Type0"));
      case SortTerm sort when sort.equals(SortTerm.ISet) ->
        builder.refField(builder.resolver().resolve(SortTerm.class, "ISet"));
      case SortTerm(var kind, var ulift) ->
        builder.mkNew(SortTerm.class, ImmutableSeq.of(builder.refEnum(kind), builder.iconst(ulift)));
      case DepTypeTerm(var kind, var param, var body) -> ExprializeUtils.makeNew(
        ExprializeUtils.getJavaRef(DepTypeTerm.class),
        ExprializeUtils.makeEnum(ExprializeUtils.getJavaRef(DTKind.class), kind),
        doSerialize(param),
        serializeClosure(body)
      );
      case CoeTerm(var type, var r, var s) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(CoeTerm.class),
        serializeClosure(type),
        doSerialize(r),
        doSerialize(s)
      );
      case ProjTerm(var of, var fst) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaRef(ProjTerm.class),
        doSerialize(of),
        Boolean.toString(fst)
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
        doSerialize(l), doSerialize(r)
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
      case MatchTerm(var discr, var clauses) -> ExprializeUtils.makeNew(CLASS_MATCHTERM,
        serializeToImmutableSeq(CLASS_TERM, discr),
        serializeMatching(clauses)
      );
      case NewTerm(var classCall) -> ExprializeUtils.makeNew(CLASS_NEW, doSerialize(classCall));
    };
  }

  private @NotNull String serializeMatching(@NotNull ImmutableSeq<Term.Matching> matchings) {
    var serializer = new PatternExprializer(this.nameGen, false);
    return makeImmutableSeq(CLASS_MATCHING,
      matchings.map(x -> {
        var pats = serializer.serializeToImmutableSeq(CLASS_PAT, x.patterns());
        var bindCount = Integer.toString(x.bindCount());
        var localTerms = ImmutableSeq.fill(x.bindCount(), LocalTerm::new);
        var tmpSerializer = new TermExprializer(nameGen, ImmutableSeq.empty(), true);
        var serLocalTerms = localTerms.map(tmpSerializer::serialize);
        var body = withMany(serLocalTerms, vars -> {
          // 0th term for index 0, so it is de bruijn index order instead of telescope order
          var freeBody = x.body().instantiateAll(SeqView.narrow(vars.view()));
          return doSerialize(freeBody);
        });

        return makeNew(CLASS_MATCHING, pats, bindCount, body);
      }));
  }

  // def f (A : Type) : Fn (a : A) -> A
  // (A : Type) : Pi(^0, IdxClosure(^1))
  // (A : Type) : Pi(^0, JitClosure(_ -> ^1))

  private @NotNull FreeJava withMany(
    @NotNull ImmutableSeq<FreeJava> subst,
    @NotNull Function<ImmutableSeq<FreeTerm>, FreeJava> continuation
  ) {

  }

  private @NotNull MethodData resolveInvoke(@NotNull ClassDesc owner, int argc) {
    return new MethodData.Default(
      owner, METHOD_INVOKE,
      Constants.CD_Term,
      SeqView.of(FreeUtils.fromClass(Supplier.class)).appendedAll(ImmutableSeq.fill(argc, Constants.CD_Term))
        .toImmutableSeq(), false
    );
  }

  // TODO: unify with makeClosure
  private @NotNull FreeJava makeThunk(@NotNull Function<TermExprializer, FreeJava> cont) {
    var binds = MutableLinkedHashMap.from(this.binds);
    var entries = binds.toImmutableSeq();
    return builder.mkLambda(entries.map(Tuple2::component2), Constants.THUNK, ap -> {
      var captured = entries.mapIndexed((i, tup) ->
        Tuple.of(tup.component1(), ap.capture(i)));

      return cont.apply(new TermExprializer(this.builder, MutableLinkedHashMap.from(captured), this.allowLocalTerm));
    });
  }

  private @NotNull FreeJava makeClosure(@NotNull BiFunction<TermExprializer, FreeJava, FreeJava> cont) {
    var binds = MutableLinkedHashMap.from(this.binds);
    var entries = binds.toImmutableSeq();
    return builder.mkLambda(entries.map(Tuple2::component2), Constants.CLOSURE, ap -> {
      var captured = entries.mapIndexed((i, tup) ->
        Tuple.of(tup.component1(), ap.capture(i)));

      return cont.apply(
        new TermExprializer(this.builder, MutableLinkedHashMap.from(captured), this.allowLocalTerm),
        ap.arg(0)
      );
    });
  }

  private @NotNull FreeJava with(
    @NotNull LocalVar var,
    @NotNull FreeJava subst,
    @NotNull Supplier<FreeJava> continuation
  ) {
    this.binds.put(var, subst);
    var result = continuation.get();
    this.binds.remove(var);
    return result;
  }

  private @NotNull FreeJava serializeClosureToImmutableSeq(@NotNull ImmutableSeq<Closure> cls) {
    return makeImmutableSeq(Closure.class, cls.map(this::serializeClosure));
  }

  private @NotNull FreeJava serializeClosure(@NotNull Closure body) {
    if (body instanceof Closure.Const(var inside)) return serializeConst(inside);

    var var = new LocalVar("<jit>");
    var appliedBody = body.apply(var);
    if (FindUsage.free(appliedBody, var) == 0) return serializeConst(appliedBody);

    var closure = makeClosure((te, arg) ->
      te.with(var, arg, () -> te.doSerialize(appliedBody)));

    return builder.mkNew(Closure.Jit.class, ImmutableSeq.of(closure));
  }

  private @NotNull FreeJava serializeConst(Term appliedBody) {
    return builder.invoke(Constants.CLOSURE_MKCONST, ImmutableSeq.of(doSerialize(appliedBody)));
  }

  @Override public @NotNull FreeJava serialize(Term unit) {
    binds.clear();
    var vars = ImmutableSeq.fill(instantiates.size(), i -> new LocalVar("arg" + i));
    unit = unit.instantiateTeleVar(vars.view());
    vars.forEachWith(instantiates, binds::put);

    return doSerialize(unit);
  }
}
