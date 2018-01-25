package com.kayak.cashier.action;

import java.util.Map;

import com.kayak.cashier.check.CheckService;
import com.kayak.cashier.check.DealErrService;
import com.kayak.cashier.modal.BizType;
import com.kayak.cashier.modal.CheckErrorType;
import com.kayak.cashier.modal.Status;
import com.kayak.cashier.modal.Variable;
import com.kayak.cashier.txn.TxnService;
import com.kayak.frame.ErrorCode;
import com.kayak.frame.Launcher;
import com.kayak.frame.SystemException;
import com.kayak.frame.action.Action;
import com.kayak.frame.util.MapUtil;
import com.kayak.pay.modal.HostStatus;

import msoa.org.apache.commons.collections.MapUtils;

public class DealErrorAction extends Action {

	@Override
	public String getCode() {
		return "DealError";
	}

	@Override
	protected void check(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		requireKeys(params, Variable.ERR_NO, Variable.DEAL_TYPE);
		requireValues(params, Variable.ERR_NO, Variable.DEAL_TYPE);
	}

	@Override
	protected void process(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		CheckService cs = Launcher.get(CheckService.class);
		TxnService ts = Launcher.get(TxnService.class);

		/** 获取该条差错信息 */
		Map<String, Object> errInfo = cs.getErrInfo(params);

		if (MapUtil.string(errInfo, Variable.STATUS).equals(Status.DEALED)) {
			throw new SystemException(ErrorCode.已处理的差错);
		}

		/** 获取待查错金额 */
		params.put(Variable.ERR_AMT, MapUtil.bigDecimalx(errInfo, Variable.ERR_AMT));
		params.put(Variable.PMC_CODE, MapUtil.string(errInfo, Variable.PMC_CODE));
		params.put(Variable.TXN_NO, MapUtil.string(errInfo, Variable.PLAT_TXN_NO));
		params.put(Variable.ERR_TYPE, MapUtil.string(errInfo, Variable.ERR_TYPE));
		String status = MapUtils.getString(errInfo, Variable.STATUS);
		if(Status.DEALFAIL.equals(status)) {
			params.put(Variable.HOST_FIRST_TIME, Variable.FALSE);
		}else {
			params.put(Variable.HOST_FIRST_TIME, Variable.TRUE);
		}
		/** 获取平台原流水 */
		Map<String, Object> txn = ts.getTxnInfo2(params);
		if (txn != null) {
			params.put(Variable.TRAN_NO, MapUtil.string(txn, Variable.GLOBAL_SEQ_NO));
			params.put(Variable.SUB_TRANS_SEQ, MapUtil.string(txn, Variable.SUB_TRANS_SEQ));
			//获取业务类型(收单、退款)
			params.put(Variable.BIZ_TYPE, MapUtil.string(txn, Variable.BIZ_TYPE));
		}
		/** 根据前端发起不同的操作，确定差错处理 */
		switch (MapUtil.string(params, Variable.DEAL_TYPE)) {
		case CheckErrorType.FILL:
			fillErr(params, result);
			break;
		case CheckErrorType.REFUND:
			refundErr(params, result);
			break;
		case CheckErrorType.REQUEST:
			requestErr(params, result);
			break;
		case CheckErrorType.LOSE:
			loseErr(params, result);
			break;
		case CheckErrorType.CATCH:
			catchErr(params, result);
			break;
		case CheckErrorType.OFFLINE:
			offLineErr(params);
			break;
		default:
			break;
		}
	}

	public void fillErr(Map<String, Object> params,Map<String, Object> result) throws SystemException {
		String bizType = MapUtil.string(params, Variable.BIZ_TYPE);
		String errType = MapUtil.string(params, Variable.ERR_TYPE);
		if ((BizType.isCreditTxn(bizType) && CheckErrorType.PLATHAVE_PMCNOT.equals(errType))
				|| (BizType.isDebitTxn(bizType) && CheckErrorType.PLATNOT_PMCHAVE.equals(errType))) {
			DealErrService des = Launcher.get(DealErrService.class);
			try {
				des.fillErr(params);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else {
			result.put(Variable.RETURN_CODE, ErrorCode.操作不支持.getCode());
			result.put(Variable.RETURN_MESSAGE, ErrorCode.操作不支持.getMessage());
		}
	}

	public void refundErr(Map<String, Object> params,Map<String, Object> result) throws SystemException {
		String bizType = MapUtil.string(params, Variable.BIZ_TYPE);
		String errType = MapUtil.string(params, Variable.ERR_TYPE);
		if ((BizType.isCreditTxn(bizType) && CheckErrorType.PLATHAVE_PMCNOT.equals(errType))
				|| (BizType.isDebitTxn(bizType) && CheckErrorType.PLATNOT_PMCHAVE.equals(errType))) {

			DealErrService des = Launcher.get(DealErrService.class);
			des.refundErr(params);
		} else {
			result.put(Variable.RETURN_CODE, ErrorCode.操作不支持.getCode());
			result.put(Variable.RETURN_MESSAGE, ErrorCode.操作不支持.getMessage());
		}
	}

	public void requestErr(Map<String, Object> params,Map<String, Object> result) throws SystemException {
		String bizType = MapUtil.string(params, Variable.BIZ_TYPE);
		String errType = MapUtil.string(params, Variable.ERR_TYPE);
		if ((BizType.isCreditTxn(bizType) && CheckErrorType.PLATNOT_PMCHAVE.equals(errType))
				|| (BizType.isDebitTxn(bizType) && CheckErrorType.PLATHAVE_PMCNOT.equals(errType))) {
			DealErrService des = Launcher.get(DealErrService.class);
			des.requestErr(params);
		}

	}

	public void loseErr(Map<String, Object> params,Map<String, Object> result) throws SystemException {
		String bizType = MapUtil.string(params, Variable.BIZ_TYPE);
		String errType = MapUtil.string(params, Variable.ERR_TYPE);
		if ((BizType.isCreditTxn(bizType) && CheckErrorType.PLATNOT_PMCHAVE.equals(errType))
				|| (BizType.isDebitTxn(bizType) && CheckErrorType.PLATHAVE_PMCNOT.equals(errType))) {
			DealErrService des = Launcher.get(DealErrService.class);
			des.loseErr(params);
		}else {
			result.put(Variable.RETURN_CODE, ErrorCode.操作不支持.getCode());
			result.put(Variable.RETURN_MESSAGE, ErrorCode.操作不支持.getMessage());
		}
	}

	public void catchErr(Map<String, Object> params,Map<String, Object> result) throws SystemException {
		String bizType = MapUtil.string(params, Variable.BIZ_TYPE);
		String errType = MapUtil.string(params, Variable.ERR_TYPE);
		if ((BizType.isCreditTxn(bizType) && CheckErrorType.PLATNOT_PMCHAVE.equals(errType))
				|| (BizType.isDebitTxn(bizType) && CheckErrorType.PLATHAVE_PMCNOT.equals(errType))) {
			DealErrService des = Launcher.get(DealErrService.class);
			try {
				des.catchErr(params);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else {
			result.put(Variable.RETURN_CODE, ErrorCode.操作不支持.getCode());
			result.put(Variable.RETURN_MESSAGE, ErrorCode.操作不支持.getMessage());
		}
	}

	public void offLineErr(Map<String, Object> params) throws SystemException {
		DealErrService des = Launcher.get(DealErrService.class);
		des.offLineErr(params,HostStatus.ACCOUNT_SUCCESS);
	}

}
