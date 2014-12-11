package com.exascale.tasks;

import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import com.exascale.compression.CompressedSocket;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.managers.MaintenanceManager;
import com.exascale.managers.PlanCacheManager;
import com.exascale.optimizer.MetaData;
import com.exascale.tables.Transaction;
import com.exascale.threads.HRDBMSThread;

public class DeleteFilesTask extends Task
{
	public void run()
	{
		new DeleteFilesThread().start();
	}
	
	private class DeleteFilesThread extends HRDBMSThread
	{
		public void run()
		{
			try
			{
				long start = System.currentTimeMillis();
				ArrayList<String> tables = new ArrayList<String>();
				ArrayList<String> indexes = new ArrayList<String>();
				long target = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("old_file_cleanup_target_days")) * 24 * 60 * 60 * 1000;
				String sql = "SELECT SCHEMA, TABNAME FROM SYS.TABLES";
				Connection conn = DriverManager.getConnection("jdbc:hrdbms://localhost:" + HRDBMSWorker.getHParms().getProperty("port_number"));
				conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
				conn.setAutoCommit(false);
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next())
				{
					String table = rs.getString(1) + "." + rs.getString(2);
					tables.add(table);
				}
			
				sendDeleteTables(tables);
				rs.close();
				conn.commit();
			
				sql = "SELECT A.SCHEMA, B.INDEXNAME FROM SYS.TABLES A, SYS.INDEXES B WHERE A.TABLEID = B.TABLEID";
				rs = stmt.executeQuery(sql);
				while (rs.next())
				{
					String index = rs.getString(1) + "." + rs.getString(2);
					indexes.add(index);
				}
			
				sendDeleteIndexes(indexes);
				rs.close();
				conn.commit();
				conn.close();
			
				long end = System.currentTimeMillis();
				long elapsed = end - start;
				MaintenanceManager.schedule(DeleteFilesTask.this, -1, elapsed, end + target);
			}
			catch(Exception e)
			{
				HRDBMSWorker.logger.warn("DeleteFilesTask failed", e);
				long target = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("old_file_cleanup_target_days")) * 24 * 60 * 60 * 1000;
				MaintenanceManager.schedule(DeleteFilesTask.this, System.currentTimeMillis() + target);
			}
		}
		
		private void sendDeleteTables(ArrayList<String> tables) throws Exception
		{
			Transaction tx = new Transaction(Transaction.ISOLATION_CS);
			ArrayList<Object> rows = PlanCacheManager.getWorkerNodes().setParms().execute(tx);
			ArrayList<Integer> nodes = new ArrayList<Integer>();
			for (Object o : rows)
			{
				if (o instanceof ArrayList)
				{
					nodes.add((Integer)((ArrayList)o).get(0));
				}
			}
			
			ArrayList<Object> tree = makeTree(nodes);
			
			boolean allOK = true;
			ArrayList<SendTableThread> threads = new ArrayList<SendTableThread>();
			for (Object o : tree)
			{
				if (o instanceof Integer)
				{
					ArrayList<Object> list = new ArrayList<Object>(1);
					list.add(o);
					SendTableThread thread = new SendTableThread(list, tx, tables);
					threads.add(thread);
				}
				else
				{
					SendTableThread thread = new SendTableThread((ArrayList<Object>)o, tx, tables);
					threads.add(thread);
				}
			}
			
			for (SendTableThread thread : threads)
			{
				thread.start();
			}
			
			for (SendTableThread thread : threads)
			{
				while (true)
				{
					try
					{
						thread.join();
						break;
					}
					catch(InterruptedException e)
					{}
				}
				boolean ok = thread.getOK();
				if (!ok)
				{
					allOK = false;
				}
			}
			
			tx.commit();
			
			if (!allOK)
			{
				throw new Exception();
			}
		}
		
		private void sendDeleteIndexes(ArrayList<String> indexes) throws Exception
		{
			Transaction tx = new Transaction(Transaction.ISOLATION_CS);
			ArrayList<Object> rows = PlanCacheManager.getWorkerNodes().setParms().execute(tx);
			ArrayList<Integer> nodes = new ArrayList<Integer>();
			for (Object o : rows)
			{
				if (o instanceof ArrayList)
				{
					nodes.add((Integer)((ArrayList)o).get(0));
				}
			}
			
			ArrayList<Object> tree = makeTree(nodes);
			
			boolean allOK = true;
			ArrayList<SendIndexThread> threads = new ArrayList<SendIndexThread>();
			for (Object o : tree)
			{
				if (o instanceof Integer)
				{
					ArrayList<Object> list = new ArrayList<Object>(1);
					list.add(o);
					SendIndexThread thread = new SendIndexThread(list, tx, indexes);
					threads.add(thread);
				}
				else
				{
					SendIndexThread thread = new SendIndexThread((ArrayList<Object>)o, tx, indexes);
					threads.add(thread);
				}
			}
			
			for (SendIndexThread thread : threads)
			{
				thread.start();
			}
			
			for (SendIndexThread thread : threads)
			{
				while (true)
				{
					try
					{
						thread.join();
						break;
					}
					catch(InterruptedException e)
					{}
				}
				boolean ok = thread.getOK();
				if (!ok)
				{
					allOK = false;
				}
			}
			
			tx.commit();
			
			if (!allOK)
			{
				throw new Exception();
			}
		}
		
		private ArrayList<Object> makeTree(ArrayList<Integer> nodes)
		{
			int max = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("max_neighbor_nodes"));
			if (nodes.size() <= max)
			{
				ArrayList<Object> retval = new ArrayList<Object>(nodes);
				return retval;
			}
			
			ArrayList<Object> retval = new ArrayList<Object>();
			int i = 0;
			while (i < max)
			{
				retval.add(nodes.get(i));
				i++;
			}
			
			int remaining = nodes.size() - i;
			int perNode = remaining / max + 1;
			
			int j = 0;
			while (i < nodes.size())
			{
				int first = (Integer)retval.get(j);
				retval.remove(j);
				ArrayList<Integer> list = new ArrayList<Integer>(perNode+1);
				list.add(first);
				int k = 0;
				while (k < perNode && i < nodes.size())
				{
					list.add(nodes.get(i));
					i++;
					k++;
				}
				
				retval.add(j, list);
				j++;
			}
			
			if (((ArrayList<Integer>)retval.get(0)).size() <= max)
			{
				return retval;
			}
			
			//more than 2 tier
			i = 0;
			while (i < retval.size())
			{
				ArrayList<Integer> list = (ArrayList<Integer>)retval.remove(i);
				retval.add(i, makeTree(list));
				i++;
			}
			
			return retval;
		}
	}
	
	private class SendTableThread extends HRDBMSThread
	{
		private ArrayList<Object> tree;
		private Transaction tx;
		private ArrayList<String> tables;
		private boolean ok = true;
		
		public SendTableThread(ArrayList<Object> tree, Transaction tx, ArrayList<String> tables)
		{
			this.tree = tree;
			this.tx = tx;
			this.tables = tables;
		}
		
		public void run()
		{
			Object obj = tree.get(0);
			while (obj instanceof ArrayList)
			{
				obj = ((ArrayList)obj).get(0);
			}
			
			Socket sock = null;
			try
			{
				String hostname = new MetaData().getHostNameForNode((Integer)obj, tx);
				sock = CompressedSocket.newCompressedSocket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
				OutputStream out = sock.getOutputStream();
				byte[] outMsg = "DELFITBL        ".getBytes("UTF-8");
				outMsg[8] = 0;
				outMsg[9] = 0;
				outMsg[10] = 0;
				outMsg[11] = 0;
				outMsg[12] = 0;
				outMsg[13] = 0;
				outMsg[14] = 0;
				outMsg[15] = 0;
				out.write(outMsg);
				ObjectOutputStream objOut = new ObjectOutputStream(out);
				objOut.writeObject(convertToHosts(tree, tx));
				objOut.writeObject(tables);
				objOut.flush();
				out.flush();
				getConfirmation(sock);
				objOut.close();
				out.close();
				sock.close();
			}
			catch(Exception e)
			{
				try
				{
					sock.close();
				}
				catch(Exception f)
				{}
				ok = false;
			}
		}
		
		public boolean getOK()
		{
			return ok;
		}
	}
	
	private class SendIndexThread extends HRDBMSThread
	{
		private ArrayList<Object> tree;
		private Transaction tx;
		private ArrayList<String> indexes;
		private boolean ok = true;
		
		public SendIndexThread(ArrayList<Object> tree, Transaction tx, ArrayList<String> indexes)
		{
			this.tree = tree;
			this.tx = tx;
			this.indexes = indexes;
		}
		
		public void run()
		{
			Object obj = tree.get(0);
			while (obj instanceof ArrayList)
			{
				obj = ((ArrayList)obj).get(0);
			}
			
			Socket sock = null;
			try
			{
				String hostname = new MetaData().getHostNameForNode((Integer)obj, tx);
				sock = CompressedSocket.newCompressedSocket(hostname, Integer.parseInt(HRDBMSWorker.getHParms().getProperty("port_number")));
				OutputStream out = sock.getOutputStream();
				byte[] outMsg = "DELFIIDX        ".getBytes("UTF-8");
				outMsg[8] = 0;
				outMsg[9] = 0;
				outMsg[10] = 0;
				outMsg[11] = 0;
				outMsg[12] = 0;
				outMsg[13] = 0;
				outMsg[14] = 0;
				outMsg[15] = 0;
				out.write(outMsg);
				ObjectOutputStream objOut = new ObjectOutputStream(out);
				objOut.writeObject(convertToHosts(tree, tx));
				objOut.writeObject(indexes);
				objOut.flush();
				out.flush();
				getConfirmation(sock);
				objOut.close();
				out.close();
				sock.close();
			}
			catch(Exception e)
			{
				try
				{
					sock.close();
				}
				catch(Exception f)
				{}
				ok = false;
			}
		}
		
		public boolean getOK()
		{
			return ok;
		}
	}
	
	private static ArrayList<Object> convertToHosts(ArrayList<Object> tree, Transaction tx) throws Exception
	{
		ArrayList<Object> retval = new ArrayList<Object>();
		int i = 0;
		while (i < tree.size())
		{
			Object obj = tree.get(i);
			if (obj instanceof Integer)
			{
				retval.add(new MetaData().getHostNameForNode((Integer)obj, tx));
			}
			else
			{
				retval.add(convertToHosts((ArrayList<Object>)obj, tx));
			}
			
			i++;
		}
		
		return retval;
	}
	
	private static void getConfirmation(Socket sock) throws Exception
	{
		InputStream in = sock.getInputStream();
		byte[] inMsg = new byte[2];
		
		int count = 0;
		while (count < 2)
		{
			try
			{
				int temp = in.read(inMsg, count, 2 - count);
				if (temp == -1)
				{
					in.close();
					throw new Exception();
				}
				else
				{
					count += temp;
				}
			}
			catch (final Exception e)
			{
				in.close();
				throw new Exception();
			}
		}
		
		String inStr = new String(inMsg, "UTF-8");
		if (!inStr.equals("OK"))
		{
			in.close();
			throw new Exception();
		}
		
		try
		{
			in.close();
		}
		catch(Exception e)
		{}
	}
}