/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.embedder;

/**
 * Allows extensions to contribute components to the Maven core container.
 */
public interface IMavenComponentContributor {

  void contribute(IMavenComponentBinder binder);

  public interface IMavenComponentBinder {

    <T> void bind(Class<T> role, Class<? extends T> impl, String hint);

  }

}
