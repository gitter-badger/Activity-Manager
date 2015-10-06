package org.activitymgr.ui.web.logic.impl;

import java.util.Set;

import org.activitymgr.ui.web.logic.ITabFolderLogic;
import org.activitymgr.ui.web.logic.ITabLogic;
import org.activitymgr.ui.web.logic.spi.ITabButtonFactory;

public abstract class AbstractTabLogicImpl<VIEW extends ITabLogic.View<?>> extends AbstractLogicImpl<VIEW> implements ITabLogic<VIEW> {

	public AbstractTabLogicImpl(ITabFolderLogic parent) {
		super(parent);
	}

	protected <TABLOGIC extends ITabLogic<VIEW>> void registerButtons(Set<ITabButtonFactory<TABLOGIC>> buttonFactories) {
		if (buttonFactories != null) {
			for (ITabButtonFactory<TABLOGIC> buttonFactory : buttonFactories) {
				getView().addButton(buttonFactory.create((TABLOGIC) this).getView());
			}
		}
	}


}
