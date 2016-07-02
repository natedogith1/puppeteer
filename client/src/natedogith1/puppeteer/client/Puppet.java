package natedogith1.puppeteer.client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import natedogith1.puppeteer.server.Message;

public class Puppet {
	
	private Socket socket;
	private BlockingQueue<byte[]> toSend = new LinkedBlockingQueue<byte[]>();
	private int curNonce = 0;
	private boolean closed = false;
	private Thread readThread;
	private Thread writeThread;
	private Map<ServerId, IServer> servers = new HashMap<ServerId, IServer>();
	private Map<Integer, IConnection> connections = new HashMap<Integer, IConnection>();
	private Map<Integer, IListener> listeners = new HashMap<Integer, IListener>();
	
	public Puppet(String server, int port) throws UnknownHostException, IOException {
		socket = new Socket(server, port);
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
	
	public void start() {
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
				writeThread.getState() == Thread.State.TERMINATED;
	}
	
	private void handleRead() {
		try {
			DataInputStream in = new DataInputStream(socket.getInputStream());
			while ( !isClosed() ) {
				int packetId = in.readByte();
				int id;
				IConnection conn = null;
				switch (Message.values()[packetId]) {
				case RESPONSE:
					int nonce = in.readInt();
					int oldPacketId = in.readByte();
					switch ( Message.values()[oldPacketId] ) {
					case REGISTER:
						listeners.get(nonce).registerReply(in.readInt(), nonce);
						break;
					case CONNECT:
					case CONNECT_NAME:
						listeners.get(nonce).connectReply(in.readInt(), nonce);
						break;
					case LOOKUP:
						int len = in.readInt();
						ServerId[] rep = new ServerId[len];
						for ( int i = 0; i < len; i++ ) {
							rep[i] = new ServerId(readString(in), in.readInt());
						}
						listeners.get(nonce).lookupReply(rep, nonce);
						break;
					default:
						break;
					}
					break;
				case CONNECT:
					IServer serv = servers.get(new ServerId(readString(in), in.readInt()));
					String name = readString(in);
					int sid = in.readInt();
					int cid = in.readInt();
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
					close();
					break;
				default:
					close();
					break;
				}
			}

		} catch (EOFException e) {
			// handled in finally
		} catch (IOException e) {
			// handled in finally
		} catch (ArrayIndexOutOfBoundsException e) {
			// caused by an invalid packet id
			// handled in finally
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
		try {
			DataOutputStream out = getSuitableOutput();
			out.writeByte(Message.END_SESSION.ordinal());
			out.close();
		} catch (IOException e) {
			assert false; // this shouldn't ever happen
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
}
