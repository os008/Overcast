/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		Modified MIT License (GPL v3 compatible)
 * 			License terms are in a separate file (license.txt)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.container.transfer/UploadJob.java
 *
 *			Modified: 18-Mar-2014 (16:28:12)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.container.transfer;


import com.yagasoft.overcast.container.File;
import com.yagasoft.overcast.container.Folder;
import com.yagasoft.overcast.container.local.LocalFile;
import com.yagasoft.overcast.container.remote.RemoteFile;


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
	 * Do NOT use!<br />
	 * As the CSP API file type can't be determined in this general implementation, so it has to be passed after creating an
	 * object of this job in the CSP.
	 * 
	 * @see com.yagasoft.overcast.container.transfer.TransferJob#success()
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
		remoteFile.updateInfo();
		localFile.setRemoteMapping(remoteFile);
		remoteFile.setLocalMapping(localFile);
		parent.add(remoteFile);
		notifyProgressListeners(TransferState.COMPLETED, 1.0f);
	}
	
	/**
	 * @see com.yagasoft.overcast.container.transfer.TransferJob#failure()
	 */
	@Override
	public void failure()
	{
		notifyProgressListeners(TransferState.FAILED, 0.0f);
	}
	
	/**
	 * @see com.yagasoft.overcast.container.transfer.TransferJob#getSourceFile()
	 */
	@Override
	public File<?> getSourceFile()
	{
		return localFile;
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
