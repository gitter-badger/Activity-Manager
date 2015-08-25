package org.activitymgr.ui.web.logic.impl;

import org.activitymgr.core.dto.Collaborator;
import org.activitymgr.ui.web.logic.spi.IFeatureAccessManager;

public class DefaultFeatureAccessManagerImpl implements IFeatureAccessManager {

	@Override
	public boolean hasAccessToTab(Collaborator collaborator, String tab) {
		return true;
	}

}
