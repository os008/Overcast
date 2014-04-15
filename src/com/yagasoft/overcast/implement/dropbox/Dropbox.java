/* 
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 * 
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			License terms are in a separate file (LICENCE.md)
 * 
 *		Project/File: Overcast/com.yagasoft.overcast.implement.dropbox/Dropbox.java
 * 
 *			Modified: Apr 15, 2014 (9:50:20 AM)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.implement.dropbox;


import java.util.Locale;

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.local.LocalFolder;
import com.yagasoft.overcast.base.container.operation.IOperationListener;
import com.yagasoft.overcast.base.container.operation.OperationEvent;
import com.yagasoft.overcast.base.container.transfer.ITransferProgressListener;
import com.yagasoft.overcast.base.container.transfer.TransferState;
import com.yagasoft.overcast.base.csp.CSP;
import com.yagasoft.overcast.exception.AuthorisationException;
import com.yagasoft.overcast.exception.CSPBuildException;
import com.yagasoft.overcast.exception.OperationException;
import com.yagasoft.overcast.exception.TransferException;
import com.yagasoft.overcast.implement.dropbox.authorisation.Authorisation;
import com.yagasoft.overcast.implement.dropbox.transfer.DownloadJob;
import com.yagasoft.overcast.implement.dropbox.transfer.Downloader;
import com.yagasoft.overcast.implement.dropbox.transfer.IProgressListener;
import com.yagasoft.overcast.implement.dropbox.transfer.UploadJob;
import com.yagasoft.overcast.implement.dropbox.transfer.Uploader;


/**
 * Class representing Dropbox. It handles authentication, transfer of files, and contains the root.
 */
public class Dropbox extends CSP<DbxEntry.File, Downloader, Uploader> implements IProgressListener
{

	/** The Dropbox singleton. */
	static private Dropbox	instance;

	/** Constant: application name. */
	static final String		APPLICATION_NAME	= "Overcast";

	/** The authorisation object. */
	static Authorisation	authorisation;

	/** The dropbox service object, which is used to call on any services. */
	static DbxClient		dropboxService;

	/** The remote file/folder factory. */
	static RemoteFactory	factory;

	/** User locale. */
	static final String		userLocale			= Locale.getDefault().toString();

	/** Request config. */
	static DbxRequestConfig	requestConfig;

	/**
	 * Instantiates a new Dropbox object.
	 *
	 * @param userID
	 *            User ID to identify this account.
	 * @param port
	 *            Port for server to receive access code -- any free port on localhost, but added to dev account.<br />
	 *            Please, refer to {@link Authorisation} for more details.
	 * @throws CSPBuildException
	 *             the CSP build exception
	 * @throws AuthorisationException
	 */
	private Dropbox(String userID, int port) throws CSPBuildException, AuthorisationException
	{
		Logger.newTitledSection("building csp");
		Logger.newEntry("building dropbox object");
		
		requestConfig = new DbxRequestConfig(userID, userLocale);

		// authenticate.
		authorisation = new Authorisation(userID, "dropbox.json", port);
		authorisation.authorise();

		// Create a DbxClient, which is what you use to make API calls.
		dropboxService = new DbxClient(requestConfig, authorisation.getAuthInfo().accessToken
				, authorisation.getAuthInfo().host);

		// initialise the remote file factory.
		factory = new RemoteFactory(this);

		name = "Dropbox";
		
		Logger.endSection("done building dropbox");
	}

	public static Dropbox getInstance(String userID, int port) throws CSPBuildException, AuthorisationException
	{
		if (instance == null)
		{
			instance = new Dropbox(userID, port);
		}

		return instance;
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initTree()
	 */
	@Override
	public void initTree() throws OperationException
	{
		remoteFileTree = factory.createFolder();
		remoteFileTree.setPath("/");
		remoteFileTree.updateFromSource();
		buildFileTree(false);
	}

	/**
	 * Calculate remote free space.
	 *
	 * @return Long
	 * @throws OperationException
	 *             the operation exception
	 * @see com.yagasoft.overcast.base.csp.CSP#calculateRemoteFreeSpace()
	 */
	@Override
	public long calculateRemoteFreeSpace() throws OperationException
	{
		Logger.newSection("getting dropbox freespace");
		
		try
		{
			DbxAccountInfo info = dropboxService.getAccountInfo();
			return info.quota.normal;
		}
		catch (DbxException e)
		{
			Logger.newEntry("failed to get free space!");
			
			e.printStackTrace();
			throw new OperationException("Couldn't determine free space. " + e.getMessage());
		}
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#download(com.yagasoft.overcast.base.container.remote.RemoteFile,
	 *      com.yagasoft.overcast.base.container.local.LocalFolder, boolean,
	 *      com.yagasoft.overcast.base.container.transfer.ITransferProgressListener)
	 */
	@Override
	public DownloadJob download(com.yagasoft.overcast.base.container.remote.RemoteFile<?> file, LocalFolder parent
			, boolean overwrite, ITransferProgressListener listener) throws TransferException, OperationException
	{
		Logger.newSection("creating download job for " + file.getPath());
		
		// check for the file existence in the parent
		for (com.yagasoft.overcast.base.container.File<?> child : parent.getFilesArray())
		{
			// if it exists ...
			if (file.getName().equals(child.getName()))
			{
				// ... delete if required.
				if (overwrite)
				{
					child.delete(new IOperationListener()
					{

						@Override
						public void operationProgressChanged(OperationEvent event)
						{}
					});
				}
				else
				{
					throw new OperationException("File exists!");
				}
			}
		}

		// create a download job and add it to the queue.
		DownloadJob downloadJob = new DownloadJob((RemoteFile) file, parent, overwrite, null, null);
		Downloader downloader = new Downloader(file.getPath(), parent.getPath(), downloadJob);
		downloadJob.setCspTransferer(downloader);
		downloadJob.setCanceller(downloader);
		downloader.addProgressListener(this);
		downloadJob.addProgressListener(listener);
		downloadQueue.add(downloadJob);
		nextDownloadJob();		// check if this job can be executed right away.

		Logger.endSection("done!");
		
		return downloadJob;
	}

	@Override
	protected void initiateDownload() throws TransferException
	{
		// download the file.
		DbxEntry.File file = currentDownloadJob.getCspTransferer().startDownload();

		// the operation wasn't cancelled ...
		if (file != null)
		{
			currentDownloadJob.success();
		}
	}

	@Override
	public void progressChanged(DownloadJob downloadJob, TransferState state, float progress)
	{
		switch (state)
		{
			case INITIALISED:
				currentDownloadJob.notifyProgressListeners(state, progress);
				break;

			case IN_PROGRESS:
				currentDownloadJob.progress(progress);
				break;

			case CANCELLED:
				currentDownloadJob.notifyProgressListeners(state, progress);
				break;

			default:
				break;
		}
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#upload(com.yagasoft.overcast.base.container.local.LocalFile,
	 *      com.yagasoft.overcast.base.container.remote.RemoteFolder, boolean,
	 *      com.yagasoft.overcast.base.container.transfer.ITransferProgressListener, java.lang.Object)
	 */
	@Override
	public UploadJob upload(LocalFile file, com.yagasoft.overcast.base.container.remote.RemoteFolder<?> parent
			, boolean overwrite, ITransferProgressListener listener) throws TransferException, OperationException
	{
		Logger.newSection("creating upload job for " + file.getPath());
		
		// overwrite if necessary.
		for (com.yagasoft.overcast.base.container.File<?> child : parent.getFilesArray())
		{
			if (child.getName().equals(file.getName()))
			{
				if (overwrite)
				{
					child.delete(new IOperationListener()
					{

						@Override
						public void operationProgressChanged(OperationEvent event)
						{}
					});
				}
				else
				{
					throw new OperationException("File exists!");
				}
			}
		}

		// create an object for the file that's going to be uploaded to be linked to.
		RemoteFile remoteFile = factory.createFile();

		// create an upload job.
		UploadJob uploadJob = new UploadJob(file, remoteFile, (RemoteFolder) parent
				, overwrite, null, null);
		Uploader uploader = new Uploader(parent.getPath(), file.getPath(), uploadJob);
		uploadJob.setCspTransferer(uploader);
		uploadJob.setCanceller(uploader);
		uploader.addProgressListener(this);
		uploadJob.addProgressListener(listener);
		uploadQueue.add(uploadJob);		// add it to the queue.
		nextUploadJob();		// check if it can be executed immediately.
		
		Logger.endSection("done!");

		return uploadJob;
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initiateUpload()
	 */
	@Override
	protected void initiateUpload() throws TransferException
	{
		// upload the file and retrieve the result.
		DbxEntry.File file = currentUploadJob.getCspTransferer().startUpload();

		// the operation wasn't cancelled ...
		if (file != null)
		{
			currentUploadJob.success(file);
		}
	}

	/**
	 * @see com.yagasoft.overcast.implement.dropbox.transfer.IProgressListener#progressChanged(com.yagasoft.overcast.implement.dropbox.transfer.UploadJob,
	 *      com.yagasoft.overcast.base.container.transfer.TransferState, float)
	 */
	@Override
	public void progressChanged(UploadJob uploadJob, TransferState state, float progress)
	{
		switch (state)
		{
			case INITIALISED:
				currentUploadJob.notifyProgressListeners(state, progress);
				break;

			case IN_PROGRESS:
				currentUploadJob.progress(progress);
				break;

			case CANCELLED:
				currentUploadJob.notifyProgressListeners(state, progress);
				break;

			default:
				break;
		}
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#getAbstractFactory()
	 */
	@Override
	public com.yagasoft.overcast.base.container.remote.RemoteFactory<?, ?, ?, ?> getAbstractFactory()
	{
		return factory;
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Getters and setters.
	// ======================================================================================

	/**
	 * Gets the factory.
	 *
	 * @return the factory
	 */
	public static RemoteFactory getFactory()
	{
		return factory;
	}

	/**
	 * @return the authorisation
	 */
	@Override
	public Authorisation getAuthorisation()
	{
		return authorisation;
	}

	/**
	 * @return the dropboxService
	 */
	public static DbxClient getDropboxService()
	{
		return dropboxService;
	}

	/**
	 * @return the requestConfig
	 */
	public static DbxRequestConfig getRequestConfig()
	{
		return requestConfig;
	}

	// ======================================================================================
	// #endregion Getters and setters.
	// //////////////////////////////////////////////////////////////////////////////////////

}