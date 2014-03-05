package org.activitymgr.ui.web.logic.impl;

import org.activitymgr.core.beans.Collaborator;
import org.activitymgr.core.beans.Contribution;
import org.activitymgr.core.beans.TaskContributions;
import org.activitymgr.core.util.StringHelper;
import org.activitymgr.ui.web.logic.ILogic;
import org.activitymgr.ui.web.logic.ITextFieldLogic;
import org.activitymgr.ui.web.logic.impl.event.DurationChangedEvent;
import org.activitymgr.ui.web.logic.impl.internal.ContributionsLogicImpl;

public class DefaultContributionCellLogicProvider implements IContributionCellLogicProviderExtension {
	
	@Override
	public ILogic<?> getCellLogic(final AbstractContributionLogicImpl parent, final Collaborator contributor, final String columnId,
			final TaskContributions weekContributions) {
		if (DAY_COLUMNS_IDENTIFIERS.contains(columnId)) {
			final int dayOfWeek = DAY_COLUMNS_IDENTIFIERS.indexOf(columnId);
			Contribution c = weekContributions.getContributions()[dayOfWeek];
			String duration = (c == null) ? "" : StringHelper.hundredthToEntry(c.getDurationId());
			ITextFieldLogic textFieldLogic = new AbstractTextFieldLogicImpl((ContributionsLogicImpl) parent, duration) {
				@Override
				public void onValueChanged(String newValue) {
					parent.getContext().getEventBus().fire(new DurationChangedEvent(parent, weekContributions, dayOfWeek, newValue, this));
				}
			};
			textFieldLogic.getView().setNumericFieldStyle();
			return textFieldLogic;
		}
		else if (IContributionCellLogicProviderExtension.PATH_COLUMN_ID.equals(columnId)) {
			return new LabelLogicImpl((ContributionsLogicImpl) parent, weekContributions.getTaskCodePath());
		}
		else if (IContributionCellLogicProviderExtension.NAME_COLUMN_ID.equals(columnId)) {
			return new LabelLogicImpl((ContributionsLogicImpl) parent, weekContributions.getTask().getName());
		}
		else if (IContributionCellLogicProviderExtension.TOTAL_COLUMN_ID.equals(columnId)) {
			return new LabelLogicImpl((ContributionsLogicImpl) parent, "");
		}
		else {
			throw new IllegalArgumentException("Unexpected column identifier '" + columnId + "'");
		}
	}

}