
package org.eclipse.m2e.tests.configurators;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IClasspathEntryDescriptor;
import org.eclipse.m2e.jdt.IJavaProjectConfigurator;


public class MNGECLIPSE2313MarkAllRawClasspathEntriesConfigurator extends AbstractProjectConfigurator implements
    IJavaProjectConfigurator {

  public static final IClasspathAttribute ATTR = JavaCore.newClasspathAttribute(
      MNGECLIPSE2313MarkAllRawClasspathEntriesConfigurator.class.getName(), "bar");

  public MNGECLIPSE2313MarkAllRawClasspathEntriesConfigurator() {
  }

  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
  }

  public void configureClasspath(IMavenProjectFacade facade, IClasspathDescriptor classpath, IProgressMonitor monitor)
      throws CoreException {
  }

  public void configureRawClasspath(ProjectConfigurationRequest request, IClasspathDescriptor classpath,
      IProgressMonitor monitor) throws CoreException {

    for(IClasspathEntryDescriptor entry : classpath.getEntryDescriptors()) {
      entry.setClasspathAttribute(ATTR.getName(), ATTR.getValue());
    }

  }

}
