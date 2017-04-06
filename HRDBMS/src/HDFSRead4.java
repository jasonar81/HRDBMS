import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.hdfs.*;
import org.apache.hadoop.hdfs.client.HdfsDataInputStream;
import org.apache.hadoop.hdfs.protocol.*;
import org.apache.hadoop.net.NetUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;


public class HDFSRead4 {
    public static void main(String[] args) throws IOException {

        Configuration conf = new Configuration();
        conf.addResource(new Path("core-site.xml"));
        conf.addResource(new Path("hdfs-site.xml"));

        String filePath = "hdfs://17.17.0.5:9000/user/root/csv_type.csv";
        Path path = new Path(filePath);
        DistributedFileSystem fs = (DistributedFileSystem) path.getFileSystem(conf);
        HdfsDataInputStream inputStream = (HdfsDataInputStream) fs.open(path);

        List<LocatedBlock> blocks = inputStream.getAllBlocks();
        for (LocatedBlock b : blocks) {
            DFSClient dfsClient = fs.getClient();

            // it should be a better way to choose node
            DatanodeInfo chosenNode = b.getLocations()[0];
            String var9 = chosenNode.getXferAddr(false);
            InetSocketAddress targetAddr = NetUtils.createSocketAddr(var9);
            BlockReader blockReader = (new BlockReaderFactory(fs.getClient().getConf())).setInetSocketAddress(targetAddr).setRemotePeerFactory(fs.getClient()).setDatanodeInfo(  chosenNode ).setStorageType(b.getStorageTypes()[0]).setFileName(/* this.src */ path.toUri().getPath()).setBlock(b.getBlock()).setBlockToken(b.getBlockToken()).setStartOffset(b.getStartOffset()).setVerifyChecksum(true).setClientName(fs.getClient().getClientName()).setLength(b.getBlock().getNumBytes() - b.getStartOffset()).setCachingStrategy(dfsClient.getDefaultReadCachingStrategy()).setAllowShortCircuitLocalReads(true).setClientCacheContext(dfsClient.getClientContext())/*.setUserGroupInformation(dfsClient.ugi)*/.setConfiguration(conf).build();
            byte[] buf = new byte[1024];
            int cnt = 0;
            long bytesRead = 0;
            try {
                while ((cnt = blockReader.read(buf, 0, buf.length)) > 0) {
                    ByteBuffer bb = ByteBuffer.wrap(buf);
                    BufferedReader r = wrapByteBuffer(bb);
                    String inputLine;
                    do {
                        inputLine = r.readLine();
                        System.out.println(inputLine);
                    } while (inputLine != null);
                    bytesRead += cnt;
                }
                if ( bytesRead != b.getBlock().getNumBytes() ) {
                    throw new IOException("Recorded block size is " + b.getBlock().getNumBytes() +
                            ", but datanode returned " +bytesRead+" bytes");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {blockReader.close(); } catch (Exception e1) {}
            }

        }
        fs.close();

    }

    private static BufferedReader wrapByteArray(byte[] byteArr) {
        return wrapByteArray(byteArr, 0, byteArr.length);
    }
    private static BufferedReader wrapByteArray(byte[] byteArr, int offset, int length) {
        ByteArrayInputStream stream = new ByteArrayInputStream(byteArr, offset, length);
        InputStreamReader sr = new InputStreamReader(stream);
        return new BufferedReader(sr);
    }
    private static BufferedReader wrapByteBuffer(ByteBuffer byteBuffer) {
        return wrapByteArray(byteBuffer.array());
    }

}