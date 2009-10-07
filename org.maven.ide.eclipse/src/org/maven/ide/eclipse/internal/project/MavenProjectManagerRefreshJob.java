/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.project;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.ui.progress.IProgressConstants;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.actions.OpenMavenConsoleAction;
import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.embedder.IMavenConfiguration;
import org.maven.ide.eclipse.embedder.MavenRuntimeManager;
import org.maven.ide.eclipse.jobs.IBackgroundProcessingQueue;
import org.maven.ide.eclipse.project.MavenUpdateRequest;

public class MavenProjectManagerRefreshJob extends Job implements IResourceChangeListener, IPreferenceChangeListener, IBackgroundProcessingQueue {

  private static final int DELTA_FLAGS = IResourceDelta.CONTENT | IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO
  | IResourceDelta.COPIED_FROM | IResourceDelta.REPLACED;
  
  private final List<MavenUpdateRequest> queue = new ArrayList<MavenUpdateRequest>();

  private final MavenProjectManagerImpl manager;
  
  private final IMavenConfiguration mavenConfiguration;

  private final MavenConsole console;

  public MavenProjectManagerRefreshJob(MavenProjectManagerImpl manager, //
      MavenRuntimeManager runtimeManager, MavenConsole console) {
    super("Updating Maven Dependencies");
    this.manager = manager;
    this.mavenConfiguration = MavenPlugin.lookup(IMavenConfiguration.class);
    this.console = console;
    setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
    setProperty(IProgressConstants.ACTION_PROPERTY, new OpenMavenConsoleAction());
  }

  public void refresh(MavenUpdateRequest updateRequest) {
    queue(updateRequest);
    schedule(1000L);
  }

  // Job
  
  public IStatus run(IProgressMonitor monitor) {
    monitor.beginTask("Refreshing Maven model", IProgressMonitor.UNKNOWN);
    ArrayList<MavenUpdateRequest> requests;
    synchronized(this.queue) {
      requests = new ArrayList<MavenUpdateRequest>(this.queue);
      this.queue.clear();
    }

    try {
      MutableProjectRegistry newState = manager.newMutableProjectRegistry();
      try {

        for (MavenUpdateRequest request : requests) {
          if(monitor.isCanceled()) {
            throw new OperationCanceledException();
          }
  
          manager.refresh(newState, request, monitor);
        }
  
        ISchedulingRule rule = ResourcesPlugin.getWorkspace().getRoot();
        getJobManager().beginRule(rule, monitor);
        try {
          manager.applyMutableProjectRegistry(newState, monitor);
        } finally {
          getJobManager().endRule(rule);
        }
      } finally {
        newState.close();
      }

    } catch(CoreException ex) {
      MavenLogger.log(ex);
      
    } catch(OperationCanceledException ex) {
      console.logMessage("Refreshing Maven model is canceled");

    } catch (StaleMutableProjectRegistryException e) {
      synchronized(this.queue) {
        this.queue.addAll(0, requests);
        if(!this.queue.isEmpty()) {
          schedule(1000L);
        }
      }
      
    } catch(Exception ex) {
      MavenLogger.log(ex.getMessage(), ex);
      
    } finally {
      monitor.done();
      
    }

    return Status.OK_STATUS;
  }

  // IResourceChangeListener
  
  public void resourceChanged(IResourceChangeEvent event) {
    boolean offline = mavenConfiguration.isOffline();  
    boolean updateSnapshots = false;

    int type = event.getType();

    if(IResourceChangeEvent.PRE_CLOSE == type || IResourceChangeEvent.PRE_DELETE == type) {
      queue(new MavenUpdateRequest((IProject) event.getResource(), //
          offline, updateSnapshots));

    } else {
      // if (IResourceChangeEvent.POST_CHANGE == type)
      IResourceDelta delta = event.getDelta(); // workspace delta
      IResourceDelta[] projectDeltas = delta.getAffectedChildren();
      Set<IProject> removeProjects = new LinkedHashSet<IProject>();
      Set<IProject> refreshProjects = new LinkedHashSet<IProject>();
      for(int i = 0; i < projectDeltas.length; i++ ) {
        try {
          projectChanged(projectDeltas[i], removeProjects, refreshProjects);
        } catch(CoreException ex) {
          MavenLogger.log(ex);
        }
      }
      
      // XXX consider to run refresh in offline mode when it is triggered by resource change
      if(!removeProjects.isEmpty()) {
        IProject[] projects = removeProjects.toArray(new IProject[removeProjects.size()]);
        MavenUpdateRequest updateRequest = new MavenUpdateRequest(projects, offline, updateSnapshots);
        updateRequest.setForce(false);
        queue(updateRequest);
        console.logMessage("Refreshing " + updateRequest.toString());
      }
      if(!refreshProjects.isEmpty()) {
        IProject[] projects = refreshProjects.toArray(new IProject[refreshProjects.size()]);
        MavenUpdateRequest updateRequest = new MavenUpdateRequest(projects, offline, updateSnapshots);
        updateRequest.setForce(false);
        queue(updateRequest);
        console.logMessage("Refreshing " + updateRequest.toString());
      }
    }

    synchronized(queue) {
      if(!queue.isEmpty()) {
        schedule(1000L);
      }
    }
  }

  private void projectChanged(IResourceDelta delta, Set<IProject> removeProjects, final Set<IProject> refreshProjects)
      throws CoreException {
    final IProject project = (IProject) delta.getResource();

    for(IPath path : MavenProjectManagerImpl.METADATA_PATH) {
      if (delta.findMember(path) != null) {
        removeProjects.add(project);
        return;
      }
    }

    delta.accept(new IResourceDeltaVisitor() {
      public boolean visit(IResourceDelta delta) {
        IResource resource = delta.getResource();
        if(resource instanceof IFile && IMavenConstants.POM_FILE_NAME.equals(resource.getName())) {
          // XXX ignore output folders
          if(delta.getKind() == IResourceDelta.REMOVED 
              || delta.getKind() == IResourceDelta.ADDED
              || (delta.getKind() == IResourceDelta.CHANGED && ((delta.getFlags() & DELTA_FLAGS) != 0))) 
          {
            // XXX check for interesting resources
            refreshProjects.add(project);
          }
        }
        return true;
      }
    });
  }

  private void queue(MavenUpdateRequest command) {
    synchronized(queue) {
      queue.add(command);
    }
  }

  public void preferenceChange(PreferenceChangeEvent event) {
    boolean offline = mavenConfiguration.isOffline();  
    boolean updateSnapshots = false;

    if (event.getSource() instanceof IProject) {
      queue(new MavenUpdateRequest(new IProject[] {(IProject) event.getSource()}, offline, updateSnapshots));
    }
  }

  public boolean isEmpty() {
    synchronized(queue) {
      return queue.isEmpty();
    }
  }

}
