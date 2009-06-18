/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.project;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.apache.maven.execution.MavenExecutionRequest;

import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.internal.project.MavenProjectManagerImpl;
import org.maven.ide.eclipse.internal.project.MavenProjectManagerRefreshJob;


/**
 * This class keeps track of all Maven projects present in the workspace and provides mapping between Maven artifacts
 * and Workspace projects.
 */
public class MavenProjectManager {

  public static final String STATE_FILENAME = "workspacestate.properties";
  
  private final MavenProjectManagerImpl manager;

  private final MavenProjectManagerRefreshJob mavenBackgroundJob;

  private final File workspaceStateFile;
  
  public MavenProjectManager(MavenProjectManagerImpl manager, MavenProjectManagerRefreshJob mavenBackgroundJob, File stateLocation) {
    this.manager = manager;
    this.mavenBackgroundJob = mavenBackgroundJob;
    this.workspaceStateFile = new File(stateLocation, STATE_FILENAME);
  }

  // Maven projects    

  /**
   * Performs requested Maven project update asynchronously, using background
   * job. This method returns immediately.
   */
  public void refresh(MavenUpdateRequest request) {
    mavenBackgroundJob.refresh(request);
  }

  /**
   * Performs requested Maven project update synchronously. In other words, this method 
   * does not return until all affected projects have been updated and 
   * corresponding MavenProjectChangeEvent's broadcast.
   * 
   * This method acquires a lock on the workspace's root.
   */
  public void refresh(MavenUpdateRequest request, IProgressMonitor monitor) throws CoreException {
    manager.refresh(request, monitor);
  }

  public void addMavenProjectChangedListener(IMavenProjectChangedListener listener) {
    manager.addMavenProjectChangedListener(listener);
  }

  public void removeMavenProjectChangedListener(IMavenProjectChangedListener listener) {
    manager.removeMavenProjectChangedListener(listener);
  }

  /**
   * Returns MavenProjectFacade corresponding to the pom. This method first looks in the project cache, then attempts to
   * load the pom if the pom is not found in the cache. In the latter case, workspace resolution is assumed to be
   * enabled for the pom but the pom will not be added to the cache.
   */
  public IMavenProjectFacade create(IFile pom, boolean load, IProgressMonitor monitor) {
    return manager.create(pom, load, monitor);
  }

  public IMavenProjectFacade create(IProject project, IProgressMonitor monitor) {
    return manager.create(project, monitor);
  }

  public ResolverConfiguration getResolverConfiguration(IProject project) {
    IMavenProjectFacade projectFacade = create(project, new NullProgressMonitor());
    if(projectFacade!=null) {
      return projectFacade.getResolverConfiguration();
    }
    return manager.readResolverConfiguration(project);
  }

  public boolean setResolverConfiguration(IProject project, ResolverConfiguration configuration) {
    return manager.setResolverConfiguration(project, configuration);
  }
  
  /**
   * @return MavenProjectFacade[] all maven projects which exist under workspace root 
   */
  public IMavenProjectFacade[] getProjects() {
    return manager.getProjects();
  }

  public IMavenProjectFacade getMavenProject(String groupId, String artifactId, String version) {
    return manager.getMavenProject(groupId, artifactId, version);
  }
  
  public File getWorkspaceStateFile() {
    return workspaceStateFile;
  }

  /**
   * Request full maven build for a project.
   * 
   * This call only has effect for projects that have maven nature and
   * Maven builder configured. 
   * 
   * This call does not trigger the build. Instead next time Maven builder
   * processes the project it will use goals to execute during clean
   * build regardless of the build type requested.
   * 
   * The main purpose of this call is to allow coordination between multiple
   * builders configured for the same project. 
   */
  public void requestFullMavenBuild(IProject project) throws CoreException {
    project.setSessionProperty(IMavenConstants.FULL_MAVEN_BUILD, Boolean.TRUE);
  }

  /**
   * PROVISIONAL
   */
  public MavenExecutionRequest createExecutionRequest(IFile pom, ResolverConfiguration resolverConfiguration) throws CoreException {
    return manager.createExecutionRequest(pom, resolverConfiguration);
  }
}
