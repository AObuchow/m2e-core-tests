/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.refactoring.exclude;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.packageview.ClassPathContainer.RequiredProjectWrapper;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.m2e.core.actions.SelectionUtil;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;

/**
 * This action is intended to be used in popup menus
 * 
 * @author Anton Kraev
 */
@SuppressWarnings("restriction")
public class DependencyExcludeAction implements IActionDelegate {

  public static final String ID = "org.eclipse.m2e.refactoring.DependencyExclude"; //$NON-NLS-1$
  
  private IFile file;
  private ArtifactKey artifactKey;

  public void run(IAction action) {
    if (artifactKey != null && file != null) {
      Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
      MavenExcludeWizard wizard = new MavenExcludeWizard(file, //
          artifactKey.getGroupId(), artifactKey.getArtifactId());
      try {
        String titleForFailedChecks = ""; //$NON-NLS-1$
        RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
        op.run(shell, titleForFailedChecks);
      } catch(InterruptedException e) {
        // XXX
      }
    }
  }

  public void selectionChanged(IAction action, ISelection selection) {
    file = null;
    artifactKey = null;
    
    // TODO move logic into adapters
    if (selection instanceof IStructuredSelection) {
      IStructuredSelection structuredSelection = (IStructuredSelection) selection;
      if(structuredSelection.size()==1) {
        Object selected = structuredSelection.getFirstElement();
        if (selected instanceof Artifact) {
          file = getFileFromEditor();
          artifactKey = new ArtifactKey((Artifact) selected);
          
        } else if (selected instanceof DependencyNode) {
          file = getFileFromEditor();
          artifactKey = new ArtifactKey(((DependencyNode) selected).getArtifact());
          
        } else if (selected instanceof org.sonatype.aether.graph.DependencyNode) {
          file = getFileFromEditor();
          artifactKey = new ArtifactKey(((org.sonatype.aether.graph.DependencyNode) selected).getDependency().getArtifact());
          
        } else if (selected instanceof RequiredProjectWrapper) {
          RequiredProjectWrapper w = (RequiredProjectWrapper) selected;
          file = getFileFromProject(w.getParentClassPathContainer().getJavaProject());
          artifactKey = SelectionUtil.getType(selected, ArtifactKey.class);
        
        } else {
          artifactKey = SelectionUtil.getType(selected, ArtifactKey.class);
          if (selected instanceof IJavaElement) {
            IJavaElement el = (IJavaElement) selected;
            file = getFileFromProject(el.getParent().getJavaProject());
          }
        
        }
      }
    }
    
    if (artifactKey != null && file != null) {
      action.setEnabled(true);
    } else {
      action.setEnabled(false);
    }
  }

  private IFile getFileFromProject(IJavaProject javaProject) {
    return javaProject.getProject().getFile("pom.xml"); //$NON-NLS-1$
  }

  private IFile getFileFromEditor() {
    IEditorPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
    if (part != null && part.getEditorInput() instanceof IFileEditorInput) {
      IFileEditorInput input = (IFileEditorInput) part.getEditorInput();
      return input.getFile();
    }
    return null;
  }

}
