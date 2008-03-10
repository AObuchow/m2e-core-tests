/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.index;

import java.util.LinkedHashMap;


public class IndexedArtifactGroup {
  public final IndexInfo info;
  public final String prefix;
  public final LinkedHashMap nodes = new LinkedHashMap();
  public final LinkedHashMap files = new LinkedHashMap();

  public IndexedArtifactGroup(IndexInfo info, String prefix) {
    this.info = info;
    this.prefix = prefix;
  }
}
