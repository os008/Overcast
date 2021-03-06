/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			Licence terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.base.container.transfer/UploadJob.java
 *
 *			Modified: 24-Jun-2014 (21:33:14)
 *			   Using: Eclipse J-EE / JDK 8 / Windows 8.1 x64
 */

package com.yagasoft.overcast.base.container.transfer;


import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.File;
import com.yagasoft.overcast.base.container.Folder;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.remote.RemoteFile;
import com.yagasoft.overcast.base.container.transfer.event.TransferState;


/**
 * A class representing a job in the upload queue.<br />
 * It's needed to contain information vital to complete the upload process.
 *
 * @param <T>
 *            the type of the object to perform the actual upload.
 * @param <S>
 *            the type of the file given by the CSP.
 */
public abstract class UploadJob<T, S> extends TransferJob<T>
{

	/** The remote file object. */
	protected RemoteFile<S>	remoteFile;

	/**
	 * Instantiates a new upload job.
	 *
	 * @param localFile
	 *            the local file
	 * @param remoteFile
	 *            the remote file
	 * @param parent
	 *            the parent
	 * @param overwrite
	 *            overwrite existing file
	 * @param cspTransferer
	 *            uploader
	 */
	public UploadJob(LocalFile localFile, RemoteFile<S> remoteFile, Folder<?> parent, boolean overwrite, T cspTransferer)
	{
		super(localFile, parent, remoteFile.getCsp(), overwrite, cspTransferer);
		this.parent = parent;
		this.remoteFile = remoteFile;
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#isDownloadJob()
	 */
	@Override
	public boolean isDownloadJob()
	{
		return false;
	}

	/**
	 * Do NOT use!<br />
	 * As the CSP API file type can't be determined in this general implementation, so it has to be passed after creating an
	 * object of this job in the CSP.
	 *
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#success()
	 */
	@Override
	public void success()
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Perform stuff on successful upload.
	 *
	 * @param file
	 *            the file object from the original CSP API.
	 */
	public void success(S file)
	{
		remoteFile.setSourceObject(file);
		localFile.setRemoteMapping(remoteFile);
		remoteFile.setLocalMapping(localFile);
		parent.add(remoteFile);
		Logger.info("OVERCAST: UPLOAD JOB: uploaded successfully: " + remoteFile.getPath());
		notifyProgressListeners(TransferState.COMPLETED, 1.0f);
	}

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
		return localFile;
	}

	/**
	 * @see com.yagasoft.overcast.base.container.transfer.TransferJob#getDestinationFile()
	 */
	@Override
	public File<?> getDestinationFile()
	{
		return remoteFile;
	}

	// --------------------------------------------------------------------------------------
	// #region Getters and setters.

	/**
	 * @return the remoteFile
	 */
	public RemoteFile<S> getRemoteFile()
	{
		return remoteFile;
	}

	/**
	 * @param remoteFile
	 *            the remoteFile to set
	 */
	public void setRemoteFile(RemoteFile<S> remoteFile)
	{
		this.remoteFile = remoteFile;
	}

	// #endregion Getters and setters.
	// --------------------------------------------------------------------------------------

}
