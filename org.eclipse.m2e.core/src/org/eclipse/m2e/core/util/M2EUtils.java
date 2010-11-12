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

package org.eclipse.m2e.core.util;

import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * M2EUtils
 *
 * @author dyocum
 */
public class M2EUtils {

  public static Font deriveFont(Font f, int style, int height){
    FontData[] fd = f.getFontData();
    FontData[] newFD = new FontData[fd.length];
    for(int i=0;i<fd.length;i++){
      newFD[i] = new FontData(fd[i].getName(), height, style);
    }
    return new Font(Display.getCurrent(), newFD);
  }
  
  public static void showErrorDialog(Shell shell, String title, String msg, Exception e){
    StringBuffer buff = new StringBuffer(msg);
    Throwable t = getRootCause(e);
    if(t != null && !nullOrEmpty(t.getMessage())){
      buff.append(t.getMessage());
    }
    MessageDialog.openError(shell, title, buff.toString());
  }
  
  public static String getRootCauseMessage(Throwable t){
    Throwable root = getRootCause(t);
    if(t == null){
      return null;
    }
    return root.getMessage();
  }
  
  public static Throwable getRootCause(Throwable ex) {
    if(ex == null){
      return null;
    }
    Throwable rootCause = ex;
    Throwable cause = rootCause.getCause();
    while(cause != null && cause != rootCause) {
        rootCause = cause;
        cause = cause.getCause();
    }
    return cause == null ? rootCause : cause;
  }
  
  public static boolean nullOrEmpty(String s){
    return s == null || s.length() == 0;
  }

  /**
   * @param shell
   * @param string
   * @param string2
   * @param updateErrors
   */
  public static void showErrorsForProjectsDialog(final Shell shell, final String title, final String message,
      final Map<String, Throwable> errorMap) {
    // TODO Auto-generated method showErrorsForProjectsDialog
    Display.getDefault().asyncExec(new Runnable(){
      public void run(){
        String[] buttons = {IDialogConstants.OK_LABEL};
        int ok_button = 0;
        M2EErrorDialog errDialog = new M2EErrorDialog(shell, title, Dialog.getImage(Dialog.DLG_IMG_MESSAGE_ERROR), message, MessageDialog.ERROR, buttons, ok_button,
            errorMap);      
        errDialog.create();
        errDialog.open();
      }
    });

  }
}
