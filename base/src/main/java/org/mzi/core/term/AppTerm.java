package org.mzi.core.term;

import java.util.List;

/**
 * @author ice1000
 */
public interface AppTerm extends Term {
  List<Arg> arguments();
}
