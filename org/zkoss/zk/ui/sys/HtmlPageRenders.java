/* HtmlPageRenders.java

	Purpose:
		
	Description:
		
	History:
		Tue Oct 14 19:06:37     2008, Created by tomyeh

Copyright (C) 2008 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.sys;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Collection;
import java.io.Writer;
import java.io.StringWriter;
import java.io.IOException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.zkoss.lang.Library;
import org.zkoss.lang.Strings;
import org.zkoss.io.Files;
import org.zkoss.util.logging.Log;
import org.zkoss.web.fn.ServletFns;
import org.zkoss.web.servlet.JavaScript;
import org.zkoss.web.servlet.StyleSheet;
import org.zkoss.xml.HTMLs;
import org.zkoss.xml.XMLs;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.WebApps;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.util.Configuration;
import org.zkoss.zk.ui.util.ThemeProvider;
import org.zkoss.zk.ui.sys.PageCtrl;
import org.zkoss.zk.ui.sys.SessionsCtrl;
import org.zkoss.zk.ui.sys.ExecutionsCtrl;
import org.zkoss.zk.ui.sys.ComponentCtrl;
import org.zkoss.zk.ui.metainfo.LanguageDefinition;
import org.zkoss.zk.ui.sys.Attributes;
import org.zkoss.zk.au.AuResponse;
import org.zkoss.zk.device.Devices;
import org.zkoss.zk.device.Device;

/**
 * Utilities for implementing HTML-based {@link PageRenderer}.
 *
 * @author tomyeh
 * @since 5.0.0
 */
public class HtmlPageRenders {
//	private static final Log log = Log.lookup(HtmlPageRenders.class);

	/** Denotes whether style sheets are generated for this request. */
	private static final String ATTR_LANG_CSS_GENED
		= "javax.zkoss.zk.lang.css.generated";
		//Naming with javax to be able to shared among portlets
	/** Denotes whether JavaScripts are generated for this request. */
	private static final String ATTR_LANG_JS_GENED
		= "javax.zkoss.zk.lang.js.generated";
		//Naming with javax to be able to shared among portlets
	/** Denotes whether the unavailable message is generated for this request. */
	private static final String ATTR_UNAVAILABLE_GENED
		= "javax.zkoss.zk.unavail.generated";
	/** Denotes whether zkdt has been generated. */
	private static final String ATTR_DESKTOP_JS_GENED
		= "javax.zkoss.zk.dtjs.generated";

	/** Denotes DOCTYPE has been generated. */
	private static final String DOCTYPE_GENED
		= "javax.zkoss.zk.doctype.generated";
	/** Denotes the first line has been generated. */
	private static final String FIRST_LINE_GENED
		= "javax.zkoss.zk.firstline.generated";

	/** The render context. */
	private static final String ATTR_RENDER_CONTEXT
		= "org.zkoss.zk.ui.renderContext";
	/** Whether is allowed to generate content directly. */
	private static final String ATTR_DIRECT_CONTENT
		= "org.zkoss.zk.ui.directContent";

	/** Sets the content type to the specified execution for the given page.
	 * @param exec the execution (never null)
	 */
	public static final void setContentType(Execution exec, Page page) {
		String contentType = ((PageCtrl)page).getContentType();
		if (contentType == null) {
			contentType = page.getDesktop().getDevice().getContentType();
			if (contentType == null) contentType = "";
		}

		final int j = contentType.indexOf(';');
		if (j < 0) {
			final String cs = page.getDesktop().getWebApp()
				.getConfiguration().getResponseCharset();
			if (cs != null && cs.length() > 0)
				contentType += ";charset=" + cs;
		}

		((ExecutionCtrl)exec).setContentType(contentType);
	}
	/** Returns the doc type, or null if not available.
	 * It is null or &lt;!DOCTYPE ...&gt;.
	 */
	public static final String outDocType(Execution exec, Page page) {
		if (exec.getAttribute(DOCTYPE_GENED) == null) {
			exec.setAttribute(DOCTYPE_GENED, Boolean.TRUE);
			final String docType = ((PageCtrl)page).getDocType();
			return trimAndLF(docType != null ?
				docType: page.getDesktop().getDevice().getDocType());
		}
		return "";
	}
	/** Trims and appends a linefeed if necessary.
	 */
	private static final String trimAndLF(String s) {
		if (s != null) {
			s = s.trim();
			final int len = s.length();
			if (len > 0 && s.charAt(len-1) != '\n')
				s += '\n';
		}
		return s;
	}
	/** Generates the unavailable message in HTML tags, if any.
	 * @param exec the execution (never null)
	 */
	public static String outUnavailable(Execution exec) {
		if (exec.getAttribute(ATTR_UNAVAILABLE_GENED) != null)
			return ""; //nothing to generate
		exec.setAttribute(ATTR_UNAVAILABLE_GENED, Boolean.TRUE);

		final Device device = exec.getDesktop().getDevice();
		String s = device.getUnavailableMessage();
		return s != null ?
			"<noscript>\n" + s + "\n</noscript>": "";
	}
	/** Returns the first line to be generated to the output,
	 * or null if no special first line.
	 */
	public static final String outFirstLine(Execution exec, Page page) {
		if (exec.getAttribute(FIRST_LINE_GENED) == null) {
			exec.setAttribute(FIRST_LINE_GENED, Boolean.TRUE);
			return trimAndLF(((PageCtrl)page).getFirstLine());
		}
		return "";
	}

	/** Generates the AU responses that are part of a page rendering.
	 * Notice that {@link #outPageContent} will invoke this method automatically.
	 * <p>It is the same as <code>outResponseJavaScripts(exec, false)</code>.
	 */
	public static final String outResponseJavaScripts(Execution exec) {
		return outResponseJavaScripts(exec, false);
	}
	/** Generates the AU responses that are part of a page rendering.
	 * Notice that {@link #outPageContent} will invoke this method automatically.
	 * @param directJS whether to generate JS directly.
	 * If true, it generates <code>"x,y"</code> where x and y are assumed to the responses.
	 * If false, it generates <code>&lt;script&gt;zkac(x,y);&lt;/script&gt;</pre></code>
	 * @since 5.0.2
	 */
	public static final String outResponseJavaScripts(Execution exec, boolean directJS) {
		final ExecutionCtrl execCtrl = (ExecutionCtrl)exec;
		final Collection responses = execCtrl.getResponses();
		if (responses == null || responses.isEmpty()) return "";
		execCtrl.setResponses(null);

		final StringBuffer sb = new StringBuffer(256);
		if (!directJS)
			sb.append("<script type=\"text/javascript\">\nzkac(");

		for (Iterator it = responses.iterator(); it.hasNext();) {
			final AuResponse response = (AuResponse)it.next();
			sb.append("'").append(response.getCommand())
				.append("',");
			final List encdata = response.getEncodedData();
			if (encdata != null)
				sb.append('\'')
					.append(Strings.escape(
						org.zkoss.json.JSONArray.toJSONString(encdata),
						Strings.ESCAPE_JAVASCRIPT))
					.append('\'');
			else
				sb.append((String)null);
			if (it.hasNext())
				sb.append(",\n");
		}

		if (!directJS)
			sb.append(");\n</script>");
		return sb.toString();
	}

	/** Returns HTML tags to include all JavaScript files and codes that are
	 * required when loading a ZUML page (never null).
	 *
	 * <p>FUTURE CONSIDERATION: we might generate the inclusion on demand
	 * instead of all at once.
	 * @param exec the execution (never null)
	 * @param wapp the Web application.
	 * If null, exec.getDesktop().getWebApp() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution, such as JSP/DSP).
	 * @param deviceType the device type, such as ajax.
	 * If null, exec.getDesktop().getDeviceType() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution).
	 */
	public static final
	String outLangJavaScripts(Execution exec, WebApp wapp, String deviceType) {
		if (exec.getAttribute(ATTR_LANG_JS_GENED) != null)
			return ""; //nothing to generate
		exec.setAttribute(ATTR_LANG_JS_GENED, Boolean.TRUE);

		final Desktop desktop = exec.getDesktop();
		if (wapp == null) wapp = desktop.getWebApp();
		if (deviceType == null)
			deviceType = desktop != null ? desktop.getDeviceType(): "ajax";

		final StringBuffer sb = new StringBuffer(1536);

		final Set jses = new LinkedHashSet(32);
		for (Iterator it = LanguageDefinition.getByDeviceType(deviceType).iterator();
		it.hasNext();)
			jses.addAll(((LanguageDefinition)it.next()).getJavaScripts());
		for (Iterator it = jses.iterator(); it.hasNext();)
			append(sb, (JavaScript)it.next());

		sb.append("\n<!-- ZK ").append(wapp.getVersion());
		if (WebApps.getFeature("ee"))
			sb.append(" EE");
		else if (WebApps.getFeature("pe"))
			sb.append(" PE");
		sb.append(' ').append(wapp.getBuild());
		Object o = wapp.getAttribute("org.zkoss.zk.ui.notice");
		if (o != null) sb.append(o);
		sb.append(" -->\n");

		int tmout = 0;
		final Boolean autoTimeout = getAutomaticTimeout(desktop);
		if (autoTimeout != null ? autoTimeout.booleanValue():
		wapp.getConfiguration().isAutomaticTimeout(deviceType)) {
			if (desktop != null) {
				tmout = desktop.getSession().getMaxInactiveInterval();
			} else {
				Object req = exec.getNativeRequest();
				if (req instanceof HttpServletRequest)  {
					final HttpSession hsess = ((HttpServletRequest)req).getSession(false);
					if (hsess != null) {
						final Session sess = SessionsCtrl.getSession(wapp, hsess);
						if (sess != null) {
							tmout = sess.getMaxInactiveInterval();
						} else {
						//try configuration first since HttpSession's timeout is set
						//when ZK Session is created (so it is not set yet)
						//Note: no need to setMaxInactiveInternval here since it will
						//be set later or not useful at the end
							tmout = wapp.getConfiguration().getSessionMaxInactiveInterval();
							if (tmout <= 0) //system default
								tmout = hsess.getMaxInactiveInterval();
						}
					} else
						tmout = wapp.getConfiguration().getSessionMaxInactiveInterval();
				}
			}
			if (tmout > 0) { //unit: seconds
				int extra = tmout / 8;
				tmout += extra > 60 ? 60: extra < 5 ? 5: extra;
					//Add extra seconds to ensure it is really timeout
			}
		}

		final boolean keepDesktop = exec.getAttribute(Attributes.NO_CACHE) == null,
			groupingAllowed = isGroupingAllowed(desktop);
		final String progressboxPos = org.zkoss.lang.Library.getProperty("org.zkoss.zul.progressbox.position", "");
		if (tmout > 0 || keepDesktop || progressboxPos.length() > 0 || !groupingAllowed) {
			sb.append("<script type=\"text/javascript\">zkopt({");

			if (keepDesktop)
				sb.append("kd:1,");
			if (!groupingAllowed)
				sb.append("gd:1,");
			if (tmout > 0)
				sb.append("to:").append(tmout).append(",");
			if (progressboxPos.length() > 0)
				sb.append("ppos:'").append(progressboxPos).append("'");

			if (sb.charAt(sb.length() - 1) == ',')
				sb.setLength(sb.length() - 1);
			sb.append("});</script>");
		}

		final Device device = Devices.getDevice(deviceType);
		String s = device.getEmbedded();
		if (s != null)
			sb.append(s).append('\n');

		return sb.toString();
	}
	private static Boolean getAutomaticTimeout(Desktop desktop) {
		if (desktop != null)
			for (Iterator it = desktop.getPages().iterator(); it.hasNext();) {
				Boolean b = ((PageCtrl)it.next()).getAutomaticTimeout();
				if (b != null) return b;
			}
		return null;
	}
	/** Returns HTML tags to include all style sheets that are
	 * defined in all languages of the specified device (never null).
	 *
	 * <p>In addition to style sheets defined in lang.xml and lang-addon.xml,
	 * it also include:
	 * <ol>
	 * <li>The style sheet specified in the theme-uri parameter.</li>
	 * </ol>
	 *
	 * <p>FUTURE CONSIDERATION: we might generate the inclusion on demand
	 * instead of all at once.
	 * @param exec the execution (never null)
	 * @param wapp the Web application.
	 * If null, exec.getDesktop().getWebApp() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution, such as JSP/DSP).
	 * @param deviceType the device type, such as ajax.
	 * If null, exec.getDesktop().getDeviceType() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution).
	 */
	public static final String outLangStyleSheets(Execution exec,
	WebApp wapp, String deviceType) {
		if (exec.getAttribute(ATTR_LANG_CSS_GENED) != null)
			return ""; //nothing to generate
		exec.setAttribute(ATTR_LANG_CSS_GENED, Boolean.TRUE);

		final StringBuffer sb = new StringBuffer(512);
		for (Iterator it = getStyleSheets(exec, wapp, deviceType).iterator();
		it.hasNext();)
			append(sb, (StyleSheet)it.next(), exec, null);

		if (sb.length() > 0) sb.append('\n');
		return sb.toString();
	}

	/** Returns a list of {@link StyleSheet} that shall be generated
	 * to the client for the specified execution.
	 * @param exec the execution (never null)
	 * @param wapp the Web application.
	 * If null, exec.getDesktop().getWebApp() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution, such as JSP/DSP).
	 * @param deviceType the device type, such as ajax.
	 * If null, exec.getDesktop().getDeviceType() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution).
	 */
	public static final
	List getStyleSheets(Execution exec, WebApp wapp, String deviceType) {
		if (wapp == null) wapp = exec.getDesktop().getWebApp();
		if (deviceType == null) deviceType = exec.getDesktop().getDeviceType();

		final Configuration config = wapp.getConfiguration();
		final Set disabled = config.getDisabledThemeURIs();
		final List sses = new LinkedList(); //a list of StyleSheet
		for (Iterator it = LanguageDefinition.getByDeviceType(deviceType).iterator();
		it.hasNext();) {
			final LanguageDefinition langdef = (LanguageDefinition)it.next();
			for (Iterator e = langdef.getStyleSheets().iterator(); e.hasNext();) {
				final StyleSheet ss = (StyleSheet)e.next();
				if (!disabled.contains(ss.getHref()))
					sses.add(ss);
			}
		}

		//Process configuration
		final ThemeProvider themeProvider = config.getThemeProvider();
		if (themeProvider != null) {
			final List orgss = new LinkedList();
			for (Iterator it =  sses.iterator(); it.hasNext();) {
				final StyleSheet ss = (StyleSheet)it.next();
				final String href = ss.getHref();
				if (href != null && href.length() > 0)
					orgss.add(ss.getMedia() != null ? ss: (Object)href); //we don't support getContent
			}

			final String[] hrefs = config.getThemeURIs();
			for (int j = 0; j < hrefs.length; ++j)
				orgss.add(hrefs[j]);

			sses.clear();
			final Collection res = themeProvider.getThemeURIs(exec, orgss);
			if (res != null) {
				for (Iterator it = res.iterator(); it.hasNext();) {
					final Object re = it.next();
					sses.add(re instanceof StyleSheet ? (StyleSheet)re:
						new StyleSheet((String)re, "text/css"));
				}
			}
		} else {
			final String[] hrefs = config.getThemeURIs();
			for (int j = 0; j < hrefs.length; ++j)
				sses.add(new StyleSheet(hrefs[j], "text/css"));
		}
		return sses;
	}

	private static void append(StringBuffer sb, StyleSheet ss,
	Execution exec, Page page) {
		String href = ss.getHref();
		String media = ss.getMedia();
		if (href != null) {
			try {
				if (exec != null)
					href = (String)exec.evaluate(page, href, String.class);

				if (href != null && href.length() > 0) {
					sb.append("\n<link rel=\"stylesheet\" type=\"")
						.append(ss.getType()).append("\" href=\"")
						.append(ServletFns.encodeURL(href));
					if (media != null)
						sb.append("\" media=\"").append(media);
					sb.append("\"/>");
				}
			} catch (jakarta.servlet.ServletException ex) {
				throw new UiException(ex);
			}
		} else {
			sb.append("\n<style");
			if (ss.getType() != null)
				sb.append(" type=\"").append(ss.getType()).append('"');
			sb.append(">\n").append(ss.getContent()).append("\n</style>");
		}
	}
	private static void append(StringBuffer sb, JavaScript js) {
		sb.append("\n<script type=\"text/javascript\"");
		if (js.getSrc() != null) {
			String url;
			try {
				url = ServletFns.encodeURL(js.getSrc());
			} catch (jakarta.servlet.ServletException ex) {
				throw new UiException(ex);
			}

			sb.append(" src=\"").append(url).append('"');
			final String charset = js.getCharset();
			if (charset != null)
				sb.append(" charset=\"").append(charset).append('"');
			sb.append('>');
		} else {
			sb.append(">\n").append(js.getContent());
		}
		sb.append("\n</script>");
	}

	/** Returns the render context, or null if not available.
	 * It is used to render the content that will appear before the content
	 * generated by {@link ContentRenderer}, such as crawlable content.
	 *
	 * @param exec the execution. If null, {@link Executions#getCurrent}
	 * is assumed.
	 */
	public static final
	RenderContext getRenderContext(Execution exec) {
		if (exec == null) exec = Executions.getCurrent();
		return exec != null ?
			(RenderContext)exec.getAttribute(ATTR_RENDER_CONTEXT): null;
	}
	private static final
	void setRenderContext(Execution exec, RenderContext rc) {
		exec.setAttribute(ATTR_RENDER_CONTEXT, rc);
	}

	/** Returns the HTML content representing a page.
	 * @param au whether it is caused by aynchrous update
	 * @param exec the execution (never null)
	 */
	public static final
	void outPageContent(Execution exec, Page page, Writer out, boolean au)
	throws IOException {
		final Desktop desktop = page.getDesktop();
		final PageCtrl pageCtrl = (PageCtrl)page;
		final Component owner = pageCtrl.getOwner();
		boolean contained = owner == null && exec.isIncluded();
			//a standalong page (i.e., no owner), and being included by
			//non-ZK page (e.g., JSP).
			//
			//Revisit Bug 2001707: OK to use exec.isIncluded() since
			//we use PageRenderer now (rather than Servlet's include)
			//TODO: test again

		//prepare style
		String style = page.getStyle();
		if (style == null || style.length() == 0) {
			style = null;
			String wd = null, hgh = null;
			if (owner instanceof HtmlBasedComponent) {
				final HtmlBasedComponent hbc = (HtmlBasedComponent)owner;
				wd = hbc.getWidth(); //null if not set
				hgh = hbc.getHeight(); //null if not set
			}

			if (wd != null || hgh != null || contained) {
				final StringBuffer sb = new StringBuffer(32);
				HTMLs.appendStyle(sb, "width", wd != null ? wd: "100%");
				HTMLs.appendStyle(sb, "height",
					hgh != null ? hgh: contained ? null: "100%");
				style = sb.toString();
			}
		}

		RenderContext rc = null, old = null;
		final boolean divRequired = !au || owner != null;
		final boolean standalone = !au && owner == null;
		if (standalone) {
			rc = new RenderContext(
				out, desktop.getWebApp().getConfiguration().isCrawlable());
			setRenderContext(exec, rc);
		} else if (owner != null) {
			old = getRenderContext(exec); //store
			setRenderContext(exec, null);
		}

		//generate div first
		if (divRequired) {
			outDivTemplateBegin(out, page.getUuid());
		}
		if (standalone) { //switch out
			//don't call outDivTemplateEnd yet since rc.temp will be generated before it
			out = new StringWriter();
		} else if (divRequired) {
			outDivTemplateEnd(out); //close it now since no rc.temp
		}

		//generate JS second
		final boolean aupg = exec.isAsyncUpdate(page); //AU this page
		if (divRequired) {
			out.write("\n<script type=\"text/javascript\">");
			if (!aupg && owner != null) {
				out.write("zkq('");
				out.write(owner.getUuid());
				out.write("',function(){");
			}
			out.write(outZkIconJS());
		}

		exec.setAttribute(ATTR_DESKTOP_JS_GENED, Boolean.TRUE);
		final int order = ComponentRedraws.beforeRedraw(false);
		final String extra;
		try {
			if (order < 0)
				out.write(aupg ? "[": divRequired ? "zkmx(": "zkx(");
			else if (order > 0) //not first child
				out.write(',');
			out.write("\n[0,'"); //0: page
			out.write(page.getUuid());
			out.write("',{");

			final StringBuffer props = new StringBuffer(128);
			final String pgid = page.getId();
			if (pgid.length() > 0)
				appendProp(props, "id", pgid);
			if (owner != null) {
				appendProp(props, "ow", owner.getUuid());
			} else {
				appendProp(props, "dt", desktop.getId());
				appendProp(props, "cu", getContextURI(exec));
				appendProp(props, "uu", desktop.getUpdateURI(null));
				appendProp(props, "ru", desktop.getRequestPath());
			}
			final String pageWgtCls = pageCtrl.getWidgetClass();
			if (pageWgtCls != null)
				appendProp(props, "wc", pageWgtCls);
			if (style != null)
				appendProp(props, "style", style);
			if (!isClientROD(page))
				appendProp(props, "z$rod", Boolean.FALSE);
			if (contained)
				appendProp(props, "ct", Boolean.TRUE);
			out.write(props.toString());
			out.write("},[");

			for (Component root = page.getFirstRoot(); root != null;
			root = root.getNextSibling())
				((ComponentCtrl)root).redraw(out);

			out.write("]]");
		} finally {
			extra = ComponentRedraws.afterRedraw();
		}

		if (order < 0) {
			outEndJavaScriptFunc(exec, out, extra, aupg);
		}

		if (standalone) {
			setRenderContext(exec, null);

			StringBuffer sw = ((StringWriter)out).getBuffer();
			out = rc.temp;
			if (divRequired)
				outDivTemplateEnd(out);
				//close tag after temp, but before perm (so perm won't be destroyed)
			Files.write(out, ((StringWriter)rc.perm).getBuffer()); //perm

			Files.write(out, sw); //js
		} else if (owner != null) { //restore
			setRenderContext(exec, old);
		}

		if (divRequired) {
			if (!aupg && owner != null)
				out.write("});");
			out.write("</script>\n");
		}
	}
	private static void outDivTemplateBegin(Writer out, String uuid)
	throws IOException {
		out.write("<div");
		writeAttr(out, "id", uuid);
		out.write(" class=\"z-temp\">");
	}
	private static void outDivTemplateEnd(Writer out)
	throws IOException {
		out.write("</div>");
	}
	/** Generates end of the function (of zkx).
	 * It assumes the function name and the first parenthesis has been generated.
	 * @param aupg whether the current page is caused by AU request
	 */
	private static void outEndJavaScriptFunc(Execution exec, Writer out,
	String extra, boolean aupg)
	throws IOException {
		final String ac = outResponseJavaScripts(exec, true);
		if (aupg) {
			if (extra.length() > 0 || ac.length() > 0) {
				out.write(",0,"); //no need to delay since js is evaluated by zkx

				if (ac.length() > 0) {
					out.write("\n[");
					out.write(ac);
					out.write(']');
				} else {
					out.write("null");
				}

				if (extra.length() > 0) {
					out.write(",'");
					out.write(Strings.escape(extra, Strings.ESCAPE_JAVASCRIPT));
					out.write('\'');
				}
			}
			out.write(']');
		} else {
			if (extra.length() > 0 || ac.length() > 0) {
				out.write(',');
				out.write(extra.length() > 0 ? '1': '0');
					//Bug 2983792: delay until non-defer script (i.e., extra) evaluated

				if (ac.length() > 0) {
					out.write(",\n[");
					out.write(ac);
					out.write(']');
				}
			}
			out.write(");\n");
			out.write(extra);
		}
	}
	private static void appendProp(StringBuffer sb, String name, Object value) {
		if (sb.length() > 0) sb.append(',');
		sb.append(name).append(':');
		boolean quote = value instanceof String;
		if (quote) sb.append('\'');
		sb.append(value); //no escape, so use with care
		if (quote) sb.append('\'');
	}

	private static final String outZkIconJS() {
		final Session sess = Sessions.getCurrent();
		if (sess != null) {
			final PI pi = (PI)sess.getAttribute("_zk.pi");
			boolean show = pi == null;
			if (show) sess.setAttribute("_zk.pi", new PI());
			else show = pi.show();

			if (show)
				return "zk.pi=1;";
		}
		return "";
	}
	private static class PI implements java.io.Serializable {
		long _t;
		private PI() {
			_t = System.currentTimeMillis();
		}
		private boolean show() {
			long now = System.currentTimeMillis();
			if (now - _t > 600000) { //every 10 minutes
				_t = now;
				return true;
			}
			return false;
		}
	}

	private static final boolean isClientROD(Page page) {
		Object o = page.getAttribute(Attributes.CLIENT_ROD);
		if (o != null)
			return (o instanceof Boolean && ((Boolean)o).booleanValue())
				|| !"false".equals(o);

		if (_crod == null) {
			final String s = Library.getProperty(Attributes.CLIENT_ROD);
			_crod = Boolean.valueOf(s == null || !"false".equals(s));
		}
		return _crod.booleanValue();
	}
	private static Boolean _crod;

	private static final boolean isGroupingAllowed(Desktop desktop) {
		final String name = "org.zkoss.zk.ui.input.grouping.allowed";
		if (desktop != null) {
			final Collection pages = desktop.getPages();
			if (!pages.isEmpty()) {
				final Page page = (Page)pages.iterator().next();
				Object o = page.getAttribute(name);
				if (o != null)
					return (o instanceof Boolean && ((Boolean)o).booleanValue())
						|| !"false".equals(o);
			}
		}

		if (_groupingAllowed == null) {
			final String s = Library.getProperty(name);
			_groupingAllowed = Boolean.valueOf(s == null || !"false".equals(s));
		}
		return _groupingAllowed.booleanValue();
	}
	private static Boolean _groupingAllowed;
		
	/** Generates the content of a standalone componnent that
	 * the peer widget is not a child of the page widget at the client.
	 * @param comp the compoent to render. It is null if no child component
	 * at all.
	 */
	public static final void outStandalone(Execution exec,
	Component comp, Writer out) throws IOException {
		if (ComponentRedraws.beforeRedraw(false) >= 0)
			throw new InternalError("Not possible: "+comp);

		final String extra;
		try {
			if (comp != null) {
				outDivTemplateBegin(out, comp.getUuid());
				outDivTemplateEnd(out);
			}

			out.write("<script type=\"text/javascript\">\nzkmx(");

			if (comp != null)
				((ComponentCtrl)comp).redraw(out);
			else
				out.write("null"); //no component at all
		} finally {
			extra = ComponentRedraws.afterRedraw();
		}

		outEndJavaScriptFunc(exec, out, extra, false);
			//generate extra, responses and ");"
		out.write("\n</script>\n");
	}
	private static final void writeAttr(Writer out, String name, String value)
	throws IOException {
		out.write(' ');
		out.write(name);
		out.write("=\"");
		out.write(XMLs.encodeAttribute(value));
		out.write('"');
	}

	/** Returns the content of the specified condition
	 * that will be placed inside the header element of the specified page,
	 * or null if it was generated before.
	 * For HTML, the header element is the HEAD element.
	 *
	 * <p>Notice that this method ignores the following invocations
	 * against the same page in the same execution. In other words,
	 * it is safe to invoke this method multiple times.
	 *
	 * @param before whether to return the headers that shall be shown
	 * before ZK's CSS/JS headers.
	 * If true, only the headers that shall be shown before (such as meta)
	 * are returned.
	 * If true, only the headers that shall be shown after (such as link)
	 * are returned.
	 */
	public static final
	String outHeaders(Execution exec, Page page, boolean before) {
		if (page == null)
			return "";

		String attr = "zkHeaderGened" + page.getUuid();
		if (before) attr += "Bf";
		if (exec.getAttribute(attr) != null)
			return null;

		exec.setAttribute(attr, Boolean.TRUE); //generated only once
		return before ? ((PageCtrl)page).getBeforeHeadTags():
			((PageCtrl)page).getAfterHeadTags();
	}
	/** Generates and returns the ZK specific HTML tags including
	 * the headers defined in the specified page, or null if it was
	 * generated before.
	 *
	 * <p>It is shortcut of<br/>
	 *<code>outZkHeader(exec, page, true)+outZkTags(exec, null, null)+outZkHeader(exec, page, false)</code>
	 *
	 * <p>Unlike {@link #outZkTags}, this method cannot be called
	 * in JSP/DSP (since desktop is not available).
	 *
	 * @see #outZkTags
	 */
	public static String outHeaderZkTags(Execution exec, Page page) {
		String s1 = outHeaders(exec, page, true),
			s2 = outZkTags(exec, null, null),
			s3 = outHeaders(exec, page, false);
		return s1 != null ?
			s2 != null ?
				s3 != null ? s1 + s2 + s3: s1 + s2:
				s3 != null ? s1 + s3: s1: //s2 null
			s2 != null ?
				s3 != null ? s2 + s3: s2:
				s3 != null ? s3: null; //s2 null
	}
	/** Generates and returns the ZK specific HTML tags such as stylesheet
	 * and JavaScript.
	 *
	 * <p>For each desktop, we have to generate a set of HTML tags
	 * to load ZK Client engine, style sheets and so on.
	 * For ZUL pages, it is generated automatically by page.dsp.
	 * However, for ZHTML pages, we have to generate these tags
	 * with special component such as org.zkoss.zhtml.Head, such that
	 * the result HTML page is legal.
	 *
	 * @param exec the execution (never null)
	 * @return the string holding the HTML tags, or null if already generated.
	 * @param wapp the Web application.
	 * If null, exec.getDesktop().getWebApp() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution, such as JSP/DSP).
	 * @param deviceType the device type, such as ajax.
	 * If null, exec.getDesktop().getDeviceType() is used.
	 * So you have to specify it if the execution is not associated
	 * with desktop (a fake execution).
	 * @see #outHeaderZkTags
	 */
	public static
	String outZkTags(Execution exec, WebApp wapp, String deviceType) {
		if (exec.getAttribute(ATTR_ZK_TAGS_GENERATED) != null)
			return null;
		exec.setAttribute(ATTR_ZK_TAGS_GENERATED, Boolean.TRUE);

		final StringBuffer sb = new StringBuffer(512).append('\n')
			.append(outLangStyleSheets(exec, wapp, deviceType))
			.append(outLangJavaScripts(exec, wapp, deviceType));

		final Desktop desktop = exec.getDesktop();
		if (desktop != null && exec.getAttribute(ATTR_DESKTOP_JS_GENED) == null) {
			sb.append("<script type=\"text/javascript\">zkdt('")
				.append(desktop.getId()).append("','")
				.append(getContextURI(exec))
				.append("','").append(desktop.getUpdateURI(null))
				.append("');").append(outZkIconJS())
				.append("</script>\n");
		}

		return sb.toString();
	}
	/** Returns if the ZK specific HTML tags are generated.
	 * @since 5.0.3
	 */
	public static boolean isZkTagsGenerated(Execution exec) {
		return exec.getAttribute(ATTR_ZK_TAGS_GENERATED) != null;
	}
	/** Used to indicate ZK tags are generated. */
	private static final String ATTR_ZK_TAGS_GENERATED = "zkHtmlTagsGened";
	private static String getContextURI(Execution exec) {
		if (exec != null) {
			String s = exec.encodeURL("/");
			int j = s.lastIndexOf('/'); //might have jsessionid=...
			return j >= 0 ? s.substring(0, j) + s.substring(j + 1): s;
		}
		return "";
	}

	/** Sets whether a component can directly generate HTML tags
	 * to the output.
	 * @see #isDirectContent
	 */
	public static boolean setDirectContent(Execution exec, boolean direct) {
		return (direct ?
			exec.setAttribute(ATTR_DIRECT_CONTENT, Boolean.TRUE):
			exec.removeAttribute(ATTR_DIRECT_CONTENT)) != null;
	}
	/** Returns whether a component can directly generate HTML tags
	 * to the output.
	 * This flag is used by components that can generate the content
	 * directly, such as {@link org.zkoss.zk.ui.HtmlNativeComponent}
	 * @see #setDirectContent
	 */
	public static boolean isDirectContent(Execution exec) {
		if (exec == null)
			exec = Executions.getCurrent();
		return exec != null && exec.getAttribute(ATTR_DIRECT_CONTENT) != null;
	}

	/** The render context which consists of two writers ({@link #temp} and
	* {@link #perm}.
	 * @see HtmlPageRenders#getRenderContext
	 */
	public static class RenderContext {
		/** The writer used to generate the content that will
		 * be replaced after the widgets have been rendered.
		 * It is mainly used to put the crawlable content, which
		 * is used only for Search Engine.
		 * <p>It is never null.
		 */
		public final Writer temp;
		/** The writer used to generate the content that exists
		 * even after the widgets have been rendered.
		 * It is currenlty used only to generate CSS style.
		 * <p>It is never null.
		 */
		public final Writer perm;
		/** Indicates whether to generate crawlable content.
		 */
		public final boolean crawlable;

		private RenderContext(Writer temp, boolean crawlable) {
			this.temp = temp;
			this.perm = new StringWriter();
			this.crawlable = crawlable;
		}
	}
}
