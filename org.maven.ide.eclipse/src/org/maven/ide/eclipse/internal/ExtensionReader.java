/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.osgi.framework.Bundle;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.index.IndexInfo;
import org.maven.ide.eclipse.internal.index.IndexInfoWriter;
import org.maven.ide.eclipse.scm.ScmHandler;
import org.maven.ide.eclipse.scm.ScmHandlerFactory;


/**
 * ExtensionReader
 * 
 * @author Eugene Kuleshov
 */
public class ExtensionReader {

  public static final String EXTENSION_INDEXES = "org.maven.ide.eclipse.indexes";

  public static final String EXTENSION_SCM_HANDLERS = "org.maven.ide.eclipse.scmHandlers";

  private static final String ELEMENT_INDEX = "index";

  private static final String ATTR_INDEX_ID = "indexId";

  private static final String ATTR_INDEX_ARCHIVE = "archive";

  private static final String ATTR_REPOSITORY_URL = "repositoryUrl";

  private static final String ATTR_UPDATE_URL = "updateUrl";

  private static final String ATTR_IS_SHORT = "isShort";

  private static final String ELEMENT_SCM_HANDLER = "handler";

  /**
   * @param configFile previously saved indexes configuration
   * @return collection of {@link IndexInfo} loaded from given config
   */
  public static Collection readIndexInfoConfig(File configFile) {
    if(configFile != null && configFile.exists()) {
      FileInputStream is = null;
      try {
        is = new FileInputStream(configFile);
        IndexInfoWriter writer = new IndexInfoWriter();
        return writer.readIndexInfo(is);
      } catch(IOException ex) {
        MavenPlugin.log("Unable to read index configuration", ex);
      } finally {
        if(is != null) {
          try {
            is.close();
          } catch(IOException ex) {
            MavenPlugin.log("Unable to close index config stream", ex);
          }
        }
      }
    }

    return Collections.EMPTY_LIST;
  }

  /**
   * @param configFile previously saved indexes configuration
   * @return collection of {@link IndexInfo} from the extension points
   */
  public static Collection readIndexInfoExtensions() {
    ArrayList indexes = new ArrayList();

    IExtensionRegistry registry = Platform.getExtensionRegistry();
    IExtensionPoint indexesExtensionPoint = registry.getExtensionPoint(EXTENSION_INDEXES);
    if(indexesExtensionPoint != null) {
      IExtension[] indexesExtensions = indexesExtensionPoint.getExtensions();
      for(int i = 0; i < indexesExtensions.length; i++ ) {
        IExtension extension = indexesExtensions[i];
        IContributor contributor = extension.getContributor();
        IConfigurationElement[] elements = extension.getConfigurationElements();
        for(int j = 0; j < elements.length; j++ ) {
          if(elements[j].getName().equals(ELEMENT_INDEX)) {
            indexes.add(readIndexElement(elements[j], contributor));
          }
        }
      }
    }

    return indexes;
  }

  private static IndexInfo readIndexElement(IConfigurationElement element, IContributor contributor) {
    String indexId = element.getAttribute(ATTR_INDEX_ID);
    String repositoryUrl = element.getAttribute(ATTR_REPOSITORY_URL);
    String indexUpdateUrl = element.getAttribute(ATTR_UPDATE_URL);
    boolean isShort = Boolean.valueOf(element.getAttribute(ATTR_IS_SHORT)).booleanValue();

    IndexInfo indexInfo = new IndexInfo(indexId, null, repositoryUrl, IndexInfo.Type.REMOTE, isShort);
    indexInfo.setIndexUpdateUrl(indexUpdateUrl);

    String archive = element.getAttribute(ATTR_INDEX_ARCHIVE);
    if(archive != null) {
      Bundle[] bundles = Platform.getBundles(contributor.getName(), null);
      URL archiveUrl = null;
      for(int i = 0; i < bundles.length && archiveUrl == null; i++ ) {
        Bundle bundle = bundles[i];
        archiveUrl = bundle.getEntry(archive);
        indexInfo.setArchiveUrl(archiveUrl);
      }
      if(archiveUrl == null) {
        MavenPlugin.log("Unable to find index archive " + archive + " in " + contributor.getName(), null);
      }
    }

    return indexInfo;
  }

  /**
   * 
   */
  public static void readScmHandlerExtensions() {
    IExtensionRegistry registry = Platform.getExtensionRegistry();
    IExtensionPoint scmHandlersExtensionPoint = registry.getExtensionPoint(EXTENSION_SCM_HANDLERS);
    if(scmHandlersExtensionPoint != null) {
      IExtension[] scmHandlersExtensions = scmHandlersExtensionPoint.getExtensions();
      for(int i = 0; i < scmHandlersExtensions.length; i++ ) {
        IExtension extension = scmHandlersExtensions[i];
        // IContributor contributor = extension.getContributor();
        IConfigurationElement[] elements = extension.getConfigurationElements();
        for(int j = 0; j < elements.length; j++ ) {
          IConfigurationElement element = elements[j];
          if(element.getName().equals(ELEMENT_SCM_HANDLER)) {
            try {
              ScmHandler handler = (ScmHandler) element.createExecutableExtension(ScmHandler.ATTR_SCM_HANDLER_CLASS);
              ScmHandlerFactory.addScmHandler(handler);
            } catch(CoreException ex) {
              MavenPlugin.log(ex);
            }
          }
        }
      }
    }

  }

}
