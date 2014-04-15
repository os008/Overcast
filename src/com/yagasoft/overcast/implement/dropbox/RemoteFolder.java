/* 
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 * 
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			License terms are in a separate file (LICENCE.md)
 * 
 *		Project/File: Overcast/com.yagasoft.overcast.implement.dropbox/RemoteFolder.java
 * 
 *			Modified: Apr 15, 2014 (9:56:55 AM)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.implement.dropbox;


import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.Container;
import com.yagasoft.overcast.base.container.Folder;
import com.yagasoft.overcast.base.container.operation.IOperationListener;
import com.yagasoft.overcast.base.container.operation.Operation;
import com.yagasoft.overcast.base.container.operation.OperationEvent;
import com.yagasoft.overcast.base.container.operation.OperationState;
import com.yagasoft.overcast.exception.AccessException;
import com.yagasoft.overcast.exception.CreationException;
import com.yagasoft.overcast.exception.OperationException;


/**
 * RemoteFolder of Dropbox.
 * 
 * @see com.yagasoft.overcast.base.container.remote.RemoteFolder
 */
public class RemoteFolder extends com.yagasoft.overcast.base.container.remote.RemoteFolder<DbxEntry.Folder>
{
	
	/**
	 * Better use the factory in Google class.
	 */
	public RemoteFolder()
	{}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#generateId()
	 */
	@Override
	public void generateId()
	{}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Folder#create(com.yagasoft.overcast.base.container.Folder, IOperationListener)
	 */
	@Override
	public synchronized void create(Folder<?> parent, IOperationListener listener) throws CreationException
	{
		addOperationListener(listener, Operation.CREATE);
		
		// check if the folder exists in the parent ...
		RemoteFolder result = parent.searchByName(name, false);
		
		try
		{
			// if it exists, problem!
			if (result != null)
			{
				throw new CreationException("Folder already Exists!");
			}
			
			sourceObject = Dropbox.dropboxService.createFolder(parent.getPath() + "/" + name);
			parent.add(this);
			notifyOperationListeners(Operation.CREATE, OperationState.COMPLETED, 1.0f);
		}
		catch (DbxException | CreationException e)
		{
			e.printStackTrace();
			notifyOperationListeners(Operation.CREATE, OperationState.FAILED, 0f);
			throw new CreationException("Couldn't create folder! " + e.getMessage());
		}
		finally
		{
			clearOperationListeners(Operation.CREATE);
		}
		
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#isExist()
	 */
	@Override
	public synchronized boolean isExist() throws AccessException
	{
		Logger.newSection("checking folder existence " + path);
		
		// if fetching meta-data of the file fails, then it doesn't exist, probably.
		try
		{
			return (Dropbox.dropboxService.getMetadata((sourceObject == null) ? path : sourceObject.path) != null);
		}
		catch (DbxException e)
		{
			Logger.endSection("problem!");
			
			e.printStackTrace();
			throw new AccessException("Couldn't determine existence! " + e.getMessage());
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Folder#buildTree(int)
	 */
	@Override
	public synchronized void buildTree(int numberOfLevels) throws OperationException
	{
		Logger.newTitledSection("building file tree");
		Logger.newEntry("building tree of " + path);
		
		// no more levels to check.
		if (numberOfLevels < 0)
		{
			return;
		}
		
		try
		{
			// get folder list from Dropbox (metadata)
			DbxEntry.WithChildren listing = Dropbox.dropboxService.getMetadataWithChildren(path);
			
			//
			HashMap<String, DbxEntry> children = new HashMap<String, DbxEntry>();
			
			for (DbxEntry child : listing.children)
			{
				children.put(child.isFolder() ? child.path : ((DbxEntry.File) child).rev, child);
			}
			
			// collect the children IDs and filter already existing and deleted ones.
			ArrayList<String> childrenIds = new ArrayList<String>(children.keySet());
			removeObsolete(childrenIds, true);
			
			// if there're new children on the server ...
			if ( !childrenIds.isEmpty())
			{
				// check each one ...
				for (String child : childrenIds)
				{
					DbxEntry childAsEntry = children.get(child);
					
					// if the child is a folder ...
					if (childAsEntry.isFolder())
					{
						// create an object for it using the factory.
						RemoteFolder folder = Dropbox.factory.createFolder((DbxEntry.Folder) childAsEntry, false);
						add(folder);	// add it to this parent.
						
						Logger.newEntry("found folder: " + folder.parent.getName() + "/" + folder.name + " => " + folder.id);
					}
					else
					{
						RemoteFile file = Dropbox.factory.createFile((DbxEntry.File) childAsEntry, false);
						add(file);
						
						Logger.newEntry("found file: " + name + "/" + file.getName() + " => " + file.getId());
					}
				}
			}
		}
		catch (DbxException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			throw new OperationException("Failed to build tree! " + e.getMessage());
		}
		
		// load sub-folders up to the level.
		for (Folder<?> folder : getFoldersArray())
		{
			folder.buildTree(numberOfLevels - 1);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Folder#calculateSize()
	 */
	@Override
	public synchronized long calculateSize() throws OperationException
	{
		return 0;
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Folder#updateInfo(boolean, boolean)
	 */
	@Override
	public synchronized void updateInfo(boolean folderContents, boolean recursively)
	{
		Logger.newSection("updating folder info " + path);
		
		id = sourceObject.path;
		name = sourceObject.name;
		path = (((parent == null) || parent.getPath().equals("/")) ? "/" : (parent.getPath() + "/")) + name;
		// size = calculateSize(); // commented because it might be heavy, so better do it explicitly.
		
		Logger.newEntry("done!");
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Folder#updateFromSource(boolean, boolean)
	 */
	@Override
	public synchronized void updateFromSource(boolean folderContents, final boolean recursively) throws OperationException
	{
		Logger.newSection("updating folder from csp " + path);
		
		if (folderContents)
		{
			buildTree(recursively);
		}
		
		try
		{
			// re-fetch the meta-data from the server.
			sourceObject = Dropbox.dropboxService.getMetadata((sourceObject == null) ? path : sourceObject.path).asFolder();
			updateInfo();
			
			try
			{
				// get link if available.
				link = new URL(Dropbox.dropboxService.createShareableUrl(sourceObject.path));
			}
			catch (MalformedURLException | DbxException e)
			{
				link = null;
			}
		}
		catch (DbxException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			throw new OperationException("Couldn't update info! " + e.getMessage());
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#copy(com.yagasoft.overcast.base.container.Folder, boolean, IOperationListener)
	 */
	@Override
	public synchronized Container<?> copy(Folder<?> destination, boolean overwrite, IOperationListener listener)
			throws OperationException
	{
		Logger.newSection("copying folder " + path);
		
		addOperationListener(listener, Operation.COPY);
		
		Container<?> existingFile = destination.searchByName(name, false);
		
		try
		{
			if ((existingFile != null) && (existingFile instanceof RemoteFile))
			{
				if (overwrite)
				{
					existingFile.delete(new IOperationListener()
					{
						
						@Override
						public void operationProgressChanged(OperationEvent event)
						{}
					});
				}
				else
				{
					throw new OperationException("Folder already exists!");
				}
			}
			
			Dropbox.dropboxService.copy(path, destination.getPath() + "/" + name);
			RemoteFolder file = Dropbox.getFactory().createFolder(sourceObject, false);
			destination.add(file);
			notifyOperationListeners(Operation.COPY, OperationState.COMPLETED, 1.0f);
			
			Logger.newEntry("done!");
			
			return file;
		}
		catch (DbxException | OperationException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			notifyOperationListeners(Operation.COPY, OperationState.FAILED, 0.0f);
			throw new OperationException("Copy of folder failed! " + e.getMessage());
		}
		finally
		{
			clearOperationListeners(Operation.COPY);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#move(com.yagasoft.overcast.base.container.Folder, boolean, IOperationListener)
	 */
	@Override
	public synchronized void move(Folder<?> destination, boolean overwrite, IOperationListener listener)
			throws OperationException
	{
		Logger.newSection("moving file " + path);
		
		addOperationListener(listener, Operation.MOVE);
		
		Container<?> existingFile = destination.searchByName(name, false);
		
		try
		{
			if ((existingFile != null) && (existingFile instanceof RemoteFile))
			{
				if (overwrite)
				{
					existingFile.delete(new IOperationListener()
					{
						
						@Override
						public void operationProgressChanged(OperationEvent event)
						{}
					});
				}
				else
				{
					throw new OperationException("Folder already exists.");
				}
			}
			
			Dropbox.dropboxService.move(path, destination.getPath() + "/" + name);
			parent.remove(this);
			destination.add(this);
			notifyOperationListeners(Operation.MOVE, OperationState.COMPLETED, 1.0f);
			
			Logger.newEntry("done!");
		}
		catch (DbxException | OperationException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			notifyOperationListeners(Operation.MOVE, OperationState.FAILED, 0.0f);
			throw new OperationException("Move of folder failed! " + e.getMessage());
		}
		finally
		{
			clearOperationListeners(Operation.MOVE);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#rename(java.lang.String, IOperationListener)
	 */
	@Override
	public synchronized void rename(String newName, IOperationListener listener) throws OperationException
	{
		Logger.newSection("renaming file " + path);
		
		addOperationListener(listener, Operation.RENAME);
		
		Container<?> existingFile = parent.searchByName(newName, false);
		
		try
		{
			if ((existingFile != null) && (existingFile instanceof RemoteFile))
			{
				throw new OperationException("Folder already exists!");
			}
			
			Dropbox.dropboxService.move(path, parent.getPath() + "/" + newName);
			notifyOperationListeners(Operation.RENAME, OperationState.COMPLETED, 1.0f);
			
			Logger.newEntry("done!");
		}
		catch (DbxException | OperationException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			notifyOperationListeners(Operation.RENAME, OperationState.FAILED, 0.0f);
			throw new OperationException("Couldn't rename folder! " + e.getMessage());
		}
		finally
		{
			clearOperationListeners(Operation.RENAME);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#delete(IOperationListener)
	 */
	@Override
	public synchronized void delete(IOperationListener listener) throws OperationException
	{
		Logger.newSection("deleting file " + path);
		
		addOperationListener(listener, Operation.DELETE);
		
		try
		{
			Dropbox.dropboxService.delete(path);
			parent.remove(this);
			notifyOperationListeners(Operation.DELETE, OperationState.COMPLETED, 1.0f);
			
			Logger.newEntry("done!");
		}
		catch (DbxException e)
		{
			Logger.newEntry("problem!");
			
			e.printStackTrace();
			notifyOperationListeners(Operation.DELETE, OperationState.FAILED, 0.0f);
			throw new OperationException("Couldn't delete folder! " + e.getMessage());
		}
		finally
		{
			clearOperationListeners(Operation.DELETE);
		}
		
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#updateInfo()
	 */
	@Override
	public synchronized void updateInfo()
	{
		updateInfo(false, false);
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.Container#updateFromSource()
	 */
	@Override
	public synchronized void updateFromSource() throws OperationException
	{
		updateFromSource(false, false);
	}
	
}
