package org.activitymgr.ui.web.logic;

import java.util.Calendar;
import java.util.List;

// TODO clear
public interface IContributionsTabLogic extends ILogic<IContributionsTabLogic.View> {

	void onPreviousYear();

	void onPreviousMonth();

	void onPreviousWeek();

	void onNextWeek();

	void onNextMonth();

	void onNextYear();

	void onDateChange(Calendar value);

	void onSelectedCollaboratorChanged(long collaboratorId);

	void onAction(String actionId);

	public interface View extends ILogic.IView<IContributionsTabLogic> {
		
		void setCollaborators(ITableCellProviderCallback<Long> collaboratorsProvider);
		
		void selectCollaborator(long collaboratorId);

		void setColumnIdentifiers(List<String> ids);
		
		void setDate(Calendar lastMonday);

		void removeAllWeekContributions();

		void addWeekContribution(long taskId, List<ILogic.IView<?>> cellViews);
		
		void setColumnFooter(String id, String value);
		
		void addAction(String actionId, String label, String keyBindingDescription, String iconId, char key, boolean ctrl, boolean shift, boolean alt);
	}

}
