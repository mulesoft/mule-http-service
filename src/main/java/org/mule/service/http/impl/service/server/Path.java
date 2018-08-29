/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a URI path, which can be a parent to regular sub paths, a catch all sub path (/*) and a URI param path (/{param}).
 * Request handler and matcher pairs (handlers for different methods, for example) can be added and removed from it. If all
 * its handlers and children's handlers' are removed, it will notify its parent the path itself can be removed. This means that
 * the tree-like structure that results from binding paths together changes as handlers are created and disposed so special care
 * needs to be taken regarding available paths.
 */
public class Path<T extends Path> {

  protected String name;
  protected Path parent;
  protected Map<String, T> subPaths = new HashMap<>();

  /**
   * Creates a new instance for the given name and with the provided parent.
   *
   * @param name for this path
   * @param parent of this path
   */
  public Path(String name, Path parent) {
    this.name = name;
    this.parent = parent;
  }

  /**
   * Removes a sub path and checks whether this path itself should be removed.
   *
   * @param pathName the path name
   */
  public void removeChildPath(final String pathName) {
    subPaths.remove(pathName);
  }

  /**
   * @return the map of sub paths available
   */
  public Map<String, T> getSubPaths() {
    return subPaths;
  }
}
