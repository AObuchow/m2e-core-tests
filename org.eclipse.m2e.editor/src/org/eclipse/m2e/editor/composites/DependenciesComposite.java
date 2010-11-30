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

package org.eclipse.m2e.editor.composites;

import static org.eclipse.m2e.editor.pom.FormUtils.nvl;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.RemoveCommand;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.index.IIndex;
import org.eclipse.m2e.core.index.IndexedArtifactFile;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectManager;
import org.eclipse.m2e.core.ui.dialogs.AddDependencyDialog;
import org.eclipse.m2e.core.ui.dialogs.EditDependencyDialog;
import org.eclipse.m2e.core.ui.dialogs.MavenRepositorySearchDialog;
import org.eclipse.m2e.editor.MavenEditorImages;
import org.eclipse.m2e.editor.MavenEditorPlugin;
import org.eclipse.m2e.editor.dialogs.ManageDependenciesDialog;
import org.eclipse.m2e.editor.internal.Messages;
import org.eclipse.m2e.editor.pom.MavenPomEditor;
import org.eclipse.m2e.editor.pom.MavenPomEditor.Callback;
import org.eclipse.m2e.editor.pom.MavenPomEditorPage;
import org.eclipse.m2e.editor.pom.SearchControl;
import org.eclipse.m2e.editor.pom.SearchMatcher;
import org.eclipse.m2e.editor.pom.ValueProvider;
import org.eclipse.m2e.model.edit.pom.Dependency;
import org.eclipse.m2e.model.edit.pom.DependencyManagement;
import org.eclipse.m2e.model.edit.pom.Model;
import org.eclipse.m2e.model.edit.pom.PomFactory;
import org.eclipse.m2e.model.edit.pom.PomPackage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.sonatype.aether.graph.DependencyNode;


/**
 * @author Eugene Kuleshov
 */
public class DependenciesComposite extends Composite {

  protected static PomPackage POM_PACKAGE = PomPackage.eINSTANCE;

  protected MavenPomEditorPage editorPage;

  MavenPomEditor pomEditor;

  private FormToolkit toolkit = new FormToolkit(Display.getCurrent());

  // controls

  PropertiesListComposite<Dependency> dependencyManagementEditor;

  DependenciesListComposite<Dependency> dependenciesEditor;

  Button dependencySelectButton;

  Action dependencySelectAction;

  SearchControl searchControl;

  SearchMatcher searchMatcher;

  DependencyFilter searchFilter;

  Action openWebPageAction;

  // model

  Model model;

  MavenProject mavenProject;

  ValueProvider<DependencyManagement> dependencyManagementProvider;

  DependencyLabelProvider dependencyLabelProvider = new DependencyLabelProvider();

  DependencyLabelProvider dependencyManagementLabelProvider = new DependencyLabelProvider();

  public DependenciesComposite(Composite composite, MavenPomEditorPage editorPage, int flags, MavenPomEditor pomEditor) {
    super(composite, flags);
    this.editorPage = editorPage;
    this.pomEditor = pomEditor;
    createComposite();
    editorPage.initPopupMenu(dependenciesEditor.getViewer(), ".dependencies"); //$NON-NLS-1$
    editorPage.initPopupMenu(dependencyManagementEditor.getViewer(), ".dependencyManagement"); //$NON-NLS-1$
  }

  private void createComposite() {
    GridLayout gridLayout = new GridLayout();
    gridLayout.makeColumnsEqualWidth = true;
    gridLayout.marginWidth = 0;
    setLayout(gridLayout);
    toolkit.adapt(this);

    SashForm horizontalSash = new SashForm(this, SWT.NONE);
    GridData horizontalCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    horizontalCompositeGridData.heightHint = 200;
    horizontalSash.setLayoutData(horizontalCompositeGridData);
    toolkit.adapt(horizontalSash, true, true);

    createDependenciesSection(horizontalSash);
    createDependencyManagementSection(horizontalSash);

    horizontalSash.setWeights(new int[] {1, 1});
  }

  private void createDependenciesSection(SashForm verticalSash) {
    Section dependenciesSection = toolkit.createSection(verticalSash, ExpandableComposite.TITLE_BAR);
    dependenciesSection.marginWidth = 3;
    dependenciesSection.setText(Messages.DependenciesComposite_sectionDependencies);

    dependenciesEditor = new DependenciesListComposite<Dependency>(dependenciesSection, SWT.NONE, true);
    dependenciesEditor.setLabelProvider(dependencyLabelProvider);
    dependenciesEditor.setContentProvider(new ListEditorContentProvider<Dependency>());

    dependenciesEditor.setRemoveButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        CompoundCommand compoundCommand = new CompoundCommand();
        EditingDomain editingDomain = editorPage.getEditingDomain();

        List<Dependency> dependencyList = dependenciesEditor.getSelection();
        for(Dependency dependency : dependencyList) {
          Command removeCommand = RemoveCommand.create(editingDomain, model, POM_PACKAGE.getModel_Dependencies(),
              dependency);
          compoundCommand.append(removeCommand);
        }

        editingDomain.getCommandStack().execute(compoundCommand);
      }
    });

    dependenciesEditor.setPropertiesListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        Dependency dependency = dependenciesEditor.getSelection().get(0);
        EditDependencyDialog d = new EditDependencyDialog(getShell(), false, editorPage.getEditingDomain(), editorPage
            .getProject());
        d.setDependency(dependency);
        if(d.open() == Window.OK) {
          dependenciesEditor.setInput(model.getDependencies());
          dependenciesEditor.setSelection(Collections.singletonList(dependency));
        }
      }
    });

    dependenciesEditor.addSelectionListener(new ISelectionChangedListener() {
      public void selectionChanged(SelectionChangedEvent event) {
        List<Dependency> selection = dependenciesEditor.getSelection();

        if(!selection.isEmpty()) {
          dependencyManagementEditor.setSelection(Collections.<Dependency> emptyList());
        }
      }
    });

    dependenciesSection.setClient(dependenciesEditor);
    toolkit.adapt(dependenciesEditor);
    toolkit.paintBordersFor(dependenciesEditor);

    dependenciesEditor.setManageButtonListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        try {
          openManageDependenciesDialog();
        } catch(InvocationTargetException e1) {
          MavenEditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, "Error: ", e1)); //$NON-NLS-1$
        } catch(InterruptedException e1) {
          MavenEditorPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, MavenEditorPlugin.PLUGIN_ID, "Error: ", e1)); //$NON-NLS-1$
        }
      }
    });
    
    dependenciesEditor.setAddButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final AddDependencyDialog addDepDialog = new AddDependencyDialog(getShell(), false, editorPage.getProject());

        /*
         * Load the dependency tree for the dialog so it can show already
         * added transitive dependencies.
         */
        Runnable runnable = new Runnable() {

          public void run() {
            pomEditor.loadDependencies(new Callback() {

              public void onFinish(DependencyNode node) {
                addDepDialog.setDepdencyNode(node);
              }

              public void onException(CoreException ex) {
                MavenLogger.log(ex);
              }
            }, Artifact.SCOPE_TEST);
          }
        };

        addDepDialog.onLoad(runnable);

        if(addDepDialog.open() == Window.OK) {
          List<Dependency> deps = addDepDialog.getDependencies();
          for(Dependency dep : deps) {
            setupDependency(new ValueProvider<Model>() {
              @Override
              public Model getValue() {
                return model;
              }
            }, POM_PACKAGE.getModel_Dependencies(), dep);
          }
          dependenciesEditor.setInput(model.getDependencies());
          dependenciesEditor.setSelection(Collections.singletonList(deps.get(0)));
        }
      }

    });

    ToolBarManager modulesToolBarManager = new ToolBarManager(SWT.FLAT);
    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_showgroupid,
        MavenEditorImages.SHOW_GROUP) {
      {
        setChecked(false);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        dependencyLabelProvider.setShowGroupId(isChecked());
        dependenciesEditor.getViewer().refresh();
      }
    });

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_filter, MavenEditorImages.FILTER) {
      {
        setChecked(true);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        TableViewer viewer = dependenciesEditor.getViewer();
        if(isChecked()) {
          viewer.addFilter(searchFilter);
        } else {
          viewer.removeFilter(searchFilter);
        }
        viewer.refresh();
        if(isChecked()) {
          searchControl.getSearchText().setFocus();
        }
      }
    });

    Composite toolbarComposite = toolkit.createComposite(dependenciesSection);
    GridLayout toolbarLayout = new GridLayout(1, true);
    toolbarLayout.marginHeight = 0;
    toolbarLayout.marginWidth = 0;
    toolbarComposite.setLayout(toolbarLayout);
    toolbarComposite.setBackground(null);

    modulesToolBarManager.createControl(toolbarComposite);
    dependenciesSection.setTextClient(toolbarComposite);
  }

  private void createDependencyManagementSection(SashForm verticalSash) {
    Section dependencyManagementSection = toolkit.createSection(verticalSash, ExpandableComposite.TITLE_BAR);
    dependencyManagementSection.marginWidth = 3;
    dependencyManagementSection.setText(Messages.DependenciesComposite_sectionDependencyManagement);

    dependencyManagementEditor = new PropertiesListComposite<Dependency>(dependencyManagementSection, SWT.NONE, true);
    dependencyManagementSection.setClient(dependencyManagementEditor);

    dependencyManagementEditor.setLabelProvider(dependencyManagementLabelProvider);
    dependencyManagementEditor.setContentProvider(new ListEditorContentProvider<Dependency>());

    dependencyManagementEditor.setRemoveButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        CompoundCommand compoundCommand = new CompoundCommand();
        EditingDomain editingDomain = editorPage.getEditingDomain();

        List<Dependency> dependencyList = dependencyManagementEditor.getSelection();
        for(Dependency dependency : dependencyList) {
          Command removeCommand = RemoveCommand.create(editingDomain, //
              dependencyManagementProvider.getValue(), POM_PACKAGE.getDependencyManagement_Dependencies(), dependency);
          compoundCommand.append(removeCommand);
        }

        editingDomain.getCommandStack().execute(compoundCommand);
      }
    });
    
    dependencyManagementEditor.setPropertiesListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        Dependency dependency = dependencyManagementEditor.getSelection().get(0);
        EditDependencyDialog d = new EditDependencyDialog(getShell(), true, editorPage.getEditingDomain(), editorPage
            .getProject());
        d.setDependency(dependency);
        if(d.open() == Window.OK) {
          dependencyManagementEditor.setInput(dependencyManagementProvider.getValue().getDependencies());
          dependencyManagementEditor.setSelection(Collections.singletonList(dependency));
        }
      }
    });

    dependencyManagementEditor.addSelectionListener(new ISelectionChangedListener() {
      public void selectionChanged(SelectionChangedEvent event) {
        List<Dependency> selection = dependencyManagementEditor.getSelection();

        if(!selection.isEmpty()) {
          dependenciesEditor.setSelection(Collections.<Dependency> emptyList());
        }
      }
    });

    toolkit.adapt(dependencyManagementEditor);
    toolkit.paintBordersFor(dependencyManagementEditor);

    dependencyManagementEditor.setAddButtonListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        // TODO calculate current list of artifacts for the project
        Set<ArtifactKey> artifacts = Collections.emptySet();
        MavenRepositorySearchDialog dialog = new MavenRepositorySearchDialog(getShell(), //
            Messages.DependenciesComposite_searchDialog_title, IIndex.SEARCH_ARTIFACT, artifacts, true);
        if(dialog.open() == Window.OK) {
          IndexedArtifactFile af = (IndexedArtifactFile) dialog.getFirstResult();
          if(af != null) {
            Dependency dependency = createDependency(dependencyManagementProvider,
                POM_PACKAGE.getDependencyManagement_Dependencies(), //
                af.group, af.artifact, af.version, af.classifier, "jar".equals(nvl(af.type)) ? "" : nvl(af.type), //$NON-NLS-1$ //$NON-NLS-2$
                "compile".equals(nvl(dialog.getSelectedScope())) ? "" : nvl(dialog.getSelectedScope()));//$NON-NLS-1$ //$NON-NLS-2$
            dependencyManagementEditor.setInput(dependencyManagementProvider.getValue().getDependencies());
            dependencyManagementEditor.setSelection(Collections.singletonList(dependency));
          }
        }
      }
    });

    ToolBarManager modulesToolBarManager = new ToolBarManager(SWT.FLAT);

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_showgroupid,
        MavenEditorImages.SHOW_GROUP) {
      {
        setChecked(false);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        dependencyManagementLabelProvider.setShowGroupId(isChecked());
        dependencyManagementEditor.getViewer().refresh();
      }
    });

    modulesToolBarManager.add(new Action(Messages.DependenciesComposite_action_filter, MavenEditorImages.FILTER) {
      {
        setChecked(true);
      }

      public int getStyle() {
        return AS_CHECK_BOX;
      }

      public void run() {
        TableViewer viewer = dependencyManagementEditor.getViewer();
        if(isChecked()) {
          viewer.addFilter(searchFilter);
        } else {
          viewer.removeFilter(searchFilter);
        }
        viewer.refresh();
        if(isChecked()) {
          searchControl.getSearchText().setFocus();
        }
      }
    });

    Composite toolbarComposite = toolkit.createComposite(dependencyManagementSection);
    GridLayout toolbarLayout = new GridLayout(1, true);
    toolbarLayout.marginHeight = 0;
    toolbarLayout.marginWidth = 0;
    toolbarComposite.setLayout(toolbarLayout);
    toolbarComposite.setBackground(null);

    modulesToolBarManager.createControl(toolbarComposite);
    dependencyManagementSection.setTextClient(toolbarComposite);
  }


  public void loadData(Model model, ValueProvider<DependencyManagement> dependencyManagementProvider) {
    this.model = model;
    this.dependencyManagementProvider = dependencyManagementProvider;
    this.dependencyLabelProvider.setPomEditor(editorPage.getPomEditor());
    this.dependencyManagementLabelProvider.setPomEditor(editorPage.getPomEditor());

    dependenciesEditor.setInput(model.getDependencies());

    DependencyManagement dependencyManagement = dependencyManagementProvider.getValue();
    dependencyManagementEditor.setInput(dependencyManagement == null ? null : dependencyManagement.getDependencies());

    dependenciesEditor.setReadOnly(editorPage.isReadOnly());
    dependencyManagementEditor.setReadOnly(editorPage.isReadOnly());

    if(searchControl != null) {
      searchControl.getSearchText().setEditable(true);
    }
  }

  public void updateView(final MavenPomEditorPage editorPage, final Notification notification) {
    Display.getDefault().asyncExec(new Runnable() {
      public void run() {
        EObject object = (EObject) notification.getNotifier();

        // XXX event is not received when <dependencies> is deleted in XML
        if(object instanceof Model) {
          Model model2 = (Model) object;

          if(model2.getDependencyManagement() != null && dependencyManagementEditor.getInput() == null) {
            dependencyManagementEditor.setInput(model2.getDependencyManagement().getDependencies());
          } else if(model2.getDependencyManagement() == null) {
            dependencyManagementEditor.setInput(null);
          }

          if(model2.getDependencies() != null && dependenciesEditor.getInput() == null) {
            dependenciesEditor.setInput(model2.getDependencies());
          } else if(model2.getDependencies() == null) {
            dependenciesEditor.setInput(null);
          }

          dependenciesEditor.refresh();
          dependencyManagementEditor.refresh();
        }

        if(object instanceof DependencyManagement) {
          if(dependenciesEditor.getInput() == null) {
            dependenciesEditor.setInput(((DependencyManagement) object).getDependencies());
          }
          dependencyManagementEditor.refresh();
        }
      }
    });
  }

  void setupDependency(ValueProvider<? extends EObject> parentProvider, EReference feature, Dependency dependency) {
    CompoundCommand compoundCommand = new CompoundCommand();
    EditingDomain editingDomain = editorPage.getEditingDomain();

    EObject parent = parentProvider.getValue();
    if(parent == null) {
      parent = parentProvider.create(editingDomain, compoundCommand);
    }

    Command addDependencyCommand = AddCommand.create(editingDomain, parent, feature, dependency);
    compoundCommand.append(addDependencyCommand);

    editingDomain.getCommandStack().execute(compoundCommand);
  }

  Dependency createDependency(ValueProvider<? extends EObject> parentProvider, EReference feature, String groupId,
      String artifactId, String version, String classifier, String type, String scope) {
    CompoundCommand compoundCommand = new CompoundCommand();
    EditingDomain editingDomain = editorPage.getEditingDomain();

    EObject parent = parentProvider.getValue();
    if(parent == null) {
      parent = parentProvider.create(editingDomain, compoundCommand);
    }

    Dependency dependency = PomFactory.eINSTANCE.createDependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setVersion(version);
    dependency.setClassifier(classifier);
    dependency.setType(type);
    dependency.setScope(scope);

    Command addDependencyCommand = AddCommand.create(editingDomain, parent, feature, dependency);
    compoundCommand.append(addDependencyCommand);

    editingDomain.getCommandStack().execute(compoundCommand);

    return dependency;
  }

  public void setSearchControl(SearchControl searchControl) {
    if(this.searchControl != null) {
      return;
    }

    this.searchMatcher = new SearchMatcher(searchControl);
    this.searchFilter = new DependencyFilter(searchMatcher);
    this.searchControl = searchControl;
    this.searchControl.getSearchText().addModifyListener(new ModifyListener() {
      public void modifyText(ModifyEvent e) {
        selectDepenendencies(dependenciesEditor, model, POM_PACKAGE.getModel_Dependencies());
        selectDepenendencies(dependencyManagementEditor, dependencyManagementProvider.getValue(),
            POM_PACKAGE.getDependencyManagement_Dependencies());
      }

      private void selectDepenendencies(ListEditorComposite<Dependency> editor, EObject parent,
          EStructuralFeature feature) {
        if(parent != null) {
          editor.setSelection((EList<Dependency>) parent.eGet(feature));
          editor.refresh();
        }
      }
    });
    //we add filter here as the default behaviour is to filter..
    TableViewer viewer = dependenciesEditor.getViewer();
    viewer.addFilter(searchFilter);
    viewer = dependencyManagementEditor.getViewer();
    viewer.addFilter(searchFilter);

  }

  String getVersion(String groupId, String artifactId, IProgressMonitor monitor) {
    try {
      MavenProject mavenProject = editorPage.getPomEditor().readMavenProject(false, monitor);
      Artifact a = mavenProject.getArtifactMap().get(groupId + ":" + artifactId); //$NON-NLS-1$
      if(a != null) {
        return a.getBaseVersion();
      }
    } catch(CoreException ex) {
      MavenLogger.log(ex);
    }
    return null;
  }

  public static class DependencyFilter extends ViewerFilter {
    private SearchMatcher searchMatcher;

    public DependencyFilter(SearchMatcher searchMatcher) {
      this.searchMatcher = searchMatcher;
    }

    public boolean select(Viewer viewer, Object parentElement, Object element) {
      if(element instanceof Dependency) {
        Dependency d = (Dependency) element;
        return searchMatcher.isMatchingArtifact(d.getGroupId(), d.getArtifactId());
      }
      return false;
    }
  }

  void openManageDependenciesDialog() throws InvocationTargetException, InterruptedException {
    /*
     * A linked list representing the path from child to root parent pom.
     * The head is the child, the tail is the root pom
     */
    final LinkedList<MavenProject> hierarchy = new LinkedList<MavenProject>();
    
    IRunnableWithProgress projectLoader = new IRunnableWithProgress() {
      public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        try {
          IMaven maven = MavenPlugin.getDefault().getMaven();
          MavenProjectManager projectManager = MavenPlugin.getDefault().getMavenProjectManager();
          mavenProject = pomEditor.readMavenProject(false, monitor);
          if (mavenProject == null) {
            IMarker[] markers = pomEditor.getPomFile().findMarkers(IMavenConstants.MARKER_ID, true, IResource.DEPTH_ZERO);
            if (markers != null && markers.length > 0) {
              Display.getDefault().asyncExec(new Runnable() {
                
                public void run() {
                  MessageDialog.openError(getShell(), Messages.DependenciesComposite_error, Messages.DependenciesComposite_fixProjectErrors);                  
                }
              });
              return;
            }
          }
          maven.detachFromSession(mavenProject);
          IMavenProjectFacade projectFacade = projectManager.create(pomEditor.getPomFile(), true, monitor);
          hierarchy.addAll(new ParentGatherer(mavenProject, projectFacade).getParentHierarchy(monitor));
        } catch(CoreException e) {
          throw new InvocationTargetException(e);
        }
      }

    };

    PlatformUI.getWorkbench().getProgressService().run(false, true, projectLoader);

    if (hierarchy.isEmpty()) {
      //We were unable to read the project metadata above, so there was an error. 
      //User has already been notified to fix the problem.
      return;
    }
    
    final ManageDependenciesDialog manageDepDialog = new ManageDependenciesDialog(getShell(), model, hierarchy,
        pomEditor.getEditingDomain());
    manageDepDialog.open();
  }

  protected class PropertiesListComposite<T> extends ListEditorComposite<T> {
    private static final String PROPERTIES_BUTTON_KEY = "PROPERTIES"; //$NON-NLS-1$
    protected Button properties;

    public PropertiesListComposite(Composite parent, int style, boolean includeSearch) {
      super(parent, style, includeSearch);
    }

    @Override
    protected void createButtons(boolean includeSearch) {
      if(includeSearch) {
        createAddButton();
      }
      createRemoveButton();
      properties = createButton(Messages.ListEditorComposite_btnProperties);
      addButton(PROPERTIES_BUTTON_KEY, properties);
    }

    public void setPropertiesListener(SelectionListener listener) {
      properties.addSelectionListener(listener);
    }

    @Override
    protected void viewerSelectionChanged() {
      super.viewerSelectionChanged();
      updatePropertiesButton();
    }

    protected void updatePropertiesButton() {
      properties.setEnabled(!readOnly && !viewer.getSelection().isEmpty());
    }
    
    @Override
    public void setReadOnly(boolean readOnly) {
      super.setReadOnly(readOnly);
      updatePropertiesButton();
    }
  }
  
  protected class DependenciesListComposite<T> extends PropertiesListComposite<T> {

    private static final String MANAGE = "MANAGE"; //$NON-NLS-1$
    protected Button manage;
    protected Model model;

    public DependenciesListComposite(Composite parent, int style, boolean includeSearch) {
      super(parent, style, includeSearch);
    }
    
    @Override
    protected void createButtons(boolean includeSearch) {
      super.createButtons(includeSearch);
      manage = createButton(Messages.DependenciesComposite_manageButton);
      addButton(MANAGE, manage);
    }
    
    @Override
    protected void viewerSelectionChanged() {
      super.viewerSelectionChanged();
      updateManageButton();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) {
      super.setReadOnly(readOnly);
      updateManageButton();
    }

    protected void updateManageButton() {
      List<Dependency> deps = (List<Dependency>) viewer.getInput();
      manage.setEnabled(!readOnly && deps != null && !deps.isEmpty());
    }
    
    public void setManageButtonListener(SelectionListener listener) {
      manage.addSelectionListener(listener);
    }
  }
}
