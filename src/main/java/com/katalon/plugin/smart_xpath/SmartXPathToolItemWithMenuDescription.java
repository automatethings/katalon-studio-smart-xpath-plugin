package com.katalon.plugin.smart_xpath;


import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.katalon.platform.api.exception.ResourceException;
import com.katalon.platform.api.extension.ToolItemWithMenuDescription;
import com.katalon.platform.api.model.ProjectEntity;
import com.katalon.platform.api.preference.PluginPreference;
import com.katalon.platform.api.service.ApplicationManager;
import com.katalon.plugin.smart_xpath.constant.SmartXPathConstants;
import com.katalon.plugin.smart_xpath.controller.AutoHealingController;
import com.katalon.plugin.smart_xpath.dialog.AutoHealingDialog;
import com.katalon.plugin.smart_xpath.entity.BrokenTestObject;

public class SmartXPathToolItemWithMenuDescription implements ToolItemWithMenuDescription {
	private Menu newMenu;
	private MenuItem smartXPathEnable;
	private MenuItem smartXPathDisable;
	private MenuItem autoHealing;
	private Control parent;

	@Override
	public Menu getMenu(Control arg0) {
		parent = arg0;
		newMenu = new Menu(arg0);
		evaluateAndAddMenuItem(newMenu);
		// This is intentional, updating static menu's item is troublesome, so
		// I'd display MenuItem on clicking on ToolItem
		return null;
	}

	@Override
	public void defaultEventHandler() {
		if (newMenu != null) {
			evaluateAndAddMenuItem(newMenu);
			// Display menu at the mouse position (guaranteed to be within the
			// ToolItem icon)
			newMenu.setVisible(true);
		}
	}

	private void evaluateAndAddMenuItem(Menu newMenu) {
		// Dispose all items
		for (MenuItem item : newMenu.getItems()) {
			item.dispose();
		}
		smartXPathEnable = null;
		smartXPathDisable = null;

		// Re-evaluate the PreferenceStore and add the appropriate menu item
		try {
			ProjectEntity currentProject = ApplicationManager.getInstance().getProjectManager().getCurrentProject();
			if (currentProject != null) {
				PluginPreference preferenceStore = ApplicationManager.getInstance().
						getPreferenceManager().getPluginPreference(currentProject.getId(),
						"com.katalon.katalon-studio-smart-xpath");
				if (preferenceStore.getBoolean("SmartXPathEnabled", false)) {
					addDisableSmartXPathMenuItem(newMenu, true);
				} else {
					addEnableSmartXPathMenuItem(newMenu, true);
				}
				addLoadAutoHealingEntitiesMenuItem(newMenu, true);
			} else {
				addLoadAutoHealingEntitiesMenuItem(newMenu, false);
			}
		} catch (ResourceException e) {
			e.printStackTrace(System.out);
		}
	}

	private MenuItem addEnableSmartXPathMenuItem(Menu parentMenu, boolean enable) {
		smartXPathEnable = new MenuItem(parentMenu, SWT.PUSH);
		smartXPathEnable.setText("Smart XPath Enable");
		smartXPathEnable.setToolTipText("Enable Smart XPath");
		smartXPathEnable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					// Retrieve PreferenceStore on click in case user installed
					// this plug-in when no project was opened
					ProjectEntity currentProject = ApplicationManager.getInstance().getProjectManager()
							.getCurrentProject();
					PluginPreference preferenceStore = ApplicationManager.getInstance().getPreferenceManager()
							.getPluginPreference(currentProject.getId(), "com.katalon.katalon-studio-smart-xpath");

					preferenceStore.setBoolean("SmartXPathEnabled", true);
					preferenceStore.save();
				} catch (ResourceException e1) {
					e1.printStackTrace(System.out);
				}
			}
		});
		return smartXPathEnable;
	}

	private MenuItem addDisableSmartXPathMenuItem(Menu parentMenu, boolean enable) {
		smartXPathDisable = new MenuItem(parentMenu, SWT.PUSH);
		smartXPathDisable.setText("Smart XPath Disable");
		smartXPathDisable.setToolTipText("Disable Smart XPath");
		smartXPathDisable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					// Retrieve PreferenceStore again in case the user installed
					// the plugin when no project was opened
					ProjectEntity currentProject = ApplicationManager.getInstance().getProjectManager()
							.getCurrentProject();
					PluginPreference preferenceStore = ApplicationManager.getInstance().getPreferenceManager()
							.getPluginPreference(currentProject.getId(), "com.katalon.katalon-studio-smart-xpath");

					preferenceStore.setBoolean("SmartXPathEnabled", false);
					preferenceStore.save();
				} catch (ResourceException e1) {
					e1.printStackTrace(System.out);
				}
			}
		});
		return smartXPathDisable;
	}

	private MenuItem addLoadAutoHealingEntitiesMenuItem(Menu parentMenu, boolean enable) {
		autoHealing = new MenuItem(parentMenu, SWT.PUSH);
		autoHealing.setText("XPath Auto-healing Logs");
		autoHealing.setEnabled(enable);
		autoHealing.setToolTipText("Approve or reject Smart XPath auto-healing effect on failed locators");
		autoHealing.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				AutoHealingDialog autoHealingDialog = new AutoHealingDialog(parent.getShell());
				
				if (autoHealingDialog.open() == Window.OK) {
					List<BrokenTestObject> approvedAutoHealingEntities = autoHealingDialog.getApprovedAutoHealingEntities();
					List<BrokenTestObject> unapprovedAutoHealingEntities = autoHealingDialog
							.getUnapprovedAutoHealingEntities();

					boolean autoHealingSucceeded = AutoHealingController.autoHealBrokenTestObjects(parent.getShell(),
							approvedAutoHealingEntities);

					if (autoHealingSucceeded) {
						ProjectEntity projectEntity = ApplicationManager.getInstance().getProjectManager()
								.getCurrentProject();
						if (projectEntity != null) {
							String pathToApprovedJson = projectEntity.getFolderLocation() + SmartXPathConstants.APPROVED_FILE_SUFFIX;
							// Append to approved.json with newly approved broken test objects
							doAppendToFileWithBrokenObjects(approvedAutoHealingEntities, pathToApprovedJson);
							String pathToWaitingForApprovalJson = projectEntity.getFolderLocation() + SmartXPathConstants.WAITING_FOR_APPROVAL_FILE_SUFFIX;
							// Overwrite waiting-for-approval.json with unapproved broken test objects
							doWriteToFileWithBrokenObjects(unapprovedAutoHealingEntities, pathToWaitingForApprovalJson);
						}
					}
				}
			}
		});
		return autoHealing;
	}
		
	private void doWriteToFileWithBrokenObjects(List<BrokenTestObject> brokenTestObjectsToUpdate, String filePath) {
		try {
			new ProgressMonitorDialog(parent.getShell()).run(true, false, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					monitor.beginTask("Writing to " + filePath  + "...", 1);
					AutoHealingController.writeToFilesWithBrokenObjects(brokenTestObjectsToUpdate,
							filePath);
				}
			});
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		}
	}
	
	private void doAppendToFileWithBrokenObjects(List<BrokenTestObject> brokenTestObjectsToUpdate, String filePath) {
		try {
			new ProgressMonitorDialog(parent.getShell()).run(true, false, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					monitor.beginTask("Appending to " + filePath  + "...", 1);
					AutoHealingController.appendToFileWithBrokenObjects(brokenTestObjectsToUpdate,
							filePath);
				}
			});
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		}
	}


	@Override
	public String iconUrl() {
		return "platform:/plugin/com.katalon.katalon-studio-smart-xpath/icons/bug_16@2x.png";
	}

	@Override
	public String name() {
		return "Smart XPath";
	}

	@Override
	public String toolItemId() {
		return "com.katalon.plugin.smart_xpath.smartXpathToolItemWithDescription";
	}

}
