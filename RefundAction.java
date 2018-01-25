package com.kayak.cashier.action;

import java.util.Map;

import com.kayak.cashier.action.service.RefundService;
import com.kayak.cashier.modal.Variable;
import com.kayak.frame.Launcher;
import com.kayak.frame.SystemException;
import com.kayak.frame.action.Action;

public class RefundAction extends Action {

	@Override
	public String getCode() {
		return "Refund";
	}

	@Override
	protected void check(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		requireKeys(params, Variable.TXN_NO);
		requireValues(params, Variable.TXN_NO);
	}

	@Override
	protected void process(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		RefundService rs = Launcher.get(RefundService.class);
		rs.process(result, params);
	}

}
