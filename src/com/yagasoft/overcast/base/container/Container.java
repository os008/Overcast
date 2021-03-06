/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			Licence terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.base.container/Container.java
 *
 *			Modified: 28-Jun-2014 (21:46:59)
 *			   Using: Eclipse J-EE / JDK 8 / Windows 8.1 x64
 */

package com.yagasoft.overcast.base.container;


import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.operation.IOperable;
import com.yagasoft.overcast.base.container.operation.IOperationListener;
import com.yagasoft.overcast.base.container.operation.Operation;
import com.yagasoft.overcast.base.container.operation.OperationEvent;
import com.yagasoft.overcast.base.container.operation.OperationState;
import com.yagasoft.overcast.base.csp.CSP;
import com.yagasoft.overcast.exception.AccessException;
import com.yagasoft.overcast.exception.OperationException;


/**
 * A class representing the common attributes and operation of the files and folders.<br />
 * All methods in this class (or subclasses) must be synchronised in the implementation to prevent conflicting operations.
 *
 * @param <T>
 *            the type of the file or folder in the original API of the CSP.
 */
public abstract class Container<T> implements IOperable, Comparable<Container<T>>
{
	
	/** Unique identifier for the container -- implementation specific. */
	protected String										id;
	
	/** Name of the container. */
	protected String										name;
	
	/** Path of the container at the source, including its name. */
	protected String										path;
	
	/** Path prefix, which will be used to clean-up the path sent by the CSP -- for API path standardisation. */
	protected String										pathPrefix;
	
	/** Size of the container in bytes. */
	protected long											size;
	
	/** Modified date in ms since 1970. */
	protected long											date;
	
	/** Container object created by the original API of the CSP. */
	protected T												sourceObject;
	
	/** Parent folder containing this container. */
	protected Folder<?>										parent;
	
	/** Listeners to the operations in this container. */
	protected Map<IOperationListener, HashSet<Operation>>	operationListeners		= new HashMap<IOperationListener, HashSet<Operation>>();
	
	/** Temporary listeners to the operations in this container; they're added through the operation methods themselves. */
	protected Map<IOperationListener, HashSet<Operation>>	tempOperationListeners	= new HashMap<IOperationListener, HashSet<Operation>>();
	
	/** CSP object related to this container, or where the container is stored at. */
	protected CSP<T, ?, ?>									csp;
	
	/**
	 * Generate unique ID for this container.
	 */
	public abstract void generateId();
	
	/**
	 * Checks if the file exists physically or not.
	 *
	 * @return true, if it exists
	 * @throws AccessException
	 *             Can't access the container to determine its existence.
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract boolean isExist() throws AccessException, OperationException;
	
	/**
	 * Is this a folder?.
	 *
	 * @return true, if it is a folder
	 */
	public abstract boolean isFolder();
	
	/**
	 * Is this a local container (on this host)?.
	 *
	 * @return true, if it is a local container
	 */
	public abstract boolean isLocal();
	
	/**
	 * Update the fields (class attributes) in this file object from the in-memory info (nothing is done outside the program). <br />
	 * Set the path, at the end of the method implementation, in this preferred format to make it standardised across the API:
	 *
	 * <pre>
	 * path = ((parent == null || parent.getPath().equals(&quot;/&quot;)) ? &quot;/&quot; : (parent.getPath() + &quot;/&quot;)) + name;
	 * cleanPath();		// remove the prefix.
	 * </pre>
	 */
	public abstract void updateInfo();
	
	/**
	 * Update from where the container resides. It reads the meta of the container.<br />
	 * For folders, it reads the children list. It might go online to do both.
	 *
	 * @throws OperationException
	 *             the operation exception
	 */
	public abstract void updateFromSource() throws OperationException;
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Operations.
	// ======================================================================================
	
	/**
	 * Initialises a basic operation. Includes checking on container existence in destination, and adding operation listener to
	 * list.
	 *
	 * @param destination
	 *            Destination folder.
	 * @param overwrite
	 *            Overwrite?
	 * @param operation
	 *            Operation to perform (enum).
	 * @param newName
	 *            New name if applicable.
	 * @param listeners
	 *            Listeners.
	 * @throws OperationException
	 *             the operation exception
	 */
	protected void initOperation(Folder<?> destination, boolean overwrite, Operation operation
			, String newName, IOperationListener... listeners)
			throws OperationException
	{
		Logger.info(csp.getName() + ": CONTAINER: " + operation + " on " + path);
		
		addTempOperationListeners(operation, listeners);
		
		String name = this.name;
		
		if (operation == Operation.RENAME)
		{
			name = newName;
		}
		
		// if it's not a delete, then check for existence at destination
		if (operation != Operation.DELETE)
		{
			List<Container<?>> existingContainer = destination.searchByName(name, false, false);
			
			if ( !existingContainer.isEmpty() && (existingContainer.get(0).isFolder() == isFolder()))
			{
				if (overwrite)
				{
					existingContainer.get(0).delete();
				}
				else
				{
					Logger.error(csp.getName() + ": CONTAINER: " + "already exists: " + path);
					throw new OperationException("Already exists!");
				}
			}
		}
	}
	
	/**
	 * Post operation stuff. Includes removing this container from the old parent upon move or delete,
	 * adding the copied or moved container to the new parent, and notifying listeners of success.
	 *
	 * @param destination
	 *            Destination folder.
	 * @param affectedContainer
	 *            Affected container. When copying, it's the new container.
	 * @param operation
	 *            Operation to perform.
	 * @throws OperationException
	 *             the operation exception
	 */
	protected void postOperation(Folder<?> destination, Container<?> affectedContainer, Operation operation)
			throws OperationException
	{
		// move or delete removes container from this container
		if ((operation == Operation.MOVE) || (operation == Operation.DELETE))
		{
			getParent().remove(this);
		}
		
		// copy or move adds a container to the destination
		if ((operation == Operation.COPY) || (operation == Operation.MOVE))
		{
			destination.add(affectedContainer);
		}
		
		notifyOperationListeners(operation, OperationState.COMPLETED, 1.0f);
		
		Logger.info(csp.getName() + ": CONTAINER: " + " finished " + operation + ": " + affectedContainer.getPath());
	}
	
	/**
	 * Stuff to do when an operation fails. Includes logging, and throwing an exception.
	 *
	 * @param operation
	 *            Operation that failed.
	 * @param e
	 *            Exception thrown at source.
	 * @throws OperationException
	 *             the operation exception
	 */
	protected void operationFailed(Operation operation, Exception e) throws OperationException
	{
		Logger.error(csp.getName() + ": CONTAINER: " + "moving file: " + path);
		Logger.except(e);
		e.printStackTrace();
		
		throw new OperationException(operation + " failed! "
				+ ((e != null) ? e.getMessage() : ""));
	}
	
	/**
	 * Copy this container to the destination folder.
	 *
	 * @param destination
	 *            Destination folder.
	 * @param overwrite
	 *            Overwrite existing container at the destination.
	 * @param listeners
	 *            Listeners.
	 * @return Container object at the destination.
	 * @throws OperationException
	 *             the operation exception
	 */
	public synchronized Container<?> copy(Folder<?> destination, boolean overwrite, IOperationListener... listeners)
			throws OperationException
	{
		try
		{
			initOperation(destination, overwrite, Operation.COPY, null, listeners);
			Container<?> copiedContainer = copyProcess(destination);
			postOperation(destination, copiedContainer, Operation.COPY);
			
			return copiedContainer;
		}
		catch (OperationException e)
		{
			operationFailed(Operation.COPY, e);
		}
		finally
		{
			removeTempOperationListeners(Operation.COPY, listeners);
		}
		
		return null;
	}
	
	/**
	 * Copy process logic. This includes how the copy process is performed. It should return a {@link Container} representing the
	 * new container copied over.
	 *
	 * @param destination
	 *            Destination folder.
	 * @return new container at destination
	 * @throws OperationException
	 *             the operation exception
	 */
	protected abstract Container<?> copyProcess(Folder<?> destination) throws OperationException;
	
	/**
	 * Move this container to the destination folder.
	 *
	 * @param destination
	 *            Destination folder.
	 * @param overwrite
	 *            Overwrite existing container at the destination.
	 * @param listeners
	 *            Listeners.
	 * @throws OperationException
	 *             the operation exception
	 */
	public synchronized void move(Folder<?> destination, boolean overwrite, IOperationListener... listeners)
			throws OperationException
	{
		try
		{
			initOperation(destination, overwrite, Operation.MOVE, null, listeners);
			setSourceObject(moveProcess(destination));
			postOperation(destination, this, Operation.MOVE);
		}
		catch (OperationException e)
		{
			operationFailed(Operation.MOVE, e);
		}
		finally
		{
			removeTempOperationListeners(Operation.MOVE, listeners);
		}
	}
	
	/**
	 * Move process logic. This includes how the move process is performed. It should return an
	 * object representing the new {@link #sourceObject} returned by the server.
	 *
	 * @param destination
	 *            Destination folder.
	 * @return source object returned by the server
	 * @throws OperationException
	 *             the operation exception
	 */
	protected abstract T moveProcess(Folder<?> destination) throws OperationException;
	
	/**
	 * Rename this container.
	 *
	 * @param newName
	 *            The new name.
	 * @param listeners
	 *            Listeners.
	 * @throws OperationException
	 *             the operation exception
	 */
	public synchronized void rename(String newName, IOperationListener... listeners) throws OperationException
	{
		try
		{
			initOperation(getParent(), false, Operation.RENAME, newName, listeners);
			setSourceObject(renameProcess(newName));
			postOperation(getParent(), this, Operation.RENAME);
		}
		catch (OperationException e)
		{
			operationFailed(Operation.RENAME, e);
		}
		finally
		{
			removeTempOperationListeners(Operation.RENAME, listeners);
		}
	}
	
	/**
	 * Rename process logic. This includes how the rename process is performed. It should return an
	 * object representing the new {@link #sourceObject} returned by the server.
	 *
	 * @param newName
	 *            New name of the container.
	 * @return source object returned by the server
	 * @throws OperationException
	 *             the operation exception
	 */
	protected abstract T renameProcess(String newName) throws OperationException;
	
	/**
	 * Delete this container.
	 *
	 * @param listeners
	 *            Listeners.
	 * @throws OperationException
	 *             the operation exception
	 */
	public synchronized void delete(IOperationListener... listeners) throws OperationException
	{
		try
		{
			initOperation(getParent(), false, Operation.DELETE, null, listeners);
			deleteProcess();
			postOperation(getParent(), this, Operation.DELETE);
		}
		catch (OperationException e)
		{
			operationFailed(Operation.DELETE, e);
		}
		finally
		{
			removeTempOperationListeners(Operation.DELETE, listeners);
		}
	}
	
	/**
	 * Delete process logic.
	 *
	 * @throws OperationException
	 *             the operation exception
	 */
	protected abstract void deleteProcess() throws OperationException;
	
	// ======================================================================================
	// #endregion Operations.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Listeners.
	// ======================================================================================
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#addOperationListener(com.yagasoft.overcast.base.container.operation.IOperationListener,
	 *      Operation)
	 */
	@Override
	public void addOperationListener(IOperationListener listener, Operation operation)
	{
		// create a new operation listener set if not already present
		operationListeners.putIfAbsent(listener, new HashSet<Operation>());
		
		// add the operation to the set associated with the key.
		operationListeners.get(listener).add(operation);
		
		// remove listener from temp list as it will now monitor this for a while
		if (tempOperationListeners.containsKey(listener)
				&& tempOperationListeners.get(listener).contains(operation))
		{
			removeTempOperationListeners(operation, listener);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#addTempOperationListeners(com.yagasoft.overcast.base.container.operation.Operation,
	 *      com.yagasoft.overcast.base.container.operation.IOperationListener[])
	 */
	@Override
	public void addTempOperationListeners(Operation operation, IOperationListener... listeners)
	{
		// if it's already monitoring in the more permanent list, then don't count it
		Arrays.stream(listeners)
				.filter(listener -> !(operationListeners.containsKey(listener)
						&& operationListeners.get(listener).contains(operation)))
				.forEach(listener ->
				{
					// create a new set for this key if the key is not present.
						tempOperationListeners.putIfAbsent(listener, new HashSet<Operation>());
						
						// add the operation to the set associated with the key.
						tempOperationListeners.get(listener).add(operation);
					});
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#removeOperationListener(com.yagasoft.overcast.base.container.operation.IOperationListener)
	 */
	@Override
	public void removeOperationListener(IOperationListener listener)
	{
		operationListeners.remove(listener);
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#removeOperationListener(com.yagasoft.overcast.base.container.operation.IOperationListener,
	 *      com.yagasoft.overcast.base.container.operation.Operation)
	 */
	@Override
	public void removeOperationListener(IOperationListener listener, Operation operation)
	{
		operationListeners.get(listener).remove(operation);
		
		// if the operations set is empty, then remove the listener.
		if (operationListeners.get(listener).isEmpty())
		{
			removeOperationListener(listener);
		}
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#removeTempOperationListeners(com.yagasoft.overcast.base.container.operation.IOperationListener[])
	 */
	@Override
	public void removeTempOperationListeners(IOperationListener... listeners)
	{
		Arrays.stream(listeners).forEach(tempOperationListeners::remove);
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#removeTempOperationListeners(com.yagasoft.overcast.base.container.operation.Operation,
	 *      com.yagasoft.overcast.base.container.operation.IOperationListener[])
	 */
	@Override
	public void removeTempOperationListeners(Operation operation, IOperationListener... listeners)
	{
		// remove operation from listener.
		Arrays.stream(listeners)
				.forEach(listener -> tempOperationListeners.get(listener).remove(operation));
		
		// if the operations set is empty, then remove the listener.
		removeTempOperationListeners(Arrays.stream(listeners)
				.filter(listener -> tempOperationListeners.get(listener).isEmpty())
				.toArray(IOperationListener[]::new));
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#notifyOperationListeners(com.yagasoft.overcast.base.container.operation.Operation,
	 *      com.yagasoft.overcast.base.container.operation.OperationState, float, com.yagasoft.overcast.base.container.Container)
	 */
	@Override
	public void notifyOperationListeners(Operation operation, OperationState state, float progress, Container<?> object)
	{
		// go through the listeners' list and notify whoever is concerned with this operation.
		operationListeners.keySet().parallelStream()
				.filter(listener -> operationListeners.get(listener).contains(operation))
				.forEach(listener -> listener.operationChange(new OperationEvent(this, operation, state, progress, object)));
		
		// go through the temp listeners' list and notify whoever is concerned with this operation.
		tempOperationListeners.keySet().parallelStream()
				.filter(listener ->
						tempOperationListeners.get(listener).contains(operation)
								&& !(operationListeners.containsKey(listener)		// make sure not to notify twice.
								&& operationListeners.get(listener).contains(operation)))
				.forEach(listener -> listener.operationChange(new OperationEvent(this, operation, state, progress, object)));
	}
	
	/**
	 * @see com.yagasoft.overcast.base.container.operation.IOperable#clearOperationListeners(com.yagasoft.overcast.base.container.operation.Operation)
	 */
	@Override
	public void clearOperationListeners(Operation operation)
	{
		operationListeners.keySet().stream()
				.forEach(listener -> removeOperationListener(listener, operation));
	}
	
	/**
	 * Remove all types of listeners that was added to this container before.<br />
	 * Override this in sub-classes if more types of listeners were added.
	 */
	public void clearAllListeners()
	{
		operationListeners.clear();
	}
	
	// ======================================================================================
	// #endregion Listeners.
	// //////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Removes the prefix from the path to make it standardised with this API's paths.<br />
	 * It should be added before communicating with the service using its path format.
	 */
	protected void cleanPath()
	{
		if (((path != null) && (path != "/")) && path.startsWith(pathPrefix))
		{
			if (path.length() <= pathPrefix.length())
			{
				path = "/";
			}
			else
			{
				path = path.replaceFirst(pathPrefix, "");
			}
		}
	}
	
	/**
	 * Checks if the object passed is identical to this one. It checks if it's a container in the first place, and if so, checks
	 * the ID, and as it's unique, there won't be conflicts.
	 *
	 * @param object
	 *            Object to compare.
	 * @return true, if they're identical
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object object)
	{
		return ((object instanceof Container) && (((Container<?>) object).id.equalsIgnoreCase(getId())));
	}
	
	/**
	 * Compares names, used for sorting.
	 *
	 * @param container
	 *            the container
	 * @return -1 if less than alphabetically, 0 if identical, 1 if higher.
	 * @see java.lang.Comparable#compareTo(Object)
	 */
	@Override
	public int compareTo(Container<T> container)
	{
		return path.compareToIgnoreCase(container.path);
	}
	
	/**
	 * Gets the name comparator.
	 *
	 * @return the name comparator
	 */
	public static Comparator<Container<?>> getNameComparator()
	{
		return ((file1, file2) -> file1.getName().compareToIgnoreCase(file2.getName()));
	}
	
	/**
	 * Gets the path comparator.
	 *
	 * @return the path comparator
	 */
	public static Comparator<Container<?>> getPathComparator()
	{
		return ((file1, file2) -> file1.getPath().compareToIgnoreCase(file2.getPath()));
	}
	
	/**
	 * Gets the size comparator.
	 *
	 * @return the size comparator
	 */
	public static Comparator<Container<?>> getSizeComparator()
	{
		return ((file1, file2) -> new Long(file1.getSize()).compareTo(file2.getSize()));
	}
	
	/**
	 * Returns the name of the container.
	 *
	 * @return the name
	 * @see java.lang.Object#toString()
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
	 * Gets the id.
	 *
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}
	
	/**
	 * Sets the id.
	 *
	 * @param id
	 *            the id to set
	 */
	public void setId(String id)
	{
		this.id = id;
	}
	
	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return this.name;
	}
	
	/**
	 * Sets the name.
	 *
	 * @param value
	 *            the new name
	 */
	public void setName(String value)
	{
		this.name = value;
		updateInfo();		// the path is affected.
	}
	
	/**
	 * Gets the path.
	 *
	 * @return the path
	 */
	public String getPath()
	{
		return this.path;
	}
	
	/**
	 * Sets the path. I advise against using this manually.
	 *
	 * @param value
	 *            the new path
	 */
	public void setPath(String value)
	{
		this.path = value;
		cleanPath();
	}
	
	/**
	 * Gets the path prefix.
	 *
	 * @return the pathPrefix
	 */
	public String getPathPrefix()
	{
		return pathPrefix;
	}
	
	/**
	 * Sets the path prefix.
	 *
	 * @param pathPrefix
	 *            the pathPrefix to set
	 */
	public void setPathPrefix(String pathPrefix)
	{
		this.pathPrefix = pathPrefix;
		cleanPath();
	}
	
	/**
	 * Gets the size.
	 *
	 * @return the size
	 */
	public long getSize()
	{
		return size;
	}
	
	/**
	 * Sets the size.
	 *
	 * @param size
	 *            the size to set
	 */
	public void setSize(long size)
	{
		this.size = size;
	}
	
	/**
	 * Gets the date.
	 *
	 * @return the date
	 */
	public long getDate()
	{
		return date;
	}
	
	/**
	 * Sets the date.
	 *
	 * @param date
	 *            the date to set
	 */
	public void setDate(long date)
	{
		this.date = date;
	}
	
	/**
	 * Gets the source object.
	 *
	 * @return the sourceObject
	 */
	public T getSourceObject()
	{
		return sourceObject;
	}
	
	/**
	 * Sets the source object, and updates the info.
	 *
	 * @param sourceObject
	 *            the sourceObject to set
	 */
	public void setSourceObject(T sourceObject)
	{
		this.sourceObject = sourceObject;
		updateInfo();		// all info are affected. (fields)
	}
	
	/**
	 * Gets the parent.
	 *
	 * @return the parent
	 */
	public Folder<?> getParent()
	{
		return parent;
	}
	
	/**
	 * Sets the parent.
	 *
	 * @param parent
	 *            the parent to set
	 */
	public void setParent(Folder<?> parent)
	{
		this.parent = parent;
		updateInfo();
	}
	
	/**
	 * Gets the csp.
	 *
	 * @return the csp
	 */
	public CSP<T, ?, ?> getCsp()
	{
		return csp;
	}
	
	/**
	 * Sets the csp.
	 *
	 * @param csp
	 *            the csp to set
	 */
	public void setCsp(CSP<T, ?, ?> csp)
	{
		this.csp = csp;
	}
	
	// ======================================================================================
	// #endregion Getters and setters.
	// //////////////////////////////////////////////////////////////////////////////////////
	
}
