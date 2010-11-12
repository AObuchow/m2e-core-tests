/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.actions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;

import org.codehaus.plexus.util.IOUtil;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectManager;
import org.eclipse.m2e.core.util.Util;
import org.eclipse.m2e.core.util.Util.FileStoreEditorInputStub;
import org.eclipse.m2e.model.edit.pom.Dependency;


/**
 * Helper methods to deal with workspace resources passed as navigator selection to actions and wizards.
 */
public class SelectionUtil {

  public static final int UNSUPPORTED = 0;

  public static final int PROJECT_WITH_NATURE = 1;

  public static final int PROJECT_WITHOUT_NATURE = 2;

  public static final int POM_FILE = 4;

  public static final int JAR_FILE = 8;

  public static final int WORKING_SET = 16;

  /** Checks which type the given selection belongs to. */
  public static int getSelectionType(IStructuredSelection selection) {
    int type = UNSUPPORTED;
    if(selection != null) {
      for(Iterator<?> it = selection.iterator(); it.hasNext();) {
        int elementType = getElementType(it.next());
        if(elementType == UNSUPPORTED) {
          return UNSUPPORTED;
        }
        type |= elementType;
      }
    }
    return type;
  }

  /** Checks which type the given element belongs to. */
  public static int getElementType(Object element) {
    IProject project = getType(element, IProject.class);
    if(project != null) {
      try {
        if(project.hasNature(IMavenConstants.NATURE_ID)) {
          return PROJECT_WITH_NATURE;
        }
        return PROJECT_WITHOUT_NATURE;
      } catch(CoreException e) {
        // ignored
      }
    }

    IFile file = getType(element, IFile.class);
    if(file != null) {
      if(IMavenConstants.POM_FILE_NAME.equals(file.getFullPath().lastSegment())) {
        return POM_FILE;
      }
    }

    ArtifactKey artifactKey  = getType(element, ArtifactKey.class);
    if(artifactKey != null) {
      return JAR_FILE;
    }

    IWorkingSet workingSet = getType(element, IWorkingSet.class);
    if(workingSet!=null) {
      return WORKING_SET;
    }
    
    return UNSUPPORTED;
  }

  /**
   * Checks if the object belongs to a given type and returns it or a suitable adapter.
   */
  @SuppressWarnings("unchecked")
  public static <T> T getType(Object element, Class<T> type) {
    if(element==null) {
      return null;
    }
    if(type.isInstance(element)) {
      return (T) element;
    }
    if(element instanceof IAdaptable) {
      T adapter = (T) ((IAdaptable) element).getAdapter(type);
      if(adapter != null) {
        return adapter;
      }
    }
    return (T) Platform.getAdapterManager().getAdapter(element, type);
  }

  public static IPath getSelectedLocation(IStructuredSelection selection) {
    Object element = selection == null ? null : selection.getFirstElement();

    IPath path = getType(element, IPath.class);
    if(path != null) {
      return path;
    }

    IResource resource = getType(element, IResource.class);
    if(resource != null) {
      return resource.getLocation();
    }
    
//    IPackageFragmentRoot fragment = getType(element, IResource.class);
//    if(fragment != null) {
//      IJavaProject javaProject = fragment.getJavaProject();
//      if(javaProject != null) {
//        IResource resource = getType(javaProject, IResource.class);
//        if(resource != null) {
//          return resource.getProject().getProject().getLocation();
//        }
//      }
//    }
    
    return null; 
  }

  public static IWorkingSet getSelectedWorkingSet(IStructuredSelection selection) {
    Object element = selection == null ? null : selection.getFirstElement();
    {
      IWorkingSet workingSet = getType(element, IWorkingSet.class);
      if(workingSet != null) {
        return workingSet;
      }
    }
    {
      IResource resource = getType(element, IResource.class);
      if(resource != null) {
        return getWorkingSet(resource.getProject());
      }
    }

//    IResource resource = getType(element, IResource.class);
//    if(resource != null) {
//      return getWorkingSet(resource);
//    }
    
//    IPackageFragmentRoot fragment = getType(element, IPackageFragmentRoot.class);
//    if(fragment != null) {
//      IJavaProject javaProject = fragment.getJavaProject();
//      if(javaProject != null) {
//        IResource resource = getType(javaProject, IResource.class);
//        if(resource != null) {
//          return getWorkingSet(resource.getProject());
//        }
//      }
//    }

    return null;
  }

  public static IWorkingSet getWorkingSet(Object element) {
    IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
    for(IWorkingSet workingSet : workingSetManager.getWorkingSets()) {
      for(IAdaptable adaptable : workingSet.getElements()) {
        if(adaptable.getAdapter(IResource.class) == element) {
          return workingSet;
        }
      }
    }
    return null;
  }

  public static IWorkingSet[] getAssignedWorkingSets(Object element) {
    List<IWorkingSet> list = new ArrayList<IWorkingSet>();
    IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
    for(IWorkingSet workingSet : workingSetManager.getWorkingSets()) {
      for(IAdaptable adaptable : workingSet.getElements()) {
        if(adaptable.getAdapter(IResource.class) == element) {
          list.add(workingSet);
        }
      }
    }
    return list.toArray(new IWorkingSet[list.size()]);
  }
  
  public static ArtifactKey getArtifactKey(Object element) throws CoreException {
    if(element instanceof Artifact) {
      return new ArtifactKey(((Artifact) element));
      
    } else if(element instanceof org.sonatype.aether.graph.DependencyNode) {
      org.sonatype.aether.artifact.Artifact artifact = ((org.sonatype.aether.graph.DependencyNode) element)
          .getDependency().getArtifact();
      return new ArtifactKey(artifact);
      
    } else if(element instanceof Dependency) {
      Dependency dependency = (Dependency) element;
      String groupId = dependency.getGroupId();
      String artifactId = dependency.getArtifactId();
      String version = dependency.getVersion();
      
      if(version == null) {
        IEditorPart editor = getActiveEditor();
        if(editor!=null) {
          MavenProject mavenProject = getMavenProject(editor.getEditorInput(), null);
          if(mavenProject!=null) {
            Artifact a = mavenProject.getArtifactMap().get(groupId + ":" + artifactId);
            version = a.getBaseVersion();
          }
        }
      }
      return new ArtifactKey(dependency.getGroupId(), dependency.getArtifactId(), version, null);
    }
    
    return SelectionUtil.getType(element, ArtifactKey.class);
  }

  public static MavenProject getMavenProject(IEditorInput editorInput, IProgressMonitor monitor) throws CoreException {
    if(editorInput instanceof IFileEditorInput) {
      IFile pomFile = ((IFileEditorInput) editorInput).getFile();
      MavenProjectManager projectManager = MavenPlugin.getDefault().getMavenProjectManager();
      IMavenProjectFacade facade = projectManager.create(pomFile, true, monitor);
      if(facade!=null) {
        return facade.getMavenProject(monitor);
      }

    } else if(editorInput instanceof IStorageEditorInput) {
      IStorageEditorInput storageInput = (IStorageEditorInput) editorInput;
      IStorage storage = storageInput.getStorage();
      IPath path = storage.getFullPath();
      if(path == null || !new File(path.toOSString()).exists()) {
        File tempPomFile = null;
        InputStream is = null;
        OutputStream os = null;
        try {
          tempPomFile = File.createTempFile("maven-pom", ".pom");
          os = new FileOutputStream(tempPomFile);
          is = storage.getContents();
          IOUtil.copy(is, os);
          return readMavenProject(tempPomFile, monitor);
        } catch(IOException ex) {
          MavenLogger.log("Can't close stream", ex);
        } finally {
          IOUtil.close(is);
          IOUtil.close(os);
          if(tempPomFile != null) {
            tempPomFile.delete();
          }
        }
      } else {
        return readMavenProject(path.toFile(), monitor);
      }

    } else if(editorInput.getClass().getName().endsWith("FileStoreEditorInput")) {
      return readMavenProject(new File(Util.proxy(editorInput, FileStoreEditorInputStub.class).getURI().getPath()), monitor);
    }
    
    return null;
  }
  
  private static MavenProject readMavenProject(File pomFile, IProgressMonitor monitor) throws CoreException {
    if(monitor==null) {
      monitor = new NullProgressMonitor();
    }
    
    IMaven maven = MavenPlugin.getDefault().getMaven();

    MavenExecutionRequest request = maven.createExecutionRequest(monitor);
    request.setOffline(false);
    request.setUpdateSnapshots(false);
    request.setRecursive(false);
    request.setPom(pomFile);

    MavenExecutionResult result = maven.execute(request, monitor);

    MavenProject project = result.getProject();
    if(project!=null) {
      return project;
    }

    if(result.hasExceptions()) {
      List<IStatus> statuses = new ArrayList<IStatus>();
      List<Throwable> exceptions = result.getExceptions();
      for(Throwable e : exceptions) {
        statuses.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, e.getMessage(), e));
      }

      throw new CoreException(new MultiStatus(IMavenConstants.PLUGIN_ID, IStatus.ERROR, //
          statuses.toArray(new IStatus[statuses.size()]), "Can't read Maven project", null));
    }

    throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, //
        "Can't read Maven project", null));
  }

  private static IEditorPart getActiveEditor() {
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if(window != null) {
      IWorkbenchPage page = window.getActivePage();
      if(page != null) {
        return page.getActiveEditor();
      }
    }
    return null;
  }

}
