package com.exascale.optimizer;

import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;

import com.exascale.misc.DataEndMarker;
import com.exascale.tables.Plan;
import com.exascale.tables.Transaction;

public final class DropIndexOperator implements Operator, Serializable
{
	private final MetaData meta;
	private Map<String, String> cols2Types;
	private Map<String, Integer> cols2Pos;
	private Map<Integer, String> pos2Col;
	private Operator parent;
	private int node;
	private final String schema;
	private final String table;
	private boolean done = false;
	private Transaction tx;

	public DropIndexOperator(final String schema, final String table, final MetaData meta)
	{
		this.meta = meta;
		this.schema = schema;
		this.table = table;
	}

	@Override
	public void add(final Operator op) throws Exception
	{
		throw new Exception("DropIndexOperator does not support children");
	}

	@Override
	public List<Operator> children()
	{
		final List<Operator> retval = new ArrayList<Operator>(1);
		return retval;
	}

	@Override
	public DropIndexOperator clone()
	{
		final DropIndexOperator retval = new DropIndexOperator(schema, table, meta);
		retval.node = node;
		return retval;
	}

	@Override
	public void close() throws Exception
	{
		cols2Pos = null;
		cols2Types = null;
		pos2Col = null;
	}

	@Override
	public int getChildPos()
	{
		return 0;
	}

	@Override
	public Map<String, Integer> getCols2Pos()
	{
		return cols2Pos;
	}

	@Override
	public Map<String, String> getCols2Types()
	{
		return cols2Types;
	}

	@Override
	public MetaData getMeta()
	{
		return meta;
	}

	@Override
	public int getNode()
	{
		return node;
	}

	@Override
	public Map<Integer, String> getPos2Col()
	{
		return pos2Col;
	}

	@Override
	public List<String> getReferences()
	{
		final List<String> retval = new ArrayList<String>();
		return retval;
	}

	@Override
	// @?Parallel
	public Object next(final Operator op) throws Exception
	{
		if (!done)
		{
			done = true;
			MetaData.dropIndex(schema, table, tx);
			return 1;
		}
		else
		{
			return new DataEndMarker();
		}
	}

	@Override
	public void nextAll(final Operator op) throws Exception
	{
	}

	@Override
	public long numRecsReceived()
	{
		return 0;
	}

	@Override
	public Operator parent()
	{
		return parent;
	}

	@Override
	public boolean receivedDEM()
	{
		return false;
	}

	@Override
	public void registerParent(final Operator op) throws Exception
	{
		throw new Exception("DropIndexOperator does not support parents.");
	}

	@Override
	public void removeChild(final Operator op)
	{
	}

	@Override
	public void removeParent(final Operator op)
	{
	}

	@Override
	public void reset() throws Exception
	{
		throw new Exception("DropIndexOperator is not resetable");
	}

	@Override
	public void serialize(final OutputStream out, final IdentityHashMap<Object, Long> prev) throws Exception
	{
		throw new Exception("Trying to call serialize on drop index operator");
	}

	@Override
	public void setChildPos(final int pos)
	{
	}

	@Override
	public void setNode(final int node)
	{
		this.node = node;
	}

	@Override
	public void setPlan(final Plan plan)
	{
	}

	public void setTransaction(final Transaction tx)
	{
		this.tx = tx;
	}

	@Override
	public void start() throws Exception
	{
	}

	@Override
	public String toString()
	{
		return "DropIndexOperator";
	}

}
