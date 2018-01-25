package com.kayak.cashier.wxpay;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import com.kayak.cashier.modal.Variable;
import com.kayak.frame.ErrorCode;
import com.kayak.frame.SystemException;

public class WxConfig extends WXPayConfig {
	private byte[] certData;

	public WxConfig() throws SystemException {
		try {
			File file = new File(
					new File(WxConfig.class.getResource("/").toURI()).getParentFile().getParentFile().getAbsolutePath()
							+ "/WEB-INF/cer/apiclient_cert.p12");
			InputStream certStream = null;

			try {
				certStream = new FileInputStream(file);
				this.certData = new byte[(int) file.length()];
				int b = certStream.read(this.certData);
				if (b == 0) {
					throw new SystemException(ErrorCode.系统错误);
				}
			} finally {
				if (certStream != null)
					certStream.close();
			}
		} catch (IOException | URISyntaxException e) {
			throw new SystemException(ErrorCode.系统错误, e);
		}
	}

	public String getAppID() {
		return "wxdace645e0bc2c424";
	}

	public String getMchID() {
		return "1900008971";
	}

	public String getKey() {
		return "3ACA91426F056322E053645AA8C0CC12";
	}

	public InputStream getCertStream() {
		ByteArrayInputStream certBis = new ByteArrayInputStream(this.certData);
		return certBis;
	}

	public int getHttpConnectTimeoutMs() {
		return Variable.POSITION_8000;
	}

	public int getHttpReadTimeoutMs() {
		return Variable.POSITION_10000;
	}

	IWXPayDomain getWXPayDomain() {
		return WXPayDomainSimpleImpl.instance();
	}

	public String getPrimaryDomain() {
		return "api.mch.weixin.qq.com";
	}

	public String getAlternateDomain() {
		return "api2.mch.weixin.qq.com";
	}

	@Override
	public int getReportWorkerNum() {
		return Variable.POSITION_1;
	}

	@Override
	public int getReportBatchSize() {
		return Variable.POSITION_2;
	}
}
