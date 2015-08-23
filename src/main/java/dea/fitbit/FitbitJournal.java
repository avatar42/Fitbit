package dea.fitbit;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FitbitJournal {
	protected final Logger log = LoggerFactory.getLogger(getClass());
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");

	private String cookies;
	private HttpClient client = HttpClientBuilder.create().build();
	private final String USER_AGENT = "Mozilla/5.0";
	private String loginUrl = "https://www.fitbit.com/login";
	private String journalUrl = "https://www.fitbit.com/journal/";
	private GregorianCalendar startDate;
	private GregorianCalendar endDate;
	private String outPath = "My Daily Journal on Fitbit.csv";
	private String email;
	private String password;
	private String header = "Date,Comment,mood,allergies\n";
	private HtmlToPlainText formatter = new HtmlToPlainText();

	private void writeRecord(FileWriter writer, String html) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%1$tm/%1$td/%1$tY,", startDate));
		Document doc = Jsoup.parse(html);

		Element journalRecords = doc.getElementById("journalRecords");
		String plainText = formatter.getPlainText(journalRecords);
		sb.append("\""
				+ plainText
						.replace('\n', '|')
						.replace("Click to delete  <> Click to edit  <> ", "")
						.replace("| visible to only myself ", "")
						.replace(
								"||Write a comment...  visible to  only myself  only my friends  everyone or |Cancel <> ",
								"") + "\",");

		Element mood = doc.getElementById("mood");
		sb.append(mood.siblingIndex() + ",");

		Element allergy = doc.getElementById("allergy");
		sb.append(allergy.siblingIndex());

		sb.append("\n");
		log.debug("Wrote:" + sb);
		writer.append(sb);
		writer.flush();
	}

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
		endDate = new GregorianCalendar();

		String page = getPageContent(loginUrl);

		List<NameValuePair> postParams = getFormParams(page);

		try (FileWriter writer = new FileWriter(outPath)) {

			writer.append(header);
			while (startDate.before(endDate)) {

				sendPost(loginUrl, postParams);
				String url = journalUrl
						+ String.format("%1$tY/%1$tm/%1$td", startDate);
				String result = getPageContent(url);
				writeRecord(writer, result);
				startDate.add(Calendar.DAY_OF_MONTH, 1);

				client = HttpClientBuilder.create().build();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		System.out.println("Done");

	}

	private void sendPost(String postUrl, List<NameValuePair> postParams)
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
		post.setHeader("Referer", loginUrl);
		post.setHeader("Content-Type", "application/x-www-form-urlencoded");

		post.setEntity(new UrlEncodedFormEntity(postParams));

		HttpResponse response = client.execute(post);

		int responseCode = response.getStatusLine().getStatusCode();

		System.out.println("\nSending 'POST' request to URL : " + postUrl);
		System.out.println("Post parameters : " + postParams);
		System.out.println("Response Code : " + responseCode);

		BufferedReader rd = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		post.reset();

		System.out.println(result.toString());

	}

	private String getPageContent(String url) throws Exception {

		HttpGet request = new HttpGet(url);

		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		request.setHeader("Accept-Language", "en-US,en;q=0.5");

		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();

		System.out.println("\nSending 'GET' request to URL : " + url);
		System.out.println("Response Code : " + responseCode);

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

		System.out.println(result);

		return result.toString();

	}

	public List<NameValuePair> getFormParams(String html)
			throws UnsupportedEncodingException {

		System.out.println("Extracting form's data...");

		Document doc = Jsoup.parse(html);

		// Google form id
		Element loginform = doc.getElementById("loginForm");
		Elements inputElements = loginform.getElementsByTag("input");

		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		for (Element inputElement : inputElements) {
			String key = inputElement.attr("name");
			String value = inputElement.attr("value");

			if (key.equals("email"))
				value = email;
			else if (key.equals("password"))
				value = password;

			paramList.add(new BasicNameValuePair(key, value));

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

		FitbitJournal http = new FitbitJournal();

		http.go(args);
	}

}