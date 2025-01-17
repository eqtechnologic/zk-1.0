/* AuSetAttribute.java

	Purpose:
		
	Description:
		
	History:
		Thu Oct 13 11:28:05     2005, Created by tomyeh

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.au.out;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.au.AuResponse;

/**
 * A response to set the attribute of the specified component at the client.
 * <p>data[0]: the uuid of the component<br/>
 * data[1]: the attribute name<br/>
 * data[2]: the attribute value
 *
 * @author tomyeh
 * @since 3.0.0
 */
public class AuSetAttribute extends AuResponse {
	/** Construct an instance for miscellanous values, such as Boolean,
	 * Integer and so on.
	 * @param val the value. It could be null, String, Date,
	 * {@link org.zkoss.zk.ui.util.DeferredValue},
	 * and any kind of objects that
	 * the client accepts (marshaled by JSON).

	 * @since 5.0.0
	 */
	public AuSetAttribute(Component comp, String attr, Object val) {
		super("setAttr", comp, new Object[] {comp.getUuid(), attr, val});
	}
}
