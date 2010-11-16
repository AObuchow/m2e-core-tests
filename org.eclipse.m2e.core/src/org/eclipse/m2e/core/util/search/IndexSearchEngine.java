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


package org.eclipse.m2e.core.util.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.maven.artifact.versioning.ComparableVersion;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.m2e.core.index.IIndex;
import org.eclipse.m2e.core.index.IndexManager;
import org.eclipse.m2e.core.index.IndexedArtifact;
import org.eclipse.m2e.core.index.IndexedArtifactFile;


/**
 * Search engine integrating {@link IndexManager} with POM XML editor.
 * 
 * @author Lukas Krecan
 * @author Eugene Kuleshov
 */
public class IndexSearchEngine implements SearchEngine {

  private final IIndex index;

  public IndexSearchEngine(IIndex index) {
    this.index = index;
  }

  public Collection<String> findArtifactIds(String groupId, String searchExpression, Packaging packaging,
      ArtifactInfo containingArtifact) {
    // TODO add support for implicit groupIds in plugin dependencies "org.apache.maven.plugins", ...
    try {
      TreeSet<String> ids = new TreeSet<String>();
      for(IndexedArtifact artifact : index.find(groupId, null, null, packaging.getText())) {
        ids.add(artifact.getArtifactId());
      }
      return subSet(ids, searchExpression);
    } catch(CoreException ex) {
      throw new SearchException(ex.getMessage(), ex.getStatus().getException());
    }
  }

  public Collection<String> findClassifiers(String groupId, String artifactId, String version, String prefix,
      Packaging packaging) {
    try {
      Collection<IndexedArtifact> values = index.find(groupId, artifactId, null, packaging.getText());
      if(values.isEmpty()) {
        return Collections.emptySet();
      }

      TreeSet<String> ids = new TreeSet<String>();
      Set<IndexedArtifactFile> files = values.iterator().next().getFiles();
      for(IndexedArtifactFile artifactFile : files) {
        if(artifactFile.classifier != null) {
          ids.add(artifactFile.classifier);
        }
      }
      return subSet(ids, prefix);
    } catch(CoreException ex) {
      throw new SearchException(ex.getMessage(), ex.getStatus().getException());
    }
  }

  public Collection<String> findGroupIds(String searchExpression, Packaging packaging, ArtifactInfo containingArtifact) {
    try {
      TreeSet<String> ids = new TreeSet<String>();

      String packagingStr = packaging == Packaging.ALL ? null : packaging.getText();

      for(IndexedArtifact artifact : index.find(searchExpression, null, null, packagingStr)) {
        ids.add(artifact.getGroupId());
      }
      return subSet(ids, searchExpression);
    } catch(CoreException ex) {
      throw new SearchException(ex.getMessage(), ex.getStatus().getException());
    }
  }

  public Collection<String> findTypes(String groupId, String artifactId, String version, String prefix,
      Packaging packaging) {
    try {
      Collection<IndexedArtifact> values = index.find(groupId, artifactId, null, packaging.getText());
      if(values.isEmpty()) {
        return Collections.emptySet();
      }

      TreeSet<String> ids = new TreeSet<String>();
      Set<IndexedArtifactFile> files = values.iterator().next().getFiles();
      for(IndexedArtifactFile artifactFile : files) {
        if(artifactFile.type != null) {
          ids.add(artifactFile.type);
        }
      }
      return subSet(ids, prefix);
    } catch(CoreException ex) {
      throw new SearchException(ex.getMessage(), ex.getStatus().getException());
    }
  }

  public Collection<String> findVersions(String groupId, String artifactId, String searchExpression, Packaging packaging) {
    try {
      Collection<IndexedArtifact> values = index.find(groupId, artifactId, null, packaging.getText());
      if(values.isEmpty()) {
        return Collections.emptySet();
      }

      TreeSet<String> ids = new TreeSet<String>();
      Set<IndexedArtifactFile> files = values.iterator().next().getFiles();
      for(IndexedArtifactFile artifactFile : files) {
        ids.add(artifactFile.version);
      }
      Collection<String> result = subSet(ids, searchExpression);

      // sort results according to o.a.m.artifact.versioning.ComparableVersion
      SortedSet<ComparableVersion> versions = new TreeSet<ComparableVersion>();
      for(String version : result) {
        versions.add(new ComparableVersion(version));
      }
      result = null; // not used any more
      List<String> sorted = new ArrayList<String>(versions.size());
      for(ComparableVersion version : versions) {
        sorted.add(version.toString());
      }
      versions = null; // not used any more
      Collections.reverse(sorted);
      return sorted;
    } catch(CoreException ex) {
      throw new SearchException(ex.getMessage(), ex.getStatus().getException());
    }
  }

  private Collection<String> subSet(TreeSet<String> ids, String searchExpression) {
    if(searchExpression == null || searchExpression.length() == 0) {
      return ids;
    }
    int n = searchExpression.length();
    return ids.subSet(searchExpression, //
        searchExpression.substring(0, n - 1) + ((char) (searchExpression.charAt(n - 1) + 1)));
  }

}
