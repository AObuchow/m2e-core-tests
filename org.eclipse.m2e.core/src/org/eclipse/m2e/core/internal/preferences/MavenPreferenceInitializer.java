/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.core.internal.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import org.apache.maven.cli.MavenCli;

import org.eclipse.m2e.core.MavenPlugin;


/**
 * Maven preferences initializer.
 * 
 * @author Eugene Kuleshov
 */
public class MavenPreferenceInitializer extends AbstractPreferenceInitializer {

  /*
   * (non-Javadoc)
   * 
   * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
   */
  public void initializeDefaultPreferences() {
    IPreferenceStore store = MavenPlugin.getDefault().getPreferenceStore();

    store.setDefault(MavenPreferenceConstants.P_USER_SETTINGS_FILE, //
        MavenCli.DEFAULT_USER_SETTINGS_FILE.getAbsolutePath());
    
    store.setDefault(MavenPreferenceConstants.P_GLOBAL_SETTINGS_FILE, ""); //$NON-NLS-1$

    store.setDefault(MavenPreferenceConstants.P_DEBUG_OUTPUT, false);

    store.setDefault(MavenPreferenceConstants.P_OFFLINE, false);

    store.setDefault(MavenPreferenceConstants.P_DOWNLOAD_SOURCES, false);
    store.setDefault(MavenPreferenceConstants.P_DOWNLOAD_JAVADOC, false);

    store.setDefault(MavenPreferenceConstants.P_GOAL_ON_UPDATE, MavenPreferenceConstants.DEFAULT_GOALS_ON_UPDATE);
    store.setDefault(MavenPreferenceConstants.P_GOAL_ON_IMPORT, MavenPreferenceConstants.DEFAULT_GOALS_ON_IMPORT);

    // store.setDefault( MavenPreferenceConstants.P_GLOBAL_CHECKSUM_POLICY, ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);
    // store.setDefault( MavenPreferenceConstants.P_UPDATE_SNAPSHOTS, false);
    // store.setDefault( MavenPreferenceConstants.P_CHECK_LATEST_PLUGIN_VERSION, false);

    store.setDefault(MavenPreferenceConstants.P_OUTPUT_FOLDER, "target-eclipse"); //$NON-NLS-1$

    store.setDefault(MavenPreferenceConstants.P_RUNTIMES, ""); //$NON-NLS-1$
    store.setDefault(MavenPreferenceConstants.P_DEFAULT_RUNTIME, ""); //$NON-NLS-1$

    store.setDefault(MavenPreferenceConstants.P_UPDATE_INDEXES, true);
    store.setDefault(MavenPreferenceConstants.P_UPDATE_PROJECTS, false);
    
    store.setDefault(MavenPreferenceConstants.P_HIDE_FOLDERS_OF_NESTED_PROJECTS, false);
    
    store.setDefault(MavenPreferenceConstants.P_SHOW_CONSOLE_ON_ERR, true);
    store.setDefault(MavenPreferenceConstants.P_SHOW_CONSOLE_ON_OUTPUT, false);
  }

}
