package net.sllmdilab.dordriver.generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sllmdilab.commons.util.Constants;
import net.sllmdilab.commons.util.T5FHIRUtils;
import net.sllmdilab.dordriver.exeptions.DorDriverException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hl7MessageTemplateFiller {

	private static final String SAMPLES_PREFIX = "SAMPLES_";
	private static final String RANGE_LOW_PREFIX = "RANGE_LOW_";
	private static final String RANGE_HIGH_PREFIX = "RANGE_HIGH_";
	private static final String RATE_PREFIX = "RATE_";
	private static final String TIMESTAMP_KEY = "TIMESTAMP";
	private static final String START_TIME_KEY = "START_TIME";
	private static final String END_TIME_KEY = "END_TIME";
	private static final String DEVICE_ID_KEY = "DEVICE_ID";
	private String tmplPath;
	private String tmpl;
	private String dstPath = (new File("HL7_WF_filled.hl7")).getAbsolutePath();
	private String dataPath;
	private Double sampleRate = 128.0; // in Hz
	private long msgTimeFrame = 3000; // in millis
	private Double pulseRate = 70.0; // in bpm
	private String deviceId = "C1007-123";
	private String startTime = "20150617120000.000";
	private WaveFormDataFactory wfFactory;
	private Pattern placeholderPattern = Pattern.compile("<(\\w+?)>");

	private static final Logger logger = LoggerFactory.getLogger(Hl7MessageTemplateFiller.class);

	public static void main(String[] args) throws IOException, ParseException {
		Options options = new Options();
		options.addOption("p", "pulse-rate", true, "Pulse rate in BPM");
		options.addOption("f", "sample-rate", true, "Sample rate");
		options.addOption("t", "msg-time-frame", true,
				"Time frame of messages in milliseconds. The data provided will be divided into multiple messages of this time frame.");
		options.addOption("s", "src", true, "Path of the template");
		options.addOption("d", "dst", true, "Path of the generated hl7 message file");
		options.addOption("o", "data", true, "Path of the data file that should be used to fill the template.");

		Hl7MessageTemplateFiller filler = new Hl7MessageTemplateFiller();
		filler.populateFromCommandLine(options, args);
		String filledTmpl = filler.loadAndFillTemplate();
		filler.saveToFile(filledTmpl, filler.dstPath);

	}

	public Hl7MessageTemplateFiller() {
		wfFactory = new WaveFormDataFactory();
	}

	private void populateFromCommandLine(Options options, String[] args) throws ParseException {
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		if (cmd.hasOption("pulse-rate")) {
			this.pulseRate = Double.parseDouble(cmd.getOptionValue("pulse-rate"));
		} else {
			System.out.println("Pulse Rate not specified. Falling back to default: " + pulseRate);
		}
		if (cmd.hasOption("sample-rate")) {
			this.sampleRate = Double.parseDouble(cmd.getOptionValue("sample-rate"));
		} else {
			System.out.println("Sample Rate not specified. Falling back to default: " + sampleRate);
		}
		if (cmd.hasOption("msg-time-frame")) {
			this.msgTimeFrame = Long.parseLong(cmd.getOptionValue("msg-time-frame"));
		} else {
			System.out.println("Time Frame not specified. Falling back to default: " + msgTimeFrame + "ms");
		}
		if (cmd.hasOption("src")) {
			this.tmplPath = (new File(cmd.getOptionValue("src"))).getAbsolutePath();
		} else {
			throw new ParseException("No template file was specified. Please specify template using the --src flag");
		}
		if (cmd.hasOption("dst")) {
			this.dstPath = cmd.getOptionValue("dst");
		} else {
			System.out.println("Destination Path not specified. Falling back to default: " + dstPath);
		}
		if (cmd.hasOption("data")) {
			this.dataPath = cmd.getOptionValue("data");
		} else {
			System.out.println("Data file not specified. Using mathematical model instead.");
		}
	}

	public String loadAndFillTemplate() throws IOException {
		this.tmpl = loadTemplate(tmplPath);
		List<Map<String, String>> msgsData;
		if (dataPath == null) {
			msgsData = Arrays.asList(createWaveFormData());
		} else {
			msgsData = loadWaveFormData(dataPath);
		}
		List<String> filledMsgs = msgsData.stream().map(data -> fillTemplate(tmpl, data)).collect(Collectors.toList());
		return StringUtils.join(filledMsgs, "\n");
	}

	public String loadTemplate(String pathStr) throws IOException {
		try (FileReader fr = new FileReader(pathStr); BufferedReader br = new BufferedReader(fr);) {
			StringBuffer sb = new StringBuffer();
			String line = "";
			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			return sb.toString();
		}
	}

	public Map<String, String> createWaveFormData() {
		WaveFormData ecg1 = wfFactory.createRandomWaveFormData(WaveFormType.ECG1, sampleRate, msgTimeFrame, pulseRate,
				WaveFormModel.SIN);
		WaveFormData ecg2 = wfFactory.createRandomWaveFormData(WaveFormType.ECG2, sampleRate, msgTimeFrame, pulseRate,
				WaveFormModel.SIN);
		WaveFormData ecg3 = wfFactory.createRandomWaveFormData(WaveFormType.ECG3, sampleRate, msgTimeFrame, pulseRate,
				WaveFormModel.SIN);
		WaveFormData sat = wfFactory.createRandomWaveFormData(WaveFormType.SAT, sampleRate, msgTimeFrame, pulseRate,
				WaveFormModel.SIN);
		return setupKeyReplacements(ecg1, ecg2, ecg3, sat);
	}

	private void addDataFromLine(String line, List<String> keys, Map<String, List<Double>> dataMap) {
		String[] data = line.split(",");

		// Handle column 0 (timestamps) separately
		String timeStampStr = StringUtils.strip(data[0], "'");

		DateFormat df = new SimpleDateFormat(Constants.ISO_DATE_FORMAT);
		long time;
		try {
			time = df.parse(timeStampStr).toInstant().toEpochMilli();
			if (!dataMap.containsKey(keys.get(0))) {
				dataMap.put(keys.get(0), new ArrayList<>());
			}
			dataMap.get(keys.get(0)).add(1.0 * time);
		} catch (java.text.ParseException e) {
			logger.error("Datetime string '" + timeStampStr + "' could not be parsed to a date with format "
					+ Constants.ISO_DATE_FORMAT);
			throw new DorDriverException(e);
		}

		for (int idx = 1; idx < data.length; idx++) {
			if (!dataMap.containsKey(keys.get(idx))) {
				dataMap.put(keys.get(idx), new ArrayList<>());
			}
			dataMap.get(keys.get(idx)).add(Double.parseDouble(StringUtils.strip(data[idx], "'")));
		}
	}

	public List<Map<String, String>> loadWaveFormData(String dataPath) throws IOException {
		List<Map<String, String>> allMsgsReplacements = new ArrayList<>();
		Map<String, List<Double>> fullDataMap = new HashMap<>();
		try (FileReader fr = new FileReader(dataPath); BufferedReader br = new BufferedReader(fr)) {
			// First line, Keys
			String line = br.readLine();
			List<String> keys = Stream.of(line.split(",")).map(s -> StringUtils.strip(s, "'"))
					.collect(Collectors.toList());

			// Remaining lines, Data
			while ((line = br.readLine()) != null) {
				addDataFromLine(line, keys, fullDataMap);
			}

			parseParametricValues(fullDataMap);

			List<Map<String, List<Double>>> splittedDataMaps = splitToMultipleMessages(fullDataMap);

			int idx = 0;
			for (Map<String, List<Double>> dataMap : splittedDataMaps) {
				Map<String, String> singleMsgReplacements = getGeneralParams();
				singleMsgReplacements.putAll(getTimeParams(idx++));
				singleMsgReplacements.putAll(convertToPlaceholderReplacementMap(dataMap));
				allMsgsReplacements.add(singleMsgReplacements);
			}
		}
		return allMsgsReplacements;
	}

	private void parseParametricValues(Map<String, List<Double>> fullDataMap) {
		for (String key : fullDataMap.keySet()) {
			// Use the first sample rate encountered as a global sample rate
			if (key.startsWith(RATE_PREFIX)) {
				this.sampleRate = fullDataMap.get(key).get(0);
				break;
			}
		}
	}

	private List<Map<String, List<Double>>> splitToMultipleMessages(Map<String, List<Double>> fullDataMap) {
		List<Map<String, List<Double>>> splittedDataMaps = new ArrayList<>();

		Map<String, List<Double>> prevDataMap = new HashMap<>();

		// Timestamps exist for all data points
		int longestListSize = fullDataMap.get(TIMESTAMP_KEY).size();
		int valuesPerMsg = (int) (this.sampleRate * this.msgTimeFrame * 0.001);
		int nrOfFullMsgs = Math.max(1, longestListSize / valuesPerMsg);
		int i = 0;
		for (int ctr = 0; ctr < nrOfFullMsgs; ctr++) {
			Map<String, List<Double>> subDataMap = getNextDataMap(fullDataMap, prevDataMap, valuesPerMsg, i);
			splittedDataMaps.add(subDataMap);
			prevDataMap = subDataMap;
			i += valuesPerMsg;
		}

		return splittedDataMaps;
	}

	private Map<String, List<Double>> getNextDataMap(Map<String, List<Double>> fullDataMap,
			Map<String, List<Double>> prevDataMap, int valuesPerMsg, int msgIdx) {
		Map<String, List<Double>> subDataMap = new HashMap<>();
		Set<String> keySet = fullDataMap.keySet();
		for (String key : keySet) {
			if (fullDataMap.get(key).size() < msgIdx) {
				// Use previous map
				subDataMap.put(key, prevDataMap.get(key));
			} else {
				subDataMap.put(
						key,
						fullDataMap.get(key).subList(msgIdx,
								msgIdx + Math.min(valuesPerMsg, fullDataMap.get(key).size())));
			}
		}
		return subDataMap;
	}

	private Map<String, String> convertToPlaceholderReplacementMap(Map<String, List<Double>> dataMap) {
		Map<String, String> replacementMap = new HashMap<>();
		Set<String> keySet = dataMap.keySet();
		for (String key : keySet) {
			replacementMap.putAll(addCalculatedValues(key, keySet, dataMap));
			replacementMap.put(key, StringUtils.join(dataMap.get(key), '^'));
		}
		return replacementMap;
	}

	private Map<String, String> addCalculatedValues(String key, Set<String> keySet, Map<String, List<Double>> dataMap) {
		Map<String, String> rMap = new HashMap<>();
		if (key.startsWith(SAMPLES_PREFIX)) {
			String dataType = key.split("_")[1];
			// Calculate data ranges from samples
			if (!keySet.contains(RANGE_LOW_PREFIX + dataType)) {
				rMap.put(RANGE_LOW_PREFIX + dataType, Collections.min(dataMap.get(key)).toString());
			}
			if (!keySet.contains(RANGE_HIGH_PREFIX + dataType)) {
				rMap.put(RANGE_HIGH_PREFIX + dataType, Collections.max(dataMap.get(key)).toString());
			}
			// Calculate sample frequency
			if (!keySet.contains(RATE_PREFIX + dataType)) {
				Double minTimestamp = Collections.min(dataMap.get(TIMESTAMP_KEY));
				Double maxTimestamp = Collections.max(dataMap.get(TIMESTAMP_KEY));
				Double sampleRate = (dataMap.get(key).size() - 1) / (maxTimestamp - minTimestamp) * 1000;
				rMap.put(RATE_PREFIX + dataType, Double.toString(sampleRate));
			}
		}
		return rMap;
	}

	private Map<String, String> getGeneralParams() {
		Map<String, String> map = new HashMap<>();
		map.put(DEVICE_ID_KEY, deviceId);
		return map;
	}

	private Map<String, String> getTimeParams(int msgIndex) {
		Map<String, String> map = new HashMap<>();
		Instant startDate = T5FHIRUtils.convertHL7DateTypeToDate(this.startTime).toInstant();
		startDate = startDate.plusMillis(this.msgTimeFrame * msgIndex);
		Instant endDate = startDate.plusMillis(this.msgTimeFrame);
		map.put(START_TIME_KEY, T5FHIRUtils.convertDateToHL7Type(Date.from(startDate)));
		map.put(END_TIME_KEY, T5FHIRUtils.convertDateToHL7Type(Date.from(endDate)));
		return map;
	}

	public Map<String, String> setupKeyReplacements(AbstractWaveFormData... wfData) {
		// General parameters
		Map<String, String> keyRepl = getGeneralParams();
		keyRepl.putAll(getTimeParams(0));

		for (AbstractWaveFormData wf : wfData) {
			keyRepl.putAll(wf.toMap());
		}
		return keyRepl;
	}

	public String fillTemplate(String tmpl, Map<String, String> values) {
		Set<String> placeholders = findPlaceholders(tmpl);
		for (String holder : placeholders) {
			if (values.containsKey(holder)) {
				tmpl = tmpl.replace("<" + holder + ">", values.get(holder));
			} else {
				throw new RuntimeException("The place holder '" + holder + "' is not specified in the data file.");
			}
		}
		return tmpl;
	}

	public Set<String> findPlaceholders(String tmpl) {
		Set<String> placeholders = new HashSet<>();
		Matcher matcher = placeholderPattern.matcher(tmpl);
		while (matcher.find()) {
			placeholders.add(matcher.group(1));
		}
		return placeholders;
	}

	private void saveToFile(String strToSave, String dst) throws IOException {
		Files.write(Paths.get(dst), strToSave.getBytes());
		logger.info("HL7 message saved at '" + dst);
	}

	public String getTmplPath() {
		return tmplPath;
	}

	public void setTmplPath(String tmplPath) {
		this.tmplPath = tmplPath;
	}

	public String getTmpl() {
		return tmpl;
	}

	public void setTmpl(String tmpl) {
		this.tmpl = tmpl;
	}

	public String getDstPath() {
		return dstPath;
	}

	public void setDstPath(String dstPath) {
		this.dstPath = dstPath;
	}

	public void setDataPath(String dataPath) {
		this.dataPath = dataPath;
	}

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
}
