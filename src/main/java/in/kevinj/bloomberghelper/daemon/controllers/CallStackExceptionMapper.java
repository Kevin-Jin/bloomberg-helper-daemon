package in.kevinj.bloomberghelper.daemon.controllers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class CallStackExceptionMapper implements ExceptionMapper<Throwable> {
	private static String makeMessage(Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(e).append('\n');
		for (StackTraceElement line : e.getStackTrace())
			sb.append("\tat ").append(line).append('\n');
		return sb.toString();
	}

	@Override
	public Response toResponse(Throwable exception) {
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity(makeMessage(exception)).build();
	}
}
