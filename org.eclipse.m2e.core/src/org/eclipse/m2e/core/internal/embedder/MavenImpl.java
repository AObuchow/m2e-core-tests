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

package org.eclipse.m2e.core.internal.embedder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.version.DefaultPluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.project.DuplicateProjectException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.DefaultSettingsProblem;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.building.SettingsProblem.Severity;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.settings.io.SettingsWriter;
import org.apache.maven.wagon.proxy.ProxyInfo;

import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.transfer.ArtifactNotFoundException;
import org.sonatype.aether.transfer.TransferListener;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenConsole;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.embedder.ILocalRepositoryListener;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.embedder.IMavenConfigurationChangeListener;
import org.eclipse.m2e.core.embedder.ISettingsChangeListener;
import org.eclipse.m2e.core.embedder.MavenConfigurationChangeEvent;
import org.eclipse.m2e.core.internal.preferences.MavenPreferenceConstants;


public class MavenImpl implements IMaven, IMavenConfigurationChangeListener {

  /**
   * Id of maven core class realm
   */
  public static final String MAVEN_CORE_REALM_ID = "plexus.core";


  private DefaultPlexusContainer plexus;

  private final IMavenConfiguration mavenConfiguration;

  private final ConverterLookup converterLookup = new DefaultConverterLookup();

  private final MavenConsole console;

  private final ArrayList<ISettingsChangeListener> settingsListeners = new ArrayList<ISettingsChangeListener>();

  private final ArrayList<ILocalRepositoryListener> localRepositoryListeners = new ArrayList<ILocalRepositoryListener>();

  public MavenImpl(IMavenConfiguration mavenConfiguration, MavenConsole console) {
    this.console = console;
    this.mavenConfiguration = mavenConfiguration;
    mavenConfiguration.addConfigurationChangeListener(this);
  }

  public MavenExecutionRequest createExecutionRequest(IProgressMonitor monitor) throws CoreException {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    if(mavenConfiguration.getGlobalSettingsFile() != null) {
      request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
    }
    if(mavenConfiguration.getUserSettingsFile() != null) {
      request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
    }

    try {
      lookup(MavenExecutionRequestPopulator.class).populateFromSettings(request, getSettings());
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not create maven execution request", ex));
    }

    ArtifactRepository localRepository = getLocalRepository();
    request.setLocalRepository(localRepository);
    request.setLocalRepositoryPath(localRepository.getBasedir());
    request.setOffline(mavenConfiguration.isOffline());

    // logging
    request.setTransferListener(createArtifactTransferListener(monitor));

    request.getUserProperties().put("m2e.version", MavenPlugin.getVersion());

    request.setCacheNotFound(true);
    request.setCacheTransferError(true);

    // the right way to disable snapshot update
    // request.setUpdateSnapshots(false);
    return request;
  }

  public String getLocalRepositoryPath() {
    String path = null;
    try {
      Settings settings = getSettings();
      path = settings.getLocalRepository();
    } catch(CoreException ex) {
      // fall through
    }
    if(path == null) {
      path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
    }
    return path;
  }

  public MavenExecutionResult execute(MavenExecutionRequest request, IProgressMonitor monitor) {
    // XXX is there a way to set per-request log level?

    MavenExecutionResult result;
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      result = lookup(Maven.class).execute(request);
    } catch(MavenExecutionRequestPopulationException ex) {
      result = new DefaultMavenExecutionResult();
      result.addException(ex);
    } catch(Exception e) {
      result = new DefaultMavenExecutionResult();
      result.addException(e);
    }
    return result;
  }

  public MavenSession createSession(MavenExecutionRequest request, MavenProject project) {
    RepositorySystemSession repoSession = createRepositorySession(request);
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    MavenSession mavenSession = new MavenSession(plexus, repoSession, request, result);
    if(project != null) {
      mavenSession.setProjects(Collections.singletonList(project));
    }
    return mavenSession;
  }

  private RepositorySystemSession createRepositorySession(MavenExecutionRequest request) {
    try {
      return ((DefaultMaven) lookup(Maven.class)).newRepositorySession(request);
    } catch(CoreException ex) {
      MavenLogger.log(ex);
      throw new IllegalStateException("Could not look up Maven embedder", ex);
    }
  }

  public void execute(MavenSession session, MojoExecution execution, IProgressMonitor monitor) {
    try {
      lookup(BuildPluginManager.class).executeMojo(session, execution);
    } catch(Exception ex) {
      session.getResult().addException(ex);
    }
  }

  public <T> T getConfiguredMojo(MavenSession session, MojoExecution mojoExecution, Class<T> clazz)
      throws CoreException {
    try {
      MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
      // getPluginRealm creates plugin realm and populates pluginDescriptor.classRealm field 
      lookup(BuildPluginManager.class).getPluginRealm(session, mojoDescriptor.getPluginDescriptor());
      return clazz.cast(lookup(MavenPluginManager.class).getConfiguredMojo(Mojo.class, session, mojoExecution));
    } catch(PluginContainerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get configured mojo for " + mojoExecution, ex));
    } catch(PluginConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get configured mojo for " + mojoExecution, ex));
    } catch(ClassCastException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get configured mojo for " + mojoExecution, ex));
    } catch(PluginResolutionException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get configured mojo for " + mojoExecution, ex));
    } catch(PluginManagerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get configured mojo for " + mojoExecution, ex));
    }
  }

  public void releaseMojo(Object mojo, MojoExecution mojoExecution) throws CoreException {
    lookup(MavenPluginManager.class).releaseMojo(mojo, mojoExecution);
  }

  public MavenExecutionPlan calculateExecutionPlan(MavenExecutionRequest request, MavenProject project,
      IProgressMonitor monitor) throws CoreException {
    MavenSession session = createSession(request, project);
    try {
      List<String> goals = request.getGoals();
      return lookup(LifecycleExecutor.class).calculateExecutionPlan(session, goals.toArray(new String[goals.size()]));
    } catch(Exception ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not calculate build plan", ex));
    }
  }
  
  public ArtifactRepository getLocalRepository() throws CoreException {
    try {
      String localRepositoryPath = getLocalRepositoryPath();
      if(localRepositoryPath != null) {
        return lookup(RepositorySystem.class).createLocalRepository(new File(localRepositoryPath));
      }
      return lookup(RepositorySystem.class).createLocalRepository(RepositorySystem.defaultUserLocalRepository);
    } catch(InvalidRepositoryException ex) {
      // can't happen
      throw new IllegalStateException(ex);
    }
  }

  public Settings getSettings() throws CoreException {
    // MUST NOT use createRequest!

    // TODO: Can't that delegate to buildSettings()?
    SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
    if(mavenConfiguration.getGlobalSettingsFile() != null) {
      request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
    }
    if(mavenConfiguration.getUserSettingsFile() != null) {
      request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
    }
    try {
      return lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
    } catch(SettingsBuildingException ex) {
      String msg = "Could not read settings.xml, assuming default values";
      MavenPlugin.getDefault().getConsole().logError(msg);
      MavenLogger.log(msg, ex);
      /*
       * NOTE: This method provides input for various other core functions, just bailing out would make m2e highly
       * unusuable. Instead, we fail gracefully and just ignore the broken settings, using defaults.
       */
      return new Settings();
    }
  }

  public Settings buildSettings(String globalSettings, String userSettings) throws CoreException {
    SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
    request.setGlobalSettingsFile(globalSettings != null ? new File(globalSettings) : null);
    request.setUserSettingsFile(userSettings != null ? new File(userSettings) : null);
    try {
      return lookup(SettingsBuilder.class).build(request).getEffectiveSettings();
    } catch(SettingsBuildingException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
          ex));
    }
  }

  public void writeSettings(Settings settings, OutputStream out) throws CoreException {
    try {
      lookup(SettingsWriter.class).write(out, null, settings);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not write settings.xml",
          ex));
    }
  }

  public List<SettingsProblem> validateSettings(String settings) {
    List<SettingsProblem> problems = new ArrayList<SettingsProblem>();
    if(settings != null) {
      File settingsFile = new File(settings);
      if(settingsFile.canRead()) {
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile(settingsFile);
        try {
          lookup(SettingsBuilder.class).build(request);
        } catch(SettingsBuildingException ex) {
          problems.addAll(ex.getProblems());
        } catch(CoreException ex) {
          problems.add(new DefaultSettingsProblem(ex.getMessage(), Severity.FATAL, settings, -1, -1, ex));
        }
      } else {
        problems.add(new DefaultSettingsProblem("Can not read settings file " + settings,
            SettingsProblem.Severity.ERROR, settings, -1, -1, null));
      }
    }

    return problems;
  }

  public void reloadSettings() throws CoreException {
    // TODO do something more meaningful
    Settings settings = getSettings();
    for(ISettingsChangeListener listener : settingsListeners) {
      try {
        listener.settingsChanged(settings);
      } catch(CoreException e) {
        MavenLogger.log(e);
      }
    }
  }

  public Server decryptPassword(Server server) throws CoreException {
    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
    SettingsDecryptionResult result = lookup(SettingsDecrypter.class).decrypt(request);
    for(SettingsProblem problem : result.getProblems()) {
      MavenLogger.log(new Status(IStatus.WARNING, IMavenConstants.PLUGIN_ID, -1, problem.getMessage(), problem
          .getException()));
    }
    return result.getServer();
  }

  public void mavenConfigutationChange(MavenConfigurationChangeEvent event) throws CoreException {
    if(MavenConfigurationChangeEvent.P_USER_SETTINGS_FILE.equals(event.getKey())
        || MavenPreferenceConstants.P_GLOBAL_SETTINGS_FILE.equals(event.getKey())) {
      reloadSettings();
    }
  }

  public Model readModel(InputStream in) throws CoreException {
    try {
      return lookup(ModelReader.class).read(in, null);
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read pom.xml", e));
    }
  }

  public Model readModel(File pomFile) throws CoreException {
    try {
      BufferedInputStream is = new BufferedInputStream(new FileInputStream(pomFile));
      try {
        return readModel(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read pom.xml", e));
    }
  }

  public void writeModel(Model model, OutputStream out) throws CoreException {
    try {
      lookup(ModelWriter.class).write(out, null, model);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not write pom.xml", ex));
    }
  }

  public MavenProject readProject(File pomFile, IProgressMonitor monitor) throws CoreException {
    try {
      MavenExecutionRequest request = createExecutionRequest(monitor);
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      configuration.setRepositorySession(createRepositorySession(request));
      return lookup(ProjectBuilder.class).build(pomFile, configuration).getProject();
    } catch(ProjectBuildingException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read maven project",
          ex));
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read maven project",
          ex));
    }
  }

  public MavenExecutionResult readProject(MavenExecutionRequest request, IProgressMonitor monitor) throws CoreException {
    File pomFile = request.getPom();
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      configuration.setRepositorySession(createRepositorySession(request));
      ProjectBuildingResult projectBuildingResult = lookup(ProjectBuilder.class).build(pomFile, configuration);
      result.setProject(projectBuildingResult.getProject());
      result.setDependencyResolutionResult(projectBuildingResult.getDependencyResolutionResult());
    } catch(ProjectBuildingException ex) {
      //don't add the exception here. this should come out as a build marker, not fill
      //the error logs with msgs
      return result.addException(ex);
    } catch(MavenExecutionRequestPopulationException ex) {
      return result.addException(ex);
    }
    return result;
  }

  /**
   * Makes MavenProject instances returned by #readProject methods suitable for caching and reuse with other
   * MavenSession instances.<br/>
   * Do note that MavenProject.getParentProject() cannot be used for detached MavenProject instances. Use
   * #resolveParentProject to resolve parent project instance.
   */
  public void detachFromSession(MavenProject project) throws CoreException {
    try {
      // TODO remove reflection when we have embedder 3.0.1 or better
      Field f = project.getClass().getDeclaredField("projectBuilderConfiguration");
      f.setAccessible(true);
      ProjectBuildingRequest request;
      request = (ProjectBuildingRequest) f.get(project);
      request.setRepositorySession(lookup(ContextRepositorySystemSession.class));
    } catch(NoSuchFieldException ex) {
      MavenLogger.log(ex.getMessage(), ex);
    } catch(IllegalAccessException ex) {
      MavenLogger.log(ex.getMessage(), ex);
    }
  }

  public MavenProject resolveParentProject(MavenExecutionRequest request, MavenProject child, IProgressMonitor monitor)
      throws CoreException {
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    configuration.setRepositorySession(createRepositorySession(request));

    try {
      configuration.setRemoteRepositories(child.getRemoteArtifactRepositories());

      File parentFile = child.getParentFile();
      if(parentFile != null) {
        return lookup(ProjectBuilder.class).build(parentFile, configuration).getProject();
      }

      Artifact parentArtifact = child.getParentArtifact();
      if(parentArtifact != null) {
        return lookup(ProjectBuilder.class).build(parentArtifact, configuration).getProject();
      }
    } catch(ProjectBuildingException ex) {
      MavenLogger.log("Could not read parent project", ex);
    }

    return null;
  }

  public Artifact resolve(String groupId, String artifactId, String version, String type, String classifier,
      List<ArtifactRepository> remoteRepositories, IProgressMonitor monitor) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);

    if(remoteRepositories == null) {
      try {
        remoteRepositories = getArtifactRepositories();
      } catch(CoreException e) {
        // we've tried
        remoteRepositories = Collections.emptyList();
      }
    }

    ArtifactRepository localRepository = getLocalRepository();

    org.sonatype.aether.RepositorySystem repoSystem = lookup(org.sonatype.aether.RepositorySystem.class);

    MavenRepositorySystemSession session = new MavenRepositorySystemSession();
    session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(new LocalRepository(localRepository
        .getBasedir())));
    session.setTransferListener(createArtifactTransferListener(monitor));

    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(RepositoryUtils.toArtifact(artifact));
    request.setRepositories(RepositoryUtils.toRepos(remoteRepositories));

    ArtifactResult result;
    try {
      result = repoSystem.resolveArtifact(session, request);
    } catch(ArtifactResolutionException ex) {
      result = ex.getResults().get(0);
    }

    setLastUpdated(localRepository, remoteRepositories, artifact);

    if(result.isResolved()) {
      artifact.selectVersion(result.getArtifact().getVersion());
      artifact.setFile(result.getArtifact().getFile());
      artifact.setResolved(true);
    } else {
      ArrayList<IStatus> members = new ArrayList<IStatus>();
      for(Exception e : result.getExceptions()) {
        if(!(e instanceof ArtifactNotFoundException)) {
          members.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, e.getMessage(), e));
        }
      }
      if(members.isEmpty()) {
        members.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Missing " + artifact, null));
      }
      IStatus[] newMembers = members.toArray(new IStatus[members.size()]);
      throw new CoreException(new MultiStatus(IMavenConstants.PLUGIN_ID, -1, newMembers, "Could not resolve artifact",
          null));
    }

    return artifact;
  }

  public String getArtifactPath(ArtifactRepository repository, String groupId, String artifactId, String version,
      String type, String classifier) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);
    return repository.pathOf(artifact);
  }

  private void setLastUpdated(ArtifactRepository localRepository, List<ArtifactRepository> remoteRepositories,
      Artifact artifact) throws CoreException {

    Properties lastUpdated = loadLastUpdated(localRepository, artifact);

    String timestamp = Long.toString(System.currentTimeMillis());

    for(ArtifactRepository repository : remoteRepositories) {
      lastUpdated.setProperty(getLastUpdatedKey(repository, artifact), timestamp);
    }

    File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
    try {
      lastUpdatedFile.getParentFile().mkdirs();
      BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(lastUpdatedFile));
      try {
        lastUpdated.store(os, null);
      } finally {
        IOUtil.close(os);
      }
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not write artifact lastUpdated status", ex));
    }
  }

  /**
   * This is a temporary implementation that only works for artifacts resolved using #resolve.
   */
  public boolean isUnavailable(String groupId, String artifactId, String version, String type, String classifier,
      List<ArtifactRepository> remoteRepositories) throws CoreException {
    Artifact artifact = lookup(RepositorySystem.class).createArtifactWithClassifier(groupId, artifactId, version, type,
        classifier);

    ArtifactRepository localRepository = getLocalRepository();

    File artifactFile = new File(localRepository.getBasedir(), localRepository.pathOf(artifact));

    if(artifactFile.canRead()) {
      // artifact is available locally
      return false;
    }

    if(remoteRepositories == null || remoteRepositories.isEmpty()) {
      // no remote repositories
      return true;
    }

    // now is the hard part
    Properties lastUpdated = loadLastUpdated(localRepository, artifact);

    for(ArtifactRepository repository : remoteRepositories) {
      String timestamp = lastUpdated.getProperty(getLastUpdatedKey(repository, artifact));
      if(timestamp == null) {
        // availability of the artifact from this repository has not been checked yet 
        return false;
      }
    }

    // artifact is not available locally and all remote repositories have been checked in the past
    return true;
  }

  private String getLastUpdatedKey(ArtifactRepository repository, Artifact artifact) {
    StringBuilder key = new StringBuilder();

    // repository part
    key.append(repository.getId());
    if(repository.getAuthentication() != null) {
      key.append('|').append(repository.getAuthentication().getUsername());
    }
    key.append('|').append(repository.getUrl());

    // artifact part
    key.append('|').append(artifact.getClassifier());

    return key.toString();
  }

  private Properties loadLastUpdated(ArtifactRepository localRepository, Artifact artifact) throws CoreException {
    Properties lastUpdated = new Properties();
    File lastUpdatedFile = getLastUpdatedFile(localRepository, artifact);
    try {
      BufferedInputStream is = new BufferedInputStream(new FileInputStream(lastUpdatedFile));
      try {
        lastUpdated.load(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(FileNotFoundException ex) {
      // that's okay
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not read artifact lastUpdated status", ex));
    }
    return lastUpdated;
  }

  private File getLastUpdatedFile(ArtifactRepository localRepository, Artifact artifact) {
    return new File(localRepository.getBasedir(), basePathOf(localRepository, artifact) + "/"
        + "m2e-lastUpdated.properties");
  }

  private static final char PATH_SEPARATOR = '/';

  private static final char GROUP_SEPARATOR = '.';

  private String basePathOf(ArtifactRepository repository, Artifact artifact) {
    StringBuilder path = new StringBuilder(128);

    path.append(formatAsDirectory(artifact.getGroupId())).append(PATH_SEPARATOR);
    path.append(artifact.getArtifactId()).append(PATH_SEPARATOR);
    path.append(artifact.getBaseVersion()).append(PATH_SEPARATOR);

    return path.toString();
  }

  private String formatAsDirectory(String directory) {
    return directory.replace(GROUP_SEPARATOR, PATH_SEPARATOR);
  }

  public <T> T getMojoParameterValue(MavenSession session, MojoExecution mojoExecution, String parameter,
      Class<T> asType) throws CoreException {

    try {
      MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

      ClassRealm pluginRealm = lookup(BuildPluginManager.class).getPluginRealm(session,
          mojoDescriptor.getPluginDescriptor());

      ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

      ConfigurationConverter typeConverter = converterLookup.lookupConverterForType(asType);

      Xpp3Dom dom = mojoExecution.getConfiguration();

      if(dom == null) {
        return null;
      }

      PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(dom);

      PlexusConfiguration configuration = pomConfiguration.getChild(parameter);

      if(configuration == null) {
        return null;
      }

      Object value = typeConverter.fromConfiguration(converterLookup, configuration, asType,
          mojoDescriptor.getImplementationClass(), pluginRealm, expressionEvaluator, null);
      return asType.cast(value);
    } catch(ComponentConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    } catch(PluginManagerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    } catch(PluginResolutionException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    }
  }

  public <T> T getMojoParameterValue(String parameter, Class<T> type, MavenSession session, Plugin plugin,
      ConfigurationContainer configuration, String goal) throws CoreException {
    Xpp3Dom config = (Xpp3Dom) configuration.getConfiguration();
    config = (config != null) ? config.getChild(parameter) : null;

    PlexusConfiguration paramConfig = null;

    if(config == null) {
      MojoDescriptor mojoDescriptor;

      try {
        mojoDescriptor = lookup(BuildPluginManager.class).getMojoDescriptor(plugin, goal,
            session.getCurrentProject().getRemotePluginRepositories(), session.getRepositorySession());
      } catch(PluginNotFoundException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not get mojo execution paramater value", ex));
      } catch(PluginResolutionException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not get mojo execution paramater value", ex));
      } catch(PluginDescriptorParsingException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not get mojo execution paramater value", ex));
      } catch(MojoNotFoundException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not get mojo execution paramater value", ex));
      } catch(InvalidPluginDescriptorException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not get mojo execution paramater value", ex));
      }

      PlexusConfiguration defaultConfig = mojoDescriptor.getMojoConfiguration();
      if(defaultConfig != null) {
        paramConfig = defaultConfig.getChild(parameter, false);
      }
    } else {
      paramConfig = new XmlPlexusConfiguration(config);
    }

    if(paramConfig == null) {
      return null;
    }

    try {
      MojoExecution mojoExecution = new MojoExecution(plugin, goal, "default");

      ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

      ConfigurationConverter typeConverter = converterLookup.lookupConverterForType(type);

      Object value = typeConverter.fromConfiguration(converterLookup, paramConfig, type, Object.class,
          plexus.getContainerRealm(), expressionEvaluator, null);
      return type.cast(value);
    } catch(ComponentConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    } catch(ClassCastException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    }
  }

  @SuppressWarnings("deprecation")
  public void xxxRemoveExtensionsRealm(MavenProject project) {
    ClassRealm realm = project.getClassRealm();
    if(realm != null && realm != plexus.getContainerRealm()) {
      ClassWorld world = ((MutablePlexusContainer) plexus).getClassWorld();
      try {
        world.disposeRealm(realm.getId());
      } catch(NoSuchRealmException ex) {
        MavenLogger.log("Could not dispose of project extensions class realm", ex);
      }
    }
  }

  public ArtifactRepository createArtifactRepository(String id, String url) throws CoreException {
    Repository repository = new Repository();
    repository.setId(id);
    repository.setUrl(url);
    repository.setLayout("default");

    ArtifactRepository repo;
    try {
      repo = lookup(RepositorySystem.class).buildArtifactRepository(repository);
      ArrayList<ArtifactRepository> repos = new ArrayList<ArtifactRepository>(Arrays.asList(repo));
      injectSettings(repos);
    } catch(InvalidRepositoryException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not create artifact repository", ex));
    }
    return repo;
  }

  public List<ArtifactRepository> getArtifactRepositories() throws CoreException {
    return getArtifactRepositories(true);
  }

  public List<ArtifactRepository> getArtifactRepositories(boolean injectSettings) throws CoreException {
    ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
    for(Profile profile : getActiveProfiles()) {
      addArtifactRepositories(repositories, profile.getRepositories());
    }

    addDefaultRepository(repositories);

    if(injectSettings) {
      injectSettings(repositories);
    }

    return removeDuplicateRepositories(repositories);
  }

  private List<ArtifactRepository> removeDuplicateRepositories(ArrayList<ArtifactRepository> repositories) {
    ArrayList<ArtifactRepository> result = new ArrayList<ArtifactRepository>();

    HashSet<String> keys = new HashSet<String>();
    for(ArtifactRepository repository : repositories) {
      StringBuilder key = new StringBuilder();
      if(repository.getId() != null) {
        key.append(repository.getId());
      }
      key.append(':').append(repository.getUrl()).append(':');
      if(repository.getAuthentication() != null && repository.getAuthentication().getUsername() != null) {
        key.append(repository.getAuthentication().getUsername());
      }
      if(keys.add(key.toString())) {
        result.add(repository);
      }
    }
    return result;
  }

  private void injectSettings(ArrayList<ArtifactRepository> repositories) throws CoreException {
    Settings settings = getSettings();

    lookup(RepositorySystem.class).injectMirror(repositories, getMirrors());
    lookup(RepositorySystem.class).injectProxy(repositories, settings.getProxies());
    lookup(RepositorySystem.class).injectAuthentication(repositories, settings.getServers());
  }

  private void addDefaultRepository(ArrayList<ArtifactRepository> repositories) throws CoreException {
    for(ArtifactRepository repository : repositories) {
      if(RepositorySystem.DEFAULT_REMOTE_REPO_ID.equals(repository.getId())) {
        return;
      }
    }
    try {
      repositories.add(0, lookup(RepositorySystem.class).createDefaultRemoteRepository());
    } catch(InvalidRepositoryException ex) {
      MavenLogger.log("Unexpected exception", ex);
    }
  }

  private void addArtifactRepositories(ArrayList<ArtifactRepository> artifactRepositories, List<Repository> repositories)
      throws CoreException {
    for(Repository repository : repositories) {
      try {
        ArtifactRepository artifactRepository = lookup(RepositorySystem.class).buildArtifactRepository(repository);
        artifactRepositories.add(artifactRepository);
      } catch(InvalidRepositoryException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
            ex));
      }
    }
  }

  private List<Profile> getActiveProfiles() throws CoreException {
    Settings settings = getSettings();
    List<String> activeProfilesIds = settings.getActiveProfiles();
    ArrayList<Profile> activeProfiles = new ArrayList<Profile>();
    for(org.apache.maven.settings.Profile settingsProfile : settings.getProfiles()) {
      if((settingsProfile.getActivation() != null && settingsProfile.getActivation().isActiveByDefault())
          || activeProfilesIds.contains(settingsProfile.getId())) {
        Profile profile = SettingsUtils.convertFromSettingsProfile(settingsProfile);
        activeProfiles.add(profile);
      }
    }
    return activeProfiles;
  }

  public List<ArtifactRepository> getPluginArtifactRepositories() throws CoreException {
    return getPluginArtifactRepositories(true);
  }

  public List<ArtifactRepository> getPluginArtifactRepositories(boolean injectSettings) throws CoreException {
    ArrayList<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
    for(Profile profile : getActiveProfiles()) {
      addArtifactRepositories(repositories, profile.getPluginRepositories());
    }
    addDefaultRepository(repositories);

    if(injectSettings) {
      injectSettings(repositories);
    }

    return removeDuplicateRepositories(repositories);
  }

  public Mirror getMirror(ArtifactRepository repo) throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(new NullProgressMonitor());
    populateDefaults(request);
    return lookup(RepositorySystem.class).getMirror(repo, request.getMirrors());
  };

  public void populateDefaults(MavenExecutionRequest request) throws CoreException {
    try {
      lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
    } catch(MavenExecutionRequestPopulationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not read Maven configuration", ex));
    }
  }

  public List<Mirror> getMirrors() throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(null);
    populateDefaults(request);
    return request.getMirrors();
  }

  public void addSettingsChangeListener(ISettingsChangeListener listener) {
    settingsListeners.add(listener);
  }

  public void removeSettingsChangeListener(ISettingsChangeListener listener) {
    settingsListeners.remove(listener);
  }

  public void addLocalRepositoryListener(ILocalRepositoryListener listener) {
    localRepositoryListeners.add(listener);
  }

  public void removeLocalRepositoryListener(ILocalRepositoryListener listener) {
    localRepositoryListeners.remove(listener);
  }

  public List<ILocalRepositoryListener> getLocalRepositoryListeners() {
    return localRepositoryListeners;
  }

  @SuppressWarnings("deprecation")
  public WagonTransferListenerAdapter createTransferListener(IProgressMonitor monitor) {
    return new WagonTransferListenerAdapter(this, monitor, console);
  }

  public TransferListener createArtifactTransferListener(IProgressMonitor monitor) {
    return new ArtifactTransferListenerAdapter(this, monitor, console);
  }

  public synchronized PlexusContainer getPlexusContainer() throws CoreException {
    if(plexus == null) {
      try {
        plexus = newPlexusContainer();
        plexus.setLoggerManager(new EclipseLoggerManager(console, mavenConfiguration));
      } catch(PlexusContainerException ex) {
        throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
            "Could not initialize embedded maven runtime", ex));
      }
    }
    return plexus;
  }

  public ProxyInfo getProxyInfo(String protocol) throws CoreException {
    Settings settings = getSettings();

    for(Proxy proxy : settings.getProxies()) {
      if(proxy.isActive() && protocol.equalsIgnoreCase(proxy.getProtocol())) {
        ProxyInfo proxyInfo = new ProxyInfo();
        proxyInfo.setType(proxy.getProtocol());
        proxyInfo.setHost(proxy.getHost());
        proxyInfo.setPort(proxy.getPort());
        proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
        proxyInfo.setUserName(proxy.getUsername());
        proxyInfo.setPassword(proxy.getPassword());
        return proxyInfo;
      }
    }

    return null;
  }

  public List<MavenProject> getSortedProjects(List<MavenProject> projects) throws CoreException {
    try {
      ProjectSorter rm = new ProjectSorter(projects);
      return rm.getSortedProjects();
    } catch(CycleDetectedException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, "unable to sort projects", ex));
    } catch(DuplicateProjectException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, "unable to sort projects", ex));
    }
  }

  public String resolvePluginVersion(String groupId, String artifactId, MavenSession session) throws CoreException {
    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    PluginVersionRequest request = new DefaultPluginVersionRequest(plugin, session);
    try {
      return lookup(PluginVersionResolver.class).resolve(request).getVersion();
    } catch(PluginVersionResolutionException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, ex.getMessage(), ex));
    }
  }

  private <T> T lookup(Class<T> clazz) throws CoreException {
    try {
      return getPlexusContainer().lookup(clazz);
    } catch(ComponentLookupException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not lookup required component", ex));
    }
  }

  private static DefaultPlexusContainer newPlexusContainer() throws PlexusContainerException {
    ContainerConfiguration mavenCoreCC = new DefaultContainerConfiguration().setClassWorld(
        new ClassWorld(MAVEN_CORE_REALM_ID, ClassWorld.class.getClassLoader())).setName(
        "mavenCore");

    mavenCoreCC.setAutoWiring(true);

    return new DefaultPlexusContainer(mavenCoreCC, new ExtensionModule());
  }

  public synchronized void disposeContainer() {
    if(plexus != null) {
      plexus.dispose();
    }
  }

  public ClassLoader getProjectRealm(MavenProject project) {
    ClassLoader classLoader = project.getClassRealm();
    if(classLoader == null) {
      classLoader = plexus.getContainerRealm();
    }
    return classLoader;
  }

}
