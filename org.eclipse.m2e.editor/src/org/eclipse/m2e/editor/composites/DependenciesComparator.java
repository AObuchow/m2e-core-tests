package org.eclipse.m2e.editor.composites;

import java.util.Comparator;

import org.eclipse.m2e.model.edit.pom.Dependency;

public class DependenciesComparator implements Comparator<Object> {
  
  private boolean sortByGroups = true;
  
  public int compare(Object o1, Object o2) {
    String[] gav1;
    String[] gav2;
    
    if (o1 instanceof Dependency) {
      gav1 = toGAV((Dependency) o1);
    } else {
      gav1 = toGAV((org.apache.maven.model.Dependency) o1);
    }
    
    if (o2 instanceof Dependency) {
      gav2 = toGAV((Dependency) o2);
    } else {
      gav2 = toGAV((org.apache.maven.model.Dependency) o2);
    }
    
    return compareGAVs(gav1, gav2);
  }
  
  protected String[] toGAV(Dependency dep) {
    String[] gav = new String[3];
    gav[0] = dep.getGroupId();
    gav[1] = dep.getArtifactId();
    gav[2] = dep.getVersion();
    return gav;
  }
  
  protected String[] toGAV(org.apache.maven.model.Dependency dep) {
    String[] gav = new String[3];
    gav[0] = dep.getGroupId();
    gav[1] = dep.getArtifactId();
    gav[2] = dep.getVersion();
    return gav;
  }
  
  protected int compareGAVs(String[] gav1, String[] gav2) {
    
    String g1 = gav1[0] == null ? "" : gav1[0]; //$NON-NLS-1$
    String g2 = gav2[0] == null ? "" : gav2[0]; //$NON-NLS-1$
    
    String a1 = gav1[1] == null ? "" : gav1[1]; //$NON-NLS-1$
    String a2 = gav2[1] == null ? "" : gav2[1]; //$NON-NLS-1$
    
    String v1 = gav1[2] == null ? "" : gav1[2]; //$NON-NLS-1$
    String v2 = gav2[2] == null ? "" : gav2[2]; //$NON-NLS-1$

    return compareDependencies(g1, a1, v1, g2, a2, v2);
  }
  
  protected int compareDependencies(String group1, String artifact1, String version1, 
      String group2, String artifact2, String version2) {
    int comp = 0;
    if (sortByGroups && (comp = group1.compareTo(group2)) != 0) {
      return comp;
    }
    if ((comp = artifact1.compareTo(artifact2)) != 0) {
      return comp;
    }

    return version1.compareTo(version2);
  }

  /**
   * Set this to false to ignore groupIDs while sorting
   * @param sortByGroups
   */
  public void setSortByGroups(boolean sortByGroups) {
    this.sortByGroups = sortByGroups;
  }
}