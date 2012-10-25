/**
 * @copyright 2012 City of Bloomington, Indiana
 * @license http://www.gnu.org/licenses/gpl.txt GNU/GPL, see LICENSE.txt
 * @author Cliff Ingham <inghamn@bloomington.in.gov>
 */
package gov.in.bloomington.georeporter.models;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

public class Open311 {
	/**
	 * Constants for Open311 keys
	 * 
	 * I'm tired of making typos in key names
	 */
	// Global required fields
	public static final String JURISDICTION = "jurisdiction_id";
	public static final String API_KEY      = "api_key";
	public static final String SERVICE_CODE = "service_code";
	public static final String SERVICE_NAME = "service_name";
	// Global basic fields
	public static final String MEDIA        = "media";
	public static final String MEDIA_URL    = "media_url";
	public static final String LATITUDE     = "lat";
	public static final String LONGITUDE    = "long";
	public static final String ADDRESS      = "address_string";
	public static final String DESCRIPTION  = "description";
	// Personal Information fields
	public static final String EMAIL        = "email";
	public static final String DEVICE_ID    = "devide_id";
	public static final String FIRST_NAME   = "first_name";
	public static final String LAST_NAME    = "last_name";
	public static final String PHONE        = "phone";
	// Custom field definition in service_definition
	public static final String METADATA     = "metadata";
	public static final String ATTRIBUTES   = "attributes";
	public static final String VARIABLE     = "variable";
	public static final String CODE         = "code";
	public static final String ORDER        = "order";
	public static final String VALUES       = "values";
	public static final String KEY          = "key";
	public static final String NAME         = "name";
	public static final String REQUIRED     = "required";
	public static final String DATATYPE     = "datatype";
	public static final String STRING       = "string";
	public static final String NUMBER       = "number";
	public static final String DATETIME     = "datetime";
	public static final String TEXT         = "text";
	public static final String SINGLEVALUELIST = "singlevaluelist";
	public static final String MULTIVALUELIST  = "multivaluelist";
	// Key names from /res/raw/available_servers.json
	public static final String URL            = "url";
	public static final String SUPPORTS_MEDIA = "supports_media";
	// Key names for the saved reports file
	private static final String SAVED_REPORTS_FILE = "service_requests";
	public  static final String SERVER             = "server";
	public  static final String SERVICE_REQUEST    = "service_request";
	public  static final String SERVICE_REQUEST_ID = "service_request_id";
	public  static final String TOKEN              = "token";
	
	public static Boolean                     sReady = false;
	public static JSONArray                   sServiceList = null;
	public static HashMap<String, JSONObject> sServiceDefinitions;
	public static ArrayList<String>           sGroups;
	
	
	private static JSONObject mEndpoint;
	private static String mBaseUrl;
	private static String mJurisdiction;
	private static String mApiKey;
	
	private static DefaultHttpClient mClient = null;
	private static final int TIMEOUT = 3000;
	
	
	private static Open311 mInstance;
	private Open311() {}
	public static synchronized Open311 getInstance() {
		if (mInstance == null) {
			mInstance = new Open311();
		}
		return mInstance;
	}
	
	/**
	 * Lazy load an Http client
	 * 
	 * @return
	 * DefaultHttpClient
	 */
	private static DefaultHttpClient getClient() {
		if (mClient == null) {
			mClient = new DefaultHttpClient();
			mClient.getParams().setParameter(CoreProtocolPNames  .HTTP_CONTENT_CHARSET, "UTF-8");
			mClient.getParams().setParameter(CoreProtocolPNames  .PROTOCOL_VERSION,     HttpVersion.HTTP_1_1);
			mClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT,           TIMEOUT);
			mClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,   TIMEOUT);
		}
		return mClient;
	}
	
	/**
	 * Loads all the service information from the endpoint
	 * 
	 * Endpoints will have a service_list, plus, for each
	 * service, there may be a service_definition.
	 * To make the user experience smoother, we are downloading
	 * and saving all the possible service information at once.
	 * 
	 * Returns false if there was a problem
	 * 
	 * @param current_server
	 * @return
	 * Boolean
	 */
	public static Boolean setEndpoint(JSONObject current_server) {
		sReady         = false;
		mBaseUrl      = null;
		mJurisdiction = null;
		mApiKey       = null;
		sGroups       = new ArrayList<String>();
		sServiceList  = null;
		sServiceDefinitions = new HashMap<String, JSONObject>();
		
		try {
			mBaseUrl      = current_server.getString(URL);
			mJurisdiction = current_server.optString(JURISDICTION);
			mApiKey       = current_server.optString(API_KEY);
		} catch (JSONException e) {
			return false;
		}
		try {
			sServiceList = new JSONArray(loadStringFromUrl(getServiceListUrl()));
			
			// Go through all the services and pull out the seperate groups
			// Also, while we're running through, load any service_definitions
			String group = "";
			int len = sServiceList.length();
			for (int i=0; i<len; i++) {
				JSONObject s = sServiceList.getJSONObject(i);
				// Add groups to mGroups
				group = s.optString("group");
				if (group != "" && !sGroups.contains(group)) { sGroups.add(group); }
				
				// Add Service Definitions to mServiceDefinitions
				if (s.optString("metadata") == "true") {
					String code = s.optString(SERVICE_CODE);
					JSONObject definition = getServiceDefinition(code);
					if (definition != null) {
						sServiceDefinitions.put(code, definition);
					}
				}
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		mEndpoint = current_server;
		sReady    = true;
		return sReady;
	}
	
	
	/**
	 * Returns the services for a given group
	 * 
	 * @param group
	 * @return
	 * ArrayList<JSONObject>
	 */
	public static ArrayList<JSONObject> getServices(String group) {
		ArrayList<JSONObject> services = new ArrayList<JSONObject>();
		int len = sServiceList.length();
		for (int i=0; i<len; i++) {
			try {
				JSONObject s = sServiceList.getJSONObject(i);
				if (s.optString("group").equals(group)) { services.add(s); }
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return services;
	}
	
	/**
	 * @param service_code
	 * @return
	 * JSONObject
	 */
	public static JSONObject getServiceDefinition(String service_code) {
		try {
			return new JSONObject(loadStringFromUrl(getServiceDefinitionUrl(service_code)));
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * POST new service request data to the endpoint
	 * 
	 * @param data
	 * @return
	 * JSONObject
	 */
	public static JSONArray postServiceRequest(HashMap<String, String> data) {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		for (Map.Entry<String, String> entry : data.entrySet()) {
			pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}
		if (mJurisdiction != null) {
			pairs.add(new BasicNameValuePair(JURISDICTION, mJurisdiction));
		}
		if (mApiKey != null) {
			pairs.add(new BasicNameValuePair(API_KEY, mApiKey));
		}
		
		HttpPost  request  = new HttpPost(mBaseUrl + "/requests.json");
		JSONArray response = null;
		try {
			request.setEntity(new UrlEncodedFormEntity(pairs));
			HttpResponse r = mClient.execute(request);
			response = new JSONArray(EntityUtils.toString(r.getEntity()));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}
	
	/**
	 * Reads the saved reports file into a JSONArray
	 * 
	 * Reports are stored as a file on the device internal storage
	 * The file is a serialized JSONArray of reports.
	 * 
	 * @return
	 * JSONArray
	 */
	public static JSONArray loadServiceRequests(Context c) {
		JSONArray service_requests = new JSONArray();
		
		StringBuffer buffer = new StringBuffer("");
		byte[] bytes = new byte[1024];
		int length;
		try {
			FileInputStream in = c.openFileInput(SAVED_REPORTS_FILE);
			while ((length = in.read(bytes)) != -1) {
				buffer.append(new String(bytes));
			}
			service_requests = new JSONArray(new String(buffer));
		} catch (FileNotFoundException e) {
			Log.w("Open311.loadServiceRequests", "Saved Reports File does not exist");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return service_requests;
	}
	
	/**
	 * Writes the stored reports back out the file
	 *
	 * Requests will be saved as JSON in the format of
	 * [
	 * 	{ server:          { },
	 * 	  service_request: { }
	 * 	},
	 *  { server:          { },
	 *    service_request: { }
	 *  }
	 * ]
	 * server: a copy of the endpoint information so we have
	 * enough information to make requests for up-to-date information
	 * 
	 * service_request: a cache of all the information from the report.
	 * This gets updated as we see new information from the server
	 * 
	 * @param c
	 * @param requests
	 * void
	 */
	private static boolean saveServiceRequests(Context c, JSONArray requests) {
		String json = requests.toString();
		FileOutputStream out;
		try {
			out = c.openFileOutput(SAVED_REPORTS_FILE, Context.MODE_PRIVATE);
			out.write(json.getBytes());
			out.close();
			return true;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Adds a service_request to the collection of saved reports
	 * 
	 * Reports are stored as a file on the device internal storage
	 * The file is a serialized JSONArray of reports.
	 * 
	 * Reports are in the form of:
	 *  { server:          { },
	 *    service_request: { }
	 *  }
	 * server: a copy of the endpoint information so we have
	 * enough information to make requests for up-to-date information
	 * 
	 * service_request: a cache of all the information from the report.
	 * This gets updated as we see new information from the server
	 *  
	 * @param report
	 * @return
	 * Boolean
	 */
	public static boolean saveServiceRequest(Context c, JSONArray request) {
		JSONObject report = new JSONObject();
		try {
			report.put(SERVER, mEndpoint);
			report.put(SERVICE_REQUEST, request.getJSONObject(0));
			
			JSONArray saved_requests = loadServiceRequests(c);
			saved_requests.put(report);
			return saveServiceRequests(c, saved_requests);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	
	/**
	 * Returns the response content from an HTTP request
	 * 
	 * @param url
	 * @return
	 * String
	 */
	private static String loadStringFromUrl(String url)
			throws ClientProtocolException, IOException, IllegalStateException {
		HttpResponse r = getClient().execute(new HttpGet(url));
		String response = EntityUtils.toString(r.getEntity());
		
		return response;
	}
	
	
	/**
	 * @return
	 * String
	 */
	private static String getServiceListUrl() {
		return mBaseUrl + "/services.json?" + JURISDICTION + "=" + mJurisdiction;
	}
	
	/**
	 * @param service_code
	 * @return
	 * String
	 */
	private static String getServiceDefinitionUrl(String service_code) {
		return mBaseUrl + "/services/" + service_code + ".json?" + JURISDICTION + "=" + mJurisdiction;
	}
}
