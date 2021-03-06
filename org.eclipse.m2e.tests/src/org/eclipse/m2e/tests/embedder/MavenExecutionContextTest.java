/*******************************************************************************
 * Copyright (c) 2013 Igor Fedorenko
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Igor Fedorenko - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.tests.embedder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Before;
import org.junit.Test;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.transfer.TransferListener;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.internal.embedder.MavenExecutionContext;
import org.eclipse.m2e.core.internal.embedder.MavenImpl;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;


public class MavenExecutionContextTest extends AbstractMavenProjectTestCase {

  MavenImpl maven;

  @Override
@Before
  public void setUp() throws Exception {
    super.setUp();
    maven = (MavenImpl) MavenPlugin.getMaven();
  }

  @Test
  public void testBasic() throws Exception {
    final MavenExecutionContext context = maven.createExecutionContext();
    context.execute((context1, monitor) -> {
      assertNotNull(context1.getLocalRepository());
      assertNotNull(context1.getRepositorySession());
      assertNotNull(context1.getSession());
      return null;
    }, monitor);
  }

  @Test
  public void testNested() throws Exception {
    final String outerProperty = "outer-property";
    final String nestedProperty = "nested-property";
    final MavenExecutionContext outer = maven.createExecutionContext();
    outer.getExecutionRequest().getUserProperties().put(outerProperty, "true");
    outer.execute((outerParam, monitor) -> {
      assertSame(outer, outerParam);
      assertTrue(outerParam.getSession().getUserProperties().containsKey(outerProperty));
      final MavenExecutionContext nested = maven.createExecutionContext();
      assertNotSame(outer, nested);
      assertNotSame(outer.getExecutionRequest(), nested.getExecutionRequest());
      nested.getExecutionRequest().getUserProperties().put(nestedProperty, "false");
      final MavenSession outerSession = outerParam.getSession();
      nested.execute((nestedParam, monitor1) -> {
        assertSame(nested, nestedParam);
        assertNotSame(outerParam, nestedParam);
        assertNotSame(outerSession, nestedParam.getSession());
        assertTrue(nestedParam.getSession().getUserProperties().containsKey(nestedProperty));
        return null;
      }, monitor);
      // check that nested context did not mess up outer context configuration 
      assertFalse(outerParam.getSession().getUserProperties().containsKey(nestedProperty));
      return null;
    }, monitor);
  }

  @Test
  public void testReenterShortcut() throws Exception {
    final MavenExecutionContext context = maven.createExecutionContext();
    context.execute((contextParam, monitor) -> {
      final MavenSession session1 = contextParam.getSession();
      final RepositorySystemSession repositorySession1 = contextParam.getRepositorySession();
      final TransferListener transferListener1 = ((IMavenExecutionContext) context).getRepositorySession()
          .getTransferListener();
      contextParam.execute((contextParam2, monitor1) -> {
        assertSame(session1, contextParam2.getSession());
        assertSame(repositorySession1, contextParam2.getRepositorySession());
        assertNotSame(transferListener1, contextParam2.getRepositorySession().getTransferListener());
        return null;
      }, monitor);
      // transfer listener is restored
      assertSame(transferListener1, contextParam.getRepositorySession().getTransferListener());
      return null;
    }, monitor);
  }

  @Test
  public void testIllegalState() throws Exception {
    final MavenExecutionContext context = maven.createExecutionContext();

    try {
      context.getLocalRepository();
      fail();
    } catch(IllegalStateException expected) {
    }

    try {
      context.getRepositorySession();
      fail();
    } catch(IllegalStateException expected) {
    }

    try {
      context.getSession();
      fail();
    } catch(IllegalStateException expected) {
    }

    try {
      context.execute((context1, monitor) -> {
        context1.getExecutionRequest().addActiveProfile("profile");
        return null;
      }, monitor);
      fail();
    } catch(IllegalStateException expected) {
    }
  }

  @Test
  public void testExecutionRequestContainsSystemProperties() throws Exception {
    maven.execute((context, monitor) -> {
      MavenExecutionRequest request = context.getExecutionRequest();
      assertNotNull(request);
      assertNotNull(request.getSystemProperties());
      assertNotNull(request.getSystemProperties().getProperty("java.version"));
      assertNotNull(request.getSystemProperties().getProperty("java.home"));
      assertNotNull(request.getSystemProperties().getProperty("maven.version"));
      assertNotNull(request.getSystemProperties().getProperty("maven.build.version"));
      return null;
    }, monitor);
  }

  @Test
  public void test496492_threadContextClassloaderLeak() throws Exception {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    maven.execute((context, monitor) -> {
      Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0]));
      return null;
    }, monitor);
    assertSame(tccl, Thread.currentThread().getContextClassLoader());
  }
}
