/* Wpds.java

	Purpose:
		
	Description:
		
	History:
		Fri Jul 17 22:10:49     2009, Created by tomyeh

Copyright (C) 2009 Potix Corporation. All Rights Reserved.

This program is distributed under LGPL Version 3.0 in the hope that
it will be useful, but WITHOUT ANY WARRANTY.
*/
package org.zkoss.zk.ui.http;

import java.util.Iterator;
import java.util.Locale;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.text.DecimalFormatSymbols;

import org.zkoss.lang.Strings;
import org.zkoss.lang.Objects;
import org.zkoss.util.CacheMap;
import org.zkoss.util.Locales;
import org.zkoss.web.Attributes;

import org.zkoss.zk.ui.metainfo.LanguageDefinition;
import org.zkoss.zk.ui.metainfo.ComponentDefinition;

/**
 * Utilities to used with WPD files.
 *
 * @author tomyeh
 * @since 5.0.0
 */
public class Wpds {
	/** Generates all widgets in the specified language.
	 * @param lang the language to look at
	 */
	public static String outWidgetListJavaScript(String lang) {
		final StringBuffer sb = new StringBuffer(4096)
			.append("zk.wgt.WidgetInfo.register([");

		boolean first = true;
		for (Iterator it = LanguageDefinition.lookup(lang)
		.getComponentDefinitions().iterator(); it.hasNext();) {
			final ComponentDefinition compdef = (ComponentDefinition)it.next();
			for (Iterator e = compdef.getMoldNames().iterator(); e.hasNext();) {
				final String mold = (String)e.next();
				final String wgtcls = compdef.getWidgetClass(null, mold);
				if (wgtcls != null) {
					if (first) first = false;
					else sb.append(',');
					sb.append('\'').append(wgtcls).append('\'');
				}
			}
		}

		return sb.append("]);").toString();
	}

	/** Generates Locale-dependent strings in JavaScript syntax.
	 */
	public final static String outLocaleJavaScript() {
		final Locale locale = Locales.getCurrent();
		return outNumberJavaScript(locale) + outDateJavaScript(locale);
	}
	/** Output number relevant texts.
	 */
	private final static String outNumberJavaScript(Locale locale) {
		final DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
		final StringBuffer sb = new StringBuffer(128);
		appendAssignJavaScript(
			sb, "zk.GROUPING", symbols.getGroupingSeparator());
		appendAssignJavaScript(
			sb, "zk.DECIMAL", symbols.getDecimalSeparator());
		appendAssignJavaScript(
			sb, "zk.PERCENT", symbols.getPercent());
		appendAssignJavaScript(
			sb, "zk.MINUS", symbols.getMinusSign());
		appendAssignJavaScript(
			sb, "zk.PER_MILL", symbols.getPerMill());
		return sb.toString();
	}
	private final static
	void appendAssignJavaScript(StringBuffer sb, String nm, char val) {
		final char quot = val == '"' ? '\'': '"';
		sb.append(nm).append('=').append(quot).append(val).append(quot).append(";\n");
	}
	/** Output date/calendar relevant labels.
	 */
	private final static String outDateJavaScript(Locale locale) {
		final int firstDayOfWeek = Utils.getFirstDayOfWeek();
		final String djkey = locale + ":" + firstDayOfWeek;
		synchronized (_datejs) {
			final String djs = (String)_datejs.get(djkey);
			if (djs != null) return djs;
		}

		String djs = getDateJavaScript(locale, firstDayOfWeek);
		synchronized (_datejs) { //OK to race
			//To minimize memory use, reuse the string if they are the same
			//which is common
			for (Iterator it = _datejs.values().iterator(); it.hasNext();) {
				final String val = (String)it.next();
				if (val.equals(djs))
					djs = val; 
			}
			_datejs.put(djkey, djs);
		}
		return djs;
	}
	private final static String getDateJavaScript(Locale locale, int firstDayOfWeek) {
		final StringBuffer sb = new StringBuffer(512);
		final Calendar cal = Calendar.getInstance(locale);
		cal.clear();

		if (firstDayOfWeek < 0)
			firstDayOfWeek = cal.getFirstDayOfWeek();
		sb.append("zk.DOW_1ST=")
			.append(firstDayOfWeek - Calendar.SUNDAY)
			.append(";\n");

		//Note: no need to df.setTimeZone(TimeZones.getCurrent()) since
		//it is used to generate locale-dependent labels

		final boolean zhlang = locale.getLanguage().equals("zh");
		SimpleDateFormat df = new SimpleDateFormat("E", locale);
		final String[] sdow = new String[7], s2dow = new String[7];
		for (int j = firstDayOfWeek, k = 0; k < 7; ++k) {
			cal.set(Calendar.DAY_OF_WEEK, j);
			sdow[k] = df.format(cal.getTime());
			if (++j > Calendar.SATURDAY) j = Calendar.SUNDAY;

			if (zhlang) {
				s2dow[k] = sdow[k].length() >= 3 ?
					sdow[k].substring(2): sdow[k];
			} else {
				final int len = sdow[k].length();
				final char cc  = sdow[k].charAt(len - 1);
				s2dow[k] = cc == '.' || cc == ',' ?
					sdow[k].substring(0, len - 1): sdow[k];
			}
		}
		df = new SimpleDateFormat("G", locale);
		sb.append("zk.ERA=\"").append(df.format(new java.util.Date())).append("\";\n");
		

		Calendar ec = Calendar.getInstance(Locale.ENGLISH);
		Calendar lc = Calendar.getInstance(locale);
		sb.append("zk.YDELTA=").append(lc.get(Calendar.YEAR) - ec.get(Calendar.YEAR)).append(";\n");
		
		df = new SimpleDateFormat("EEEE", locale);
		final String[] fdow = new String[7];
		for (int j = firstDayOfWeek, k = 0; k < 7; ++k) {
			cal.set(Calendar.DAY_OF_WEEK, j);
			fdow[k] = df.format(cal.getTime());
			if (++j > Calendar.SATURDAY) j = Calendar.SUNDAY;
		}

		df = new SimpleDateFormat("MMM", locale);
		final String[] smon = new String[12], s2mon = new String[12];
		for (int j = 0; j < 12; ++j) {
			cal.set(Calendar.MONTH, j);
			smon[j] = df.format(cal.getTime());

			if (zhlang) {
				s2mon[j] = smon[0].length() >= 2 ? //remove the last char
					smon[j].substring(0, smon[j].length() -1): smon[j];
			} else {
				final int len = smon[j].length();
				final char cc  = smon[j].charAt(len - 1);
				s2mon[j] = cc == '.' || cc == ',' ?
					smon[j].substring(0, len - 1): smon[j];
			}
		}

		df = new SimpleDateFormat("MMMM", locale);
		final String[] fmon = new String[12];
		for (int j = 0; j < 12; ++j) {
			cal.set(Calendar.MONTH, j);
			fmon[j] = df.format(cal.getTime());
		}

		appendJavaScriptArray(sb, "SDOW", sdow);
		if (Objects.equals(s2dow, sdow))
			sb.append("zk.S2DOW=zk.SDOW;\n");
		else
			appendJavaScriptArray(sb, "S2DOW", s2dow);
		if (Objects.equals(fdow, sdow))
			sb.append("zk.FDOW=zk.SDOW;\n");
		else
			appendJavaScriptArray(sb, "FDOW", fdow);

		appendJavaScriptArray(sb, "SMON", smon);
		if (Objects.equals(s2mon, smon))
			sb.append("zk.S2MON=zk.SMON;\n");
		else
			appendJavaScriptArray(sb, "S2MON", s2mon);
		if (Objects.equals(fmon, smon))
			sb.append("zk.FMON=zk.SMON;\n");
		else
			appendJavaScriptArray(sb, "FMON", fmon);

		//AM/PM available since ZK 3.0
		df = new SimpleDateFormat("a", locale);
		cal.set(Calendar.HOUR_OF_DAY, 3);
		final String[] ampm = new String[2];
		ampm[0] = df.format(cal.getTime());
		cal.set(Calendar.HOUR_OF_DAY, 15);
		ampm[1] = df.format(cal.getTime());
		appendJavaScriptArray(sb, "APM", ampm);

		return sb.toString();
	}
	private static final void appendJavaScriptArray(StringBuffer sb,
	String varnm, String[] vals) {
		sb.append("zk.").append(varnm).append("=[");
		for (int j = 0;;) {
			sb.append('\'')
				.append(Strings.escape(vals[j], Strings.ESCAPE_JAVASCRIPT))
				.append('\'');
			if (++j >= vals.length) break;
			else sb.append(',');
		}
		sb.append("];\n");
	}
	private static final CacheMap _datejs;
	static {
		_datejs = new CacheMap(8);
		_datejs.setLifetime(24*60*60*1000);
	}
}
