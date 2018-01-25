package com.kayak.cashier.wxpay;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kayak.cashier.dao.TxnDao;
import com.kayak.cashier.modal.Status;
import com.kayak.cashier.modal.Variable;
import com.kayak.frame.Launcher;
import com.kayak.frame.Service;
import com.kayak.frame.SystemException;
import com.kayak.frame.dao.DaoService;
import com.kayak.frame.util.MapUtil;
import com.kayak.frame.util.options.Options;

public class WxpayService implements Service {
	private static final Logger log = LoggerFactory.getLogger(WxpayService.class);
	private static WXPay pay;
	private ScheduledExecutorService ses;
	private TxnDao td;
	private static Map<String, Object> realParamsMap;

	private String SUCCESS = "SUCCESS";
	private String FAIL = "FAIL";
	private String USERPAYING = "USERPAYING";

	/**
	 * 支付配置文件加载
	 * 
	 * @param bizmap
	 *            请求参数Map
	 */
	static {
		try {
			WxConfig config = new WxConfig();
			realParamsMap = new HashMap<>();
			pay = new WXPay(config);
		} catch (Exception e) {
			log.info("错误信息：" + e);
		}

	}

	@Override
	public String getName() {
		return "微信支付服务";
	}

	@Override
	public void initialize(Options options) throws SystemException {
		ses = Executors.newScheduledThreadPool(Variable.POSITION_5);
		DaoService ds = Launcher.get(DaoService.class);
		td = ds.getDao(TxnDao.class);
	}

	@Override
	public void reload(Options options) throws SystemException {
		/** 重新加载 */
	}

	/**
	 * 微信统一下单
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayUnifiedOrder(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.SUB_OPENID, params.get(Variable.OPEN_ID));
		realParamsMap.put(Variable.BODY, params.get(Variable.SUBJECT));
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.TXN_NO));
		Float amt = Float.parseFloat(params.get(Variable.AMT).toString()) * Variable.POSITION_100;
		realParamsMap.put(Variable.TOTAL_FEE, amt.intValue());
		realParamsMap.put(Variable.TRADE_TYPE, "JSAPI");
		realParamsMap.put(Variable.SPBILL_CREATE_IP, params.get(Variable.DEVICE_IP));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));
		realParamsMap.put(Variable.NOTIFY_URL, params.get(Variable.NOTIFY_URL) + "/cashier/UnifiedOrderWxPayNotify");

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求统一下单参数：" + data);
		data = pay.unifiedOrder(data);
		log.info("微信返回请求统一下单参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_PROCESSING);
				params.put(Variable.PMC_TRAN_NO, data.get(Variable.PREPAY_ID));

				WxConfig config = new WxConfig();
				String timeStamp = String.valueOf(System.currentTimeMillis());
				String appId = config.getAppID();
				String nonceStr = getRandomString(Variable.POSITION_32);
				String wxPackage = "prepay_id=" + data.get(Variable.PREPAY_ID).toString();
				String key = config.getKey();

				try {
					StringBuffer sb = new StringBuffer();
					sb.append("appId=").append(appId).append("&nonceStr=").append(nonceStr).append("&package=")
							.append(wxPackage).append("signType=MD5").append("&timeStamp=").append(timeStamp)
							.append("&key=").append(key);
					String s = sb.toString();
					MessageDigest md = MessageDigest.getInstance("MD5");
					md.update(s.getBytes("UTF-8"));
					String t = new BigInteger(1, md.digest()).toString(Variable.POSITION_16);

					String pmcSign = "paySign=" + t + "&nonceStr=" + nonceStr + "&package=" + wxPackage + "&timeStamp="
							+ timeStamp;
					result.put(Variable.PMC_SIGN, pmcSign);

				} catch (Exception e) {
				}

			} else {
				params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			return Status.FAIL;
		}

	}

	/**
	 * 微信刷卡支付
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayMicroPay(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.BODY, params.get(Variable.SUBJECT));
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.TXN_NO));
		Float amt = Float.parseFloat(params.get(Variable.AMT).toString()) * Variable.POSITION_100;
		realParamsMap.put(Variable.TOTAL_FEE, amt.intValue());
		realParamsMap.put(Variable.AUTH_CODE, params.get(Variable.AUTH_CODE));
		realParamsMap.put(Variable.SPBILL_CREATE_IP, params.get(Variable.CREATE_IP));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求刷卡支付参数：" + data);
		data = pay.microPay(data);
		log.info("微信返回请求刷卡支付参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
				params.put(Variable.TXN_STEP, Status.PAYED);
				params.put(Variable.PMC_DATE, data.get(Variable.TIME_END).substring(0, Variable.POSITION_8));
				params.put(Variable.PMC_TRAN_NO, data.get(Variable.TRANSACTION_ID));
			} else {
				if (data.get(Variable.ERR_CODE) != null && data.get(Variable.ERR_CODE).equals(this.USERPAYING)) {
					params.put(Variable.DEAL_STATUS, Status.DEAL_PROCESSING);
					params.put(Variable.TXN_STEP, Status.UNPAY);
					wxpayAsyncQuery(params);
				} else {
					params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
					params.put(Variable.TXN_STEP, Status.UNPAY);
				}
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			params.put(Variable.TXN_STEP, Status.UNPAY);
			return Status.FAIL;
		}
	}

	/**
	 * 微信订单退款
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayRefund(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.OUT_REFUND_NO, params.get(Variable.TXN_NO));
		realParamsMap.put(Variable.TRANSACTION_ID, params.get(Variable.PMC_TRAN_NO));
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.ORG_TXN_NO));
		Float amt = Float.parseFloat(params.get(Variable.AMT).toString()) * Variable.POSITION_100;
		realParamsMap.put(Variable.TOTAL_FEE, amt.intValue());
		realParamsMap.put(Variable.REFUND_FEE, amt.intValue());
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求订单退款参数：" + data);
		data = pay.refund(data);
		log.info("微信返回请求订单退款参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
				params.put(Variable.PMC_TRAN_NO, data.get(Variable.REFUND_ID));
			} else {
				params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			return Status.FAIL;
		}
	}

	/**
	 * 微信订单撤销(只用于刷卡支付反扫)
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayReverse(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.TRANSACTION_ID, params.get(Variable.PMC_TRAN_NO));
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.ORG_TXN_NO));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求订单撤销参数：" + data);
		data = pay.reverse(data);
		log.info("微信返回请求订单撤销参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
				params.put(Variable.TXN_STEP, Status.REVERSED);
			} else {
				params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
				params.put(Variable.TXN_STEP, Status.UNPAY);
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			params.put(Variable.TXN_STEP, Status.UNPAY);
			return Status.FAIL;
		}
	}

	/**
	 * 微信订单关闭(只用于公众号支付正扫)
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayClose(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.ORG_TXN_NO));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求订单关闭参数：" + data);
		data = pay.closeOrder(data);
		log.info("微信返回请求订单关闭参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
				params.put(Variable.TXN_STEP, Status.REVERSED);
			} else {
				params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
				params.put(Variable.TXN_STEP, Status.UNPAY);
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			params.put(Variable.TXN_STEP, Status.UNPAY);
			return Status.FAIL;
		}
	}

	/**
	 * 微信订单查询
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayQuery(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.TRANSACTION_ID, params.get(Variable.PMC_TRAN_NO));
		realParamsMap.put(Variable.OUT_TRADE_NO, params.get(Variable.TXN_NO));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.CHILD_MCHT_NO));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}
		log.info("向微信发起订单状态查询参数：" + data);
		data = pay.orderQuery(data);
		log.info("收到向微信发起订单状态查询结果：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.TXN_STATUS, Status.SUCCESS);
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				if (data.get(Variable.TRADE_STATE) != null && data.get(Variable.TRADE_STATE).equals(this.USERPAYING)) {
					params.put(Variable.DEAL_STATUS, Status.DEAL_PROCESSING);
				} else {
					params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
					params.put(Variable.PMC_DATE, data.get(Variable.TIME_END).substring(0, Variable.POSITION_8));
					params.put(Variable.PMC_TRAN_NO, data.get(Variable.TRANSACTION_ID));
				}
			} else if (data.get(Variable.RESULT_CODE).equals(this.FAIL)) {
				params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			} else {
				params.put(Variable.DEAL_STATUS, Status.DEAL_PROCESSING);
			}
			return Status.SUCCESS;
		} else {
			params.put(Variable.TXN_STATUS, Status.FAIL);
			params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
			return Status.FAIL;
		}
	}

	/**
	 * 微信新增商户
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayAddMcht(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		WxConfig config = new WxConfig();
		realParamsMap.clear();
		realParamsMap.put(Variable.APPID, config.getAppID());
		realParamsMap.put(Variable.MCH_ID, config.getMchID());
		realParamsMap.put(Variable.MERCHANT_NAME, params.get(Variable.NAME));
		realParamsMap.put(Variable.MERCHANT_SHORTNAME, params.get(Variable.BRIEF));
		realParamsMap.put(Variable.SERVICE_PHONE, params.get(Variable.PHONE));
		realParamsMap.put(Variable.CHANNEL_ID, "38405265");//TODO 改配置
		realParamsMap.put(Variable.BUSINESS, params.get(Variable.PMC_BUSI_CODE));
		realParamsMap.put(Variable.MERCHANT_REMARK, params.get(Variable.REMARK));

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求新增商户参数：" + data);
		data = pay.addMcht(data);
		log.info("微信返回请求新增商户参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			if (data.get(Variable.RESULT_CODE).equals(this.SUCCESS)) {
				params.put(Variable.RETURN_CODE, data.get(Variable.RESULT_CODE));
				params.put(Variable.RETURN_MSG, data.get(Variable.RESULT_MSG));
				params.put(Variable.CONTRACT_NO, data.get(Variable.SUB_MCH_ID));
				return Status.SUCCESS;
			} else {
				params.put(Variable.RETURN_CODE, data.get(Variable.RESULT_CODE));
				params.put(Variable.RETURN_MSG, data.get(Variable.RESULT_MSG));
				return Status.FAIL;
			}
			
		} else {
			return Status.FAIL;
		}
	}

	/**
	 * 微信商户查询
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public void wxpayQueryMcht(Map<String, Object> result, Map<String, Object> params) throws SystemException {
		WxConfig config = new WxConfig();
		realParamsMap.clear();
		realParamsMap.put(Variable.APPID, config.getAppID());
		realParamsMap.put(Variable.MCH_ID, config.getMchID());
		realParamsMap.put(Variable.MERCHANT_NAME, params.get(Variable.MERCHANT_NAME));
		realParamsMap.put(Variable.SUB_MCH_ID, params.get(Variable.SUB_MCH_ID));
		realParamsMap.put(Variable.PAGE_INDEX, "1");

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求商户查询参数：" + data);
		data = pay.queryMcht(data);
		log.info("微信返回请求商户查询参数：" + data);

		for (String key : data.keySet()) {
			result.put(key, data.get(key));
		}
	}

	/**
	 * 微信对账单下载
	 * 
	 * @param params
	 * @return
	 * @throws Exception
	 */
	public String wxpayDownloadBill(Map<String, Object> result, Map<String, Object> params) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.BILL_DATE, MapUtil.string(params, Variable.BILL_DATE));
		realParamsMap.put(Variable.BILL_TYPE, "SUCCESS");

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求对账单下载参数：" + data);
		data = pay.downloadBill(data);
		log.info("微信返回请求对账单下载参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			params.put(Variable.DATA, data.get(Variable.DATA));
			return Status.SUCCESS;
		} else {
			return Status.FAIL;
		}
	}
	
	/**
	 * 获取微信对账账单
	 * @param billDate:对账日期
	 * @param billType:账单类型,ALL，返回当日所有订单信息，默认值
							 SUCCESS，返回当日成功支付的订单
							 REFUND，返回当日退款订单
							 RECHARGE_REFUND，返回当日充值退款订单（相比其他对账单多一栏“返还手续费”）
	 * @return:账单内容
	 * @throws SystemException
	 */
	public String wxpayDownloadBill(String billDate, String billType) throws SystemException {

		realParamsMap.clear();
		realParamsMap.put(Variable.BILL_DATE, billDate);
		realParamsMap.put(Variable.BILL_TYPE, billType);

		Map<String, String> data = new HashMap<>();
		for (String key : realParamsMap.keySet()) {
			data.put(key, realParamsMap.get(key) == null ? "" : realParamsMap.get(key).toString());
		}

		log.info("发往微信请求对账单下载参数：" + data);
		data = pay.downloadBill(data);
		log.info("微信返回请求对账单下载参数：" + data);

		if (data.get(Variable.RETURN_CODE).equals(this.SUCCESS)) {
			return data.get(Variable.DATA);
		} else {
			return "";
		}
	}

	public static String getRandomString(int length) {
		String base = "abcdefghijklmnopqrstuvwxyz0123456789";
		Random random = new Random();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < length; i++) {
			int number = random.nextInt(base.length());
			sb.append(base.charAt(number));
		}
		return sb.toString();
	}

	// ===========================================================

	/** 微信反扫：刷卡支付，异步发起查询 */
	/** 应用场景：用户正在输入密码（USERPAYING） */
	public void wxpayAsyncQuery(Map<String, Object> params) throws SystemException {
		QueryPayStatusTask task = new QueryPayStatusTask();

		task.params = params;
		params.put(Variable.QUERIED_TIMES, Variable.POSITION_10);
		/** 开启定时任务，异步上微信查询 */
		ses.schedule(task, Variable.POSITION_5, TimeUnit.SECONDS);
	}

	private class QueryPayStatusTask implements Runnable {
		private Map<String, Object> params;

		@Override
		public void run() {
			params.put(Variable.QUERIED_TIMES, MapUtil.integer(params, Variable.QUERIED_TIMES) - 1);
			log.info("向微信发起轮询查询订单状态：剩余" + MapUtil.integer(params, Variable.QUERIED_TIMES) + "次");
			Map<String, Object> result = new HashMap<>();
			try {
				wxpayQuery(result, params);
				if (!MapUtil.string(params, Variable.DEAL_STATUS).equals(Status.DEAL_SUCCESS)
						&& !MapUtil.string(params, Variable.DEAL_STATUS).equals(Status.DEAL_FAIL)) {
					if (MapUtil.integer(params, Variable.QUERIED_TIMES) > 0) {
						/** 没查到终态，且还有查询次数，再次发起查询 */
						ses.schedule(this, Variable.POSITION_5, TimeUnit.SECONDS);
					}
				} else {
					if (MapUtil.string(params, Variable.DEAL_STATUS).equals(Status.DEAL_SUCCESS)) {
						params.put(Variable.TXN_STATUS, Status.SUCCESS);
						params.put(Variable.DEAL_STATUS, Status.DEAL_SUCCESS);
						params.put(Variable.TXN_STEP, Status.PAYED);
					} else {
						params.put(Variable.TXN_STATUS, Status.SUCCESS);
						params.put(Variable.DEAL_STATUS, Status.DEAL_FAIL);
						params.put(Variable.TXN_STEP, Status.UNPAY);
					}
					td.updateTxn(params);
				}
			} catch (Exception e) {
				log.info("错误信息：" + e);
			}
		}
	}

}
