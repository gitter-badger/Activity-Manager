package org.activitymgr.ui.web.logic.impl.internal;

import org.activitymgr.ui.web.logic.ITasksTabLogic;
import org.activitymgr.ui.web.logic.ITreeContentProviderCallback;
import org.activitymgr.ui.web.logic.impl.AbstractLogicImpl;
import org.activitymgr.ui.web.logic.impl.AbstractTabLogicImpl;

public class TasksTabLogicImpl extends AbstractTabLogicImpl<ITasksTabLogic.View> implements ITasksTabLogic {

	public TasksTabLogicImpl(AbstractLogicImpl<ITasksTabLogic.View> parent) {
		super(parent);
		TaskTreeContentProvider treeContentCallback = new TaskTreeContentProvider(this);
		getView().setTreeContentProviderCallback(buildTransactionalWrapper(treeContentCallback, ITreeContentProviderCallback.class));
	}

}
