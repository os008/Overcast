/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			Licence terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.base.container.transfer/DownloadJob.java
 *
 *			Modified: 24-Jun-2014 (21:33:03)
 *			   Using: Eclipse J-EE / JDK 8 / Windows 8.1 x64
 */

package com.yagasoft.overcast.base.container.transfer;


import java.nio.file.Paths;

import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.File;
import com.yagasoft.overcast.base.container.Folder;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.remote.RemoteFile;
import com.yagasoft.overcast.base.container.transfer.event.TransferState;


/**
 * A class representing a job in the download queue.<br />
 * It's needed to contain information vital to complete the download process.
 *
 * @param <T>
 *            the type of the object to perform the actual download.
 */
public abstract class DownloadJob<T> extends TransferJob<T>
{

	/** The remote file to download. */
	protected RemoteFile<?>	remoteFile;

	/**
	 * Instantiates a new download job.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @param parent
	 *            the parent
	 * @param overwrite
	 *            overwrite already existing file.
	 * @param cspTransferer
	 *            downloader
	 */
	public DownloadJob(RemoteFile<?> remoteFile, Folder<?> parent, boolean overwrite, T cspTransferer)
	{
		super(new LocalFile(), parent, remoteFile.getCsp(), overwrite, cspTransferer);
		this.parent = parent;
		this.remoteFile = remoteFile;

		// prepare the local file object
		localFile.setSourceObject(Paths.get(parent.getPath(), remoteFile.getName()));
		localFile.setPath(localFile.getSourceObject().toString());
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#isDownloadJob()
	 */
	@Override
	public boolean isDownloadJob()
	{
		return true;
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#success()
	 */
	@Override
	public void success()
	{
		localFile.updateInfo();
		localFile.setRemoteMapping(remoteFile);
		remoteFile.setLocalMapping(localFile);
		parent.add(localFile);
		Logger.info("OVERCAST: DOWNJOB: success: " + localFile.getPath());
		notifyProgressListeners(TransferState.COMPLETED, 1.0f);
	}

	/**
	 * ...
	 *
	 * @param path
	 *            the path
	 */
	public void success(String path)
	{}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#failure()
	 */
	@Override
	public void failure()
	{
		notifyProgressListeners(TransferState.FAILED, 0.0f);
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#getSourceFile()
	 */
	@Override
	public File<?> getSourceFile()
	{
		return remoteFile;
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#getDestinationFile()
	 */
	@Override
	public File<?> getDestinationFile()
	{
		return localFile;
	}

	// --------------------------------------------------------------------------------------
	// #region Getters and setters.

	/**
	 * @return the remoteFile
	 */
	public RemoteFile<?> getRemoteFile()
	{
		return remoteFile;
	}

	/**
	 * @param remoteFile
	 *            the remoteFile to set
	 */
	public void setRemoteFile(RemoteFile<?> remoteFile)
	{
		this.remoteFile = remoteFile;
	}

	// #endregion Getters and setters.
	// --------------------------------------------------------------------------------------

}
