package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.sort.Sort;
import org.mzi.ref.LevelVar;
import org.mzi.util.Decision;

/**
 * @author ice1000
 */
public record UnivTerm(@NotNull Sort sort) implements Term {
  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitUniv(this, p);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  // TODO[JDK-8247334]: uncomment when we move to JDK16
  public static final /*@NotNull*/ UnivTerm PROP = new UnivTerm(new Sort(0, -1));
  public static final /*@NotNull*/ UnivTerm SET0 = new UnivTerm(Sort.hSet(new Sort.Level(0)));
  public static final /*@NotNull*/ UnivTerm STD = new UnivTerm(new Sort(new Sort.Level(LevelVar.UP), new Sort.Level(LevelVar.HP)));
}
