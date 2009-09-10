/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.project;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.project.MavenProject;

import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.embedder.ArtifactKey;
import org.maven.ide.eclipse.embedder.ArtifactRef;
import org.maven.ide.eclipse.embedder.ArtifactRepositoryRef;
import org.maven.ide.eclipse.project.IMavenProjectFacade;
import org.maven.ide.eclipse.project.IMavenProjectVisitor;
import org.maven.ide.eclipse.project.IMavenProjectVisitor2;
import org.maven.ide.eclipse.project.MavenProjectUtils;
import org.maven.ide.eclipse.project.MavenUpdateRequest;
import org.maven.ide.eclipse.project.ResolverConfiguration;


/**
 * MavenProject facade
 */
public class MavenProjectFacade implements IMavenProjectFacade, Serializable {

  private static final long serialVersionUID = 707484407691175077L;

  private final MavenProjectManagerImpl manager;

  private final IFile pom;

  private final File pomFile;

  private transient MavenProject mavenProject;
  private transient MavenExecutionPlan executionPlan;

  private transient Map<String, Object> sessionProperties;

  // XXX make final, there should be no need to change it
  private ResolverConfiguration resolverConfiguration;

  private final long[] timestamp = new long[MavenProjectManagerImpl.METADATA_PATH.size() + 1];

  // cached values from mavenProject
  private ArtifactKey artifactKey;
  private List<String> modules;
  private String packaging;
  private IPath[] resourceLocations;
  private IPath[] testResourceLocations;
  private IPath[] compileSourceLocations;
  private IPath[] testCompileSourceLocations;
  private IPath outputLocation;
  private IPath testOutputLocation;
  private Set<ArtifactRef> artifacts;
  private Set<ArtifactRepositoryRef> artifactRepositories;
  private Set<ArtifactRepositoryRef> pluginArtifactRepositories;

  public MavenProjectFacade(MavenProjectManagerImpl manager, IFile pom, MavenProject mavenProject,
      ResolverConfiguration resolverConfiguration) {
    this.manager = manager;
    this.pom = pom;
    IPath location = pom.getLocation();
    this.pomFile = location == null ? null : location.toFile(); // save pom file
    this.resolverConfiguration = resolverConfiguration;
    setMavenProject(mavenProject);
    updateTimestamp();
  }

  private void setMavenProject(MavenProject mavenProject) {
    this.mavenProject = mavenProject;
    
    this.artifactKey = new ArtifactKey(mavenProject.getArtifact());
    this.packaging = mavenProject.getPackaging();
    this.modules = mavenProject.getModules();

    this.resourceLocations = MavenProjectUtils.getResourceLocations(getProject(), mavenProject.getResources());
    this.testResourceLocations = MavenProjectUtils.getResourceLocations(getProject(), mavenProject.getTestResources());
    this.compileSourceLocations = MavenProjectUtils.getSourceLocations(getProject(), mavenProject.getCompileSourceRoots());
    this.testCompileSourceLocations = MavenProjectUtils.getSourceLocations(getProject(),mavenProject.getTestCompileSourceRoots());

    IPath fullPath = getProject().getFullPath();

    IPath path = getProjectRelativePath(mavenProject.getBuild().getOutputDirectory());
    this.outputLocation = (path != null) ? fullPath.append(path) : null;

    path = getProjectRelativePath(mavenProject.getBuild().getTestOutputDirectory());
    this.testOutputLocation = path != null ? fullPath.append(path) : null;

    this.artifacts = ArtifactRef.fromArtifact(mavenProject.getArtifacts());

    this.artifactRepositories = new LinkedHashSet<ArtifactRepositoryRef>();
    for(ArtifactRepository repository : mavenProject.getRemoteArtifactRepositories()) {
      this.artifactRepositories.add(new ArtifactRepositoryRef(repository.getId(), repository.getUrl()));
    }

    this.pluginArtifactRepositories = new LinkedHashSet<ArtifactRepositoryRef>();
    for(ArtifactRepository repository : mavenProject.getPluginArtifactRepositories()) {
      this.pluginArtifactRepositories.add(new ArtifactRepositoryRef(repository.getId(), repository.getUrl()));
    }
  }

  public void setMavenProject() {
    setMavenProject(mavenProject);
  }

  /**
   * Returns project relative paths of resource directories
   */
  public IPath[] getResourceLocations() {
    return resourceLocations;
  }

  /**
   * Returns project relative paths of test resource directories
   */
  public IPath[] getTestResourceLocations() {
    return testResourceLocations;
  }

  public IPath[] getCompileSourceLocations() {
    return compileSourceLocations;
  }

  public IPath[] getTestCompileSourceLocations() {
    return testCompileSourceLocations;
  }

  /**
   * Returns project resource for given file system location or null the location is outside of project.
   * 
   * @param resourceLocation absolute file system location
   * @return IPath the full, absolute workspace path resourceLocation
   */
  public IPath getProjectRelativePath(String resourceLocation) {
    return MavenProjectUtils.getProjectRelativePath(getProject(), resourceLocation);
  }

  /**
   * Returns the full, absolute path of this project maven build output directory relative to the workspace or null if
   * maven build output directory cannot be determined or outside of the workspace.
   */
  public IPath getOutputLocation() {
    return outputLocation;
  }

  /**
   * Returns the full, absolute path of this project maven build test output directory relative to the workspace or null
   * if maven build output directory cannot be determined or outside of the workspace.
   */
  public IPath getTestOutputLocation() {
    return testOutputLocation;
  }

  public IPath getFullPath() {
    return getProject().getFullPath();
  }

  /**
   * Lazy load and cache MavenProject instance
   */
  public synchronized MavenProject getMavenProject(IProgressMonitor monitor) throws CoreException {
    if (mavenProject == null) {
      MavenExecutionResult result = manager.readProjectWithDependencies(pom, resolverConfiguration, //
          new MavenUpdateRequest(true /* offline */, false /* updateSnapshots */), monitor);
      mavenProject = result.getProject();
      if (mavenProject == null) {
        MultiStatus status = new MultiStatus(IMavenConstants.PLUGIN_ID, 0, "Could not read maven project", null);
        List<Exception> exceptions = result.getExceptions();
        for (Exception e : exceptions) {
          status.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, 0, e.getMessage(), e));
        }
        throw new CoreException(status);
      }
    }
    return mavenProject;
  }

  public synchronized MavenExecutionPlan getExecutionPlan(IProgressMonitor monitor) throws CoreException {
    if (executionPlan == null) {
      executionPlan = manager.calculateExecutionPlan(this, monitor);
    }
    return executionPlan;
  }

  public String getPackaging() {
    return packaging;
  }

  public IProject getProject() {
    return pom.getProject();
  }

  public IFile getPom() {
    return pom;
  }

  public File getPomFile() {
    return pomFile;
  }

  /**
   * Returns the full, absolute path of the given file relative to the workspace. Returns null if the file does not
   * exist or is not a member of this project.
   */
  public IPath getFullPath(File file) {
    return MavenProjectUtils.getFullPath(getProject(), file);
  }

  /**
   * Visits trough Maven project artifacts and modules
   * 
   * @param visitor a project visitor used to visit Maven project
   * @param flags flags to specify visiting behavior. See {@link IMavenProjectVisitor#LOAD},
   *          {@link IMavenProjectVisitor#NESTED_MODULES}.
   */
  public void accept(IMavenProjectVisitor visitor, int flags) throws CoreException {
    acceptImpl(visitor, flags, null);
  }

  public void accept(IMavenProjectVisitor2 visitor, int flags, IProgressMonitor monitor) throws CoreException {
    acceptImpl(visitor, flags, monitor);
  }

  private void acceptImpl(IMavenProjectVisitor visitor, int flags, IProgressMonitor monitor) throws CoreException {
    if(visitor.visit(this)) {
      if (visitor instanceof IMavenProjectVisitor2 && monitor != null) {
        MavenProject mavenProject = ((flags & IMavenProjectVisitor.LOAD) > 0)? getMavenProject(monitor): getMavenProject();
        if (mavenProject != null) {
          for(Artifact artifact : mavenProject.getArtifacts()) {
            ((IMavenProjectVisitor2) visitor).visit(this, artifact);
          }
        }
      }

      if((flags & IMavenProjectVisitor.FORCE_MODULES) > 0 //
          || ((flags & IMavenProjectVisitor.NESTED_MODULES) > 0 //
              && resolverConfiguration.shouldIncludeModules())) {
        IFile pom = getPom();
        for(String moduleName : getMavenProjectModules()) {
          IFile modulePom = pom.getParent().getFile(new Path(moduleName).append(IMavenConstants.POM_FILE_NAME));
          IMavenProjectFacade moduleFacade = manager.create(modulePom, false, null);
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

  public List<String> getMavenProjectModules() {
    return modules;
  }
  
  public Set<ArtifactRef> getMavenProjectArtifacts() {
    return artifacts;
  }

  public ResolverConfiguration getResolverConfiguration() {
    return resolverConfiguration;
  }

  public void setResolverConfiguration(ResolverConfiguration configuration) {
    resolverConfiguration = configuration;
  }

  /**
   * @return true if maven project needs to be re-read from disk  
   */
  public boolean isStale() {
    IProject project = getProject();
    int i = 0;
    for(IPath path : MavenProjectManagerImpl.METADATA_PATH) {
      if (timestamp[i] != getModificationStamp(project.getFile(path))) {
        return true;
      }
      i++;
    }
    return timestamp[timestamp.length - 1] != getModificationStamp(pom);
  }

  private void updateTimestamp() {
    IProject project = getProject();
    int i = 0;
    for(IPath path : MavenProjectManagerImpl.METADATA_PATH) {
      timestamp[i] = getModificationStamp(project.getFile(path)); 
      i++;
    }
    timestamp[timestamp.length - 1] = getModificationStamp(pom);
  }

  private static long getModificationStamp(IFile file) {
    /*
     * this implementation misses update in the following scenario
     * 
     * 1. two files, A and B, with different content were created with same localTimeStamp
     * 2. original A was deleted and B moved to A
     * 
     * See also https://bugs.eclipse.org/bugs/show_bug.cgi?id=160728
     */
    return file.getLocalTimeStamp() + file.getModificationStamp();
  }

  public ArtifactKey getArtifactKey() {
    return artifactKey;
  }

  public MavenProject getMavenProject() {
    return mavenProject;
  }

  public synchronized void setSessionProperty(String key, Object value) {
    if (sessionProperties == null) {
      sessionProperties = new HashMap<String, Object>();
    }
    if (value != null) {
      sessionProperties.put(key, value);
    } else {
      sessionProperties.remove(key);
    }
  }

  public synchronized Object getSessionProperty(String key) {
    return sessionProperties != null? sessionProperties.get(key): null;
  }

  public Set<ArtifactRepositoryRef> getArtifactRepositoryRefs() {
    return artifactRepositories;
  }

  public Set<ArtifactRepositoryRef> getPluginArtifactRepositoryRefs() {
    return pluginArtifactRepositories;
  }
}
