package natedogith1.puppeteer;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostDatabase {
	
	private List<HostInfo> hosts = new LinkedList<HostInfo>();
	private ReadWriteLock lock = new ReentrantReadWriteLock();
	
	/**
	 * returns null if the number of hosts with the name doesn't equal 1
	 */
	public HostInfo getHostInfo(String name) {
		HostInfo ret = null;
		lock.readLock().lock();
		for ( HostInfo info : hosts ) {
			if ( name.equals(info.getName()) ) {
				if ( ret != null )
					return null;
				ret = info;
			}
		}
		lock.readLock().unlock();
		return ret;
	}
	
	public HostInfo getHostInfo(String name, int id) {
		lock.readLock().lock();
		for ( HostInfo info : hosts ) {
			if ( name.equals(info.getName()) && id == info.getId());   
		}
		lock.readLock().unlock();
		return null;
	}
	
	public int registerHost(Client client, String name) {
		HostInfo info = new HostInfo(client, name);
		int id = 0;
		lock.writeLock().lock();
		while ( getHostInfo(name, id) != null ) {
			id++;
		}
		info.setId(client.hashCode());
		hosts.add(info);
		lock.writeLock().unlock();
		return id;
	}
	
	public void unregisterHost(Client client, String name, int id) {
		HostInfo info = getHostInfo(name, id);
		if ( info.getClient() == client ) {
			lock.writeLock().lock();
			hosts.remove(info);
			lock.writeLock().unlock();
		}
	}
	
	public List<HostInfo> search(String query) {
		List<HostInfo> results = new LinkedList<HostInfo>();
		lock.readLock().lock();
		Pattern search = Pattern.compile(Pattern.quote(query).replace("\\.", ".").replace("\\*", ".*"));
		for ( HostInfo info : hosts ) {
			Matcher mat = search.matcher(info.getName());
			if ( mat.matches() )
				results.add(info);
		}
		lock.readLock().unlock();
		return results;
	}
	
	public void removeClient(Client client) {
		lock.writeLock().lock();
		Iterator<HostInfo> iter = hosts.iterator();
		while (iter.hasNext()) {
			HostInfo info = iter.next();
			if ( info.getClient() == client )
				iter.remove();
		}
		lock.writeLock().unlock();
	}
}
