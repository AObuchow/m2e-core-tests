/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.editor.xml;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.editor.xml.internal.Messages;

/**
 * @author Eugene Kuleshov
 */
public class MvnImages {

  // object images
  
  public static final Image IMG_JAR = createImage("jar_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_JARS = createImage("jars_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_REPOSITORY = createImage("repository_obj.gif");  //$NON-NLS-1$
  
  public static final Image IMG_PLUGIN = createImage("plugin_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_PLUGINS = createImage("plugins_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_EXECUTION = createImage("execution_obj.gif"); //$NON-NLS-1$
  
  public static final Image IMG_GOAL = createImage("goal_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_FILTER = createImage("filter_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_RESOURCE = createImage("resource_obj.gif"); //$NON-NLS-1$
  
  public static final Image IMG_RESOURCES = createImage("resources_obj.gif");  //$NON-NLS-1$

  public static final Image IMG_INCLUDE = createImage("include_obj.gif");  //$NON-NLS-1$
  
  public static final Image IMG_EXCLUDE = createImage("exclude_obj.gif");  //$NON-NLS-1$
  
  public static final Image IMG_PERSON = createImage("person_obj.gif"); //$NON-NLS-1$
  
  public static final Image IMG_ROLE = createImage("role_obj.gif"); //$NON-NLS-1$
  
  public static final Image IMG_PROPERTY = createImage("property_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_PROPERTIES = createImage("properties_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_REPORT = createImage("report_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_PROFILE = createImage("profile_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_PROFILES = createImage("profiles_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_PARAMETER = createImage("parameter_obj.gif"); //$NON-NLS-1$

  public static final Image IMG_BUILD = createImage("build_obj.gif"); //$NON-NLS-1$
  
  public static final Image IMG_ELEMENT = createImage("element_obj.gif"); //$NON-NLS-1$
  

  private static ImageDescriptor create(String key) {
    try {
      ImageDescriptor imageDescriptor = createDescriptor(key);
      ImageRegistry imageRegistry = getImageRegistry();
      if(imageRegistry!=null) {
        imageRegistry.put(key, imageDescriptor);
      }
      return imageDescriptor;
    } catch (Exception ex) {
      MavenLogger.log(key, ex);
      return null;
    }
  }

  private static Image createImage(String key) {
    create(key);
    ImageRegistry imageRegistry = getImageRegistry();
    return imageRegistry==null ? null : imageRegistry.get(key);
  }

  private static ImageRegistry getImageRegistry() {
    MvnIndexPlugin plugin = MvnIndexPlugin.getDefault();
    return plugin==null ? null : plugin.getImageRegistry();
  }

  private static ImageDescriptor createDescriptor(String image) {
    return AbstractUIPlugin.imageDescriptorFromPlugin(MvnIndexPlugin.PLUGIN_ID, "icons/" + image); //$NON-NLS-1$
  }
  
}
