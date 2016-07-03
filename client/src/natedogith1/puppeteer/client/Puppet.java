package natedogith1.puppeteer.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import test.Logger;

public class Puppet {
	
	public static final int DEFAULT_PORT = 11717;
	
	private String server;
	private int port;
	private Socket socket;
	private BlockingQueue<byte[]> toSend = new LinkedBlockingQueue<byte[]>();
	private int curNonce = 0;
	private boolean closed = false;
	private Thread readThread;
	private Thread writeThread;
	private Map<ServerId, IServer> servers = new ConcurrentHashMap<ServerId, IServer>();
	private Map<Integer, IConnection> connections = new ConcurrentHashMap<Integer, IConnection>();
	private Map<Integer, IListener> listeners = new ConcurrentHashMap<Integer, IListener>();
	private List<Runnable> closeListeners = Collections.synchronizedList(new LinkedList<Runnable>());
	
	public Puppet(String server, int port){
		this.server = server;
		if ( port < 0 )
			port = DEFAULT_PORT;
		this.port = port;
		readThread = new Thread("Read") {
			@Override
			public void run() {
				handleRead();
			}
		};
		writeThread = new Thread("Write") {
			@Override
			public void run() {
				handleWrite();
			}
		};
	}
	
	public void start() throws UnknownHostException, IOException {
		socket = new Socket(server, port);
		readThread.start();
		writeThread.start();
	}
	
	
	private int getNonce() {
		return curNonce++;
	}
	
	private void writeData(DataOutputStream out, byte[] buf) throws IOException {
		out.writeInt(buf.length);
		out.write(buf);
	}
	
	private void writeString(DataOutputStream out, String str) throws IOException {
		writeData(out, str.getBytes("UTF-8"));
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
	
	public boolean registerCloseListener(Runnable listener) {
		return closeListeners.add(listener);
	}
			
	public boolean unregisterCloseListener(Runnable listener) {
		return closeListeners.remove(listener);
	}
	
	public void connect(String name, IConnection connection) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.CONNECT_NAME.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			writeString(out, name);
			out.close();
			listeners.put(nonce, new ConnectionComplete(connection));
		} catch (IOException e) {
			assert false;
		}
	}
	
	public void connect(String name, IConnection connection, int id) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.CONNECT.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			writeString(out, name);
			out.writeInt(id);
			out.close();
			listeners.put(nonce, new ConnectionComplete(connection));
		} catch (IOException e) {
			assert false;
		}
	}
	
	public void lookup(String query, IListener listener) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.LOOKUP.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			writeString(out, query);
			out.close();
			listeners.put(nonce, listener);
		} catch (IOException e) {
			assert false;
		}
	}
	
	public void register(String name, IServer server) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.REGISTER.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			writeString(out, name);
			out.close();
			listeners.put(nonce, new ServerComplete(name, server));
		} catch (IOException e) {
			assert false;
		}
	}
	
	public void unregister(String name, IServer server, int id) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.UNREGISTER.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			writeString(out, name);
			out.writeInt(id);
			out.close();
			servers.remove(new ServerId(name,id));
		} catch (IOException e) {
			assert false;
		}
	}
	
	public void sendData(int channel, byte buf[]) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte(Message.SEND.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			out.writeInt(channel);
			writeData(out, buf);
			out.close();
		} catch (IOException e) {
			assert false; // not sure how this can happen
		}
	}
	
	public void close(int channel) {
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte((byte)Message.CLOSE.ordinal());
			int nonce = getNonce();
			out.writeInt(nonce);
			out.writeInt(channel);
			out.close();
			IConnection conn = connections.remove(channel);
			if ( conn != null )
				conn.close(channel);
		} catch (IOException e) {
			assert false;
		}
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

	public boolean isClosed() {
		return closed || readThread.getState() == Thread.State.TERMINATED || 
				writeThread.getState() == Thread.State.TERMINATED || socket.isClosed();
	}
	
	private void handleRead() {
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			loop:while ( !isClosed() ) {
				int packetId = in.readByte();
				int id;
				IConnection conn = null;
				switch (Message.values()[packetId]) {
				case RESPONSE:
					int nonce = in.readInt();
					int oldPacketId = in.readByte();
					IListener listener= listeners.remove(nonce);
					if ( listener == null )
						listener = new IListener.ListenerAdapter(){};
					switch ( Message.values()[oldPacketId] ) {
					case REGISTER:
						listener.registerReply(in.readInt(), nonce);
						break;
					case CONNECT:
					case CONNECT_NAME:
						listener.connectReply(in.readInt(), nonce);
						break;
					case LOOKUP:
						int len = in.readInt();
						ServerId[] rep = new ServerId[len];
						for ( int i = 0; i < len; i++ ) {
							rep[i] = new ServerId(readString(in), in.readInt());
						}
						listener.lookupReply(rep, nonce);
						break;
					default:
						break;
					}
					break;
				case CONNECT:
					String name = readString(in);
					int sid = in.readInt();
					int cid = in.readInt();
					IServer serv = servers.get(new ServerId(name, sid));
					if ( serv != null )
						conn = serv.newConnection(cid, name, sid);
					if ( conn == null )
						conn = new DeadConnection(this);
					conn.setId(cid);
					connections.put(cid, conn);
					break;
				case SEND:
					id = in.readInt();
					conn = connections.get(id);
					if ( conn == null )
						conn = new DeadConnection(this);
					conn.dataRecieved(readData(in), id);
					break;
				case CLOSE:
					id = in.readInt();
					conn = connections.remove(id);
					if ( conn == null )
						conn = new DeadConnection(this);
					conn.close(id);
					break;
				case END_SESSION:
					break loop;
				default:
					break loop;
				}
			}

		} catch (EOFException e) {
			// handled in finally
		} catch (IOException e) {
			// handled in finally
		} catch (ArrayIndexOutOfBoundsException e) {
			// caused by an invalid packet id
			// handled in finally
		} finally {
			close();
		}
	}
	
	private void handleWrite() {
		try {
			while ( !isClosed() ) {
				OutputStream out = socket.getOutputStream();
				out.write(toSend.take());
			}
		} catch (IOException e) {
			
		} catch (InterruptedException e) {
			
		} finally {
			close();
		}
	}
	
	public void close() {
		if ( closed )
			return;
		closed = true;
		for ( Runnable runnable : closeListeners )
			runnable.run();
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte(Message.END_SESSION.ordinal());
			out.close();
		} catch (IOException e) {
			assert false; // this shouldn't ever happen
		}
		for ( Map.Entry<ServerId, IServer> e : servers.entrySet() )
			e.getValue().close(e.getKey().getName(),e.getKey().getId());
		for ( Map.Entry<Integer, IConnection> e : connections.entrySet() )
			e.getValue().close(e.getKey());
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
	
	private class ConnectionComplete extends IListener.ListenerAdapter {
		private IConnection connection;
		public ConnectionComplete( IConnection connection ) {
			this.connection = connection;
		}
		@Override
		public void connectReply(int id) {
			connections.put(id, connection);
			connection.setId(id);
		}
	}
	
	private class ServerComplete extends IListener.ListenerAdapter {
		private String name;
		private IServer server;
		public ServerComplete( String name, IServer server ) {
			this.name = name;
			this.server = server;
		}
		@Override
		public void registerReply(int id) {
			servers.put(new ServerId(name, id), server);
			server.idAquired(id, name);
		}
	}
	
	public Map<ServerId,IServer> getServers() {
		return Collections.unmodifiableMap(servers);
	}
	public int getPort() {
		return socket.getLocalPort();
	}
}
