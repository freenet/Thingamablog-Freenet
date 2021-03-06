/*
 * todesbaum-lib -
 * Copyright (C) 2006 David Roden
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package net.sf.thingamablog.util.freenet.fcp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.thingamablog.util.io.Closer;

/**
 * Implementation of the <code>ClientPutComplexDir</code> command. This
 * command can be used to insert directories that do not exist on disk.
 *
 * @author David Roden &lt;droden@gmail.com&gt;
 * @version $Id: ClientPutComplexDir.java 17481 2008-02-02 21:21:16Z bombe $
 */
public class ClientPutComplexDir extends ClientPutDir {

	/** The file entries of this directory. */
	private List fileEntries = new ArrayList();

	/** Whether this request has payload. */
	private boolean hasPayload = false;

	/** The input streams for the payload. */
	private File payloadFile;

	/** The total number of bytes of the payload. */
	private long payloadLength = 0;

	/**
	 * Creates a new <code>ClientPutComplexDir</code> command with the specified identifier and URI.
	 * @param identifier The identifier of the command
	 * @param uri The URI of the command
	 */
	public ClientPutComplexDir(String identifier, String uri) {
		super("ClientPutComplexDir", identifier, uri);
	}

	/**
	 * Adds a file to the directory inserted by this request.
	 * @param fileEntry The file entry to add to the directory
	 */
	public void addFileEntry(FileEntry fileEntry) {
		if (fileEntry instanceof DirectFileEntry) {
			if (payloadFile == null){
				try {
					payloadFile = File.createTempFile("payload", ".dat");
					payloadFile.deleteOnExit();
				} catch (IOException e) {
				}
			}
			if (payloadFile != null) {
				InputStream payloadInputStream = ((DirectFileEntry) fileEntry).getDataInputStream();
				FileOutputStream payloadOutputStream = null;
				try {
					payloadOutputStream = new FileOutputStream(payloadFile, true);
					byte[] buffer = new byte[65536];
					int read = 0;
					while ((read = payloadInputStream.read(buffer)) != -1) {
						payloadOutputStream.write(buffer, 0, read);
					}
					payloadOutputStream.flush();
					fileEntries.add(fileEntry);
				} catch (IOException ioe1) {
					/* hmm, ignore? */
				} finally {
					Closer.close(payloadOutputStream);
					Closer.close(payloadInputStream);
				}
			}
		} else {
			fileEntries.add(fileEntry);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	protected void write(Writer writer) throws IOException {
		super.write(writer);
		int fileIndex = 0;
                Iterator it = fileEntries.iterator();
		while (it.hasNext()) {
                    FileEntry fileEntry = (FileEntry) it.next();
			writer.write("Files." + fileIndex + ".Name=" + fileEntry.getFilename() + LINEFEED);
			if (fileEntry.getContentType() != null) {
				writer.write("Files." + fileIndex + ".Metadata.ContentType=" + fileEntry.getContentType() + LINEFEED);
			}
			writer.write("Files." + fileIndex + ".UploadFrom=" + fileEntry.getName() + LINEFEED);
			if (fileEntry instanceof DirectFileEntry) {
				hasPayload = true;
				writer.write("Files." + fileIndex + ".DataLength=" + ((DirectFileEntry) fileEntry).getDataLength() + LINEFEED);
				payloadLength += ((DirectFileEntry) fileEntry).getDataLength();
			} else if (fileEntry instanceof DiskFileEntry) {
				writer.write("Files." + fileIndex + ".Filename=" + ((DiskFileEntry) fileEntry).getLocalFilename() + LINEFEED);
			} else if (fileEntry instanceof RedirectFileEntry) {
				writer.write("Files." + fileIndex + ".TargetURI=" + ((RedirectFileEntry) fileEntry).getTargetURI() + LINEFEED);
			}
			fileIndex++;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	protected boolean hasPayload() {
		return hasPayload;
	}

	/**
	 * {@inheritDoc}
	 */
	protected long getPayloadLength() {
		return payloadLength;
	}

	/**
	 * {@inheritDoc}
	 */
	protected InputStream getPayload() {
		if (payloadFile != null) {
			try {
				return new FileInputStream(payloadFile);
			} catch (FileNotFoundException e) {
				/* shouldn't occur. */
			}
		}
		return null;
	}

}
