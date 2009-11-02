/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.project;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IWorkingSet;

import org.apache.maven.model.Model;

import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.core.Messages;


/**
 * Project import configuration bean.
 */
public class ProjectImportConfiguration {

  private static final String GROUP_ID = "\\[groupId\\]";

  private static final String ARTIFACT_ID = "\\[artifactId\\]";

  private static final String VERSION = "\\[version\\]";

  /** resolver configuration bean */
  private ResolverConfiguration resolverConfiguration;

  /** the project name template */
  private String projectNameTemplate = "";
  
  private IWorkingSet[] workingSets;

  /** Creates a new configuration. */
  public ProjectImportConfiguration(ResolverConfiguration resolverConfiguration) {
    this.resolverConfiguration = resolverConfiguration;
  }

  /** Creates a new configuration. */
  public ProjectImportConfiguration() {
    this(new ResolverConfiguration());
  }

  /** Returns the resolver configuration bean. */
  public ResolverConfiguration getResolverConfiguration() {
    return resolverConfiguration;
  }

  /** Sets the project name template. */
  public void setProjectNameTemplate(String projectNameTemplate) {
    this.projectNameTemplate = projectNameTemplate;
  }

  /** Returns the project name template. */
  public String getProjectNameTemplate() {
    return projectNameTemplate;
  }

  /** @deprecated UI aspects will be refactored out of core import logic */
  public void setWorkingSet(IWorkingSet workingSet) {
    this.workingSets = workingSet == null ? null : new IWorkingSet[]{workingSet};
  }

  /** @deprecated UI aspects will be refactored out of core import logic */
  public void setWorkingSets(IWorkingSet[] workingSets) {
    this.workingSets = workingSets;
  }
  
  /** @deprecated UI aspects will be refactored out of core import logic */
  public IWorkingSet[] getWorkingSets() {
    return this.workingSets;
  }

  /** 
   * Calculates the project name for the given model.
   * 
   * @deprecated This method does not take into account MavenProjectInfo.basedirRename
   */
  public String getProjectName(Model model) {
    // XXX should use resolved MavenProject or Model
    if(projectNameTemplate.length() == 0) {
      return model.getArtifactId();
    }

    String artifactId = model.getArtifactId();
    String groupId = model.getGroupId();
    if(groupId == null && model.getParent() != null) {
      groupId = model.getParent().getGroupId();
    }
    String version = model.getVersion();
    if(version == null && model.getParent() != null) {
      version = model.getParent().getVersion();
    }

    // XXX needs MavenProjectManager update to resolve groupId and version
    return projectNameTemplate.replaceAll(GROUP_ID, groupId).replaceAll(ARTIFACT_ID, artifactId).replaceAll(VERSION,
        version == null ? "" : version);
  }

  /**
   * @deprecated This method does not take into account MavenProjectInfo.basedirRename.
   *    Use IMavenProjectImportResult#getProject instead
   */
  public IProject getProject(IWorkspaceRoot root, Model model) {
    return root.getProject(getProjectName(model));
  }

  /**
   * @deprecated business logic does not belong to a value object
   */
  public IStatus validateProjectName(Model model) {
    String projectName = getProjectName(model); 
    IWorkspace workspace = ResourcesPlugin.getWorkspace();

    // check if the project name is valid
    IStatus nameStatus = workspace.validateName(projectName, IResource.PROJECT);
    if(!nameStatus.isOK()) {
      return nameStatus;
    }

    // check if project already exists
    if(workspace.getRoot().getProject(projectName).exists()) {
      return new Status( IStatus.ERROR, IMavenConstants.PLUGIN_ID, 0, Messages.getString("wizard.project.page.project.validator.projectExists",projectName), null);
    }
    
    return Status.OK_STATUS;
  }
}
