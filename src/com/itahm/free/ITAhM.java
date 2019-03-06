package com.itahm.free;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import com.itahm.http.HTTPListener;
import com.itahm.http.HTTPServer;
import com.itahm.http.Request;
import com.itahm.Agent;
import com.itahm.http.Response;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

public class ITAhM extends HTTPServer implements HTTPListener {

	private byte [] event = null;
	
	public ITAhM(JSONObject config) throws Exception  {
		super(config);
		
		System.out.format("ITAhM HTTP Server started with TCP %d.\n", config.getInt("tcp"));
	}

	public void init(JSONObject config) {
		System.setErr(
			new PrintStream(
				new OutputStream() {

					@Override
					public void write(int b) throws IOException {
					}	
				}
			) {
			
				@Override
				public void print(Object e) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);

					((Exception)e).printStackTrace(pw);
					
					Agent.event().put(
						new JSONObject()
						.put("origin", "exception")
						.put("message", sw.toString()), false);
				}
			}
		);
		
		System.out.println("ITAhM Agent start.");
		
		if (config.has("expire")) {
			Calendar c = Calendar.getInstance();
			
			c.setTimeInMillis(config.getLong("expire"));
			
			System.out.format("Expire: %s", String.format("%04d-%02d-%02d %02d:%02d:%02d"
				, c.get(Calendar.YEAR)
				, c.get(Calendar.MONTH +1)
				, c.get(Calendar.DAY_OF_MONTH)
				, c.get(Calendar.HOUR_OF_DAY)
				, c.get(Calendar.MINUTE)
				, c.get(Calendar.SECOND)));
			
			System.out.println();
		}
		else {
			System.out.println("Expire: none");
		}
		
		if (config.has("limit")) {
			System.out.format("Limit: %d", config.getLong("limit"));
			
			System.out.println();
		}
		else {
			System.out.println("Limit: none");
		}
		
		System.out.println("License: free");
		
		if (config.has("root")) {
			File root = new File(config.getString("root"));
			
			if (!root.isDirectory()) {
				System.out.println("Check your Configuration.Root");
				
				return;
			}
		
			System.out.format("Root : %s\n", root.getAbsoluteFile());
			
			Agent.Config.root(root);
		}
		else {
			System.out.println("Check your Configuration.Root");
			
			return;
		}
		
		System.out.format("Agent loading...\n");
		
		Agent.Config.listener(this);
		
		try {	
			Agent.start();
		} catch (IOException ioe) {
			System.err.print(ioe);
			
			return;
		}
	
		System.out.println("ITAhM agent has been successfully started.");
	}
	
	@Override
	public void close() {
		try {
			super.close();
			
			Agent.stop();
		} catch (IOException ioe) {
			System.err.print(ioe);
		}
	}
	
	@Override
	public void doPost(Request request, Response response) {
		response.setHeader("Access-Control-Allow-Origin", "http://console.itahm.com");
		response.setHeader("Access-Control-Allow-Credentials", "true");
		
		if (!Agent.ready) {
			response.setStatus(Response.Status.UNAVAILABLE);
			
			return;
		}
		
		JSONObject data;
		
		try {
			data = new JSONObject(new String(request.read(), StandardCharsets.UTF_8.name()));
			
			if (!data.has("command")) {
				throw new JSONException("Command not found.");
			}
			
			switch (data.getString("command").toLowerCase()) {
			case "listen":
				JSONObject event = null;
				
				if (data.has("index")) {
					event = Agent.getEvent(data.getLong("index"));
					
				}
				
				if (event == null) {
					synchronized(this) {
						try {
							wait();
						} catch (InterruptedException ie) {
						}
						
						response.write(this.event);
					}
				}
				else {
					response.write(event.toString().getBytes(StandardCharsets.UTF_8.name()));
				}
				
				break;
			
			case "debug":
				System.err.print(new Exception("test"));
				
				break;
				
			default:
				if (!Agent.request(data, response)) {
					throw new JSONException("Command not found.");
				}
			}
		} catch (JSONException | UnsupportedEncodingException e) {
			response.setStatus(Response.Status.BADREQUEST);
			
			response.write(new JSONObject().
				put("error", e.getMessage()).toString());
		}
	}
	
	public static void main(String[] args) {
		JSONObject config = new JSONObject()
			//.put("expire", "0")
			//.put("limit", "0")
			//.put("license", "XXXXXXXXXXXX")
			;
		
		for (int i=0, _i=args.length; i<_i; i++) {
			if (args[i].indexOf("-") != 0) {
				continue;
			}
			
			switch(args[i].substring(1).toUpperCase()) {
			case "ROOT":
				config.put("root", args[++i]);
				
				break;
			case "TCP":
				try {
					config.put("tcp", Integer.parseInt(args[++i]));
				}
				catch (NumberFormatException nfe) {}
				
				break;
			}
		}
		
		try {
			if (!config.has("root")) {
				config.put("root", new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
			}
			
			try {
				ITAhM itahm = new ITAhM(config);
				
				Runtime.getRuntime().addShutdownHook(
					new Thread() {
						public void run() {
							itahm.close();
						}
					});
			}
			catch (BindException be) {
				System.out.format("Error!: Address %d already in use", config.getInt("tcp"));
				System.out.println();
				System.out.println("Can not start ITAhM Agent.");
				
				throw be;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}

	@Override
	public void sendEvent(JSONObject event, boolean broadcast) {
		synchronized(this) {
			try {
				this.event = event.toString().getBytes(StandardCharsets.UTF_8.name());
				
				notifyAll();
			} catch (UnsupportedEncodingException e) {}			
		}
	}

	@Override
	public void doGet(Request request, Response response) {
	}

}
