/* ConfigParser.java

	Purpose:
		
	Description:
		
	History:
		Sun Mar 26 18:09:10     2006, Created by tomyeh

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.sys;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.net.URL;

import org.zkoss.lang.Library;
import org.zkoss.lang.Classes;
import org.zkoss.util.Cache;
import org.zkoss.util.Utils;
import org.zkoss.util.resource.Locator;
import org.zkoss.util.resource.ClassLocator;
import org.zkoss.util.logging.Log;
import org.zkoss.idom.Document;
import org.zkoss.idom.Element;
import org.zkoss.idom.input.SAXBuilder;
import org.zkoss.idom.util.IDOMs;
import org.zkoss.xel.ExpressionFactory;
import org.zkoss.web.servlet.http.Encodes;

import org.zkoss.zk.Version;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.util.Configuration;
import org.zkoss.zk.ui.util.URIInfo;
import org.zkoss.zk.ui.util.CharsetFinder;
import org.zkoss.zk.ui.util.ThemeProvider;
import org.zkoss.zk.ui.metainfo.DefinitionLoaders;
import org.zkoss.zk.ui.sys.Attributes;
import org.zkoss.zk.scripting.Interpreters;
import org.zkoss.zk.device.Devices;
import org.zkoss.zk.au.AuDecoder;
import org.zkoss.zk.au.AuWriters;
import org.zkoss.zk.au.AuWriter;

/**
 * Used to parse WEB-INF/zk.xml, metainfo/zk/zk.xml 
 * and meta/zk/config.xml into {@link Configuration}.
 *
 * @author tomyeh
 */
public class ConfigParser {
	private static final Log log = Log.lookup(ConfigParser.class);

	/** The number of segments in a version.
	 */
	private static final int MAX_VERSION_SEGMENT = 4;
	private static int[] _zkver;
	private static boolean _syscfgLoaded;
	private static List _parsers;

	/** Checks and returns whether the loaded document's version is correct.
	 * It is the same as checkVersion(url, doc, false).
	 * @since 3.5.0
	 */
	public static boolean checkVersion(URL url, Document doc)
	throws Exception {
		return checkVersion(url, doc, false);
	}
	/** Checks and returns whether the loaded document's version is correct.
	 * @param zk5required whether ZK 5 or later is required.
	 * If true and zk-version is earlier than 5, doc will be ignored
	 * (and false is returned).
	 * @since 5.0.0
	 */
	public static
	boolean checkVersion(URL url, Document doc, boolean zk5required)
	throws Exception {
		final Element el = doc.getRootElement().getElement("version");
		if (el == null)
			return true; //version is optional (3.0.5)

		if (_zkver == null)
			_zkver = Utils.parseVersion(Version.UID);

		String s = el.getElementValue("zk-version", true);
		if (s != null) {
			final int[] reqzkver = Utils.parseVersion(s);
			if (Utils.compareVersion(_zkver, reqzkver) < 0) {
				log.info("Ignore "+url+"\nCause: ZK version must be "+s+" or later, not "+Version.UID);
				return false;
			}
			if (zk5required && reqzkver.length > 0 && reqzkver[0] < 5) {
				log.info("Ingore "+url+"\nCause: version "+s+" not supported");
				return false;
			}
		}

		final String clsnm = el.getElementValue("version-class", true);
		if (clsnm == null) {
			return true; //version is optional 3.0.5
		}
		
		if (clsnm.length() == 0)
			log.warning("Ignored: empty version-class, "+el.getLocator());

		final String uid = IDOMs.getRequiredElementValue(el, "version-uid");
		final Class cls = Classes.forNameByThread(clsnm);
		final Field fld = cls.getField("UID");
		final String uidInClass = (String)fld.get(null);
		if (!uid.equals(uidInClass)) {
			log.info("Ignore "+url+"\nCause: version not matched; expected="+uidInClass+", xml="+uid);
			return false;
		}

		return true; //matched
	}

	/** Used to provide backward compatibility to 2.3.0's richlet definition. */
	private int _richletnm;

	/** Parses metainfo/zk/config.xml placed in class-path.
	 *
	 * <p>Note: the application-independent configurations (aka.,
	 * the system default configurations) are loaded only once,
	 * no matter how many times this method is called.
	 *
	 * @param config the object to store configurations.
	 * If null, only the application-independent configurations are parsed.
	 * @since 3.5.0
	 */
	public void parseConfigXml(Configuration config) {
		boolean syscfgLoaded;
		synchronized (ConfigParser.class) {
			syscfgLoaded = _syscfgLoaded;
			_syscfgLoaded = true;
		}
		if (!syscfgLoaded)
			log.info("Loading system default");
		else if (config == null)
			return; //nothing to do

		try {
			final ClassLocator locator = new ClassLocator();
			final List xmls = locator.getDependentXMLResources(
				"metainfo/zk/config.xml", "config-name", "depends");
			for (Iterator it = xmls.iterator(); it.hasNext();) {
				final ClassLocator.Resource res = (ClassLocator.Resource)it.next();
				if (log.debugable()) log.debug("Loading "+res.url);
				try {
					if (checkVersion(res.url, res.document)) {
						final Element el = res.document.getRootElement();
						if (!syscfgLoaded) {
							parseSubZScriptConfig(el);
							parseSubDeviceConfig(el);
							parseSubSystemConfig(config, el);
							parseSubClientConfig(config, el);
							parseProperties(el);
						}

						if (config != null) {
							parseListeners(config, el);
							parsePreferences(config, el);
						}
					}
				} catch (Exception ex) {
					throw UiException.Aide.wrap(ex, "Failed to load "+res.url);
						//abort since it is hardly to work then
				}
			}
		} catch (Exception ex) {
			throw UiException.Aide.wrap(ex); //abort
		}
	}
	private static void parseSubZScriptConfig(Element root) {
		for (Iterator it = root.getElements("zscript-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			Interpreters.add(el);
				//Note: zscript-config is applied to the whole system, not just langdef
		}
	}
	private static void parseSubDeviceConfig(Element root) {
		for (Iterator it = root.getElements("device-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			Devices.add(el);
		}
	}
	private static
	void parseSubSystemConfig(Configuration config, Element root)
	throws Exception {
		for (Iterator it = root.getElements("system-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			if (config != null) {
				parseSystemConfig(config, el);
			} else {
				Class cls = parseClass(el, "au-writer-class", AuWriter.class);
				if (cls != null)
					AuWriters.setImplementationClass(cls);
				cls = parseClass(el, "config-parser-class", org.zkoss.zk.ui.util.ConfigParser.class);
				if (cls != null) {
					if (_parsers == null)
						_parsers = new LinkedList();
					_parsers.add(cls.newInstance());
				}
			}
		}
	}
	/** Unlike other private parseXxx, config might be null. */
	private static
	void parseSubClientConfig(Configuration config, Element root)
	throws Exception {
		for (Iterator it = root.getElements("client-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			if (config != null) {
				parseClientConfig(config, el);
			} else {
				Integer v = parseInteger(el, "resend-delay", false);
				if (v != null)
					Library.setProperty(Attributes.RESEND_DELAY, v.toString());
			}
		}
	}
	private static void parseListeners(Configuration config, Element root)
	throws Exception {
		for (Iterator it = root.getElements("listener").iterator();
		it.hasNext();) {
			parseListener(config, (Element)it.next());
		}
	}
	private static void parseListener(Configuration config, Element el) {
		try {
			config.addListener(parseClass(el, "listener-class", null, true));
		} catch (Exception ex) {
			log.error("Unable to load a listenr, "+el.getLocator(), ex);
		}
	}

	/** Parses zk.xml, specified by url, into the configuration.
	 *
	 * @param url the URL of zk.xml.
	 */
	public void parse(URL url, Configuration config, Locator locator)
	throws Exception {
		if (url == null || config == null)
			throw new IllegalArgumentException("null");
		log.info("Parsing "+url);
		parse(new SAXBuilder(true, false, true).build(url).getRootElement(),
			config, locator);
	}
	/** Parses zk.xml, specified by the root element.
	 * @since 3.0.1
	 */
	public void parse(Element root, Configuration config, Locator locator)
	throws Exception {
		l_out:
		for (Iterator it = root.getElements().iterator(); it.hasNext();) {
			final Element el = (Element)it.next();
			final String elnm = el.getName();
			if ("listener".equals(elnm)) {
				parseListener(config, el);
			} else if ("richlet".equals(elnm)) {
				final String clsnm =
					IDOMs.getRequiredElementValue(el, "richlet-class");
				final Map params =
					IDOMs.parseParams(el, "init-param", "param-name", "param-value");

				String path = el.getElementValue("richlet-url", true);
				if (path != null) {
				//deprecated since 2.4.0, but backward compatible
					final int cnt;
					synchronized (this) {
						cnt = _richletnm++;
					}
					final String name = "z_obs_" + Integer.toHexString(cnt);
					try {
						config.addRichlet(name, clsnm, params);
						config.addRichletMapping(name, path);
					} catch (Throwable ex) {
						log.error("Illegal richlet definition at "+el.getLocator(), ex);
					}
				} else { //syntax since 2.4.0
					final String nm =
						IDOMs.getRequiredElementValue(el, "richlet-name");
					try {
						config.addRichlet(nm, clsnm, params);
					} catch (Throwable ex) {
						log.error("Illegal richlet definition at "+el.getLocator(), ex);
					}
				}
			} else if ("richlet-mapping".equals(elnm)) { //syntax since 2.4.0
				final String nm =
					IDOMs.getRequiredElementValue(el, "richlet-name");
				final String path =
					IDOMs.getRequiredElementValue(el, "url-pattern");
				try {
					config.addRichletMapping(nm, path);
				} catch (Throwable ex) {
					log.error("Illegal richlet mapping at "+el.getLocator(), ex);
				}
			} else if ("desktop-config".equals(elnm)) {
			//desktop-config
			//	desktop-timeout
			//  disable-theme-uri
			//	file-check-period
			//	extendlet-check-period
			//	theme-provider-class
			//	theme-uri
			//	repeat-uuid
				parseDesktopConfig(config, el);
				parseClientConfig(config, el); //backward compatible with 2.4

			} else if ("client-config".equals(elnm)) { //since 3.0.0
			//client-config
			//	click-filter-delay
			//  keep-across-visits
			//  processing-prompt-delay
			//	error-reload
			//	tooltip-delay
			//  resend-delay
			//  debug-js
				parseClientConfig(config, el);

			} else if ("session-config".equals(elnm)) {
			//session-config
			//	session-timeout
			//	max-desktops-per-session
			//  max-requests-per-session
			//	max-pushes-per-session
			//  timer-keep-alive
			//	timeout-uri
			//  automatic-timeout
				Integer v = parseInteger(el, "session-timeout", false);
				if (v != null) config.setSessionMaxInactiveInterval(v.intValue());

				v = parseInteger(el, "max-desktops-per-session", false);
				if (v != null) config.setSessionMaxDesktops(v.intValue());

				v = parseInteger(el, "max-requests-per-session", false);
				if (v != null) config.setSessionMaxRequests(v.intValue());

				v = parseInteger(el, "max-pushes-per-session", false);
				if (v != null) config.setSessionMaxPushes(v.intValue());

				String s = el.getElementValue("timer-keep-alive", true);
				if (s != null) config.setTimerKeepAlive("true".equals(s));

				parseTimeoutURI(config, el);
			} else if ("language-config".equals(elnm)) {
			//language-config
			//	addon-uri
				parseLangAddon(locator, el);
			} else if ("language-mapping".equals(elnm)) {
			//language-mapping
			//	language-name/extension
				DefinitionLoaders.addExtension(
					IDOMs.getRequiredElementValue(el, "extension"),
					IDOMs.getRequiredElementValue(el, "language-name"));
				//Note: we don't add it to LanguageDefinition now
				//since addon-uri might be specified later
				//(so we cannot load definitions now)
			} else if ("system-config".equals(elnm)) {
			//system-config
			//  disable-event-thread
			//	max-spare-threads
			//  max-suspended-threads
			//	event-time-warning
			//  max-upload-size
			//  upload-charset
			//  upload-charset-finder-class
			//  max-process-time
			//	response-charset
			//  cache-provider-class
			//  ui-factory-class
			//  failover-manager-class
			//	engine-class
			//	id-generator-class
			//  web-app-class
			//	method-cache-class
			//	url-encoder-class
			//	au-writer-class
			//	au-decoder-class
				parseSystemConfig(config, el);
			} else if ("xel-config".equals(elnm)) {
			//xel-config
			//	evaluator-class
				Class cls = parseClass(el, "evaluator-class", ExpressionFactory.class);
				if (cls != null) config.setExpressionFactoryClass(cls);
			} else if ("zscript-config".equals(elnm)) {
			//zscript-config
				Interpreters.add(el);
					//Note: zscript-config is applied to the whole system, not just langdef
			} else if ("device-config".equals(elnm)) {
			//device-config
				Devices.add(el);
					//Note: device-config is applied to the whole system, not just langdef
				parseTimeoutURI(config, el);
					//deprecated since 3.6.3, but to be backward-compatible
			} else if ("log".equals(elnm)) {
				final String base = el.getElementValue("log-base", true);
				if (base != null)
					org.zkoss.util.logging.LogService.init(base, null); //start the log service
			} else if ("error-page".equals(elnm)) {
			//error-page
				final Class cls =
					parseClass(el, "exception-type", Throwable.class, true);
				final String loc =
					IDOMs.getRequiredElementValue(el, "location");
				String deviceType = el.getElementValue("device-type", true);
				if (deviceType == null) deviceType = "ajax";
				else if (deviceType.length() == 0)
					log.error("device-type not specified at "+el.getLocator());

				config.addErrorPage(deviceType, cls, loc);
			} else if ("preference".equals(elnm)) {
				parsePreference(config, el);
			} else if ("library-property".equals(elnm)) {
				parseLibProperty(el);
			} else if ("system-property".equals(elnm)) {
				parseSysProperty(el);
			} else {
				if (_parsers != null)
					for (Iterator e = _parsers.iterator(); e.hasNext();) {
						org.zkoss.zk.ui.util.ConfigParser parser =
							(org.zkoss.zk.ui.util.ConfigParser)e.next();
						if (parser.parse(config, el))
							continue l_out;
					}
				log.error("Unknown element: "+elnm+", at "+el.getLocator());
			}
		}
	}

	private static void parseProperties(Element root) {
		for (Iterator it = root.getElements("library-property").iterator();
		it.hasNext();) {
			parseLibProperty((Element)it.next());
		}
		for (Iterator it = root.getElements("system-property").iterator();
		it.hasNext();) {
			parseSysProperty((Element)it.next());
		}
	}
	private static void parseLibProperty(Element el) {
		final String nm = IDOMs.getRequiredElementValue(el, "name");
		final String val = IDOMs.getRequiredElementValue(el, "value");
		Library.setProperty(nm, val);
	}
	private static void parseSysProperty(Element el) {
		final String nm = IDOMs.getRequiredElementValue(el, "name");
		final String val = IDOMs.getRequiredElementValue(el, "value");
		System.setProperty(nm, val);
	}
	private static void parsePreferences(Configuration config, Element root) {
		for (Iterator it = root.getElements("preference").iterator();
		it.hasNext();) {
			parsePreference(config, (Element)it.next());
		}
	}
	private static void parsePreference(Configuration config, Element el) {
		final String nm = IDOMs.getRequiredElementValue(el, "name");
		final String val = IDOMs.getRequiredElementValue(el, "value");
		config.setPreference(nm, val);
	}

	/** Parses timeout-uri an other info. */
	private static void parseTimeoutURI(Configuration config, Element conf)
	throws Exception {
		String deviceType = conf.getElementValue("device-type", true);
		String s = conf.getElementValue("timeout-uri", true);
		if (s != null)
			config.setTimeoutURI(deviceType, s, URIInfo.SEND_REDIRECT);

		s = conf.getElementValue("timeout-message", true);
		if (s != null)
			config.setTimeoutMessage(deviceType, s);

		s = conf.getElementValue("automatic-timeout", true);
		if (s != null)
			config.setAutomaticTimeout(deviceType, !"false".equals(s));
	}

	/** Parses desktop-config. */
	private static void parseDesktopConfig(Configuration config, Element conf)
	throws Exception {
		//theme-uri
		for (Iterator it = conf.getElements("theme-uri").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String uri = el.getText(true);
			if (uri.length() != 0) config.addThemeURI(uri);
		}

		//disable-theme-uri
		for (Iterator it = conf.getElements("disable-theme-uri").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String uri = el.getText(true);
			if (uri.length() != 0) config.addDisabledThemeURI(uri);
		}

		//theme-provider-class
		Class cls = parseClass(conf, "theme-provider-class",
			ThemeProvider.class);
		if (cls != null)
			config.setThemeProvider((ThemeProvider)cls.newInstance());

		//desktop-timeout
		Integer v = parseInteger(conf, "desktop-timeout", false);
		if (v != null) config.setDesktopMaxInactiveInterval(v.intValue());

		//file-check-period
		v = parseInteger(conf, "file-check-period", true);
		if (v != null)
			Library.setProperty("org.zkoss.util.resource.checkPeriod", v.toString());
			//library-wide property

		//extendlet-check-period
		v = parseInteger(conf, "extendlet-check-period", true);
		if (v != null)
			Library.setProperty("org.zkoss.util.resource.extendlet.checkPeriod", v.toString());
			//library-wide property

		String s = conf.getElementValue("repeat-uuid", true);
		if (s != null) config.setRepeatUuid(!"false".equals(s));

		s = conf.getElementValue("id-to-uuid-prefix", true);
		if (s != null) {
			log.warning("id-to-uuid-prefix deprecated, " + conf.getLocator());
			Library.setProperty(Attributes.ID_TO_UUID_PREFIX, s);
			//library-wide property
		}
	}
	/** Parses client-config. */
	private static void parseSystemConfig(Configuration config, Element el)
	throws Exception {
		String s = el.getElementValue("disable-event-thread", true);
		if (s != null) {
			final boolean enable = "false".equals(s);
			if (!enable) log.info("The event processing thread is disabled");
			config.enableEventThread(enable);
		}

		Integer v = parseInteger(el, "max-spare-threads", false);
		if (v != null) config.setMaxSpareThreads(v.intValue());
		
		v = parseInteger(el, "max-suspended-threads", false);
		if (v != null) config.setMaxSuspendedThreads(v.intValue());

		v = parseInteger(el, "event-time-warning", false);
		if (v != null) config.setEventTimeWarning(v.intValue());
		
		v = parseInteger(el, "max-upload-size", false);
		if (v != null) config.setMaxUploadSize(v.intValue());

		v = parseInteger(el, "max-process-time", true);
		if (v != null) config.setMaxProcessTime(v.intValue());

		s = el.getElementValue("upload-charset", true);
		if (s != null) config.setUploadCharset(s);

		s = el.getElementValue("response-charset", true);
		if (s != null) config.setResponseCharset(s);

		s = el.getElementValue("crawlable", true);
		if (s != null) config.setCrawlable(!"false".equals(s));

		Class cls = parseClass(el, "upload-charset-finder-class",
			CharsetFinder.class);
		if (cls != null)
			config.setUploadCharsetFinder((CharsetFinder)cls.newInstance());

		cls = parseClass(el, "cache-provider-class",
			DesktopCacheProvider.class);
		if (cls != null) config.setDesktopCacheProviderClass(cls);

		cls = parseClass(el, "ui-factory-class", UiFactory.class);
		if (cls != null) config.setUiFactoryClass(cls);

		cls = parseClass(el, "failover-manager-class", FailoverManager.class);
		if (cls != null) config.setFailoverManagerClass(cls);

		cls = parseClass(el, "engine-class", UiEngine.class);
		if (cls != null) config.setUiEngineClass(cls);

		cls = parseClass(el, "id-generator-class", IdGenerator.class);
		if (cls != null) config.setIdGeneratorClass(cls);

		cls = parseClass(el, "session-cache-class", SessionCache.class);
		if (cls != null) config.setSessionCacheClass(cls);

		cls = parseClass(el, "au-decoder-class", AuDecoder.class);
		if (cls != null) config.setAuDecoderClass(cls);

		cls = parseClass(el, "web-app-class", WebApp.class);
		if (cls != null) config.setWebAppClass(cls);

		cls = parseClass(el, "method-cache-class", Cache.class);
		if (cls != null)
			ComponentsCtrl.setEventMethodCache((Cache)cls.newInstance());

		cls = parseClass(el, "au-writer-class", AuWriter.class);
		if (cls != null)
			AuWriters.setImplementationClass(cls);
	}
	/** Parses client-config. */
	private static void parseClientConfig(Configuration config, Element conf) {
		Integer v = parseInteger(conf, "processing-prompt-delay", true);
		if (v != null) config.setProcessingPromptDelay(v.intValue());

		v = parseInteger(conf, "tooltip-delay", true);
		if (v != null) config.setTooltipDelay(v.intValue());

		v = parseInteger(conf, "resend-delay", false);
		if (v != null) config.setResendDelay(v.intValue());

		v = parseInteger(conf, "click-filter-delay", false);
		if (v != null) config.setClickFilterDelay(v.intValue());

		String s = conf.getElementValue("keep-across-visits", true);
		if (s != null)
			config.setKeepDesktopAcrossVisits(!"false".equals(s));

		s = conf.getElementValue("debug-js", true);
		if (s != null) config.setDebugJS(!"false".equals(s));

		//client (JS) pacakges
		for (Iterator it = conf.getElements("package").iterator();
		it.hasNext();) {
			config.addClientPackage(IDOMs.getRequiredElementValue((Element)it.next(), "package-name"));
		}

		//error-reload
		for (Iterator it = conf.getElements("error-reload").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();

			String deviceType = el.getElementValue("device-type", true);
			String connType = el.getElementValue("connection-type", true);
			v = parseInteger(el, "error-code", true);
			if (v == null)
				throw new UiException("error-code is required, "+el.getLocator());
			String uri = IDOMs.getRequiredElementValue(el, "reload-uri");
			if ("false".equals(uri)) uri = null;

			config.setClientErrorReload(deviceType, v.intValue(), uri, connType);
		}
	}

	/** Parse language-config/addon-uri. */
	private static void parseLangAddon(Locator locator, Element conf) {
		for (Iterator it = conf.getElements("addon-uri").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String path = el.getText(true);

			final URL url = locator.getResource(path);
			if (url == null)
				log.error("File not found: "+path+", at "+el.getLocator());
			else
				DefinitionLoaders.addAddon(locator, url);
		}
	}
	/** Parse a class, if specified, whether it implements cls.
	 */
	private static Class parseClass(Element el, String elnm, Class cls) {
		return parseClass(el, elnm, cls, false);
	}
	private static
	Class parseClass(Element el, String elnm, Class cls, boolean required) {
		//Note: we throw exception rather than warning to make sure
		//the developer correct it
		final String clsnm = el.getElementValue(elnm, true);
		if (clsnm != null && clsnm.length() != 0) {
			try {
				final Class klass = Classes.forNameByThread(clsnm);
				if (cls != null && !cls.isAssignableFrom(klass)) {
					String msg = clsnm+" must implement "+cls.getName()+", "+el.getLocator();
					if (required)
						throw new UiException(msg);
					log.error(msg);
					return null;
				}
//				if (log.debuggable()) log.debug("Using "+clsnm+" for "+cls);
				return klass;
			} catch (Throwable ex) {
				String msg = ex instanceof ClassNotFoundException ?
					clsnm + " not found": "Unable to load "+clsnm;
				msg += ", at "+el.getLocator();
				if (required)
					throw new UiException(msg, ex);
				log.error(msg);
				return null;
			}
		} else if (required)
			throw new UiException(elnm+" required, at "+el.getLocator());
		return null;
	}

	/** Configures an integer. */
	private static Integer parseInteger(Element el, String subnm,
	boolean positiveOnly) throws UiException {
		//Note: we throw exception rather than warning to make sure
		//the developer correct it
		String val = el.getElementValue(subnm, true);
		if (val != null && val.length() > 0) {
			try { 
				final int v = Integer.parseInt(val);
				if (positiveOnly && v <= 0)
					throw new UiException("The "+subnm+" element must be a positive number, not "+val+", at "+el.getLocator());
				return new Integer(v);
			} catch (NumberFormatException ex) { //eat
				throw new UiException("The "+subnm+" element must be a number, not "+val+", at "+el.getLocator());
			}
		}
		return null;
	}
}
