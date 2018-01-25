package com.kayak.cashier.action;

import java.util.Map;

import com.kayak.cashier.action.service.QueryTxnStatusService;
import com.kayak.cashier.modal.Variable;
import com.kayak.frame.Launcher;
import com.kayak.frame.SystemException;
import com.kayak.frame.action.Action;

public class QueryTxnStatusAction extends Action {

	@Override
	public String getCode() {
		return "QueryTxnStatus";
	}

	@Override
	protected void check(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		requireKeys(params, Variable.TXN_NO);
		requireValues(params, Variable.TXN_NO);
	}

	@Override
	protected void process(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		QueryTxnStatusService qtss = Launcher.get(QueryTxnStatusService.class);
		qtss.process(result, params);
	}

}
