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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.utils.StringUtils;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;

import org.eclipse.m2e.core.core.IMavenConstants;
import org.eclipse.m2e.core.core.MavenLogger;
import org.eclipse.m2e.core.internal.project.MavenMarkerManager;
import org.eclipse.m2e.editor.xml.internal.Messages;

public class PomQuickAssistProcessor implements IQuickAssistProcessor {
  private static final String GROUP_ID_NODE = "groupId"; //$NON-NLS-1$
  private static final String ARTIFACT_ID_NODE = "artifactId"; //$NON-NLS-1$
  private static final String VERSION_NODE = "version"; //$NON-NLS-1$

  public static final String PROJECT_NODE = "project"; //$NON-NLS-1$
  public static final String XSI_VALUE = " xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"+ //$NON-NLS-1$
  "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\""; //$NON-NLS-1$
  
  public boolean canAssist(IQuickAssistInvocationContext arg0) {
    return true;
  }

  public boolean canFix(Annotation an) {

    if (an instanceof MarkerAnnotation) {
      MarkerAnnotation mark = (MarkerAnnotation) an;
      try {
        if (mark.getMarker().isSubtypeOf(IMavenConstants.MARKER_HINT_ID)) {
          return true;
        }
      } catch(CoreException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return false;
  }
  
  public ICompletionProposal[] computeQuickAssistProposals(IQuickAssistInvocationContext context) {
   List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
   Iterator<Annotation> annotationIterator = context.getSourceViewer().getAnnotationModel().getAnnotationIterator();
   while(annotationIterator.hasNext()){
     Annotation annotation = annotationIterator.next();
     if (annotation instanceof MarkerAnnotation) {
       MarkerAnnotation mark = (MarkerAnnotation) annotation;
       try {
         Position position = context.getSourceViewer().getAnnotationModel().getPosition(annotation);
         int lineNum = context.getSourceViewer().getDocument().getLineOfOffset(position.getOffset()) + 1;
         int currentLineNum = context.getSourceViewer().getDocument().getLineOfOffset(context.getOffset()) + 1;
         if (currentLineNum == lineNum) {
           if (mark.getMarker().isSubtypeOf(IMavenConstants.MARKER_HINT_ID)) {
             String hint = mark.getMarker().getAttribute(IMavenConstants.MARKER_ATTR_EDITOR_HINT, ""); //$NON-NLS-1$
             if (hint.equals("parent_groupid")) { //$NON-NLS-1$
               proposals.add(new IdPartRemovalProposal(context, false, mark));
             }
             if (hint.equals("parent_version")) { //$NON-NLS-1$
               proposals.add(new IdPartRemovalProposal(context, true, mark));
             }
             if (hint.equals("managed_dependency_override")) { //$NON-NLS-1$
               proposals.add(new ManagedVersionRemovalProposal(context, true, mark));
               //add a proposal to ignore the marker
               proposals.add(new IgnoreWarningProposal(context, mark, "NO-MVN-MAN-VER"));
             }
             if (hint.equals("managed_plugin_override")) { //$NON-NLS-1$
               proposals.add(new ManagedVersionRemovalProposal(context, false, mark));
               //add a proposal to ignore the marker
               proposals.add(new IgnoreWarningProposal(context, mark, "NO-MVN-MAN-VER"));
             }
             if (hint.equals("schema")) { //$NON-NLS-1$
               proposals.add(new SchemaCompletionProposal(context, mark));
             }
           }
         }
       } catch(Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
       }
     }
     
   }
   
   if (proposals.size() > 0) {
     return proposals.toArray(new ICompletionProposal[0]);
   }
   return null;
  }

  public String getErrorMessage() {
    return null;
  }
  
  static Element getRootElement(IDocument doc) {
    IDOMModel domModel = null;
    try {
      domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
      IStructuredDocument document = domModel.getStructuredDocument();
      Element root = domModel.getDocument().getDocumentElement();
      return root;
    } finally {
      if (domModel != null) {
        domModel.releaseFromRead();
      }
    }
  }
  
  static IStructuredDocument getDocument(IMarker marker) {
    if (marker.getResource().getType() == IResource.FILE)
    {
      IDOMModel domModel = null;
      try {
        domModel = (IDOMModel)StructuredModelManager.getModelManager().getModelForEdit((IFile)marker.getResource());
        return domModel.getStructuredDocument();
      } catch(Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } finally {
        if (domModel != null) {
          domModel.releaseFromRead();
        }
      }
    }
    return null;
  }
  
  static String previewForRemovedElement(IDocument doc, Element removed) {
    if (removed != null && removed instanceof IndexedRegion) {
      IndexedRegion reg = (IndexedRegion)removed;
      try {
        int line = doc.getLineOfOffset(reg.getStartOffset());
        int startLine = doc.getLineOffset(line);
        int prev2 = doc.getLineOffset(line - 2);
        String prevString = StringUtils.convertToHTMLContent(doc.get(prev2, startLine - prev2));
        String currentLine = doc.get(startLine, doc.getLineLength(line));
        int next2End = doc.getLineOffset(line + 2) + doc.getLineLength(line + 2);
        int next2Start = startLine + doc.getLineLength( line ) + 1;
        String nextString = StringUtils.convertToHTMLContent(doc.get(next2Start, next2End - next2Start));
        return "<html>...<br>" + prevString + /**"<del>" + currentLine + "</del>" +*/ nextString + "...<html>";  //$NON-NLS-1$ //$NON-NLS-2$
      } catch(BadLocationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

class SchemaCompletionProposal implements ICompletionProposal, ICompletionProposalExtension5 {

  IQuickAssistInvocationContext context;
  private MarkerAnnotation annotation;
  public SchemaCompletionProposal(IQuickAssistInvocationContext context, MarkerAnnotation mark){
    this.context = context;
    annotation = mark;
  }
  
  public void apply(IDocument doc) {
    IDOMModel domModel = null;
    try {
      domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
      IStructuredDocument document = domModel.getStructuredDocument();
      Element root = domModel.getDocument().getDocumentElement();
  
      //now check parent version and groupid against the current project's ones..
      if (root.getNodeName().equals(PomQuickAssistProcessor.PROJECT_NODE)) { //$NON-NLS-1$
        if (root instanceof IndexedRegion) {
          IndexedRegion off = (IndexedRegion) root;
  
          int offset = off.getStartOffset() + PomQuickAssistProcessor.PROJECT_NODE.length() + 1;
          if (offset <= 0) {
            return;
          }
          InsertEdit edit = new InsertEdit(offset, PomQuickAssistProcessor.XSI_VALUE);
          try {
            edit.apply(doc);
            annotation.getMarker().delete();
            Display.getDefault().asyncExec(new Runnable() {
              public void run() {
                IEditorPart activeEditor = MvnIndexPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow()
                    .getActivePage().getActiveEditor();
                MvnIndexPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage()
                    .saveEditor(activeEditor, false);
              }
            });
          } catch(Exception e) {
            MavenLogger.log("Unable to insert schema info", e); //$NON-NLS-1$
          }
        }
      }
    } finally {
      if (domModel != null) {
        domModel.releaseFromRead();
      }
    }
  }

  public String getAdditionalProposalInfo() {
    //NOT TO BE REALLY IMPLEMENTED, we have the other method
    return null;
  }

  public IContextInformation getContextInformation() {
    return null;
  }

  public String getDisplayString() {
    return Messages.PomQuickAssistProcessor_name;
  }

  public Image getImage() {
    return WorkbenchPlugin.getDefault().getImageRegistry().get(org.eclipse.ui.internal.SharedImages.IMG_OBJ_ADD);
  }

  public Point getSelection(IDocument arg0) {
    return null;
  }

  public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
    // TODO Auto-generated method stub
    return "<html>...<br>&lt;project <b>" + PomQuickAssistProcessor.XSI_VALUE + "</b>&gt;<br>...</html>"; //$NON-NLS-1$ //$NON-NLS-2$
  }
  
}


static class IdPartRemovalProposal implements ICompletionProposal, ICompletionProposalExtension5, IMarkerResolution {

  private IQuickAssistInvocationContext context;
  private final boolean isVersion;
  private final IMarker marker;
  public IdPartRemovalProposal(IQuickAssistInvocationContext context, boolean version, MarkerAnnotation mark) {
    this.context = context;
    isVersion = version;
    marker = mark.getMarker();
  }
  
  public IdPartRemovalProposal(IMarker marker, boolean version) {
    this.marker = marker;
    isVersion = version;
  }
  
  public void apply(IDocument doc) {
    Element root = getRootElement(doc);
    processFix(doc, root, isVersion, marker);
  }

  private void processFix(IDocument doc, Element root, boolean isversion, IMarker marker) {
    //now check parent version and groupid against the current project's ones..
    if (root.getNodeName().equals(PomQuickAssistProcessor.PROJECT_NODE)) { //$NON-NLS-1$
      Element value = MavenMarkerManager.findChildElement(root, isversion ? VERSION_NODE : GROUP_ID_NODE); //$NON-NLS-1$ //$NON-NLS-2$
      if (value != null && value instanceof IndexedRegion) {
        IndexedRegion off = (IndexedRegion) value;

        int offset = off.getStartOffset();
        if (offset <= 0) {
          return;
        }
        Node prev = value.getNextSibling();
        if (prev instanceof Text) {
          //check the content as well??
          off = ((IndexedRegion) prev);
        }
        DeleteEdit edit = new DeleteEdit(offset, off.getEndOffset() - offset);
        try {
          edit.apply(doc);
          marker.delete();
        } catch(Exception e) {
          MavenLogger.log("Unable to remove the element", e); //$NON-NLS-1$
        }
      }
    }
  }

  public String getAdditionalProposalInfo() {
    return null;
  }

  public IContextInformation getContextInformation() {
    return null;
  }

  public String getDisplayString() {
    return isVersion ? Messages.PomQuickAssistProcessor_title_version : Messages.PomQuickAssistProcessor_title_groupId;
  }

  public Image getImage() {
    return WorkbenchPlugin.getDefault().getImageRegistry().get(org.eclipse.ui.internal.SharedImages.IMG_TOOL_DELETE);
  }

  public Point getSelection(IDocument arg0) {
    return null;
  }

  public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
    if (context == null) {
      //no context in markerresolution, just to be sure..
      return null;
    }
    IDocument doc = context.getSourceViewer().getDocument();
    IDOMModel domModel = null;
    try {
      domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
//      IStructuredDocument document = domModel.getStructuredDocument();
      Element root = domModel.getDocument().getDocumentElement();

      //now check parent version and groupid against the current project's ones..
      if (root.getNodeName().equals(PomQuickAssistProcessor.PROJECT_NODE)) { //$NON-NLS-1$
        Element value = MavenMarkerManager.findChildElement(root, isVersion ? VERSION_NODE : GROUP_ID_NODE); //$NON-NLS-1$ //$NON-NLS-2$
        String toRet = previewForRemovedElement(doc, value);
        if (toRet != null) {
          return toRet;
        }
      }
    } finally {
      if (domModel != null) {
        domModel.releaseFromRead();
      }
    }      
    return Messages.PomQuickAssistProcessor_remove_hint;
  }

  public String getLabel() {
    return getDisplayString();
  }

  public void run(IMarker marker) {
    IStructuredDocument doc = getDocument(marker);
    if (doc != null) {
      Element root = getRootElement(doc);
      processFix(doc, root, isVersion, marker);
    }
  } 
}

static class ManagedVersionRemovalProposal implements ICompletionProposal, ICompletionProposalExtension5, IMarkerResolution {

  private IQuickAssistInvocationContext context;
  private final boolean isDependency;
  private final IMarker marker;
  public ManagedVersionRemovalProposal(IQuickAssistInvocationContext context, boolean dependency, MarkerAnnotation mark) {
    this.context = context;
    isDependency = dependency;
    marker = mark.getMarker();
  }
  
  public ManagedVersionRemovalProposal(IMarker marker, boolean dependency) {
    this.marker = marker;
    isDependency = dependency;
  }
  

  
  public void apply(IDocument doc) {
    Element root = getRootElement(doc);
    processFix(doc, root, isDependency, marker);

  }

  private void processFix(IDocument doc, Element root, boolean isdep, IMarker marker) {
    if (root.getNodeName().equals(PomQuickAssistProcessor.PROJECT_NODE)) { 
      Element artifact = findArtifactElement(root, isdep, marker);
      if (artifact == null) {
        //TODO report somehow?
        MavenLogger.log("Unable to find the marked element"); //$NON-NLS-1$
        return;
      }
      Element value = MavenMarkerManager.findChildElement(artifact, VERSION_NODE); //$NON-NLS-1$ //$NON-NLS-2$
      if (value != null && value instanceof IndexedRegion) {
        IndexedRegion off = (IndexedRegion) value;

        int offset = off.getStartOffset();
        if (offset <= 0) {
          return;
        }
        Node prev = value.getNextSibling();
        if (prev instanceof Text) {
          //check the content as well??
          off = ((IndexedRegion) prev);
        }
        DeleteEdit edit = new DeleteEdit(offset, off.getEndOffset() - offset);
        try {
          edit.apply(doc);
          marker.delete();
        } catch(Exception e) {
          MavenLogger.log("Unable to remove the element", e); //$NON-NLS-1$
        }
      }
    }
  }

  private Element findArtifactElement(Element root, boolean isdep, IMarker marker) {
    if (root == null) {
      return null;
    }
    String groupId = marker.getAttribute("groupId", null);
    String artifactId = marker.getAttribute("artifactId", null);
    assert groupId != null;
    assert artifactId != null;
    
    String profile = marker.getAttribute("profile", null);
    Element artifactParent = root;
    if (profile != null) {
      Element profileRoot = MavenMarkerManager.findChildElement(root, "profiles");
      if (profileRoot != null) {
        for (Element prf : MavenMarkerManager.findChildElements(profileRoot, "profile")) {
          if (profile.equals(MavenMarkerManager.getElementTextValue(MavenMarkerManager.findChildElement(prf, "id")))) {
            artifactParent = prf;
            break;
          }
        }
      }
    }
    if (!isdep) {
      //we have plugins now, need to go one level down to build
      artifactParent = MavenMarkerManager.findChildElement(artifactParent, "build");
    }
    if (artifactParent == null) {
      return null;
    }
    Element list = MavenMarkerManager.findChildElement(artifactParent, isdep ? "dependencies" : "plugins");
    if (list == null) {
      return null;
    }
    Element artifact = null;
    for (Element art : MavenMarkerManager.findChildElements(list, isdep ? "dependency" : "plugin")) {
       String grpString = MavenMarkerManager.getElementTextValue(MavenMarkerManager.findChildElement(art, GROUP_ID_NODE));
       String artString = MavenMarkerManager.getElementTextValue(MavenMarkerManager.findChildElement(art, ARTIFACT_ID_NODE));
       if (groupId.equals(grpString) && artifactId.equals(artString)) {
         artifact = art;
         break;
       }
    }
    return artifact;
  }

  public String getAdditionalProposalInfo() {
    return null;
  }

  public IContextInformation getContextInformation() {
    return null;
  }

  public String getDisplayString() {
    return Messages.PomQuickAssistProcessor_title_version;
  }

  public Image getImage() {
    return WorkbenchPlugin.getDefault().getImageRegistry().get(org.eclipse.ui.internal.SharedImages.IMG_TOOL_DELETE);
  }

  public Point getSelection(IDocument arg0) {
    return null;
  }

  public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
    if (context == null) {
      //no context in markerresolution, just to be sure..
      return null;
    }
    IDocument doc = context.getSourceViewer().getDocument();
    IDOMModel domModel = null;
    try {
      domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
      Element root = domModel.getDocument().getDocumentElement();
      Element artifact = findArtifactElement(root, isDependency, marker);
      if (artifact != null) {
        Element value = MavenMarkerManager.findChildElement(artifact, VERSION_NODE); 
        String toRet = previewForRemovedElement(doc, value);
        if (toRet != null) {
          return toRet;
        }
      }
    } finally {
      if (domModel != null) {
        domModel.releaseFromRead();
      }
    }      
    return Messages.PomQuickAssistProcessor_remove_hint;
  }

  public String getLabel() {
    return getDisplayString();
  }

  public void run(IMarker marker) {
    IStructuredDocument doc = getDocument(marker);
    if (doc != null) {
      Element root = getRootElement(doc);
      processFix(doc, root, isDependency, marker);
    }
  } 
}

static class IgnoreWarningProposal implements ICompletionProposal, ICompletionProposalExtension5, IMarkerResolution {

  private IQuickAssistInvocationContext context;
  private final IMarker marker;
  private final String markupText;
  public IgnoreWarningProposal(IQuickAssistInvocationContext context, MarkerAnnotation mark, String markupText) {
    this.context = context;
    marker = mark.getMarker();
    this.markupText = markupText;
  }
  
  public IgnoreWarningProposal(IMarker marker, String markupText) {
    this.marker = marker;
    this.markupText = markupText;
  }
  
  public void apply(IDocument doc) {
    if (doc instanceof IStructuredDocument) {
      processFix((IStructuredDocument) doc, marker);
    } else {
      IStructuredDocument strdoc = getDocument(marker);
      if (strdoc != null) {
        processFix(strdoc, marker);
      }
    }
  }

  private void processFix(IStructuredDocument doc, IMarker marker) {
      IDOMModel domModel = null;
      try {
        domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
        int line;
        if (context != null) {
          line = doc.getLineOfOffset(context.getOffset());
        } else {
          line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
          assert line != -1;
          line = line - 1;
        }
        try {
          int linestart = doc.getLineOffset(line);
          int lineend = linestart + doc.getLineLength(line);
          int start = linestart;
          IndexedRegion reg = domModel.getIndexedRegion(start);
          while (reg != null && !(reg instanceof Element) && start < lineend) {
            reg = domModel.getIndexedRegion(reg.getEndOffset() + 1);
            if (reg != null) {
              start = reg.getStartOffset();
            }
          }
          if (reg != null && reg instanceof Element) {
            InsertEdit edit = new InsertEdit(reg.getEndOffset(), "<!--$" + markupText + "$-->");
            try {
              edit.apply(doc);
              marker.delete();
            } catch(Exception e) {
              MavenLogger.log("Unable to insert", e); //$NON-NLS-1$
            }
          }
        } catch(BadLocationException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      } finally {
        if (domModel != null) {
          domModel.releaseFromRead();
        }
      }      
  }

  public String getAdditionalProposalInfo() {
    return null;
  }

  public IContextInformation getContextInformation() {
    return null;
  }

  public String getDisplayString() {
    return "Ignore this warning";
  }

  public Image getImage() {
    return MvnImages.IMG_CLOSE;
  }

  public Point getSelection(IDocument arg0) {
    return null;
  }

  public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
    if (context == null) {
      //no context in markerresolution, just to be sure..
      return null;
    }
    IDOMModel domModel = null;
    try {
      IDocument doc = context.getSourceViewer().getDocument();
      domModel = (IDOMModel) StructuredModelManager.getModelManager().getExistingModelForRead(doc);
      try {
        //the offset of context is important here, not the offset of the marker!!!
        //line/offset of marker only gets updated hen file gets saved.
        //we need the proper handling also for unsaved documents..
        int line = doc.getLineOfOffset(context.getOffset());
        int linestart = doc.getLineOffset(line);
        int lineend = linestart + doc.getLineLength(line);
        int start = linestart;
        IndexedRegion reg = domModel.getIndexedRegion(start);
        while (reg != null && !(reg instanceof Element) && start < lineend) {
          reg = domModel.getIndexedRegion(reg.getEndOffset() + 1);
          if (reg != null) {
            start = reg.getStartOffset();
          }
        }
        if (reg != null && reg instanceof Element) { //just a simple guard against moved marker
          try {
            int startLine = doc.getLineOffset(line);
            String currentLine = StringUtils.convertToHTMLContent(doc.get(reg.getStartOffset(), reg.getEndOffset() - reg.getStartOffset()));
            String insert = StringUtils.convertToHTMLContent("<!--$" + markupText + "$-->");
            return "<html>...<br>" + currentLine + "<b>" + insert + "</b><br>...<html>";  //$NON-NLS-1$ //$NON-NLS-2$
          } catch(BadLocationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      } catch(BadLocationException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
    } finally {
      if (domModel != null) {
        domModel.releaseFromRead();
      }
    }      
    return Messages.PomQuickAssistProcessor_remove_hint;
  }

  public String getLabel() {
    return getDisplayString();
  }

  public void run(IMarker marker) {
    IStructuredDocument doc = getDocument(marker);
    if (doc != null) {
      processFix(doc, marker);
    }
  } 
}

}
