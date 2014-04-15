/* 
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 * 
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			License terms are in a separate file (LICENCE.md)
 * 
 *		Project/File: Overcast/com.yagasoft.overcast.base.csp/CSP.java
 * 
 *			Modified: Apr 15, 2014 (9:33:52 AM)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.base.csp;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.Container;
import com.yagasoft.overcast.base.container.Folder;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.local.LocalFolder;
import com.yagasoft.overcast.base.container.operation.IOperationListener;
import com.yagasoft.overcast.base.container.operation.OperationEvent;
import com.yagasoft.overcast.base.container.remote.RemoteFactory;
import com.yagasoft.overcast.base.container.remote.RemoteFile;
import com.yagasoft.overcast.base.container.remote.RemoteFolder;
import com.yagasoft.overcast.base.container.transfer.DownloadJob;
import com.yagasoft.overcast.base.container.transfer.ITransferProgressListener;
import com.yagasoft.overcast.base.container.transfer.TransferJob;
import com.yagasoft.overcast.base.container.transfer.UploadJob;
import com.yagasoft.overcast.base.csp.authorisation.Authorisation;
import com.yagasoft.overcast.exception.CSPBuildException;
import com.yagasoft.overcast.exception.CreationException;
import com.yagasoft.overcast.exception.OperationException;
import com.yagasoft.overcast.exception.TransferException;


/**
 * The class representing the Cloud Storage Provider.<br />
 * The constructors should throw a {@link CSPBuildException} if it can't go through to the end.
 * 
 * @param <SourceFileType>
 *            The source file type (file type from the original CSP API) must be passed to this class.<br />
 *            It's needed to assist in creating the {@link RemoteFile}.
 * @param <DownloaderType>
 *            The type of the downloader (from the original CSP API). Used to create the job.
 * @param <UploaderType>
 *            The type of the uploader (from the original CSP API). Used to create the job.
 */
public abstract class CSP<SourceFileType, DownloaderType, UploaderType>
{
	
	/** Name of the CSP. Can be used to be displayed next to files in an application, for example. */
	protected String											name;
	
	/** Authorisation object. */
	protected Authorisation										authorisation;
	
	/** Root of the local file tree. */
	protected LocalFolder										localFileTree;
	
	/** Root of the remote file tree. */
	protected RemoteFolder<?>									remoteFileTree;
	
	/** Is the local file tree fully loaded in memory. */
	protected boolean											fullLocalTreeLoaded;
	
	/** Is the remote file tree fully loaded in memory. */
	protected boolean											fullRemoteTreeLoaded;
	
	/** Remote free space. */
	protected long												remoteFreeSpace;
	
	/** Download queue. */
	protected Queue<DownloadJob<DownloaderType>>				downloadQueue	= new LinkedList<DownloadJob<DownloaderType>>();
	
	/** Current download job. */
	protected DownloadJob<DownloaderType>						currentDownloadJob;
	
	/** Current download thread. */
	protected Thread											currentDownloadThread;
	
	/** Upload queue. */
	protected Queue<UploadJob<UploaderType, SourceFileType>>	uploadQueue		= new LinkedList<UploadJob<UploaderType, SourceFileType>>();
	
	/** Current upload job. */
	protected UploadJob<UploaderType, SourceFileType>			currentUploadJob;
	
	/** Current upload thread. */
	protected Thread											currentUploadThread;
	
	// you should add a RemoteFactory object in the subclasses.
	
	/**
	 * Initialises the tree -- only reads the root's content from the source (only single level).
	 * 
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract void initTree() throws OperationException;
	
	/**
	 * Builds the file tree by starting from the root and going down.
	 * 
	 * @param recursively
	 *            If true, then build all the levels possible under the root.
	 * @throws OperationException
	 *             the operation exception
	 */
	public void buildFileTree(boolean recursively) throws OperationException
	{
		// make sure there's a root to access.
		if (remoteFileTree == null)
		{
			initTree();
		}
		
		// build more levels if required.
		remoteFileTree.buildTree(recursively);
		
		Logger.endSection(">>> Finished building the root tree.");
	}
	
	/**
	 * Builds the file tree by starting from the root and going down.
	 * 
	 * @param numberOfLevels
	 *            How many levels to fetch -- 0 is root level only.
	 * @throws OperationException
	 *             the operation exception
	 */
	public void buildFileTree(int numberOfLevels) throws OperationException
	{
		remoteFileTree.buildTree(numberOfLevels);
		
		Logger.endSection(">>> Finished building the root tree up to level " + numberOfLevels);
	}
	
	/**
	 * Calculate remote free space available on the CSP.
	 * 
	 * @return the free space in bytes.
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract long calculateRemoteFreeSpace() throws OperationException;
	
	/**
	 * Download the folder (passed) from the server.<br />
	 * It creates the folder locally and all sub-folders if necessary.
	 * 
	 * @param folder
	 *            Folder to download.
	 * @param parent
	 *            The local folder to download to. Must pass a {@link LocalFolder} with the path initialised in it.
	 * @param overwrite
	 *            Whether to overwrite any existing files and folders on the local disk or not.
	 * @param listener
	 *            Object listening to the changes in the transfer state.
	 * @return the download jobs
	 * @throws TransferException
	 *             the transfer exception
	 * @throws OperationException
	 *             the operation exception
	 * @throws CreationException
	 *             the creation exception
	 */
	@SuppressWarnings("rawtypes")
	public DownloadJob<?>[] download(RemoteFolder<?> folder, LocalFolder parent, boolean overwrite
			, ITransferProgressListener listener) throws TransferException, OperationException, CreationException
	{
		Logger.newTitledSection("downloading folder");
		Logger.newEntry("downloading folder: " + folder.getPath());
		
		// make sure the folder doesn't exist at the destination.
		Container<?> result = parent.searchByName(folder.getName(), false);
		LocalFolder localFolder = null;
		
		// if it doesn't exist ...
		if ((result == null) || !result.isFolder())
		{
			// ... create the folder at the destination.
			localFolder = new LocalFolder();
			
			localFolder.create(parent, new IOperationListener()
			{
				
				@Override
				public void operationProgressChanged(OperationEvent event)
				{}
			});
			
			localFolder.updateFromSource(true, false);		// update the folder info
		}
		else
		{	// ... else, just use the one at the destination.
			localFolder = (LocalFolder) result;
		}
		
		// link the remote and local folders.
		localFolder.setRemoteMapping(folder);
		folder.setLocalMapping(localFolder);
		
		// collect the jobs in this array to be returned later.
		ArrayList<DownloadJob> downloadJobs = new ArrayList<DownloadJob>();
		
		// add each file in the folder to the download queue.
		for (com.yagasoft.overcast.base.container.File<?> file : folder.getFilesArray())
		{
			downloadJobs.add(download((RemoteFile) file, parent, overwrite, listener));
		}
		
		// call the download method for each sub-folder.
		for (Folder<?> childFolder : folder.getFoldersArray())
		{
			downloadJobs.addAll(new ArrayList<DownloadJob>(Arrays.asList(download((RemoteFolder) childFolder, localFolder,
					overwrite, listener))));
		}
		
		// return the jobs.
		return downloadJobs.toArray(new DownloadJob[downloadJobs.size()]);
	}
	
	/**
	 * Download the file (passed) from the server.<br />
	 * This method should prepare a job only -- not start the download, and then add the job to the queue.<br />
	 * Please, check the {@link DownloadJob} for what to do here.
	 * 
	 * @param file
	 *            File to download.
	 * @param parent
	 *            The local folder to download to. Must pass a {@link LocalFolder} with the path initialised in it.
	 * @param overwrite
	 *            Whether to overwrite existing file on the local disk or not.
	 * @param listener
	 *            Object listening to the changes in the transfer state.
	 * @return the download job
	 * @throws TransferException
	 *             A problem occurred during the transfer of the file.
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract DownloadJob<?> download(RemoteFile<?> file, LocalFolder parent, boolean overwrite,
			ITransferProgressListener listener)
			throws TransferException, OperationException;
	
	/**
	 * If the queue has a job, set it as the current one after removing it from the queue, and then start the job.<br />
	 * <br />
	 * This uses a separate thread so as not to block the program, and to be able to accept more jobs.<br />
	 * This method is automatically called after the job is done.
	 */
	public void nextDownloadJob()
	{
		// if there's nothing being transferred, and there's something in the queue ...
		if ((currentDownloadJob == null) && !downloadQueue.isEmpty())
		{
			// ... take one job from the queue ...
			currentDownloadJob = downloadQueue.remove();
			
			Logger.newSection(">>> starting a new download: " + currentDownloadJob.getRemoteFile().getPath());
			
			currentDownloadThread = new Thread(new Runnable()
			{
				
				@Override
				public void run()
				{
					try
					{	// start the transfer (starts when thread starts below).
						initiateDownload();
						
						Logger.endSection("Finished download.");
					}
					catch (TransferException e)
					{	// in case of failure, notify the listeners of the failure, and check for more jobs.
						e.printStackTrace();
						currentDownloadJob.failure();
						
						Logger.endSection("Download failed!");
					}
					finally
					{
						resetDownload();
					}
				}
			});
			
			// go ...
			currentDownloadThread.start();
		}
	}
	
	/**
	 * Contains the procedure that will actually download the file after its associated thread triggers.<br />
	 * The job's success method should be called here.
	 * 
	 * @throws TransferException
	 *             the transfer exception
	 */
	protected abstract void initiateDownload() throws TransferException;
	
	/**
	 * Checks the download queue, if it's the same job passed, then call {@link #cancelCurrentDownload()};
	 * if not, then simply remove it from the queue.
	 * 
	 * @param job
	 *            the job
	 */
	public void cancelDownload(TransferJob<?> job)
	{
		if (currentDownloadJob == job)
		{
			cancelCurrentDownload();
		}
		else
		{
			Logger.post("cancelling download: " + currentDownloadJob.getRemoteFile().getPath());
			
			downloadQueue.remove(job);
		}
	}
	
	/**
	 * Cancel current running download.
	 */
	public void cancelCurrentDownload()
	{
		Logger.post("cancelling download: " + currentDownloadJob.getRemoteFile().getPath());
		
		currentDownloadJob.cancelTransfer();
	}
	
	/**
	 * Resets the download by removing the one running and adding the next one. It does NOT cancel the current download.
	 */
	public void resetDownload()
	{
		currentDownloadJob = null;
		nextDownloadJob();
	}
	
	/**
	 * Upload the folder (passed) to the server.<br />
	 * It creates the folder remotely and all sub-folders if necessary.
	 * 
	 * @param folder
	 *            Folder to upload.
	 * @param parent
	 *            The remote folder to upload to. Must pass a {@link RemoteFolder} with the path initialised in it.
	 * @param overwrite
	 *            Whether to overwrite any existing files and folders on the server or not.
	 * @param listener
	 *            Object listening to the changes in the transfer state.
	 * @return the upload jobs
	 * @throws TransferException
	 *             the transfer exception
	 * @throws OperationException
	 *             the operation exception
	 * @throws CreationException
	 *             the creation exception
	 */
	@SuppressWarnings("rawtypes")
	public UploadJob<?, ?>[] upload(LocalFolder folder, RemoteFolder<?> parent, boolean overwrite
			, ITransferProgressListener listener) throws TransferException, OperationException, CreationException
	{
		Logger.newTitledSection("uploading folder");
		Logger.newEntry("uploading folder: " + folder.getPath());
		
		// check if the folder exists at the CSP.
		Container<?> result = parent.searchByName(folder.getName(), false);
		RemoteFolder<?> remoteFolder = null;
		
		// if it doesn't exist, create it.
		if ((result == null) || !result.isFolder())
		{
			remoteFolder = getAbstractFactory().createFolder();
			remoteFolder.setName(folder.getName());
			
			remoteFolder.create(parent, new IOperationListener()
			{
				
				@Override
				public void operationProgressChanged(OperationEvent event)
				{}
			});
			
			remoteFolder.updateFromSource(true, false);
		}
		else
		{
			remoteFolder = (RemoteFolder) result;
		}
		
		remoteFolder.setLocalMapping(folder);
		folder.setRemoteMapping(remoteFolder);
		
		ArrayList<UploadJob> uploadJobs = new ArrayList<UploadJob>();
		
		// go through the files in the folder, and create an upload job for them.
		for (com.yagasoft.overcast.base.container.File<?> file : folder.getFilesArray())
		{
			uploadJobs.add(upload((LocalFile) file, parent, overwrite, listener));
		}
		
		// check sub-folders as well.
		for (Folder childFolder : folder.getFoldersArray())
		{
			uploadJobs.addAll(new ArrayList<UploadJob>(Arrays.asList(upload((LocalFolder) childFolder, remoteFolder,
					overwrite, listener))));
		}
		
		return uploadJobs.toArray(new UploadJob[uploadJobs.size()]);
	}
	
	/**
	 * Upload the file (passed) to the server.
	 * 
	 * @param file
	 *            File to upload..
	 * @param parent
	 *            The remote folder to upload to. Must pass a {@link RemoteFolder} with the path initialised in it.
	 * @param overwrite
	 *            Whether to overwrite existing file on the server or not.
	 * @param listener
	 *            Object listening to the changes in the transfer state.
	 * @return the upload job
	 * @throws TransferException
	 *             A problem occurred during the transfer of the file.
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract UploadJob<?, ?> upload(LocalFile file, RemoteFolder<?> parent, boolean overwrite,
			ITransferProgressListener listener)
			throws TransferException, OperationException;
	
	/**
	 * If the queue has a job, set it as the current one after removing it from the queue, and then start the job. <br />
	 * This uses a separate thread so as not to block the program.
	 */
	public void nextUploadJob()
	{
		// if no transfers, and queue has a job ...
		if ((currentUploadJob == null) && !uploadQueue.isEmpty())
		{
			currentUploadJob = uploadQueue.remove();
			
			Logger.newSection(">>> starting a new upload: " + currentUploadJob.getLocalFile().getPath());
			
			currentUploadThread = new Thread(new Runnable()
			{
				
				@Override
				public void run()
				{
					try
					{
						// start the transfer (starts when thread starts below).
						initiateUpload();
						
						Logger.endSection("Finished upload.");
					}
 					catch (TransferException e)
					{
						e.printStackTrace();
						currentUploadJob.failure();
						
						Logger.endSection("Upload failed!");
					}
					finally
					{
						resetUpload();
					}
				}
			});
			
			// go ...
			currentUploadThread.start();
		}
	}
	
	/**
	 * Starts the upload and passes the result to the upload job to add to the file object.
	 * 
	 * @throws TransferException
	 *             the transfer exception
	 */
	protected abstract void initiateUpload() throws TransferException;
	
	/**
	 * Checks the upload queue, if it's the same job passed, then call {@link #cancelCurrentUpload()};
	 * if not, then simply remove it from the queue.
	 * 
	 * @param job
	 *            the job
	 */
	public void cancelUpload(TransferJob<?> job)
	{
		if (currentUploadJob == job)
		{
			cancelCurrentUpload();
		}
		else
		{
			Logger.post("cancelling upload: " + currentUploadJob.getLocalFile().getPath());
			
			uploadQueue.remove(job);
		}
	}
	
	/**
	 * Cancel current upload.
	 */
	public void cancelCurrentUpload()
	{
		Logger.post("cancelling upload: " + currentUploadJob.getLocalFile().getPath());
		
		currentUploadJob.cancelTransfer();
	}
	
	/**
	 * Resets the upload by removing the one running and adding the next one. It does NOT cancel the current upload.
	 */
	public void resetUpload()
	{
		currentUploadJob = null;
		nextUploadJob();
	}
	
	/**
	 * Returns the factory as an abstract one -- can't predict its type from here.
	 * 
	 * @return the abstract factory
	 */
	public abstract RemoteFactory<?, ?, ?, ?> getAbstractFactory();
	
	/**
	 * Returns the name of the CSP.
	 */
	@Override
	public String toString()
	{
		return name;
	}
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Getters and setters.
	// ======================================================================================
	
	/**
	 * Gets the name.
	 * 
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}
	
	/**
	 * Sets the name.
	 * 
	 * @param name
	 *            the name to set
	 */
	public void setName(String name)
	{
		this.name = name;
	}
	
	/**
	 * Gets the authorisation.
	 * 
	 * @return the authorisation
	 */
	public Authorisation getAuthorisation()
	{
		return authorisation;
	}
	
	/**
	 * Sets the authorisation.
	 * 
	 * @param value
	 *            the new authorisation
	 */
	public void setAuthorisation(Authorisation value)
	{
		authorisation = value;
	}
	
	/**
	 * Gets the local file tree.
	 * 
	 * @return the localFileTree
	 */
	public LocalFolder getLocalFileTree()
	{
		return localFileTree;
	}
	
	/**
	 * Sets the local file tree.
	 * 
	 * @param localFileTree
	 *            the localFileTree to set
	 */
	public void setLocalFileTree(LocalFolder localFileTree)
	{
		this.localFileTree = localFileTree;
	}
	
	/**
	 * Gets the remote file tree.
	 * 
	 * @return the remote file tree
	 */
	public RemoteFolder<?> getRemoteFileTree()
	{
		return remoteFileTree;
	}
	
	/**
	 * Sets the remote file tree.
	 * 
	 * @param value
	 *            the new remote file tree
	 */
	public void setRemoteFileTree(RemoteFolder<?> value)
	{
		remoteFileTree = value;
	}
	
	/**
	 * Checks if is full local tree loaded.
	 * 
	 * @return the fullLocalTreeLoaded
	 */
	public boolean isFullLocalTreeLoaded()
	{
		return fullLocalTreeLoaded;
	}
	
	/**
	 * Sets the full local tree loaded.
	 * 
	 * @param fullLocalTreeLoaded
	 *            the fullLocalTreeLoaded to set
	 */
	public void setFullLocalTreeLoaded(boolean fullLocalTreeLoaded)
	{
		this.fullLocalTreeLoaded = fullLocalTreeLoaded;
	}
	
	/**
	 * Checks if is full remote tree loaded.
	 * 
	 * @return the fullRemoteTreeLoaded
	 */
	public boolean isFullRemoteTreeLoaded()
	{
		return fullRemoteTreeLoaded;
	}
	
	/**
	 * Sets the full remote tree loaded.
	 * 
	 * @param fullRemoteTreeLoaded
	 *            the fullRemoteTreeLoaded to set
	 */
	public void setFullRemoteTreeLoaded(boolean fullRemoteTreeLoaded)
	{
		this.fullRemoteTreeLoaded = fullRemoteTreeLoaded;
	}
	
	/**
	 * Gets the remote free space.
	 * 
	 * @return the remoteFreeSpace
	 */
	public long getRemoteFreeSpace()
	{
		return remoteFreeSpace;
	}
	
	/**
	 * Sets the remote free space.
	 * 
	 * @param remoteFreeSpace
	 *            the remoteFreeSpace to set
	 */
	public void setRemoteFreeSpace(long remoteFreeSpace)
	{
		this.remoteFreeSpace = remoteFreeSpace;
	}
	
	// ======================================================================================
	// #endregion Getters and setters.
	// //////////////////////////////////////////////////////////////////////////////////////
	
}