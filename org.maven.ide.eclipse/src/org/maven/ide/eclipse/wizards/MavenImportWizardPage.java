/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.core.Messages;
import org.maven.ide.eclipse.embedder.MavenModelManager;
import org.maven.ide.eclipse.project.AbstractProjectScanner;
import org.maven.ide.eclipse.project.LocalProjectScanner;
import org.maven.ide.eclipse.project.MavenProjectInfo;
import org.maven.ide.eclipse.project.ProjectImportConfiguration;


/**
 * Maven Import Wizard Page
 * 
 * @author Eugene Kuleshov
 */
public class MavenImportWizardPage extends AbstractMavenWizardPage {

  static final Object[] EMPTY = new Object[0];

  protected Combo rootDirectoryCombo;

  protected CheckboxTreeViewer projectTreeViewer;

  private List<String> locations;

  private IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

  private WorkingSetGroup workingSetGroup;

  private boolean showLocation = true;

  protected MavenImportWizardPage(ProjectImportConfiguration importConfiguration) {
    super("MavenProjectImportWizardPage", importConfiguration);
    setTitle("Maven Projects");
    setDescription("Select Maven projects");
    setPageComplete(false);
  }

  public void setShowLocation(boolean showLocation) {
    this.showLocation = showLocation;
  }

  public void setLocations(List<String> locations) {
    this.locations = locations;
  }
  
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(3, false));
    setControl(composite);

    if(showLocation || locations==null || locations.isEmpty()) {
      final Label selectRootDirectoryLabel = new Label(composite, SWT.NONE);
      selectRootDirectoryLabel.setLayoutData(new GridData());
      selectRootDirectoryLabel.setText(Messages.getString("wizard.import.page.root"));

      rootDirectoryCombo = new Combo(composite, SWT.NONE);
      rootDirectoryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
      rootDirectoryCombo.addSelectionListener(new SelectionAdapter() {
        public void widgetDefaultSelected(SelectionEvent e) {
          if(rootDirectoryCombo.getText().trim().length() > 0) {
            scanProjects();
          }
        }
        
        public void widgetSelected(SelectionEvent e) {
          if(rootDirectoryCombo.getText().trim().length() > 0) {
            scanProjects();
          }
        }
      });
      rootDirectoryCombo.setFocus();
      addFieldWithHistory("rootDirectory", rootDirectoryCombo);
      
      if(locations!=null && locations.size()==1) {
        rootDirectoryCombo.setText(locations.get(0));
      }

      final Button browseButton = new Button(composite, SWT.NONE);
      browseButton.setText(Messages.getString("wizard.import.page.browse"));
      browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
      browseButton.addSelectionListener(new SelectionAdapter() {
        public void widgetSelected(SelectionEvent e) {
          DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.NONE);
          dialog.setText(Messages.getString("wizard.import.page.selectRootFolder"));
          String path = rootDirectoryCombo.getText();
          if(path.length()==0) {
            path = ResourcesPlugin.getWorkspace().getRoot().getLocation().toPortableString();
          }
          dialog.setFilterPath(path);

          String result = dialog.open();
          if(result != null) {
            rootDirectoryCombo.setText(result);
            scanProjects();
          }
        }
      });
    }

    final Label projectsLabel = new Label(composite, SWT.NONE);
    projectsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
    projectsLabel.setText(Messages.getString("wizard.import.page.projects"));

    projectTreeViewer = new CheckboxTreeViewer(composite, SWT.BORDER);

    projectTreeViewer.addCheckStateListener(new ICheckStateListener() {
      public void checkStateChanged(CheckStateChangedEvent event) {
        projectTreeViewer.setSubtreeChecked(event.getElement(), event.getChecked());
        updateCheckedState();
        Object[] checkedElements = projectTreeViewer.getCheckedElements();
        setPageComplete(checkedElements != null && checkedElements.length > 0);
      }
    });
    
    projectTreeViewer.addSelectionChangedListener(new ISelectionChangedListener() {

      public void selectionChanged(SelectionChangedEvent event) {
        IStructuredSelection selection = (IStructuredSelection) event.getSelection();
        validateProjectInfo((MavenProjectInfo) selection.getFirstElement());
      }});

    projectTreeViewer.setContentProvider(new ITreeContentProvider() {

      public Object[] getElements(Object element) {
        if(element instanceof List) {
          @SuppressWarnings("unchecked")
          List<MavenProjectInfo> projects = (List<MavenProjectInfo>) element;
          return projects.toArray(new MavenProjectInfo[projects.size()]);
        }
        return EMPTY;
      }

      public Object[] getChildren(Object parentElement) {
        if(parentElement instanceof List) {
          @SuppressWarnings("unchecked")
          List<MavenProjectInfo> projects = (List<MavenProjectInfo>) parentElement;
          return projects.toArray(new MavenProjectInfo[projects.size()]);
        } else if(parentElement instanceof MavenProjectInfo) {
          MavenProjectInfo mavenProjectInfo = (MavenProjectInfo) parentElement;
          Collection<MavenProjectInfo> projects = mavenProjectInfo.getProjects();
          return projects.toArray(new MavenProjectInfo[projects.size()]);
        }
        return EMPTY;
      }

      public Object getParent(Object element) {
        return null;
      }

      public boolean hasChildren(Object parentElement) {
        if(parentElement instanceof List) {
          List<?> projects = (List<?>) parentElement;
          return !projects.isEmpty();
        } else if(parentElement instanceof MavenProjectInfo) {
          MavenProjectInfo mavenProjectInfo = (MavenProjectInfo) parentElement;
          return !mavenProjectInfo.getProjects().isEmpty();
        }
        return false;
      }

      public void dispose() {
      }

      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }
    });

    projectTreeViewer.setLabelProvider(new ProjectLabelProvider());

    final Tree projectTree = projectTreeViewer.getTree();
    GridData projectTreeData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 3);
    projectTreeData.heightHint = 250;
    projectTreeData.widthHint = 500;
    projectTree.setLayoutData(projectTreeData);

    final Button selectAllButton = new Button(composite, SWT.NONE);
    selectAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    selectAllButton.setText(Messages.getString("wizard.import.page.selectAll"));
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        projectTreeViewer.expandAll();
        setAllChecked(true);
        // projectTreeViewer.setSubtreeChecked(projectTreeViewer.getInput(), true);
        validate();
      }
    });

    final Button deselectAllButton = new Button(composite, SWT.NONE);
    deselectAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    deselectAllButton.setText(Messages.getString("wizard.import.page.deselectAll"));
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        setAllChecked(false);
        // projectTreeViewer.setSubtreeChecked(projectTreeViewer.getInput(), false);
        setPageComplete(false);
      }
    });

    final Button refreshButton = new Button(composite, SWT.NONE);
    refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
    refreshButton.setText(Messages.getString("wizard.import.page.refresh"));
    refreshButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        scanProjects();
      }
    });

    this.workingSetGroup = new WorkingSetGroup(composite, getImportConfiguration(), getShell());
    
    createAdvancedSettings(composite, new GridData(SWT.FILL, SWT.TOP, false, false, 3, 1));
    resolverConfigurationComponent.template.addModifyListener(new ModifyListener(){
      public void modifyText(ModifyEvent arg0) {
        validate();
      }
    });
    
    if(locations!=null && !locations.isEmpty()) {
      scanProjects();
    }
  }

  public void dispose() {
    super.dispose();
    workingSetGroup.dispose();
  }
  
  protected void scanProjects() {
    final AbstractProjectScanner<MavenProjectInfo> projectScanner = getProjectScanner();
    try {
      getWizard().getContainer().run(true, true, new IRunnableWithProgress() {
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
          projectScanner.run(monitor);
        }
      });

      projectTreeViewer.setInput(projectScanner.getProjects());
      projectTreeViewer.expandAll();
      // projectTreeViewer.setAllChecked(true);
      setAllChecked(true);
      Object[] checkedElements = projectTreeViewer.getCheckedElements();
      setPageComplete(checkedElements != null && checkedElements.length > 0);
      setErrorMessage(null);
      setMessage(null);

      List<Throwable> errors = projectScanner.getErrors();
      if(!errors.isEmpty()) {
        StringBuffer sb = new StringBuffer(Messages.getString("wizard.import.page.scanningErrors", errors.size()));
        int n = 1;
        for(Throwable ex : errors) {
          if(ex instanceof CoreException) {
            String msg = ((CoreException) ex).getStatus().getMessage();
            sb.append("\n  ").append(n).append(" ").append(msg.trim());
            
          } else {
            String msg = ex.getMessage()==null ? ex.toString() : ex.getMessage();
            sb.append("\n  ").append(n).append(" ").append(msg.trim());
          }
          n++;
        }
        
        setMessage(sb.toString(), IMessageProvider.WARNING);
      }
      
    } catch(InterruptedException ex) {
      // canceled

    } catch(InvocationTargetException ex) {
      Throwable e = ex.getTargetException() == null ? ex : ex.getTargetException();
      String msg;
      if(e instanceof CoreException) {
        msg = e.getMessage();
        MavenLogger.log(msg, e);
      } else {
        msg = "Scanning error " + projectScanner.getDescription() + "; " + e.toString();
        MavenPlugin.getDefault().getConsole().logError(msg);
        MavenLogger.log(msg, e);
      }
      projectTreeViewer.setInput(null);
      setPageComplete(false);
      setErrorMessage(msg);

    }
  }

  void setAllChecked(boolean state) {
    @SuppressWarnings("unchecked")
    List<MavenProjectInfo> input = (List<MavenProjectInfo>) projectTreeViewer.getInput();
    if(input!=null) {
      for(MavenProjectInfo mavenProjectInfo : input) {
        projectTreeViewer.setSubtreeChecked(mavenProjectInfo, state);
      }
      updateCheckedState();
    }
  }
  
  void updateCheckedState() {
    Object[] elements = projectTreeViewer.getCheckedElements();
    for(int i = 0; i < elements.length; i++ ) {
      Object element = elements[i];
      if(element instanceof MavenProjectInfo) {
        MavenProjectInfo info = (MavenProjectInfo) element;
        if(isWorkspaceFolder(info) || isAlreadyExists(info)) {
          projectTreeViewer.setChecked(info, false);
        }
      }
    }
  }

  boolean isWorkspaceFolder(MavenProjectInfo info) {
    if(info!=null) {
      File pomFile = info.getPomFile();
      if(pomFile != null) {
        File parentFile = pomFile.getParentFile();
        if(parentFile.getAbsolutePath().equals(workspaceRoot.getLocation().toFile().getAbsolutePath())) {
          return true;
        }
      }
    }
    return false;
  }  
  
  boolean isAlreadyExists(MavenProjectInfo info) {
    if(info!=null) {
      IWorkspace workspace = ResourcesPlugin.getWorkspace();
      IProject project = getImportConfiguration().getProject(workspace.getRoot(), info.getModel());
      return project.exists();
    }
    return false;
  }

  protected AbstractProjectScanner<MavenProjectInfo> getProjectScanner() {
    File root = workspaceRoot.getLocation().toFile();
    MavenPlugin mavenPlugin = MavenPlugin.getDefault();
    MavenModelManager modelManager = mavenPlugin.getMavenModelManager();
    MavenConsole console = mavenPlugin.getConsole();
    if(showLocation || locations == null || locations.isEmpty()) {
      return new LocalProjectScanner(root, rootDirectoryCombo.getText(), false, modelManager, console);
    }
    return new LocalProjectScanner(root, locations, true, modelManager, console);
  }

  /**
   * @return collection of <code>MavenProjectInfo</code>
   */
  public Collection<MavenProjectInfo> getProjects() {
    Collection<MavenProjectInfo> checkedProjects = new ArrayList<MavenProjectInfo>();
    for(Object o : projectTreeViewer.getCheckedElements()) {
      checkedProjects.add((MavenProjectInfo) o);
    }

    return checkedProjects;
  }

  private void collectProjects(List<MavenProjectInfo> mavenProjects, Set<MavenProjectInfo> checkedProjects,
      Collection<MavenProjectInfo> childProjects) {
    for(MavenProjectInfo projectInfo : childProjects) {
      if(checkedProjects.contains(projectInfo)) {
        mavenProjects.add(projectInfo);
      } else {
        collectProjects(mavenProjects, checkedProjects, projectInfo.getProjects());
      }
    }
  }

  protected boolean validateProjectInfo(MavenProjectInfo info) {
    if(info!=null) {
      String projectName = getImportConfiguration().getProjectName(info.getModel());
      if(isWorkspaceFolder(info)) {
        setMessage(Messages.getString("wizard.import.validator.workspaceFolder", projectName), IMessageProvider.WARNING);
      } else if(isAlreadyExists(info)) {
        setMessage(Messages.getString("wizard.import.validator.projectExists", projectName), IMessageProvider.WARNING);
      } else {
        setMessage(null, IMessageProvider.WARNING);
        return false;
      }
    }
    return true;
  }

  protected void validate() {
    Object[] elements = projectTreeViewer.getCheckedElements();
    for(int i = 0; i < elements.length; i++ ) {
      Object element = elements[i];
      if(element instanceof MavenProjectInfo) {
        if (validateProjectInfo((MavenProjectInfo) element)) {
          setPageComplete(false);
          return;
        }
      }
    }

    setMessage(null);
    setPageComplete(projectTreeViewer.getCheckedElements().length > 0);
    projectTreeViewer.refresh();
  }
  
  /**
   * ProjectLabelProvider
   */
  class ProjectLabelProvider extends LabelProvider implements IColorProvider {

    public String getText(Object element) {
      if(element instanceof MavenProjectInfo) {
        MavenProjectInfo info = (MavenProjectInfo) element;
        
        if(info.getProfiles().isEmpty()) {
          return info.getLabel() + " - " + getId(info);
        }
        
        return info.getLabel() + " - " + getId(info) + "  " + info.getProfiles();
      }
      return super.getText(element);
    }

    private String getId(MavenProjectInfo info) {
      Model model = info.getModel();
      
      String groupId = model.getGroupId();
      String artifactId = model.getArtifactId();
      String version = model.getVersion();
      String packaging = model.getPackaging();

      Parent parent = model.getParent();

      if(groupId==null && parent!=null) {
        groupId = parent.getGroupId();
      }
      if(groupId==null) {
        groupId = "[inherited]";
      }
      
      if(version==null && parent!=null) {
        version = parent.getVersion();
      }
      if(version==null) {
        version = "[inherited]";
      }

      return groupId + ":" + artifactId + ":" + version + ":" + packaging;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IColorProvider#getBackground(java.lang.Object)
     */
    public Color getForeground(Object element) {
      if(element instanceof MavenProjectInfo) {
        MavenProjectInfo info = (MavenProjectInfo) element;
        if(isWorkspaceFolder(info)) {
          return Display.getDefault().getSystemColor(SWT.COLOR_RED);
        } else if(isAlreadyExists(info)) {
          return Display.getDefault().getSystemColor(SWT.COLOR_GRAY);
        }
      }
      return null;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.IColorProvider#getForeground(java.lang.Object)
     */
    public Color getBackground(Object element) {
      return null;
    }

  }

}
