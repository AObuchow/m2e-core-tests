/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.actions;

import org.eclipse.jface.action.Action;

import org.eclipse.m2e.core.MavenImages;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.Messages;

public class MavenConsoleRemoveAction extends Action {

  public MavenConsoleRemoveAction() {
    setToolTipText(Messages.MavenConsoleRemoveAction_tooltip);
    setImageDescriptor(MavenImages.CLOSE);
  }
  
  public void run() {
    MavenPlugin.getDefault().getConsole().closeConsole();
  }

}
