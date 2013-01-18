/*******************************************************************************
 * Copyright (c) 2013 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Red Hat, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.tests.conversion;

import org.eclipse.core.resources.IProject;


/**
 * TestSortProjectConversionParticipant, accepts projects which name ends with "-test-sort-participant"
 * 
 * @author Fred Bricon
 */
public class TestSortProjectConversionParticipant extends TestProjectConversionParticipant {

  public boolean accept(IProject project) {
    return project != null && project.getName().endsWith("-test-sort-participant");
  }

}
