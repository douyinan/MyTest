package com.kayak.cashier.wxpay;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.kayak.frame.ErrorCode;
import com.kayak.frame.SystemException;

public class WXPayRequest {
	private WXPayConfig config;

	public WXPayRequest(WXPayConfig config) throws SystemException {

		this.config = config;
	}

	/**
	 * 请求，只请求一次，不做重试
	 * 
	 * @param domain
	 * @param urlSuffix
	 * @param uuid
	 * @param data
	 * @param connectTimeoutMs
	 * @param readTimeoutMs
	 * @param useCert
	 *            是否使用证书，针对退款、撤销等操作
	 * @return
	 * @throws Exception
	 */
	private String requestOnce(final String domain, String urlSuffix, String uuid, String data, int connectTimeoutMs,
			int readTimeoutMs, boolean useCert) throws SystemException {
		BasicHttpClientConnectionManager connManager;
		if (useCert) {
			// 证书
			char[] password = config.getMchID().toCharArray();
			InputStream certStream = config.getCertStream();
			KeyStore ks;
			SSLContext sslContext;
			try {
				ks = KeyStore.getInstance("PKCS12");
				ks.load(certStream, password);
			} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
				throw new SystemException(ErrorCode.系统错误, e);
			}

			// 实例化密钥库 & 初始化密钥工厂
			KeyManagerFactory kmf;
			try {
				kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, password);
			} catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
				throw new SystemException(ErrorCode.系统错误);
			}

			// 创建 SSLContext
			try {
				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
			} catch (NoSuchAlgorithmException | KeyManagementException e) {
				throw new SystemException(ErrorCode.系统错误);
			}

			SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
					new String[] { "TLSv1" }, null, new DefaultHostnameVerifier());

			connManager = new BasicHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory> create()
					.register("http", PlainConnectionSocketFactory.getSocketFactory())
					.register("https", sslConnectionSocketFactory).build(), null, null, null);
		} else {
			connManager = new BasicHttpClientConnectionManager(
					RegistryBuilder.<ConnectionSocketFactory> create()
							.register("http", PlainConnectionSocketFactory.getSocketFactory())
							.register("https", SSLConnectionSocketFactory.getSocketFactory()).build(),
					null, null, null);
		}

		HttpClient httpClient = HttpClientBuilder.create().setConnectionManager(connManager).build();

		String url = "https://" + domain + urlSuffix;
		HttpPost httpPost = new HttpPost(url);

		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(readTimeoutMs)
				.setConnectTimeout(connectTimeoutMs).build();
		httpPost.setConfig(requestConfig);

		StringEntity postEntity = new StringEntity(data, "UTF-8");
		httpPost.addHeader("Content-Type", "text/xml");
		httpPost.addHeader("User-Agent", "wxpay sdk java v1.0 " + config.getMchID());
		httpPost.setEntity(postEntity);

		HttpResponse httpResponse;
		try {
			httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			return EntityUtils.toString(httpEntity, "UTF-8");
		} catch (IOException e) {
			throw new SystemException(ErrorCode.系统错误);
		}
	}

	private String request(String urlSuffix, String uuid, String data, int connectTimeoutMs, int readTimeoutMs,
			boolean useCert, boolean autoReport) throws SystemException {
		long elapsedTimeMillis = 0;
		long startTimestampMs = WXPayUtil.getCurrentTimestampMs();
		boolean firstHasDnsErr = false;
		boolean firstHasConnectTimeout = false;
		boolean firstHasReadTimeout = false;
		IWXPayDomain.DomainInfo domainInfo = config.getWXPayDomain().getDomain(config);
		if (domainInfo == null) {
			throw new SystemException(ErrorCode.系统错误);
		}
		try {
			String result = requestOnce(domainInfo.getDomain(), urlSuffix, uuid, data, connectTimeoutMs, readTimeoutMs,
					useCert);
			elapsedTimeMillis = WXPayUtil.getCurrentTimestampMs() - startTimestampMs;
			config.getWXPayDomain().report(domainInfo.getDomain(), elapsedTimeMillis, null);
			WXPayReport.getInstance(config).report(uuid, elapsedTimeMillis, domainInfo.getDomain(), domainInfo.isPrimaryDomain(),
					connectTimeoutMs, readTimeoutMs, firstHasDnsErr, firstHasConnectTimeout, firstHasReadTimeout);
			return result;
		} catch (SystemException ex) {
			throw new SystemException(ErrorCode.系统错误);
		}
	}

	/**
	 * 可重试的，非双向认证的请求
	 * 
	 * @param urlSuffix
	 * @param uuid
	 * @param data
	 * @return
	 */
	public String requestWithoutCert(String urlSuffix, String uuid, String data, boolean autoReport)
			throws SystemException {
		return this.request(urlSuffix, uuid, data, config.getHttpConnectTimeoutMs(), config.getHttpReadTimeoutMs(),
				false, autoReport);
	}

	/**
	 * 可重试的，非双向认证的请求
	 * 
	 * @param urlSuffix
	 * @param uuid
	 * @param data
	 * @param connectTimeoutMs
	 * @param readTimeoutMs
	 * @return
	 */
	public String requestWithoutCert(String urlSuffix, String uuid, String data, int connectTimeoutMs,
			int readTimeoutMs, boolean autoReport) throws SystemException {
		return this.request(urlSuffix, uuid, data, connectTimeoutMs, readTimeoutMs, false, autoReport);
	}

	/**
	 * 可重试的，双向认证的请求
	 * 
	 * @param urlSuffix
	 * @param uuid
	 * @param data
	 * @return
	 */
	public String requestWithCert(String urlSuffix, String uuid, String data, boolean autoReport)
			throws SystemException {
		return this.request(urlSuffix, uuid, data, config.getHttpConnectTimeoutMs(), config.getHttpReadTimeoutMs(),
				true, autoReport);
	}

	/**
	 * 可重试的，双向认证的请求
	 * 
	 * @param urlSuffix
	 * @param uuid
	 * @param data
	 * @param connectTimeoutMs
	 * @param readTimeoutMs
	 * @return
	 */
	public String requestWithCert(String urlSuffix, String uuid, String data, int connectTimeoutMs, int readTimeoutMs,
			boolean autoReport) throws SystemException {
		return this.request(urlSuffix, uuid, data, connectTimeoutMs, readTimeoutMs, true, autoReport);
	}
}
