/*******************************************************************************
 * Copyright (c) 2007, 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.actions;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenProjectManager;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.core.project.ResolverConfiguration;


public class ChangeNatureAction implements IObjectActionDelegate {

  public static final String ID_ENABLE_WORKSPACE = "org.eclipse.m2e.enableWorkspaceResolutionAction";
  
  public static final String ID_DISABLE_WORKSPACE = "org.eclipse.m2e.disableWorkspaceResolutionAction";

  public static final int ENABLE_WORKSPACE = 1;

  public static final int DISABLE_WORKSPACE = 2;
  
  private ISelection selection;
  
  private int option;
  
  public ChangeNatureAction(int option) {
    this.option = option;
  }

  public void selectionChanged(IAction action, ISelection selection) {
    this.selection = selection;
  }

  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
  }

  public void run(IAction action) {
    if(selection instanceof IStructuredSelection) {
      IStructuredSelection structuredSelection = (IStructuredSelection) selection;
      Set<IProject> projects = new LinkedHashSet<IProject>();
      for(Iterator<?> it = structuredSelection.iterator(); it.hasNext();) {
        Object element = it.next();
        IProject project = null;
        if(element instanceof IProject) {
          project = (IProject) element;
        } else if(element instanceof IAdaptable) {
          project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
        }
        if(project != null) {
          projects.add(project);
        }
      }

      new UpdateJob(projects, option).schedule();
    }
  }

  static class UpdateJob extends WorkspaceJob {
    private final Set<IProject> projects;
    private final int option;

    private final IProjectConfigurationManager importManager;
    private final MavenProjectManager projectManager;
    private final IMavenConfiguration mavenConfiguration;

    public UpdateJob(Set<IProject> projects, int option) {
      super("Changing nature");
      this.projects = projects;
      this.option = option;

      MavenPlugin plugin = MavenPlugin.getDefault();
      this.importManager = plugin.getProjectConfigurationManager();
      this.projectManager = plugin.getMavenProjectManager();
      
      this.mavenConfiguration = MavenPlugin.getDefault().getMavenConfiguration();
    }
    
    public IStatus runInWorkspace(IProgressMonitor monitor) {
      MultiStatus status = null;
      for(IProject project : projects) {
        if (monitor.isCanceled()) {
          throw new OperationCanceledException();
        }

        monitor.subTask(project.getName());

        try {
          changeNature(project, monitor);
        } catch (CoreException ex) {
          if (status == null) {
            status = new MultiStatus(IMavenConstants.PLUGIN_ID, IStatus.ERROR, "Can't change nature", null);
          }
          status.add(ex.getStatus());
        }
      }

      boolean offline = mavenConfiguration.isOffline();
      boolean updateSnapshots = false;
      projectManager.refresh(new MavenUpdateRequest(projects.toArray(new IProject[projects.size()]), //
          offline, updateSnapshots));
      
      return status != null? status: Status.OK_STATUS;
    }

    private void changeNature(final IProject project, IProgressMonitor monitor) throws CoreException {
      MavenPlugin plugin = MavenPlugin.getDefault();
      MavenProjectManager projectManager = plugin.getMavenProjectManager();
      
      final ResolverConfiguration configuration = projectManager.getResolverConfiguration(project);

      boolean updateSourceFolders = false;

      switch(option) {
        case ENABLE_WORKSPACE:
          configuration.setResolveWorkspaceProjects(true);
          break;
        case DISABLE_WORKSPACE:
          configuration.setResolveWorkspaceProjects(false);
          break;
      }

      projectManager.setResolverConfiguration(project, configuration);

      if (updateSourceFolders) {
        importManager.updateProjectConfiguration(project, //
            configuration, mavenConfiguration.getGoalOnUpdate(), monitor);
      }
    }
  }

}
