package com.exascale;

public class RIDChange 
{
	private RID oldRID;
	private RID newRID;
	
	public RIDChange(RID oldRID, RID newRID)
	{
		this.oldRID = oldRID;
		this.newRID = newRID;
	}
	
	public RID getOld()
	{
		return oldRID;
	}
	
	public RID getNew()
	{
		return newRID;
	}
}
