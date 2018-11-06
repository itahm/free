package com.itahm.free;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import com.itahm.http.HTTPListener;
import com.itahm.http.HTTPServer;
import com.itahm.http.Request;
import com.itahm.http.Connection;
import com.itahm.http.Response;
import com.itahm.http.Session;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

public class ITAhM extends HTTPServer implements Closeable, HTTPListener {

	private byte [] event = null;
	
	public ITAhM() throws Exception  {
		super("0.0.0.0", 2014);
		
		System.out.format("ITAhM HTTP Server started with TCP %d.\n", 2014);
		
		if (!Agent.isValidLicense(license)) {
			throw new Exception("Check your License[1].");
		}
		
		if (root == null || !root.isDirectory()) {
			throw new Exception("Root not found.");
		}
		
		System.out.format("Root : %s\n", root.getAbsoluteFile());
		
		if (expire > 0) {
			if (Calendar.getInstance().getTimeInMillis() > expire) {
				throw new Exception("Check your License[2].");
			}
			
			new Timer().schedule(new TimerTask() {
				
				@Override
				public void run() {
					Agent.close();
					
					new Exception("Check your License[3].").printStackTrace();
				}
			}, new Date(expire));
		}
		
		System.out.format("Agent loading...\n");
		
		Agent.initialize(root);
		Agent.setLimit(limit);
		Agent.setExpire(expire);
		Agent.setListener(this);
		
		Agent.start();
		
		System.out.println("ITAhM agent has been successfully started.");
	}

	@Override
	public void close() {
		Agent.close();
	}
	
	@Override
	public void doGet(Request request, Response response) {
		String uri = request.getRequestURI();
		File file = new File(Agent.root, uri);
		
		if (!Pattern.compile("^/data/.*").matcher(uri).matches() && file.isFile()) {
			try {
				response.write(file);
			} catch (IOException e) {
				response.setStatus(Response.Status.SERVERERROR);
			}
		}
		else {
			response.setStatus(Response.Status.NOTFOUND);
		}
	}
	
	@Override
	public void doPost(Request request, Response response) {
		String origin = request.getHeader(Connection.Header.ORIGIN.toString());
		
		if (origin != null) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.setHeader("Access-Control-Allow-Credentials", "true");
		}
		
		JSONObject data;
		
		try {
			data = new JSONObject(new String(request.read(), StandardCharsets.UTF_8.name()));
			
			if (!data.has("command")) {
				throw new JSONException("Command not found.");
			}
			
			Session session = request.getSession(false);
			
			switch (data.getString("command").toLowerCase()) {
			case "signin":
				JSONObject account = null;
				
				if (session == null) {
					account = Agent.signIn(data);
					
					if (account == null) {
						response.setStatus(Response.Status.UNAUTHORIZED);
					}
					else {
						session = request.getSession();
					
						session.setAttribute("account", account);
						session.setMaxInactiveInterval(60 * 60);
					}
				}
				else {
					account = (JSONObject)session.getAttribute("account");
				}
				
				if (account != null) {
					response.write(account.toString());
				}
				
				break;
				
			case "signout":
				if (session != null) {
					session.invalidate();
				}
				
				break;
				
			case "listen":
				if (session == null) {
					response.setStatus(Response.Status.UNAUTHORIZED);
				}
				else {
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
				}
				
				break;
				
			default:
				if (session == null) {
					response.setStatus(Response.Status.UNAUTHORIZED);
				}
				else if (!Agent.request(data, response)) {
					throw new JSONException("Command not found.");
				}
			}
					
		} catch (JSONException | UnsupportedEncodingException e) {
			response.setStatus(Response.Status.BADREQUEST);
			
			response.write(new JSONObject().
				put("error", e.getMessage()).toString());
		}
	}
	
	public static void main(String[] args) throws Exception {
		File root = null;
		int tcp = 2014;
		
		for (int i=0, _i=args.length; i<_i; i++) {
			if (args[i].indexOf("-") != 0) {
				continue;
			}
			
			switch(args[i].substring(1).toUpperCase()) {
			case "PATH":
				root = new File(args[++i]);
				
				break;
			case "TCP":
				try {
					tcp = Integer.parseInt(args[++i]);
				}
				catch (NumberFormatException nfe) {}
				
				break;
			}
		}
		
		if (root == null) {
			root = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
		}
		
		ITAhM itahm = new ITAhM(root, tcp);
			
		Runtime.getRuntime().addShutdownHook(
			new Thread() {
				public void run() {
					itahm.close();
				}
			});
	}

	@Override
	public void sendEvent(JSONObject event, boolean broadcast) {
		synchronized(this) {
			try {
				this.event = event.toString().getBytes(StandardCharsets.UTF_8.name());
				
				notifyAll();
			} catch (UnsupportedEncodingException e) {}			
		}
		
		if (broadcast) {
			// TODO customize for sms or app
		}
	}
}
