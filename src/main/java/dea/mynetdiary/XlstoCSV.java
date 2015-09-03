package dea.mynetdiary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.jsoup.helper.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for converting MyFitnessDiary exports to importable csv
 * 
 * @author dea
 * 
 */
public class XlstoCSV {
	protected final Logger log = LoggerFactory.getLogger(getClass());
	public static final int COLUMN_DATE = 0;
	public static final int COLUMN_WEE = 1;
	public static final int COLUMN_BREAKFAST = 2;
	public static final int COLUMN_MORNING = 3;
	public static final int COLUMN_LUNCH = 4;
	public static final int COLUMN_AFTERNOON = 5;
	public static final int COLUMN_DINNER = 6;
	public static final int COLUMN_LATE = 7;

	public static final String SOURCE_STR = "Source: ";
	public static final String WEIGHT_LABEL = "Weight:";
	public static final String COMMENTS_STR = "Comments";

	public static final char SPACE_CHAR = (char) 32;

	private static final SimpleDateFormat InDateFormat = new SimpleDateFormat(
			"EEE MM/dd/yyyy");
	private static final SimpleDateFormat outDateFormat = new SimpleDateFormat(
			"MM/dd/yyyy HH:mm:ss");

	protected ResourceBundle bundle;
	private HashMap<String, Integer> trackerList = new HashMap<String, Integer>();

	private HashSet<String> missing = new HashSet<String>();

	private String header;
	private String bpLabel;

	public XlstoCSV() {
		bundle = ResourceBundle.getBundle("myNetDiary");
		bpLabel = getBundleVal(String.class, "BP.label", "BP");
		@SuppressWarnings("unchecked")
		ArrayList<String> l = getBundleVal(ArrayList.class, "trackers",
				new ArrayList<String>());
		StringBuffer data = new StringBuffer();
		data.append("\"Recorded\",\"Source\"");
		int i = 0;
		// force comments to last column
		for (String n : l) {
			if (!COMMENTS_STR.equals(n)) {
				trackerList.put(n, i++);
				data.append(",\"").append(n).append("\"");
			}
		}
		trackerList.put(COMMENTS_STR, i);
		data.append(",\"").append(COMMENTS_STR).append("\"\n");
		header = data.toString();
	}

	/**
	 * Get value from bundle. Supported types are Integer, Long, Boolean or
	 * ArrayList<String>
	 * 
	 * @param asClass
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T> T getBundleVal(Class<T> asClass, String key, T defaultValue) {
		if (bundle.containsKey(key)) {
			try {
				if (Integer.class.isAssignableFrom(asClass))
					return (T) new Integer(bundle.getString(key));

				if (Long.class.isAssignableFrom(asClass))
					return (T) new Long(bundle.getString(key));

				if (Boolean.class.isAssignableFrom(asClass))
					return (T) new Boolean(bundle.getString(key));

				if (String.class.isAssignableFrom(asClass))
					return (T) bundle.getString(key);

				if (ArrayList.class.isAssignableFrom(asClass)) {
					String tmp = bundle.getString(key);
					ArrayList<String> rtn = new ArrayList<String>();
					if (tmp != null) {
						StringTokenizer st = new StringTokenizer(tmp, ",");
						while (st.hasMoreTokens()) {
							rtn.add(st.nextToken().trim());
						}
					}
					return (T) rtn;
				}

				if (HashMap.class.isAssignableFrom(asClass)) {
					String tmp = bundle.getString(key);
					HashMap<String, Integer> rtn = new HashMap<String, Integer>();
					if (tmp != null) {
						StringTokenizer st = new StringTokenizer(tmp, ",");
						int i = 0;
						while (st.hasMoreTokens()) {
							rtn.put(st.nextToken().trim(), i++);
						}
					}
					return (T) rtn;
				}

			} catch (Exception e) {
				log.error("Failed to parse " + key + ":"
						+ bundle.getString(key));
			}
		}
		log.warn("Using default value for " + key + ":" + defaultValue);
		return (T) defaultValue;
	}

	private void logDebug(String s) {
		StringBuilder sb = new StringBuilder();
		for (int c = 0; c < s.length(); c++) {
			sb.append((int) s.charAt(c)).append(',');
		}
		log.debug(sb.toString());

	}

	private Date addTimeToDate(Date baseDate, int hour, int minutes) {
		Calendar c = Calendar.getInstance();
		c.setTime(baseDate);
		c.set(Calendar.HOUR, hour);
		c.set(Calendar.MINUTE, minutes);
		return c.getTime();

	}

	private Record parseRecord(String s, Date baseDate, int column)
			throws UnsupportedEncodingException {

		Record rtn = new Record();
		int start = 0;
		int end = 0;
		try {
			end = s.indexOf(':');
			int hour = Integer.parseInt(s.substring(start, end).trim());
			start = end + 1;
			end = s.indexOf(SPACE_CHAR, start);
			int minutes = Integer.parseInt(s.substring(start, end));
			rtn.recorded = addTimeToDate(baseDate, hour, minutes);
		} catch (Exception e) {
			// for some reason weight has no timestamp
			if (s.contains(WEIGHT_LABEL)) {
				rtn.recorded = baseDate;
				end = -1;
				switch (column) {
				case COLUMN_WEE:
					rtn.recorded = addTimeToDate(baseDate, 3, 0);
					break;
				case COLUMN_BREAKFAST:
					rtn.recorded = addTimeToDate(baseDate, 6, 0);
					break;
				case COLUMN_MORNING:
					rtn.recorded = addTimeToDate(baseDate, 9, 0);
					break;
				case COLUMN_LUNCH:
					rtn.recorded = addTimeToDate(baseDate, 12, 0);
					break;
				case COLUMN_AFTERNOON:
					rtn.recorded = addTimeToDate(baseDate, 15, 0);
					break;
				case COLUMN_DINNER:
					rtn.recorded = addTimeToDate(baseDate, 18, 0);
					break;
				case COLUMN_LATE:
					rtn.recorded = addTimeToDate(baseDate, 23, 0);
					break;

				}
			} else {
				log.error("Could not parse time from:" + s, e);
				logDebug(s);
			}
		}

		try {
			start = end + 1;
			end = s.indexOf(':', start);
			rtn.name = StringEscapeUtils.unescapeHtml4(s.substring(start, end))
					.trim();
			rtn.isBP = bpLabel.equals(rtn.name);
		} catch (Exception e) {
			log.error("Could not parse name from between " + start + " and "
					+ end + " of " + s);
			logDebug(s);
		}

		int nameEnd = end;
		int i = nameEnd;
		// some no not have a number value, only a comment
		start = 0;
		end = 0;
		for (; i < s.length(); i++) {
			if (start == 0 && Character.isDigit(s.charAt(i))) {
				start = i;
			} else if (s.charAt(i) == '.') {
				// count as digit
			} else if (s.charAt(i) == '/') {
				// ignore for now
			} else if (start > 0 && end == 0 && !Character.isDigit(s.charAt(i))
					&& s.charAt(i) != '/') {
				end = i;
				break;
			}
		}
		// TODO: need special handling of BP ###/##
		if (rtn.isBP) {
			rtn.comment = s.substring(start, end);
		} else if (start > 0 && end > 0) {
			rtn.value = Float.parseFloat(s.substring(start, end));
		} else if (COMMENTS_STR.equals(rtn.name)) {
			i = nameEnd;
		} else {
			log.error("Could not parse value from between " + start + " and "
					+ end + " of " + s);
			logDebug(s);
			i = nameEnd;
		}
		for (; i < s.length(); i++) {
			if (StringUtil.isWhitespace(s.charAt(i))) {
				break;
			}
		}
		i++;
		String tmp;
		try {
			tmp = StringEscapeUtils.unescapeHtml4(s.substring(i));
		} catch (Exception e) {
			log.warn("URL decode failed, data might have issues:" + s);
			tmp = s.substring(i);
		}
		if (tmp.startsWith(SOURCE_STR)) {
			rtn.source = tmp.substring(SOURCE_STR.length());
		} else if (!rtn.isBP) {
			// a bit of special processing here to decode newlines from download
			// tool
			rtn.comment = tmp.replace('|', '\n');
		}

		return rtn;
	}

	private void writeLine(FileOutputStream fos, Record r, String label,
			float value) throws IOException {
		Integer idx = trackerList.get(label);
		if (idx != null) {

			StringBuffer data = new StringBuffer();
			data.append('"').append(outDateFormat.format(r.recorded))
					.append("\",");
			data.append('"').append(r.source).append("\",");
			int i = 0;
			for (; i < idx; i++) {
				data.append(',');
			}
			if (!COMMENTS_STR.equals(r.name)) {
				data.append(value).append(",");
			}
			for (; i < trackerList.size() - 2; i++) {
				data.append(',');
			}
			data.append('"').append(r.comment).append("\"");
			data.append("\n");
			fos.write(data.toString().getBytes());
		} else {
			log.warn("Tracker:"
					+ r.name
					+ " not in trackers list in myNetDiary.properties. Will not be exported");
			missing.add(r.name);
		}

	}

	private void parseCell(FileOutputStream fos, Date d, String cellData,
			int column) throws IOException {
		if (d == null) {
			throw new IOException("Date not found for:" + cellData);
		}
		StringTokenizer st = new StringTokenizer(cellData, "\n");
		while (st.hasMoreTokens()) {
			// For storing data into CSV files
			Record r = parseRecord(st.nextToken(), d, column);
			if (r.isBP) {
				int ldi = r.name.indexOf('/');
				int vdi = r.comment.indexOf('/');
				writeLine(fos, r, r.name.substring(0, ldi),
						Float.parseFloat(r.comment.substring(0, vdi)));
				writeLine(fos, r, r.name.substring(ldi + 1),
						Float.parseFloat(r.comment.substring(vdi + 1)));
			} else {
				writeLine(fos, r, r.name, r.value);
			}
		}
	}

	/**
	 * Convert the Day Parts Report xls to a csv usable by FitnessSyncer and
	 * such
	 * 
	 * @param inputFile
	 * @param outputFile
	 */
	public void dayPartsExcelReport(File inputFile, File outputFile) {
		try (FileOutputStream fos = new FileOutputStream(outputFile);

		// Get the workbook object for XLS file
				HSSFWorkbook workbook = new HSSFWorkbook(new FileInputStream(
						inputFile));) {

			// write header of CSV
			fos.write(header.getBytes());

			// Get first sheet from the workbook
			HSSFSheet sheet = workbook.getSheetAt(0);
			Cell cell;
			Row row;

			// Iterate through each rows from first sheet
			Iterator<Row> rowIterator = sheet.iterator();
			// skip headers
			row = rowIterator.next();
			row = rowIterator.next();
			while (rowIterator.hasNext()) {
				row = rowIterator.next();
				Date d = null;
				// For each row, iterate through each columns
				Iterator<Cell> cellIterator = row.cellIterator();
				while (cellIterator.hasNext()) {
					cell = cellIterator.next();
					log.trace("" + cell.getRowIndex() + ":"
							+ cell.getColumnIndex() + ":" + cell.getCellType()
							+ ":" + cell);

					switch (cell.getCellType()) {
					case Cell.CELL_TYPE_STRING:
						String s = cell.getStringCellValue();

						if (cell.getColumnIndex() == COLUMN_DATE
								&& s.contains("/")) {
							try {
								d = InDateFormat.parse(s);
								break;
							} catch (ParseException e) {
								log.warn("Possible bad date:" + s
										+ " treating as string");
							}

						}
						parseCell(fos, d, s, cell.getColumnIndex());
						break;

					case Cell.CELL_TYPE_BLANK:
						break;

					default:
						log.warn("Ignoring unknown cell type:" + cell);
					}

				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (!missing.isEmpty()) {
			log.warn("Found the following tracker names that were not in your list in the properties file:"
					+ missing);
		}
	}

	class Record {
		Date recorded;
		String name;
		String source = "Manual";
		float value;
		String comment;
		boolean isBP;

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Record [recorded=");
			builder.append(recorded);
			builder.append(", name=");
			builder.append(name);
			builder.append(", source=");
			builder.append(source);
			builder.append(", value=");
			builder.append(value);
			builder.append(", comment=");
			builder.append(comment);
			builder.append("]");
			return builder.toString();
		}
	}

	public static void main(String[] args) {
		File inputFile = new File("dayPartsExcelReport.xls");
		File outputFile = new File("output.csv");
		XlstoCSV o = new XlstoCSV();

		o.dayPartsExcelReport(inputFile, outputFile);
	}
}