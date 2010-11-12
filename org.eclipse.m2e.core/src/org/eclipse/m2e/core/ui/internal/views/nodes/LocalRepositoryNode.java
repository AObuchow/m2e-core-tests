/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.core.ui.internal.views.nodes;

import org.eclipse.m2e.core.internal.index.NexusIndex;
import org.eclipse.m2e.core.repository.IRepository;

/**
 * LocalRepositoryNode
 *
 * @author igor
 */
public class LocalRepositoryNode extends AbstractIndexedRepositoryNode {

  public LocalRepositoryNode(NexusIndex index) {
    super(index);
  }

  public String getName() {
    IRepository repository = index.getRepository();
    StringBuilder sb = new StringBuilder();
    sb.append("Local Repository");
    if (repository.getBasedir() != null) {
      sb.append(" (").append(repository.getBasedir().getAbsolutePath()).append(')');
    }
    return sb.toString();
  }
}
