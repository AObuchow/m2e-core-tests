/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.editor.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.util.IOUtil;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.embedder.IMaven;

class PomTemplateContextUtil {

  public static final PomTemplateContextUtil INSTANCE = new PomTemplateContextUtil();

  private final Map<String, PluginDescriptor> descriptors = new HashMap<String, PluginDescriptor>();

  public PluginDescriptor getPluginDescriptor(String groupId, String artifactId, String version) {
    String name = groupId + ":" + artifactId + ":" + version;
    PluginDescriptor descriptor = descriptors.get(name);
    if(descriptor!=null) {
      return descriptor;
    }
    
    MavenPlugin plugin = MavenPlugin.getDefault();
    MavenConsole console = plugin.getConsole();
    try {
      IMaven embedder = MavenPlugin.lookup(IMaven.class);

      List<ArtifactRepository> repositories = embedder.getArtifactRepositories();

      Artifact artifact = embedder.resolve(groupId, artifactId, version, "maven-plugin", null,  repositories, null);

      File file = artifact.getFile();
      if(file == null) {
        String msg = "Can't resolve plugin " + name;
        console.logError(msg);
      } else {
        InputStream is = null;
        ZipFile zf = null;
        try {
          zf = new ZipFile(file);
          ZipEntry entry = zf.getEntry("META-INF/maven/plugin.xml");
          if(entry != null) {
            is = zf.getInputStream(entry);
            PluginDescriptorBuilder builder = new PluginDescriptorBuilder();
            descriptor = builder.build(new InputStreamReader(is));
            descriptors.put(name, descriptor);
            return descriptor;
          }

        } catch(Exception ex) {
          String msg = "Can't read configuration for " + name;
          console.logError(msg);
          MavenLogger.log(msg, ex);

        } finally {
          IOUtil.close(is);
          try {
            zf.close();
          } catch(IOException ex) {
            // ignore
          }
        }
      }

    } catch(CoreException ex) {
      IStatus status = ex.getStatus();
      if(status.getException() != null) {
        console.logError(status.getMessage() + "; " + status.getException().getMessage());
      } else {
        console.logError(status.getMessage());
      }
      MavenLogger.log(ex);
    }
    return null;
  }

}
