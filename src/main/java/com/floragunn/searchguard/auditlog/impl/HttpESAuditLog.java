/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use. 
 * For commercial use in a production environment you have to obtain a license 
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.auditlog.impl;

import java.io.IOException;
import java.util.Arrays;

import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Provider;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;

import com.floragunn.searchguard.httpclient.HttpClient;
import com.floragunn.searchguard.httpclient.HttpClient.HttpClientBuilder;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

public final class HttpESAuditLog extends AbstractAuditLog {

	// config in elasticsearch.yml

	protected final ESLogger log = Loggers.getLogger(this.getClass());
	private final String index;
	private final String type;
	private final HttpClient client;
	private final String[] servers;

	public HttpESAuditLog(final Settings settings,
	        final IndexNameExpressionResolver resolver, final Provider<ClusterService> clusterService) throws Exception {

		super(settings, resolver, clusterService);

		Settings auditSettings = settings.getAsSettings("searchguard.audit.config");

		servers = auditSettings.getAsArray("http_endpoints", new String[] { "localhost:9200" });
		this.index = auditSettings.get("index", "auditlog");
		this.type = auditSettings.get("type", "auditlog");
		boolean verifyHostnames = auditSettings.getAsBoolean("verify_hostnames", true);
		boolean enableSsl = auditSettings.getAsBoolean("enable_ssl", false);
		boolean enableSslClientAuth = auditSettings.getAsBoolean("enable_ssl_client_auth", false);
		String user = auditSettings.get("username");
		String password = auditSettings.get("password");

		HttpClientBuilder builder = HttpClient.builder(servers);
		Environment env = new Environment(settings);

		if (enableSsl) {
			builder.enableSsl(
					env.configFile().resolve(settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH)).toFile(),
					settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD, "changeit"), verifyHostnames);

			if (enableSslClientAuth) {
				builder.setPkiCredentials(
						env.configFile().resolve(settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH)).toFile(),
						settings.get(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, "changeit"));
			}
		}

		if (user != null && password != null) {
			builder.setBasicCredentials(user, password);
		}

		client = builder.build();
	}

	@Override
	public void close() throws IOException {
		if (client != null) {
			client.close();
		}
	}

	@Override
	protected void save(final AuditMessage msg) {
		try {
			boolean successful = client.index(msg.toString(), index, type, true);

			if (!successful) {
				log.error("Unable to send audit log {} to one of these servers: {}", msg, Arrays.toString(servers));
			}
		} catch (Exception e) {
			log.error("Unable to send audit log {} due to {}", e, msg, e.toString());
		}
	}
}
