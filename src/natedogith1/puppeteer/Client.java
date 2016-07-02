package natedogith1.puppeteer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Client {

	private Server server;
	private Socket socket;
	private Queue<byte[]> toSend = new ConcurrentLinkedQueue<byte[]>();
	private Object sendLock = new Object();
	private Map<Integer,Connection> connections = new HashMap<Integer,Connection>();
	private int curId = 1;
	boolean closed = false;
	Thread readThread;
	Thread writeThread;
	
	public Client(Server server, Socket socket) {
		this.server = server;
		this.socket = socket;
		String threadSuffix = " for " + socket.getInetAddress().toString() + ":" + socket.getPort();
		readThread = new Thread("Read" + threadSuffix) {
			@Override
			public void run() {
				handleRead();
			}
		};
		writeThread = new Thread("Write" + threadSuffix) {
			@Override
			public void run() {
				handleWrite();
			}
		};
	}
	
	public void start() {
		readThread.start();
		writeThread.start();
	}
	
	public boolean isClosed() {
		return closed || readThread.getState() == Thread.State.TERMINATED || 
				writeThread.getState() == Thread.State.TERMINATED;
	}
	
	private DataOutputStream getSuitableOutput() {
		final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		return new DataOutputStream(new FilterOutputStream(bOut){
			@Override
			public void close() throws IOException {
				bOut.close();
				if ( bOut.size() > 0 )
					toSend.add(bOut.toByteArray());
			}
		});
	}
	
	private void writeString(DataOutputStream out, String str) throws IOException {
		byte[] buf = str.getBytes("UTF-8");
		out.writeInt(buf.length);
		out.write(buf);
	}
	
	private byte[] readData(DataInputStream in) throws IOException {
		int length = in.readInt();
		byte[] buf = new byte[length];
		in.readFully(buf);
		return buf;
	}
	
	private String readString(DataInputStream in) throws IOException {
		return new String(readData(in), "UTF-8");
	}
	
	public void closeConnection(int conId) {
		try {
			connections.remove(conId);
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.CLOSE.ordinal());
			out.writeInt(conId);
			out.close();
		} catch (IOException e) {
			assert false;// this shouldn't ever happen
		}
	}
	
	public void putData(int conId, byte[] buf) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.SEND.ordinal());
			out.writeInt(buf.length);
			out.write(buf);
		} catch (IOException e) {
			assert false;// this shouldn't ever happen
		}
	}
	
	private int getNextConnectionId() {
		if ( curId == 0)
			curId++;
		return curId++;
	}
	
	private int establishConnection(HostInfo self, Client other, int otherId) throws IOException {
		DataOutputStream out = getSuitableOutput();
		out.writeByte(Message.CONNECT.ordinal());
		writeString(out, self.getName());
		out.writeInt(self.getId());
		int id = getNextConnectionId();
		out.writeInt(id);
		out.close();
		connections.put(id, new Connection(other, otherId));
		return id;
	}
	
	private int handleConnect(HostInfo info) throws IOException {
		if ( info == null )
			return 0;
		Client other = info.getClient();
		int id = getNextConnectionId();
		int otherId = other.establishConnection(info, this, id);
		connections.put(id, new Connection(other, otherId));
		return id;
	}
	
	private void doLookup(DataOutputStream out, String query) throws IOException {
		List<HostInfo> infos = server.getHostDatabase().search(query);
		out.write(Message.LOOKUP.ordinal());
		out.writeInt(infos.size());
		for ( HostInfo info : infos ) {
			writeString(out, info.getName());
			out.writeInt(info.getId());
		}
	}
	
	private void handleRead() {
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			while ( !isClosed() ) {
				DataOutputStream out = getSuitableOutput();
				int packetID = in.readByte();
				int nonce = in.readInt();
				out.writeByte(Message.RESPONSE.ordinal());
				out.writeInt(nonce);
				String name;
				int id;
				int conId;
				byte[] data;
				Connection con;
				switch (Message.values()[packetID]) {
				case REGISTER:
					name = readString(in);
					id = server.getHostDatabase().registerHost(this, name);
					out.writeInt(id);
					break;
				case UNREGISTER:
					name = readString(in);
					id = in.readInt();
					server.getHostDatabase().unregisterHost(this, name, id);
					break;
				case CONNECT:
					name = readString(in);
					id = in.readInt();
					conId = handleConnect(server.getHostDatabase().getHostInfo(name, id));
					out.writeInt(conId);
					break;
				case CONNECT_NAME:
					name = readString(in);
					conId = handleConnect(server.getHostDatabase().getHostInfo(name));
					out.writeInt(conId);
					break;
				case LOOKUP:
					out.writeInt(nonce);
					doLookup(out, readString(in));
					break;
				case SEND:
					conId = in.readInt();
					data = readData(in);
					con = connections.get(conId);
					con.other.putData(con.otherId, data);
					break;
				case CLOSE:
					conId = in.readInt();
					con = connections.remove(conId);
					con.other.closeConnection(con.otherId);
					break;
				case END_SESSION:
					close();;
					break;
				default:
					break;
				}
				out.close();
			}
		} catch (EOFException e) {
			// handled in finally
		} catch (IOException e) {
			// handled in finally
		} finally {
			close();
		}
	}
	
	private void handleWrite() {
		try {
			while ( !isClosed() ) {
				OutputStream out = socket.getOutputStream();
				synchronized(sendLock) {
					try {
						while(toSend.isEmpty())
							sendLock.wait();
					} catch (InterruptedException e) {
						return;
					}
				}
				while(!toSend.isEmpty()) {
					out.write(toSend.poll());
				}
			}
		} catch (IOException e) {
			
		} finally {
			close();
		}
	}
	
	private void close() {
		if ( closed )
			return;
		closed = true;
		server.getHostDatabase().removeClient(this);
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte(Message.END_SESSION.ordinal());
			out.close();
		} catch (IOException e) {
			assert false; // this shouldn't ever happen
		}
		for( Connection e : connections.values() ) {
			e.other.closeConnection(e.otherId);
		}
		try {
			socket.close();
		} catch (IOException e) {
			// not sure what can even be thrown here
		}
		if ( readThread != null )
			readThread.interrupt();
		if ( writeThread != null )
			writeThread.interrupt();
	}
	
	private static class Connection {
		Client other;
		int otherId;
		public Connection(Client other, int otherId) {
			this.other = other;
			this.otherId = otherId;
		}
	}
}
