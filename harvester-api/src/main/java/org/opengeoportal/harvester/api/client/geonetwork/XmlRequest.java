package org.opengeoportal.harvester.api.client.geonetwork;

import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.protocol.Protocol;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.opengeoportal.harvester.api.client.geonetwork.exception.BadSoapResponseEx;
import org.opengeoportal.harvester.api.client.geonetwork.exception.BadXmlResponseEx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Utility class used in {@link GeoNetworkClient} to send requests to the
 * GeoNetwork server.
 * 
 * Code from GeoNetwork opensource project.
 * 
 */
public class XmlRequest {
	private static final Namespace NAMESPACE_ENV = Namespace.getNamespace(
			"env", "http://www.w3.org/2003/05/soap-envelope");

	public enum Method {
		GET, POST
	}

	public XmlRequest() {
		this(null, 80);
	}

	public XmlRequest(String host) {
		this(host, 80);
	}

	public XmlRequest(String host, int port) {
		this(host, port, "http");
	}

	public XmlRequest(String host, int port, String protocol) {
		this.host = host;
		this.port = port;
		this.protocol = protocol;

		setMethod(Method.GET);
		state.addCookie(cookie);
		client.setState(state);
		client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
		client.setHostConfiguration(config);
		List<String> authPrefs = new ArrayList<String>(2);
		authPrefs.add(AuthPolicy.DIGEST);
		authPrefs.add(AuthPolicy.BASIC);
		// This will exclude the NTLM authentication scheme
		client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY,
				authPrefs);
	}

	/**
	 * Build a {@link XmlRequest} based on the URL passed.
	 * @param url the URL to be requested.
	 */
	public XmlRequest(URL url) {
		this(url.getHost(), url.getPort() == -1 ? url.getDefaultPort() : url
				.getPort(), url.getProtocol());

		address = url.getPath();
		query = url.getQuery();
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getAddress() {
		return address;
	}

	public Method getMethod() {
		return method;
	}

	public String getSentData() {
		return sentData;
	}

	public String getReceivedData() {
		return receivedData;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	// ---------------------------------------------------------------------------

	public void setUrl(URL url) {
		host = url.getHost();
		port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();
		protocol = url.getProtocol();
		address = url.getPath();
		query = url.getQuery();
	}

	public void setMethod(Method m) {
		method = m;
	}

	public void setUseSOAP(boolean yesno) {
		useSOAP = yesno;
	}

	public void setUseProxy(boolean yesno) {
		useProxy = yesno;
	}

	public void setProxyHost(String host) {
		proxyHost = host;
	}

	public void setProxyPort(int port) {
		proxyPort = port;
	}

	public void setProxyCredentials(String username, String password) {
		if (username == null || username.trim().length() == 0)
			return;

		Credentials cred = new UsernamePasswordCredentials(username, password);
		AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT,
				AuthScope.ANY_REALM);

		client.getState().setProxyCredentials(scope, cred);

		proxyAuthent = true;
	}

	public void clearParams() {
		alSimpleParams.clear();
		postParams = null;
	}

	public void addParam(String name, Object value) {
		if (value != null)
			alSimpleParams.add(new NameValuePair(name, value.toString()));

		method = Method.GET;
	}

	public void setRequest(Element request) {
		postParams = (Element) request.detach();
		method = Method.POST;
	}

	/**
	 * Sends an xml request and obtains an xml response
	 */
	public Element execute(Element request) throws IOException,
			BadXmlResponseEx, BadSoapResponseEx {
		setRequest(request);
		return execute();
	}

	/**
	 * Sends a request and obtains an xml response. The request can be a GET or
	 * a POST depending on the method used to set parameters. Calls to the
	 * 'addParam' method set a GET request while the setRequest method sets a
	 * POST/xml request.
	 */
	public Element execute() throws IOException, BadXmlResponseEx,
			BadSoapResponseEx {
		HttpMethodBase httpMethod = setupHttpMethod();

		Element response = doExecute(httpMethod);

		if (useSOAP)
			response = soapUnembed(response);

		return response;
	}

	/**
	 * Sends the content of a file using a POST request and gets the response in
	 * xml format.
	 */
	public Element send(String name, File inFile) throws IOException,
			BadXmlResponseEx, BadSoapResponseEx {
		Part[] parts = new Part[alSimpleParams.size() + 1];

		int partsIndex = 0;

		parts[partsIndex] = new FilePart(name, inFile);

		for (NameValuePair nv : alSimpleParams)
			parts[++partsIndex] = new StringPart(nv.getName(), nv.getValue());

		PostMethod post = new PostMethod();
		post.setRequestEntity(new MultipartRequestEntity(parts, post
				.getParams()));
		post.addRequestHeader("Accept", !useSOAP ? "application/xml"
				: "application/soap+xml");
		post.setPath(address);
		post.setDoAuthentication(useAuthent());

		// --- execute request

		Element response = doExecute(post);

		if (useSOAP)
			response = soapUnembed(response);

		return response;
	}

	public void setCredentials(String username, String password) {
		Credentials cred = new UsernamePasswordCredentials(username, password);
		AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT,
				AuthScope.ANY_REALM);

		client.getState().setCredentials(scope, cred);
		client.getParams().setAuthenticationPreemptive(true);
		serverAuthent = true;
	}

	private Element doExecute(HttpMethodBase httpMethod) throws IOException,
			BadXmlResponseEx {
		config.setHost(host, port, Protocol.getProtocol(protocol));

		if (useProxy)
			config.setProxy(proxyHost, proxyPort);

		byte[] data = null;

		try {
			client.executeMethod(httpMethod);
			data = httpMethod.getResponseBody();

			// HttpClient is unable to automatically handle redirects of entity
			// enclosing methods such as POST and PUT.
			// Get the location header and run the request against it.
			String redirectLocation;
			Header locationHeader = httpMethod.getResponseHeader("location");
			if (locationHeader != null) {
				redirectLocation = locationHeader.getValue();
				httpMethod.setPath(redirectLocation);
				client.executeMethod(httpMethod);
				data = httpMethod.getResponseBody();
			}
			return Xml.loadStream(new ByteArrayInputStream(data));
		}

		catch (JDOMException e) {
			throw new BadXmlResponseEx(new String(data, "UTF8"));
		}

		finally {
			httpMethod.releaseConnection();

			sentData = getSentData(httpMethod);
			receivedData = getReceivedData(httpMethod, data);
		}
	}

	private HttpMethodBase setupHttpMethod()
			throws UnsupportedEncodingException {
		HttpMethodBase httpMethod;

		if (method == Method.GET) {
			httpMethod = new GetMethod();

			if (query != null && !query.equals(""))
				httpMethod.setQueryString(query);

			else if (alSimpleParams.size() != 0)
				httpMethod.setQueryString(alSimpleParams
						.toArray(new NameValuePair[alSimpleParams.size()]));

			httpMethod.addRequestHeader("Accept", !useSOAP ? "application/xml"
					: "application/soap+xml");
			httpMethod.setFollowRedirects(true);
		} else {
			PostMethod post = new PostMethod();

			if (!useSOAP) {
				postData = (postParams == null) ? "" : Xml
						.getString(new Document(postParams));
				post.setRequestEntity(new StringRequestEntity(postData,
						"application/xml", "UTF8"));
			} else {
				postData = Xml.getString(new Document(soapEmbed(postParams)));
				post.setRequestEntity(new StringRequestEntity(postData,
						"application/soap+xml", "UTF8"));
			}

			httpMethod = post;
		}

		httpMethod.setPath(address);
		httpMethod.setDoAuthentication(useAuthent());

		return httpMethod;
	}

	private String getSentData(HttpMethodBase httpMethod) {
		String sentData = httpMethod.getName() + " " + httpMethod.getPath();

		if (httpMethod.getQueryString() != null)
			sentData += "?" + httpMethod.getQueryString();

		sentData += "\r\n";

		for (Header h : httpMethod.getRequestHeaders())
			sentData += h;

		sentData += "\r\n";

		if (httpMethod instanceof PostMethod)
			sentData += postData;

		return sentData;
	}

	private String getReceivedData(HttpMethodBase httpMethod, byte[] response) {
		String receivedData = "";

		try {
			// --- if there is a connection error (the server is unreachable)
			// this
			// --- call causes a NullPointerEx

			receivedData = httpMethod.getStatusText() + "\r\r";

			for (Header h : httpMethod.getResponseHeaders())
				receivedData += h;

			receivedData += "\r\n";

			if (response != null)
				receivedData += new String(response, "UTF8");
		} catch (Exception e) {
			receivedData = "";
		}

		return receivedData;
	}

	private Element soapEmbed(Element elem) {
		Element envl = new Element("Envelope", NAMESPACE_ENV);
		Element body = new Element("Body", NAMESPACE_ENV);

		envl.addContent(body);
		body.addContent(elem);

		return envl;
	}

	@SuppressWarnings("unchecked")
	private Element soapUnembed(Element envelope) throws BadSoapResponseEx {
		Namespace ns = envelope.getNamespace();
		Element body = envelope.getChild("Body", ns);

		if (body == null)
			throw new BadSoapResponseEx(Xml.getString(envelope));

		List<Element> list = body.getChildren();

		if (list.size() == 0)
			throw new BadSoapResponseEx(Xml.getString(envelope));

		return list.get(0);
	}

	private boolean useAuthent() {
		return proxyAuthent || serverAuthent;
	}

	private String host;
	private int port;
	private String protocol;
	private String address;
	private boolean serverAuthent;
	private String query;
	private Method method;
	private boolean useSOAP;
	private Element postParams;
	private boolean useProxy;
	private String proxyHost;
	private int proxyPort;
	private boolean proxyAuthent;

	private HttpClient client = new HttpClient();
	private HttpState state = new HttpState();
	private Cookie cookie = new Cookie();

	private HostConfiguration config = new HostConfiguration();

	private ArrayList<NameValuePair> alSimpleParams = new ArrayList<NameValuePair>();

	// --- transient vars

	private String sentData;
	private String receivedData;
	private String postData;
}