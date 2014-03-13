/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		Modified MIT License (GPL v3 compatible)
 * 			License terms are in a separate file (license.txt)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.ubuntu/Ubuntu.java
 *
 *			Modified: 13-Mar-2014 (17:51:43)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.ubuntu;


import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

import com.ubuntuone.api.files.U1FileAPI;
import com.ubuntuone.api.files.model.U1File;
import com.ubuntuone.api.files.model.U1Node;
import com.ubuntuone.api.files.model.U1User;
import com.ubuntuone.api.files.request.U1DownloadListener;
import com.ubuntuone.api.files.request.U1UploadListener;
import com.ubuntuone.api.files.util.U1Failure;
import com.ubuntuone.api.files.util.U1RequestListener.U1UserRequestListener;
import com.yagasoft.overcast.CSP;
import com.yagasoft.overcast.container.local.LocalFile;
import com.yagasoft.overcast.container.local.LocalFolder;
import com.yagasoft.overcast.container.remote.RemoteFile;
import com.yagasoft.overcast.container.transfer.DownloadJob;
import com.yagasoft.overcast.container.transfer.ITransferProgressListener;
import com.yagasoft.overcast.container.transfer.TransferState;
import com.yagasoft.overcast.container.transfer.UploadJob;
import com.yagasoft.overcast.exception.OperationException;
import com.yagasoft.overcast.exception.TransferException;


/**
 * Class representing Ubuntu One. It handles authentication, transfer of files, and contains the root.
 */
public class Ubuntu extends CSP<U1File, U1DownloadListener, U1UploadListener>
{

	/** The authorisation. */
	private Authorisation	authorisation;

	/** The http client. */
	static HttpClient		httpClient;

	/** Files API instance. */
	static U1FileAPI		ubuntuService;

	/** The remote file factory. */
	static RemoteFactory	factory;

	private boolean			operationSuccess	= false;

	/**
	 * Instantiates a new Ubuntu.
	 */
	public Ubuntu()
	{
		// get the communication medium.
		httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager());

		// authorise using the username and password.
		authorisation = new Authorisation("os1983@gmail.com", "i018AiOOU9geK");
		authorisation.authorise();

		// create the Ubuntu One service to be used for all operations.
		ubuntuService = new U1FileAPI(Ubuntu.class.getPackage().getName(), "1.0", httpClient, authorisation.oauthAuthorizer);

//		ubuntuService.getNode("/~/Ubuntu One", new U1NodeListener()
//		{
//
//			@Override
//			public void onSuccess(U1Node node)
//			{
//				System.out.println(node.getPath());
//			}
//
//			@Override
//			public void onUbuntuOneFailure(U1Failure failure)
//			{
//				System.err.println("Ubuntu One error: " + failure.getMessage());
//			}
//
//			@Override
//			public void onFailure(U1Failure failure)
//			{
//				System.err.println("General error: " + failure.getMessage());
//			}
//		});

//		ubuntuService.listDirectory("/~/Ubuntu One", new U1NodeRequestListener()
//		{
//
//			@Override
//			public void onStart()
//			{}
//
//			@Override
//			public void onSuccess(U1Node result)
//			{
//				System.out.println(result.getKey() + " => " + result.getResourcePath() + " : " + result.getKind());
//			}
//
//			@Override
//			public void onFinish()
//			{}
//
//			@Override
//			public void onUbuntuOneFailure(U1Failure failure)
//			{
//				System.err.println("Ubuntu One failure: " + failure);
//			}
//
//			@Override
//			public void onFailure(U1Failure failure)
//			{
//				System.err.println("General failure: " + failure);
//			}
//		});

		// create the factory.
		factory = new RemoteFactory(this);

		try
		{
			remoteFreeSpace = calculateRemoteFreeSpace();
		}
		catch (OperationException e)
		{
			e.printStackTrace();
		}

		name = "Ubuntu One";
	}

	@Override
	public void initTree()
	{
		remoteFileTree = factory.createFolder();
		remoteFileTree.setPath("/~/Ubuntu One");
		remoteFileTree.updateFromSource(false, false);
		buildFileTree(false);
	}

//	@Override
//	public void buildFileTree(boolean recursively)
//	{
//		remoteFileTree.buildTree(recursively);
//	}

	/**
	 * @see com.yagasoft.overcast.CSP#download(com.yagasoft.overcast.container.remote.RemoteFolder,
	 *      com.yagasoft.overcast.container.local.LocalFolder, boolean,
	 *      com.yagasoft.overcast.container.transfer.ITransferProgressListener, java.lang.Object)
	 */
	@Override
	public DownloadJob<?>[] download(com.yagasoft.overcast.container.remote.RemoteFolder<?> folder, LocalFolder parent,
			boolean overwrite,
			ITransferProgressListener listener, Object object)
	{
		return null;
	}

	/**
	 * @see com.yagasoft.overcast.CSP#download(com.yagasoft.overcast.container.remote.RemoteFile,
	 *      com.yagasoft.overcast.container.local.LocalFolder, boolean,
	 *      com.yagasoft.overcast.container.transfer.ITransferProgressListener, java.lang.Object)
	 */
	@Override
	public DownloadJob<?> download(RemoteFile<?> file, LocalFolder parent, boolean overwrite,
			ITransferProgressListener listener,
			Object object) throws TransferException, OperationException
	{
		// overwrite if required.
		for (com.yagasoft.overcast.container.File<?> child : parent.getFilesArray())
		{
			if (file.getName().equals(child.getName()))
			{
				if (overwrite)
				{
					child.delete();
				}
				else
				{
					throw new TransferException("File exists!");
				}
			}
		}

		file.addProgressListener(listener, object);

		DownloadJob<U1DownloadListener> downloadJob = new DownloadJob<U1DownloadListener>(file, parent, overwrite
				, new U1DownloadListener()
				{

					@Override
					public void onStart()
					{
						currentDownloadJob.notifyListeners(TransferState.INITIALISED, 0.0f);
					}

					@Override
					public void onProgress(long bytes, long total)
					{
						currentDownloadJob.progress(bytes / (float) total);
					}

					@Override
					public void onSuccess()
					{
						currentDownloadJob.success();
					}

					@Override
					public void onUbuntuOneFailure(U1Failure failure)
					{
						currentDownloadJob.failure();
						System.err.println("Ubuntu One failure: " + failure);
					}

					@Override
					public void onFailure(U1Failure failure)
					{
						currentDownloadJob.failure();
						System.err.println("Generic failure: " + failure);
					}

					@Override
					public void onCancel()
					{
						System.err.println("Upload canceled!");
					}

					@Override
					public void onFinish()
					{
						currentDownloadJob = null;
						nextDownloadJob();
					}
				});
		downloadQueue.add(downloadJob);		// add to queue.

		nextDownloadJob();		// check if immediate execution is possible.

		return downloadJob;
	}

	/**
	 * @see com.yagasoft.overcast.CSP#nextDownloadJob()
	 */
	@Override
	public void nextDownloadJob()
	{
		new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				// if current transfer is empty and the queue is not ...
				if ((currentDownloadJob == null) && !downloadQueue.isEmpty())
				{
					currentDownloadJob = downloadQueue.remove();

					// download the file.
					ubuntuService.downloadFile(currentDownloadJob.getRemoteFile().getPath()
							, currentDownloadJob.getLocalFile().getPath()
							, currentDownloadJob.getCspTransferer(), null);
				}
			}
		}).start();
	}

	/**
	 * @see com.yagasoft.overcast.CSP#upload(com.yagasoft.overcast.container.local.LocalFolder,
	 *      com.yagasoft.overcast.container.remote.RemoteFolder, boolean,
	 *      com.yagasoft.overcast.container.transfer.ITransferProgressListener, java.lang.Object)
	 */
	@Override
	public UploadJob<?, ?>[] upload(LocalFolder folder, com.yagasoft.overcast.container.remote.RemoteFolder<?> parent,
			boolean overwrite,
			ITransferProgressListener listener, Object object)
	{
		return null;
	}

	/**
	 * @see com.yagasoft.overcast.CSP#upload(com.yagasoft.overcast.container.local.LocalFile,
	 *      com.yagasoft.overcast.container.remote.RemoteFolder, boolean,
	 *      com.yagasoft.overcast.container.transfer.ITransferProgressListener, java.lang.Object)
	 */
	@Override
	public UploadJob<?, ?> upload(final LocalFile file, com.yagasoft.overcast.container.remote.RemoteFolder<?> parent,
			boolean overwrite,
			ITransferProgressListener listener, Object object) throws TransferException, OperationException
	{
		// overwrite if required.
		for (com.yagasoft.overcast.container.File<?> child : parent.getFilesArray())
		{
			if (child.getName().equals(file.getName()))
			{
				if (overwrite)
				{
					child.delete();
				}
				else
				{
					throw new TransferException("File exists!");
				}
			}
		}

		file.addProgressListener(listener, object);

		RemoteFile<U1File> remoteFile = factory.createFile();

		UploadJob<U1UploadListener, U1File> uploadJob = new UploadJob<U1UploadListener, U1File>(
				file, remoteFile, parent, overwrite
				, new U1UploadListener()
				{

					@Override
					public void onStart()
					{
						currentUploadJob.notifyListeners(TransferState.INITIALISED, 0.0f);
					}

					@Override
					public void onProgress(long bytes, long total)
					{
						currentUploadJob.progress(bytes / (float) total);
					}

					@Override
					public void onSuccess(U1Node node)
					{
						currentUploadJob.success((U1File) node);
					}

					@Override
					public void onUbuntuOneFailure(U1Failure failure)
					{
						currentUploadJob.failure();
						System.err.println("Ubuntu One failure: " + failure);
					}

					@Override
					public void onFailure(U1Failure failure)
					{
						currentUploadJob.failure();
						System.err.println("Generic failure: " + failure);
					}

					@Override
					public void onCancel()
					{
						System.err.println("Upload canceled!");
					}

					@Override
					public void onFinish()
					{
						currentUploadJob = null;
						nextUploadJob();
					}
				});
		uploadQueue.add(uploadJob);

		nextUploadJob();

		return uploadJob;
	}

	/**
	 * @see com.yagasoft.overcast.CSP#nextUploadJob()
	 */
	@Override
	public void nextUploadJob()
	{
		new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				// if no current transfer and queue is empty ...
				if ((currentUploadJob == null) && !uploadQueue.isEmpty())
				{
					currentUploadJob = uploadQueue.remove();

					// upload the file.
					ubuntuService.uploadFile(currentUploadJob.getLocalFile().getPath(), currentUploadJob.getLocalFile().getType()
							, currentUploadJob.getParent().getPath() + "/" + currentUploadJob.getLocalFile().getName(), true,
							false
							, currentUploadJob.getCspTransferer(), null);
				}
			}
		}).start();
	}

	/**
	 * @see com.yagasoft.overcast.CSP#calculateRemoteFreeSpace()
	 */
	@Override
	public long calculateRemoteFreeSpace() throws OperationException
	{
		operationSuccess = false;

		ubuntuService.getUser(new U1UserRequestListener()
		{

			@Override
			public void onStart()
			{}

			@Override
			public void onSuccess(U1User result)
			{
				remoteFreeSpace = result.getMaxBytes() - result.getUsedBytes();
				operationSuccess = true;
			}

			@Override
			public void onUbuntuOneFailure(U1Failure failure)
			{}

			@Override
			public void onFailure(U1Failure failure)
			{}

			@Override
			public void onFinish()
			{}
		});

		if ( !operationSuccess)
		{
			throw new OperationException("Failed to get free space.");
		}
		else
		{
			operationSuccess = false;

			return remoteFreeSpace;
		}
	}

	// --------------------------------------------------------------------------------------
	// #region Getters and setters.

	/**
	 * @return the ubuntuService
	 */
	public static U1FileAPI getUbuntuService()
	{
		return ubuntuService;
	}

	/**
	 * @param ubuntuService
	 *            the ubuntuService to set
	 */
	public static void setUbuntuService(U1FileAPI ubuntuService)
	{
		Ubuntu.ubuntuService = ubuntuService;
	}

	/**
	 * @return the factory
	 */
	public static RemoteFactory getFactory()
	{
		return factory;
	}

	/**
	 * @param factory
	 *            the factory to set
	 */
	public static void setFactory(RemoteFactory factory)
	{
		Ubuntu.factory = factory;
	}

	/**
	 * @return the httpClient
	 */
	public static HttpClient getHttpClient()
	{
		return httpClient;
	}

	/**
	 * @param httpClient
	 *            the httpClient to set
	 */
	public static void setHttpClient(HttpClient httpClient)
	{
		Ubuntu.httpClient = httpClient;
	}

	// #endregion Getters and setters.
	// --------------------------------------------------------------------------------------

}
