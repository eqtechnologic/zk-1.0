/* ZkFileItemFactory.java

	Purpose:
		
	Description:
		
	History:
		Mon Aug 14 19:21:26     2006, Created by tomyeh

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under LGPL Version 3.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.au.http;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;

import org.zkoss.util.logging.Log;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.impl.Attributes;

/**
 * The file item factory that monitors the progress of uploading.
 *
 * @author tomyeh
 */
/*package*/ class ZkFileItemFactory extends DiskFileItemFactory {
	private static final Log log = Log.lookup(ZkFileItemFactory.class);

	private final Desktop _desktop;
	private final String _key;
	/** The total length (content length). */
	private long _cbtotal;
	/** # of bytes being received. */
	private long _cbrcv;

	/*package*/ ZkFileItemFactory(Desktop desktop, HttpServletRequest request, String key) {
    	setSizeThreshold(1024*128);	// maximum size that will be stored in memory

		_desktop = desktop;
		_key = key;
		long cbtotal = 0;
		String ctlen = request.getHeader("content-length");
		if (ctlen != null)
			try {
				cbtotal = Long.parseLong(ctlen.trim());
				//if (log.debugable()) log.debug("content-length="+cbtotal);
			} catch (Throwable ex) {
				log.warning(ex);
			}
		_cbtotal = cbtotal;
		
		if (_desktop.getAttribute(Attributes.UPLOAD_PERCENT) == null) {
			_desktop.setAttribute(Attributes.UPLOAD_PERCENT, new HashMap());
			_desktop.setAttribute(Attributes.UPLOAD_SIZE, new HashMap());
		}
		((Map)_desktop.getAttribute(Attributes.UPLOAD_PERCENT)).put(key, new Integer(0));
		((Map)_desktop.getAttribute(Attributes.UPLOAD_SIZE)).put(key, new Long(_cbtotal));
	}

	/*package*/ void onProgress(long cbRead) {
		int percent = 0;
		if (_cbtotal > 0) {
			_cbrcv = cbRead;
			percent = (int)(_cbrcv * 100 / _cbtotal);
		}
		((Map)_desktop.getAttribute(Attributes.UPLOAD_PERCENT)).put(_key, new Integer(percent));
	}

	//-- FileItemFactory --//
    public FileItem createItem(String fieldName, String contentType,
	boolean isFormField, String fileName) {
		return new ZkFileItem(fieldName, contentType, isFormField, fileName,
			getSizeThreshold(), getRepository());
	}

	//-- helper classes --//
	/** FileItem created by {@link ZkFileItemFactory}.
	 */
	/*package*/ class ZkFileItem extends DiskFileItem {
		/*package*/ ZkFileItem(String fieldName, String contentType,
		boolean isFormField, String fileName, int sizeThreshold,
		File repository) {
			super(fieldName, contentType, isFormField,
				fileName, sizeThreshold, repository);
		}

		/** Returns the charset by parsing the content type.
		 * If none is defined, UTF-8 is assumed.
		 */
	    public String getCharSet() {
			final String charset = super.getCharSet();
			return charset != null ? charset: "UTF-8";
		}
	}

	/*package*/ class ProgressCallback implements ProgressListener {
	    public void update(long pBytesRead, long pContentLength, int pItems) {
	    	onProgress(pBytesRead);
	    	if (pContentLength >= 0)
	    		_cbtotal = pContentLength;
	    }
	}
}
