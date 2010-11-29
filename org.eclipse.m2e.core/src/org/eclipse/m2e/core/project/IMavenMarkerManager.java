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

package org.eclipse.m2e.core.project;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.apache.maven.execution.MavenExecutionResult;

/**
 * IMavenMarkerManager
 *
 * @author Fred Bricon
 */
public interface IMavenMarkerManager {

  /**
   * Add markers to a pom file from a MavenExecutionResult.
   * @param pomFile the pom file to attach markers to.
   * @param result containing messages to be addedd as markers
   */
  public void addMarkers(IResource pomFile, String type, MavenExecutionResult result);
  
  /**
   * Add a Maven marker to a resource
   * @param resource : the IResource to attach the marker to.
   * @param message : the marker's message.
   * @param lineNumber : the resource line to attach the marker to.
   * @param severity : the severity of the marker. 
   */
  public IMarker addMarker(IResource resource, String type, String message, int lineNumber, int severity);

  /**
   * Delete all Maven markers of the specified type from an IResource
   */
  public void deleteMarkers(IResource resource, String type) throws CoreException;
  
  /**
   * Transform an exception into an error marker on an IResource
   */
  public void addErrorMarkers(IResource resource, String type, Exception ex);

  public void addEditorHintMarkers(IResource pom, String type);
}
