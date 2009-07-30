package org.maven.ide.eclipse.ui.internal.preferences;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.ide.IDE;

import org.apache.maven.settings.validation.SettingsValidationResult;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.embedder.IMaven;
import org.maven.ide.eclipse.embedder.IMavenConfiguration;
import org.maven.ide.eclipse.embedder.MavenRuntime;
import org.maven.ide.eclipse.embedder.MavenRuntimeManager;
import org.maven.ide.eclipse.index.IndexManager;
import org.maven.ide.eclipse.internal.embedder.MavenEmbeddedRuntime;
import org.maven.ide.eclipse.internal.preferences.MavenPreferenceConstants;


/**
 * Maven installations preference page
 * 
 * @author Eugene Kuleshov
 */
public class MavenInstallationsPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

  final MavenPlugin mavenPlugin;

  final MavenRuntimeManager runtimeManager;
  
  final IMavenConfiguration mavenConfiguration;
  
  final IMaven maven;

  MavenRuntime defaultRuntime;

  List<MavenRuntime> runtimes;

  CheckboxTableViewer runtimesViewer;

  Text globalSettingsText;
  
  private String globalSettings;

  boolean dirty = false;

  public MavenInstallationsPreferencePage() {
    setTitle("Maven Installations");

    this.mavenPlugin = MavenPlugin.getDefault();
    this.runtimeManager = mavenPlugin.getMavenRuntimeManager();
    this.mavenConfiguration = MavenPlugin.lookup(IMavenConfiguration.class);
    this.maven = MavenPlugin.lookup(IMaven.class);
  
  }

  public void init(IWorkbench workbench) {
  }

  protected void performDefaults() {
    runtimeManager.reset();
    defaultRuntime = runtimeManager.getDefaultRuntime();
    runtimes = runtimeManager.getMavenRuntimes();

    runtimesViewer.setInput(runtimes);
    runtimesViewer.setChecked(defaultRuntime, true);
    runtimesViewer.refresh(); 
    
    storeCustom("");
    globalSettingsText.setText("");
    mavenConfiguration.setGlobalSettingsFile("");
    
    updateGlobals(true);
    super.performDefaults();
    setDirty(true);
  }
  
  
  protected void storeCustom(String dir){
    mavenPlugin.getPreferenceStore().setValue(P_MAVEN_CUSTOM_GLOBAL, dir == null ? "" : dir);
  }
  /* (non-Javadoc)
   * @see org.eclipse.jface.preference.PreferencePage#performApply()
   */
  protected void performApply() {
    updateSettings();
  }

  public void updateSettings(){   
    new Job("Updating Maven installation settings") {
      protected IStatus run(IProgressMonitor monitor) {
        String dir = getGlobalSettingsText();

        runtimeManager.setRuntimes(runtimes);
        runtimeManager.setDefaultRuntime(defaultRuntime);
        String oldSettings = mavenConfiguration.getGlobalSettingsFile();

        mavenConfiguration.setGlobalSettingsFile(dir);
        if(defaultRuntime == null || defaultRuntime instanceof MavenEmbeddedRuntime){
          storeCustom(dir);
        }
        mavenPlugin.getIndexManager().scheduleIndexUpdate(IndexManager.LOCAL_INDEX, true, 0L);
        if((dir == null && oldSettings != null) || (dir != null && !(dir.equals(oldSettings)))){
          //mavenPlugin.getIndexManager().scheduleIndexUpdate(IndexManager.LOCAL_INDEX, true, 0L);
        }
        return Status.OK_STATUS;
      }
    }.schedule();
  }
  
  public boolean performOk() {
    if (dirty) {
      updateSettings();
    }
    return true;
  }
  
  public void setDirty(boolean dirty){
    this.dirty = dirty;
  }
  
  public boolean isDirty(){
    return this.dirty;
  }
  
  protected boolean validateMavenInstall(String dir){
    if(dir == null || dir.length() == 0){
      return false;
    }
    File selectedDir = new File(dir);
    if(!selectedDir.isDirectory()){
      MessageDialog.openError(getShell(), "Maven Install", "Select the directory where Maven is installed.");
      return false;
    }
    File binDir = new File(dir, "bin");
    File confDir = new File(dir, "conf");
    File libDir = new File(dir, "lib");
    if(!binDir.exists() || !confDir.exists() || !libDir.exists()){
      MessageDialog.openError(getShell(), "Maven Install", "The selected directory is not a valid Maven directory.");
      return false;
    }
    return true;
  }
  protected Control createContents(Composite parent) {

    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout gridLayout = new GridLayout(3, false);
    gridLayout.marginBottom = 5;
    gridLayout.marginRight = 5;
    gridLayout.marginHeight = 0;
    gridLayout.marginWidth = 0;
    composite.setLayout(gridLayout);

    Label link = new Label(composite, SWT.NONE);
    link.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 3, 1));
    link.setText("Select the installation used to launch maven:");
    

    createTable(composite);
    createGlobalSettings(composite);


    defaultRuntime = runtimeManager.getDefaultRuntime();
    runtimes = runtimeManager.getMavenRuntimes();

    runtimesViewer.setInput(runtimes);
    runtimesViewer.setChecked(defaultRuntime, true);
    runtimesViewer.refresh(); // should listen on property changes instead?

    checkSettings();
    updateGlobals(false);
    globalSettingsText.addModifyListener(new ModifyListener() {
      public void modifyText(ModifyEvent modifyevent) {
        setGlobalSettingsText(globalSettingsText.getText());
        updateGlobalSettingsLink();
        checkSettings();
        setDirty(true);    
      }
    });

    return composite;
  }
  
  /**
   * 
   */
  private void updateGlobalSettingsText(boolean useLastCustomGlobal) {
    String globalSettings = getGlobalSettingsFile(useLastCustomGlobal);
    globalSettingsText.setText(globalSettings == null ? "" : globalSettings);
  }
  
  /**
   * Use this to retrieve the global settings file which has not been applied yet
   * @return
   */
  public String getGlobalSettingsFile(boolean useLastCustomGlobal) {
    if(defaultRuntime == null || defaultRuntime instanceof MavenEmbeddedRuntime){
      String globalSettings = null;
      if(useLastCustomGlobal){
        globalSettings = mavenPlugin.getPreferenceStore().getString(P_MAVEN_CUSTOM_GLOBAL);
      } else {
        globalSettings = mavenPlugin.getPreferenceStore().getString(MavenPreferenceConstants.P_GLOBAL_SETTINGS_FILE);
      }
      return globalSettings.trim().length()==0 ? null : globalSettings;
    }
    return defaultRuntime == null ? null : defaultRuntime.getSettings();
  } 
  
  public void setGlobalSettingsText(String settings){
    this.globalSettings = settings;
  }
  public String getGlobalSettingsText(){
    return this.globalSettings;
  }
  /**
   * 
   */
  private void updateGlobals(boolean useLastCustomGlobal) {
    updateGlobalSettingsText(useLastCustomGlobal);
    updateGlobalSettingsLink();
    updateGlobalSettingsBrowseButton();
  }

  private Link globalSettingsLink;

  private Button globalSettingsBrowseButton;
  
  private MavenRuntime getCheckedRuntime(){
    Object[] runtimes = runtimesViewer.getCheckedElements();
    if(runtimes != null && runtimes.length > 0){
      return (MavenRuntime)runtimes[0];
    }
    return null;
  }
  
  protected MavenRuntime getSelectedMavenRuntime(){
    IStructuredSelection sel = (IStructuredSelection)runtimesViewer.getSelection();
    return (MavenRuntime) sel.getFirstElement();
  }
  
  private void updateGlobalSettingsLink(){
    MavenRuntime runtime = getCheckedRuntime();
    String text = "";
    String currText = globalSettingsText.getText();
    boolean showURL = false;
    
    File f = new File(currText);
    if(f.exists()){
      showURL = true;
    }
    String openFile = showURL ? " (<a href=\"#\">open file</a>):" : ":";
    if(runtime instanceof MavenEmbeddedRuntime){
      text = "Global settings"+openFile;
    } else {
      text = "Global settings from installation directory"+openFile;
    }
    globalSettingsLink.setText(text);
  }
  
  private void updateGlobalSettingsBrowseButton(){
    MavenRuntime runtime = getCheckedRuntime();
    boolean enabled = (runtime != null && (runtime instanceof MavenEmbeddedRuntime));
    globalSettingsBrowseButton.setEnabled(enabled);
    globalSettingsText.setEditable(enabled);
  }
  
  private void createGlobalSettings(Composite composite) {
    globalSettingsLink = new Link(composite, SWT.NONE);
    globalSettingsLink.setData("name", "globalSettingsLink");
    
    globalSettingsLink.setToolTipText("Open editor for global settings");
    GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
    gd.verticalIndent = 25;
    globalSettingsLink.setLayoutData(gd);
    
    globalSettingsLink.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        String globalSettings = getGlobalSettings();
        if(globalSettings.length() == 0) {
          globalSettings = defaultRuntime.getSettings();
        }
        if(globalSettings != null && globalSettings.length() > 0) {
          openEditor(globalSettings);
        }
      }
    });

    globalSettingsText = new Text(composite, SWT.BORDER);
    globalSettingsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    globalSettingsText.setData("name", "globalSettingsText");
    globalSettingsBrowseButton = new Button(composite, SWT.NONE);
    GridData gd_globalSettingsBrowseButton = new GridData(SWT.FILL, SWT.CENTER, false, false);
    globalSettingsBrowseButton.setLayoutData(gd_globalSettingsBrowseButton);
    globalSettingsBrowseButton.setText("&Browse...");
    globalSettingsBrowseButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        if(getGlobalSettings().length() > 0) {
          dialog.setFileName(getGlobalSettings());
        }
        String file = dialog.open();
        if(file != null) {
          file = file.trim();
          if(file.length() > 0) {
            globalSettingsText.setText(file);
          }
        }
      }
    });
  }
 

  private void createTable(Composite composite){
    runtimesViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER | SWT.FULL_SELECTION);

    runtimesViewer.setLabelProvider(new RuntimesLabelProvider());

    runtimesViewer.setContentProvider(new IStructuredContentProvider() {

      @SuppressWarnings("unchecked")
      public Object[] getElements(Object input) {
        if(input instanceof List) {
          List list = (List) input;
          if(list.size() > 0) {
            return list.toArray(new MavenRuntime[list.size()]);
          }
        }
        return new Object[0];
      }

      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }

      public void dispose() {
      }

    });

    Table table = runtimesViewer.getTable();
    table.setLinesVisible(false);
    table.setHeaderVisible(false);
    GridData gd_table = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 3);
    gd_table.heightHint = 151;
    gd_table.widthHint = 333;
    table.setLayoutData(gd_table);

    TableColumn typeColumn = new TableColumn(table, SWT.NONE);
    typeColumn.setWidth(325);
    typeColumn.setText("");

    Button addButton = new Button(composite, SWT.NONE);
    addButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    addButton.setText("&Add...");
    addButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        DirectoryDialog dlg = new DirectoryDialog(getShell());
        dlg.setText("Maven Installation");
        dlg.setMessage("Select Maven installation directory");
        String dir = dlg.open();
        if(dir == null){
          return;
        }
        boolean ok = validateMavenInstall(dir);
        if(ok){
          MavenRuntime runtime = MavenRuntimeManager.createExternalRuntime(dir);
          if(runtimes.contains(runtime)) {
            MessageDialog.openError(getShell(), "Maven Install", "The selected Maven install is already registered.");
          } else {
            runtimes.add(runtime);
            runtimesViewer.refresh();
            runtimesViewer.setAllChecked(false);
            runtimesViewer.setChecked(runtime, true);
            if(runtime != null){
              setCheckedRuntime(runtime);
            }
          }
        }
      }
    });

    final Button editButton = new Button(composite, SWT.NONE);
    editButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    editButton.setEnabled(false);
    editButton.setText("&Edit...");
    editButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        MavenRuntime runtime = getSelectedMavenRuntime();
        DirectoryDialog dlg = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dlg.setText("Maven Installation");
        dlg.setMessage("Select Maven installation directory");
        dlg.setFilterPath(runtime.getLocation());
        String dir = dlg.open();
        boolean ok = validateMavenInstall(dir);
        if(ok && !dir.equals(runtime.getLocation())) {
          MavenRuntime newRuntime = MavenRuntimeManager.createExternalRuntime(dir);
          if(runtimes.contains(newRuntime)) {
            MessageDialog.openError(getShell(), "Maven Install", "Selected Maven install is already registered");
          } else {
            runtimes.set(runtimes.indexOf(runtime), newRuntime);
            runtimesViewer.refresh();
            setDirty(true);
            if(newRuntime != null){
              setCheckedRuntime(newRuntime);
            }
          }
        }
      }
    });

    final Button removeButton = new Button(composite, SWT.NONE);
    removeButton.setEnabled(false);
    removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
    removeButton.setText("&Remove");
    removeButton.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        MavenRuntime runtime = getSelectedMavenRuntime();
        runtimes.remove(runtime);
        runtimesViewer.refresh();
        Object[] checkedElements = runtimesViewer.getCheckedElements();
        if(checkedElements == null || checkedElements.length == 0) {
          defaultRuntime = runtimeManager.getRuntime(MavenRuntimeManager.EMBEDDED);
          runtimesViewer.setChecked(defaultRuntime, true);
          setCheckedRuntime(defaultRuntime);
        }
        setDirty(true);
      }
    });

    runtimesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      public void selectionChanged(SelectionChangedEvent event) {
        if(runtimesViewer.getSelection() instanceof IStructuredSelection) {
          MavenRuntime runtime = getSelectedMavenRuntime();
          boolean isEnabled = runtime != null && runtime.isEditable();
          removeButton.setEnabled(isEnabled);
          editButton.setEnabled(isEnabled);
        }
      }
    });

    runtimesViewer.addCheckStateListener(new ICheckStateListener() {
      public void checkStateChanged(CheckStateChangedEvent event) {
        if(event.getElement() != null && event.getChecked()){
          
          setCheckedRuntime((MavenRuntime)event.getElement());
        }
      }
    });
    Link noteLabel = new Link(composite, SWT.WRAP | SWT.READ_ONLY);
    GridData noteLabelData = new GridData(SWT.FILL, SWT.TOP, false, false, 2, 1);
    noteLabelData.horizontalIndent = 15;
    noteLabelData.widthHint = 100;
    
    noteLabel.setLayoutData(noteLabelData);
    noteLabel.setText("Note: Embedded runtime is always used for dependency resolution, but " +
        "does not use global settings when it is used to launch Maven. To learn more, visit the <a href=\"http://maven.apache.org/\">maven</a> web page.");
    noteLabel.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        try {
          URL url = new URL(e.text);
          IWebBrowser browser = PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser();
          browser.openURL(url);
        } catch(MalformedURLException ex) {
          MavenLogger.log("Malformed URL", ex);
        } catch(PartInitException ex) {
          MavenLogger.log(ex);
        }
      }
    });
  }
  
  private static final String P_MAVEN_CUSTOM_GLOBAL = "customGlobalSettingsFile";
  
  protected void setCheckedRuntime(MavenRuntime runtime){
    runtimesViewer.setAllChecked(false);
    runtimesViewer.setChecked(runtime, true);
    defaultRuntime = runtime;
    boolean useDefault = (defaultRuntime == null || defaultRuntime instanceof MavenEmbeddedRuntime);
    updateGlobals(useDefault);
    setDirty(true);
  }

  void checkSettings() {
    setErrorMessage(null);
    setMessage(null);

    String globalSettings = getGlobalSettings();
    if(globalSettings != null && globalSettings.length() > 0) {
      File globalSettingsFile = new File(globalSettings);
      if(!globalSettingsFile.exists()) {
        setMessage("Global settings file doesn't exist", IMessageProvider.WARNING);
        globalSettings = null;
      }
    } else {
      globalSettings = null;
    }

    SettingsValidationResult result = maven.validateSettings(globalSettings);
    if(result.getMessageCount() > 0) {
      setMessage("Unable to parse global settings file; " + result.getMessage(0), IMessageProvider.WARNING);
    }

  }


  
  @SuppressWarnings("unchecked")
  void openEditor(final String fileName) {
    // XXX create new settings.xml if does not exist

    IWorkbench workbench = PlatformUI.getWorkbench();
    IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
    IWorkbenchPage page = window.getActivePage();

    IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor("settings.xml");

    File file = new File(fileName);
    IEditorInput input = null;
    try {
      //class implementing editor input for external file has been renamed in eclipse 3.3, hence reflection
      Class javaInput = null;
      try {
        javaInput = Class.forName("org.eclipse.ui.internal.editors.text.JavaFileEditorInput");
        Constructor cons = javaInput.getConstructor(new Class[] {File.class});
        input = (IEditorInput) cons.newInstance(new Object[] {file});
      } catch(Exception e) {
        try {
          IFileStore fileStore = EFS.getLocalFileSystem().fromLocalFile(file);
          Class storeInput = Class.forName("org.eclipse.ui.ide.FileStoreEditorInput");
          Constructor cons = storeInput.getConstructor(new Class[] {IFileStore.class});
          input = (IEditorInput) cons.newInstance(new Object[] {fileStore});
        } catch(Exception ex) {
          //ignore...
        }
      }
      final IEditorPart editor = IDE.openEditor(page, input, desc.getId());
      editor.addPropertyListener(new IPropertyListener() {
        public void propertyChanged(Object source, int propId) {
          if(!editor.isDirty()) {
            mavenPlugin.getConsole().logMessage("Refreshing settings " + fileName);
            invalidateMavenSettings(false);
          }
        }
      });

    } catch(PartInitException ex) {
      MavenLogger.log(ex);
    }
  }


  void invalidateMavenSettings(final boolean reindex) {
//    new Job("Invalidating Maven settings") {
//      protected IStatus run(IProgressMonitor monitor) {
//        mavenPlugin.getMavenEmbedderManager().invalidateMavenSettings();
//        if(reindex) {
//          mavenPlugin.getIndexManager().scheduleIndexUpdate(IndexManager.LOCAL_INDEX, true, 0L);
//        }
//        return Status.OK_STATUS;
//      }
//    }.schedule();
  }
  String getGlobalSettings() {
    return globalSettingsText.getText().trim();
  }

  static class RuntimesLabelProvider implements ITableLabelProvider, IColorProvider {

    public String getColumnText(Object element, int columnIndex) {
      MavenRuntime runtime = (MavenRuntime) element;
      return runtime.toString();
    }

    public Image getColumnImage(Object element, int columnIndex) {
      return null;
    }

    public Color getBackground(Object element) {
      return null;
    }

    public Color getForeground(Object element) {
      MavenRuntime runtime = (MavenRuntime) element;
      if(!runtime.isEditable()) {
        return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
      }
      return null;
    }

    public void dispose() {
    }

    public boolean isLabelProperty(Object element, String property) {
      return false;
    }

    public void addListener(ILabelProviderListener listener) {
    }

    public void removeListener(ILabelProviderListener listener) {
    }

  }

}
