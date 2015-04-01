package in.kevinj.bloomberghelper.daemon.model;

import in.kevinj.bloomberghelper.common.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Model {
	public static final Model INSTANCE = new Model();

	private final ReadWriteLock lock;
	private final Map<String, Client> passHashes;

	private Model() {
		lock = new ReentrantReadWriteLock();
		passHashes = new HashMap<>();
	}

	public boolean register(String key, String password, Role role) {
		lock.writeLock().lock();
		try {
			Client client = passHashes.get(key);
			if (client != null)
				return false;
			client = new Client(password);
			client.connected(role);
			passHashes.put(key, client);
			return true;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean login(String key, String password, Role role) {
		Client client;
		lock.readLock().lock();
		try {
			client = passHashes.get(key);
		} finally {
			lock.readLock().unlock();
		}
		if (client == null || !client.authenticate(password))
			return false;
		client.connected(role);
		return true;
	}

	public Client getClient(String key) {
		lock.readLock().lock();
		try {
			return passHashes.get(key);
		} finally {
			lock.readLock().unlock();
		}
	}
}
