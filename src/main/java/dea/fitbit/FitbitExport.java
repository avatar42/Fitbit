package dea.fitbit;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This does not work. There is a prepost action that sends a huge post before
 * the actual request. I have not been able to sort where the data for this
 * prepost comes from as it is buried in JavaScript. Without this prepost you
 * seem to just get an app failure. See go method for details
 */
public class FitbitExport {
	protected final Logger log = LoggerFactory.getLogger(getClass());
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");

	private String[] options = { "BODY", "FOODS", "ACTIVITIES", "SLEEP" };

	private String cookies;
	private HttpClient client;
	private final String USER_AGENT = "Mozilla/5.0";
	private String loginUrl = "https://www.fitbit.com/login";
	private String exportUrl = "https://www.fitbit.com/export/user/data";
	private GregorianCalendar startDate;
	private GregorianCalendar endDate;
	private String email;
	private String password;

	public void go(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("USAGE:" + getClass().getName()
					+ " email password date");
			System.err.println("Example:" + getClass().getName()
					+ " bob@gmail.com P@$$w0rd 7/30/2015");
			return;
		}
		email = args[0];
		password = args[1];
		Date d = logDateFormat.parse(args[2]);
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);

		startDate = new GregorianCalendar(cal.get(Calendar.YEAR),
				cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
		GregorianCalendar now = new GregorianCalendar();

		client = HttpClientBuilder.create().build();
		String page = getPageContent(loginUrl);

		List<NameValuePair> postParams = getFormParams(page, "loginForm");
		JSONParser parser = new JSONParser();

		while (startDate.before(now)) {
			client = HttpClientBuilder.create().build();
			log.info("startDate:"
					+ String.format("%1$tY-%1$tm-%1$td", startDate));
			endDate = new GregorianCalendar(cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH), 1);
			log.info("endDate:" + String.format("%1$tY-%1$tm-%1$td", endDate));
			cal.add(Calendar.MONTH, 1);
			endDate = new GregorianCalendar(cal.get(Calendar.YEAR),
					cal.get(Calendar.MONTH), 1);
			log.info("endDate:" + String.format("%1$tY-%1$tm-%1$td", endDate));
			sendPost(loginUrl, postParams);

			String resp = getPageContent(exportUrl);
			log.info("resp:" + resp);

			// Prepost
			// https://api.mixpanel.com/track/?data=eyJldmVudCI6ICJQcmVtaXVtOiBQcm9kdWN0IiwicHJvcGVydGllcyI6IHsiJG9zIjogIldpbmRvd3MiLCIkYnJvd3NlciI6ICJDaHJvbWUiLCIkcmVmZXJyZXIiOiAiaHR0cHM6Ly93d3cuZml0Yml0LmNvbS91c2VyL3Byb2ZpbGUvZWRpdCIsIiRyZWZlcnJpbmdfZG9tYWluIjogInd3dy5maXRiaXQuY29tIiwiJGN1cnJlbnRfdXJsIjogImh0dHBzOi8vd3d3LmZpdGJpdC5jb20vZXhwb3J0L3VzZXIvZGF0YSIsIiRicm93c2VyX3ZlcnNpb24iOiA0NCwiJHNjcmVlbl9oZWlnaHQiOiAxMDgwLCIkc2NyZWVuX3dpZHRoIjogMTkyMCwibXBfbGliIjogIndlYiIsIiRsaWJfdmVyc2lvbiI6ICIyLjYuMiIsImRpc3RpbmN0X2lkIjogIjI0MDQ0MjgiLCJjaXR5IjogIiIsImNvdW50cnkiOiAiIiwiaW50ZXJmYWNlIjogIiIsIm1lbWJlciI6ICIiLCJzdGF0ZSI6ICIiLCJ0aW1lem9uZSI6ICIiLCJBcGkgVXNhZ2UiOiAiIiwiQ2l0eSI6ICIiLCJTdGF0ZSI6ICIiLCJNZW1iZXIiOiAiIiwiVHJhY2tlciI6ICIiLCJMb2NhbGUiOiAiZW5fVVMiLCJMb2NhbGVMYW5nIjogImVuX1VTIiwiTG9jYWxlUmVhbCI6ICJVUyIsIkxvZ2dlZGluIjogIlRydWUiLCIkaW5pdGlhbF9yZWZlcnJlciI6ICIkZGlyZWN0IiwiJGluaXRpYWxfcmVmZXJyaW5nX2RvbWFpbiI6ICIkZGlyZWN0IiwiRW52aXJvbm1lbnQiOiAicHJvZCIsIkxvZ2dlZCBJbiI6IHRydWUsIlBsYXRmb3JtIjogIldlYiIsIk9wZXJhdGluZyBTeXN0ZW0gVmVyc2lvbiI6ICJXaW5kb3dzICA3IiwiUHJvZmlsZSBDb3VudHJ5IjogIlVTIiwiVGltZXpvbmUiOiAiQW1lcmljYS9DaGljYWdvIiwiUHJlbWl1bSI6ICJleHBpcmVkIiwiaVBob25lIjogIkZhbHNlIiwiU2lnblVwIENvaG9ydCI6ICIyMDEzX0FwciIsIlNpZ25VcCBDb2hvcnQgRGF0ZSI6ICIyMDEzLTA0LTEzIiwiU2lnblVwIE9mZmVyIjogIiIsIlNpZ25VcCBpUGhvbmUiOiAiRmFsc2UiLCJTaWduVXAgT0F1dGgiOiAiIiwiQ29uZGl0aW9uIjogIm92ZXIiLCJBY3Rpdml0eSBMZXZlbCI6ICIyMDAwLTQ5OTlzIiwiQXBpIFVzYWdlIERhdGUiOiAiMjAxNS0wOS0wMiIsIkFwaSBVc2VyIjogIlRydWUiLCJBZ2UiOiBudWxsLCJIYXMgQW5kcm9pZCBBcHAiOiB0cnVlLCJQYWlyZWQgT25lIjogdHJ1ZSwiSGFzIGlQaG9uZSBBcHAiOiBmYWxzZSwiUGFpcmVkIFppcCI6IGZhbHNlLCJQYWlyZWQgQXJpYSI6IHRydWUsIkhlaWdodCI6IG51bGwsIlByZW1pdW0gRXhwaXJlZCI6IHRydWUsIlBhaXJlZCBEZXZpY2VzIjogWwogICAgIk9uZSIsCiAgICAiQXJpYSIKXSwiR29hbCBXZWlnaHQgQ2hhbmdlIjogbnVsbCwiUGFpcmVkIEZvcmNlIjogZmFsc2UsIlBhaXJlZCBVbHRyYSI6IGZhbHNlLCJHZW5kZXIiOiBudWxsLCJHb29nbGUgTGlua2VkIjogZmFsc2UsIkdvYWxQcmltYXJ5IjogIlNURVBTIiwiRm9vZCBQbGFuIEludGVuc2l0eSI6ICJNZWRpdW0iLCJQYWlyZWQgRmxleCI6IGZhbHNlLCJCb2R5IFR5cGUiOiBudWxsLCJVc2VyIEFnZSBSYW5nZSI6IG51bGwsIlBhaXJlZCBDbGFzc2ljIjogZmFsc2UsIkZhY2Vib29rIExpbmtlZCI6IHRydWUsIiRzZWFyY2hfZW5naW5lIjogImdvb2dsZSIsIiFQQUdFX1RZUEUiOiAiUHJlbWl1bTogU3Vic2NyaXB0aW9uIiwiIVVJX0VWRU5UIjogIkV4cG9ydCIsIiFUSU1FX1BFUklPRCI6ICJDVVNUT00iLCIhREFUQV9UWVBFIjogWwogICAgIkJPRFkiLAogICAgIkZPT0RTIiwKICAgICJBQ1RJVklUSUVTIiwKICAgICJTTEVFUCIKXSwiIUZJTEVfRk9STUFUIjogIlhMUyIsInRva2VuIjogIjgyZDE5ODQ1YjI5OGZjYzhiODcxMzg2MWM5Y2Y2N2MwIn19&ip=1&_=1441722285987
			// TODO: add prepost GET call

			// Ask for sheet to be created
			List<NameValuePair> epostParams = getFormParams(resp,
					"dataExportForm");
			resp = sendPost(exportUrl, epostParams);

			// response
			// {"fileFormat":"XLS","fileIdentifier":"b57a89825cb4e57a1450a2724516d8b2d0cb3e35.xls"}
			log.info("resp:" + resp);
			JSONObject jo = (JSONObject) parser.parse(resp);

			String fileIdentifier = (String) jo.get("fileIdentifier");
			// check to see if file ready
			// https://www.fitbit.com/premium/export?isExportedFileReady=true&fileIdentifier=b57a89825cb4e57a1450a2724516d8b2d0cb3e35.xls
			boolean ready = false;
			while (!ready) {
				resp = getPageContent("https://www.fitbit.com/premium/export?isExportedFileReady=true&fileIdentifier="
						+ fileIdentifier);
				log.info("resp:" + resp);
				jo = (JSONObject) parser.parse(resp);
				// response when ready {"fileIsReady":true}
				ready = (boolean) jo.get("fileIsReady");
			}

			// then get file
			// https://www.fitbit.com/premium/export/download/b57a89825cb4e57a1450a2724516d8b2d0cb3e35.xls

			downloadFile(fileIdentifier);

			startDate = endDate;

		}
		System.out.println("Done");

	}

	private String sendPost(String postUrl, List<NameValuePair> postParams)
			throws Exception {

		HttpPost post = new HttpPost(postUrl);

		// add header
		post.setHeader("Host", "www.fitbit.com");
		post.setHeader("User-Agent", USER_AGENT);
		post.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		post.setHeader("Accept-Language", "en-US,en;q=0.5");
		post.setHeader("Cookie", getCookies());
		post.setHeader("Connection", "keep-alive");
		post.setHeader("Referer", postUrl);
		post.setHeader("Content-Type", "application/x-www-form-urlencoded");

		post.setEntity(new UrlEncodedFormEntity(postParams));

		HttpResponse response = client.execute(post);

		int responseCode = response.getStatusLine().getStatusCode();

		log.info("\nSending 'POST' request to URL : " + postUrl);
		log.info("Post parameters : " + postParams);
		log.info("Response Code : " + responseCode);

		BufferedReader rd = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		post.reset();

		log.info(result.toString());

		return result.toString();
	}

	private String getPageContent(String url) throws Exception {

		HttpGet request = new HttpGet(url);

		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		request.setHeader("Accept-Language", "en-US,en;q=0.5");

		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();

		log.info("\nSending 'GET' request to URL : " + url);
		log.info("Response Code : " + responseCode);

		BufferedReader rd = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		// set cookies
		setCookies(response.getFirstHeader("Set-Cookie") == null ? ""
				: response.getFirstHeader("Set-Cookie").toString());

		log.info(result.toString());

		return result.toString();

	}

	private void downloadFile(String fileName) throws Exception {

		HttpGet request = new HttpGet(
				"https://www.fitbit.com/premium/export/download/" + fileName);

		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		request.setHeader("Accept-Language", "en-US,en;q=0.5");

		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();

		log.info("\nSending 'GET' request to file : " + fileName);
		log.info("Response Code : " + responseCode);

		InputStream rd = response.getEntity().getContent();
		String outFilename = String.format("fitbit_export_%1$tY%1$tm%1$td.xls",
				endDate);
		try (FileOutputStream fos = new FileOutputStream(outFilename)) {
			byte[] bytesArray = new byte[4096];
			int bytesRead = -1;
			while ((bytesRead = rd.read(bytesArray)) != -1) {
				fos.write(bytesArray, 0, bytesRead);
			}
		} catch (Exception e) {
			log.error("Failed to write:" + outFilename, e);
		}
		// set cookies
		setCookies(response.getFirstHeader("Set-Cookie") == null ? ""
				: response.getFirstHeader("Set-Cookie").toString());

	}

	public List<NameValuePair> getFormParams(String html, String formName)
			throws UnsupportedEncodingException {

		log.info("Extracting form's data...");

		Document doc = Jsoup.parse(html);

		// Google form id
		Element loginform = doc.getElementById(formName);
		Elements inputElements = loginform.getElementsByTag("input");

		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		HashSet<String> done = new HashSet<String>();
		for (Element inputElement : inputElements) {
			String key = inputElement.attr("name");
			String value = inputElement.attr("value");

			if (!done.contains(key)) {
				if (key.equals("email")) {
					value = email;
					paramList.add(new BasicNameValuePair(key, value));
				} else if (key.equals("password")) {
					value = password;
					paramList.add(new BasicNameValuePair(key, value));
				} else if (key.equals("export")) {
					paramList.add(new BasicNameValuePair(key, "true"));
				} else if (key.equals("dataPeriod.periodType")) {
					paramList.add(new BasicNameValuePair(key, "CUSTOM"));
				} else if (key.equals("startDate")) {
					paramList.add(new BasicNameValuePair(key, String.format(
							"%1$tY-%1$tm-%1$td", startDate)));
				} else if (key.equals("endDate")) {
					paramList.add(new BasicNameValuePair(key, String.format(
							"%1$tY-%1$tm-%1$td", endDate)));
				} else if (key.equals("fileFormat")) {
					paramList.add(new BasicNameValuePair(key, "XLS"));
				} else if (key.equals("dataExportType")) {
					// // for (String s : options) {
					// // paramList.add(new BasicNameValuePair("dataExportType",
					// // s));
					// // }
					paramList.add(new BasicNameValuePair(key, value));
					// paramList.add(new BasicNameValuePair(key, "BODY"));
				} else if (key.equals("_sourcePage")) {
					paramList.add(new BasicNameValuePair(key, value));
				} else if (key.equals("__fp")) {
					paramList.add(new BasicNameValuePair(key, value));
				} else if ("loginForm".equals(formName)) {
					paramList.add(new BasicNameValuePair(key, value));
				}
				done.add(key);
			} else {
				if (key.equals("dataExportType")) {
					paramList.add(new BasicNameValuePair(key, value));
				}
			}
		}

		return paramList;
	}

	public String getCookies() {
		return cookies;
	}

	public void setCookies(String cookies) {
		this.cookies = cookies;
	}

	public static void main(String[] args) throws Exception {

		// make sure cookies is turn on
		CookieHandler.setDefault(new CookieManager());

		FitbitExport http = new FitbitExport();

		http.go(args);
	}

}