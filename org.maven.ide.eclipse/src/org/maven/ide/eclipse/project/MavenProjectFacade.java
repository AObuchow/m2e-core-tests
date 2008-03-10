/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.project;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.internal.project.MavenProjectManagerImpl;


/**
 * MavenProjectFacade
 * 
 * getAllXXX methods consider nested modules of this project, if any. 
 * getXXX methods work on this project only.
 */
public class MavenProjectFacade {

  private final MavenProjectManagerImpl manager;

  private final IFile pom;

  private final MavenProject mavenProject;

  private final ResolverConfiguration resolverConfiguration;

  public MavenProjectFacade(MavenProjectManagerImpl manager, IFile pom, MavenProject mavenProject,
      ResolverConfiguration resolverConfiguration) {
    this.manager = manager;
    this.pom = pom;
    this.mavenProject = mavenProject;
    this.resolverConfiguration = resolverConfiguration;
  }

  /**
   * Returns project relative paths of resource directories
   */
  public IPath[] getResourceLocations() {
    return getResourceLocations(mavenProject.getResources());
  }

  private IPath[] getResourceLocations(List resources) {
    LinkedHashSet locations = new LinkedHashSet();
    for(Iterator it = resources.iterator(); it.hasNext();) {
      Resource resource = (Resource) it.next();
      locations.add(getProjectRelativePath(resource.getDirectory()));
    }
    return (IPath[]) locations.toArray(new IPath[locations.size()]);
  }

  /**
   * Returns project relative paths of test resource directories
   */
  public IPath[] getTestResourceLocations() {
    return getResourceLocations(mavenProject.getTestResources());
  }

  public IPath[] getCompileSourceLocations() {
    return getSourceLocations(mavenProject.getCompileSourceRoots());
  }

  private IPath[] getSourceLocations(List roots) {
    LinkedHashSet locations = new LinkedHashSet();
    for(Iterator i = roots.iterator(); i.hasNext();) {
      IPath path = getProjectRelativePath((String) i.next());
      if(path != null) {
        locations.add(path);
      }
    }
    return (IPath[]) locations.toArray(new IPath[locations.size()]);
  }

  public IPath[] getTestCompileSourceLocations() {
    return getSourceLocations(mavenProject.getTestCompileSourceRoots());
  }

  /**
   * Returns project resource for given filesystem location or null the location is outside of project.
   * 
   * @param resourceLocation absolute filesystem location
   * @return IPath the full, absolute workspace path resourceLocation
   */
  public IPath getProjectRelativePath(String resourceLocation) {
    if(resourceLocation == null) {
      return null;
    }
    IPath projectLocation = getProject().getLocation();
    IPath directory = Path.fromOSString(resourceLocation); // this is an absolute path!
    if(!projectLocation.isPrefixOf(directory)) {
      return null;
    }

    return directory.removeFirstSegments(projectLocation.segmentCount()).makeRelative().setDevice(null);
  }

  public void filterResources(IProgressMonitor monitor) throws CoreException {
    try {
      manager.filterResources(this, monitor);
    } catch(MavenEmbedderException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Returns the full, absolute path of this project maven build output directory relative to the workspace or null if
   * maven build output directory cannot be determined or outside of the workspace.
   */
  public IPath getOutputLocation() {
    IPath path = getProjectRelativePath(mavenProject.getBuild().getOutputDirectory());
    return path != null ? getProject().getFullPath().append(path) : null;
  }

  /**
   * Returns the full, absolute path of this project maven build test output directory relative to the workspace or null
   * if maven build output directory cannot be determined or outside of the workspace.
   */
  public IPath getTestOutputLocation() {
    IPath path = getProjectRelativePath(mavenProject.getBuild().getTestOutputDirectory());
    return path != null ? getProject().getFullPath().append(path) : null;
  }

  public IPath getFullPath() {
    return getProject().getFullPath();
  }

  public MavenProject getMavenProject() {
    return mavenProject;
  }

  public Set getTestArtifacts() {
    Set result = new LinkedHashSet();
    addExternalArtifacts(result, mavenProject.getTestArtifacts());
    return result;
  }

  private void addExternalArtifacts(Set result, List artifacts) {
    for(Iterator it = artifacts.iterator(); it.hasNext();) {
      Artifact artifact = (Artifact) it.next();
      if(getFullPath(artifact.getFile()) == null) {
        result.add(artifact);
      }
    }
  }

  public Set getRuntimeArtifacts() {
    Set result = new LinkedHashSet();
    addExternalArtifacts(result, mavenProject.getRuntimeArtifacts());
    return result;
  }

  public String getPackaging() {
    return mavenProject.getPackaging();
  }

  public IProject getProject() {
    return pom.getProject();
  }

  public IFile getPom() {
    return pom;
  }

  /**
   * Returns the full, absolute path of the given file relative to the workspace. Returns null if the file does not
   * exist or is not a member of this project.
   */
  public IPath getFullPath(File file) {
    IProject project = getProject();
    if (project == null || file == null) {
      return null;
    }

    IPath projectPath = project.getLocation();
    if(projectPath == null) {
      return null;
    }
    
    IPath filePath = new Path(file.getAbsolutePath());
    if (!projectPath.isPrefixOf(filePath)) {
      return null;
    }
    IResource resource = project.findMember(filePath.removeFirstSegments(projectPath.segmentCount()));
    if (resource == null) {
      return null;
    }
    return resource.getFullPath();
  }

  public void addDependency(IFile file, Dependency dependency) {
    // TODO Auto-generated method addDependency
    throw new UnsupportedOperationException("To be implemented");
  }

  /**
   * Visits trough Maven project artifacts and modules
   * 
   * @param visitor a project visitor used to visit Maven project
   * @param flags flags to specify visiting behavior. See {@link IMavenProjectVisitor#LOAD},
   *          {@link IMavenProjectVisitor#NESTED_MODULES}.
   */
  public void accept(IMavenProjectVisitor visitor, int flags) throws CoreException {
    IJavaProject javaProject = JavaCore.create(getProject());
    ResolverConfiguration configuration = BuildPathManager.getResolverConfiguration(javaProject);

    if(visitor.visit(this)) {
      MavenProject mavenProject = getMavenProject();
      for(Iterator it = mavenProject.getArtifacts().iterator(); it.hasNext();) {
        visitor.visit(this, (Artifact) it.next());
      }

      if((flags & IMavenProjectVisitor.FORCE_MODULES) > 0 //
          || ((flags & IMavenProjectVisitor.NESTED_MODULES) > 0 //
              && configuration != null && configuration.shouldIncludeModules())) {
        IFile pom = getPom();
        for(Iterator it = mavenProject.getModules().iterator(); it.hasNext();) {
          String moduleName = (String) it.next();
          IFile modulePom = pom.getParent().getFile(new Path(moduleName).append(MavenPlugin.POM_FILE_NAME));
          MavenProjectFacade moduleFacade = manager.create(modulePom, false, null);
          if(moduleFacade == null && ((flags & IMavenProjectVisitor.LOAD) > 0)) {
            moduleFacade = manager.create(modulePom, true, new NullProgressMonitor());
          }
          if(moduleFacade != null) {
            moduleFacade.accept(visitor, flags);
          }
        }
      }
    }
  }

  public ResolverConfiguration getResolverConfiguration() {
    return resolverConfiguration;
  }

}
