package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.Term;

/**
 * A ref with an implementation, normally appears in <code>let</code> and stuffs.
 *
 * @author ice1000
 */
public record EvalVar(@NotNull String name, @NotNull Term term) implements Var {
}
