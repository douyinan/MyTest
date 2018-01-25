package com.kayak.cashier.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kayak.cashier.action.service.QueryTxnStatusService;
import com.kayak.cashier.alipay.AlipayService;
import com.kayak.cashier.check.CheckService;
import com.kayak.cashier.modal.PmcCode;
import com.kayak.cashier.modal.Variable;
import com.kayak.cashier.util.FileUtil;
import com.kayak.frame.ErrorCode;
import com.kayak.frame.Launcher;
import com.kayak.frame.SystemException;
import com.kayak.frame.action.Action;
import com.kayak.frame.util.MapUtil;

public class AliCheckAction extends Action {
	private static final Logger log = LoggerFactory.getLogger(QueryTxnStatusService.class);

	@Override
	public String getCode() {
		return "AliCheck";
	}

	@Override
	protected void check(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		/** 字段校验 */
	}

	@Override
	protected void process(Map<String, Object> result, Map<String, Object> params) throws SystemException {
 
		CheckService cs = Launcher.get(CheckService.class);
		AlipayService as = Launcher.get(AlipayService.class);

		/** 获取通道对账文件 */
		as.alipayBillUrlQuery(result, params);
		List<String> arrlist;
		try {
			arrlist = FileUtil.readZipCvsFile(MapUtil.string(params, Variable.BILL_URL), "E:/zhifubao_check/",
					MapUtil.string(params, Variable.BILL_DATE) + ".zip");
		} catch (Exception e) {
			throw new SystemException(ErrorCode.系统错误, e);
		}
		log.info("arrlist:" + arrlist);
		// ==========================================================================
		String data = MapUtil.string(params, Variable.DATA).replaceAll("`", "");

		/** 将通道文件转化为Map */
		String[] billList = data.split("\n", -1);
		Map<String, Object> bill = new HashMap<>();
		for (int i = 1; i < billList.length - Variable.POSITION_2; i++) {
			String[] li = billList[i].split(",", -1);
			bill.put(li[Variable.POSITION_6],
					li[Variable.POSITION_6] + '|' + li[Variable.POSITION_9] + '|' + li[Variable.POSITION_12]);
		}

		/** 获取支付平台net流水 */
		Map<String, Object> tmpMap = new HashMap<>();
		tmpMap.put(Variable.PMC_CODE, PmcCode.ZHIFUBAO);
		tmpMap.put(Variable.PMC_DATE, "20170707");
		List<Map<String, Object>> txnList = cs.getAllDayTxnForBill(tmpMap);

		Map<String, Object> map = new HashMap<>();
		for (Map<String, Object> r : txnList) {
			map.put(MapUtil.string(r, Variable.TXN_NO), MapUtil.string(r, Variable.RESULT));
		}

		/** 开始勾兑流水 */
	}

}
