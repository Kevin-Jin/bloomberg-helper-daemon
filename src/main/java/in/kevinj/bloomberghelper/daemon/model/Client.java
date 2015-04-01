package in.kevinj.bloomberghelper.daemon.model;

import in.kevinj.bloomberghelper.common.Role;

import java.util.EnumSet;
import java.util.Set;

public class Client {
	public enum Status {
		WAITING
	}

	private final byte[] passHash;
	private final Set<Role> connected;
	private Status status;

	public Client(String password) {
		passHash = HashFunctions.sha512(password);
		connected = EnumSet.noneOf(Role.class);
		status = Status.WAITING;
	}

	public boolean authenticate(String password) {
		return (HashFunctions.checkSha512Hash(passHash, password));
	}

	public boolean dataReady() {
		return connected.contains(Role.DEV_CLIENT) && connected.contains(Role.TERM_CLIENT);
	}

	public void connected(Role role) {
		connected.add(role);
	}

	public void disconnected(Role role) {
		connected.remove(role);
	}

	public Status getStatus() {
		return status;
	}
}
