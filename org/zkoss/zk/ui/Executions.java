/* Executions.java

	Purpose:
		
	Description:
		
	History:
		Fri Jun  3 17:55:08     2005, Created by tomyeh

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui;

import java.util.Map;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import org.zkoss.idom.Document;

import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.metainfo.PageDefinition;
import org.zkoss.zk.ui.metainfo.LanguageDefinition;
import org.zkoss.zk.xel.Evaluator;
import org.zkoss.zk.ui.sys.UiEngine;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.sys.DesktopCtrl;
import org.zkoss.zk.ui.sys.ExecutionsCtrl;
import org.zkoss.zk.ui.sys.ServerPush;

/**
 * Utilities to access {@link Execution}.
 *
 * @author tomyeh
 */
public class Executions {
	/** Stores the current {@link Execution}. */
	protected static final ThreadLocal _exec = new ThreadLocal();

	/** Returns the current execution.
	 */
	public static final Execution getCurrent() {
		return (Execution)_exec.get();
	}

	/** Returns the evaluator of the current execution.
	 * It is usually used to parse the expression into {@link org.zkoss.xel.Expression}
	 * or used with {@link org.zkoss.zk.xel.ExValue}.
	 * for performance improvement.
	 *
	 * @param page the page that this evaluator is associated.
	 * If null, the current page and then the first page is assumed.
	 * @param expfcls the implementation of {@link org.zkoss.xel.ExpressionFactory},
	 * or null to use the default ({@link org.zkoss.zk.ui.util.Configuration#getExpressionFactoryClass}.
	 * @since 3.0.0
	 */
	public static final Evaluator getEvaluator(Page page, Class expfcls) {
		return getCurrent().getEvaluator(page, expfcls);
	}
	/** Returns the evaluator of the current execution.
	 * It is a shortcut of getEvaluator(comp != null ? comp.getPage(): null)
	 *
	 * @param comp the component to retrieve the page for the evaluator
	 * @param expfcls the implementation of {@link org.zkoss.xel.ExpressionFactory},
	 * or null to use the default ({@link org.zkoss.zk.ui.util.Configuration#getExpressionFactoryClass}.
	 * @since 3.0.0
	 */
	public static final Evaluator getEvaluator(Component comp, Class expfcls) {
		return getCurrent().getEvaluator(comp, expfcls);
	}

	/** Evluates the specified expression by use of the current context
	 * ({@link #getCurrent}).
	 *
	 * <p>The function mapper is retrieved from component's page's function
	 * mapper ({@link Page#getFunctionMapper}).
	 * If null, the current page, if any, is used to retrieve
	 * the mapper.
	 *
	 * <p>For better performance, you can use the instance returned by
	 *{@link #getEvaluator} to parse and cached the parsed expression.
	 * {@link org.zkoss.zk.xel.ExValue} is a utility class to simply
	 * the task.
	 *
	 * @param comp as the self variable (ignored if null)
	 */
	public static final Object evaluate(Component comp,
	String expr, Class expectedType) {
		return getCurrent().evaluate(comp, expr, expectedType);
	}
	/** Evluates the specified expression with the resolver of the current
	 * execution ({@link #getCurrent}).
	 *
	 * <p>The function mapper is retrieved from page's function
	 * mapper ({@link Page#getFunctionMapper}).
	 * If null, the current page, if any, is used to retrieve
	 * the mapper.
	 *
	 * <p>For better performance, you can use the instance returned by
	 *{@link #getEvaluator} to parse and cached the parsed expression.
	 * {@link org.zkoss.zk.xel.ExValue} is a utility class to simply
	 * the task.
	 *
	 * @param page used as the self variable and to retrieve the function
	 * mapper if funmap is not defined. Ignored if null.
	 */
	public static final Object evaluate(Page page,
	String expr, Class expectedType) {
		return getCurrent().evaluate(page, expr, expectedType);
	}

	/** Encodes the specified URL.
	 *
	 * <p>It resolves "*" contained in URI, if any, to the proper Locale,
	 * and the browser code.
	 * Refer to {@link org.zkoss.web.servlet.Servlets#locate(jakarta.servlet.ServletContext, ServletRequest, String, Locator)}
	 * for details. 
	 *
	 * @exception NullPointerException if the current execution is not
	 * available.
	 * @see #encodeURL
	 */
	public static final String encodeURL(String uri) {
		return getCurrent().encodeURL(uri);
	}

	/**  Encodes the specified URL into an instance of {@link URL}.
	 * It is similar to {@link #encodeURL}, except it returns an instance
	 * of {@link URL}.
	 *
	 * @exception NullPointerException if the current execution is not
	 * available.
	 * @exception MalformedURLException if failed to convert it to
	 * a legal {@link URL}
	 * @since 3.5.0
	 */
	public static final URL encodeToURL(String uri)
	throws MalformedURLException {
		final Execution exec = getCurrent();
		uri = exec.encodeURL(uri);
		if (uri.indexOf("://") < 0) {
			final StringBuffer sb = new StringBuffer(256)
				.append(exec.getScheme()).append("://")
				.append(exec.getServerName());
			int port = exec.getServerPort();
			if (port != 80) sb.append(':').append(port);
			if (uri.length() > 0 && uri.charAt(0) != '/')
				sb.append('/');
			uri = sb.append(uri).toString();
		}
		return new URL(uri);
	}
	/** Creates components from a page file specified by an URI.
	 * Shortcut to {@link Execution#createComponents(String, Component, Map)}.
	 *
	 * @param parent the parent component, or null if you want it to be
	 * a root component. If parent is null, the page is assumed to be
	 * the current page, which is determined by the execution context.
	 * In other words, the new component will be the root component
	 * of the current page if parent is null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return the first component being created.
	 * @see #createComponents(PageDefinition, Component, Map)
	 */
	public static final Component createComponents(
	String uri, Component parent, Map arg) {
		return getCurrent().createComponents(uri, parent, arg);
	}
	/** Creates components based on the specified page definition.
	 * Shortcut to {@link Execution#createComponents(PageDefinition, Component, Map)}.
	 *
	 * @param pagedef the page definition to use. It cannot be null.
	 * @param parent the parent component, or null if you want it to be
	 * a root component. If parent is null, the page is assumed to be
	 * the current page, which is determined by the execution context.
	 * In other words, the new component will be the root component
	 * of the current page if parent is null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return the first component being created.
	 * @see #createComponents(String, Component, Map)
	 */
	public static final Component createComponents(PageDefinition pagedef,
	Component parent, Map arg) {
		return getCurrent().createComponents(pagedef, parent, arg);
	}

	/** Creates components from the raw content specified by a string.
	 * Shortcut to {@link Execution#createComponentsDirectly(String, String, Component, Map)}.
	 *
	 * @param content the raw content of the page. It must be a XML and
	 * compliant to the page format (such as ZUL).
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param parent the parent component, or null if you want it to be
	 * a root component. If parent is null, the page is assumed to be
	 * the current page, which is determined by the execution context.
	 * In other words, the new component will be the root component
	 * of the current page if parent is null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return the first component being created.
	 * @see #createComponents(PageDefinition, Component, Map)
	 * @see #createComponents(String, Component, Map)
	 * @see #createComponentsDirectly(Document, String, Component, Map)
	 * @see #createComponentsDirectly(Reader, String, Component, Map)
	 */
	public static final Component createComponentsDirectly(String content,
	String extension, Component parent, Map arg) {
		return getCurrent().createComponentsDirectly(content, extension, parent, arg);
	}
	/** Creates components from the raw content specified by a DOM tree.
	 * Shortcut to {@link Execution#createComponentsDirectly(Document, String, Component, Map)}.
	 *
	 * @param content the raw content in DOM.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param parent the parent component, or null if you want it to be
	 * a root component. If parent is null, the page is assumed to be
	 * the current page, which is determined by the execution context.
	 * In other words, the new component will be the root component
	 * of the current page if parent is null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return the first component being created.
	 * @see #createComponents(PageDefinition, Component, Map)
	 * @see #createComponents(String, Component, Map)
	 * @see #createComponentsDirectly(String, String, Component, Map)
	 * @see #createComponentsDirectly(Reader, String, Component, Map)
	 */
	public static final Component createComponentsDirectly(Document content,
	String extension, Component parent, Map arg) {
		return getCurrent().createComponentsDirectly(content, extension, parent, arg);
	}
	/** Creates components from the raw content read from the specified reader.
	 * Shortcut to {@link Execution#createComponentsDirectly(Reader, String, Component, Map)}.
	 *
	 * <p>The raw content is loader and parsed to a page defintion by use of
	 * {@link Execution#getPageDefinitionDirectly(Reader, String)}, and then
	 * invokes {@link #createComponents(PageDefinition,Component,Map)}
	 * to create components.
	 *
	 * @param reader the reader to retrieve the raw content.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param parent the parent component, or null if you want it to be
	 * a root component. If parent is null, the page is assumed to be
	 * the current page, which is determined by the execution context.
	 * In other words, the new component will be the root component
	 * of the current page if parent is null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return the first component being created.
	 * @see #createComponents(PageDefinition, Component, Map)
	 * @see #createComponents(String, Component, Map)
	 * @see #createComponentsDirectly(Document, String, Component, Map)
	 * @see #createComponentsDirectly(String, String, Component, Map)
	 */
	public static Component createComponentsDirectly(Reader reader,
	String extension, Component parent, Map arg)
	throws IOException {
		return getCurrent().createComponentsDirectly(reader, extension, parent, arg);
	}

	/** Creates components that don't belong to any page
	 * from the specified page definition.
	 *
	 * <p>Unlike {@link #createComponents(PageDefinition,Component,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param pagedef the page definition to use. It cannot be null.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return all top-level components being created.
	 * @see #createComponents(WebApp, String, Map)
	 * @since 3.6.2
	 */
	public static Component[] createComponents(WebApp wapp, PageDefinition pagedef, Map arg) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.createComponents(pagedef, arg);
		} finally {
			afterCC(cci);
		}
	}
	/** Creates components that don't belong to any page
	 * from a page file specified by an URI.
	 *
	 * <p>It loads the page definition from the specified URI (by
	 * use {@link #getPageDefinition} ), and then
	 * invokes {@link #createComponents(WebApp,PageDefinition,Map)}
	 * to create components.
	 *
	 * <p>Unlike {@link #createComponents(String,Component,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return all top-level components being created.
	 * @see #createComponents(WebApp, PageDefinition, Map)
	 * @see #createComponentsDirectly(WebApp, String, String, Map)
	 * @see #createComponentsDirectly(WebApp, Document, String, Map)
	 * @see #createComponentsDirectly(WebApp, Reader, String, Map)
	 * @since 3.6.2
	 */
	public static Component[] createComponents(WebApp wapp, String uri, Map arg) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.createComponents(uri, arg);
		} finally {
			afterCC(cci);
		}
	}
	/** Creates components that don't belong to any page
	 * from the raw content specified by a string.
	 *
	 * <p>The raw content is parsed to a page defintion by use of
	 * {@link #getPageDefinitionDirectly(WebApp,String,String)}, and then
	 * invokes {@link #createComponents(WebApp,PageDefinition,Map)}
	 * to create components.
	 *
	 * <p>Unlike {@link #createComponentsDirectly(String,String,Component,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param content the raw content of the page. It must be in ZUML.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return all top-level components being created.
	 * @see #createComponents(WebApp, PageDefinition, Map)
	 * @see #createComponents(WebApp, String, Map)
	 * @see #createComponentsDirectly(WebApp, Document, String, Map)
	 * @see #createComponentsDirectly(WebApp, Reader, String, Map)
	 * @since 3.6.2
	 */
	public static Component[]
	createComponentsDirectly(WebApp wapp, String content, String extension,
	Map arg) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.createComponentsDirectly(content, extension, arg);
		} finally {
			afterCC(cci);
		}
	}
	/** Creates components that don't belong to any page
	 * from the raw content specified by a DOM tree.
	 *
	 * <p>The raw content is parsed to a page defintion by use of
	 * {@link #getPageDefinitionDirectly(WebApp,Document, String)}, and then
	 * invokes {@link #createComponents(WebApp,PageDefinition,Map)}
	 * to create components.
	 *
	 * <p>Unlike {@link #createComponentsDirectly(Document,String,Component,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param content the raw content in DOM.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return all top-level components being created.
	 * @see #createComponents(WebApp, PageDefinition, Map)
	 * @see #createComponents(WebApp, String, Map)
	 * @see #createComponentsDirectly(WebApp, Document, String, Map)
	 * @see #createComponentsDirectly(WebApp, Reader, String, Map)
	 * @since 3.6.2
	 */
	public static Component[]
	createComponentsDirectly(WebApp wapp, Document content, String extension,
	Map arg) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.createComponentsDirectly(content, extension, arg);
		} finally {
			afterCC(cci);
		}
	}
	/** Creates components that don't belong to any page
	 * from the raw content read from the specified reader.
	 *
	 * <p>Unl
	 *
	 * <p>The raw content is loaded and parsed to a page defintion by use of
	 * {@link #getPageDefinitionDirectly(WebApp,Reader,String)}, and then
	 * invokes {@link #createComponents(WebApp,PageDefinition,Map)}
	 * to create components.
	 *
	 * <p>Unlike {@link #createComponentsDirectly(Reader,String,Component,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param reader the reader to retrieve the raw content in ZUML.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @param arg a map of parameters that is accessible by the arg variable
	 * in EL, or by {@link Execution#getArg}.
	 * Ignored if null.
	 * @return all top-level components being created.
	 * @see #createComponents(WebApp, PageDefinition, Map)
	 * @see #createComponents(WebApp, String, Map)
	 * @see #createComponentsDirectly(WebApp, Document, String, Map)
	 * @see #createComponentsDirectly(WebApp, String, String, Map)
	 * @since 3.6.2
	 */
	public static Component[] createComponentsDirectly(WebApp wapp, Reader reader, String extension,
	Map arg) throws IOException {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.createComponentsDirectly(reader, extension, arg);
		} finally {
			afterCC(cci);
		}
	}

	/** Returns the page definition from the page file specified by an URI.
	 *
	 * <p>Like {@link #createComponents(WebApp,PageDefinition,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param uri the URI of the page file.
	 *
	 * @see #getPageDefinitionDirectly(WebApp, String, String)
	 * @see #getPageDefinitionDirectly(WebApp, Document, String)
	 * @see #getPageDefinitionDirectly(WebApp, Reader, String)
	 * @since 3.6.2
	 */
	public static PageDefinition getPageDefinition(WebApp wapp, String uri) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.getPageDefinition(uri);
		} finally {
			afterCC(cci);
		}
	}
	/** Converts the specified page content to a page definition.
	 *
	 * <p>Like {@link #createComponents(WebApp,PageDefinition,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param content the raw content of the page. It must be in ZUML.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @see #getPageDefinitionDirectly(WebApp, Document, String)
	 * @see #getPageDefinitionDirectly(WebApp, Reader, String)
	 * @see #getPageDefinition
	 * @since 3.6.2
	 */
	public PageDefinition
	getPageDefinitionDirectly(WebApp wapp, String content, String extension) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.getPageDefinitionDirectly(content, extension);
		} finally {
			afterCC(cci);
		}
	}
	/** Converts the specified page content, in DOM, to a page definition.
	 *
	 * <p>Like {@link #createComponentsDirectly(WebApp,Document,String,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param content the raw content of the page in DOM.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @see #getPageDefinitionDirectly(WebApp, String, String)
	 * @see #getPageDefinitionDirectly(WebApp, Reader, String)
	 * @see #getPageDefinition
	 * @since 3.6.2
	 */
	public PageDefinition
	getPageDefinitionDirectly(WebApp wapp, Document content, String extension) {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.getPageDefinitionDirectly(content, extension);
		} finally {
			afterCC(cci);
		}
	}
	/** Reads the raw content from a reader and converts it into
	 * a page definition.
	 *
	 * <p>Like {@link #createComponentsDirectly(WebApp,Reader,String,Map)},
	 * this method can be inovked without the current execution, such as
	 * a working thread. In this case, the wapp argument must be specified.
	 *
	 * @param wapp the Web application. It is optional and used only if
	 * no current execution (e.g., in a working thread).
	 * @param reader used to input the raw content of the page. It must be in ZUML.
	 * @param extension the default extension if the content doesn't specify
	 * an language. In other words, if
	 * the content doesn't specify an language, {@link LanguageDefinition#getByExtension}
	 * is called.
	 * If extension is null and the content doesn't specify a language,
	 * the language called "xul/html" is assumed.
	 * @see #getPageDefinitionDirectly(WebApp, String, String)
	 * @see #getPageDefinitionDirectly(WebApp, Document, String)
	 * @see #getPageDefinition
	 * @since 3.6.2
	 */
	public PageDefinition
	getPageDefinitionDirectly(WebApp wapp, Reader reader, String extension)
	throws IOException {
		final CCInfo cci = beforeCC(wapp);
		try {
			return cci.exec.getPageDefinitionDirectly(reader, extension);
		} finally {
			afterCC(cci);
		}
	}

	private static final CCInfo beforeCC(WebApp wapp) {
		Execution exec = Executions.getCurrent();
		if (exec != null)
			return new CCInfo(exec, false);

		((WebAppCtrl)wapp).getUiEngine()
			.activate(exec = CCExecution.newInstance(wapp));
		return new CCInfo(exec, true);
		
	}
	private static final void afterCC(CCInfo cci) {
		if (cci.created) {
			try {
				((WebAppCtrl)cci.exec.getDesktop().getWebApp())
					.getUiEngine().deactivate(cci.exec);
			} catch (Throwable ex) {
			}
		}
	}
	private static class CCInfo {
		private final Execution exec;
		private final boolean created;
		private CCInfo(Execution exec, boolean created) {
			this.exec = exec;
			this.created = created;
		}
	}

	/** Sends a temporary redirect response to the client using the specified
	 * redirect location URL by use of the current execution,
	 * {@link #getCurrent}.
	 *
	 * <p>After calling this method, the caller shall end the processing
	 * immediately (by returning). All pending requests and events will
	 * be dropped.
	 *
	 * @param uri the URI to redirect to, or null to reload the same page
	 * @see Execution#sendRedirect
	 */
	public static void sendRedirect(String uri) {
		getCurrent().sendRedirect(uri);
	}

	/** A shortcut of Executions.getCurrent().include(page).
	 *
	 * @see Execution#include(Writer,String,Map,int)
	 * @see Execution#include(String)
	 */
	public static void include(String page)
	throws IOException {
		getCurrent().include(page);
	}
	/** A shortcut of Executions.getCurrent().forward(page).
	 *
	 * @see Execution#forward(Writer,String,Map,int)
	 * @see Execution#forward(String)
	 */
	public static void forward(String page)
	throws IOException {
		getCurrent().forward(page);
	}

	//-- wait/notify --//
	/** Suspends the current processing of an event and wait until the
	 * other thread invokes {@link #notify(Object)}, {@link #notifyAll(Object)},
	 * {@link #notify(Desktop, Object)} or {@link #notifyAll(Desktop, Object)}
	 * for the specified object.
	 *
	 * <p>It can only be called when the current thread is processing an event.
	 * And, when called, the current processing is suspended and ZK continues
	 * to process the next event and finally render the result.
	 *
	 * <p>It is typical use to implement a modal dialog where it won't return
	 * until the modal dialog ends.
	 *
	 * @param mutex any non-null object to identify what to notify.
	 * It must be same object passed to {@link #notify(Desktop, Object)}.
	 * If there is racing issue, you have to enclose it with
	 * <code>synchronized</code> (though it is optional).
	 * @exception UiException if it is called not during event processing.
	 * @exception SuspendNotAllowedException if there are too many suspended
	 * exceptions.
	 * Deployers can control the maximal allowed number of suspended exceptions
	 * by specifying <code>max-suspended-thread</code> in <code>zk.xml</code>,
	 * or invoking {@link org.zkoss.zk.ui.util.Configuration#setMaxSuspendedThreads}.
	 */
	public static final void wait(Object mutex)
	throws InterruptedException, SuspendNotAllowedException {
		getUiEngine().wait(mutex);
	}
	/** Wakes up a single event processing thread that is waiting on the
	 * specified object.
	 *
	 * <p>Unlike {@link #notify(Desktop, Object)}, this method can be invoked only
	 * in the event listener that processing the same desktop.
	 * In addition, this method can be called under the event listener.
	 *
	 * <p>Use {@link #notify(Desktop, Object)} if you want to notify in other
	 * thread, such as a working thread.
	 *
	 * @param mutex any non-null object to identify what to notify.
	 * It must be same object passed to {@link #wait}.
	 * If there is racing issue, you have to enclose it with
	 * <code>synchronized</code> (though it is optional).
	 * @see #notify(Desktop, Object)
	 * @see #notifyAll(Object)
	 * @exception UiException if it is called not during event processing.
	 */
	public static final void notify(Object mutex) {
		getUiEngine().notify(mutex);
	}
	/** Wakes up all event processing thread that are waiting on the
	 * specified object.
	 *
	 * <p>Unlike {@link #notify(Desktop, Object)}, this method can be invoked only
	 * in the event listener that processing the same desktop.
	 * In addition, this method can be called under the event listener.
	 *
	 * <p>Use {@link #notifyAll(Desktop, Object)} if you want to notify in other
	 * thread, such as a working thread.
	 *
	 * @param mutex any non-null object to identify what to notify.
	 * It must be same object passed to {@link #wait}.
	 * If there is racing issue, you have to enclose it with
	 * <code>synchronized</code> (though it is optional).
	 * @see #notify(Desktop, Object)
	 * @see #notifyAll(Object)
	 * @exception UiException if it is called not during event processing.
	 */
	public static final void notifyAll(Object mutex) {
		getUiEngine().notifyAll(mutex);
	}
	/** Wakes up a single event processing thread for the specified desktop
	 * that is waiting on the specified object.
	 *
	 * <p>Unlike {@link #notify(Object)}, this method can be called any time.
	 * It is designed to let working threads resume an event processing
	 * thread.
	 *
	 * <p>Notice: if this method is NOT called in an event processing thread,
	 * the resumed thread won't execute until the next request is received.
	 * To enforce it happen, you might use the timer component (found in ZUL).
	 *
	 * <p>Notice: to resolve racing issue, you usually need to follow
	 * this pattern.
	 * <pre><code>
//Event Handling Thread
synchronized (mutex) {
	final WorkingThread worker = new WorkingThread(desktop);
	synchronized (mutex) {
		worker.start();
		Executions.wait(mutex);
	}
	....
}
//Working Thread
public void run() {
	....
	synchronized (mutex) {
		Executions.notify(desktop, mutex);
	}
}
	 </code></pre>
	 *
	 * @param desktop the desktop which the suspended thread is processing.
	 * It must be the same desktop of the suspended thread.
	 * @param mutex any non-null object to identify what to notify.
	 * It must be same object passed to {@link #wait}.
	 * If there is racing issue, you have to enclose it with
	 * <code>synchronized</code> (though it is optional).
	 * @see #notify(Object)
	 * @see #notifyAll(Desktop, Object)
	 */
	public static final void notify(Desktop desktop, Object mutex) {
		getUiEngine(desktop).notify(desktop, mutex);
	}
	/** Wakes up all event processing theads for the specified desktop
	 * that are waiting on the specified object.
	 *
	 * <p>Unlike {@link #notifyAll(Object)}, this method can be called any time.
	 * It is designed to let working threads resume an event processing
	 * thread.
	 *
	 * <p>Notice: if this method is NOT called in an event processing thread,
	 * the resumed thread won't execute until the next request is received.
	 * To enforce it happen, you might use the timer component (found in ZUL).
	 *
	 * <p>Notice: to resolve racing issue, you usually need to follow
	 * this pattern.
	 * <pre><code>
//Event Handling Thread
synchronized (mutex) {
	final WorkingThread worker = new WorkingThread(desktop);
	synchronized (mutex) {
		worker.start();
		Executions.wait(mutex);
	}
	....
}
//Working Thread
public void run() {
	....
	synchronized (mutex) {
		Executions.notifyAll(desktop, mutex);
	}
}
	 </code></pre>
	 *
	 * @param desktop the desktop which the suspended thread is processing.
	 * It must be the same desktop of the suspended thread.
	 * @param mutex any non-null object to identify what to notify.
	 * It must be same object passed to {@link #wait}.
	 * If there is racing issue, you have to enclose it with
	 * <code>synchronized</code> (though it is optional).
	 * @see #notify(Object)
	 * @see #notifyAll(Desktop, Object)
	 */
	public static final void notifyAll(Desktop desktop, Object mutex) {
		getUiEngine(desktop).notifyAll(desktop, mutex);
	}

	/** Activates a server-push thread.
	 * It causes the current thread to wait until the desktop is available
	 * to access, the desktop no longer exists,
	 * or some other thread interrupts this thread.
	 *
	 * <p>A server-push thread is a working thread that manipulates a desktop
	 * independent of event listeners. It can manipulate the components
	 * of the desktop as long as it is activated.
	 *
	 * <p>Due to the overhead of using server-push threads, the server-push
	 * feature is disabled by default. To use it, you have to enable
	 * it first with {@link Desktop#enableServerPush}.
	 * Once enabled, you can use as many as sevrer-push threads you like
	 * (for the desktop with the server-push feature enabled).
	 *
	 * <p>Before a server-push thread can access the components of the
	 * desktop it belongs, you have to activate it first.
	 * To activate a server-push thread, you have to invoke {@link #activate}.
	 * Once it returns, the server-push thread is activated and it, like
	 * event listeners, can manipulate the components of the corresponding
	 * desktop directly.
	 *
	 * <p>A typical use pattern:
	 *
	 * <pre><code>class MyWorkingThread extends Thread {
	 *  public void run() {
	 *    while (anything_to_publish) {
	 *       //prepare something to publish
	 *       //you can create new components and manipulate them before
	 *       //activation, as long as they are not attached to the desktop
	 *
	 *       Executions.activate(desktop);
	 *       try {
	 *         try {
	 *           //activated
	 *           //manipulate the components that are attached the desktop
	 *         } finally {
	 *           Executions.deactivate(desktop)
	 *         }
	 *       } catch (DesktopUnavailableException ex) {
	 *         //clean up (since desktop is dead)
	*       }
	 *   }
	 * }
	 *}</code></pre>
	 *
	 * <p>Note: the access of components is sequentialized. That is,
	 * at most one server-push thread is activated. All others, including
	 * the event listeners, have to wait util it is deactivated
	 * (i.e., until {@link #deactivate} is called).
	 * Thus, it is better to minimize the time remaining activated.
	 * A typical practice is to create new components and manipulate them
	 * before activated. Then, you have to only attach them after activated.
	 *
	 * <pre><code> Tree tree = new Tree();
	 * new Treechildren().setParent(tree); //initialize the tree
	 * Exections.activate(desktop);
	 * try {
	 *   tree.setPage(page); //assume page is a page of desktop
	 *</code></pre>
	 *
	 * <p>Note: you don't need to invoke this method in the event listener
	 * since it is already activated when an event listen starts execution.
	 *
	 * @exception InterruptedException if it is interrupted by other thread
	 * @exception DesktopUnavailableException if the desktop is removed
	 * (when activating).
	 * @since 3.0.0
	 */
	public static final void activate(Desktop desktop)
	throws InterruptedException, DesktopUnavailableException {
		activate(desktop, 0);
	}
	/** Activates a server-push thread with, or until a certain amount of
	 * real time has elapsed.
	 * It causes the current thread to wait until the desktop is available
	 * to access, the desktop no longer exists,
	 * some other thread interrupts this thread,
	 * or a certain amount of real time has elapsed.
	 *
	 * @param timeout the maximum time to wait in milliseconds.
	 * Ingored (i.e., never timeout) if non-positive.
	 * @return whether it is activated or it is timeout.
	 * The only reason it returns false is timeout.
	 * @exception InterruptedException if it is interrupted by other thread
	 * @exception DesktopUnavailableException if the desktop is removed
	 * (when activating).
	 * @exception IllegalStateException if the server push is not
	 * enabled for this desktop yet ({@link Desktop#enableServerPush}).
	 * @since 3.0.0
	 * @see #activate(Desktop)
	 * @see #deactivate
	 */
	public static final boolean activate(Desktop desktop, long timeout)
	throws InterruptedException, DesktopUnavailableException {
		return ((DesktopCtrl)desktop).activateServerPush(timeout);
	}
	/** Deactivates a server-push thread.
	 * @since 3.0.0
	 * @see #activate(Desktop)
	 * @see #activate(Desktop, long)
	 */
	public static final void deactivate(Desktop desktop) {
		((DesktopCtrl)desktop).deactivateServerPush();
	}

	private static final UiEngine getUiEngine(Desktop desktop) {
		if (desktop == null)
			throw new IllegalArgumentException("desktop cannot be null");
		return ((WebAppCtrl)desktop.getWebApp()).getUiEngine();
	}
	private static final UiEngine getUiEngine() {
		final Execution exec = getCurrent();
		if (exec == null)
			throw new IllegalStateException("This method can be called only under an event listener");
		return ((WebAppCtrl)exec.getDesktop().getWebApp()).getUiEngine();
	}
}
