/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wizards;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.core.Messages;
import org.maven.ide.eclipse.project.ProjectImportConfiguration;
import org.maven.ide.eclipse.ui.internal.util.SelectionUtil;


/**
 * Simple project wizard for creating a new Maven2 project.
 * <p>
 * The wizard provides the following functionality to the user:
 * <ul>
 * <li>Create the project in the workspace or at some external location.</li>
 * <li>Provide information about the Maven2 artifact to create.</li>
 * <li>Choose directories of the default Maven2 directory structure to create.</li>
 * <li>Choose a set of Maven2 dependencies for the project.</li>
 * </ul>
 * </p>
 * <p>
 * Once the wizard has finished, the following resources are created and configured:
 * <ul>
 * <li>A POM file containing the given artifact information and the chosen dependencies.</li>
 * <li>The chosen Maven2 directories.</li>
 * <li>The .classpath file is configured to hold appropriate entries for the Maven2 directories created as well as the
 * Java and Maven2 classpath containers.</li>
 * </ul>
 * </p>
 */
public class MavenProjectWizard extends Wizard implements INewWizard {
  /** The name of the default wizard page image. */
  private static final String DEFAULT_PAGE_IMAGE_NAME = "icons/new_m2_project_wizard.gif";

  /** The default wizard page image. */
  private static final ImageDescriptor DEFAULT_PAGE_IMAGE = MavenPlugin.getImageDescriptor(DEFAULT_PAGE_IMAGE_NAME);

  /** The wizard page for gathering general project information. */
  protected MavenProjectWizardLocationPage locationPage;

  /** The archetype selection page. */
  protected MavenProjectWizardArchetypePage archetypePage;

  /** The wizard page for gathering Maven2 project information. */
  protected MavenProjectWizardArtifactPage artifactPage;

  /** The wizard page for gathering archetype project information. */
  protected MavenProjectWizardArchetypeParametersPage parametersPage;

  /** The wizard page for choosing the Maven2 dependencies to use. */
  protected MavenDependenciesWizardPage dependenciesPage;

  ProjectImportConfiguration configuration;

  protected Button simpleProject;

  private IStructuredSelection selection;

  /**
   * Default constructor. Sets the title and image of the wizard.
   */
  public MavenProjectWizard() {
    super();
    setWindowTitle(Messages.getString("wizard.project.title"));
    setDefaultPageImageDescriptor(DEFAULT_PAGE_IMAGE);
    setNeedsProgressMonitor(true);
  }
  
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    this.selection = selection;
  }

  public void addPages() {
    configuration = new ProjectImportConfiguration();
    configuration.setWorkingSet(SelectionUtil.getSelectedWorkingSet(selection));
    
    locationPage = new MavenProjectWizardLocationPage(configuration, //
        Messages.getString("wizard.project.page.project.title"), //
        Messages.getString("wizard.project.page.project.description")) {
      
      protected void createAdditionalControls(Composite container) {
        simpleProject = new Button(container, SWT.CHECK);
        simpleProject.setText(Messages.getString("wizard.project.page.project.simpleProject"));
        simpleProject.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 3, 1));
        simpleProject.addSelectionListener(new SelectionAdapter() {
          public void widgetSelected(SelectionEvent e) {
            validate();
          }
        });
        
        Label label = new Label(container, SWT.NONE);
        GridData labelData = new GridData(SWT.FILL, SWT.TOP, false, false, 3, 1);
        labelData.heightHint = 10;
        label.setLayoutData(labelData);
      }
      
      /** Skips the archetype selection page if the user chooses a simple project. */
      public IWizardPage getNextPage() {
        return getPage(simpleProject.getSelection() ? "MavenProjectWizardArtifactPage" : "MavenProjectWizardArchetypePage");
      }
    };
    locationPage.setLocationPath(SelectionUtil.getSelectedLocation(selection));
    
    archetypePage = new MavenProjectWizardArchetypePage(configuration);
    parametersPage = new MavenProjectWizardArchetypeParametersPage(configuration);
    artifactPage = new MavenProjectWizardArtifactPage(configuration);
    dependenciesPage = new MavenDependenciesWizardPage(configuration, //
        Messages.getString("wizard.project.page.dependencies.title"), //
        Messages.getString("wizard.project.page.dependencies.description"));
    dependenciesPage.setDependencies(new Dependency[0]);
    dependenciesPage.setShowScope(true);

    addPage(locationPage);
    addPage(archetypePage);
    addPage(parametersPage);
    addPage(artifactPage);
    addPage(dependenciesPage);
  }

  /** Adds the listeners after the page controls are created. */
  public void createPageControls(Composite pageContainer) {
    super.createPageControls(pageContainer);

    simpleProject.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        boolean isSimpleproject = simpleProject.getSelection();
        archetypePage.setUsed(!isSimpleproject);
        parametersPage.setUsed(!isSimpleproject);
        artifactPage.setUsed(isSimpleproject);
        getContainer().updateButtons();
      }
    });
    
    archetypePage.addArchetypeSelectionListener(new ISelectionChangedListener() {
      public void selectionChanged(SelectionChangedEvent selectionchangedevent) {
        parametersPage.setArchetype(archetypePage.getArchetype());
        getContainer().updateButtons();
      }
    });

//    locationPage.addProjectNameListener(new ModifyListener() {
//      public void modifyText(ModifyEvent e) {
//        parametersPage.setProjectName(locationPage.getProjectName());
//        artifactPage.setProjectName(locationPage.getProjectName());
//      }
//    });
  }

  /** Returns the model. */
  public Model getModel() {
    if(simpleProject.getSelection()) {
      return artifactPage.getModel();
    }
    return parametersPage.getModel();
  }
  
  /**
   * To perform the actual project creation, an operation is created and run using this wizard as execution context.
   * That way, messages about the progress of the project creation are displayed inside the wizard.
   */
  public boolean performFinish() {
    // First of all, we extract all the information from the wizard pages.
    // Note that this should not be done inside the operation we will run
    // since many of the wizard pages' methods can only be invoked from within
    // the SWT event dispatcher thread. However, the operation spawns a new
    // separate thread to perform the actual work, i.e. accessing SWT elements
    // from within that thread would lead to an exception.

//    final IProject project = locationPage.getProjectHandle();
//    final String projectName = locationPage.getProjectName();

    // Get the location where to create the project. For some reason, when using
    // the default workspace location for a project, we have to pass null
    // instead of the actual location.
    final Model model = getModel();
    final String projectName = configuration.getProjectName(model);
    IStatus nameStatus = configuration.validateProjectName(model);
    if(!nameStatus.isOK()) {
      MessageDialog.openError(getShell(), Messages.getString("wizard.project.job.failed", projectName), nameStatus.getMessage());
      return false;
    }

    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    
    final IPath location = locationPage.isInWorkspace() ? null : locationPage.getLocationPath();
    final IWorkspaceRoot root = workspace.getRoot();
    final IProject project = configuration.getProject(root, model);
    
    boolean pomExists = ( locationPage.isInWorkspace() ?
        root.getLocation().append(project.getName()) : location ).append(IMavenConstants.POM_FILE_NAME).toFile().exists();
    if ( pomExists ) {
      MessageDialog.openError(getShell(), Messages.getString("wizard.project.job.failed", projectName), Messages.getString("wizard.project.error.pomAlreadyExists"));
      return false;
    }

    final Job job;
    
    final MavenPlugin plugin = MavenPlugin.getDefault();

    if(simpleProject.getSelection()) {
      @SuppressWarnings("unchecked")
      List<Dependency> modelDependencies = model.getDependencies();
      modelDependencies.addAll(Arrays.asList(dependenciesPage.getDependencies()));

      final String[] folders = artifactPage.getFolders();

      job = new WorkspaceJob(Messages.getString("wizard.project.job.creatingProject", projectName)) {
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          try {
            plugin.getProjectConfigurationManager().createSimpleProject(project, location, model, folders, //
                configuration, monitor);
            return Status.OK_STATUS;
          } catch(CoreException e) {
            return e.getStatus();
          } finally {
            monitor.done();
          }
        }
      };

    } else {
      final Archetype archetype = archetypePage.getArchetype();
      
      final String groupId = model.getGroupId();
      final String artifactId = model.getArtifactId();
      final String version = model.getVersion();
      final String javaPackage = parametersPage.getJavaPackage();
      final Properties properties = parametersPage.getProperties();
      
      job = new WorkspaceJob(Messages.getString("wizard.project.job.creating", archetype.getArtifactId())) {
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          try {
            plugin.getProjectConfigurationManager().createArchetypeProject(project, location, archetype, //
                groupId, artifactId, version, javaPackage, properties, configuration, monitor);
            return Status.OK_STATUS;
          } catch(CoreException e) {
            return e.getStatus();
          } finally {
            monitor.done();
          }
        }
      };
    }

    job.addJobChangeListener(new JobChangeAdapter() {
      public void done(IJobChangeEvent event) {
        final IStatus result = event.getResult();
        if(!result.isOK()) {
          Display.getDefault().asyncExec(new Runnable() {
            public void run() {
              MessageDialog.openError(getShell(), //
                  Messages.getString("wizard.project.job.failed", projectName), result.getMessage());
            }
          });
        }
      }
    });
    

    job.setRule(plugin.getProjectConfigurationManager().getRule());
    job.schedule();

//    ProjectListener listener = new ProjectListener();
//    workspace.addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE);
//    try {
//      job.setRule(plugin.getProjectConfigurationManager().getRule());
//      job.schedule();
//      
//      // MNGECLIPSE-766 wait until new project is created
//      while(listener.getNewProject() == null && (job.getState() & (Job.WAITING | Job.RUNNING)) > 0) {
//        try {
//          Thread.sleep(100L);
//        } catch (InterruptedException ex) {
//          // ignore
//        }
//      }
//      
//    } finally {
//      workspace.removeResourceChangeListener(listener);
//    }

    return true;
  }

  
//  static class ProjectListener implements IResourceChangeListener {
//    private IProject newProject = null;
//    
//    public void resourceChanged(IResourceChangeEvent event) {
//      IResourceDelta root = event.getDelta();
//      IResourceDelta[] projectDeltas = root.getAffectedChildren();
//      for (int i = 0; i < projectDeltas.length; i++) {              
//        IResourceDelta delta = projectDeltas[i];
//        IResource resource = delta.getResource();
//        if (delta.getKind() == IResourceDelta.ADDED) {
//          newProject = (IProject)resource;
//        }
//      }
//    }
//    /**
//     * Gets the newProject.
//     * @return Returns a IProject
//     */
//    public IProject getNewProject() {
//      return newProject;
//    }
//  }
  
}
