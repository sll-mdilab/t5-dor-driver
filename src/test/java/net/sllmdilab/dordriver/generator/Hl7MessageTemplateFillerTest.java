package net.sllmdilab.dordriver.generator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sllmdilab.dordriver.generator.Hl7MessageTemplateFiller;
import net.sllmdilab.dordriver.generator.WaveFormData;
import net.sllmdilab.dordriver.generator.WaveFormDataFactory;
import net.sllmdilab.dordriver.generator.WaveFormModel;
import net.sllmdilab.dordriver.generator.WaveFormType;

import org.junit.Before;
import org.junit.Test;

public class Hl7MessageTemplateFillerTest {

	private WaveFormDataFactory wfFactory;
	private String mockTmpl = "OBX|1009|NA|131331^MDC_ECG_LEAD_I^MDC|1.1.1.1003|<SAMPLES_ECG1>|266418^MDC_DIM_MILLI_VOLT^MDC||||||||<START_TIME>||<START_TIME>";
	//@formatter:off
	private String fullMockTmpl = "" 
			+ "MSH|^~\\&|SendingApplication|SendingFacility|ReceivingApplication|ReceivingFacility|20120530112345||ORU^R01^ORU_R01|9879790004|P|2.6|||NE|AL|USA|ASCII|EN^English^ISO639||IHE_PCD_ORU_R01^IHE PCD^1.3.6.1.4.1.19376.1.6.1.1.1^ISO\r"
			+ "PID|||010101-2425||Doe^John^^^^^B\r"
			+ "PV1||I|ICU^2^23\r"
			+ "OBR|2||XXX|WAVEFORM|||<START_TIME>|<END_TIME>\r"
			+ "OBX|1000||69965^MDC_DEV_MON_PHYSIO_MULTI_PARAM_MDS^MDC|1.0.0.0||x^x^x||F|||F|||||||<DEVICE_ID>\r"
			+ "OBX|1001|NA|131329^MDC_ECG_LEAD_I^MDC|1.1.1.1001|<SAMPLES_ECG1>|266418^MDC_DIM_MILLI_VOLT^MDC|||||F|||<START_TIME>\r"
			+ "OBX|1002|NM|0^MDC_ATTR_SAMP_RATE^MDC|1.1.1.1001.1|<RATE_ECG1>|264608^MDC_DIM_PER_SEC|||||F\r"
			+ "OBX|1003|NR|0^MDC_ATTR_DATA_RANGE^MDC|1.1.1.1001.2|<RANGE_LOW_ECG1>^<RANGE_HIGH_ECG1>|266418^MDC_DIM_MILLI_VOLT^MDC|||||F\r";
	//@formatter:on
	private final static String MOCK_DATA_PATH_1 = "src/test/resources/wf-mock-samples-1.csv";
	private final static String MOCK_DATA_PATH_2 = "src/test/resources/wf-mock-samples-2.csv";
	private final static String MOCK_DATA_PATH_3 = "src/test/resources/wf-mock-samples-3.csv";
	private final static String MOCK_SAMPLES_KEY = "SAMPLES_ECG1";
	private final static String MOCK_RATE_KEY = "RATE_ECG1";
	private final static String MOCK_RANGE_LOW_KEY = "RANGE_LOW_ECG1";
	private final static String MOCK_RANGE_HIGH_KEY = "RANGE_HIGH_ECG1";
	private final static Pattern HL7_VALUES_PATTERN = Pattern.compile("^([^\\|]*\\|){5}([^\\|]*)\\|");
	private final static Pattern HL7_START_TIME_PATTERN = Pattern.compile("^([^\\|]*\\|){7}([^\\|]*)\\|");
	private final static Pattern HL7_END_TIME_PATTERN = Pattern.compile("^([^\\|]*\\|){8}([^\\|]*)\\|?");

	private Hl7MessageTemplateFiller filler;

	@Before
	public void setup() {
		Hl7MessageTemplateFiller msgFiller = new Hl7MessageTemplateFiller();
		filler = spy(msgFiller);
		wfFactory = new WaveFormDataFactory();
	}

	@Test
	public void shouldReturnCorrectPlaceHolders() {
		Set<String> placeholders = filler.findPlaceholders(mockTmpl);
		Set<String> expectedPlaceholders = new HashSet<>();
		expectedPlaceholders.add("SAMPLES_ECG1");
		expectedPlaceholders.add("START_TIME");

		assertEquals(2, placeholders.size());
		assertTrue(placeholders.containsAll(expectedPlaceholders));
	}

	@Test
	public void shouldReplacePlaceholdersWithCorrectValues() {
		WaveFormData wf = wfFactory.createRandomWaveFormData(WaveFormType.ECG1, 10.0, 1000, 1.0, WaveFormModel.LINEAR);
		Map<String, String> values = filler.setupKeyReplacements(wf);
		double[] expectedOutput = { 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0 };
		// Objectify the array to go with StringUtils.join
		List<Double> dList = new ArrayList<>();
		for (double d : expectedOutput) {
			dList.add(d);
		}
		String filledTmpl = filler.fillTemplate(mockTmpl, values);
		// Extracts the content between 5th and 6th pipes
		Matcher m = HL7_VALUES_PATTERN.matcher(filledTmpl);
		String output = null;
		if (m.find()) {
			output = m.group(2);
		}
		String[] split = output.split("\\^");
		double[] actualOutput = new double[split.length];
		for (int i = 0; i < split.length; i++) {
			actualOutput[i] = Double.parseDouble(split[i]);
		}
		assertArrayEquals(expectedOutput, actualOutput, 0.0001);
	}

	@Test
	public void shouldParseFileCorrectly() throws IOException {
		List<Map<String, String>> dataMaps = filler.loadWaveFormData(MOCK_DATA_PATH_1);
		assertEquals(1, dataMaps.size());
		Map<String, String> map = dataMaps.get(0);
		double[] expectedData = { 0.160, 0.185, 0.185, 0.185, 0.185, 0.185, 0.185, 0.185, 0.185, 0.200 };
		String samples = map.get(MOCK_SAMPLES_KEY);
		String rate = map.get(MOCK_RATE_KEY);

		assertEquals(250.0, Double.parseDouble(rate), 0.01);

		List<Double> data = Stream.of(samples.split("\\^")).map(d -> Double.parseDouble(d))
				.collect(Collectors.toList());
		double[] dataArr = Stream.of(data.toArray(new Double[data.size()])).mapToDouble(Double::doubleValue).toArray();

		assertArrayEquals(expectedData, dataArr, 0.0001);
	}

	@Test
	public void shouldNotCalculateParametersIfSpecifiedInInputFile() throws Exception {
		List<Map<String, String>> dataMaps = filler.loadWaveFormData(MOCK_DATA_PATH_2);
		assertEquals(1, dataMaps.size());
		Map<String, String> map = dataMaps.get(0);

		double expectedRate = 250.0;
		double expectedHigh = 0.5;
		double expectedLow = 0.160;

		assertEquals(expectedRate, Double.parseDouble(map.get(MOCK_RATE_KEY)), 0.001);
		assertEquals(expectedLow, Double.parseDouble(map.get(MOCK_RANGE_LOW_KEY)), 0.001);
		assertEquals(expectedHigh, Double.parseDouble(map.get(MOCK_RANGE_HIGH_KEY)), 0.001);
	}

	@Test
	public void shouldProduceMulitpleMessages() throws Exception {
		doReturn(fullMockTmpl).when(filler).loadTemplate(any());
		filler.setDataPath(MOCK_DATA_PATH_3);

		String filledTmpl = filler.loadAndFillTemplate();
		List<String> msgs = splitMessages(filledTmpl);

		assertEquals(2, msgs.size());
	}

	@Test
	public void secondMessageEndTimeShouldBeEqualToFirstMessageStartTime() throws Exception {
		doReturn(fullMockTmpl).when(filler).loadTemplate(any());
		filler.setDataPath(MOCK_DATA_PATH_3);

		String filledTmpl = filler.loadAndFillTemplate();
		List<String> msgs = splitMessages(filledTmpl);

		assertEquals(2, msgs.size());

		String obr1 = getRowFromHL7Message(msgs.get(0), "OBR|2|");
		Matcher m1 = HL7_END_TIME_PATTERN.matcher(obr1);
		String endTimeMsg1 = null;
		if (m1.find()) {
			endTimeMsg1 = m1.group(2);
		}
		String obr2 = getRowFromHL7Message(msgs.get(1), "OBR|2|");
		Matcher m2 = HL7_START_TIME_PATTERN.matcher(obr2);
		String startTimeMsg2 = null;
		if (m2.find()) {
			startTimeMsg2 = m2.group(2);
		}

		assertEquals(endTimeMsg1, startTimeMsg2);
	}

	@Test
	public void secondMessageDataShouldBeContinuationOfFirstMessageData() throws Exception {
		doReturn(fullMockTmpl).when(filler).loadTemplate(any());
		filler.setDataPath(MOCK_DATA_PATH_3);

		String filledTmpl = filler.loadAndFillTemplate();
		List<String> msgs = splitMessages(filledTmpl);

		assertEquals(2, msgs.size());

		List<double[]> expectedData = new ArrayList<>();
		double[] expectedData1 = { 0.160, 0.185, 0.185, 0.185, 0.185, 0.185 };
		double[] expectedData2 = { 0.185, 0.185, 0.185, 0.200, 0.185, 0.200 };
		expectedData.add(expectedData1);
		expectedData.add(expectedData2);

		int i = 0;
		for (String msg : msgs) {
			String obx = getRowFromHL7Message(msg, "OBX|1001|");
			double[] expected = expectedData.get(i++);
			assertWaveformDataEquals(obx, expected);
		}
	}

	private List<String> splitMessages(String allMsgs) {
		return Arrays.asList(allMsgs.split("\n"));
	}

	private void assertWaveformDataEquals(String obx, double[] expected) {
		Matcher m = HL7_VALUES_PATTERN.matcher(obx);
		String v = null;
		if (m.find()) {
			v = m.group(2);
		}
		List<Double> data = Stream.of(v.split("\\^")).map(d -> Double.parseDouble(d)).collect(Collectors.toList());
		double[] dataArr = Stream.of(data.toArray(new Double[data.size()])).mapToDouble(Double::doubleValue).toArray();

		assertArrayEquals(expected, dataArr, 0.0001);
	}

	/**
	 * Extracts a row from a HL7v2 message using the first two HL7v2 fields as key.
	 * 
	 * E.g. The row "OBX|1009|NA|131331^MDC_ECG_LEAD_I^MDC|..." would be retrieved by the key "OBX|1009|"
	 * 
	 * @param msg
	 *            a full HL7v2 message
	 * @param key
	 *            the two first HL7v2 fields of a row, e.g. "OBX|1009|"
	 * @return row matching the key, null if not found.
	 */
	private String getRowFromHL7Message(String msg, String key) {
		Pattern p = Pattern.compile("^([^\\|]*\\|){2}");

		Map<String, String> rowMap = new HashMap<>();
		for (String row : msg.split("\r")) {
			Matcher m = p.matcher(row);
			if (m.find()) {
				rowMap.put(m.group(0), row);
			}
		}
		return rowMap.get(key);
	}
}
