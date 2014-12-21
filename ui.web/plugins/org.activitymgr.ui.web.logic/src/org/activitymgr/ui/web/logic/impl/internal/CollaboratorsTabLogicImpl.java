package org.activitymgr.ui.web.logic.impl.internal;

import org.activitymgr.ui.web.logic.ICollaboratorsTabLogic;
import org.activitymgr.ui.web.logic.ITableCellProviderCallback;
import org.activitymgr.ui.web.logic.impl.AbstractLogicImpl;

public class CollaboratorsTabLogicImpl extends AbstractLogicImpl<ICollaboratorsTabLogic.View> implements ICollaboratorsTabLogic {

	public CollaboratorsTabLogicImpl(AbstractLogicImpl<ICollaboratorsTabLogic.View> parent) {
		super(parent);
		CollaboratorsListContentProvider contentProvider = new CollaboratorsListContentProvider(this, getContext(), getModelMgr(), true, false);
		getView().setCollaboratorsProviderCallback(getContext().buildTransactionalWrapper(contentProvider, ITableCellProviderCallback.class));
	}

}
