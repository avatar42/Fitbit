package dea.myFitnessPal;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.examples.HtmlToPlainText;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads MyFitnessPal food logs to a csv file USAGE:MyFitnessPalFoodExport
 * userName password oldestDate [newestDate]
 * Example:USAGE:MyFitnessPalFoodExport bob1980 P@$$w0rd 7/10/2015 7/30/2015
 * would get all of bob1980's food logs from 7/10/2015 to 7/29/2015 if 7/30/2015
 * had been left off it would get up to today
 * 
 * @author dea
 * 
 */
public class MyFitnessPalFoodExport {
	protected final Logger log = LoggerFactory.getLogger(getClass());
	private static final SimpleDateFormat logDateFormat = new SimpleDateFormat(
			"MM/dd/yyyy");

	private String cookies;
	private HttpClient client = HttpClientBuilder.create().build();
	private final String USER_AGENT = "Mozilla/5.0";
	private String loginUrl = "https://www.myfitnesspal.com/account/login";
	private String loginForm = "fancy_login";
	private String journalUrl = "http://www.myfitnesspal.com/food/diary?date=";
	private GregorianCalendar startDate;
	private GregorianCalendar endDate;
	private String outPath = "MyFitnessPalFood.csv";
	private String username;
	private String password;
	private String header = "\"Date\",\"Meal\",\"Food\",\"Calories\",\"Carbs\",\"Fat\",\"Protein\",\"Sodium\",\"Sugar\"\n";
	private HtmlToPlainText formatter = new HtmlToPlainText();

	private void writeRecord(FileWriter writer, String html) throws IOException {
		Document doc = Jsoup.parse(html);
		String meal = "";
		Elements journalRecords = doc.getElementsByClass("food_container");
		if (journalRecords != null) {
			Elements rows = journalRecords.first().getElementsByTag("tr");
			for (Element row : rows) {
				if (row.hasClass("meal_header")) {
					Element cell = row.getElementsByTag("td").first();
					meal = formatter.getPlainText(cell);
				} else if (row.hasClass("bottom") || row.hasClass("spacer")) {
					// ignore options row
				} else {
					if (row.hasClass("total")) {
						meal = "Totals";
					}
					StringBuilder sb = new StringBuilder();
					sb.append(String.format("%1$tm/%1$td/%1$tY", startDate));
					sb.append(",\"").append(meal).append("\"");
					Elements cells = row.getElementsByTag("td");
					for (Element cell : cells) {
						if (!cell.hasClass("delete")) {
							sb.append(",");

							String plainText = formatter.getPlainText(cell);
							try {
								int v = Integer.parseInt(plainText);
								sb.append(v);
							} catch (NumberFormatException e) {
								sb.append("\"")
										.append(plainText.replace("<>", ""))
										.append("\"");
							}
						}
					}
					sb.append("\n");
					log.debug("Wrote:" + sb);
					writer.append(sb);
					writer.flush();
				}
			}
		} else {
			log.info("No food data for:" + String.format("%1$tm/%1$td/%1$tY"));
		}

		Element waterRecord = doc.getElementById("water_cups");
		if (waterRecord != null) {
			String plainText = formatter.getPlainText(waterRecord);
			if (plainText != null) {
				int start = plainText.indexOf("Up <> ");
				if (start > -1) {
					start += 6;
					int end = plainText.indexOf(" Down <>", start);
					if (end > -1) {
						StringBuilder sb = new StringBuilder();
						sb.append(String.format("%1$tm/%1$td/%1$tY", startDate));
						sb.append(",\"Water\"");
						sb.append(",\"")
								.append(plainText.substring(start, end))
								.append("\"");
						sb.append("\n");
						log.debug("Wrote:" + sb);
						writer.append(sb);
						writer.flush();
					}
				}
			}
		}
		Element notes = doc.getElementById("notes");
		if (notes != null) {
			Elements noteRecords = doc.getElementsByClass("note");
			if (noteRecords != null) {
				Element n = noteRecords.first();
				String plainText = formatter.getPlainText(n);
				if (plainText != null) {
					StringBuilder sb = new StringBuilder();
					sb.append(String.format("%1$tm/%1$td/%1$tY", startDate));
					sb.append(",\"Notes\"");
					sb.append(",\"").append(plainText).append("\"");
					sb.append("\n");
					log.debug("Wrote:" + sb);
					writer.append(sb);
					writer.flush();
				}
			}
		}
	}

	public void go(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("USAGE:" + getClass().getName()
					+ " userName password oldestDate [newestDate]");
			System.err.println("Example:" + getClass().getName()
					+ " bob1980 P@$$w0rd 7/10/2015 7/30/2015");
			return;
		}
		username = args[0];
		password = args[1];
		Date d = logDateFormat.parse(args[2]);
		Calendar cal = Calendar.getInstance();
		cal.setTime(d);

		startDate = new GregorianCalendar(cal.get(Calendar.YEAR),
				cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
		if (args.length == 4) {
			Date ed = logDateFormat.parse(args[3]);
			Calendar ecal = Calendar.getInstance();
			ecal.setTime(ed);

			endDate = new GregorianCalendar(ecal.get(Calendar.YEAR),
					ecal.get(Calendar.MONTH), ecal.get(Calendar.DAY_OF_MONTH));

		} else {
			endDate = new GregorianCalendar();
		}
		String page = getPageContent(loginUrl);

		List<NameValuePair> postParams = getFormParams(page, loginForm);

		try (FileWriter writer = new FileWriter(outPath)) {

			writer.append(header);
			while (startDate.before(endDate)) {

				sendPost(loginUrl, postParams);
				String url = journalUrl
						+ String.format("%1$tY-%1$tm-%1$td", startDate);
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

	/**
	 * Prints headers at info log level
	 */
	protected void checkHeaders(HttpMessage request) throws ParseException {
		log.info(request.getClass().getSimpleName() + ":Headers:");
		Header[] respHeaders = request.getAllHeaders();
		for (Header header : respHeaders) {
			log.debug(header.getName() + ":" + header.getValue());
		}
	}

	private void initHeaders(AbstractHttpMessage post)
			throws MalformedURLException {
		URL url = new URL(loginUrl);
		// add header
		post.setHeader("Host", url.getHost());
		post.setHeader("User-Agent", USER_AGENT);
		post.setHeader("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		post.setHeader("Accept-Language", "en-US,en;q=0.5");
		post.setHeader("Cookie", getCookies());
		post.setHeader("Connection", "keep-alive");
		post.setHeader("Referer", loginUrl);
		post.setHeader("Upgrade-Insecure-Requests", "1");

	}

	private void sendPost(String postUrl, List<NameValuePair> postParams)
			throws Exception {

		HttpPost post = new HttpPost(postUrl);

		initHeaders(post);

		post.setHeader("Content-Type", "application/x-www-form-urlencoded");

		post.setEntity(new UrlEncodedFormEntity(postParams));

		HttpResponse response = client.execute(post);

		log.info("\nSending 'POST' request to URL : " + postUrl);
		log.info("Post parameters : " + postParams);
		checkHeaders(post);
		int responseCode = response.getStatusLine().getStatusCode();

		log.info("Response Code : " + responseCode);
		checkHeaders(response);

		BufferedReader rd = new BufferedReader(new InputStreamReader(response
				.getEntity().getContent()));

		StringBuffer result = new StringBuffer();
		String line = "";
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}

		post.reset();

		log.info(result.toString());

	}

	private String getPageContent(String url) throws Exception {

		HttpGet request = new HttpGet(url);

		initHeaders(request);

		log.info("\nSending 'GET' request to URL : " + url);
		checkHeaders(request);
		HttpResponse response = client.execute(request);
		int responseCode = response.getStatusLine().getStatusCode();

		log.info("Response Code : " + responseCode);
		checkHeaders(response);

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

	public List<NameValuePair> getFormParams(String html, String formName)
			throws UnsupportedEncodingException {

		log.info("Extracting form's data...");

		Document doc = Jsoup.parse(html);

		Element loginform = doc.getElementById(formName);
		Elements inputElements = loginform.getElementsByTag("input");

		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		for (Element inputElement : inputElements) {
			String key = inputElement.attr("name");
			String value = inputElement.attr("value");

			if (key.equals("email"))
				value = username;
			else if (key.equals("username"))
				value = username;
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

		MyFitnessPalFoodExport http = new MyFitnessPalFoodExport();

		http.go(args);
	}

}