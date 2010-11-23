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

package org.eclipse.m2e.editor.xml;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator;
import org.eclipse.ui.IMarkerResolutionGenerator2;

import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenLogger;


/**
 * MavenMarkerResolutionGenerator
 * 
 * @author dyocum
 */
public class MavenMarkerResolutionGenerator implements IMarkerResolutionGenerator, IMarkerResolutionGenerator2 {

  /* (non-Javadoc)
   * @see org.eclipse.ui.IMarkerResolutionGenerator#getResolutions(org.eclipse.core.resources.IMarker)
   */
  public IMarkerResolution[] getResolutions(IMarker marker) {
    String type;
    try {
      type = marker.getType();
    } catch(CoreException e) {
      MavenLogger.log(e);
      type = ""; //$NON-NLS-1$
    }
    if(IMavenConstants.MARKER_HINT_ID.equals(type)) {
      String hint = marker.getAttribute(IMavenConstants.MARKER_ATTR_EDITOR_HINT, ""); //$NON-NLS-1$
      //only provide a quickfix for the schema marker
      if("schema".equals(hint)) {
        return new IMarkerResolution[] {new XMLSchemaMarkerResolution()};
      }
      if ("parent_version".equals(hint)) {
        return new IMarkerResolution[] {new PomQuickAssistProcessor.IdPartRemovalProposal(marker, true) };
      }
      if ("parent_groupid".equals(hint)) {
        return new IMarkerResolution[] {new PomQuickAssistProcessor.IdPartRemovalProposal(marker, false) };
      }
    }
    return new IMarkerResolution[0];
  }

  public boolean hasResolutions(IMarker marker) {
    String type;
    try {
      type = marker.getType();
    } catch(CoreException e) {
      MavenLogger.log(e);
      type = ""; //$NON-NLS-1$
    }
    return IMavenConstants.MARKER_HINT_ID.equals(type);
  }

}
