
package com.yagasoft.overcast.google;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Children;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.yagasoft.overcast.container.Container;
import com.yagasoft.overcast.container.Folder;
import com.yagasoft.overcast.container.IRemote;
import com.yagasoft.overcast.container.ITransferProgressListener;
import com.yagasoft.overcast.container.LocalFolder;


public class RemoteFolder extends com.yagasoft.overcast.container.RemoteFolder<File>
{

	/**
	 * Better use the factory in Google class.
	 */
	public RemoteFolder()
	{}

	/**
	 * @see com.yagasoft.overcast.container.Folder#create(com.yagasoft.overcast.container.Folder)
	 */
	@Override
	public void create(Folder<?> parent) throws Exception
	{
		RemoteFolder result = parent.searchByName(name, false);

		if (result != null)
		{
			throw new Exception("Folder already exists!");
		}

		File metadata = new File();
		metadata.setTitle(name);
		metadata.setMimeType("application/vnd.google-apps.folder");
		metadata.setParents(Arrays.asList(new ParentReference().setId(parent.getId())));

		try
		{
			Drive.Files.Insert insert = Google.driveService.files().insert(metadata);
			sourceObject = insert.execute();
			parent.add(this);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @see com.yagasoft.overcast.container.Folder#create(java.lang.String)
	 */
	@Override
	public void create(String parentPath) throws Exception
	{
		ArrayList<String> splitPath = new ArrayList<String>(Arrays.asList(path.split("/")));

		if (splitPath.get(0).equals(""))
		{
			splitPath.remove(0);
		}

		RemoteFolder parent = (RemoteFolder) csp.getRemoteFileTree();

		if (splitPath.size() > 0)
		{
			RemoteFolder result = parent.searchByName(splitPath.get(0), false);

			while ((result != null) && (splitPath.size() > 0))
			{
				parent = result;
				splitPath.remove(0);

				if (splitPath.size() > 0)
				{
					result = parent.searchByName(splitPath.get(0), false);
				}
				else
				{
					break;
				}
			}
		}

		while (splitPath.size() > 0)
		{
			RemoteFolder tempFolder = new RemoteFolder();
			tempFolder.setName(splitPath.remove(0));
			tempFolder.create(parent);

			parent = tempFolder;
		}

		RemoteFolder result = parent.searchByName(name, false);

		if (result != null)
		{
			throw new Exception("Folder already exists!");
		}
		else
		{
			create(parent);
		}
	}

	/**
	 * @see com.yagasoft.overcast.container.Container#isExist()
	 */
	@Override
	public boolean isExist()
	{
		return false;
	}

	/**
	 * @see com.yagasoft.overcast.container.Folder#buildTree(int)
	 */
	@Override
	public void buildTree(final int numberOfLevels)
	{
		if (numberOfLevels < 0)
		{
			return;
		}

		try
		{
			Children.List request = Google.driveService.children().list(id);
			request.setQ("trashed=false");

			ArrayList<String> childrenIds = new ArrayList<String>();

			do
			{
				try
				{
					ChildList children = request.execute();

					for (ChildReference child : children.getItems())
					{
						childrenIds.add(child.getId());
					}

					request.setPageToken(children.getNextPageToken());
				}
				catch (IOException e)
				{
					System.out.println("An error occurred: " + e);
					request.setPageToken(null);
				}
			} while ((request.getPageToken() != null) && (request.getPageToken().length() > 0));

			removeObsolete(childrenIds, true);

			if ( !childrenIds.isEmpty())
			{
				BatchRequest batch = Google.driveService.batch();

				JsonBatchCallback<File> callback = new JsonBatchCallback<File>()
				{

					@Override
					public void onSuccess(File remote, HttpHeaders responseHeaders)
					{
						if (remote.getMimeType().indexOf("folder") >= 0)
						{
							RemoteFolder folder = Google.factory.createFolder(remote, true);
							add(folder);

							System.out.println("Folder: " + folder.parent.getName() + "\\" + folder.name + " => " + folder.id);

							folder.buildTree(numberOfLevels - 1);
						}
						else
						{
							RemoteFile file = Google.factory.createFile(remote, true);
							add(file);

							System.out.println("File: " + name + "\\" + file.getName() + " => " + file.getId());
						}
					}

					@Override
					public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders)
					{
						System.out.println("Error Message: " + e.getMessage());
					}
				};

				for (String childId : childrenIds)
				{
					Google.driveService.files().get(childId).queue(batch, callback);
				}

				batch.execute();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @see com.yagasoft.overcast.container.Folder#calculateSize()
	 */
	@Override
	public long calculateSize()
	{
		return 0;
	}

	/**
	 * @see com.yagasoft.overcast.container.Folder#updateInfo(boolean, boolean)
	 */
	@Override
	public void updateInfo(boolean folderContents, boolean recursively)
	{
		id = sourceObject.getId();
		name = sourceObject.getTitle();

//		try
//		{
//			link= new URL(sourceObject.getDownloadUrl());
//		}
//		catch (MalformedURLException e)
//		{
//			e.printStackTrace();
//		}
	}

	/**
	 * @see com.yagasoft.overcast.container.Folder#updateFromSource(boolean,
	 *      boolean)
	 */
	@Override
	public void updateFromSource(boolean folderContents, final boolean recursively)
	{
		if (folderContents)
		{
			buildTree(recursively);
		}

		try
		{
			sourceObject = Google.driveService.files().get((sourceObject == null) ? id : sourceObject.getId()).execute();
			updateInfo();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @see com.yagasoft.overcast.container.Container#copy(com.yagasoft.overcast.container.Folder,
	 *      boolean)
	 */
	@Override
	public Container<?> copy(Folder<?> destination, boolean overwrite)
	{
		return null;
	}

	/**
	 * @see com.yagasoft.overcast.container.Container#move(com.yagasoft.overcast.container.Folder,
	 *      boolean)
	 */
	@Override
	public void move(Folder<?> destination, boolean overwrite)
	{}

	/**
	 * @see com.yagasoft.overcast.container.Container#rename(java.lang.String)
	 */
	@Override
	public void rename(String newName)
	{}

	/**
	 * @see com.yagasoft.overcast.container.Container#delete()
	 */
	@Override
	public void delete()
	{}

	/**
	 * @see com.yagasoft.overcast.container.Container#updateInfo()
	 */
	@Override
	public void updateInfo()
	{
		updateInfo(false, false);
	}

	/**
	 * @see com.yagasoft.overcast.container.Container#updateFromSource()
	 */
	@Override
	public void updateFromSource()
	{
		updateFromSource(false, false);
	}

	/**
	 * @see com.yagasoft.overcast.container.IRemote#download(com.yagasoft.overcast.container.Folder,
	 *      boolean, com.yagasoft.overcast.container.ITransferProgressListener,
	 *      java.lang.Object)
	 */
	@Override
	public void download(Folder<?> parent, boolean overwrite, ITransferProgressListener listener, Object object) throws Exception
	{
		Container<?> result = parent.searchByName(name, false);
		LocalFolder localFolder = null;

		if ((result == null) || !result.isFolder())
		{
			try
			{
				localFolder = new LocalFolder();
				localFolder.create(parent);
				localFolder.updateFromSource(true, false);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			localFolder = (LocalFolder) result;
		}

		localFolder.setRemoteMapping(this);
		setLocalMapping(localFolder);

		for (com.yagasoft.overcast.container.File<?> file : getFilesArray())
		{
			try
			{
				((IRemote) file).download(parent, overwrite, listener, object);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		for (Folder<?> childFolder : getFoldersArray())
		{
			((IRemote) childFolder).download(localFolder, overwrite, listener, object);
		}
	}
}
