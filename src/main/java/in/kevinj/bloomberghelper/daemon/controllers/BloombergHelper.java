package in.kevinj.bloomberghelper.daemon.controllers;

import in.kevinj.bloomberghelper.common.Role;
import in.kevinj.bloomberghelper.daemon.model.Model;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ChunkedOutput;

@Path("bloomberghelper")
public class BloombergHelper {
	private static final String ROLES_REGEX = "TERM_CLIENT|DEV_CLIENT";

	@POST
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("TERM_CLIENT/register")
	public void termClientRegister(@Context Request req, @Suspended AsyncResponse asyncResponse) {
		System.out.println("Registering term-client from " + req.getRemoteAddr());
		Charset charset;
		if (req.getCharacterEncoding() != null)
			charset = Charset.forName(req.getCharacterEncoding());
		else
			charset = Charset.defaultCharset();
		NIOInputStream stream = req.getNIOInputStream();
		byte[] buffer = new byte[4096];
		stream.notifyAvailable(new ReadHandler() {
			@Override
			public void onDataAvailable() throws Exception {
				synchronized (buffer) {
					while (stream.isReady()) {
						int read = stream.read(buffer);
						System.out.print(new String(buffer, 0, read, charset));
					}
					stream.notifyAvailable(this);
				}
			}

			@Override
			public void onError(Throwable t) {
				System.out.println("CONNECTION CLOSED: PREMATURE");
				try {
					stream.close();
					asyncResponse.resume(Response
							.status(Response.Status.OK)
							.header("Connection", "Close")
							.entity("OK")
							.build());
				} catch (IOException e) { }
			}

			@Override
			public void onAllDataRead() throws Exception {
				System.out.println("CONNECTION CLOSED: EOF");
				stream.close();
				asyncResponse.resume(Response
						.status(Response.Status.OK)
						.header("Connection", "Close")
						.entity("OK")
						.build());
			}
		});
	}

	@POST
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("DEV_CLIENT/register")
	public ChunkedOutput<String> devClientRegister(@Context Request req) {
		System.out.println("Registering dev-client from " + req.getRemoteAddr());
		ChunkedOutput<String> output = new ChunkedOutput<String>(String.class);

		new Thread(() -> {
			try {
				//TODO: heartbeat every 30 seconds
				for (int i = 0; i < 10; i++) {
					Thread.sleep(2000);
					output.write(i + "\r\n");
					System.out.print(i + "\r\n");
				}
				System.out.println("CONNECTION CLOSED: EOF");
			} catch (Throwable th) {
				System.out.println("CONNECTION CLOSED: PREMATURE");
			} finally {
				try {
					output.close();
				} catch (IOException e) { }
			}
		}).start();

		return output;
	}

	/*@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.TEXT_PLAIN)
	@Path("{role : " + ROLES_REGEX + "}/register")
	public void register(@FormParam("key") String key, @FormParam("password") String password, @PathParam("role") String role, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		asyncResponse.register((CompletionCallback) (throwable -> {
			if (throwable != null) {
				System.err.println("Could not register");
				throwable.printStackTrace();
			}
		}));

		req.addAfterServiceListener(sameReq -> {
			System.out.println("CLOSED BEFORE");
		});
		//asyncResponse.resume(Boolean.toString(Model.INSTANCE.register(key, password, Role.valueOf(role))));
	}*/

	//TODO: pass a login token. HMAC the current time, HTTP verb, URL, and username with the password
	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("{role : " + ROLES_REGEX + "}/login")
	public void login(@FormParam("key") String key, @FormParam("password") String password, @PathParam("role") String role, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		asyncResponse.register((CompletionCallback) (throwable -> {
			if (throwable != null) {
				throw new WebApplicationException("Failed to login", throwable);
			}
		}));

		asyncResponse.resume(Boolean.toString(Model.INSTANCE.login(key, password, Role.valueOf(role))));
	}

	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("downloadbin")
	public void downloadBin(@QueryParam("key") String key, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		asyncResponse.register((CompletionCallback) (throwable -> {
			if (throwable != null) {
				throw new WebApplicationException("Failed to upload file", throwable);
			}
		}));
		asyncResponse.setTimeout(1, TimeUnit.MINUTES);
		asyncResponse.setTimeoutHandler(resp -> {
			//Model.INSTANCE.getClient(key).disconnected(role);
			asyncResponse.resume("HEARTBEAT");
		});
		//"This callback will be executed only if the connection was prematurely terminated or lost while the response is being written to the back client."
		//so this is useless for us. we want to detect whether the client was closed before we can send a response
		/*asyncResponse.register((ConnectionCallback) (resp -> {
			System.err.println("CLOSED");
		}));
		req.getRequest().getConnection().addCloseListener((org.glassfish.grizzly.CloseListener<?, ?>) (c, t) -> {
			System.out.println("CLOSED");
		});*/

		try (InputStream file = new FileInputStream("C:\\Users\\Kevin\\Google Drive\\Big Videos\\Urusei Yatsura\\Films\\[LRE]Urusei Yatsura - Movie 1 Only You [05ABAA82].mkv")) {
			StreamingOutput streamer = output -> {
				try {
					IOUtils.copyLarge(file, output);
				} catch (Exception e) {
					throw new WebApplicationException("Failed to upload file", e, Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to upload file").build());
				}
			};
			Response.ResponseBuilder res = Response.ok(streamer)
					.status(Response.Status.OK)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.zip\"")
					.header(HttpHeaders.CONTENT_LENGTH, file.available())
					.header(HttpHeaders.CACHE_CONTROL, "no-cache, must-revalidate")
					.header("Pragma", "no-cache")
					.header("Content-Transfer-Encoding", "binary")
					.header("Connection", "Close");
			asyncResponse.resume(res.build());
		} catch (IOException e) {
			throw new WebApplicationException("Failed to upload file", e);
		}
	}

	@GET
	@Path("uploadbin")
	public Response uploadBinPage() {
		return Response.status(Response.Status.OK).entity("<form action=\"uploadbin\" method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"file\"><input type=\"submit\"></form>").build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.TEXT_PLAIN + "; charset=utf-8")
	@Path("uploadbin")
	public void uploadBin(@FormDataParam("file") InputStream uploadedInputStream, @FormDataParam("file") FormDataContentDisposition fileDetail, @HeaderParam("Content-Length") int length, @Context Request req, @Suspended AsyncResponse asyncResponse) {
		try (OutputStream file = new FileOutputStream("C:\\Users\\Kevin\\test.file")) {
			IOUtils.copyLarge(uploadedInputStream, file);
			asyncResponse.resume("SUCCESS");
		} catch (IOException e) {
			throw new WebApplicationException("Failed to download file", e);
		}
	}
}
