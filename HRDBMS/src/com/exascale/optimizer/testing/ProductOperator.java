package com.exascale.optimizer.testing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.exascale.optimizer.testing.ResourceManager.DiskBackedArray;

public class ProductOperator extends JoinOperator implements Serializable
{
	protected ArrayList<Operator> children = new ArrayList<Operator>(2);
	protected Operator parent;
	protected HashMap<String, String> cols2Types;
	protected HashMap<String, Integer> cols2Pos;
	protected TreeMap<Integer, String> pos2Col;
	protected MetaData meta;
	protected volatile BufferedLinkedBlockingQueue outBuffer = new BufferedLinkedBlockingQueue(Driver.QUEUE_SIZE);
	protected volatile DiskBackedArray inBuffer;
	protected int NUM_RT_THREADS = 4 * ResourceManager.cpus;
	protected int NUM_PTHREADS = 4 * ResourceManager.cpus;
	protected AtomicLong outCount = new AtomicLong(0);
	protected volatile boolean readersDone = false;
	protected int childPos = -1; 
	protected int node;
	protected int rightChildCard = 16;
	protected boolean cardSet = false;
	
	public void reset()
	{
		System.out.println("ProductOperator cannot be reset");
		System.exit(1);
	}
	
	public boolean setRightChildCard(int card)
	{
		if (cardSet)
		{
			return false;
		}
		
		cardSet = true;
		rightChildCard = card;
		return true;
	}
	
	public void setChildPos(int pos)
	{
		childPos = pos;
	}
	
	public int getChildPos()
	{
		return childPos;
	}
	
	public ProductOperator(MetaData meta)
	{
		this.meta = meta;
	}
	
	public ProductOperator clone()
	{
		ProductOperator retval = new ProductOperator(meta);
		retval.node = node;
		retval.rightChildCard = rightChildCard;
		retval.cardSet = cardSet;
		return retval;
	}
	
	public int getNode()
	{
		return node;
	}
	
	public void setNode(int node)
	{
		this.node = node;
	}
	
	public ArrayList<String> getReferences()
	{
		ArrayList<String> retval = new ArrayList<String>(0);
		return retval;
	}
	
	public ArrayList<Operator> children()
	{
		return children;
	}
	
	public MetaData getMeta()
	{
		return meta;
	}
	
	public Operator parent()
	{
		return parent;
	}
	
	public String toString()
	{
		return "ProductOperator";
	}
	
	public void add(Operator op) throws Exception
	{
		if (children.size() < 2)
		{
			if (childPos == -1)
			{
				children.add(op);
			}
			else
			{
				children.add(childPos, op);
				childPos = -1;
			}
			op.registerParent(this);
			
			if (children.size() == 2 && children.get(0).getCols2Types() != null && children.get(1).getCols2Types() != null)
			{
				cols2Types = (HashMap<String, String>)children.get(0).getCols2Types().clone();
				cols2Pos = (HashMap<String, Integer>)children.get(0).getCols2Pos().clone();
				pos2Col = (TreeMap<Integer,String>)children.get(0).getPos2Col().clone();
				
				cols2Types.putAll(children.get(1).getCols2Types());
				for (Map.Entry entry : children.get(1).getPos2Col().entrySet())
				{
					cols2Pos.put((String)entry.getValue(), cols2Pos.size());
					pos2Col.put(pos2Col.size(), (String)entry.getValue());
				}
			}
		}
		else
		{
			throw new Exception("ProductOperator only supports 2 children");
		}
	}
	
	public void removeChild(Operator op)
	{
		childPos = children.indexOf(op);
		children.remove(op);
		op.removeParent(this);
	}
	
	public void removeParent(Operator op)
	{
		parent = null;
	}
	
	public void start() throws Exception 
	{
		inBuffer = ResourceManager.newDiskBackedArray(rightChildCard);
		
		for (Operator child : children)
		{
			child.start();
		}
		
		new InitThread().start();
		
	}
	
	protected class InitThread extends ThreadPoolThread
	{
		public void run()
		{
			ThreadPoolThread[] threads;
			int i = 0;
			threads = new ReaderThread[NUM_RT_THREADS];
			while (i < NUM_RT_THREADS)
			{
				threads[i] = new ReaderThread();
				threads[i].start();
				i++;
			}
			
			i = 0;
			ThreadPoolThread[] threads2 = new ProcessThread[NUM_PTHREADS];
			while (i < NUM_PTHREADS)
			{
				threads2[i] = new ProcessThread();
				threads2[i].start();
				i++;
			}
			
			i = 0;
			while (i < NUM_RT_THREADS)
			{
				while (true)
				{
					try 
					{
						threads[i].join();
						i++;
						break;
					} 
					catch (InterruptedException e) 
					{
					}
				}
			}
			
			readersDone = true;
			
			i = 0;
			while (i < NUM_PTHREADS)
			{
				while (true)
				{
					try 
					{
						threads2[i].join();
						i++;
						break;
					} 
					catch (InterruptedException e) 
					{
					}
				}
			}
			
			while (true)
			{
				try
				{
					outBuffer.put(new DataEndMarker());
					inBuffer.close();
					break;
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	protected class ReaderThread extends ThreadPoolThread
	{	
		public void run()
		{
			try
			{
				Operator child = children.get(1);
				Object o = child.next(ProductOperator.this);
				while (! (o instanceof DataEndMarker))
				{
					inBuffer.add((ArrayList<Object>)o);
					o = child.next(ProductOperator.this);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	protected class ProcessThread extends ThreadPoolThread
	{
		public void run()
		{
			try
			{
				Operator left = children.get(0);
				Object o = left.next(ProductOperator.this);
				//@Parallel
				while (! (o instanceof DataEndMarker))
				{
					ArrayList<Object> lRow = (ArrayList<Object>)o;
					long i = 0;
					while (true)
					{
						if (i >= inBuffer.size())
						{
							if (readersDone)
							{
								if (i >= inBuffer.size())
								{
									break;
								}
								
								continue;
							}
							else
							{
								Thread.sleep(1);
								continue;
							}
						}
						Object orow = inBuffer.get(i);
						i++;
						ArrayList<Object> rRow = (ArrayList<Object>)orow;
						
						if (orow == null)
						{
							Thread.sleep(1);
							continue;
						}
						
						ArrayList<Object> out = new ArrayList<Object>(lRow.size() + rRow.size());
						out.addAll(lRow);
						out.addAll(rRow);
						outBuffer.put(out);
						long count = outCount.incrementAndGet();
						//if (count % 100000 == 0)
						//{
						//	System.out.println("ProductOperator has output " + count + " rows");
						//}
					}
					
					o = left.next(ProductOperator.this);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	public void nextAll(Operator op) throws Exception
	{
		children.get(0).nextAll(op);
		children.get(1).nextAll(op);
		Object o = next(op);
		while (!(o instanceof DataEndMarker))
		{
			o = next(op);
		}
	}
	
	public Object next(Operator op) throws Exception
	{
		Object o;
		o = outBuffer.take();
		
		if (o instanceof DataEndMarker)
		{
			o = outBuffer.peek();
			if (o == null)
			{
				outBuffer.put(new DataEndMarker());
				return new DataEndMarker();
			}
			else
			{
				outBuffer.put(new DataEndMarker());
				return o;
			}
		}
		return o;
	}
	
	public void close() throws Exception 
	{
		for (Operator child : children)
		{
			child.close();
		}
		
		inBuffer.close();
	}

	public void registerParent(Operator op) throws Exception
	{
		if (parent == null)
		{
			parent = op;
		}
		else
		{
			throw new Exception("ProductOperator can only have 1 parent.");
		}
	}

	@Override
	public HashMap<String, String> getCols2Types() {
		return cols2Types;
	}

	@Override
	public HashMap<String, Integer> getCols2Pos() {
		return cols2Pos;
	}

	@Override
	public TreeMap<Integer, String> getPos2Col() {
		return pos2Col;
	}

	@Override
	public void addJoinCondition(ArrayList<Filter> filters) {
		throw new UnsupportedOperationException("ProductOperator does not support addJoinCondition");
		
	}

	@Override
	public void addJoinCondition(String left, String right) {
		throw new UnsupportedOperationException("ProductOperator does not support addJoinCondition");
		
	}

	@Override
	public HashSet<HashMap<Filter, Filter>> getHSHMFilter() {
		return null;
	}

	@Override
	public ArrayList<String> getJoinForChild(Operator op) 
	{
		return null;
	}
}