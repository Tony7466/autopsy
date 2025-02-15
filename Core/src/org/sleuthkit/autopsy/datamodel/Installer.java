/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datamodel;

import java.awt.Component;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JOptionPane;
import org.openide.LifecycleManager;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.LibraryLock;
import org.sleuthkit.datamodel.LibraryLock.LockState;
import org.sleuthkit.datamodel.SleuthkitJNI;

/**
 * Installer checks that the JNI library is working when the module is loaded.
 */
public class Installer extends ModuleInstall {

    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }

    @Messages({
        "Installer_validate_tskLibLock_title=Error calling Sleuth Kit library",
        "Installer_validate_tskLibLock_description=<html>Another forensics application is running that uses The Sleuth Kit.<br/>You must close that application before launching Autopsy.<br/>If that application is Cyber Triage, then you should upgrade it so that it can run at the same time as Autopsy.</html>"
    })
    @Override
    public void validate() throws IllegalStateException {

        
        /*
         * The NetBeans API specifies that a module should throw an
         * IllegalStateException if it can't be initalized, but NetBeans doesn't
         * handle that behaviour well (it just disables the module, and all
         * dependant modules, on the current and all subsequent application
         * launches). Hence, we deal with it manually.
         *
         */

        // Check that the the Sleuth Kit JNI is working by getting the Sleuth Kit version number
        Logger logger = Logger.getLogger(Installer.class.getName());

        try {
            LibraryLock libLock = LibraryLock.acquireLibLock();
            if (libLock != null && libLock.getLockState() == LockState.HELD_BY_OLD) {
                throw new OldAppLockException("A lock on the libtsk_jni lib is already held by an old application.  " + (libLock.getLibTskJniFile() != null ? libLock.getLibTskJniFile().getAbsolutePath() : ""));
            }
                    
            String skVersion = SleuthkitJNI.getVersion();

            if (skVersion == null) {
                throw new Exception(NbBundle.getMessage(this.getClass(), "Installer.exception.tskVerStringNull.msg"));
            } else if (skVersion.length() == 0) {
                throw new Exception(NbBundle.getMessage(this.getClass(), "Installer.exception.taskVerStringBang.msg"));
            } else {
                logger.log(Level.CONFIG, "Sleuth Kit Version: {0}", skVersion); //NON-NLS
            }

        } catch (Exception | UnsatisfiedLinkError e) {
            
            // Normal error box log handler won't be loaded yet, so show error here.
            final Component parentComponent = null; // Use default window frame.
            final int messageType = JOptionPane.ERROR_MESSAGE;

            final String message;
            final String title;
            
            if (e instanceof OldAppLockException ex) {
                logger.log(Level.SEVERE, "An older application already holds a lock on the libtsk_jni lib", ex);
                message = Bundle.Installer_validate_tskLibLock_description();
                title = Bundle.Installer_validate_tskLibLock_title();
            } else {
                logger.log(Level.SEVERE, "Error calling Sleuth Kit library (test call failed)", e); //NON-NLS
                logger.log(Level.SEVERE, "Is Autopsy or Cyber Triage already running?)", e); //NON-NLS
                message = NbBundle.getMessage(this.getClass(), "Installer.tskLibErr.msg", e.toString());
                title = NbBundle.getMessage(this.getClass(), "Installer.tskLibErr.err");
            }
            
            JOptionPane.showMessageDialog(
                    parentComponent,
                    message,
                    title,
                    messageType);

            // exit after user exits the error dialog box
            LifecycleManager.getDefault().exit();
        }

    }

    @Override
    public void close() {
        try {
            LibraryLock.removeLibLock();
        } catch (Exception ex) {
            Logger logger = Logger.getLogger(Installer.class.getName());
            logger.log(Level.WARNING, "There was an error removing the TSK lib lock.", ex);
        }
    }

    @Override
    public void uninstalled() {
        try {
            LibraryLock.removeLibLock();
        } catch (Exception ex) {
            Logger logger = Logger.getLogger(Installer.class.getName());
            logger.log(Level.WARNING, "There was an error removing the TSK lib lock.", ex);
        }
    }
    
    
    

    /**
     * An exception when an older application (Autopsy
     */
    static class OldAppLockException extends Exception {

        public OldAppLockException(String message) {
            super(message);
        }
        
    }
}
