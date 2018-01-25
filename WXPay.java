package com.kayak.cashier.wxpay;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kayak.cashier.modal.Variable;
import com.kayak.cashier.wxpay.WXPayConstants.SignType;
import com.kayak.frame.ErrorCode;
import com.kayak.frame.SystemException;

public class WXPay {

	private WXPayConfig config;
	private SignType signType;
	private boolean autoReport;
	private boolean useSandbox;
	private String notifyUrl;
	private WXPayRequest wxPayRequest;

	public WXPay(final WXPayConfig config) throws SystemException {
		this(config, null, false, false);
	}

	public WXPay(final WXPayConfig config, final boolean autoReport) throws SystemException {
		this(config, null, autoReport, false);
	}

	public WXPay(final WXPayConfig config, final boolean autoReport, final boolean useSandbox) throws SystemException {
		this(config, null, autoReport, useSandbox);
	}

	public WXPay(final WXPayConfig config, final String notifyUrl) throws SystemException {
		this(config, notifyUrl, true, false);
	}

	public WXPay(final WXPayConfig config, final String notifyUrl, final boolean autoReport) throws SystemException {
		this(config, notifyUrl, autoReport, false);
	}

	public WXPay(final WXPayConfig config, final String notifyUrl, final boolean autoReport, final boolean useSandbox)
			throws SystemException {
		this.config = config;
		this.notifyUrl = notifyUrl;
		this.autoReport = autoReport;
		this.useSandbox = useSandbox;
		if (useSandbox) {
			this.signType = SignType.MD5; // 沙箱环境
		} else {
			this.signType = SignType.HMACSHA256;
		}
		this.wxPayRequest = new WXPayRequest(config);
	}

	public void checkWXPayConfig() throws SystemException {
		if (this.config == null) {
			throw new SystemException(ErrorCode.系统错误);
		}
		if (this.config.getAppID() == null || this.config.getAppID().trim().length() == 0) {
			throw new SystemException(ErrorCode.系统错误);
		}
		if (this.config.getMchID() == null || this.config.getMchID().trim().length() == 0) {
			throw new SystemException(ErrorCode.系统错误);
		}
		if (this.config.getCertStream() == null) {
			throw new SystemException(ErrorCode.系统错误);
		}
		if (this.config.getWXPayDomain() == null) {
			throw new SystemException(ErrorCode.系统错误);
		}

		if (this.config.getHttpConnectTimeoutMs() < Variable.POSITION_10) {
			throw new SystemException(ErrorCode.系统错误);
		}
		if (this.config.getHttpReadTimeoutMs() < Variable.POSITION_10) {
			throw new SystemException(ErrorCode.系统错误);
		}

	}

	/**
	 * 向 Map 中添加 appid、mch_id、nonce_str、sign_type、sign <br>
	 * 该函数适用于商户适用于统一下单等接口，不适用于红包、代金券接口
	 *
	 * @param reqData
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> fillRequestData(Map<String, String> reqData) throws SystemException {
		reqData.put(Variable.APPID, config.getAppID());
		reqData.put(Variable.MCH_ID, config.getMchID());
		reqData.put(Variable.NONCE_STR, WXPayUtil.generateUUID());
		if (SignType.MD5.equals(this.signType)) {
			reqData.put(Variable.SIGN_TYPE, WXPayConstants.MD5);
		} else if (SignType.HMACSHA256.equals(this.signType)) {
			reqData.put(Variable.SIGN_TYPE, WXPayConstants.HMACSHA256);
		}
		reqData.put(Variable.SIGN, WXPayUtil.generateSignature(reqData, config.getKey(), this.signType));
		return reqData;
	}

	/** 生成签名另一种方法 */
	public Map<String, String> sign(Map<String, String> params, String key) {
		List<String> names = new ArrayList<>(params.keySet());
		Collections.sort(names);

		String d = "";
		StringBuffer sb = new StringBuffer();
		for (String name : names) {
			if (Variable.SIGN.equals(name))
				continue;
			String value = (String) params.get(name);
			if (value == null || value.isEmpty())
				continue;

			sb.append(d).append(name).append("=").append(value);
			d = "&";
		}
		sb.append(d).append("key=").append(key);

		String s = sb.toString();

		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(s.getBytes("UTF-8"));
			String t = new BigInteger(1, md.digest()).toString(Variable.POSITION_16);
			params.put(Variable.SIGN, t);

		} catch (Exception e) {
		}
		return params;
	}

	/**
	 * 判断xml数据的sign是否有效，必须包含sign字段，否则返回false。
	 *
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return 签名是否有效
	 * @throws Exception
	 */
	public boolean isResponseSignatureValid(Map<String, String> reqData) throws SystemException {
		if (!reqData.containsKey(Variable.SIGN)) {
			return true;
		} else {
			// 返回数据的签名方式和请求中给定的签名方式是一致的
			return WXPayUtil.isSignatureValid(reqData, this.config.getKey(), this.signType);
		}
	}

	/**
	 * 判断支付结果通知中的sign是否有效
	 *
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return 签名是否有效
	 * @throws Exception
	 */
	public boolean isPayResultNotifySignatureValid(Map<String, String> reqData) throws SystemException {
		String signTypeInData = reqData.get(WXPayConstants.FIELD_SIGN_TYPE);
		if (signTypeInData == null) {
			this.signType = SignType.MD5;
		} else {
			signTypeInData = signTypeInData.trim();
			if (signTypeInData.length() == 0) {
				this.signType = SignType.MD5;
			} else if (WXPayConstants.MD5.equals(signTypeInData)) {
				this.signType = SignType.MD5;
			} else if (WXPayConstants.HMACSHA256.equals(signTypeInData)) {
				this.signType = SignType.HMACSHA256;
			} else {
				throw new SystemException(ErrorCode.系统错误);
			}
		}
		return WXPayUtil.isSignatureValid(reqData, this.config.getKey(), this.signType);
	}

	/**
	 * 不需要证书的请求
	 * 
	 * @param urlSuffix
	 *            String
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public String requestWithoutCert(String urlSuffix, Map<String, String> reqData, int connectTimeoutMs,
			int readTimeoutMs) throws SystemException {
		String msgUUID = reqData.get(Variable.NONCE_STR);
		String reqBody = WXPayUtil.mapToXml(reqData);

		String resp = this.wxPayRequest.requestWithoutCert(urlSuffix, msgUUID, reqBody, connectTimeoutMs, readTimeoutMs,
				autoReport);
		return resp;
	}

	/**
	 * 需要证书的请求
	 * 
	 * @param urlSuffix
	 *            String
	 * @param reqData
	 *            向wxpay post的请求数据 Map
	 * @param connectTimeoutMs
	 *            超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public String requestWithCert(String urlSuffix, Map<String, String> reqData, int connectTimeoutMs,
			int readTimeoutMs) throws SystemException {
		String msgUUID = reqData.get(Variable.NONCE_STR);
		String reqBody = WXPayUtil.mapToXml(reqData);

		String resp = this.wxPayRequest.requestWithCert(urlSuffix, msgUUID, reqBody, connectTimeoutMs, readTimeoutMs,
				this.autoReport);
		return resp;
	}

	/**
	 * 处理 HTTPS API返回数据，转换成Map对象。return_code为SUCCESS时，验证签名。
	 * 
	 * @param xmlStr
	 *            API返回的XML格式数据
	 * @return Map类型数据
	 * @throws Exception
	 */
	public Map<String, String> processResponseXml(String xmlStr) throws SystemException {
		String returnCode;
		Map<String, String> respData = WXPayUtil.xmlToMap(xmlStr);
		if (respData.containsKey(Variable.RETURN_CODE)) {
			returnCode = respData.get(Variable.RETURN_CODE);
		} else {
			throw new SystemException(ErrorCode.系统错误);
		}

		if (returnCode.equals(WXPayConstants.FAIL)) {
			return respData;
		} else if (returnCode.equals(WXPayConstants.SUCCESS)) {
			if (this.isResponseSignatureValid(respData)) {
				return respData;
			} else {
				throw new SystemException(ErrorCode.系统错误);
			}
		} else {
			throw new SystemException(ErrorCode.系统错误);
		}
	}

	/**
	 * 作用：提交刷卡支付<br>
	 * 场景：刷卡支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> microPay(Map<String, String> reqData) throws SystemException {
		return this.microPay(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：提交刷卡支付<br>
	 * 场景：刷卡支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> microPay(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_MICROPAY_URL_SUFFIX;
		} else {
			url = WXPayConstants.MICROPAY_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 提交刷卡支付，针对软POS，尽可能做成功 内置重试机制，最多60s
	 * 
	 * @param reqData
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> microPayWithPos(Map<String, String> reqData) throws SystemException {
		return this.microPayWithPos(reqData, this.config.getHttpConnectTimeoutMs());
	}

	/**
	 * 提交刷卡支付，针对软POS，尽可能做成功 内置重试机制，最多60s
	 * 
	 * @param reqData
	 * @param connectTimeoutMs
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> microPayWithPos(Map<String, String> reqData, int connectTimeoutMs)
			throws SystemException {
		int remainingTimeMs = Variable.POSITION_60 * Variable.POSITION_1000;
		long startTimestampMs = 0;
		Map<String, String> lastResult = null;

		while (true) {
			startTimestampMs = WXPayUtil.getCurrentTimestampMs();
			int readTimeoutMs = remainingTimeMs - connectTimeoutMs;
			if (readTimeoutMs > Variable.POSITION_1000) {
				try {
					lastResult = this.microPay(reqData, connectTimeoutMs, readTimeoutMs);
					String returnCode = lastResult.get("return_code");
					if (returnCode.equals("SUCCESS")) {
						String resultCode = lastResult.get("result_code");
						String errCode = lastResult.get("err_code");
						if (resultCode.equals("SUCCESS")) {
							break;
						} else {
							// 看错误码，若支付结果未知，则重试提交刷卡支付
							if (errCode.equals("SYSTEMERROR") || errCode.equals("BANKERROR")
									|| errCode.equals("USERPAYING")) {
								remainingTimeMs = remainingTimeMs
										- (int) (WXPayUtil.getCurrentTimestampMs() - startTimestampMs);
								if (remainingTimeMs <= Variable.POSITION_100) {
									break;
								} else {
									WXPayUtil.getLogger().info("microPayWithPos: try micropay again");
									if (remainingTimeMs > Variable.POSITION_5 * Variable.POSITION_1000) {
										Thread.sleep(Variable.POSITION_5 * Variable.POSITION_1000);
									} else {
										Thread.sleep(Variable.POSITION_1 * Variable.POSITION_1000);
									}
									continue;
								}
							} else {
								break;
							}
						}
					} else {
						break;
					}
				} catch (Exception ex) {
					lastResult = null;
				}
			} else {
				break;
			}
		}

		if (lastResult == null) {
			throw new SystemException(ErrorCode.系统错误);
		} else {
			return lastResult;
		}
	}

	/**
	 * 作用：统一下单<br>
	 * 场景：公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> unifiedOrder(Map<String, String> reqData) throws SystemException {
		return this.unifiedOrder(reqData, config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：统一下单<br>
	 * 场景：公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> unifiedOrder(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_UNIFIEDORDER_URL_SUFFIX;
		} else {
			url = WXPayConstants.UNIFIEDORDER_URL_SUFFIX;
		}
		if (this.notifyUrl != null) {
			reqData.put(Variable.NOTIFY_URL, this.notifyUrl);
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：查询订单<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> orderQuery(Map<String, String> reqData) throws SystemException {
		return this.orderQuery(reqData, config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：查询订单<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据 int
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> orderQuery(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_ORDERQUERY_URL_SUFFIX;
		} else {
			url = WXPayConstants.ORDERQUERY_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：撤销订单<br>
	 * 场景：刷卡支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> reverse(Map<String, String> reqData) throws SystemException {
		return this.reverse(reqData, config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：撤销订单<br>
	 * 场景：刷卡支付<br>
	 * 其他：需要证书
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> reverse(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_REVERSE_URL_SUFFIX;
		} else {
			url = WXPayConstants.REVERSE_URL_SUFFIX;
		}
		String respXml = this.requestWithCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：关闭订单<br>
	 * 场景：公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> closeOrder(Map<String, String> reqData) throws SystemException {
		return this.closeOrder(reqData, config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：关闭订单<br>
	 * 场景：公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> closeOrder(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_CLOSEORDER_URL_SUFFIX;
		} else {
			url = WXPayConstants.CLOSEORDER_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：申请退款<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> refund(Map<String, String> reqData) throws SystemException {
		return this.refund(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：申请退款<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
	 * 其他：需要证书
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> refund(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url = "";
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_REFUND_URL_SUFFIX;
		} else {
			url = WXPayConstants.REFUND_URL_SUFFIX;
		}
		String respXml = this.requestWithCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：退款查询<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> refundQuery(Map<String, String> reqData) throws SystemException {
		return this.refundQuery(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：退款查询<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> refundQuery(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_REFUNDQUERY_URL_SUFFIX;
		} else {
			url = WXPayConstants.REFUNDQUERY_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：对账单下载（成功时返回对账单数据，失败时返回XML格式数据）<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> downloadBill(Map<String, String> reqData) throws SystemException {
		return this.downloadBill(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：对账单下载<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
	 * 其他：无论是否成功都返回Map。若成功，返回的Map中含有return_code、return_msg、data，
	 * 其中return_code为`SUCCESS`，data为对账单数据。
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return 经过封装的API返回数据
	 * @throws Exception
	 */
	public Map<String, String> downloadBill(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_DOWNLOADBILL_URL_SUFFIX;
		} else {
			url = WXPayConstants.DOWNLOADBILL_URL_SUFFIX;
		}
		String respStr = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs)
				.trim();
		Map<String, String> ret;
		// 出现错误，返回XML数据
		if (respStr.indexOf("<") == 0) {
			ret = WXPayUtil.xmlToMap(respStr);
		} else {
			// 正常返回csv数据
			ret = new HashMap<>();
			ret.put(Variable.RETURN_CODE, WXPayConstants.SUCCESS);
			ret.put(Variable.RETURN_MSG, "ok");
			ret.put(Variable.DATA, respStr);
		}
		return ret;
	}

	/**
	 * 作用：交易保障<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> report(Map<String, String> reqData) throws SystemException {
		return this.report(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：交易保障<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> report(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_REPORT_URL_SUFFIX;
		} else {
			url = WXPayConstants.REPORT_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return WXPayUtil.xmlToMap(respXml);
	}

	/**
	 * 作用：转换短链接<br>
	 * 场景：刷卡支付、扫码支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> shortUrl(Map<String, String> reqData) throws SystemException {
		return this.shortUrl(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：转换短链接<br>
	 * 场景：刷卡支付、扫码支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> shortUrl(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_SHORTURL_URL_SUFFIX;
		} else {
			url = WXPayConstants.SHORTURL_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：授权码查询OPENID接口<br>
	 * 场景：刷卡支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> authCodeToOpenid(Map<String, String> reqData) throws SystemException {
		return this.authCodeToOpenid(reqData, this.config.getHttpConnectTimeoutMs(),
				this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：授权码查询OPENID接口<br>
	 * 场景：刷卡支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> authCodeToOpenid(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url;
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_AUTHCODETOOPENID_URL_SUFFIX;
		} else {
			url = WXPayConstants.AUTHCODETOOPENID_URL_SUFFIX;
		}
		String respXml = this.requestWithoutCert(url, this.fillRequestData(reqData), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：下属商户录入<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> addMcht(Map<String, String> reqData) throws SystemException {
		return this.addMcht(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：下属商户录入<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
	 * 其他：需要证书
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> addMcht(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url = "";
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_ADDMCHT_URL_SUFFIX;
		} else {
			url = WXPayConstants.ADDMCHT_URL_SUFFIX;
		}
		String respXml = this.requestWithCert(url, sign(reqData, config.getKey()), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

	/**
	 * 作用：下属商户查询<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> queryMcht(Map<String, String> reqData) throws SystemException {
		return this.queryMcht(reqData, this.config.getHttpConnectTimeoutMs(), this.config.getHttpReadTimeoutMs());
	}

	/**
	 * 作用：下属商户查询<br>
	 * 场景：刷卡支付、公共号支付、扫码支付、APP支付<br>
	 * 其他：需要证书
	 * 
	 * @param reqData
	 *            向wxpay post的请求数据
	 * @param connectTimeoutMs
	 *            连接超时时间，单位是毫秒
	 * @param readTimeoutMs
	 *            读超时时间，单位是毫秒
	 * @return API返回数据
	 * @throws Exception
	 */
	public Map<String, String> queryMcht(Map<String, String> reqData, int connectTimeoutMs, int readTimeoutMs)
			throws SystemException {
		String url = "";
		if (this.useSandbox) {
			url = WXPayConstants.SANDBOX_QUERYMCHT_URL_SUFFIX;
		} else {
			url = WXPayConstants.QUERYMCHT_URL_SUFFIX;
		}
		String respXml = this.requestWithCert(url, sign(reqData, config.getKey()), connectTimeoutMs, readTimeoutMs);
		return this.processResponseXml(respXml);
	}

}
