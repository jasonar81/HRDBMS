package com.exascale.optimizer.externalTable;

import com.exascale.misc.MyDate;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class HTTPCsvExternal implements ExternalTableType
{
	protected HashMap<String, String> cols2Types;
	protected HashMap<String, Integer> cols2Pos;
	protected TreeMap<Integer, String> pos2Col;
	protected String name;
	protected String schema;

	/** Reader to read csv file */
	private BufferedReader input;

	/** Line in CSV file */
	private int line = 0;

	/** Parameters defined in SYS.EXTERNALTABLES */
	private CsvExternalParams params;

    public Class getParamsClass() throws NoSuchFieldException
    {
        return this.getClass().getDeclaredField("params").getType();
    }

	public void setCols2Types(HashMap<String, String> cols2Types) {
		this.cols2Types = cols2Types;
	}
	public void setCols2Pos(HashMap<String, Integer> cols2Pos) {
		this.cols2Pos = cols2Pos;
	}
	public void setPos2Col(TreeMap<Integer, String> pos2Col) {
		this.pos2Col = pos2Col;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setSchema(String schema) {
		this.schema = schema;
	}

	@Override
	public void start()
	{
		try {
			URL csvFile = new URL(params.getUrl());
			input = new BufferedReader(new InputStreamReader(csvFile.openStream()));
		} catch (Exception e) {
			throw new ExternalTableException("Unable to download CSV file " + params.getUrl());
		}

		skipHeader();
	}

	/** Skip header of CSV file if metadata parameters define to do so */
	private void skipHeader() {
		try {
			if (params.getIgnoreHeader()) {
				// skip header;
				input.readLine();
				line++;
			}
		} catch (Exception e) {
			throw new ExternalTableException("Unable to read header in CSV file " + params.getUrl());
		}
	}

	@Override
	public void setParams(ExternalParamsInterface params) {
		this.params = (CsvExternalParams) params;
        if (!params.valid()) {
            throw new ExternalTableException("Parameters are not valid");
        }
	}

	@Override
	public ArrayList next() {
		String inputLine;
		try {
			line++;
			inputLine = input.readLine();
		} catch (Exception e) {
			throw new ExternalTableException("Unable to read line "+ line +" in CSV file " + params.getUrl());
		}

		/** This is the parser splitting the data that it found into columns of a map. */
		if (inputLine != null)
		{
			return convertCsvLineToObject(inputLine);
		} else {
			return null;
		}
	}

	@Override
	public boolean hasNext() {
		throw new UnsupportedOperationException("hasNext method is not supported in HTTPCsvExternal in this stage");
	}

	/** Convert csv line into table row.
	 *  Runtime exception is thrown when type of CSV column does not match type of table column	 */
	private ArrayList<Object> convertCsvLineToObject(final String inputLine)
	{
		final ArrayList<Object> retval = new ArrayList<Object>();
		ArrayList<String> row = new ArrayList<>(Arrays.asList(inputLine.split(params.getDelimiter())));
		if (row.size() != pos2Col.size()) {
			throw new ExternalTableException(
					"Line: " + line
							+ ".\nSize of external table does not match column count in CSV file '" + params.getUrl() + "'."
							+ "\nColumns in csv file: " + row.size()
							+ "\nColumns defined in external table schema: " + pos2Col.size()
			);
		}

		int column = 0;
		for (final Map.Entry<Integer, String> entry : pos2Col.entrySet()) {
			String type = cols2Types.get(entry.getValue());
			try {
				if (type.equals("INT")) {
					retval.add(Integer.parseInt(row.get(column)));
				} else if (type.equals("LONG")) {
					retval.add(Long.parseLong(row.get(column)));
				} else if (type.equals("FLOAT")) {
					retval.add(Double.parseDouble(row.get(column)));
				} else if (type.equals("VARCHAR")) {
					// TODO We need to cut value if it exceeds the limit defined in the table schema
					retval.add(row.get(column));
				} else if (type.equals("CHAR")) {
					// TODO We need to cut value if it exceeds the limit defined in the table schema
					retval.add(row.get(column));
				} else if (type.equals("DATE")) {
					final int year = Integer.parseInt(row.get(column).substring(0, 4));
					final int month = Integer.parseInt(row.get(column).substring(5, 7));
					final int day = Integer.parseInt(row.get(column).substring(8, 10));
					retval.add(new MyDate(year, month, day));
				}
			} catch (Exception e) {
				throw new ExternalTableException(
						"Line: " + line + ", column: " + column + "\n"
						+ "Error conversion '" + row.get(column) + "' to type '" + type + "'."
				);
			}
			column++;
		}

		return retval;
	}

	@Override
	public void reset()
	{
		throw new UnsupportedOperationException("Reset method is not supported in HTTPCsvExternal in this stage");
	}

	@Override
	public void close()
	{
		try {
			input.close();
		} catch (Exception e) {
			throw new ExternalTableException("Error during closing InputBuffer reading CSV file");
		}
	}
}