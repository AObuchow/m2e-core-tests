/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.launch;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.embedder.MavenEmbedder;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourcePathComputer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.embedder.IMavenLauncherConfiguration;
import org.maven.ide.eclipse.embedder.MavenEmbedderManager;
import org.maven.ide.eclipse.embedder.MavenRuntime;
import org.maven.ide.eclipse.project.IMavenProjectFacade;


/**
 * Computes Maven launch configuration default source lookup path. 
 * 
 * Default source lookup includes Maven core libraries only. 
 * It does not (and cannot) include entries for any Maven plugins 
 * which are loaded dynamically at runtime. 
 * 
 * @author Eugene Kuleshov
 */
public class MavenSourcePathComputer implements ISourcePathComputer {

  private MavenEmbedderManager embedderManager;

  public MavenSourcePathComputer() {
    embedderManager = MavenPlugin.getDefault().getMavenEmbedderManager();
  }

  public String getId() {
    return "org.maven.ide.eclipse.launching.MavenSourceComputer";
  }

  public ISourceContainer[] computeSourceContainers(ILaunchConfiguration configuration, IProgressMonitor monitor)
      throws CoreException {
    final List<IRuntimeClasspathEntry> entries = new ArrayList<IRuntimeClasspathEntry>();

    IRuntimeClasspathEntry jreEntry = JavaRuntime.computeJREEntry(configuration);
    if(jreEntry != null) {
      entries.add(jreEntry);
    }

    MavenRuntime runtime = MavenLaunchUtils.getMavenRuntime(configuration);
    IMavenLauncherConfiguration collector = new IMavenLauncherConfiguration() {
      public void addArchiveEntry(String entry) throws CoreException {
        addArchiveRuntimeClasspathEntry(entries, entry);
      }
      public void addProjectEntry(IMavenProjectFacade facade) {
        IJavaProject javaProject = JavaCore.create(facade.getProject());
        if(javaProject != null) {
          entries.add(JavaRuntime.newDefaultProjectClasspathEntry(javaProject));
        }
      }
      public void addRealm(String world) {
      }
      public void setMainType(String type, String world) {
      }
    };

    collector.addArchiveEntry(MavenLaunchUtils.getCliResolver());
    MavenLaunchUtils.addUserComponents(configuration, collector);
    runtime.createLauncherConfiguration(collector, monitor);

    IRuntimeClasspathEntry[] resolved = JavaRuntime.resolveSourceLookupPath( //
        entries.toArray(new IRuntimeClasspathEntry[entries.size()]), configuration);
    return JavaRuntime.getSourceContainers(resolved);
  }

  protected void addArchiveRuntimeClasspathEntry(List<IRuntimeClasspathEntry> entries, String entryPath) throws CoreException {
    File entryFile = new File(entryPath);
    
    if (!entryFile.exists()) {
      return;
    }
    
    if (entryFile.isDirectory()) {
      DirectoryScanner ds = new DirectoryScanner();
      ds.setBasedir(entryFile);
      ds.setIncludes(new String[] {
          "META-INF/maven/*/*/pom.properties",
      });
      ds.scan();
      String[] files = ds.getIncludedFiles();
      for (String file : files) {
        try {
          BufferedInputStream is = new BufferedInputStream(new FileInputStream(new File(entryFile, file)));
          try {
            addArchiveRuntimeClasspathEntry(entries, entryPath, is);
          } finally {
            is.close();
          }
        } catch (IOException e) {
          // ignore it
        }

      }
    } else {
      try {
        JarFile jar = new JarFile(entryFile);
        try {
          Enumeration<JarEntry> zes = jar.entries();
          while (zes.hasMoreElements()) {
            JarEntry ze = zes.nextElement();
            String name = ze.getName();
            if (!ze.isDirectory() && name.startsWith("META-INF/maven/") && name.endsWith("pom.properties")) {
              addArchiveRuntimeClasspathEntry(entries, entryPath, jar.getInputStream(ze));
            }
          }
        } finally {
          jar.close();
        }
      } catch (IOException e) {
        // ignore it
      }
    }
    
  }
  
  private void addArchiveRuntimeClasspathEntry(List<IRuntimeClasspathEntry> entries, String entryPath, InputStream is) throws IOException, CoreException {
    Properties p = new Properties();
    p.load(is);

    String groupId = p.getProperty("groupId");
    String artifactId = p.getProperty("artifactId");
    String version = p.getProperty("version");

    File sourcesJar = getSourcesJar(groupId, artifactId, version);
    if (sourcesJar != null) {
      IRuntimeClasspathEntry entry = null; 
      entry = JavaRuntime.newArchiveRuntimeClasspathEntry(Path.fromOSString(entryPath));
      entry.setSourceAttachmentPath(Path.fromOSString(sourcesJar.getAbsolutePath()));
      entries.add(entry);
    }
  }

  private File getSourcesJar(String groupId, String artifactId, String version) throws CoreException {
    if (groupId != null && artifactId != null && version != null) {
      MavenEmbedder embedder = embedderManager.getWorkspaceEmbedder();
      ArtifactRepository localRepository = embedder.getLocalRepository();
      ArtifactRepositoryLayout localLayout = localRepository.getLayout();

      Artifact artifact = embedder.createArtifactWithClassifier(groupId, artifactId, version, "jar", "sources");
      File file = new File(localRepository.getBasedir(), localLayout.pathOf(artifact));
      
      if (file.exists() && file.canRead()) {
        return file;
      }
    }

    return null;
  }

}
