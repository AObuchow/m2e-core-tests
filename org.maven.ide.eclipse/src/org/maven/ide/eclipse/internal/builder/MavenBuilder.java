/*******************************************************************************
 * Copyright (c) 2007, 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.builder;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import org.sonatype.plexus.build.incremental.ThreadBuildContext;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.builder.AbstractEclipseBuildContext;
import org.maven.ide.eclipse.builder.EclipseBuildContext;
import org.maven.ide.eclipse.builder.EclipseIncrementalBuildContext;
import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.embedder.IMaven;
import org.maven.ide.eclipse.embedder.IMavenConfiguration;
import org.maven.ide.eclipse.project.IMavenMarkerManager;
import org.maven.ide.eclipse.project.IMavenProjectFacade;
import org.maven.ide.eclipse.project.IProjectConfigurationManager;
import org.maven.ide.eclipse.project.MavenProjectManager;
import org.maven.ide.eclipse.project.MavenUpdateRequest;
import org.maven.ide.eclipse.project.configurator.AbstractBuildParticipant;
import org.maven.ide.eclipse.project.configurator.ILifecycleMapping;
import org.maven.ide.eclipse.util.M2EUtils;


public class MavenBuilder extends IncrementalProjectBuilder {

  public static boolean DEBUG = MavenPlugin.getDefault().isDebugging()
      & Boolean.parseBoolean(Platform.getDebugOption(IMavenConstants.PLUGIN_ID + "/debug/builder"));

  public static QualifiedName BUILD_CONTEXT_KEY = new QualifiedName(IMavenConstants.PLUGIN_ID, "BuildContext");

  static interface GetDeltaCallback {
    public IResourceDelta getDelta(IProject project);
  }

  private GetDeltaCallback getDeltaCallback = new GetDeltaCallback() {
    public IResourceDelta getDelta(IProject project) {
      return MavenBuilder.this.getDelta(project);
    }
  };

  /*
   * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
   *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
   */
  @SuppressWarnings("unchecked")
  protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {
    MavenPlugin plugin = MavenPlugin.getDefault();
    MavenConsole console = plugin.getConsole();
    MavenProjectManager projectManager = plugin.getMavenProjectManager();
    IProjectConfigurationManager configurationManager = plugin.getProjectConfigurationManager();
    IMavenConfiguration mavenConfiguration = MavenPlugin.lookup(IMavenConfiguration.class);
    IMavenMarkerManager markerManager = plugin.getMavenMarkerManager();
    deleteProjectMarkers();
    
    IProject project = getProject();
    if(project.hasNature(IMavenConstants.NATURE_ID)) {
      deleteProjectMarkers();
      IFile pomResource = project.getFile(IMavenConstants.POM_FILE_NAME);
      if(pomResource == null) {
        console.logError("Project " + project.getName() + " does not have pom.xml");
        return null;
      }

      IMavenProjectFacade projectFacade = projectManager.create(getProject(), monitor);
      if(projectFacade == null) {
        // XXX is this really possible? should we warn the user?
        return null;
      }

      if (projectFacade.isStale()) {
        MavenUpdateRequest updateRequest = new MavenUpdateRequest(project, mavenConfiguration.isOffline() /*offline*/, false /*updateSnapshots*/);
        projectManager.refresh(updateRequest, monitor);
        IMavenProjectFacade facade = projectManager.create(project, monitor);
        if(facade == null){
          // error marker should have been created
          return null;
        }
      }

      IResourceDelta delta = getDelta(project);
      AbstractEclipseBuildContext buildContext;
      Map<String, Object> contextState = (Map<String, Object>) project.getSessionProperty(BUILD_CONTEXT_KEY);
      if(contextState != null && (INCREMENTAL_BUILD == kind || AUTO_BUILD == kind)) {
        buildContext = new EclipseIncrementalBuildContext(delta, contextState);
      } else {
        // must be full build
        contextState = new HashMap<String, Object>();
        project.setSessionProperty(BUILD_CONTEXT_KEY, contextState);
        buildContext = new EclipseBuildContext(project, contextState);
      }

      Set<IProject> dependencies = new HashSet<IProject>();

      IMaven maven = MavenPlugin.lookup(IMaven.class);
      MavenExecutionRequest request = projectManager.createExecutionRequest(pomResource, projectFacade.getResolverConfiguration(), monitor);
      
      MavenProject mavenProject = null;
      try{
        mavenProject = projectFacade.getMavenProject(monitor);
      } catch(CoreException ce){
        //unable to read the project facade
        addErrorMarker(ce);
        monitor.done();
        return null;
      }
      MavenSession session = maven.createSession(request, mavenProject);
      ILifecycleMapping lifecycleMapping = configurationManager.getLifecycleMapping(projectFacade, monitor);

      ThreadBuildContext.setThreadBuildContext(buildContext);
      try {
        List<AbstractBuildParticipant> participants = lifecycleMapping.getBuildParticipants(projectFacade, monitor);
        for(InternalBuildParticipant participant : participants) {
          participant.setMavenProjectFacade(projectFacade);
          participant.setGetDeltaCallback(getDeltaCallback);
          participant.setSession(session);
          participant.setBuildContext(buildContext);
          try {
            if(FULL_BUILD == kind || delta != null || participant.callOnEmptyDelta()) {
              Set<IProject> sub = participant.build(kind, monitor);
              if(sub != null) {
                dependencies.addAll(sub);
              }
            }
          } catch(Exception e) {
            MavenLogger.log("Exception in build participant", e);
          } finally {
            participant.setMavenProjectFacade(null);
            participant.setGetDeltaCallback(null);
            participant.setSession(null);
            participant.setBuildContext(null);
          }
        }
      } catch (CoreException e) {
        addErrorMarker(e);
      } finally {
        ThreadBuildContext.setThreadBuildContext(null);
      }

      for(File file : buildContext.getFiles()) {
        IPath path = getProjectRelativePath(project, file);
        if(path == null) {
          continue; // odd
        }

        if(!file.exists()) {
          IResource resource = project.findMember(path);
          if (resource != null) {
            resource.refreshLocal(IResource.DEPTH_INFINITE, monitor);
          }
        } else if(file.isDirectory()) {
          IFolder ifolder = project.getFolder(path);
          ifolder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        } else {
          IFile ifile = project.getFile(path);
          ifile.refreshLocal(IResource.DEPTH_ZERO, monitor);
        }
      }

      MavenExecutionResult result = session.getResult();
      if (result.hasExceptions()) {
        markerManager.addMarkers(pomResource, result);
      }

      return !dependencies.isEmpty() ? dependencies.toArray(new IProject[dependencies.size()]) : null;
    }
    return null;
  }

  /**
   * Get rid of project markers
   */
  private void deleteProjectMarkers() throws CoreException{
    if(getProject() != null){
      getProject().deleteMarkers(IMavenConstants.MARKER_ID, true, IResource.DEPTH_ZERO);
    }
  }

  private void addErrorMarker(Exception e) throws CoreException {
    String msg = e.getMessage();
    String rootCause = M2EUtils.getRootCauseMessage(e);
    if(!e.equals(msg)){
      msg = msg+": "+rootCause;
    }

    IMarker[] findMarkers = getProject().findMarkers(IMavenConstants.MARKER_ID, true, IResource.DEPTH_INFINITE);
    if(findMarkers != null){
      for(int i=0;i<findMarkers.length;i++){
        String markerMsg = (String)findMarkers[i].getAttribute(IMarker.MESSAGE);
        if(markerMsg != null && markerMsg.equals(msg)){
          //get rid of old markers with the same message so we don't have a lot of duplicates,
          //and the marker is always up to date
          findMarkers[i].delete();
        }
      }
    }
    IMarker marker = getProject().createMarker(IMavenConstants.MARKER_ID);

    marker.setAttribute(IMarker.MESSAGE, msg);
    marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
    marker.setAttribute(IMarker.TRANSIENT, true);
  }

  public static IPath getProjectRelativePath(IProject project, File file) {
    if(project == null || file == null) {
      return null;
    }

    IPath projectPath = project.getLocation();
    if(projectPath == null) {
      return null;
    }

    IPath filePath = new Path(file.getAbsolutePath());
    if(!projectPath.isPrefixOf(filePath)) {
      return null;
    }

    return filePath.removeFirstSegments(projectPath.segmentCount());
  }

  protected void clean(IProgressMonitor monitor) throws CoreException{
    MavenPlugin plugin = MavenPlugin.getDefault();
    MavenProjectManager projectManager = plugin.getMavenProjectManager();
    IProjectConfigurationManager configurationManager = plugin.getProjectConfigurationManager();

    deleteProjectMarkers();
    IProject project = getProject();
    if(project.hasNature(IMavenConstants.NATURE_ID)) {
      IFile pomResource = project.getFile(IMavenConstants.POM_FILE_NAME);
      if(pomResource == null) {
        return;
      }

      IMavenProjectFacade projectFacade = projectManager.create(getProject(), monitor);
      if(projectFacade == null) {
        return;
      }

      IMaven maven = MavenPlugin.lookup(IMaven.class);
      
      // TODO flush relevant caches

      project.setSessionProperty(BUILD_CONTEXT_KEY, null); // clean context state
      Map<String, Object> contextState = new HashMap<String, Object>();
      EclipseBuildContext buildContext = new EclipseBuildContext(project, contextState);

      MavenExecutionRequest request = projectManager.createExecutionRequest(pomResource, projectFacade.getResolverConfiguration(), monitor);
      MavenSession session = null;
      try{
        session = maven.createSession(request, projectFacade.getMavenProject(monitor));
      } catch(CoreException ce){
        //the pom cannot be read. don't fill the log full of junk, just add an error marker
        addErrorMarker(ce);
        return;
      }
      ILifecycleMapping lifecycleMapping = configurationManager.getLifecycleMapping(projectFacade, monitor);
      
      ThreadBuildContext.setThreadBuildContext(buildContext);
      try {
        for (InternalBuildParticipant participant : lifecycleMapping.getBuildParticipants(projectFacade, monitor)) {
          participant.setMavenProjectFacade(projectFacade);
          participant.setGetDeltaCallback(getDeltaCallback);
          participant.setSession(session);
          try {
            participant.clean(monitor);
          } catch (Exception ex) {
            // TODO Auto-generated catch block
            MavenLogger.log("Totoally unexpected exception", ex);
          } finally {
            participant.setMavenProjectFacade(null);
            participant.setGetDeltaCallback(null);
            participant.setSession(null);
          }
        }
      } catch (CoreException e) {
        addErrorMarker(e);
      } finally {
        ThreadBuildContext.setThreadBuildContext(null);
      }
    }
  }
  
}
