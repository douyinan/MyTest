package com.kayak.cashier.action;

import java.util.Map;

import com.kayak.cashier.action.service.RegisterService;
import com.kayak.cashier.modal.Variable;
import com.kayak.frame.Launcher;
import com.kayak.frame.SystemException;
import com.kayak.frame.action.Action;

public class RegisterAction extends Action {

	@Override
	public String getCode() {
		return "Register";
	}

	/**
	 * 商户到支付宝或微信备案
	 */
	@Override
	protected void check(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		requireKeys(params, Variable.SUB_MCHT_NO, Variable.NAME, Variable.BRIEF, Variable.PHONE, Variable.CHANNEL_ID,
				Variable.BUSINESS, Variable.REMARK, Variable.PRODUCTS);
		requireValues(params, Variable.SUB_MCHT_NO, Variable.NAME, Variable.BRIEF, Variable.PHONE, Variable.CHANNEL_ID,
				Variable.BUSINESS, Variable.REMARK, Variable.PRODUCTS);
	}

	@Override
	protected void process(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		RegisterService rs = Launcher.get(RegisterService.class);
		rs.process(result, params);
	}

}
