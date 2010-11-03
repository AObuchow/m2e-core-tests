/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.internal.embedder;

import org.eclipse.osgi.util.NLS;

import org.codehaus.plexus.logging.Logger;

import org.eclipse.m2e.core.core.MavenConsole;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.Messages;

class EclipseLogger implements Logger {
  private MavenConsole console;
  private final IMavenConfiguration mavenConfiguration;

  public EclipseLogger(MavenConsole console, IMavenConfiguration mavenConfiguration) {
    this.console = console;
    this.mavenConfiguration = mavenConfiguration;
  }

  private void out(String s) {
    console.logMessage(s);
  }

  private void outError(String s) {
    console.logError(s);
  }
  
  public void debug( String msg ) {
    if (isDebugEnabled()) {
      out(NLS.bind(Messages.EclipseLogger_debug1,msg));
    }
  }

  public void debug( String msg, Throwable t) {
    if (isDebugEnabled()) {
      out( NLS.bind(Messages.EclipseLogger_debug2, msg, t.getMessage()));
    }
  }

  public void info( String msg ) {
    if (isInfoEnabled()) {
      out( NLS.bind(Messages.EclipseLogger_info1, msg));
    }
  }

  public void info( String msg, Throwable t ) {
    if (isInfoEnabled()) {
      out( NLS.bind(Messages.EclipseLogger_info2, msg, t.getMessage()));
    }
  }

  public void warn( String msg ) {
    if (isWarnEnabled()) {
      out(NLS.bind(Messages.EclipseLogger_warn1, msg));
    }
  }
  
  public void warn( String msg, Throwable t ) {
    if (isWarnEnabled()) {
      out( NLS.bind(Messages.EclipseLogger_warn2, msg, t.getMessage()));
    }
  }
  
  public void fatalError( String msg ) {
    if (isFatalErrorEnabled()) {
      outError(NLS.bind(Messages.EclipseLogger_fatal1, msg));
    }
  }
  
  public void fatalError( String msg, Throwable t ) {
    if (isFatalErrorEnabled()) {
      outError( NLS.bind(Messages.EclipseLogger_fatal2, msg, t.getMessage()));
    }
  }
  
  public void error( String msg ) {
    if (isErrorEnabled()) {
      outError(NLS.bind(Messages.EclipseLogger_error1, msg));
    }
  }
  
  public void error( String msg, Throwable t ) {
    if (isErrorEnabled()) {
      outError( NLS.bind(Messages.EclipseLogger_error2, msg, t.getMessage()));
    }
  }
  
  public boolean isDebugEnabled() {
    return mavenConfiguration.isDebugOutput();
  }
  
  public boolean isInfoEnabled() {
    return true;
  }

  public boolean isWarnEnabled() {
    return true;
  }

  public boolean isErrorEnabled() {
    return true;
  }

  public boolean isFatalErrorEnabled() {
    return true;
  }

  public void setThreshold( int treshold ) {
  }

  public int getThreshold() {
    return LEVEL_DEBUG;
  }

  public Logger getChildLogger(String name) {
    return this;
  }

  public String getName() {
    return Messages.EclipseLogger_name;
  }

}

