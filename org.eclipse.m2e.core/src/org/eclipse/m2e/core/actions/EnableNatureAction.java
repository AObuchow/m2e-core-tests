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

package org.eclipse.m2e.core.actions;

import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.Messages;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.core.wizards.MavenPomWizard;


public class EnableNatureAction implements IObjectActionDelegate, IExecutableExtension {

  public static final String ID = "org.eclipse.m2e.enableNatureAction"; //$NON-NLS-1$

  static final String ID_WORKSPACE = "org.eclipse.m2e.enableWorkspaceResolutionAction"; //$NON-NLS-1$
  
  static final String ID_MODULES = "org.eclipse.m2e.enableModulesAction"; //$NON-NLS-1$
  
  private boolean workspaceProjects = true;
  
  private ISelection selection;
  
  public EnableNatureAction() {
  }
  
  public EnableNatureAction(String option) {
    setInitializationData(null, null, option);
  }

  public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
    if(IMavenConstants.NO_WORKSPACE_PROJECTS.equals(data)) {
      this.workspaceProjects = false;
    }
  }

  public void selectionChanged(IAction action, ISelection selection) {
    this.selection = selection;
  }

  public void setActivePart(IAction action, IWorkbenchPart targetPart) {
  }

  public void run(IAction action) {
    if(selection instanceof IStructuredSelection) {
      IStructuredSelection structuredSelection = (IStructuredSelection) selection;
      for(Iterator<?> it = structuredSelection.iterator(); it.hasNext();) {
        Object element = it.next();
        IProject project = null;
        if(element instanceof IProject) {
          project = (IProject) element;
        } else if(element instanceof IAdaptable) {
          project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
        }
        if(project != null) {
          enableNature(project, structuredSelection.size() == 1);
        }
      }
    }
  }

  private void enableNature(final IProject project, boolean isSingle) {
      final MavenPlugin plugin = MavenPlugin.getDefault();
      IFile pom = project.getFile(IMavenConstants.POM_FILE_NAME);
      if(isSingle && !pom.exists()) {
        // XXX move into AbstractProjectConfigurator and use Eclipse project settings
        IWorkbench workbench = plugin.getWorkbench();

        MavenPomWizard wizard = new MavenPomWizard();
        wizard.init(workbench, (IStructuredSelection) selection);

        Shell shell = workbench.getActiveWorkbenchWindow().getShell();
        WizardDialog wizardDialog = new WizardDialog(shell, wizard);
        wizardDialog.create();
        wizardDialog.getShell().setText(Messages.EnableNatureAction_wizard_shell);
        if(wizardDialog.open() == Window.CANCEL) {
          return;
        }
      }
      Job job = new Job(Messages.EnableNatureAction_job_enable) {
  
        protected IStatus run(IProgressMonitor monitor) {
          try {
            ResolverConfiguration configuration = new ResolverConfiguration();
            configuration.setResolveWorkspaceProjects(workspaceProjects);
            configuration.setActiveProfiles(""); //$NON-NLS-1$
  
            boolean hasMavenNature = project.hasNature(IMavenConstants.NATURE_ID);
  
            IProjectConfigurationManager configurationManager = plugin.getProjectConfigurationManager();
  
            configurationManager.enableMavenNature(project, configuration, new NullProgressMonitor());
  
            if(!hasMavenNature) {
              IMavenConfiguration mavenConfiguration = MavenPlugin.getDefault().getMavenConfiguration();
              configurationManager.updateProjectConfiguration(project, configuration, //
                  mavenConfiguration.getGoalOnUpdate(), new NullProgressMonitor());
            }
          } catch(CoreException ex) {
            MavenLogger.log(ex);
          }
          return Status.OK_STATUS;
        }
      };
      job.schedule();

  }

}
