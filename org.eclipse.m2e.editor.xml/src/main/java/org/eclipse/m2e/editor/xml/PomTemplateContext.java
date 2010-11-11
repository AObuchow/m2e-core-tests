/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.m2e.editor.xml;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.StringUtils;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.osgi.util.NLS;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.editor.xml.internal.Messages;
import org.eclipse.m2e.editor.xml.search.ArtifactInfo;
import org.eclipse.m2e.editor.xml.search.Packaging;
import org.eclipse.m2e.editor.xml.search.SearchEngine;


/**
 * Context types.
 * 
 * @author Lukas Krecan
 * @author Eugene Kuleshov
 */
public enum PomTemplateContext {
  
  UNKNOWN("unknown"), // //$NON-NLS-1$
  
  DOCUMENT("#document"), // //$NON-NLS-1$
  
  PROJECT("project"), // //$NON-NLS-1$
  
  PARENT("parent"), // //$NON-NLS-1$
  
  MODULES("modules"), // //$NON-NLS-1$

  PROPERTIES("properties"), // //$NON-NLS-1$
  
  DEPENDENCIES("dependencies"), // //$NON-NLS-1$
  
  EXCLUSIONS("exclusions"), // //$NON-NLS-1$
  
  PLUGINS("plugins"), // //$NON-NLS-1$

  PLUGIN("plugin"), // //$NON-NLS-1$

  EXECUTIONS("executions"), // //$NON-NLS-1$
  
  PROFILES("profiles"), // //$NON-NLS-1$
  
  REPOSITORIES("repositories"), // //$NON-NLS-1$

  CONFIGURATION("configuration") { //$NON-NLS-1$

    @Override
    protected void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      if("execution".equals(node.getParentNode().getNodeName()) //$NON-NLS-1$
          || "reportSet".equals(node.getParentNode().getNodeName())) { //$NON-NLS-1$
        node = node.getParentNode().getParentNode();
      }
      String groupId = getGroupId(node);
      if(groupId==null) {
        groupId = "org.apache.maven.plugins";  // TODO support other default groups //$NON-NLS-1$
      }
      String artifactId = getArtifactId(node);
      String version = extractVersion(project, getVersion(node), groupId, artifactId, EXTRACT_STRATEGY_PLUGIN | EXTRACT_STRATEGY_SEARCH);
      if (version == null) {
        return;
      }
      
      PluginDescriptor descriptor = PomTemplateContextUtil.INSTANCE.getPluginDescriptor(groupId, artifactId, version);
      if(descriptor!=null) {
        List<MojoDescriptor> mojos = descriptor.getMojos();
        HashSet<String> params = new HashSet<String>();
        for(MojoDescriptor mojo : mojos) {
          List<Parameter> parameters = (List<Parameter>) mojo.getParameters();
          for(Parameter parameter : parameters) {
            boolean editable = parameter.isEditable();
            if(editable) {
              String name = parameter.getName();
              if(!params.contains(name) && name.startsWith(prefix)) {
                params.add(name);
                
                String text = NLS.bind(Messages.PomTemplateContext_param, parameter.isRequired(), parameter.getType());
                
                String expression = parameter.getExpression();
                if(expression!=null) {
                  text += NLS.bind(Messages.PomTemplateContext_param_expr, expression);
                }
                
                String defaultValue = parameter.getDefaultValue();
                if(defaultValue!=null) {
                  text += NLS.bind(Messages.PomTemplateContext_param_def, defaultValue);
                }
                
                String desc = parameter.getDescription().trim();
                text += desc.startsWith("<p>") ? desc : "<br>" + desc; //$NON-NLS-1$ //$NON-NLS-2$
                
                proposals.add(new Template(name, text, getContextTypeId(), //
                    "<" + name + ">${cursor}</" + name + ">", false)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
              }
            }
          }
        }
      }
    }
  },
  
  GROUP_ID("groupId") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      String contextTypeId = getContextTypeId();
      for(String groupId : getSearchEngine(project).findGroupIds(prefix, getPackaging(node), getContainingArtifact(node))) {
        add(proposals, contextTypeId, groupId);
      }
    }
  },

  ARTIFACT_ID("artifactId") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      String groupId = getGroupId(node);
      if(groupId != null) {
        String contextTypeId = getContextTypeId();
        for(String artifactId : getSearchEngine(project).findArtifactIds(groupId, prefix, getPackaging(node),
            getContainingArtifact(node))) {
          add(proposals, contextTypeId, artifactId, groupId + ":" + artifactId);
        }
      }
    }
  },

  VERSION("version") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      String groupId = getGroupId(node);
      String artifactId = getArtifactId(node);
      if(groupId != null && artifactId != null) {
        String contextTypeId = getContextTypeId();
        for(String version : getSearchEngine(project).findVersions(groupId, artifactId, prefix, getPackaging(node))) {
          add(proposals, contextTypeId, version, groupId + ":" + artifactId + ":" + version);
        }
      }
      //mkleint: this concept that all versions out there are equal is questionable..
      if (project != null && "dependency".equals(node.getParentNode().getNodeName())) { //$NON-NLS-1$
        //see if we can complete the properties ending with .version
        
        IMavenProjectFacade mvnproject = MavenPlugin.getDefault().getMavenProjectManager().getProject(project);
        if(mvnproject != null) {
          MavenProject mvn = mvnproject.getMavenProject();
          if (mvn != null) {
            Properties props = mvn.getProperties();
            if (props != null) {
              String contextTypeId = getContextTypeId();
              List<String> keys = new ArrayList<String>();
              for (Object key : props.keySet()) {
                //only add the properties following the .version convention
                if (key.toString().endsWith(".version")) { //$NON-NLS-1$
                  keys.add(key.toString());
                }
              }
              Collections.sort(keys);
              for (String key : keys) {
                String expr = "${" + key + "}"; //$NON-NLS-1$ //$NON-NLS-2$
                proposals.add(new Template(expr, Messages.PomTemplateContext_expression_description, contextTypeId, "$" + expr, false)); //$NON-NLS-2$
              }
            }
          }
        }
      }
    }
  },

  CLASSIFIER("classifier") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      String groupId = getGroupId(node);
      String artifactId = getArtifactId(node);
      String version = getVersion(node);
      if(groupId != null && artifactId != null && version != null) {
        String contextTypeId = getContextTypeId();
        for(String classifier : getSearchEngine(project).findClassifiers(groupId, artifactId, version, prefix,
            getPackaging(node))) {
          add(proposals, contextTypeId, classifier, groupId + ":" + artifactId + ":" + version + ":" + classifier);
        }
      }
    }
  },

  TYPE("type") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) throws CoreException {
      String groupId = getGroupId(node);
      String artifactId = getArtifactId(node);
      String version = getVersion(node);
      String contextTypeId = getContextTypeId();
      if(groupId != null && artifactId != null && version != null) {
        for(String type : getSearchEngine(project).findTypes(groupId, artifactId, version, prefix, getPackaging(node))) {
          add(proposals, contextTypeId, type, groupId + ":" + artifactId + ":" + version + ":" + type);
        }
      }
    }
  },
  
  PACKAGING("packaging") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) {
      String contextTypeId = getContextTypeId();
      // TODO only show "pom" packaging in root section
      add(proposals, contextTypeId, "pom"); //$NON-NLS-1$
      add(proposals, contextTypeId, "jar"); //$NON-NLS-1$
      add(proposals, contextTypeId, "war"); //$NON-NLS-1$
      add(proposals, contextTypeId, "ear"); //$NON-NLS-1$
      add(proposals, contextTypeId, "ejb"); //$NON-NLS-1$
      add(proposals, contextTypeId, "eclipse-plugin"); //$NON-NLS-1$
      add(proposals, contextTypeId, "eclipse-feature"); //$NON-NLS-1$
      add(proposals, contextTypeId, "eclipse-update-site"); //$NON-NLS-1$
      add(proposals, contextTypeId, "maven-plugin"); //$NON-NLS-1$
      add(proposals, contextTypeId, "maven-archetype"); //$NON-NLS-1$
    }
  },
  
  SCOPE("scope") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) {
      String contextTypeId = getContextTypeId();
      add(proposals, contextTypeId, "compile"); //$NON-NLS-1$
      add(proposals, contextTypeId, "test"); //$NON-NLS-1$
      add(proposals, contextTypeId, "provided"); //$NON-NLS-1$
      add(proposals, contextTypeId, "runtime"); //$NON-NLS-1$
      add(proposals, contextTypeId, "system"); //$NON-NLS-1$
      // TODO only show "import" scope in <dependencyManagement>
      add(proposals, contextTypeId, "import"); //$NON-NLS-1$
    }    
  },
  
  SYSTEM_PATH("systemPath"), //$NON-NLS-1$
  
  PHASE("phase") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix) {
      String contextTypeId = getContextTypeId();
      // TODO the following list should be derived from the packaging handler (the actual lifecycle)
      
      // Clean Lifecycle
      add(proposals, contextTypeId, "pre-clean", Messages.PomTemplateContext_preclean); //$NON-NLS-1$
      add(proposals, contextTypeId, "clean", Messages.PomTemplateContext_clean); //$NON-NLS-1$
      add(proposals, contextTypeId, "post-clean", Messages.PomTemplateContext_postclean); //$NON-NLS-1$
      
      // Default Lifecycle
      add(proposals, contextTypeId, "validate", Messages.PomTemplateContext_validate); //$NON-NLS-1$
      add(proposals, contextTypeId, "generate-sources", Messages.PomTemplateContext_generatesources); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-sources", Messages.PomTemplateContext_processsources); //$NON-NLS-1$
      add(proposals, contextTypeId, "generate-resources", Messages.PomTemplateContext_generateresources); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-resources", Messages.PomTemplateContext_processresources); //$NON-NLS-1$
      add(proposals, contextTypeId, "compile", Messages.PomTemplateContext_compile); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-classes", Messages.PomTemplateContext_processclasses); //$NON-NLS-1$
      add(proposals, contextTypeId, "generate-test-sources", Messages.PomTemplateContext_generatetestsources); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-test-sources", Messages.PomTemplateContext_processtestsources); //$NON-NLS-1$
      add(proposals, contextTypeId, "generate-test-resources", Messages.PomTemplateContext_generatetestresources); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-test-resources", Messages.PomTemplateContext_processtestresources); //$NON-NLS-1$
      add(proposals, contextTypeId, "test-compile", Messages.PomTemplateContext_testcompile); //$NON-NLS-1$
      add(proposals, contextTypeId, "process-test-classes", Messages.PomTemplateContext_processtestclasses); //$NON-NLS-1$
      add(proposals, contextTypeId, "test", Messages.PomTemplateContext_test); //$NON-NLS-1$
      add(proposals, contextTypeId, "prepare-package", Messages.PomTemplateContext_preparepackage); //$NON-NLS-1$
      add(proposals, contextTypeId, "package", Messages.PomTemplateContext_package); //$NON-NLS-1$
      add(proposals, contextTypeId, "pre-integration-test", Messages.PomTemplateContext_preintegrationtest); //$NON-NLS-1$
      add(proposals, contextTypeId, "integration-test", Messages.PomTemplateContext_integrationtest); //$NON-NLS-1$
      add(proposals, contextTypeId, "post-integration-test", Messages.PomTemplateContext_postintegrationtest); //$NON-NLS-1$
      add(proposals, contextTypeId, "verify", Messages.PomTemplateContext_verify); //$NON-NLS-1$
      add(proposals, contextTypeId, "install", Messages.PomTemplateContext_install); //$NON-NLS-1$
      add(proposals, contextTypeId, "deploy", Messages.PomTemplateContext_deploy); //$NON-NLS-1$
      
      // Site Lifecycle
      add(proposals, contextTypeId, "pre-site", Messages.PomTemplateContext_presite); //$NON-NLS-1$
      add(proposals, contextTypeId, "site", Messages.PomTemplateContext_site); //$NON-NLS-1$
      add(proposals, contextTypeId, "post-site", Messages.PomTemplateContext_postsite); //$NON-NLS-1$
      add(proposals, contextTypeId, "site-deploy", Messages.PomTemplateContext_sitedeploy); //$NON-NLS-1$
    }
  },

  GOAL("goal") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix)  throws CoreException {
      if(!"goals".equals(node.getParentNode().getNodeName())) { //$NON-NLS-1$
        return;
      }
      node = node.getParentNode();
      if(!"execution".equals(node.getParentNode().getNodeName())) { //$NON-NLS-1$
        return;
      }
      node = node.getParentNode();
      if(!"executions".equals(node.getParentNode().getNodeName())) { //$NON-NLS-1$
        return;
      }
      node = node.getParentNode();

      String groupId = getGroupId(node);
      if(groupId==null) {
        groupId = "org.apache.maven.plugins"; //$NON-NLS-1$
      }
      String artifactId = getArtifactId(node);
            
      String version = extractVersion(project, getVersion(node), groupId, artifactId, EXTRACT_STRATEGY_PLUGIN | EXTRACT_STRATEGY_SEARCH);
      if(version==null) {
        return;
      }
      
      PluginDescriptor descriptor = PomTemplateContextUtil.INSTANCE.getPluginDescriptor(groupId, artifactId, version);
      if (descriptor != null) {
        List<MojoDescriptor> mojos = descriptor.getMojos();
        if (mojos != null) {
          String contextTypeId = getContextTypeId();
          for (MojoDescriptor mojo : mojos) {
            add(proposals, contextTypeId, mojo.getGoal(), mojo.getDescription());
          }
        }
      }
    }
  },

  MODULE("module") { //$NON-NLS-1$
    @Override
    public void addTemplates(IProject project, Collection<Template> proposals, Node node, String prefix)
        throws CoreException {
      if(project == null) {
        //shall not happen just double check.
        return;
      }
      File currentPom = new File(project.getLocationURI());
      File directory = currentPom;
      String path = prefix;
      boolean endingSlash = path.endsWith("/"); //$NON-NLS-1$
      String[] elems = StringUtils.split(path, "/"); //$NON-NLS-1$
      String lastElement = null;
      for(int i = 0; i < elems.length; i++ ) {
        if("..".equals(elems[i])) { //$NON-NLS-1$
          directory = directory != null ? directory.getParentFile() : null;
        } else if(i < elems.length - (endingSlash ? 0 : 1)) {
          directory = directory != null ? new File(directory, elems[i]) : null;
        } else {
          lastElement = elems[i];
        }
      }
      path = lastElement != null ? path.substring(0, path.length() - lastElement.length()) : path;
      if (directory != null && directory.exists() && directory.isDirectory()) {
        File[] offerings = directory.listFiles(new FileFilter() {
          public boolean accept(File pathname) {
            if (pathname.isDirectory()) {
              File pom = new File(pathname, "pom.xml"); //$NON-NLS-1$
              //TODO shall also handle polyglot maven :)
              return pom.exists() && pom.isFile();
            }
            return false;
          }
        });
        for (File candidate : offerings) {
          if(lastElement == null || candidate.getName().startsWith(lastElement)) {
            add(proposals, getContextTypeId(), path + candidate.getName(), NLS.bind(Messages.PomTemplateContext_candidate, candidate));
          }
        }
      }
    }
  };

  private static final String PREFIX = MvnIndexPlugin.PLUGIN_ID + ".templates.contextType."; //$NON-NLS-1$

  private final String nodeName;

  private PomTemplateContext(String nodeName) {
    this.nodeName = nodeName;
  }

  /**
   * Return templates depending on the context type.
   */
  public Template[] getTemplates(IProject project, Node node, String prefix) {
    Collection<Template> templates = new ArrayList<Template>();
    try {
      addTemplates(project, templates, node, prefix);
    } catch (CoreException e) {
      MavenLogger.log(e);
    }
    
    TemplateStore store = MvnIndexPlugin.getDefault().getTemplateStore();
    if(store != null) {
      templates.addAll(Arrays.asList(store.getTemplates(getContextTypeId())));
    }
    
    return templates.toArray(new Template[templates.size()]);
  }
  
  protected void addTemplates(IProject project, Collection<Template> templates, Node currentNode, String prefix) throws CoreException {
  }

  protected String getNodeName() {
    return nodeName;
  }
  
  public String getContextTypeId() {
    return PREFIX + nodeName;
  }

  public static PomTemplateContext fromId(String contextTypeId) {
    for(PomTemplateContext context : values()) {
      if(context.getContextTypeId().equals(contextTypeId)) {
        return context;
      }
    }
    return UNKNOWN;
  }

  public static PomTemplateContext fromNodeName(String idSuffix) {
    for(PomTemplateContext context : values()) {
      if(context.getNodeName().equals(idSuffix)) {
        return context;
      }
    }
    return UNKNOWN;
  }

  private static SearchEngine getSearchEngine(IProject project) throws CoreException {
    return MvnIndexPlugin.getDefault().getSearchEngine(project);
  }
  
  
  ///
  
  /**
   * Returns containing artifactInfo for exclusions. Otherwise returns null.
   */
  protected ArtifactInfo getContainingArtifact(Node currentNode) {
    if(isExclusion(currentNode)) {
      Node node = currentNode.getParentNode().getParentNode();
      return getArtifactInfo(node);
    }
    return null;
  }

  /**
   * Returns artifact info from siblings of given node.
   */
  private ArtifactInfo getArtifactInfo(Node node) {
    return new ArtifactInfo(getGroupId(node), getArtifactId(node), getVersion(node), //
        getSiblingTextValue(node, "classifier"), getSiblingTextValue(node, "type")); //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Returns required packaging.
   */
  protected Packaging getPackaging(Node currentNode) {
    if(isPlugin(currentNode)) {
      return Packaging.PLUGIN;
    } else if(isParent(currentNode)) {
      return Packaging.POM;
    }
    return Packaging.ALL;
  }

  /**
   * Returns true if user is editing plugin dependency.
   */
  private boolean isPlugin(Node currentNode) {
    return "plugin".equals(currentNode.getParentNode().getNodeName()); //$NON-NLS-1$
  }
  
  /**
   * Returns true if user is editing plugin dependency exclusion.
   */
  private boolean isExclusion(Node currentNode) {
    return "exclusion".equals(currentNode.getParentNode().getNodeName()); //$NON-NLS-1$
  }
  
  /**
   * Returns true if user is editing parent dependency.
   */
  private boolean isParent(Node currentNode) {
    return "parent".equals(currentNode.getParentNode().getNodeName()); //$NON-NLS-1$
  }
  
  protected String getGroupId(Node currentNode) {
    return getSiblingTextValue(currentNode, "groupId"); //$NON-NLS-1$
  }

  /**
   * 
   * @param project
   * @param version
   * @param groupId
   * @param artifactId
   * @return
   * @throws CoreException
   */
  
  static int EXTRACT_STRATEGY_PLUGIN = 1;
  static int EXTRACT_STRATEGY_DEPENDENCY = 2;
  static int EXTRACT_STRATEGY_SEARCH = 4;
  
  static String extractVersion(IProject project, String version, String groupId, String artifactId, int strategy)
      throws CoreException {
    //interpolate the version found to get rid of expressions 
    version = simpleInterpolate(project, version);
    
    if (version==null) {
      Packaging pack = Packaging.ALL; 
      if ( (strategy & EXTRACT_STRATEGY_PLUGIN) != 0) {
        version = searchPM(project, groupId, artifactId);
        pack = Packaging.PLUGIN;
      }
      if ( (strategy & EXTRACT_STRATEGY_DEPENDENCY) != 0) {
        version = searchDM(project, groupId, artifactId);
      }
      if (version == null && (strategy & EXTRACT_STRATEGY_SEARCH) != 0) {      
        Collection<String> versions = getSearchEngine(project).findVersions(groupId, artifactId, "", pack); //$NON-NLS-1$
        if(versions.isEmpty()) {
          return null;
        }
        version = versions.iterator().next();
      }
    }
    return version;
  }

  private static String simpleInterpolate(IProject project, String version) {
    if (version != null && version.contains("${")) { //$NON-NLS-1$
      //when expression is in the version but no project instance around
      // just give up.
      if(project == null) {
        return null;
      }
      IMavenProjectFacade mvnproject = MavenPlugin.getDefault().getMavenProjectManager().getProject(project);
      if (mvnproject != null) {
        MavenProject prj = mvnproject.getMavenProject();
        if (prj != null) {
          Properties props = prj.getProperties();
          if (props != null) {
            RegexBasedInterpolator inter = new RegexBasedInterpolator();
            inter.addValueSource(new PropertiesBasedValueSource(props));
            try {
              version = inter.interpolate(version);
            } catch(InterpolationException e) {
              version = null;
            }
          }
        }
      }
    }
    return version;
  }
  
  private static String searchPM(IProject project, String groupId, String artifactId) {
    if (project == null) {
      return null;
    }
    String version = null;
    //see if we can find the plugin in plugin management of resolved project.
    IMavenProjectFacade mvnproject = MavenPlugin.getDefault().getMavenProjectManager().getProject(project);
    if(mvnproject != null) {
      String id = Plugin.constructKey(groupId, artifactId);
      MavenProject prj = mvnproject.getMavenProject();
      if(prj != null) {
        PluginManagement pm = prj.getPluginManagement();
        if(pm != null) {
          for(Plugin pl : pm.getPlugins()) {
            if(id.equals(pl.getKey())) {
              version = pl.getVersion();
              break;
            }
          }
        }
      }
    }
    return version;
  }
  
  private static String searchDM(IProject project, String groupId, String artifactId) {
    if (project == null) {
      return null;
    }
    String version = null;
    //see if we can find the dependency is in dependency management of resolved project.
    IMavenProjectFacade mvnproject = MavenPlugin.getDefault().getMavenProjectManager().getProject(project);
    if(mvnproject != null) {
      String id = groupId + ":" + artifactId + ":";
      MavenProject prj = mvnproject.getMavenProject();
      if(prj != null) {
        DependencyManagement dm = prj.getDependencyManagement();
        if(dm != null) {
          for(Dependency dep : dm.getDependencies()) {
            if(dep.getManagementKey().startsWith(id)) {
              version = dep.getVersion();
              break;
            }
          }
        }
      }
    }
    return version;
  }  

  protected static String getArtifactId(Node currentNode) {
    return getSiblingTextValue(currentNode, "artifactId"); //$NON-NLS-1$
  }

  protected static String getVersion(Node currentNode) {
    return getSiblingTextValue(currentNode, "version"); //$NON-NLS-1$
  }

  private static String getSiblingTextValue(Node sibling, String name) {
    Node node = getSiblingWithName(sibling, name);
    return getNodeTextValue(node);
  }

  /**
   * Returns sibling with given name.
   */
  private static Node getSiblingWithName(Node node, String name) {
    NodeList nodeList = node.getParentNode().getChildNodes();
    for(int i = 0; i < nodeList.getLength(); i++ ) {
      if(name.equals(nodeList.item(i).getNodeName())) {
        return nodeList.item(i);
      }
    }
    return null;
  }

  /**
   * Returns text value of the node.
   */
  private static String getNodeTextValue(Node node) {
    if(node != null && hasOneNode(node.getChildNodes())) {
      return ((Text) node.getChildNodes().item(0)).getData().trim();
    }
    return null;
  }

  /**
   * Returns true if there is only one node in the nodeList.
   */
  private static boolean hasOneNode(NodeList nodeList) {
    return nodeList != null && nodeList.getLength() == 1;
  }

  private static void add(Collection<Template> proposals, String contextTypeId, String name) {
    add(proposals, contextTypeId, name, name);
  }    
  
  private static void add(Collection<Template> proposals, String contextTypeId, String name, String description) {
    proposals.add(new Template(name, description, contextTypeId, name, false));
  }    
  
}
