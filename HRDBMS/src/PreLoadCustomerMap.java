import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;


public class PreLoadCustomerMap extends Mapper<Object, Text, Text, Text>
{
	private Text outKey;
	
	protected void map(Object key, Text row, Context context) throws IOException, InterruptedException
	{
		StringTokenizer tokens = new StringTokenizer(row.toString(), "|", false);
		tokens.nextToken(); tokens.nextToken(); tokens.nextToken(); tokens.nextToken();
		String partKey = tokens.nextToken().substring(0, 2);
		outKey = new Text("C" + (Math.abs(partKey.hashCode()) % Integer.parseInt(context.getConfiguration().get("HRDBMS.nodes"))));
		context.write(outKey, row);
	}
}
