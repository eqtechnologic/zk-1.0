/* DropEvent.java

	Purpose:
		
	Description:
		
	History:
		Mon Feb 27 00:08:50     2006, Created by tomyeh

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.event;

import java.util.Map;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.au.AuRequest;
import org.zkoss.zk.au.AuRequests;

/**
 * Represents an event cause by user's dragging and dropping a component.
 *
 * <p>The component being dragged can be retrieved by {@link #getDragged}.
 * The component that received the dragged component is {@link #getTarget}.
 * 
 * @author tomyeh
 */
public class DropEvent extends MouseEvent {
	private final Component _dragged;

	/** Converts an AU request to a drop event.
	 * @since 5.0.0
	 */
	public static DropEvent getDropEvent(AuRequest request) {
		final Map data = request.getData();
		final int keys = AuRequests.parseKeys(data);
		return new DropEvent(request.getCommand(), request.getComponent(),
			request.getDesktop().getComponentByUuid((String)data.get("dragged")),
			AuRequests.getInt(data, "x", 0), AuRequests.getInt(data, "y", 0),
			AuRequests.getInt(data, "pageX", 0), AuRequests.getInt(data, "pageY", 0),
			keys);
	}

	/** Constructs a drop event.
	 * @param dragged The component being dragged and drop to {@link #getTarget}.
	 * @since 5.0.0
	 */
	public DropEvent(String name, Component target, Component dragged,
	int x, int y, int pageX, int pageY, int keys) {
		super(name, target, x, y, pageX, pageY, keys);
		_dragged = dragged;
	}
	/** @deprecated As of release 5.0.0, replaced with
	 * {@link #DropEvent(String,Component,Component,int,int,int)}.
	 */
	public DropEvent(String name, Component target, Component dragged,
	int x, int y, int keys) {
		this(name, target, dragged, x, y, x, y, keys);
	}
	/** Returns the component being dragged and drop to {@link #getTarget}.
	 */
	public final Component getDragged() {
		return _dragged;
	}
	/** Not appliable to {@link DropEvent}.
	 * It always returns null if you drag and drop a component to
	 * components that partition itself into several areas, such
	 * as <code>imagemap</code>
	 */
	public String getArea() {
		return null;
	}
}
